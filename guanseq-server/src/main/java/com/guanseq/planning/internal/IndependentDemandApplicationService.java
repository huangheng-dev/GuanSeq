package com.guanseq.planning.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
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
import com.guanseq.planning.api.IndependentDemandPage;
import com.guanseq.planning.api.IndependentDemandRecord;
import com.guanseq.planning.api.PlanningDemandReferenceData;
import com.guanseq.sales.api.SalesOrderReleasedEvent;

@Service
public class IndependentDemandApplicationService {

	private static final Set<String> PLANNING_ROLES = Set.of("PLANNING_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final IndependentDemandRepository demandRepository;
	private final IndependentDemandEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	IndependentDemandApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider,
			IndependentDemandRepository demandRepository,
			IndependentDemandEventRepository eventRepository,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.masterDataProvider = masterDataProvider;
		this.demandRepository = demandRepository;
		this.eventRepository = eventRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public IndependentDemandPage list(String username, String query, String status, String sourceType, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = demandRepository.search(
				access.tenantOrganizationId(), normalize(query), normalizeFilter(status), normalizeFilter(sourceType),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.ASC, "requiredDate").and(Sort.by(Sort.Direction.DESC, "updatedAt"))));
		return new IndependentDemandPage(result.getContent().stream().map(this::toRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public IndependentDemandRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireDemand(access, id));
	}

	@Transactional(readOnly = true)
	public PlanningDemandReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return new PlanningDemandReferenceData(masterDataProvider.listActiveMaterials(access.tenantOrganizationId()).stream()
				.map(item -> new PlanningDemandReferenceData.MaterialOption(item.id(), item.code(), item.name(), item.specification(), item.baseUnit()))
				.toList());
	}

	@Transactional
	public IndependentDemandRecord create(String username, IndependentDemandRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requirePlanningRole(access);
		validateRequiredDate(request.requiredDate());
		MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), request.materialId());
		IndependentDemandEntity demand = IndependentDemandEntity.manual(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextDemandNumber(), access.userId());
		demand.update(material.id(), material.code(), material.name(), material.specification(), material.baseUnit(),
				request.quantity(), request.requiredDate(), request.priority(), request.owner(), request.note(), access.userId());
		demandRepository.saveAndFlush(demand);
		audit(access, demand, "CREATED", null, "DRAFT", null, Map.of("sourceType", "MANUAL"));
		return toRecord(demand);
	}

	@Transactional
	public IndependentDemandRecord update(String username, UUID id, IndependentDemandRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requirePlanningRole(access);
		IndependentDemandEntity demand = requireDemand(access, id);
		requireVersion(demand, request.expectedVersion());
		if (!"MANUAL".equals(demand.getSourceType())) throw conflict("销售订单来源需求不能手工修改");
		if (!"DRAFT".equals(demand.getStatus())) throw conflict("只有草稿需求可以编辑");
		validateRequiredDate(request.requiredDate());
		MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), request.materialId());
		demand.update(material.id(), material.code(), material.name(), material.specification(), material.baseUnit(),
				request.quantity(), request.requiredDate(), request.priority(), request.owner(), request.note(), access.userId());
		demandRepository.saveAndFlush(demand);
		audit(access, demand, "UPDATED", "DRAFT", "DRAFT", null, Map.of("quantity", request.quantity()));
		return toRecord(demand);
	}

	@Transactional
	public IndependentDemandRecord action(String username, UUID id, IndependentDemandRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requirePlanningRole(access);
		IndependentDemandEntity demand = requireDemand(access, id);
		requireVersion(demand, request.expectedVersion());
		if (!"MANUAL".equals(demand.getSourceType())) throw conflict("销售订单来源需求由订单流程控制，不能手工变更状态");
		String from = demand.getStatus();
		String target = switch (request.action()) {
			case "ACTIVATE" -> {
				if (!"DRAFT".equals(from)) throw conflict("只有草稿需求可以激活");
				yield "ACTIVE";
			}
			case "CANCEL" -> {
				if (!Set.of("DRAFT", "ACTIVE").contains(from)) throw conflict("当前需求不能取消");
				if (request.comment() == null || request.comment().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "取消需求必须填写原因");
				yield "CANCELLED";
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的需求动作");
		};
		demand.transition(target, request.comment(), access.userId());
		demandRepository.saveAndFlush(demand);
		audit(access, demand, request.action(), from, target, request.comment(), Map.of("sourceType", demand.getSourceType()));
		return toRecord(demand);
	}

	@EventListener
	@Transactional
	public void acceptReleasedOrder(SalesOrderReleasedEvent event) {
		for (SalesOrderReleasedEvent.Line line : event.lines()) {
			if (demandRepository.existsByTenantOrganizationIdAndSourceTypeAndSourceLineId(
					event.tenantOrganizationId(), "SALES_ORDER", line.lineId())) continue;
			IndependentDemandEntity demand = IndependentDemandEntity.salesOrder(
					event.tenantOrganizationId(), event.owningOrganizationId(), event.workspaceId(), nextDemandNumber(),
					event.actorUserId(), event.orderId(), event.orderNumber(), line.lineId(), line.lineNumber(), event.customerName());
			demand.update(line.materialId(), line.materialCode(), line.materialName(), line.materialSpecification(),
					line.unit(), line.quantity(), event.requiredDate(), "NORMAL", event.owner(),
					"由销售订单下达自动生成", event.actorUserId());
			demandRepository.saveAndFlush(demand);
			eventRepository.save(new IndependentDemandEventEntity(event.tenantOrganizationId(), event.workspaceId(),
					event.actorUserId(), demand.getId(), "IMPORTED", null, "ACTIVE", MDC.get("requestId"), null,
					Map.of("sourceType", "SALES_ORDER", "sourceNumber", event.orderNumber(), "sourceLineNumber", line.lineNumber())));
		}
	}

	private IndependentDemandEntity requireDemand(CurrentWorkspaceAccess access, UUID id) {
		return demandRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "独立需求不存在或不在当前租户范围"));
	}

	private void audit(CurrentWorkspaceAccess access, IndependentDemandEntity demand, String action,
			String from, String to, String comment, Map<String, Object> details) {
		eventRepository.save(new IndependentDemandEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), demand.getId(), action, from, to, MDC.get("requestId"), comment, details));
	}

	private IndependentDemandRecord toRecord(IndependentDemandEntity demand) {
		return new IndependentDemandRecord(demand.getId(), demand.getDemandNumber(), demand.getSourceType(),
				demand.getSourceId(), demand.getSourceNumber(), demand.getSourceLineId(), demand.getSourceLineNumber(),
				demand.getSourceCustomer(), demand.getMaterialId(), demand.getMaterialCode(), demand.getMaterialName(),
				demand.getMaterialSpecification(), demand.getUnit(), demand.getQuantity(), demand.getRequiredDate(),
				demand.getPriority(), demand.getOwner(), demand.getStatus(), demand.getNote(),
				demand.getCancellationReason(), demand.getVersion(), demand.getUpdatedAt());
	}

	private String nextDemandNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('planning.demand_number_seq')", Long.class);
		return "DMD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private static void validateRequiredDate(LocalDate date) {
		if (date.isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "需求日期不能早于今天");
	}

	private static void requirePlanningRole(CurrentWorkspaceAccess access) {
		if (!PLANNING_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护计划需求");
	}

	private static void requireVersion(IndependentDemandEntity demand, long expected) {
		if (demand.getVersion() != expected) throw conflict("需求已经被其他用户修改，请刷新后重试");
	}

	private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
	private static String normalizeFilter(String value) { return value == null || value.isBlank() || "ALL".equals(value) ? "" : value; }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
