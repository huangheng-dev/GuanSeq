package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.finance.api.GrirAccrualPage;
import com.guanseq.finance.api.GrirAccrualPreview;
import com.guanseq.finance.api.GrirAccrualRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableLine;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableOrder;

@Service
public class GrirAccrualApplicationService {

	private static final Set<String> WRITE_ROLES = Set.of("ADMIN", "FINANCE_MANAGER");
	private static final Set<String> STATUSES = Set.of("POSTED", "REVERSED");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final ProcurementPayableQueryProvider procurementProvider;
	private final GrirAccrualRepository accrualRepository;
	private final PayableInvoiceRepository invoiceRepository;
	private final GrirAccrualEventRepository eventRepository;
	private final AccountingPeriodGuard periodGuard;
	private final JdbcTemplate jdbcTemplate;

	GrirAccrualApplicationService(CurrentWorkspaceProvider workspaceProvider,
			ProcurementPayableQueryProvider procurementProvider,
			GrirAccrualRepository accrualRepository,
			PayableInvoiceRepository invoiceRepository,
			GrirAccrualEventRepository eventRepository,
			AccountingPeriodGuard periodGuard,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.procurementProvider = procurementProvider;
		this.accrualRepository = accrualRepository;
		this.invoiceRepository = invoiceRepository;
		this.eventRepository = eventRepository;
		this.periodGuard = periodGuard;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public GrirAccrualPage list(String username, Integer year, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Page<GrirAccrualEntity> result = accrualRepository.search(access.tenantOrganizationId(), year,
				normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "fiscalYear").and(Sort.by(Sort.Direction.DESC, "fiscalPeriod"))));
		return new GrirAccrualPage(result.getContent().stream().map(this::toRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public GrirAccrualRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(findAccrual(access, id));
	}

	@Transactional(readOnly = true)
	public GrirAccrualPreview preview(String username, int fiscalYear, int fiscalPeriod) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		validatePeriod(fiscalYear, fiscalPeriod);
		List<GrirAccrualEntity> prior = accrualRepository.findPriorPosted(access.tenantOrganizationId(),
				fiscalYear, fiscalPeriod);
		GrirAccrualEntity priorAccrual = prior.isEmpty() ? null : prior.get(0);
		Map<UUID, BigDecimal> invoicedByLine = invoicedQuantities(access.tenantOrganizationId());
		List<GrirAccrualPreview.Line> lines = buildPreviewLines(access.tenantOrganizationId(), invoicedByLine);
		BigDecimal total = lines.stream().map(GrirAccrualPreview.Line::netAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		return new GrirAccrualPreview(fiscalYear, fiscalPeriod,
				priorAccrual == null ? null : priorAccrual.getId(),
				priorAccrual == null ? null : priorAccrual.getAccrualNumber(),
				priorAccrual == null ? BigDecimal.ZERO.setScale(2) : priorAccrual.getTotalNetAmount(),
				total, lines);
	}

	@Transactional
	public GrirAccrualRecord run(String username, String requestIdHeader, GrirAccrualRecord.RunRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "运行月末暂估");
		validatePeriod(request.fiscalYear(), request.fiscalPeriod());
		LocalDate accrualDate = resolveAccrualDate(request);
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(), accrualDate);
		String requestId = normalizeRequestId(requestIdHeader, "grir-accrual");
		lockBusinessKey("grir-accrual:" + access.tenantOrganizationId() + ":"
				+ request.fiscalYear() + "-" + request.fiscalPeriod());
		GrirAccrualEntity duplicate = accrualRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		GrirAccrualEntity existing = accrualRepository
				.findByTenantOrganizationIdAndFiscalYearAndFiscalPeriod(
						access.tenantOrganizationId(), request.fiscalYear(), request.fiscalPeriod())
				.orElse(null);
		if (existing != null && "POSTED".equals(existing.getStatus())) {
			return toRecord(existing);
		}
		Map<UUID, BigDecimal> invoicedByLine = invoicedQuantities(access.tenantOrganizationId());
		String note = request.note() == null ? null : request.note().trim();
		GrirAccrualEntity accrual = new GrirAccrualEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextAccrualNumber(),
				request.fiscalYear(), request.fiscalPeriod(), accrualDate,
				note == null || note.isBlank() ? null : note, requestId, access.userId());
		for (PayableOrder order : procurementProvider.listReceivedOrders(access.tenantOrganizationId())) {
			for (PayableLine line : order.lines()) {
				BigDecimal received = line.acceptedQuantity();
				BigDecimal invoiced = invoicedByLine.getOrDefault(line.id(), BigDecimal.ZERO)
						.setScale(4, RoundingMode.HALF_UP);
				BigDecimal accrued = received.subtract(invoiced);
				if (accrued.signum() > 0) {
					accrual.addLine(order, line, received, invoiced);
				}
			}
		}
		List<GrirAccrualEntity> priorList = accrualRepository.findPriorPosted(access.tenantOrganizationId(),
				request.fiscalYear(), request.fiscalPeriod());
		GrirAccrualEntity prior = priorList.isEmpty() ? null : priorList.get(0);
		try {
			accrualRepository.saveAndFlush(accrual);
			if (prior != null) {
				prior.markReversedBy(accrual.getId(), accrualDate, access.userId());
				accrualRepository.saveAndFlush(prior);
				eventRepository.saveAndFlush(new GrirAccrualEventEntity(access.tenantOrganizationId(),
						access.workspaceId(), access.userId(), prior.getId(), "REVERSE_PRIOR", requestId,
						Map.of("priorAccrualNumber", prior.getAccrualNumber(),
								"newAccrualId", accrual.getId(),
								"newAccrualNumber", accrual.getAccrualNumber(),
								"reversalDate", accrualDate.toString())));
			}
			eventRepository.saveAndFlush(new GrirAccrualEventEntity(access.tenantOrganizationId(),
					access.workspaceId(), access.userId(), accrual.getId(), "ACCRUE", requestId,
					Map.of("accrualNumber", accrual.getAccrualNumber(),
							"fiscalYear", accrual.getFiscalYear(),
							"fiscalPeriod", accrual.getFiscalPeriod(),
							"totalNetAmount", accrual.getTotalNetAmount(),
							"lineCount", accrual.getLines().size(),
							"priorReversed", prior != null)));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"该会计年月暂估已生成或请求编号冲突，请刷新后确认", exception);
		}
		return toRecord(accrual);
	}

	@Transactional
	public GrirAccrualRecord reverse(String username, UUID id, String requestIdHeader,
			GrirAccrualRecord.ReverseRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "手动冲回暂估");
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				request.reversalDate());
		String requestId = normalizeRequestId(requestIdHeader, "grir-reverse");
		GrirAccrualEntity duplicate = accrualRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		lockBusinessKey("grir-reverse:" + id);
		GrirAccrualEntity accrual = findAccrual(access, id);
		if ("REVERSED".equals(accrual.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该暂估单已冲回，不能重复操作");
		}
		String reason = request.reason().trim();
		if (reason.length() < 4) throw invalid("冲回原因至少需要 4 个字符");
		accrual.manuallyReverse(request.reversalDate(), reason, access.userId());
		try {
			accrualRepository.saveAndFlush(accrual);
			eventRepository.saveAndFlush(new GrirAccrualEventEntity(access.tenantOrganizationId(),
					access.workspaceId(), access.userId(), accrual.getId(), "MANUAL_REVERSE", requestId,
					Map.of("accrualNumber", accrual.getAccrualNumber(),
							"reversalDate", request.reversalDate().toString(),
							"reason", reason)));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "冲回请求与已有业务事实冲突，请刷新后确认", exception);
		}
		return toRecord(accrual);
	}

	// ---- helpers ----

	private List<GrirAccrualPreview.Line> buildPreviewLines(UUID tenantId, Map<UUID, BigDecimal> invoicedByLine) {
		return procurementProvider.listReceivedOrders(tenantId).stream()
				.flatMap(order -> order.lines().stream()
						.filter(line -> line.acceptedQuantity().subtract(
								invoicedByLine.getOrDefault(line.id(), BigDecimal.ZERO)
										.setScale(4, RoundingMode.HALF_UP))
								.signum() > 0)
						.sorted(Comparator.comparingInt(PayableLine::lineNumber))
						.map(line -> {
							BigDecimal received = line.acceptedQuantity();
							BigDecimal invoiced = invoicedByLine.getOrDefault(line.id(), BigDecimal.ZERO)
									.setScale(4, RoundingMode.HALF_UP);
							BigDecimal accrued = received.subtract(invoiced).setScale(4, RoundingMode.HALF_UP);
							BigDecimal netAmount = accrued.multiply(line.unitPrice())
									.setScale(2, RoundingMode.HALF_UP);
							return new GrirAccrualPreview.Line(order.id(), order.orderNumber(),
									order.supplierId(), order.supplierCode(), order.supplierName(),
									line.id(), line.lineNumber(), line.materialId(), line.materialCode(),
									line.materialName(), line.materialSpecification(), line.unit(),
									received, invoiced, accrued, line.unitPrice(), netAmount);
						}))
				.sorted(Comparator.comparing(GrirAccrualPreview.Line::orderNumber)
						.thenComparingInt(GrirAccrualPreview.Line::lineNumber))
				.toList();
	}

	private Map<UUID, BigDecimal> invoicedQuantities(UUID tenantId) {
		Map<UUID, BigDecimal> result = new HashMap<>();
		for (PayableInvoiceEntity invoice : invoiceRepository.findByTenantOrganizationId(tenantId)) {
			for (PayableInvoiceLineEntity line : invoice.getLines()) {
				result.merge(line.getPurchaseOrderLineId(), line.getInvoiceQuantity(), BigDecimal::add);
			}
		}
		return result;
	}

	private LocalDate resolveAccrualDate(GrirAccrualRecord.RunRequest request) {
		if (request.accrualDate() != null) return request.accrualDate();
		LocalDate first = LocalDate.of(request.fiscalYear(), request.fiscalPeriod(), 1);
		return first.withDayOfMonth(first.lengthOfMonth());
	}

	private void validatePeriod(int fiscalYear, int fiscalPeriod) {
		if (fiscalPeriod < 1 || fiscalPeriod > 12) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会计期间必须在 1–12 之间");
		}
		if (fiscalYear < 2000 || fiscalYear > 2100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会计年度超出范围");
		}
	}

	private GrirAccrualEntity findAccrual(CurrentWorkspaceAccess access, UUID id) {
		return accrualRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"暂估单不存在或不在当前租户范围"));
	}

	private GrirAccrualRecord toRecord(GrirAccrualEntity accrual) {
		return new GrirAccrualRecord(accrual.getId(), accrual.getAccrualNumber(),
				accrual.getFiscalYear(), accrual.getFiscalPeriod(), accrual.getAccrualDate(),
				accrual.getStatus(), accrual.getTotalNetAmount(), accrual.getReversedByAccrualId(),
				accrual.getReversalDate(), accrual.getReversalReason(), accrual.getNote(),
				accrual.getVersion(), accrual.getCreatedAt(),
				accrual.getLines().stream()
						.sorted(Comparator.comparingInt(GrirAccrualLineEntity::getLineNumber))
						.map(this::toLineRecord).toList());
	}

	private GrirAccrualRecord.Line toLineRecord(GrirAccrualLineEntity line) {
		return new GrirAccrualRecord.Line(line.getId(), line.getPurchaseOrderId(), line.getOrderNumber(),
				line.getSupplierId(), line.getSupplierCode(), line.getSupplierName(),
				line.getPurchaseOrderLineId(), line.getLineNumber(), line.getMaterialId(),
				line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(),
				line.getUnit(), line.getReceivedQuantity(), line.getInvoicedQuantity(),
				line.getAccruedQuantity(), line.getUnitPrice(), line.getNetAmount());
	}

	private void lockBusinessKey(String key) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
				statement -> statement.setString(1, key), resultSet -> null);
	}

	private String nextAccrualNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.grir_accrual_number_seq')", Long.class);
		return "GRIR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
				+ String.format("%06d", value);
	}

	private static void requireWriteRole(CurrentWorkspaceAccess access, String action) {
		if (!WRITE_ROLES.contains(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权" + action);
		}
	}

	private static String normalizeStatus(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的暂估状态");
		}
		return normalized;
	}

	private static String normalizeRequestId(String value, String prefix) {
		if (value != null && !value.isBlank()) {
			String normalized = value.trim();
			if (normalized.length() > 120) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id 不能超过 120 个字符");
			}
			return normalized;
		}
		String mdc = MDC.get("requestId");
		return mdc != null && !mdc.isBlank() ? mdc : prefix + "-" + UUID.randomUUID();
	}

	private static ResponseStatusException invalid(String message) {
		return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
	}
}
