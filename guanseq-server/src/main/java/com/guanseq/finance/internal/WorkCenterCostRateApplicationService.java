package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.finance.api.WorkCenterCostRatePage;
import com.guanseq.finance.api.WorkCenterCostRateRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class WorkCenterCostRateApplicationService {

	private static final Set<String> WRITE_ROLES = Set.of("ADMIN", "FINANCE_MANAGER");
	private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final WorkCenterCostRateRepository rateRepository;
	private final WorkCenterCostRateEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	WorkCenterCostRateApplicationService(CurrentWorkspaceProvider workspaceProvider,
			WorkCenterCostRateRepository rateRepository, WorkCenterCostRateEventRepository eventRepository,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.rateRepository = rateRepository;
		this.eventRepository = eventRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public WorkCenterCostRatePage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = rateRepository.search(access.tenantOrganizationId(), access.operatingOrganizationId(), normalize(query),
				normalizeStatus(status), PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Order.desc("effectiveDate"), Sort.Order.asc("workCenterCode"))));
		return new WorkCenterCostRatePage(result.getContent().stream().map(this::toRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional
	public WorkCenterCostRateRecord create(String username, String requestIdHeader,
			WorkCenterCostRateRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "发布工作中心成本费率");
		String requestId = normalizeRequestId(requestIdHeader, "work-center-cost-rate");
		WorkCenterCostRateEntity duplicate = rateRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		BigDecimal laborRate = request.laborRatePerHour().setScale(6, RoundingMode.HALF_UP);
		BigDecimal overheadRate = request.overheadRatePerHour().setScale(6, RoundingMode.HALF_UP);
		if (laborRate.add(overheadRate).signum() <= 0) throw invalid("人工与制造费用小时费率之和必须大于零");
		String workCenterCode = request.workCenterCode().trim().toUpperCase();
		lockBusinessKey("work-center-cost-rate:" + access.tenantOrganizationId() + ":"
				+ access.operatingOrganizationId() + ":" + workCenterCode + ":" + request.effectiveDate());
		duplicate = rateRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(duplicate);
		if (rateRepository.findByTenantOrganizationIdAndOwningOrganizationIdAndWorkCenterCodeAndEffectiveDate(
				access.tenantOrganizationId(), access.operatingOrganizationId(), workCenterCode, request.effectiveDate()).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该工作中心在此生效日期已经存在成本费率");
		}
		WorkCenterCostRateEntity rate = new WorkCenterCostRateEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), workCenterCode, request.workCenterName(),
				request.currency(), laborRate, overheadRate, request.effectiveDate(), requestId, access.userId());
		try {
			rateRepository.saveAndFlush(rate);
			eventRepository.saveAndFlush(new WorkCenterCostRateEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), rate.getId(), "PUBLISH", null, rate.getStatus(), requestId,
					Map.of("workCenterCode", workCenterCode, "effectiveDate", request.effectiveDate().toString(),
							"laborRatePerHour", laborRate, "overheadRatePerHour", overheadRate)));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "成本费率发布请求与已有业务事实冲突，请刷新后确认", exception);
		}
		return toRecord(rate);
	}

	@Transactional
	public WorkCenterCostRateRecord changeStatus(String username, UUID id, String requestIdHeader,
			WorkCenterCostRateRecord.StatusRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "变更工作中心成本费率状态");
		String requestId = normalizeRequestId(requestIdHeader, "work-center-cost-rate-status");
		WorkCenterCostRateEventEntity duplicate = eventRepository
				.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicate != null) return toRecord(findRate(access, duplicate.getRateId()));
		lockBusinessKey("work-center-cost-rate-status:" + id);
		WorkCenterCostRateEntity rate = findRate(access, id);
		if (rate.getVersion() != request.expectedVersion()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "成本费率已被其他事务更新，请刷新后重试");
		}
		String target = request.status().trim().toUpperCase();
		if (target.equals(rate.getStatus())) throw invalid("成本费率已经处于" + target + "状态");
		String from = rate.getStatus();
		rate.changeStatus(target, access.userId());
		try {
			rateRepository.saveAndFlush(rate);
			eventRepository.saveAndFlush(new WorkCenterCostRateEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), rate.getId(), "CHANGE_STATUS", from, target, requestId,
					Map.of("workCenterCode", rate.getWorkCenterCode(), "effectiveDate", rate.getEffectiveDate().toString())));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "成本费率已被其他事务更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "成本费率状态请求与已有业务事实冲突，请刷新后确认", exception);
		}
		return toRecord(rate);
	}

	private WorkCenterCostRateEntity findRate(CurrentWorkspaceAccess access, UUID id) {
		return rateRepository.findByIdAndTenantOrganizationIdAndOwningOrganizationId(
				id, access.tenantOrganizationId(), access.operatingOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工作中心成本费率不存在或不可见"));
	}

	private WorkCenterCostRateRecord toRecord(WorkCenterCostRateEntity rate) {
		return new WorkCenterCostRateRecord(rate.getId(), rate.getWorkCenterCode(), rate.getWorkCenterName(),
				rate.getCurrency(), rate.getLaborRatePerHour(), rate.getOverheadRatePerHour(),
				rate.getLaborRatePerHour().add(rate.getOverheadRatePerHour()).setScale(6, RoundingMode.HALF_UP),
				rate.getEffectiveDate(), rate.getStatus(), rate.getVersion(), rate.getCreatedAt(), rate.getUpdatedAt());
	}

	private void lockBusinessKey(String key) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
				statement -> statement.setString(1, key), resultSet -> null);
	}

	private static void requireWriteRole(CurrentWorkspaceAccess access, String action) {
		if (!WRITE_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权" + action);
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的成本费率状态");
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
