package com.guanseq.equipment.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;

@Service
public class EquipmentTelemetryRetentionAutomationService {

	private static final Logger log = LoggerFactory.getLogger(EquipmentTelemetryRetentionAutomationService.class);
	private static final int DELETE_BATCH_SIZE = 10_000;
	private static final int LEASE_MINUTES = 5;
	private static final int PARTIAL_RETRY_MINUTES = 1;
	private static final int BASE_FAILURE_RETRY_MINUTES = 15;
	private static final int MAX_FAILURE_RETRY_MINUTES = 360;

	private final EquipmentTelemetryRetentionPolicyRepository policyRepository;
	private final EquipmentTelemetrySampleRepository sampleRepository;
	private final EquipmentTelemetryRetentionRunRepository runRepository;
	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactions;
	private final boolean schedulerAvailable;
	private final String instanceId = "guanseq-" + UUID.randomUUID();

	EquipmentTelemetryRetentionAutomationService(
			EquipmentTelemetryRetentionPolicyRepository policyRepository,
			EquipmentTelemetrySampleRepository sampleRepository,
			EquipmentTelemetryRetentionRunRepository runRepository,
			JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager,
			@Value("${guanseq.telemetry.retention-scheduler-enabled:false}") boolean schedulerAvailable) {
		this.policyRepository = policyRepository;
		this.sampleRepository = sampleRepository;
		this.runRepository = runRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.transactions = new TransactionTemplate(transactionManager);
		this.schedulerAvailable = schedulerAvailable;
	}

	boolean isSchedulerAvailable() {
		return schedulerAvailable;
	}

