package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.finance.api.ReceivableCreditNotePage;
import com.guanseq.finance.api.ReceivableCreditNoteRecord;
import com.guanseq.finance.api.ReceivableInvoicePage;
import com.guanseq.finance.api.ReceivableInvoiceRecord;
import com.guanseq.finance.api.ReceivableReferenceData;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.sales.api.SalesReceivableQueryProvider;
import com.guanseq.sales.api.SalesReceivableQueryProvider.ReceivableLine;
import com.guanseq.sales.api.SalesReceivableQueryProvider.ReceivableOrder;

@Service
public class ReceivableApplicationService {

	private static final Set<String> WRITE_ROLES = Set.of("ADMIN", "FINANCE_MANAGER");
	private static final Set<String> STATUSES = Set.of("OPEN", "PARTIALLY_PAID", "PAID", "CREDIT_PENDING", "SETTLED");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final SalesReceivableQueryProvider salesProvider;
	private final ReceivableInvoiceRepository invoiceRepository;
	private final ReceivableReceiptRepository receiptRepository;
	private final ReceivableCreditNoteRepository creditNoteRepository;
	private final ReceivableReversalRepository reversalRepository;
	private final ReceivableEventRepository eventRepository;
	private final AccountingPeriodGuard periodGuard;
	private final OrderProfitApplicationService orderProfitService;
	private final JdbcTemplate jdbcTemplate;

