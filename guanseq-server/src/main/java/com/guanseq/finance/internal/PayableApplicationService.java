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

import com.guanseq.finance.api.PayableInvoicePage;
import com.guanseq.finance.api.PayableInvoiceRecord;
import com.guanseq.finance.api.PayableReferenceData;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableLine;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableOrder;

@Service
public class PayableApplicationService {

	private static final Set<String> WRITE_ROLES = Set.of("ADMIN", "FINANCE_MANAGER");
	private static final Set<String> STATUSES = Set.of("OPEN", "PARTIALLY_PAID", "PAID");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final ProcurementPayableQueryProvider procurementProvider;
	private final PayableInvoiceRepository invoiceRepository;
	private final PayablePaymentRepository paymentRepository;
	private final PayableEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	PayableApplicationService(CurrentWorkspaceProvider workspaceProvider, ProcurementPayableQueryProvider procurementProvider,
			PayableInvoiceRepository invoiceRepository, PayablePaymentRepository paymentRepository,
			PayableEventRepository eventRepository, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.procurementProvider = procurementProvider;
		this.invoiceRepository = invoiceRepository;
		this.paymentRepository = paymentRepository;
		this.eventRepository = eventRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PayableInvoicePage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Page<PayableInvoiceEntity> result = invoiceRepository.search(access.tenantOrganizationId(), normalize(query),
				normalizeStatus(status), PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "createdAt")));
		return new PayableInvoicePage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public PayableInvoiceRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(findInvoice(access, id));
	}

	@Transactional(readOnly = true)
	public PayableReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Map<UUID, BigDecimal> invoicedByLine = new HashMap<>();
		for (PayableInvoiceEntity invoice : invoiceRepository.findByTenantOrganizationId(access.tenantOrganizationId())) {
			for (PayableInvoiceLineEntity line : invoice.getLines()) {
				invoicedByLine.merge(line.getPurchaseOrderLineId(), line.getInvoiceQuantity(), BigDecimal::add);
			}
		}
		return new PayableReferenceData(procurementProvider.listReceivedOrders(access.tenantOrganizationId()).stream()
				.map(order -> toReferenceOrder(order, invoicedByLine)).toList());
	}

	@Transactional
	public PayableInvoiceRecord createInvoice(String username, String requestIdHeader,
			PayableInvoiceRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "登记采购应付发票");
		String requestId = normalizeRequestId(requestIdHeader, "payable-invoice");
		PayableInvoiceEntity duplicate = invoiceRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		if (request.dueDate().isBefore(request.invoiceDate())) throw invalid("到期日不能早于发票日期");
		lockBusinessKey("payable-invoice:" + request.purchaseOrderId());
		duplicate = invoiceRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		PayableOrder order = procurementProvider.findReceivedOrder(access.tenantOrganizationId(), request.purchaseOrderId())
				.orElseThrow(() -> invalid("只有存在合格收货数量的采购订单可以登记应付发票"));
		String supplierInvoiceNumber = request.supplierInvoiceNumber().trim();
		lockBusinessKey("payable-supplier-invoice:" + access.tenantOrganizationId() + ":" + order.supplierId() + ":"
				+ supplierInvoiceNumber);
		if (invoiceRepository.findByTenantOrganizationIdAndSupplierIdAndSupplierInvoiceNumber(
				access.tenantOrganizationId(), order.supplierId(), supplierInvoiceNumber).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该供应商发票号已经登记，不能重复入账");
		}
		Map<UUID, PayableLine> orderLines = order.lines().stream().collect(Collectors.toMap(PayableLine::id, Function.identity()));
		Map<UUID, BigDecimal> alreadyInvoiced = invoicedQuantities(access.tenantOrganizationId(), order.id());
		Set<UUID> requestedLines = new HashSet<>();
		PayableInvoiceEntity invoice = new PayableInvoiceEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextInvoiceNumber(), supplierInvoiceNumber,
				order, request.invoiceDate(), request.dueDate(), requestId, access.userId());
		for (PayableInvoiceRecord.LineInput input : request.lines()) {
			if (!requestedLines.add(input.purchaseOrderLineId())) throw invalid("同一采购订单行不能重复开票");
			PayableLine line = orderLines.get(input.purchaseOrderLineId());
			if (line == null) throw invalid("发票行不属于指定采购订单或尚无合格收货");
			BigDecimal quantity = input.invoiceQuantity().setScale(4, RoundingMode.HALF_UP);
			BigDecimal remaining = line.acceptedQuantity().subtract(alreadyInvoiced.getOrDefault(line.id(), BigDecimal.ZERO))
					.setScale(4, RoundingMode.HALF_UP);
			if (quantity.signum() <= 0 || quantity.compareTo(remaining) > 0) {
				throw invalid(line.materialCode() + " 开票数量超过未开票的合格收货数量 " + remaining.toPlainString());
			}
			invoice.addLine(line, quantity);
		}
		if (invoice.getGrossAmount().signum() <= 0) throw invalid("应付发票含税金额必须大于零");
		try {
			invoiceRepository.saveAndFlush(invoice);
			eventRepository.saveAndFlush(new PayableEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), invoice.getId(), null, "CREATE_INVOICE", null, invoice.getStatus(), requestId,
					Map.of("invoiceNumber", invoice.getInvoiceNumber(), "supplierInvoiceNumber", invoice.getSupplierInvoiceNumber(),
							"purchaseOrderId", order.id(), "orderNumber", order.orderNumber(), "grossAmount", invoice.getGrossAmount())));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应付发票请求或供应商发票号与已有业务事实冲突，请刷新后确认", exception);
		}
		return toRecord(invoice);
	}

	@Transactional
	public PayableInvoiceRecord postPayment(String username, UUID invoiceId, String requestIdHeader,
			PayableInvoiceRecord.PaymentRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "登记并核销付款");
		String requestId = normalizeRequestId(requestIdHeader, "payable-payment");
		lockBusinessKey("payable-payment:" + invoiceId);
		PayablePaymentEntity duplicate = paymentRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(findInvoice(access, duplicate.getInvoiceId()));
		PayableInvoiceEntity invoice = findInvoice(access, invoiceId);
		if (invoice.getVersion() != request.expectedVersion()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应付发票已被其他事务更新，请刷新后重试");
		}
		if ("PAID".equals(invoice.getStatus())) throw invalid("该应付发票已全部付款");
		if (request.paymentDate().isBefore(invoice.getInvoiceDate())) throw invalid("付款日期不能早于发票日期");
		BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
		if (amount.signum() <= 0 || amount.compareTo(invoice.outstandingAmount()) > 0) {
			throw invalid("付款金额不能超过待付金额 " + invoice.outstandingAmount().toPlainString());
		}
		String fromStatus = invoice.getStatus();
		PayablePaymentEntity payment = invoice.applyPayment(nextPaymentNumber(), amount, request.paymentDate(),
				request.paymentMethod().trim().toUpperCase(), request.bankReference(), request.note(), requestId, access.userId());
		try {
			invoiceRepository.saveAndFlush(invoice);
			eventRepository.saveAndFlush(new PayableEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), invoice.getId(), payment.getId(), "POST_PAYMENT", fromStatus, invoice.getStatus(), requestId,
					Map.of("paymentNumber", payment.getPaymentNumber(), "amount", amount,
							"outstandingAmount", invoice.outstandingAmount())));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "应付发票已被其他事务更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "付款请求与已有业务事实冲突，请刷新后确认", exception);
		}
		return toRecord(invoice);
	}

	private PayableReferenceData.InvoiceableOrder toReferenceOrder(PayableOrder order, Map<UUID, BigDecimal> invoicedByLine) {
		List<PayableReferenceData.InvoiceableLine> lines = order.lines().stream().map(line -> {
			BigDecimal invoiced = invoicedByLine.getOrDefault(line.id(), BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
			BigDecimal remaining = line.acceptedQuantity().subtract(invoiced).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
			return new PayableReferenceData.InvoiceableLine(line.id(), line.lineNumber(), line.materialId(), line.materialCode(),
					line.materialName(), line.materialSpecification(), line.unit(), line.acceptedQuantity(), invoiced, remaining,
					line.unitPrice());
		}).toList();
		BigDecimal acceptedAmount = lines.stream().map(line -> line.acceptedQuantity().multiply(line.unitPrice()))
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		BigDecimal invoicedAmount = lines.stream().map(line -> line.invoicedQuantity().multiply(line.unitPrice()))
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		return new PayableReferenceData.InvoiceableOrder(order.id(), order.orderNumber(), order.supplierId(),
				order.supplierCode(), order.supplierName(), order.currency(), order.taxRate(), order.status(), acceptedAmount,
				invoicedAmount, acceptedAmount.subtract(invoicedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP), lines);
	}

	private Map<UUID, BigDecimal> invoicedQuantities(UUID tenantId, UUID orderId) {
		Map<UUID, BigDecimal> result = new HashMap<>();
		for (PayableInvoiceEntity invoice : invoiceRepository.findByTenantOrganizationIdAndPurchaseOrderId(tenantId, orderId)) {
			for (PayableInvoiceLineEntity line : invoice.getLines()) {
				result.merge(line.getPurchaseOrderLineId(), line.getInvoiceQuantity(), BigDecimal::add);
			}
		}
		return result;
	}

	private PayableInvoiceEntity findInvoice(CurrentWorkspaceAccess access, UUID id) {
		return invoiceRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "应付发票不存在或不在当前租户范围"));
	}

	private PayableInvoiceRecord toRecord(PayableInvoiceEntity invoice) {
		return new PayableInvoiceRecord(invoice.getId(), invoice.getInvoiceNumber(), invoice.getSupplierInvoiceNumber(),
				invoice.getPurchaseOrderId(), invoice.getOrderNumber(), invoice.getSupplierId(), invoice.getSupplierCode(),
				invoice.getSupplierName(), invoice.getCurrency(), invoice.getInvoiceDate(), invoice.getDueDate(), invoice.getTaxRate(),
				invoice.getNetAmount(), invoice.getTaxAmount(), invoice.getGrossAmount(), invoice.getPaidAmount(),
				invoice.outstandingAmount(), invoice.getStatus(), invoice.getVersion(), invoice.getCreatedAt(),
				invoice.getLines().stream().sorted(Comparator.comparingInt(PayableInvoiceLineEntity::getLineNumber))
						.map(this::toLineRecord).toList(),
				invoice.getPayments().stream().sorted(Comparator.comparing(PayablePaymentEntity::getPaymentDate)
						.thenComparing(PayablePaymentEntity::getCreatedAt).reversed()).map(this::toPaymentRecord).toList());
	}

	private PayableInvoiceRecord.Line toLineRecord(PayableInvoiceLineEntity line) {
		return new PayableInvoiceRecord.Line(line.getId(), line.getPurchaseOrderLineId(), line.getLineNumber(),
				line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
				line.getInvoiceQuantity(), line.getUnitPrice(), line.getNetAmount(), line.getTaxAmount(), line.getGrossAmount());
	}

	private PayableInvoiceRecord.Payment toPaymentRecord(PayablePaymentEntity payment) {
		return new PayableInvoiceRecord.Payment(payment.getId(), payment.getPaymentNumber(), payment.getAmount(),
				payment.getPaymentDate(), payment.getPaymentMethod(), payment.getBankReference(), payment.getNote(),
				payment.getStatus(), payment.getCreatedAt());
	}

	private void lockBusinessKey(String key) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
				statement -> statement.setString(1, key), resultSet -> null);
	}

	private String nextInvoiceNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.payable_invoice_number_seq')", Long.class);
		return "APINV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private String nextPaymentNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.payable_payment_number_seq')", Long.class);
		return "APPAY-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private static void requireWriteRole(CurrentWorkspaceAccess access, String action) {
		if (!WRITE_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权" + action);
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的应付状态");
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
