package com.guanseq.production.internal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
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
import com.guanseq.product.api.BomReferenceProvider;
import com.guanseq.product.api.RoutingReferenceProvider;
import com.guanseq.production.api.ProductionOrderPage;
import com.guanseq.production.api.ProductionOrderCommandService;
import com.guanseq.production.api.ProductionOrderRecord;
import com.guanseq.production.api.ProductionOrderReferenceData;
import com.guanseq.production.api.ProductionScheduledReceiptProvider;

@Service
public class ProductionOrderApplicationService implements ProductionScheduledReceiptProvider, ProductionOrderCommandService {
	private static final Set<String> CONTROL_ROLES = Set.of("PRODUCTION_MANAGER", "PLANNING_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final BomReferenceProvider bomReferenceProvider;
	private final RoutingReferenceProvider routingReferenceProvider;
	private final ProductionOrderRepository orderRepository;
	private final ProductionOrderEventRepository eventRepository;
	private final OperationTaskApplicationService operationTaskService;
	private final JdbcTemplate jdbcTemplate;

	ProductionOrderApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider, BomReferenceProvider bomReferenceProvider,
			RoutingReferenceProvider routingReferenceProvider, ProductionOrderRepository orderRepository,
			ProductionOrderEventRepository eventRepository, OperationTaskApplicationService operationTaskService,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.masterDataProvider = masterDataProvider;
		this.bomReferenceProvider = bomReferenceProvider; this.routingReferenceProvider = routingReferenceProvider;
		this.orderRepository = orderRepository; this.eventRepository = eventRepository;
		this.operationTaskService = operationTaskService; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public ProductionOrderPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = orderRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new ProductionOrderPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ProductionOrderRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); return toRecord(requireOrder(access, id));
	}

	@Transactional(readOnly = true)
	public ProductionOrderReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return new ProductionOrderReferenceData(masterDataProvider.listActiveMaterials(access.tenantOrganizationId()).stream()
				.filter(item -> "MAKE".equals(item.procurementType()))
				.map(item -> new ProductionOrderReferenceData.MaterialOption(item.id(), item.code(), item.name(),
						item.specification(), item.baseUnit())).toList());
	}

	@Transactional
	public ProductionOrderRecord create(String username, ProductionOrderRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireControlRole(access); validateDates(request.plannedStartDate(), request.plannedReceiptDate());
		MaterialReference material = requireMakeMaterial(access, request.materialId());
		ProductionOrderEntity order = new ProductionOrderEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextOrderNumber(), access.userId());
		order.update(material, request.plannedQuantity(), request.plannedStartDate(), request.plannedReceiptDate(),
				request.workshop(), request.owner(), request.sourceType(), request.sourceId(), request.sourceNumber(), access.userId());
		orderRepository.saveAndFlush(order); audit(access, order, "CREATED", null, "DRAFT", null); return toRecord(order);
	}

	@Override
	@Transactional
	public CreatedOrder createFromMrp(String username, CreateFromMrpCommand command) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireControlRole(access);
		var existing = orderRepository.findByTenantOrganizationIdAndSourceTypeAndSourceId(
				access.tenantOrganizationId(), "MRP", command.suggestionId());
		if (existing.isPresent()) return new CreatedOrder(existing.get().getId(), existing.get().getOrderNumber());
		if (command.quantity() == null || command.quantity().signum() <= 0)
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "生产建议转单数量必须大于零");
		if (command.workshop() == null || command.workshop().isBlank() || command.owner() == null || command.owner().isBlank())
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "生产建议转单必须指定车间和责任人");
		validateDates(command.plannedStartDate(), command.plannedReceiptDate());
		MaterialReference material = requireMakeMaterial(access, command.materialId());
		ProductionOrderEntity order = new ProductionOrderEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextOrderNumber(), access.userId());
		order.update(material, command.quantity(), command.plannedStartDate(), command.plannedReceiptDate(),
				command.workshop(), command.owner(), "MRP", command.suggestionId(), command.sourceNumber(), access.userId());
		orderRepository.saveAndFlush(order); audit(access, order, "CREATED_FROM_MRP", null, "DRAFT", null);
		return new CreatedOrder(order.getId(), order.getOrderNumber());
	}

	@Transactional
	public ProductionOrderRecord update(String username, UUID id, ProductionOrderRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireControlRole(access);
		ProductionOrderEntity order = requireOrder(access, id); requireVersion(order, request.expectedVersion());
		requireStatus(order, "DRAFT", "只有草稿生产订单可以编辑"); validateDates(request.plannedStartDate(), request.plannedReceiptDate());
		MaterialReference material = requireMakeMaterial(access, request.materialId());
		order.update(material, request.plannedQuantity(), request.plannedStartDate(), request.plannedReceiptDate(),
				request.workshop(), request.owner(), request.sourceType(), request.sourceId(), request.sourceNumber(), access.userId());
		orderRepository.saveAndFlush(order); audit(access, order, "UPDATED", "DRAFT", "DRAFT", null); return toRecord(order);
	}

	@Transactional
	public ProductionOrderRecord action(String username, UUID id, ProductionOrderRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireControlRole(access);
		ProductionOrderEntity order = requireOrder(access, id); requireVersion(order, request.expectedVersion()); String from = order.getStatus();
		String target = switch (request.action()) {
			case "RELEASE" -> { requireStatus(order, "DRAFT", "只有草稿生产订单可以下达");
				if (!bomReferenceProvider.hasEffectiveBom(access.tenantOrganizationId(), order.getMaterialId(), order.getPlannedStartDate()))
					throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "下达前必须存在计划开工日已生效的 BOM");
				if (!routingReferenceProvider.hasEffectiveRouting(access.tenantOrganizationId(), order.getMaterialId(), order.getPlannedStartDate()))
					throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "下达前必须存在计划开工日已生效的工艺路线");
				yield "RELEASED"; }
			case "START" -> { requireStatus(order, "RELEASED", "只有已下达生产订单可以开工"); yield "IN_PROGRESS"; }
			case "CANCEL" -> { if (!Set.of("DRAFT", "RELEASED").contains(order.getStatus())) throw conflict("只有草稿或未开工的已下达生产订单可以取消");
				if (request.comment() == null || request.comment().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "取消生产订单必须填写原因"); yield "CANCELLED"; }
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的生产订单动作");
		};
		order.transition(target, request.comment(), access.userId());
		if ("RELEASED".equals(target)) operationTaskService.createTasksForReleasedOrder(access, order);
		orderRepository.saveAndFlush(order);
		audit(access, order, request.action(), from, target, request.comment()); return toRecord(order);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScheduledReceipt> listReleasedProductionReceipts(UUID tenantId, Collection<UUID> materialIds, LocalDate horizonEnd) {
		if (materialIds.isEmpty()) return List.of();
		return orderRepository.findScheduledReceipts(tenantId, materialIds, horizonEnd).stream()
				.map(order -> new ScheduledReceipt(order.getId(), order.getOrderNumber(), order.getWorkshop(),
						order.getMaterialId(), order.getMaterialCode(), order.getMaterialName(), order.getUnit(),
						order.getOutstandingQuantity(), order.getPlannedReceiptDate()))
				.sorted(java.util.Comparator.comparing(ScheduledReceipt::expectedReceiptDate).thenComparing(ScheduledReceipt::orderNumber)).toList();
	}

	private ProductionOrderRecord toRecord(ProductionOrderEntity order) {
		return new ProductionOrderRecord(order.getId(), order.getOrderNumber(), order.getMaterialId(), order.getMaterialCode(),
				order.getMaterialName(), order.getMaterialSpecification(), order.getUnit(), order.getPlannedQuantity(),
				order.getCompletedQuantity(), order.getReportedQuantity(), order.getReportableQuantity(),
				order.getOutstandingQuantity(), order.getPlannedStartDate(),
				order.getPlannedReceiptDate(), order.getWorkshop(), order.getOwner(), order.getSourceType(), order.getSourceId(),
				order.getSourceNumber(), order.getStatus(), order.getCancellationReason(), order.getVersion(), order.getUpdatedAt());
	}

	private MaterialReference requireMakeMaterial(CurrentWorkspaceAccess access, UUID id) {
		MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), id);
		if (!"MAKE".equals(material.procurementType())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只有自制物料可以建立生产订单"); return material;
	}
	private ProductionOrderEntity requireOrder(CurrentWorkspaceAccess access, UUID id) { return orderRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产订单不存在或不在当前租户范围")); }
	private void audit(CurrentWorkspaceAccess access, ProductionOrderEntity order, String action, String from, String to, String comment) { eventRepository.save(new ProductionOrderEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(), order.getId(), action, from, to, MDC.get("requestId"), comment, Map.of("orderNumber", order.getOrderNumber(), "materialCode", order.getMaterialCode()))); }
	private String nextOrderNumber() { Long sequence = jdbcTemplate.queryForObject("select nextval('production.production_order_number_seq')", Long.class); return "MO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence); }
	private static void validateDates(LocalDate start, LocalDate receipt) { if (start.isAfter(receipt)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "计划开工日期不能晚于计划完工日期"); }
	private static void requireControlRole(CurrentWorkspaceAccess access) { if (!CONTROL_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护生产订单"); }
	private static void requireVersion(ProductionOrderEntity order, long expected) { if (order.getVersion() != expected) throw conflict("生产订单已经被其他用户修改，请刷新后重试"); }
	private static void requireStatus(ProductionOrderEntity order, String expected, String message) { if (!expected.equals(order.getStatus())) throw conflict(message); }
	private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