	ReceivableApplicationService(CurrentWorkspaceProvider workspaceProvider, SalesReceivableQueryProvider salesProvider,
			ReceivableInvoiceRepository invoiceRepository, ReceivableReceiptRepository receiptRepository,
			ReceivableCreditNoteRepository creditNoteRepository, ReceivableReversalRepository reversalRepository,
			ReceivableEventRepository eventRepository, AccountingPeriodGuard periodGuard,
			OrderProfitApplicationService orderProfitService, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.salesProvider = salesProvider;
		this.invoiceRepository = invoiceRepository;
		this.receiptRepository = receiptRepository;
		this.creditNoteRepository = creditNoteRepository;
		this.reversalRepository = reversalRepository;
		this.eventRepository = eventRepository;
		this.periodGuard = periodGuard;
		this.orderProfitService = orderProfitService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public ReceivableInvoicePage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Page<ReceivableInvoiceEntity> result = invoiceRepository.search(access.tenantOrganizationId(), normalize(query),
				normalizeStatus(status), PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "createdAt")));
		return new ReceivableInvoicePage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ReceivableInvoiceRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(findInvoice(access, id));
	}

	@Transactional(readOnly = true)
	public ReceivableReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Map<UUID, BigDecimal> invoicedByLine = new HashMap<>();
		for (ReceivableInvoiceEntity invoice : invoiceRepository.findByTenantOrganizationId(access.tenantOrganizationId())) {
			for (ReceivableInvoiceLineEntity line : invoice.getLines()) {
				invoicedByLine.merge(line.getSalesOrderLineId(), line.getInvoiceQuantity(), BigDecimal::add);
			}
		}
		return new ReceivableReferenceData(salesProvider.listShippedOrders(access.tenantOrganizationId()).stream()
				.map(order -> toReferenceOrder(order, invoicedByLine)).toList());
	}

	@Transactional
	public ReceivableInvoiceRecord createInvoice(String username, String requestIdHeader,
			ReceivableInvoiceRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "开具应收发票");
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(), request.invoiceDate());
		String requestId = normalizeRequestId(requestIdHeader, "receivable-invoice");
		ReceivableInvoiceEntity duplicate = invoiceRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		if (request.dueDate().isBefore(request.invoiceDate())) {
			throw invalid("到期日不能早于开票日期");
		}
		lockBusinessKey("receivable-invoice:" + request.salesOrderId());
		duplicate = invoiceRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		ReceivableOrder order = salesProvider.findShippedOrder(access.tenantOrganizationId(), request.salesOrderId())
				.orElseThrow(() -> invalid("只有已部分发货或全部发货的销售订单可以开票"));
		Map<UUID, ReceivableLine> orderLines = order.lines().stream()
				.collect(Collectors.toMap(ReceivableLine::id, Function.identity()));
		Map<UUID, BigDecimal> alreadyInvoiced = invoicedQuantities(access.tenantOrganizationId(), order.id());
		Set<UUID> requestedLines = new HashSet<>();
		ReceivableInvoiceEntity invoice = new ReceivableInvoiceEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextInvoiceNumber(), order,
				request.invoiceDate(), request.dueDate(), requestId, access.userId());
		for (ReceivableInvoiceRecord.LineInput input : request.lines()) {
			if (!requestedLines.add(input.salesOrderLineId())) throw invalid("同一销售订单行不能重复开票");
			ReceivableLine line = orderLines.get(input.salesOrderLineId());
			if (line == null) throw invalid("开票行不属于指定销售订单");
			BigDecimal quantity = input.invoiceQuantity().setScale(4, RoundingMode.HALF_UP);
			BigDecimal remaining = line.deliveredQuantity().subtract(alreadyInvoiced
					.getOrDefault(line.id(), BigDecimal.ZERO)).setScale(4, RoundingMode.HALF_UP);
			if (quantity.signum() <= 0 || quantity.compareTo(remaining) > 0) {
				throw invalid(line.materialCode() + " 开票数量超过未开票的已发数量 " + remaining.toPlainString());
			}
			invoice.addLine(line, quantity);
		}
		if (invoice.getGrossAmount().signum() <= 0) throw invalid("应收发票含税金额必须大于零");
		try {
			invoiceRepository.saveAndFlush(invoice);
			eventRepository.saveAndFlush(new ReceivableEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), invoice.getId(), null, "CREATE_INVOICE", null, invoice.getStatus(), requestId,
					Map.of("invoiceNumber", invoice.getInvoiceNumber(), "salesOrderId", order.id(),
							"orderNumber", order.orderNumber(), "grossAmount", invoice.getGrossAmount())));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "开票请求与已有业务事实冲突，请刷新后确认", exception);
		}
		return toRecord(invoice);
	}

	@Transactional
	public ReceivableInvoiceRecord postReceipt(String username, UUID invoiceId, String requestIdHeader,
			ReceivableInvoiceRecord.ReceiptRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "登记并核销收款");
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(), request.receiptDate());
		String requestId = normalizeRequestId(requestIdHeader, "receivable-receipt");
		lockBusinessKey("receivable-receipt:" + invoiceId);
		ReceivableReceiptEntity duplicate = receiptRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(findInvoice(access, duplicate.getInvoiceId()));
		ReceivableInvoiceEntity invoice = findInvoice(access, invoiceId);
		if (invoice.getVersion() != request.expectedVersion()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应收发票已被其他事务更新，请刷新后重试");
		}
		if ("PAID".equals(invoice.getStatus())) throw invalid("该应收发票已全部收款");
		if (request.receiptDate().isBefore(invoice.getInvoiceDate())) throw invalid("收款日期不能早于开票日期");
		BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
		if (amount.signum() <= 0 || amount.compareTo(invoice.outstandingAmount()) > 0) {
			throw invalid("收款金额不能超过待收金额 " + invoice.outstandingAmount().toPlainString());
		}
		String fromStatus = invoice.getStatus();
		ReceivableReceiptEntity receipt = invoice.applyReceipt(nextReceiptNumber(), amount, request.receiptDate(),
				request.paymentMethod().trim().toUpperCase(), request.bankReference(), request.note(), requestId, access.userId());
		try {
			invoiceRepository.saveAndFlush(invoice);
			eventRepository.saveAndFlush(new ReceivableEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), invoice.getId(), receipt.getId(), "POST_RECEIPT", fromStatus, invoice.getStatus(), requestId,
					Map.of("receiptNumber", receipt.getReceiptNumber(), "amount", amount,
							"outstandingAmount", invoice.outstandingAmount())));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应收发票已被其他事务更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "收款请求与已有业务事实冲突，请刷新后确认", exception);
		}
		return toRecord(invoice);
	}

	// ---- 红字发票 ----

	@Transactional(readOnly = true)
	public ReceivableCreditNotePage listCreditNotes(String username, String query, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Page<ReceivableCreditNoteEntity> result = creditNoteRepository.search(access.tenantOrganizationId(), normalize(query),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "createdAt")));
		return new ReceivableCreditNotePage(result.getContent().stream().map(this::toCreditNoteRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ReceivableCreditNoteRecord getCreditNote(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toCreditNoteRecord(creditNoteRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "红字发票不存在或不在当前租户范围")));
	}

	@Transactional
	public ReceivableCreditNoteRecord createCreditNote(String username, String requestIdHeader,
			ReceivableCreditNoteRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "开具应收红字发票");
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(), request.creditNoteDate());
		String requestId = normalizeRequestId(requestIdHeader, "receivable-credit-note");
		ReceivableCreditNoteEntity duplicate = creditNoteRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toCreditNoteRecord(duplicate);
		if (request.dueDate().isBefore(request.creditNoteDate())) throw invalid("到期日不能早于红字发票日期");
		lockBusinessKey("receivable-credit-note:" + request.originalInvoiceId());
		duplicate = creditNoteRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toCreditNoteRecord(duplicate);
		ReceivableInvoiceEntity original = findInvoice(access, request.originalInvoiceId());
		Map<UUID, ReceivableInvoiceLineEntity> originalLines = original.getLines().stream()
				.collect(Collectors.toMap(ReceivableInvoiceLineEntity::getId, Function.identity()));
		Map<UUID, BigDecimal> alreadyCredited = creditedQuantities(access.tenantOrganizationId(), original.getId());
		Set<UUID> requestedLines = new HashSet<>();
		ReceivableCreditNoteEntity creditNote = new ReceivableCreditNoteEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextCreditNoteNumber(), original,
				request.taxNoticeNumber(), request.creditNoteDate(), request.dueDate(),
				request.reason().trim(), requestId, access.userId());
		for (ReceivableCreditNoteRecord.LineInput input : request.lines()) {
			if (!requestedLines.add(input.originalInvoiceLineId())) throw invalid("同一原发票行不能重复红冲");
			ReceivableInvoiceLineEntity originalLine = originalLines.get(input.originalInvoiceLineId());
			if (originalLine == null) throw invalid("红冲行不属于指定的原蓝字发票");
			BigDecimal creditQuantity = input.creditQuantity().setScale(4, RoundingMode.HALF_UP);
			BigDecimal remaining = originalLine.getInvoiceQuantity()
					.subtract(alreadyCredited.getOrDefault(originalLine.getId(), BigDecimal.ZERO))
					.setScale(4, RoundingMode.HALF_UP);
			if (creditQuantity.signum() <= 0 || creditQuantity.compareTo(remaining) > 0) {
				throw invalid(originalLine.getMaterialCode() + " 红冲数量超过可冲数量 " + remaining.toPlainString());
			}
			creditNote.addLine(originalLine, creditQuantity, input.unitPrice());
		}
		if (creditNote.getGrossAmount().signum() >= 0) throw invalid("红字发票含税金额必须为负数");
		String fromStatus = original.getStatus();
		original.applyCreditNote(creditNote.getGrossAmount(), access.userId());
		try {
			creditNoteRepository.saveAndFlush(creditNote);
			invoiceRepository.saveAndFlush(original);
			eventRepository.saveAndFlush(new ReceivableEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), original.getId(), null, "CREATE_CREDIT_NOTE", fromStatus, original.getStatus(), requestId,
					Map.of("creditNoteNumber", creditNote.getCreditNoteNumber(), "creditNoteId", creditNote.getId(),
							"originalInvoiceId", original.getId(), "grossAmount", creditNote.getGrossAmount(),
							"creditBalance", original.getCreditBalance())));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "红字发票请求与已有业务事实冲突，请刷新后确认", exception);
		}
		orderProfitService.markImpactedIfSettled(username, original.getSalesOrderId(), "RECEIVABLE_CREDIT_NOTE", creditNote.getCreditNoteNumber());
		return toCreditNoteRecord(creditNote);
	}

	@Transactional
	public ReceivableInvoiceRecord postRefund(String username, UUID invoiceId, String requestIdHeader,
			ReceivableCreditNoteRecord.RefundRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "登记应收退款");
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(), request.refundDate());
		String requestId = normalizeRequestId(requestIdHeader, "receivable-refund");
		lockBusinessKey("receivable-refund:" + invoiceId);
		ReceivableReceiptEntity duplicate = receiptRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(findInvoice(access, duplicate.getInvoiceId()));
		ReceivableInvoiceEntity invoice = findInvoice(access, invoiceId);
		if (invoice.getVersion() != request.expectedVersion()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应收发票已被其他事务更新，请刷新后重试");
		}
		if (invoice.getCreditBalance().signum() <= 0) throw invalid("该发票没有待退余额，不能登记退款");
		if (request.refundDate().isBefore(invoice.getInvoiceDate())) throw invalid("退款日期不能早于开票日期");
		BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
		if (amount.signum() <= 0 || amount.compareTo(invoice.getCreditBalance()) > 0) {
			throw invalid("退款金额不能超过待退余额 " + invoice.getCreditBalance().toPlainString());
		}
		String fromStatus = invoice.getStatus();
		ReceivableReceiptEntity refund = invoice.applyRefund(nextRefundNumber(), amount, request.refundDate(),
				request.paymentMethod().trim().toUpperCase(), request.bankReference(), request.note(), requestId, access.userId());
		try {
			invoiceRepository.saveAndFlush(invoice);
			eventRepository.saveAndFlush(new ReceivableEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), invoice.getId(), refund.getId(), "POST_REFUND", fromStatus, invoice.getStatus(), requestId,
					Map.of("refundNumber", refund.getReceiptNumber(), "amount", amount,
							"creditBalance", invoice.getCreditBalance())));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应收发票已被其他事务更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "退款请求与已有业务事实冲突，请刷新后确认", exception);
		}
		orderProfitService.markImpactedIfSettled(username, invoice.getSalesOrderId(), "RECEIVABLE_REFUND", refund.getReceiptNumber());
		return toRecord(invoice);
	}

	@Transactional
	public ReceivableInvoiceRecord reverseReceipt(String username, UUID receiptId, String requestIdHeader,
			ReceivableCreditNoteRecord.ReverseRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "反核销应收收款或退款");
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(), request.reversalDate());
		String requestId = normalizeRequestId(requestIdHeader, "receivable-reversal");
		ReceivableReversalEntity duplicate = reversalRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(findInvoice(access, duplicate.getInvoiceId()));
		lockBusinessKey("receivable-reversal:" + receiptId);
		duplicate = reversalRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(findInvoice(access, duplicate.getInvoiceId()));
		ReceivableReceiptEntity receipt = receiptRepository.findByIdAndTenantOrganizationId(receiptId, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "收款记录不存在或不在当前租户范围"));
		if ("REVERSED".equals(receipt.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "该记录已被反核销，不能重复操作");
		ReceivableInvoiceEntity invoice = findInvoice(access, receipt.getInvoiceId());
		if (request.reversalDate().isBefore(receipt.getReceiptDate())) throw invalid("反核销日期不能早于原记录日期");
		if (request.reason().trim().length() < 4) throw invalid("反核销原因至少需要 4 个字符");
		ReceivableReversalEntity reversal = new ReceivableReversalEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextReversalNumber(), receipt, invoice,
				request.reversalDate(), request.reason().trim(), requestId, access.userId());
		String fromStatus = invoice.getStatus();
		invoice.reverseReceipt(receipt, reversal.getId(), access.userId());
		try {
			reversalRepository.saveAndFlush(reversal);
			invoiceRepository.saveAndFlush(invoice);
			eventRepository.saveAndFlush(new ReceivableEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), invoice.getId(), receipt.getId(), "REVERSE_RECEIPT", fromStatus, invoice.getStatus(), requestId,
					Map.of("reversalNumber", reversal.getReversalNumber(), "reversedDirection", receipt.getDirection(),
							"amount", receipt.getAmount())));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应收发票已被其他事务更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "反核销请求与已有业务事实冲突，请刷新后确认", exception);
		}
		orderProfitService.markImpactedIfSettled(username, invoice.getSalesOrderId(), "RECEIVABLE_REVERSAL", reversal.getReversalNumber());
		return toRecord(invoice);
	}

	private ReceivableReferenceData.InvoiceableOrder toReferenceOrder(ReceivableOrder order,
			Map<UUID, BigDecimal> invoicedByLine) {
		List<ReceivableReferenceData.InvoiceableLine> lines = order.lines().stream().map(line -> {
			BigDecimal invoiced = invoicedByLine.getOrDefault(line.id(), BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
			BigDecimal remaining = line.deliveredQuantity().subtract(invoiced).max(BigDecimal.ZERO)
					.setScale(4, RoundingMode.HALF_UP);
			return new ReceivableReferenceData.InvoiceableLine(line.id(), line.lineNumber(), line.materialId(),
					line.materialCode(), line.materialName(), line.materialSpecification(), line.unit(),
					line.deliveredQuantity(), invoiced, remaining, line.unitPrice());
		}).toList();
		BigDecimal deliveredAmount = lines.stream().map(line -> line.deliveredQuantity().multiply(line.unitPrice()))
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		BigDecimal invoicedAmount = lines.stream().map(line -> line.invoicedQuantity().multiply(line.unitPrice()))
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		return new ReceivableReferenceData.InvoiceableOrder(order.id(), order.orderNumber(), order.customerId(),
				order.customerCode(), order.customerName(), order.currency(), order.taxRate(), order.status(), deliveredAmount,
				invoicedAmount, deliveredAmount.subtract(invoicedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP), lines);
	}

	private Map<UUID, BigDecimal> invoicedQuantities(UUID tenantId, UUID orderId) {
		Map<UUID, BigDecimal> result = new HashMap<>();
		for (ReceivableInvoiceEntity invoice : invoiceRepository.findByTenantOrganizationIdAndSalesOrderId(tenantId, orderId)) {
			for (ReceivableInvoiceLineEntity line : invoice.getLines()) {
				result.merge(line.getSalesOrderLineId(), line.getInvoiceQuantity(), BigDecimal::add);
			}
		}
		return result;
	}

	private Map<UUID, BigDecimal> creditedQuantities(UUID tenantId, UUID originalInvoiceId) {
		Map<UUID, BigDecimal> result = new HashMap<>();
		for (ReceivableCreditNoteEntity cn : creditNoteRepository.findByTenantOrganizationIdAndOriginalInvoiceId(tenantId, originalInvoiceId)) {
			for (ReceivableCreditNoteLineEntity line : cn.getLines()) {
				result.merge(line.getOriginalInvoiceLineId(), line.getCreditQuantity(), BigDecimal::add);
			}
		}
		return result;
	}

	private ReceivableInvoiceEntity findInvoice(CurrentWorkspaceAccess access, UUID id) {
		return invoiceRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "应收发票不存在或不在当前租户范围"));
	}

	private ReceivableInvoiceRecord toRecord(ReceivableInvoiceEntity invoice) {
		return new ReceivableInvoiceRecord(invoice.getId(), invoice.getInvoiceNumber(), invoice.getSalesOrderId(),
				invoice.getOrderNumber(), invoice.getCustomerId(), invoice.getCustomerCode(), invoice.getCustomerName(),
				invoice.getCurrency(), invoice.getInvoiceDate(), invoice.getDueDate(), invoice.getTaxRate(), invoice.getNetAmount(),
				invoice.getTaxAmount(), invoice.getGrossAmount(), invoice.getReceivedAmount(), invoice.outstandingAmount(),
				invoice.getCreditBalance(), invoice.getStatus(), invoice.getVersion(), invoice.getCreatedAt(), invoice.getLines().stream()
						.sorted(Comparator.comparingInt(ReceivableInvoiceLineEntity::getLineNumber)).map(this::toLineRecord).toList(),
				invoice.getReceipts().stream().sorted(Comparator.comparing(ReceivableReceiptEntity::getReceiptDate)
						.thenComparing(ReceivableReceiptEntity::getCreatedAt).reversed()).map(this::toReceiptRecord).toList());
	}

	private ReceivableInvoiceRecord.Line toLineRecord(ReceivableInvoiceLineEntity line) {
		return new ReceivableInvoiceRecord.Line(line.getId(), line.getSalesOrderLineId(), line.getLineNumber(), line.getMaterialId(),
				line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
				line.getInvoiceQuantity(), line.getUnitPrice(), line.getNetAmount(), line.getTaxAmount(), line.getGrossAmount());
	}

	private ReceivableInvoiceRecord.Receipt toReceiptRecord(ReceivableReceiptEntity receipt) {
		return new ReceivableInvoiceRecord.Receipt(receipt.getId(), receipt.getReceiptNumber(), receipt.getDirection(),
				receipt.getAmount(), receipt.getReceiptDate(), receipt.getPaymentMethod(), receipt.getBankReference(),
				receipt.getNote(), receipt.getStatus(), receipt.getCreatedAt());
	}

	private ReceivableCreditNoteRecord toCreditNoteRecord(ReceivableCreditNoteEntity cn) {
		return new ReceivableCreditNoteRecord(cn.getId(), cn.getCreditNoteNumber(), cn.getOriginalInvoiceId(),
				cn.getOriginalInvoiceNumber(), cn.getSalesOrderId(), cn.getOrderNumber(), cn.getCustomerId(),
				cn.getCustomerCode(), cn.getCustomerName(), cn.getCurrency(), cn.getTaxNoticeNumber(),
				cn.getCreditNoteDate(), cn.getDueDate(), cn.getTaxRate(), cn.getNetAmount(), cn.getTaxAmount(),
				cn.getGrossAmount(), cn.getReason(), cn.getStatus(), cn.getVersion(), cn.getCreatedAt(),
				cn.getLines().stream().sorted(Comparator.comparingInt(ReceivableCreditNoteLineEntity::getLineNumber))
						.map(this::toCreditNoteLineRecord).toList());
	}

	private ReceivableCreditNoteRecord.Line toCreditNoteLineRecord(ReceivableCreditNoteLineEntity line) {
		return new ReceivableCreditNoteRecord.Line(line.getId(), line.getOriginalInvoiceLineId(), line.getSalesOrderLineId(),
				line.getLineNumber(), line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(),
				line.getMaterialSpecification(), line.getUnit(), line.getCreditQuantity(), line.getUnitPrice(),
				line.getNetAmount(), line.getTaxAmount(), line.getGrossAmount());
	}

	private void lockBusinessKey(String key) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
				statement -> statement.setString(1, key), resultSet -> null);
	}

	private String nextInvoiceNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.receivable_invoice_number_seq')", Long.class);
		return "ARINV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private String nextReceiptNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.receivable_receipt_number_seq')", Long.class);
		return "ARRC-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private String nextCreditNoteNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.receivable_credit_note_number_seq')", Long.class);
		return "ARCN-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private String nextRefundNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.receivable_receipt_number_seq')", Long.class);
		return "ARRF-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private String nextReversalNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.receivable_reversal_number_seq')", Long.class);
		return "ARRV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private static void requireWriteRole(CurrentWorkspaceAccess access, String action) {
		if (!WRITE_ROLES.contains(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权" + action);
		}
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的应收状态");
		return normalized;
	}
	private static String normalizeRequestId(String value, String prefix) {
		if (value != null && !value.isBlank()) {
			String normalized = value.trim();
			if (normalized.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id 不能超过 120 个字符");
			return normalized;
		}
		String mdc = MDC.get("requestId");
		return mdc != null && !mdc.isBlank() ? mdc : prefix + "-" + UUID.randomUUID();
	}
	private static ResponseStatusException invalid(String message) {
		return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
	}
}