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
import com.guanseq.product.api.BomPage;
import com.guanseq.product.api.BomRecord;
import com.guanseq.product.api.BomRecord.LineInput;
import com.guanseq.product.api.BomReferenceData;
import com.guanseq.product.api.BomReferenceProvider;

@Service
public class BomApplicationService implements BomReferenceProvider {

	private static final Set<String> PRODUCT_ROLES = Set.of("PRODUCT_ENGINEER", "PLANNING_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final BomRepository bomRepository;
	private final BomLineRepository lineRepository;
	private final BomEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	BomApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider,
			BomRepository bomRepository,
			BomLineRepository lineRepository,
			BomEventRepository eventRepository,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.masterDataProvider = masterDataProvider;
		this.bomRepository = bomRepository;
		this.lineRepository = lineRepository;
		this.eventRepository = eventRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public BomPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = bomRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new BomPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public BomRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireBom(access, id));
	}

	@Transactional(readOnly = true)
	public BomReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		List<MaterialReference> materials = masterDataProvider.listActiveMaterials(access.tenantOrganizationId());
		List<BomReferenceData.MaterialOption> components = materials.stream().map(this::toOption).toList();
		return new BomReferenceData(components.stream().filter(item -> "MAKE".equals(item.procurementType())).toList(),
				components);
	}

	@Transactional
	public BomRecord create(String username, BomRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireProductRole(access);
		MaterialReference parent = requireMakeParent(access, request.parentMaterialId());
		List<BomLineEntity> lines = buildLines(null, access, parent, request.lines());
		BomEntity entity = new BomEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextBomNumber(), access.userId());
		entity.updateDraft(parent.id(), parent.code(), parent.name(), parent.specification(), parent.baseUnit(),
				request.usageType(), request.versionCode(), request.baseQuantity(), request.effectiveFrom(), request.owner(),
				request.changeReason(), access.userId());
		try {
			bomRepository.saveAndFlush(entity);
			lineRepository.saveAll(lines.stream().map(line -> copyForBom(line, entity.getId())).toList());
			lineRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw conflict("同一父项、用途与版本编码的 BOM 已存在", exception);
		}
		audit(access, entity, "CREATED", null, "DRAFT",
				Map.of("versionCode", entity.getVersionCode(), "lineCount", lines.size()));
		return toRecord(entity);
	}

	@Transactional
	public BomRecord update(String username, UUID id, BomRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireProductRole(access);
		BomEntity entity = requireBom(access, id);
		requireVersion(entity.getVersion(), request.expectedVersion());
		requireStatus(entity, "DRAFT", "只有草稿 BOM 可以编辑");
		MaterialReference parent = requireMakeParent(access, request.parentMaterialId());
		List<BomLineEntity> lines = buildLines(entity.getId(), access, parent, request.lines());
		entity.updateDraft(parent.id(), parent.code(), parent.name(), parent.specification(), parent.baseUnit(),
				request.usageType(), request.versionCode(), request.baseQuantity(), request.effectiveFrom(), request.owner(),
				request.changeReason(), access.userId());
		try {
			bomRepository.saveAndFlush(entity);
			lineRepository.deleteByBomId(entity.getId());
			lineRepository.flush();
			lineRepository.saveAll(lines.stream().map(line -> copyForBom(line, entity.getId())).toList());
			lineRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw conflict("同一父项、用途与版本编码的 BOM 已存在", exception);
		}
		audit(access, entity, "UPDATED", "DRAFT", "DRAFT",
				Map.of("versionCode", entity.getVersionCode(), "lineCount", lines.size()));
		return toRecord(entity);
	}

	@Transactional
	public BomRecord act(String username, UUID id, BomRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireProductRole(access);
		BomEntity entity = requireBom(access, id);
		requireVersion(entity.getVersion(), request.expectedVersion());
		return switch (request.action()) {
			case "PUBLISH" -> publish(access, entity);
			case "INACTIVATE" -> inactivate(access, entity);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 BOM 动作");
		};
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasEffectiveBom(UUID tenantOrganizationId, UUID parentMaterialId, LocalDate effectiveDate) {
		return bomRepository.findEffectivePublished(tenantOrganizationId, parentMaterialId, effectiveDate).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.Optional<BomReferenceProvider.EffectiveBom> findEffectiveBom(UUID tenantOrganizationId,
			UUID parentMaterialId, LocalDate effectiveDate) {
		return bomRepository.findEffectivePublished(tenantOrganizationId, parentMaterialId, effectiveDate)
				.map(bom -> new BomReferenceProvider.EffectiveBom(bom.getId(), bom.getParentMaterialId(),
						bom.getBaseQuantity(), lineRepository.findByBomIdOrderByLineNumberAsc(bom.getId()).stream()
								.map(line -> new BomReferenceProvider.Component(line.getComponentMaterialId(),
										line.getComponentMaterialCode(), line.getComponentMaterialName(), line.getUnit(),
										line.getQuantity(), line.getScrapRate())).toList()));
	}

	private BomRecord publish(CurrentWorkspaceAccess access, BomEntity entity) {
		requireStatus(entity, "DRAFT", "只有草稿 BOM 可以发布");
		MaterialReference parent = requireMakeParent(access, entity.getParentMaterialId());
		List<BomLineEntity> lines = lineRepository.findByBomIdOrderByLineNumberAsc(entity.getId());
		if (lines.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "BOM 至少需要一条组件明细");
		for (BomLineEntity line : lines) {
			masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), line.getComponentMaterialId());
		}
		if (bomRepository.existsByTenantOrganizationIdAndParentMaterialIdAndUsageTypeAndStatusAndIdNot(
				access.tenantOrganizationId(), parent.id(), entity.getUsageType(), "PUBLISHED", entity.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该父项和用途已经有已发布 BOM，请先停用旧版本");
		}
		for (BomLineEntity line : lines) {
			if (reaches(access.tenantOrganizationId(), line.getComponentMaterialId(), parent.id(),
					entity.getEffectiveFrom(), new HashSet<>())) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
						"发布会形成循环 BOM：组件 " + line.getComponentMaterialCode() + " 最终引用父项 " + parent.code());
			}
		}
		entity.publish(access.userId());
		try {
			bomRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("该父项和用途已经有已发布 BOM，请刷新后重试", exception);
		}
		audit(access, entity, "PUBLISHED", "DRAFT", "PUBLISHED",
				Map.of("versionCode", entity.getVersionCode(), "lineCount", lines.size(),
						"effectiveFrom", entity.getEffectiveFrom().toString()));
		return toRecord(entity);
	}

	private BomRecord inactivate(CurrentWorkspaceAccess access, BomEntity entity) {
		requireStatus(entity, "PUBLISHED", "只有已发布 BOM 可以停用");
		entity.inactivate(access.userId(), LocalDate.now());
		bomRepository.saveAndFlush(entity);
		audit(access, entity, "INACTIVATED", "PUBLISHED", "INACTIVE",
				Map.of("versionCode", entity.getVersionCode(), "effectiveTo", entity.getEffectiveTo().toString()));
		return toRecord(entity);
	}

	private boolean reaches(UUID tenantId, UUID currentMaterialId, UUID targetMaterialId, LocalDate effectiveDate,
			Set<UUID> visited) {
		if (currentMaterialId.equals(targetMaterialId)) return true;
		if (!visited.add(currentMaterialId)) return false;
		return bomRepository.findEffectivePublished(tenantId, currentMaterialId, effectiveDate)
				.map(bom -> lineRepository.findByBomIdOrderByLineNumberAsc(bom.getId()).stream()
						.anyMatch(line -> reaches(tenantId, line.getComponentMaterialId(), targetMaterialId,
								effectiveDate, visited)))
				.orElse(false);
	}

	private List<BomLineEntity> buildLines(UUID ignoredBomId, CurrentWorkspaceAccess access, MaterialReference parent,
			List<LineInput> inputs) {
		Set<UUID> componentIds = new HashSet<>();
		List<BomLineEntity> result = new ArrayList<>();
		int index = 0;
		for (LineInput input : inputs) {
			if (parent.id().equals(input.componentMaterialId())) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "父项物料不能直接作为自身组件");
			}
			if (!componentIds.add(input.componentMaterialId())) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "同一组件不能在一个 BOM 中重复出现");
			}
			MaterialReference component = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(),
					input.componentMaterialId());
			index += 10;
			result.add(new BomLineEntity(ignoredBomId, access.tenantOrganizationId(), index, component.id(),
					component.code(), component.name(), component.specification(), component.baseUnit(), input.quantity(),
					input.scrapRate(), input.note()));
		}
		return result;
	}

	private static BomLineEntity copyForBom(BomLineEntity source, UUID bomId) {
		return new BomLineEntity(bomId, source.getTenantOrganizationId(), source.getLineNumber(), source.getComponentMaterialId(),
				source.getComponentMaterialCode(), source.getComponentMaterialName(),
				source.getComponentMaterialSpecification(), source.getUnit(), source.getQuantity(), source.getScrapRate(),
				source.getNote());
	}

	private MaterialReference requireMakeParent(CurrentWorkspaceAccess access, UUID parentMaterialId) {
		MaterialReference parent = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), parentMaterialId);
		if (!"MAKE".equals(parent.procurementType())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只有获取方式为自制的物料可以建立生产 BOM");
		}
		return parent;
	}

	private BomEntity requireBom(CurrentWorkspaceAccess access, UUID id) {
		return bomRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOM 不存在或不在当前租户范围"));
	}

	private void audit(CurrentWorkspaceAccess access, BomEntity entity, String action, String fromStatus,
			String toStatus, Map<String, Object> details) {
		eventRepository.save(new BomEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				entity.getId(), action, fromStatus, toStatus, MDC.get("requestId"), details));
	}

	private BomRecord toRecord(BomEntity entity) {
		List<BomRecord.Line> lines = lineRepository.findByBomIdOrderByLineNumberAsc(entity.getId()).stream()
				.map(line -> new BomRecord.Line(line.getId(), line.getLineNumber(), line.getComponentMaterialId(),
						line.getComponentMaterialCode(), line.getComponentMaterialName(),
						line.getComponentMaterialSpecification(), line.getUnit(), line.getQuantity(), line.getScrapRate(),
						line.getNote()))
				.toList();
		List<BomRecord.Event> events = eventRepository.findByBomIdOrderByOccurredAtDesc(entity.getId()).stream()
				.map(event -> new BomRecord.Event(event.getId(), event.getAction(), event.getFromStatus(),
						event.getToStatus(), event.getRequestId(), event.getDetails(), event.getOccurredAt()))
				.toList();
		return new BomRecord(entity.getId(), entity.getBomNumber(), entity.getParentMaterialId(),
				entity.getParentMaterialCode(), entity.getParentMaterialName(), entity.getParentMaterialSpecification(),
				entity.getParentUnit(), entity.getUsageType(), entity.getVersionCode(), entity.getBaseQuantity(),
				entity.getEffectiveFrom(), entity.getEffectiveTo(), entity.getOwner(), entity.getChangeReason(),
				entity.getStatus(), entity.getVersion(), entity.getUpdatedAt(), entity.getPublishedAt(), lines, events);
	}

	private BomReferenceData.MaterialOption toOption(MaterialReference material) {
		return new BomReferenceData.MaterialOption(material.id(), material.code(), material.name(),
				material.specification(), material.baseUnit(), material.procurementType());
	}

	private String nextBomNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('product.bom_number_seq')", Long.class);
		return "BOM-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private static void requireProductRole(CurrentWorkspaceAccess access) {
		if (!PRODUCT_ROLES.contains(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护 BOM 版本");
		}
	}

	private static void requireVersion(long current, long expected) {
		if (current != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "BOM 已被其他用户修改，请刷新后重试");
	}

	private static void requireStatus(BomEntity entity, String expected, String message) {
		if (!expected.equals(entity.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private static String normalizeStatus(String value) {
		return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase();
	}

	private static ResponseStatusException conflict(String message, DataIntegrityViolationException cause) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
	}
}
