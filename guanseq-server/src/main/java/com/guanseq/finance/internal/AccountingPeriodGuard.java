package com.guanseq.finance.internal;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会计期间守卫：在财务写操作前校验单据日期所属期间是否为 OPEN。
 * 被 ReceivableApplicationService、PayableApplicationService 和
 * OrderProfitApplicationService 共享注入。
 */
@Component
public class AccountingPeriodGuard {

	private final AccountingPeriodRepository periodRepository;

	AccountingPeriodGuard(AccountingPeriodRepository periodRepository) {
		this.periodRepository = periodRepository;
	}

	/**
	 * 校验指定租户在业务日期所在月份的会计期间是否为 OPEN。
	 * 若期间不存在则用当前操作人身份自动创建（自动补建当前年和前后一年的期间）。
	 * 若期间已关账则抛出 409 PERIOD_CLOSED（响应头含 X-Period-Label 和 X-Error-Code）。
	 */
	void requireOpen(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, LocalDate businessDate) {
		if (businessDate == null) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "业务日期不能为空");
		}
		int year = businessDate.getYear();
		int month = businessDate.getMonthValue();
		AccountingPeriodEntity period = periodRepository
				.findByTenantOrganizationIdAndFiscalYearAndFiscalPeriod(tenantOrganizationId, year, month)
				.orElseGet(() -> createPeriod(tenantOrganizationId, workspaceId, actorUserId, year, month));
		if ("CLOSED".equals(period.getStatus())) {
			String label = year + "-" + String.format("%02d", month);
			throw new PeriodClosedException(label);
		}
	}

	private AccountingPeriodEntity createPeriod(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId,
			int year, int month) {
		if (year < 2020 || year > 2099) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"会计期间未初始化：日期超出支持范围（2020-2099）");
		}
		UUID actor = actorUserId != null ? actorUserId : tenantOrganizationId;
		AccountingPeriodEntity entity = new AccountingPeriodEntity(UUID.randomUUID(), tenantOrganizationId,
				workspaceId, year, month, actor,
				"auto-period-" + year + "-" + String.format("%02d", month));
		try {
			return periodRepository.save(entity);
		} catch (DataIntegrityViolationException e) {
			// 并发创建：另一个事务已经插入了同一期间，重新查询即可
			return periodRepository
					.findByTenantOrganizationIdAndFiscalYearAndFiscalPeriod(tenantOrganizationId, year, month)
					.orElseThrow(() -> e);
		}
	}
}
