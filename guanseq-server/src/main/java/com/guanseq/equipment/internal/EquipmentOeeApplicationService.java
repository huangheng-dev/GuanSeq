package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.equipment.api.EquipmentOeePage;
import com.guanseq.equipment.api.EquipmentOeeRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentOeeApplicationService {

	private static final Set<String> MAINTAIN_ROLES = Set.of("MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> APPROVE_ROLES = Set.of("PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> STATUSES = Set.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED");
	private static final Set<String> DOWNTIME_CATEGORIES = Set.of("EQUIPMENT_FAILURE", "SETUP_CHANGEOVER",
			"MATERIAL_WAIT", "QUALITY_HOLD", "PERSONNEL_WAIT", "PLANNED_MAINTENANCE", "OTHER");
	private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentOeeRecordRepository recordRepository;
	private final EquipmentOeeDowntimeRepository downtimeRepository;
	private final EquipmentOeeEventRepository eventRepository;
	private final EquipmentAssetRepository assetRepository;

	EquipmentOeeApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentOeeRecordRepository recordRepository, EquipmentOeeDowntimeRepository downtimeRepository,
			EquipmentOeeEventRepository eventRepository, EquipmentAssetRepository assetRepository) {
		this.workspaceProvider = workspaceProvider;
		this.recordRepository = recordRepository;
		this.downtimeRepository = downtimeRepository;
		this.eventRepository = eventRepository;
		this.assetRepository = assetRepository;
	}

	@Transactional(readOnly = true)
	public EquipmentOeePage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalizedStatus = normalizeStatus(status);
		var result = recordRepository.search(access.tenantOrganizationId(), access.workspaceId(), normalizeQuery(query),
				normalizedStatus, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "windowStart")));
		List<EquipmentOeeRecordEntity> approved = recordRepository.findByTenantOrganizationIdAndWorkspaceIdAndStatus(
				access.tenantOrganizationId(), access.workspaceId(), "APPROVED");
		return new EquipmentOeePage(result.getContent().stream().map(record -> toRecord(access, record, false)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(), approved.size(),
				average(approved, Metric.AVAILABILITY), average(approved, Metric.PERFORMANCE),
				average(approved, Metric.QUALITY), average(approved, Metric.OEE), canMaintain(access), canApprove(access));
	}

	@Transactional(readOnly = true)
	public EquipmentOeeRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(access, requireRecord(access, id), true);
	}

	@Transactional
	public EquipmentOeeRecord create(String username, EquipmentOeeRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintain(access);
		String requestId = requestId();
		var replay = recordRepository.findByTenantOrganizationIdAndCreationRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return toRecord(access, replay.get(), true);
		validateFacts(request.windowStart(), request.windowEnd(), request.plannedProductionMinutes(),
				request.idealCycleSeconds(), request.totalCount(), request.goodCount());
		EquipmentAssetEntity asset = assetRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(request.assetId(),
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> notFound("设备不存在或不在当前工作区范围"));
		EquipmentOeeRecordEntity record = new EquipmentOeeRecordEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextNumber(), asset, request.windowStart(),
				request.windowEnd(), request.plannedProductionMinutes(), request.idealCycleSeconds(), request.totalCount(),
				request.goodCount(), request.shiftName(), request.productionReference(), request.sourceReference(),
				requestId, access.userId());
		try {
			recordRepository.saveAndFlush(record);
			eventRepository.saveAndFlush(new EquipmentOeeEventEntity(record, access.userId(), "CREATED", null,
					"DRAFT", request.reason(), requestId, Map.of("sourceType", "MANUAL_VERIFIED")));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OEE 记录编号或创建请求已存在，请刷新后确认", exception);
		}
		return toRecord(access, record, true);
	}

	@Transactional
	public EquipmentOeeRecord act(String username, UUID id, EquipmentOeeRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		EquipmentOeeRecordEntity record = requireRecord(access, id);
		String requestId = requestId();
		String eventAction = eventAction(request.action());
		var replay = eventRepository.findByOeeRecordIdAndRequestId(id, requestId);
		if (replay.isPresent()) {
			if (!eventAction.equals(replay.get().getAction())) throw conflict("请求编号已用于其他 OEE 动作");
			return toRecord(access, record, true);
		}
		requireVersion(record.getVersion(), request.expectedVersion());
		String from = record.getStatus();
		Map<String, Object> details = Map.of();
		try {
			switch (request.action()) {
				case "UPDATE" -> {
					requireMaintain(access); requireEditable(record);
					requireUpdateFields(request);
					List<EquipmentOeeDowntimeEntity> downtimes = downtimeRepository.findByOeeRecordIdOrderByStartedAt(id);
					validateFacts(request.windowStart(), request.windowEnd(), request.plannedProductionMinutes(),
							request.idealCycleSeconds(), request.totalCount(), request.goodCount());
					validateExistingDowntimes(downtimes, request.windowStart(), request.windowEnd(),
							request.plannedProductionMinutes());
					record.updateFacts(request.windowStart(), request.windowEnd(), request.plannedProductionMinutes(),
							request.idealCycleSeconds(), request.totalCount(), request.goodCount(), request.shiftName(),
							request.productionReference(), request.sourceReference(), totalDowntime(downtimes), access.userId());
				}
				case "ADD_DOWNTIME" -> {
					requireMaintain(access); requireEditable(record); requireDowntimeFields(request);
					List<EquipmentOeeDowntimeEntity> existing = downtimeRepository.findByOeeRecordIdOrderByStartedAt(id);
					validateDowntime(record, existing, null, request.downtimeStartedAt(), request.downtimeEndedAt(),
							request.reasonCategory(), request.responsibleParty(), request.description());
					EquipmentOeeDowntimeEntity downtime = new EquipmentOeeDowntimeEntity(record, request.downtimeStartedAt(),
							request.downtimeEndedAt(), request.reasonCategory(), request.responsibleParty(),
							request.description(), access.userId());
					downtimeRepository.saveAndFlush(downtime);
					existing.add(downtime);
					record.refreshDowntime(totalDowntime(existing), access.userId());
					details = Map.of("downtimeId", downtime.getId(), "category", downtime.getReasonCategory());
				}
				case "UPDATE_DOWNTIME" -> {
					requireMaintain(access); requireEditable(record); requireDowntimeFields(request);
					if (request.downtimeId() == null) throw invalid("修改停机必须提供停机编号");
					EquipmentOeeDowntimeEntity downtime = requireDowntime(record, request.downtimeId());
					List<EquipmentOeeDowntimeEntity> existing = downtimeRepository.findByOeeRecordIdOrderByStartedAt(id);
					validateDowntime(record, existing, downtime.getId(), request.downtimeStartedAt(),
							request.downtimeEndedAt(), request.reasonCategory(), request.responsibleParty(), request.description());
					downtime.update(request.downtimeStartedAt(), request.downtimeEndedAt(), request.reasonCategory(),
							request.responsibleParty(), request.description(), access.userId());
					downtimeRepository.saveAndFlush(downtime);
					record.refreshDowntime(totalDowntime(existing), access.userId());
					details = Map.of("downtimeId", downtime.getId(), "category", downtime.getReasonCategory());
				}
				case "REMOVE_DOWNTIME" -> {
					requireMaintain(access); requireEditable(record);
					if (request.downtimeId() == null) throw invalid("移除停机必须提供停机编号");
					EquipmentOeeDowntimeEntity downtime = requireDowntime(record, request.downtimeId());
					downtimeRepository.delete(downtime);
					downtimeRepository.flush();
					record.refreshDowntime(totalDowntime(downtimeRepository.findByOeeRecordIdOrderByStartedAt(id)), access.userId());
					details = Map.of("downtimeId", downtime.getId(), "category", downtime.getReasonCategory());
				}
				case "SUBMIT" -> {
					requireMaintain(access); requireEditable(record);
					validateSubmission(access, record);
					record.submit(access.userId());
				}
				case "APPROVE" -> {
					requireApprove(access); requireStatus(record, "SUBMITTED");
					record.approve(access.userId());
				}
				case "REJECT" -> {
					requireApprove(access); requireStatus(record, "SUBMITTED");
					record.reject(request.reason(), access.userId());
				}
				default -> throw invalid("不支持的 OEE 动作");
			}
			recordRepository.saveAndFlush(record);
			eventRepository.saveAndFlush(new EquipmentOeeEventEntity(record, access.userId(), eventAction, from,
					record.getStatus(), request.reason(), requestId, details));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw conflict("OEE 记录已被其他用户修改，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OEE 动作与已有事实冲突，请刷新后重试", exception);
		}
		return toRecord(access, record, true);
	}

	private EquipmentOeeRecord toRecord(CurrentWorkspaceAccess access, EquipmentOeeRecordEntity record, boolean detail) {
		List<EquipmentOeeRecord.Downtime> downtimes = detail
				? downtimeRepository.findByOeeRecordIdOrderByStartedAt(record.getId()).stream()
						.map(item -> new EquipmentOeeRecord.Downtime(item.getId(), item.getStartedAt(), item.getEndedAt(),
								item.getDurationMinutes(), item.getReasonCategory(), item.getResponsibleParty(),
								item.getDescription(), item.getCreatedBy(), item.getCreatedAt(), item.getUpdatedBy(), item.getUpdatedAt()))
						.toList() : List.of();
		List<EquipmentOeeRecord.Event> events = detail
				? eventRepository.findByOeeRecordIdOrderByOccurredAtDesc(record.getId()).stream()
						.map(event -> new EquipmentOeeRecord.Event(event.getId(), event.getActorUserId(), event.getAction(),
								event.getFromStatus(), event.getToStatus(), event.getReason(), event.getRequestId(),
								event.getDetails(), event.getOccurredAt())).toList() : List.of();
		return new EquipmentOeeRecord(record.getId(), record.getRecordNumber(), record.getAssetId(),
				record.getAssetCodeSnapshot(), record.getAssetNameSnapshot(), record.getWorkCenterCodeSnapshot(),
				record.getWorkCenterNameSnapshot(), record.getLocationSnapshot(), record.getWindowStart(), record.getWindowEnd(),
				record.getPlannedProductionMinutes(), record.getDowntimeMinutes(), record.getRunMinutes(),
				record.getIdealCycleSeconds(), record.getTotalCount(), record.getGoodCount(), record.getAvailabilityRate(),
				record.getPerformanceRate(), record.getQualityRate(), record.getOeeRate(), record.getShiftName(),
				record.getProductionReference(), record.getSourceType(), record.getSourceReference(), record.getStatus(),
				record.getRejectionReason(), record.getVersion(), record.getCreatedBy(), record.getCreatedAt(),
				record.getSubmittedBy(), record.getSubmittedAt(), record.getApprovedBy(), record.getApprovedAt(),
				record.getRejectedBy(), record.getRejectedAt(), record.getUpdatedAt(), availableActions(access, record),
				downtimes, events);
	}

	private void validateSubmission(CurrentWorkspaceAccess access, EquipmentOeeRecordEntity record) {
		validateFacts(record.getWindowStart(), record.getWindowEnd(), record.getPlannedProductionMinutes(),
				record.getIdealCycleSeconds(), record.getTotalCount(), record.getGoodCount());
		if (record.getRunMinutes().signum() == 0 && record.getTotalCount() > 0) throw invalid("运行时间为零时总产量必须为零");
		if (record.getPerformanceRate().compareTo(BigDecimal.valueOf(100)) > 0)
			throw invalid("性能率超过 100%，请核对理想节拍、产量和运行时间口径");
		if (recordRepository.countOverlappingSubmitted(access.tenantOrganizationId(), access.workspaceId(),
				record.getAssetId(), record.getId(), record.getWindowStart(), record.getWindowEnd()) > 0)
			throw conflict("同一设备已有重叠的已提交或已审核 OEE 统计窗口");
	}

	private static void validateFacts(Instant start, Instant end, BigDecimal planned, BigDecimal ideal,
			long total, long good) {
		if (start == null || end == null || !end.isAfter(start)) throw invalid("统计结束时间必须晚于开始时间");
		if (planned == null || planned.signum() <= 0) throw invalid("计划生产分钟必须大于零");
		BigDecimal windowMinutes = BigDecimal.valueOf(Duration.between(start, end).toMillis())
				.divide(BigDecimal.valueOf(60000), 4, RoundingMode.HALF_UP);
		if (planned.compareTo(windowMinutes) > 0) throw invalid("计划生产分钟不能超过统计窗口总分钟");
		if (ideal == null || ideal.signum() <= 0) throw invalid("理想节拍必须大于零");
		if (total < 0 || good < 0 || good > total) throw invalid("合格产量不能大于总产量，产量也不能为负数");
	}

	private static void validateExistingDowntimes(List<EquipmentOeeDowntimeEntity> items, Instant start, Instant end,
			BigDecimal planned) {
		for (EquipmentOeeDowntimeEntity item : items) {
			if (item.getStartedAt().isBefore(start) || item.getEndedAt().isAfter(end))
				throw invalid("修改后的统计窗口不能排除已有停机事件");
		}
		if (totalDowntime(items).compareTo(planned) > 0) throw invalid("停机分钟合计不能超过计划生产分钟");
	}

	private static void validateDowntime(EquipmentOeeRecordEntity record, List<EquipmentOeeDowntimeEntity> existing,
			UUID selfId, Instant start, Instant end, String category, String party, String description) {
		if (start == null || end == null || !end.isAfter(start)) throw invalid("停机结束时间必须晚于开始时间");
		if (start.isBefore(record.getWindowStart()) || end.isAfter(record.getWindowEnd()))
			throw invalid("停机事件必须完整位于 OEE 统计窗口内");
		if (!DOWNTIME_CATEGORIES.contains(category)) throw invalid("停机原因分类无效");
		if (party == null || party.isBlank()) throw invalid("停机责任方不能为空");
		if (description == null || description.trim().length() < 4) throw invalid("停机事实说明至少需要 4 个字符");
		for (EquipmentOeeDowntimeEntity item : existing) {
			if (item.getId().equals(selfId)) continue;
			if (start.isBefore(item.getEndedAt()) && end.isAfter(item.getStartedAt())) throw conflict("停机事件时间不能重叠");
		}
		BigDecimal duration = BigDecimal.valueOf(Duration.between(start, end).toMillis())
				.divide(BigDecimal.valueOf(60000), 2, RoundingMode.HALF_UP);
		BigDecimal withoutSelf = existing.stream().filter(item -> !item.getId().equals(selfId))
				.map(EquipmentOeeDowntimeEntity::getDurationMinutes).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (withoutSelf.add(duration).compareTo(record.getPlannedProductionMinutes()) > 0)
			throw invalid("停机分钟合计不能超过计划生产分钟");
	}

	private static BigDecimal totalDowntime(List<EquipmentOeeDowntimeEntity> items) {
		return items.stream().map(EquipmentOeeDowntimeEntity::getDurationMinutes)
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
	}

	private EquipmentOeeRecordEntity requireRecord(CurrentWorkspaceAccess access, UUID id) {
		return recordRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> notFound("OEE 记录不存在或不在当前工作区范围"));
	}

	private EquipmentOeeDowntimeEntity requireDowntime(EquipmentOeeRecordEntity record, UUID id) {
		return downtimeRepository.findByIdAndOeeRecordId(id, record.getId())
				.orElseThrow(() -> notFound("停机事件不存在或不属于当前 OEE 记录"));
	}

	private static void requireUpdateFields(EquipmentOeeRecord.ActionRequest request) {
		if (request.windowStart() == null || request.windowEnd() == null || request.plannedProductionMinutes() == null
				|| request.idealCycleSeconds() == null || request.totalCount() == null || request.goodCount() == null
				|| request.shiftName() == null || request.shiftName().isBlank()) throw invalid("修改 OEE 必须提交完整统计口径");
	}

	private static void requireDowntimeFields(EquipmentOeeRecord.ActionRequest request) {
		if (request.downtimeStartedAt() == null || request.downtimeEndedAt() == null || request.reasonCategory() == null
				|| request.responsibleParty() == null || request.description() == null)
			throw invalid("停机事件必须填写时间、分类、责任方和事实说明");
	}

	private static void requireEditable(EquipmentOeeRecordEntity record) {
		if (!Set.of("DRAFT", "REJECTED").contains(record.getStatus())) throw conflict("只有草稿或已驳回记录可以修改");
	}

	private static void requireStatus(EquipmentOeeRecordEntity record, String expected) {
		if (!expected.equals(record.getStatus())) throw conflict("当前 OEE 状态不允许执行该动作");
	}

	private static void requireVersion(long actual, long expected) {
		if (actual != expected) throw conflict("OEE 记录已被其他用户修改，请刷新后重试");
	}

	private static List<String> availableActions(CurrentWorkspaceAccess access, EquipmentOeeRecordEntity record) {
		if (Set.of("DRAFT", "REJECTED").contains(record.getStatus()) && canMaintain(access))
			return List.of("UPDATE", "ADD_DOWNTIME", "UPDATE_DOWNTIME", "REMOVE_DOWNTIME", "SUBMIT");
		if ("SUBMITTED".equals(record.getStatus()) && canApprove(access)) return List.of("APPROVE", "REJECT");
		return List.of();
	}

	private static boolean canMaintain(CurrentWorkspaceAccess access) { return MAINTAIN_ROLES.contains(access.roleCode()); }
	private static boolean canApprove(CurrentWorkspaceAccess access) { return APPROVE_ROLES.contains(access.roleCode()); }
	private static void requireMaintain(CurrentWorkspaceAccess access) {
		if (!canMaintain(access)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护 OEE 记录");
	}
	private static void requireApprove(CurrentWorkspaceAccess access) {
		if (!canApprove(access)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权审核 OEE 记录");
	}

	private static String normalizeStatus(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) throw invalid("OEE 状态筛选无效");
		return normalized;
	}
	private static String normalizeQuery(String value) { return value == null ? "" : value.trim(); }

	private static BigDecimal average(List<EquipmentOeeRecordEntity> records, Metric metric) {
		if (records.isEmpty()) return BigDecimal.ZERO.setScale(4);
		BigDecimal total = records.stream().map(record -> switch (metric) {
			case AVAILABILITY -> record.getAvailabilityRate(); case PERFORMANCE -> record.getPerformanceRate();
			case QUALITY -> record.getQualityRate(); case OEE -> record.getOeeRate();
		}).reduce(BigDecimal.ZERO, BigDecimal::add);
		return total.divide(BigDecimal.valueOf(records.size()), 4, RoundingMode.HALF_UP);
	}

	private static String eventAction(String action) { return switch (action) {
		case "UPDATE" -> "UPDATED"; case "ADD_DOWNTIME" -> "DOWNTIME_ADDED";
		case "UPDATE_DOWNTIME" -> "DOWNTIME_UPDATED"; case "REMOVE_DOWNTIME" -> "DOWNTIME_REMOVED";
		case "SUBMIT" -> "SUBMITTED"; case "APPROVE" -> "APPROVED"; case "REJECT" -> "REJECTED";
		default -> action;
	}; }

	private static String nextNumber() {
		return "OEE-" + NUMBER_TIME.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
	}
	private static String requestId() {
		String value = MDC.get("requestId");
		return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
	}

	private enum Metric { AVAILABILITY, PERFORMANCE, QUALITY, OEE }
	private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
	private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private static ResponseStatusException conflict(String message, Exception cause) { return new ResponseStatusException(HttpStatus.CONFLICT, message, cause); }
}
