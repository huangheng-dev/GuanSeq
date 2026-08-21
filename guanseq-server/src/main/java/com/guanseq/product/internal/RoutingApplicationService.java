package com.guanseq.product.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
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
import com.guanseq.masterdata.api.MasterDataReferenceProvider;
import com.guanseq.masterdata.api.MasterDataReferenceProvider.MaterialReference;
import com.guanseq.product.api.RoutingPage;
import com.guanseq.product.api.RoutingRecord;
import com.guanseq.product.api.RoutingRecord.OperationInput;
import com.guanseq.product.api.RoutingReferenceData;
import com.guanseq.product.api.RoutingReferenceProvider;
import com.guanseq.product.api.RoutingReferenceProvider.EffectiveOperation;
import com.guanseq.product.api.RoutingReferenceProvider.EffectiveRouting;

@Service
public class RoutingApplicationService implements RoutingReferenceProvider {

	private static final Set<String> PRODUCT_ROLES = Set.of("PRODUCT_ENGINEER", "PLANNING_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final RoutingRepository routingRepository;
	private final RoutingOperationRepository operationRepository;
	private final RoutingEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	RoutingApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider,
			RoutingRepository routingRepository,
			RoutingOperationRepository operationRepository,
			RoutingEventRepository eventRepository,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.masterDataProvider = masterDataProvider;
		this.routingRepository = routingRepository;
		this.operationRepository = operationRepository;
		this.eventRepository = eventRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public RoutingPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = routingRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new RoutingPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public RoutingRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireRouting(access, id));
	}

	@Transactional(readOnly = true)
	public RoutingReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return new RoutingReferenceData(masterDataProvider.listActiveMaterials(access.tenantOrganizationId()).stream()
				.filter(material -> "MAKE".equals(material.procurementType()))
				.map(this::toOption).toList());
	}

	@Transactional
	public RoutingRecord create(String username, RoutingRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireProductRole(access);
		MaterialReference material = requireMakeMaterial(access, request.materialId());
		List<RoutingOperationEntity> operations = buildOperations(null, access, request.operations());
		RoutingEntity entity = new RoutingEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextRoutingNumber(), access.userId());
		entity.updateDraft(material.id(), material.code(), material.name(), material.specification(), material.baseUnit(),
				request.usageType(), request.versionCode(), request.baseQuantity(), request.effectiveFrom(), request.owner(),
				request.changeReason(), access.userId());
		try {
			routingRepository.saveAndFlush(entity);
			operationRepository.saveAll(operations.stream().map(item -> copyForRouting(item, entity.getId())).toList());
			operationRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw conflict("同一物料、用途与版本编码的工艺路线已存在", exception);
		}
		audit(access, entity, "CREATED", null, "DRAFT",
				Map.of("versionCode", entity.getVersionCode(), "operationCount", operations.size()));
		return toRecord(entity);
	}

	@Transactional
	public RoutingRecord update(String username, UUID id, RoutingRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireProductRole(access);
		RoutingEntity entity = requireRouting(access, id);
		requireVersion(entity.getVersion(), request.expectedVersion());
		requireStatus(entity, "DRAFT", "只有草稿工艺路线可以编辑");
		MaterialReference material = requireMakeMaterial(access, request.materialId());
		List<RoutingOperationEntity> operations = buildOperations(entity.getId(), access, request.operations());
		entity.updateDraft(material.id(), material.code(), material.name(), material.specification(), material.baseUnit(),
				request.usageType(), request.versionCode(), request.baseQuantity(), request.effectiveFrom(), request.owner(),
				request.changeReason(), access.userId());
		try {
			routingRepository.saveAndFlush(entity);
			operationRepository.deleteByRoutingId(entity.getId());
			operationRepository.flush();
			operationRepository.saveAll(operations.stream().map(item -> copyForRouting(item, entity.getId())).toList());
			operationRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw conflict("同一物料、用途与版本编码的工艺路线已存在", exception);
		}
		audit(access, entity, "UPDATED", "DRAFT", "DRAFT",
				Map.of("versionCode", entity.getVersionCode(), "operationCount", operations.size()));
		return toRecord(entity);
	}

	@Transactional
	public RoutingRecord act(String username, UUID id, RoutingRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireProductRole(access);
		RoutingEntity entity = requireRouting(access, id);
		requireVersion(entity.getVersion(), request.expectedVersion());
		return switch (request.action()) {
			case "PUBLISH" -> publish(access, entity);
			case "INACTIVATE" -> inactivate(access, entity);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的工艺路线动作");
		};
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasEffectiveRouting(UUID tenantOrganizationId, UUID materialId, LocalDate effectiveDate) {
		return routingRepository.findEffectivePublished(tenantOrganizationId, materialId, effectiveDate).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public EffectiveRouting findEffectiveRouting(UUID tenantOrganizationId, UUID materialId, LocalDate effectiveDate) {
		RoutingEntity routing = routingRepository.findEffectivePublished(tenantOrganizationId, materialId, effectiveDate)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "计划开工日没有已生效工艺路线"));
		List<EffectiveOperation> operations = operationRepository.findByRoutingIdOrderBySequenceNumberAsc(routing.getId()).stream()
				.map(item -> new EffectiveOperation(item.getId(), item.getSequenceNumber(), item.getOperationCode(),
						item.getOperationName(), item.getWorkCenterCode(), item.getWorkCenterName(), item.getSetupMinutes(),
						item.getRunMinutesPerUnit(), item.getQueueMinutes(), item.isInspectionRequired(),
						item.getInstructionSummary()))
				.toList();
		return new EffectiveRouting(routing.getId(), routing.getRoutingNumber(), routing.getVersionCode(),
				routing.getMaterialId(), routing.getMaterialCode(), routing.getMaterialName(), routing.getBaseQuantity(), operations);
	}

	private RoutingRecord publish(CurrentWorkspaceAccess access, RoutingEntity entity) {
		requireStatus(entity, "DRAFT", "只有草稿工艺路线可以发布");
		requireMakeMaterial(access, entity.getMaterialId());
		List<RoutingOperationEntity> operations = operationRepository.findByRoutingIdOrderBySequenceNumberAsc(entity.getId());
		if (operations.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "工艺路线至少需要一道工序");
		if (routingRepository.existsByTenantOrganizationIdAndMaterialIdAndUsageTypeAndStatusAndIdNot(
				access.tenantOrganizationId(), entity.getMaterialId(), entity.getUsageType(), "PUBLISHED", entity.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该物料和用途已经有已发布工艺路线，请先停用旧版本");
		}
		entity.publish(access.userId());
		try {
			routingRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("该物料和用途已经有已发布工艺路线，请刷新后重试", exception);
		}
		audit(access, entity, "PUBLISHED", "DRAFT", "PUBLISHED",
				Map.of("versionCode", entity.getVersionCode(), "operationCount", operations.size(),
						"effectiveFrom", entity.getEffectiveFrom().toString()));
		return toRecord(entity);
	}

	private RoutingRecord inactivate(CurrentWorkspaceAccess access, RoutingEntity entity) {
		requireStatus(entity, "PUBLISHED", "只有已发布工艺路线可以停用");
		entity.inactivate(access.userId(), LocalDate.now());
		routingRepository.saveAndFlush(entity);
		audit(access, entity, "INACTIVATED", "PUBLISHED", "INACTIVE",
				Map.of("versionCode", entity.getVersionCode(), "effectiveTo", entity.getEffectiveTo().toString()));
		return toRecord(entity);
	}

	private List<RoutingOperationEntity> buildOperations(UUID ignoredRoutingId, CurrentWorkspaceAccess access,
			List<OperationInput> inputs) {
		Set<String> operationCodes = new HashSet<>();
		List<RoutingOperationEntity> result = new ArrayList<>();
		int sequence = 0;
		for (OperationInput input : inputs) {
			String operationCode = input.operationCode().trim().toUpperCase();
			if (!operationCodes.add(operationCode)) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "同一工艺路线不能重复使用工序编码");
			}
			if (input.setupMinutes().compareTo(BigDecimal.ZERO) == 0
					&& input.runMinutesPerUnit().compareTo(BigDecimal.ZERO) == 0) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "每道工序的准备或单件工时至少一项必须大于 0");
			}
			sequence += 10;
			result.add(new RoutingOperationEntity(ignoredRoutingId, access.tenantOrganizationId(), sequence,
					operationCode, input.operationName(), input.workCenterCode().toUpperCase(), input.workCenterName(),
					input.setupMinutes(), input.runMinutesPerUnit(), input.queueMinutes(), input.inspectionRequired(),
					input.instructionSummary()));
		}
		return result;
	}

	private static RoutingOperationEntity copyForRouting(RoutingOperationEntity source, UUID routingId) {
		return new RoutingOperationEntity(routingId, source.getTenantOrganizationId(), source.getSequenceNumber(),
				source.getOperationCode(), source.getOperationName(), source.getWorkCenterCode(), source.getWorkCenterName(),
				source.getSetupMinutes(), source.getRunMinutesPerUnit(), source.getQueueMinutes(),
				source.isInspectionRequired(), source.getInstructionSummary());
	}

	private MaterialReference requireMakeMaterial(CurrentWorkspaceAccess access, UUID materialId) {
		MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), materialId);
		if (!"MAKE".equals(material.procurementType())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只有获取方式为自制的物料可以建立生产工艺路线");
		}
		return material;
	}

	private RoutingEntity requireRouting(CurrentWorkspaceAccess access, UUID id) {
		return routingRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工艺路线不存在或不在当前租户范围"));
	}

	private void audit(CurrentWorkspaceAccess access, RoutingEntity entity, String action, String fromStatus,
			String toStatus, Map<String, Object> details) {
		eventRepository.save(new RoutingEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				entity.getId(), action, fromStatus, toStatus, MDC.get("requestId"), details));
	}

	private RoutingRecord toRecord(RoutingEntity entity) {
		List<RoutingRecord.Operation> operations = operationRepository
				.findByRoutingIdOrderBySequenceNumberAsc(entity.getId()).stream()
				.map(item -> new RoutingRecord.Operation(item.getId(), item.getSequenceNumber(), item.getOperationCode(),
						item.getOperationName(), item.getWorkCenterCode(), item.getWorkCenterName(), item.getSetupMinutes(),
						item.getRunMinutesPerUnit(), item.getQueueMinutes(), item.isInspectionRequired(),
						item.getInstructionSummary()))
				.toList();
		List<RoutingRecord.Event> events = eventRepository.findByRoutingIdOrderByOccurredAtDesc(entity.getId()).stream()
				.map(event -> new RoutingRecord.Event(event.getId(), event.getAction(), event.getFromStatus(),
						event.getToStatus(), event.getRequestId(), event.getDetails(), event.getOccurredAt()))
				.toList();
		return new RoutingRecord(entity.getId(), entity.getRoutingNumber(), entity.getMaterialId(), entity.getMaterialCode(),
				entity.getMaterialName(), entity.getMaterialSpecification(), entity.getMaterialUnit(), entity.getUsageType(),
				entity.getVersionCode(), entity.getBaseQuantity(), entity.getEffectiveFrom(), entity.getEffectiveTo(),
				entity.getOwner(), entity.getChangeReason(), entity.getStatus(), entity.getVersion(), entity.getUpdatedAt(),
				entity.getPublishedAt(), operations, events);
	}

	private RoutingReferenceData.MaterialOption toOption(MaterialReference material) {
		return new RoutingReferenceData.MaterialOption(material.id(), material.code(), material.name(),
				material.specification(), material.baseUnit(), material.procurementType());
	}

	private String nextRoutingNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('product.routing_number_seq')", Long.class);
		return "RTG-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private static void requireProductRole(CurrentWorkspaceAccess access) {
		if (!PRODUCT_ROLES.contains(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护工艺路线版本");
		}
	}

	private static void requireVersion(long current, long expected) {
		if (current != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "工艺路线已被其他用户修改，请刷新后重试");
	}

	private static void requireStatus(RoutingEntity entity, String expected, String message) {
		if (!expected.equals(entity.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }

	private static String normalizeStatus(String value) {
		return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase();
	}

	private static ResponseStatusException conflict(String message, DataIntegrityViolationException cause) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
	}
}
