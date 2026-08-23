package com.guanseq.finance.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.finance.api.AccountingPeriodRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class AccountingPeriodApplicationService {

	private static final String ADMIN = "ADMIN";
	private static final String FINANCE_MANAGER = "FINANCE_MANAGER";

	private final CurrentWorkspaceProvider workspaceProvider;
	private final AccountingPeriodRepository periodRepository;

	AccountingPeriodApplicationService(CurrentWorkspaceProvider workspaceProvider,
			AccountingPeriodRepository periodRepository) {
		this.workspaceProvider = workspaceProvider;
		this.periodRepository = periodRepository;
	}

	@Transactional(readOnly = true)
	public List<AccountingPeriodRecord> list(String username, Integer year) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		int targetYear = year != null ? year : java.time.LocalDate.now().getYear();
		ensureNextYearExists(access, targetYear);
		return periodRepository
				.findByTenantOrganizationIdAndFiscalYearOrderByFiscalPeriodAsc(access.tenantOrganizationId(), targetYear)
				.stream().map(this::toRecord).toList();
	}

	@Transactional(readOnly = true)
	public AccountingPeriodRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(findPeriod(access, id));
	}

	@Transactional
	public AccountingPeriodRecord create(String username, String requestIdHeader,
			AccountingPeriodRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "创建会计期间");
		if (request.fiscalYear() < 2020 || request.fiscalYear() > 2099) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "年份必须在 2020-2099 之间");
		}
		if (request.fiscalPeriod() < 1 || request.fiscalPeriod() > 12) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "月份必须在 1-12 之间");
		}
		periodRepository.findByTenantOrganizationIdAndFiscalYearAndFiscalPeriod(
				access.tenantOrganizationId(), request.fiscalYear(), request.fiscalPeriod()).ifPresent(p -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"会计期间 " + request.fiscalYear() + "-" + String.format("%02d", request.fiscalPeriod()) + " 已存在");
				});
		AccountingPeriodEntity entity = new AccountingPeriodEntity(UUID.randomUUID(),
				access.tenantOrganizationId(), access.workspaceId(), request.fiscalYear(), request.fiscalPeriod(),
				access.userId(), normalizeRequestId(requestIdHeader, "accounting-period"));
		try {
			periodRepository.save(entity);
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "会计期间已存在（并发冲突）");
		}
		return toRecord(entity);
	}

	@Transactional
	public AccountingPeriodRecord close(String username, String requestIdHeader, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access, "关账");
		AccountingPeriodEntity period = findPeriod(access, id);
		String requestId = normalizeRequestId(requestIdHeader, "period-close");
		if (period.getRequestId() != null && period.getRequestId().equals(requestId)) {
			return toRecord(period);
		}
		if ("CLOSED".equals(period.getStatus())) {
			return toRecord(period);
		}
		period.close(access.userId());
		period.setRequestId(requestId);
		try {
			periodRepository.save(period);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "期间已被其他操作修改，请刷新后重试");
		}
		return toRecord(period);
	}

	@Transactional
	public AccountingPeriodRecord reopen(String username, String requestIdHeader, UUID id,
			AccountingPeriodRecord.ReopenRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireAdmin(access, "重开会计期间");
		if (request.reason() == null || request.reason().trim().length() < 4) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "重开原因不能少于 4 个字符");
		}
		AccountingPeriodEntity period = findPeriod(access, id);
		if (!"CLOSED".equals(period.getStatus())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "期间未关账，无需重开");
		}
		if (request.expectedVersion() != null && period.getVersion() != request.expectedVersion()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "期间已被其他操作修改，请刷新后重试");
		}
		period.reopen(access.userId(), request.reason().trim());
		period.setRequestId(normalizeRequestId(requestIdHeader, "period-reopen"));
		try {
			periodRepository.save(period);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "期间已被其他操作修改，请刷新后重试");
		}
		return toRecord(period);
	}

	private void ensureNextYearExists(CurrentWorkspaceAccess access, int year) {
		for (int y = year; y <= year + 1; y++) {
			List<AccountingPeriodEntity> existing = periodRepository
					.findByTenantOrganizationIdAndFiscalYearOrderByFiscalPeriodAsc(access.tenantOrganizationId(), y);
			if (existing.isEmpty()) {
				for (int m = 1; m <= 12; m++) {
					AccountingPeriodEntity p = new AccountingPeriodEntity(UUID.randomUUID(),
							access.tenantOrganizationId(), access.workspaceId(), y, m, access.userId(),
							"auto-period-gen-" + y + "-" + String.format("%02d", m));
					try {
						periodRepository.save(p);
					} catch (org.springframework.dao.DataIntegrityViolationException ignored) {
						// 并发创建，忽略
					}
				}
			}
		}
	}

	private AccountingPeriodEntity findPeriod(CurrentWorkspaceAccess access, UUID id) {
		return periodRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会计期间不存在"));
	}

	private AccountingPeriodRecord toRecord(AccountingPeriodEntity e) {
		return new AccountingPeriodRecord(
				e.getId(),
				e.getFiscalYear(),
				e.getFiscalPeriod(),
				e.getFiscalYear() + "-" + String.format("%02d", e.getFiscalPeriod()),
				e.getStatus(),
				e.getClosedAt(),
				e.getClosedBy() != null ? e.getClosedBy().toString() : null,
				e.getReopenedAt(),
				e.getReopenedBy() != null ? e.getReopenedBy().toString() : null,
				e.getReopenReason(),
				e.getVersion(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	private static void requireWriteRole(CurrentWorkspaceAccess access, String action) {
		String role = access.roleCode();
		if (!ADMIN.equals(role) && !FINANCE_MANAGER.equals(role)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权" + action);
		}
	}

	private static void requireAdmin(CurrentWorkspaceAccess access, String action) {
		if (!ADMIN.equals(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可" + action);
		}
	}

	private static String normalizeRequestId(String header, String prefix) {
		if (header != null && !header.isBlank()) return header;
		return prefix + "-" + UUID.randomUUID();
	}
}
