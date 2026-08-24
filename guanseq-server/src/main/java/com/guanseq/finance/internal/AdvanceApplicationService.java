package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.finance.api.AdvancePage;
import com.guanseq.finance.api.AdvanceRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.masterdata.api.MasterDataReferenceProvider;
import com.guanseq.masterdata.api.MasterDataReferenceProvider.CustomerReference;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider.SupplierReference;

@Service
public class AdvanceApplicationService {

	private static final Set<String> WRITE_ROLES = Set.of("ADMIN", "FINANCE_MANAGER");
	private static final Set<String> TYPES = Set.of("RECEIVABLE", "PAYABLE");
	private static final Set<String> STATUSES = Set.of("OPEN", "PARTIALLY_USED", "CLOSED");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final ProcurementPayableQueryProvider procurementProvider;
	private final AdvanceRepository advanceRepository;
	private final AdvanceApplicationRepository applicationRepository;
	private final AdvanceRefundRepository refundRepository;
	private final AdvanceEventRepository eventRepository;
	private final AccountingPeriodGuard periodGuard;
	private final JdbcTemplate jdbcTemplate;

	AdvanceApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider,
			ProcurementPayableQueryProvider procurementProvider,
			AdvanceRepository advanceRepository,
			AdvanceApplicationRepository applicationRepository,
			AdvanceRefundRepository refundRepository,
			AdvanceEventRepository eventRepository,
			AccountingPeriodGuard periodGuard,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.masterDataProvider = masterDataProvider;
		this.procurementProvider = procurementProvider;
		this.advanceRepository = advanceRepository;
		this.applicationRepository = applicationRepository;
		this.refundRepository = refundRepository;
		this.eventRepository = eventRepository;
		this.periodGuard = periodGuard;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public AdvancePage list(String username, String type, String status, String partyId,
			String query, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalizedType = normalizeType(type);
		String normalizedStatus = normalizeStatus(status);
		UUID partyUuid = parsePartyId(partyId);
		Page<AdvanceEntity> result = advanceRepository.search(access.tenantOrganizationId(),
				normalizedType, normalizedStatus, partyUuid, normalize(query),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "createdAt")));
		List<AdvanceRecord> records = result.getContent().stream().map(e -> toSummary(e)).toList();
		return new AdvancePage(records, result.getTotalElements(), result.getTotalPages(),
				result.getNumber(), result.getSize());
	}

	@Transactional(readOnly = true)
	public AdvanceRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		AdvanceEntity entity = advanceRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "预收预付单不存在"));
		return toDetail(entity);
	}

	@Transactional
	public AdvanceRecord register(String username, String requestIdHeader, AdvanceRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "登记预收预付");
		if (!TYPES.contains(request.type())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类型必须为 RECEIVABLE 或 PAYABLE");
		}
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), request.advanceDate());
		String requestId = normalizeRequestId(requestIdHeader, "advance-register");

		AdvanceEntity duplicate = advanceRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toDetail(duplicate);

		PartyInfo party = resolveParty(access, request.type(), request.partyId());
		BigDecimal totalAmount = request.totalAmount().setScale(2, RoundingMode.HALF_UP);
		if (totalAmount.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预收预付金额必须大于 0");
		}

		lockBusinessKey("advance:" + access.tenantOrganizationId() + ":" + request.type() + ":" + request.partyId());
		duplicate = advanceRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toDetail(duplicate);

		String prefix = "RECEIVABLE".equals(request.type()) ? "ADR" : "ADP";
		String number = nextAdvanceNumber(prefix);

		AdvanceEntity entity = new AdvanceEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), number, request.type(), party.partyType(), party.partyId(),
				party.partyCode(), party.partyName(), request.advanceDate(), totalAmount,
				request.note(), requestId, access.userId());
		advanceRepository.saveAndFlush(entity);

		eventRepository.saveAndFlush(new AdvanceEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), entity.getId(), null, null, "REGISTER", null, entity.getStatus(),
				requestId, Map.of("totalAmount", totalAmount, "partyName", party.partyName())));

		return toDetail(entity);
	}

	@Transactional
	public AdvanceRecord refund(String username, UUID id, String requestIdHeader, AdvanceRecord.RefundRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "退还预收预付");
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), request.refundDate());
		String requestId = normalizeRequestId(requestIdHeader, "advance-refund");

		AdvanceEntity entity = advanceRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "预收预付单不存在"));

		advanceRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId)
				.ifPresent(d -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "该请求已处理"); });

		BigDecimal refundAmount = request.refundAmount().setScale(2, RoundingMode.HALF_UP);
		if (refundAmount.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退款金额必须大于 0");
		}
		if (entity.availableBalance().compareTo(refundAmount) < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退款金额超过可用余额");
		}

		lockBusinessKey("advance:" + access.tenantOrganizationId() + ":" + entity.getType() + ":" + entity.getPartyId());

		String fromStatus = entity.getStatus();
		AdvanceRefundEntity refund = entity.refund(refundAmount, request.refundDate(),
				request.reason().trim(), requestId, access.userId());
		advanceRepository.saveAndFlush(entity);

		eventRepository.saveAndFlush(new AdvanceEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), entity.getId(), null, refund.getId(), "REFUND", fromStatus, entity.getStatus(),
				requestId, Map.of("refundAmount", refundAmount, "reason", request.reason().trim())));

		return toDetail(entity);
	}

	/**
	 * 开票时抵扣预收/预付。由应收/应付 ApplicationService 在开票事务内调用。
	 * 返回更新后的 AdvanceEntity（已持久化），调用方负责更新发票已收/已付金额。
	 */
	@Transactional
	public AdvanceApplicationResult applyAdvance(CurrentWorkspaceAccess access, String advanceType,
			UUID partyId, UUID invoiceId, String invoiceNumber, BigDecimal grossAmount,
			UUID specifiedAdvanceId, LocalDate invoiceDate) {
		AdvanceEntity advance;
		if (specifiedAdvanceId != null) {
			advance = advanceRepository.findByIdAndTenantOrganizationId(specifiedAdvanceId, access.tenantOrganizationId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定的预收预付单不存在"));
			if (!advance.getType().equals(advanceType)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预收预付单类型不匹配");
			}
			if (!advance.getPartyId().equals(partyId)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预收预付单与客户/供应商不匹配");
			}
		} else {
			List<AdvanceEntity> openAdvances = advanceRepository.findOpenByParty(
					access.tenantOrganizationId(), advanceType, partyId);
			if (openAdvances.isEmpty()) return null;
			advance = openAdvances.get(0);
		}

		BigDecimal available = advance.availableBalance();
		if (available.signum() <= 0) return null;

		BigDecimal applied = available.min(grossAmount).setScale(2, RoundingMode.HALF_UP);
		if (applied.signum() <= 0) return null;

		String fromStatus = advance.getStatus();
		String requestId = "advance-apply-" + invoiceId + "-" + advance.getId();
		AdvanceApplicationEntity application = advance.apply(invoiceId, invoiceNumber, applied,
				invoiceDate, requestId, access.userId());
		advanceRepository.saveAndFlush(advance);

		eventRepository.saveAndFlush(new AdvanceEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), advance.getId(), application.getId(), null, "APPLY",
				fromStatus, advance.getStatus(), requestId,
				Map.of("appliedAmount", applied, "invoiceNumber", invoiceNumber)));

		return new AdvanceApplicationResult(advance.getId(), advance.getAdvanceNumber(), applied);
	}

	public record AdvanceApplicationResult(UUID advanceId, String advanceNumber, BigDecimal appliedAmount) { }

	// --- private helpers ---

	private PartyInfo resolveParty(CurrentWorkspaceAccess access, String type, UUID partyId) {
		if ("RECEIVABLE".equals(type)) {
			CustomerReference customer = masterDataProvider.requireActiveCustomer(access.tenantOrganizationId(), partyId);
			return new PartyInfo("CUSTOMER", customer.id(), customer.code(), customer.name());
		} else {
			SupplierReference supplier = procurementProvider.findActiveSupplier(access.tenantOrganizationId(), partyId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "供应商不存在或未启用"));
			return new PartyInfo("SUPPLIER", supplier.id(), supplier.code(), supplier.name());
		}
	}

	private record PartyInfo(String partyType, UUID partyId, String partyCode, String partyName) { }

	private void lockBusinessKey(String key) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
				statement -> statement.setString(1, key), resultSet -> null);
	}

	private String nextAdvanceNumber(String prefix) {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.advance_number_seq')", Long.class);
		return prefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private AdvanceRecord toSummary(AdvanceEntity entity) {
		return new AdvanceRecord(entity.getId(), entity.getAdvanceNumber(), entity.getType(),
				entity.getPartyType(), entity.getPartyId(), entity.getPartyCode(), entity.getPartyName(),
				entity.getCurrency(), entity.getAdvanceDate(), entity.getTotalAmount(),
				entity.getAppliedAmount(), entity.getRefundedAmount(), entity.availableBalance(),
				entity.getStatus(), entity.getNote(), entity.getVersion(), entity.getCreatedAt(),
				List.of(), List.of());
	}

	private AdvanceRecord toDetail(AdvanceEntity entity) {
		List<AdvanceRecord.ApplicationRecord> apps = entity.getApplications().stream()
				.sorted((a, b) -> b.getApplicationDate().compareTo(a.getApplicationDate()))
				.map(a -> new AdvanceRecord.ApplicationRecord(a.getId(), a.getInvoiceId(),
						a.getInvoiceNumber(), a.getAppliedAmount(), a.getApplicationDate(), a.getCreatedAt()))
				.toList();
		List<AdvanceRecord.RefundRecord> refs = entity.getRefunds().stream()
				.sorted((a, b) -> b.getRefundDate().compareTo(a.getRefundDate()))
				.map(r -> new AdvanceRecord.RefundRecord(r.getId(), r.getRefundAmount(),
						r.getRefundDate(), r.getReason(), r.getCreatedAt()))
				.toList();
		return new AdvanceRecord(entity.getId(), entity.getAdvanceNumber(), entity.getType(),
				entity.getPartyType(), entity.getPartyId(), entity.getPartyCode(), entity.getPartyName(),
				entity.getCurrency(), entity.getAdvanceDate(), entity.getTotalAmount(),
				entity.getAppliedAmount(), entity.getRefundedAmount(), entity.availableBalance(),
				entity.getStatus(), entity.getNote(), entity.getVersion(), entity.getCreatedAt(),
				apps, refs);
	}

	private static void requireWriteRole(CurrentWorkspaceAccess access, String action) {
		if (!WRITE_ROLES.contains(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权" + action);
		}
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }

	private static String normalizeType(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!TYPES.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的预收预付类型");
		return normalized;
	}

	private static String normalizeStatus(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的预收预付状态");
		return normalized;
	}

	private static UUID parsePartyId(String value) {
		if (value == null || value.isBlank()) return null;
		try { return UUID.fromString(value.trim()); }
		catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户/供应商 ID 格式无效"); }
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
}
