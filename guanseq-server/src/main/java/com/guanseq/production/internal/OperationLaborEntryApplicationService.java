package com.guanseq.production.internal;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.production.api.OperationLaborEntryPage;
import com.guanseq.production.api.OperationLaborEntryRecord;

@Service
public class OperationLaborEntryApplicationService {
	private static final Set<String> READ_ROLES = Set.of("PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "FINANCE_MANAGER", "ADMIN");
	private static final Set<String> RECORD_ROLES = Set.of("PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> APPROVAL_ROLES = Set.of("PRODUCTION_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final OperationTaskRepository taskRepository;
	private final OperationLaborEntryRepository entryRepository;
	private final OperationLaborEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	OperationLaborEntryApplicationService(CurrentWorkspaceProvider workspaceProvider, OperationTaskRepository taskRepository,
			OperationLaborEntryRepository entryRepository, OperationLaborEventRepository eventRepository,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.taskRepository = taskRepository;
		this.entryRepository = entryRepository; this.eventRepository = eventRepository; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public OperationLaborEntryPage list(String username, String query, String status, UUID taskId, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access, READ_ROLES, "当前角色无权查看实际人工工时");
		var result = entryRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status), taskId,
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Order.desc("workDate"), Sort.Order.desc("createdAt"))));
		return new OperationLaborEntryPage(result.getContent().stream().map(this::toRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public OperationLaborEntryRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access, READ_ROLES, "当前角色无权查看实际人工工时");
		return toRecord(requireEntry(access, id));
	}

	@Transactional
	public OperationLaborEntryRecord create(String username, OperationLaborEntryRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access, RECORD_ROLES, "当前角色无权登记实际人工工时");
		String requestId = currentRequestId("operation-labor-record-");
		var duplicate = entryRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) {
			if (!duplicate.get().getTaskId().equals(request.taskId())) throw conflict("请求编号已用于其他工时记录，请刷新后重试");
			return toRecord(duplicate.get());
		}
		OperationTaskEntity task = requireTask(access, request.taskId());
		if (!Set.of("IN_PROGRESS", "COMPLETED").contains(task.getStatus()))
			throw unprocessable("只有执行中或已完工工序可以登记实际人工工时");
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		if (request.workDate().isAfter(today)) throw unprocessable("工时日期不能晚于当前日期");
		if (task.getStartedAt() != null && request.workDate().isBefore(task.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate()))
			throw unprocessable("工时日期不能早于工序开工日期");
		String shiftName = requireText(request.shiftName(), "工时班次不能为空");
		String operatorName = requireText(request.operatorName(), "操作人不能为空");
		OperationLaborEntryEntity entry = new OperationLaborEntryEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextEntryNumber(), task, request.workDate(),
				shiftName, operatorName, request.actualMinutes(), trimToNull(request.note()), requestId, access.userId());
		try {
			entryRepository.saveAndFlush(entry);
			eventRepository.saveAndFlush(new OperationLaborEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), entry.getId(), entry.getTaskId(), entry.getOrderId(), "RECORDED", null, "RECORDED",
					requestId, trimToNull(request.note()), Map.of("operatorName", operatorName, "shiftName", shiftName,
							"workDate", request.workDate().toString(), "actualMinutes", request.actualMinutes())));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("工时登记请求编号或业务编号冲突，请刷新确认结果");
		}
		return toRecord(entry);
	}

	@Transactional
	public OperationLaborEntryRecord action(String username, UUID id, OperationLaborEntryRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access, APPROVAL_ROLES, "当前角色无权审核或冲销实际人工工时");
		String requestId = currentRequestId("operation-labor-action-");
		var duplicateEvent = eventRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicateEvent.isPresent()) {
			if (!duplicateEvent.get().getEntryId().equals(id)) throw conflict("请求编号已用于其他工时动作，请刷新后重试");
			return toRecord(requireEntry(access, id));
		}
		OperationLaborEntryEntity entry = requireEntry(access, id);
		if (entry.getVersion() != request.expectedVersion()) throw conflict("工时记录已被其他用户更新，请刷新后重试");
		String from = entry.getStatus();
		String comment = trimToNull(request.reason());
		try {
			switch (request.action()) {
				case "APPROVE" -> {
					OperationTaskEntity task = requireTask(access, entry.getTaskId());
					if (!"COMPLETED".equals(task.getStatus())) throw new IllegalStateException("只有工序已完工后才能审核实际人工工时");
					entry.approve(requestId, access.userId());
					eventRepository.save(new OperationLaborEventEntity(access.tenantOrganizationId(), access.workspaceId(),
							access.userId(), entry.getId(), entry.getTaskId(), entry.getOrderId(), "APPROVED", from, "APPROVED",
							requestId, comment, Map.of("actualMinutes", entry.getActualMinutes(), "operatorName", entry.getOperatorName())));
				}
				case "VOID" -> {
					String reason = requireText(request.reason(), "冲销工时必须填写原因");
					entry.voidEntry(reason, requestId, access.userId());
					eventRepository.save(new OperationLaborEventEntity(access.tenantOrganizationId(), access.workspaceId(),
							access.userId(), entry.getId(), entry.getTaskId(), entry.getOrderId(), "VOIDED", from, "VOIDED",
							requestId, reason, Map.of("actualMinutes", entry.getActualMinutes(), "previousStatus", from)));
				}
				default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的工时动作");
			}
			entryRepository.saveAndFlush(entry); eventRepository.flush();
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw unprocessable(exception.getMessage());
		} catch (DataIntegrityViolationException exception) {
			throw conflict("工时动作请求编号冲突，请刷新确认结果");
		}
		return toRecord(entry);
	}

	private OperationLaborEntryRecord toRecord(OperationLaborEntryEntity entry) {
		List<OperationLaborEventEntity> events = eventRepository.findByEntryIdOrderByOccurredAtDesc(entry.getId());
		return new OperationLaborEntryRecord(entry.getId(), entry.getEntryNumber(), entry.getTaskId(), entry.getTaskNumber(),
				entry.getOrderId(), entry.getOrderNumber(), entry.getOperationCode(), entry.getOperationName(),
				entry.getWorkCenterCode(), entry.getWorkCenterName(), entry.getWorkDate(), entry.getShiftName(),
				entry.getOperatorName(), entry.getActualMinutes(), entry.getStatus(), entry.getNote(), entry.getApprovedBy(),
				entry.getApprovedAt(), entry.getVoidedBy(), entry.getVoidedAt(), entry.getVoidReason(), entry.getVersion(),
				entry.getCreatedAt(), entry.getUpdatedAt(), events.stream().map(event -> new OperationLaborEntryRecord.Event(
						event.getId(), event.getAction(), event.getFromStatus(), event.getToStatus(), event.getRequestId(),
						event.getComment(), event.getOccurredAt())).toList());
	}

	private OperationTaskEntity requireTask(CurrentWorkspaceAccess access, UUID id) {
		return taskRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工序任务不存在或不在当前租户范围"));
	}
	private OperationLaborEntryEntity requireEntry(CurrentWorkspaceAccess access, UUID id) {
		return entryRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工时记录不存在或不在当前租户范围"));
	}
	private String nextEntryNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('production.operation_labor_entry_number_seq')", Long.class);
		return "LAB-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}
	private static void requireRole(CurrentWorkspaceAccess access, Set<String> roles, String message) {
		if (!roles.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
	}
	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		return value.trim();
	}
	private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
	private static String normalizeStatus(String value) {
		return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase();
	}
	private static String currentRequestId(String prefix) {
		String requestId = MDC.get("requestId");
		return requestId == null || requestId.isBlank() ? prefix + UUID.randomUUID() : requestId;
	}
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private static ResponseStatusException unprocessable(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}
