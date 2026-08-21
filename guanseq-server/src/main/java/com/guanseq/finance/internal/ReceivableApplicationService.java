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
	private static final Set<String> STATUSES = Set.of("OPEN", "PARTIALLY_PAID", "PAID");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final SalesReceivableQueryProvider salesProvider;
	private final ReceivableInvoiceRepository invoiceRepository;
	private final ReceivableReceiptRepository receiptRepository;
	private final ReceivableEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	ReceivableApplicationService(CurrentWorkspaceProvider workspaceProvider, SalesReceivableQueryProvider salesProvider,
			ReceivableInvoiceRepository invoiceRepository, ReceivableReceiptRepository receiptRepository,
			ReceivableEventRepository eventRepository, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.salesProvider = salesProvider;
		this.invoiceRepository = invoiceRepository;
		this.receiptRepository = receiptRepository;
		this.eventRepository = eventRepository;
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

	private ReceivableInvoiceEntity findInvoice(CurrentWorkspaceAccess access, UUID id) {
		return invoiceRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "应收发票不存在或不在当前租户范围"));
	}

	private ReceivableInvoiceRecord toRecord(ReceivableInvoiceEntity invoice) {
		return new ReceivableInvoiceRecord(invoice.getId(), invoice.getInvoiceNumber(), invoice.getSalesOrderId(),
				invoice.getOrderNumber(), invoice.getCustomerId(), invoice.getCustomerCode(), invoice.getCustomerName(),
				invoice.getCurrency(), invoice.getInvoiceDate(), invoice.getDueDate(), invoice.getTaxRate(), invoice.getNetAmount(),
				invoice.getTaxAmount(), invoice.getGrossAmount(), invoice.getReceivedAmount(), invoice.outstandingAmount(),
				invoice.getStatus(), invoice.getVersion(), invoice.getCreatedAt(), invoice.getLines().stream()
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
		return new ReceivableInvoiceRecord.Receipt(receipt.getId(), receipt.getReceiptNumber(), receipt.getAmount(),
				receipt.getReceiptDate(), receipt.getPaymentMethod(), receipt.getBankReference(), receipt.getNote(),
				receipt.getStatus(), receipt.getCreatedAt());
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