	ExecutionOutcome runNow(CurrentWorkspaceAccess access, long expectedVersion, String reason, String requestId) {
		if (!schedulerAvailable) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"设备样本自动清理运行器未启用，请先完成部署配置");
		}
		EquipmentTelemetryRetentionRunEntity replay = runRepository
				.findByTenantOrganizationIdAndWorkspaceIdAndRequestId(
						access.tenantOrganizationId(), access.workspaceId(), requestId).orElse(null);
		if (replay != null) return new ExecutionOutcome(replay, true);
		return execute(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				"USER_RETRY", expectedVersion, false, reason, requestId);
	}

	public void dispatchDuePolicies() {
		if (!schedulerAvailable) return;
		Instant now = Instant.now();
		for (EquipmentTelemetryRetentionPolicyEntity policy : policyRepository
				.findTop100ByAutomaticCleanupEnabledTrueAndNextCleanupAtLessThanEqualOrderByNextCleanupAtAsc(now)) {
			String requestId = "retention-scheduled-" + UUID.randomUUID();
			try {
				execute(policy.getTenantOrganizationId(), policy.getWorkspaceId(), null,
						"SCHEDULED", null, true, "按工作区保留策略自动清理", requestId);
			} catch (LeaseBusyException | RunSkippedException ignored) {
				// Another instance owns the workspace lease, or the policy changed after the due scan.
			} catch (RuntimeException exception) {
				log.error("Telemetry retention dispatch could not persist an outcome: policyId={}, errorType={}",
						policy.getId(), exception.getClass().getSimpleName());
			}
		}
	}

	private ExecutionOutcome execute(UUID tenantId, UUID workspaceId, UUID initiatedBy,
			String triggerType, Long expectedVersion, boolean requireDue, String reason, String requestId) {
		String leaseOwner = leaseOwner(requestId);
		if (!acquireLease(tenantId, workspaceId, leaseOwner)) {
			throw new LeaseBusyException();
		}
		Instant startedAt = Instant.now();
		try {
			EquipmentTelemetryRetentionRunEntity run = transactions.execute(status -> {
				EquipmentTelemetryRetentionPolicyEntity policy = policyRepository.findForUpdate(tenantId, workspaceId)
						.orElseThrow(() -> new RunSkippedException("保留策略不存在"));
				if (!policy.isAutomaticCleanupEnabled()) {
					if (requireDue) throw new RunSkippedException("策略已关闭自动清理");
					throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "当前工作区尚未启用自动清理");
				}
				if (expectedVersion != null && policy.getVersion() != expectedVersion) {
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"设备样本保留策略已被其他用户修改，请刷新后重试");
				}
				Instant now = Instant.now();
				if (requireDue && (policy.getNextCleanupAt() == null || policy.getNextCleanupAt().isAfter(now))) {
					throw new RunSkippedException("策略尚未到期");
				}
				Instant cutoffAt = now.minus(policy.getRetentionDays(), ChronoUnit.DAYS);
				long deleted = sampleRepository.deleteExpiredBatch(tenantId, workspaceId,
						cutoffAt, DELETE_BATCH_SIZE);
				long remaining = sampleRepository
						.countByTenantOrganizationIdAndWorkspaceIdAndReceivedAtBefore(tenantId, workspaceId, cutoffAt);
				Instant completedAt = Instant.now();
				EquipmentTelemetryRetentionRunEntity completed = EquipmentTelemetryRetentionRunEntity.completed(
						policy, triggerType, initiatedBy, instanceId, requestId, reason, cutoffAt,
						deleted, remaining, startedAt, completedAt);
				Instant next = remaining > 0 ? completedAt.plus(PARTIAL_RETRY_MINUTES, ChronoUnit.MINUTES)
						: completedAt.plus(policy.getCleanupIntervalHours(), ChronoUnit.HOURS);
				policy.automationSucceeded(completed.getStatus(), completedAt, next);
				policyRepository.save(policy);
				runRepository.saveAndFlush(completed);
				releaseLease(tenantId, workspaceId, leaseOwner);
				return completed;
			});
			if (run == null) throw new IllegalStateException("自动清理事务未返回结果");
			return new ExecutionOutcome(run, false);
		} catch (RunSkippedException | ResponseStatusException exception) {
			releaseLeaseSafely(tenantId, workspaceId, leaseOwner);
			throw exception;
		} catch (RuntimeException failure) {
			EquipmentTelemetryRetentionRunEntity failed = recordFailure(tenantId, workspaceId,
					initiatedBy, triggerType, requestId, reason, startedAt, leaseOwner, failure);
			if (failed == null) throw failure;
			return new ExecutionOutcome(failed, false);
		}
	}

	private EquipmentTelemetryRetentionRunEntity recordFailure(UUID tenantId, UUID workspaceId,
			UUID initiatedBy, String triggerType, String requestId, String reason, Instant startedAt,
			String leaseOwner, RuntimeException failure) {
		try {
			return transactions.execute(status -> {
				EquipmentTelemetryRetentionPolicyEntity policy = policyRepository.findForUpdate(tenantId, workspaceId)
						.orElse(null);
				if (policy == null || !policy.isAutomaticCleanupEnabled()) {
					releaseLease(tenantId, workspaceId, leaseOwner);
					return null;
				}
				Instant completedAt = Instant.now();
				Instant cutoffAt = completedAt.minus(policy.getRetentionDays(), ChronoUnit.DAYS);
				int retryMinutes = failureRetryMinutes(policy.getConsecutiveFailures());
				policy.automationFailed(completedAt, completedAt.plus(retryMinutes, ChronoUnit.MINUTES));
				EquipmentTelemetryRetentionRunEntity failedRun = EquipmentTelemetryRetentionRunEntity.failed(
						policy, triggerType, initiatedBy, instanceId, requestId, reason, cutoffAt,
						failureCode(failure), "自动清理未完成，系统将在退避时间后重试",
						startedAt, completedAt);
				policyRepository.save(policy);
				runRepository.saveAndFlush(failedRun);
				releaseLease(tenantId, workspaceId, leaseOwner);
				return failedRun;
			});
		} catch (DataIntegrityViolationException duplicate) {
			return runRepository.findByTenantOrganizationIdAndWorkspaceIdAndRequestId(
					tenantId, workspaceId, requestId).orElse(null);
		} catch (RuntimeException persistenceFailure) {
			log.error("Telemetry retention failure evidence could not be persisted: workspaceId={}, errorType={}",
					workspaceId, persistenceFailure.getClass().getSimpleName());
			releaseLeaseSafely(tenantId, workspaceId, leaseOwner);
			return null;
		}
	}

	private boolean acquireLease(UUID tenantId, UUID workspaceId, String ownerId) {
		Instant acquiredAt = Instant.now();
		Boolean acquired = transactions.execute(status -> jdbcTemplate.update("""
				insert into equipment.telemetry_retention_leases
				  (tenant_organization_id, workspace_id, owner_id, acquired_at, lease_until)
				values (?, ?, ?, ?, ?)
				on conflict (tenant_organization_id, workspace_id) do update
				set owner_id = excluded.owner_id,
				    acquired_at = excluded.acquired_at,
				    lease_until = excluded.lease_until
				where equipment.telemetry_retention_leases.lease_until <= excluded.acquired_at
				""", tenantId, workspaceId, ownerId, Timestamp.from(acquiredAt),
					Timestamp.from(acquiredAt.plus(LEASE_MINUTES, ChronoUnit.MINUTES))) == 1);
		return Boolean.TRUE.equals(acquired);
	}

	private void releaseLeaseSafely(UUID tenantId, UUID workspaceId, String ownerId) {
		try {
			transactions.executeWithoutResult(status -> releaseLease(tenantId, workspaceId, ownerId));
		} catch (RuntimeException exception) {
			log.warn("Telemetry retention lease will expire naturally: workspaceId={}, errorType={}",
					workspaceId, exception.getClass().getSimpleName());
		}
	}

	private void releaseLease(UUID tenantId, UUID workspaceId, String ownerId) {
		jdbcTemplate.update("""
				delete from equipment.telemetry_retention_leases
				where tenant_organization_id = ? and workspace_id = ? and owner_id = ?
				""", tenantId, workspaceId, ownerId);
	}

	private String leaseOwner(String requestId) {
		String owner = instanceId + ":" + requestId;
		return owner.length() <= 160 ? owner : owner.substring(0, 160);
	}

	private static int failureRetryMinutes(int consecutiveFailures) {
		long multiplier = 1L << Math.min(5, Math.max(0, consecutiveFailures));
		return (int) Math.min(MAX_FAILURE_RETRY_MINUTES, BASE_FAILURE_RETRY_MINUTES * multiplier);
	}

	private static String failureCode(RuntimeException failure) {
		return failure instanceof org.springframework.dao.DataAccessException
				? "DATABASE_OPERATION_FAILED" : "RETENTION_CLEANUP_FAILED";
	}

	record ExecutionOutcome(EquipmentTelemetryRetentionRunEntity run, boolean replayed) { }

	private static final class LeaseBusyException extends ResponseStatusException {
		private LeaseBusyException() {
			super(HttpStatus.CONFLICT, "当前工作区已有样本清理任务运行中，请稍后刷新");
		}
	}

	private static final class RunSkippedException extends RuntimeException {
		private RunSkippedException(String message) { super(message); }
	}
}
