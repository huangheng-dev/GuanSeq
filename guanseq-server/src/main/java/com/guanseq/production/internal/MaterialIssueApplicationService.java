package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.guanseq.product.api.BomReferenceProvider;
import com.guanseq.product.api.BomReferenceProvider.EffectiveBom;
import com.guanseq.production.api.MaterialIssuePage;
import com.guanseq.production.api.MaterialIssueRecord;
import com.guanseq.production.api.MaterialIssueReferenceData;
import com.guanseq.production.api.MaterialReturnRecord;
import com.guanseq.warehouse.api.ProductionMaterialStockService;
import com.guanseq.warehouse.api.ProductionMaterialStockService.IssueCommand;
import com.guanseq.warehouse.api.ProductionMaterialStockService.IssueLine;
import com.guanseq.warehouse.api.ProductionMaterialStockService.ReturnCommand;
import com.guanseq.warehouse.api.ProductionMaterialStockService.ReturnLine;
import com.guanseq.warehouse.api.ProductionMaterialStockService.StockMovementResult;
import com.guanseq.warehouse.api.WarehouseReferenceProvider;
import com.guanseq.warehouse.api.WarehouseReferenceProvider.LocationOption;
import com.guanseq.warehouse.api.WarehouseReferenceProvider.WarehouseOption;

@Service
public class MaterialIssueApplicationService {
	private static final Set<String> CONTROL_ROLES = Set.of("PRODUCTION_MANAGER", "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN");
	private static final int SCALE = 6;

	private final CurrentWorkspaceProvider workspaceProvider;
	private final ProductionOrderRepository orderRepository;
	private final MaterialIssueRepository issueRepository;
	private final MaterialIssueLineRepository lineRepository;
	private final MaterialIssueEventRepository eventRepository;
	private final MaterialReturnRepository returnRepository;
	private final MaterialReturnLineRepository returnLineRepository;
	private final MaterialStockTransactionRepository transactionRepository;
	private final BomReferenceProvider bomReferenceProvider;
	private final WarehouseReferenceProvider warehouseReferenceProvider;
	private final ProductionMaterialStockService stockService;
	private final JdbcTemplate jdbcTemplate;

	MaterialIssueApplicationService(CurrentWorkspaceProvider workspaceProvider, ProductionOrderRepository orderRepository,
			MaterialIssueRepository issueRepository, MaterialIssueLineRepository lineRepository,
			MaterialIssueEventRepository eventRepository, MaterialReturnRepository returnRepository,
			MaterialReturnLineRepository returnLineRepository, MaterialStockTransactionRepository transactionRepository,
			BomReferenceProvider bomReferenceProvider, WarehouseReferenceProvider warehouseReferenceProvider,
			ProductionMaterialStockService stockService, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.orderRepository = orderRepository; this.issueRepository = issueRepository;
		this.lineRepository = lineRepository; this.eventRepository = eventRepository; this.returnRepository = returnRepository;
		this.returnLineRepository = returnLineRepository; this.transactionRepository = transactionRepository;
		this.bomReferenceProvider = bomReferenceProvider; this.warehouseReferenceProvider = warehouseReferenceProvider;
		this.stockService = stockService; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public MaterialIssuePage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = issueRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new MaterialIssuePage(result.getContent().stream().map(item -> toRecord(access, item)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public MaterialIssueRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		MaterialIssueEntity issue = requireIssue(access, id);
		return toRecord(access, requireIssue(access, id));
	}

	@Transactional(readOnly = true)
	public MaterialIssueReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		boolean canControl = CONTROL_ROLES.contains(access.roleCode());
		List<ProductionOrderEntity> released = orderRepository.search(access.tenantOrganizationId(), "", "RELEASED",
				PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent();
		List<ProductionOrderEntity> inProgress = orderRepository.search(access.tenantOrganizationId(), "", "IN_PROGRESS",
				PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent();
		List<MaterialIssueReferenceData.ProductionOrderOption> orders = new ArrayList<>();
		for (ProductionOrderEntity order : released) addCandidate(access, order, orders);
		for (ProductionOrderEntity order : inProgress) addCandidate(access, order, orders);
		List<MaterialIssueEntity> activeIssues = new ArrayList<>();
		activeIssues.addAll(issueRepository.search(access.tenantOrganizationId(), "", "DRAFT",
				PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent());
		activeIssues.addAll(issueRepository.search(access.tenantOrganizationId(), "", "PARTIAL",
				PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent());
		Set<UUID> warehouseIds = activeIssues.stream().map(MaterialIssueEntity::getWarehouseId).collect(Collectors.toSet());
		Set<UUID> materialIds = activeIssues.stream()
				.flatMap(issue -> lineRepository.findByTenantOrganizationIdAndIssueIdOrderByLineNumberAsc(
						access.tenantOrganizationId(), issue.getId()).stream())
				.map(MaterialIssueLineEntity::getComponentMaterialId).collect(Collectors.toSet());
		List<MaterialIssueReferenceData.AvailableStockOption> availableStocks = (canControl ? stockService
				.listAvailableBalances(access.tenantOrganizationId(), warehouseIds, materialIds).stream()
				.map(item -> new MaterialIssueReferenceData.AvailableStockOption(item.id(), item.warehouseId(), item.warehouseCode(),
						item.locationId(), item.locationCode(), item.locationName(), item.materialId(), item.materialCode(),
						item.lotNumber(), item.availableQuantity(), item.version())).toList() : List.of());
		return new MaterialIssueReferenceData(canControl, orders,
				warehouseReferenceProvider.listActiveWarehouses(access.tenantOrganizationId()),
				warehouseReferenceProvider.listActiveLocations(access.tenantOrganizationId()).stream()
						.map(item -> new MaterialIssueReferenceData.LocationOption(item.id(), item.warehouseId(), item.code(), item.name(), item.locationType())).toList(),
				availableStocks);
	}

	@Transactional
	public MaterialIssueRecord create(String username, MaterialIssueRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireControlRole(access);
		String requestId = currentRequestId("material-issue-create-");
		var duplicate = issueRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) return toRecord(access, duplicate.get());
		ProductionOrderEntity order = orderRepository.findByIdAndTenantOrganizationId(request.productionOrderId(), access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产订单不存在或不在当前租户范围"));
		if (!Set.of("RELEASED", "IN_PROGRESS").contains(order.getStatus())) throw conflict("只有已下达或执行中的生产订单可以生成领料单");
		if (issueRepository.existsByTenantOrganizationIdAndProductionOrderIdAndStatusNot(access.tenantOrganizationId(), order.getId(), "CANCELLED"))
			throw conflict("该生产订单已有未取消领料单");
		WarehouseOption warehouse = warehouseReferenceProvider.listActiveWarehouses(access.tenantOrganizationId()).stream()
				.filter(item -> item.id().equals(request.warehouseId())).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "发料仓库不存在、已停用或不在当前租户范围"));
		EffectiveBom bom = bomReferenceProvider.findEffectiveBom(access.tenantOrganizationId(), order.getMaterialId(), order.getPlannedStartDate())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "计划开工日没有已生效 BOM，无法生成领料需求"));
		MaterialIssueEntity issue;
		try {
			issue = new MaterialIssueEntity(access.tenantOrganizationId(), access.operatingOrganizationId(), access.workspaceId(),
					nextIssueNumber(), order, warehouse, requestId, access.userId());
			issueRepository.saveAndFlush(issue);
			int lineNumber = 10;
			for (var component : bom.components()) {
				BigDecimal required = order.getPlannedQuantity().multiply(component.quantity())
						.divide(bom.baseQuantity(), SCALE, RoundingMode.HALF_UP)
						.multiply(BigDecimal.ONE.add(component.scrapRate())).setScale(SCALE, RoundingMode.HALF_UP);
				lineRepository.saveAndFlush(issue.createLine(lineNumber, component, required, access.userId()));
				lineNumber += 10;
			}
			eventRepository.save(new MaterialIssueEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
					issue.getId(), "CREATED", null, "DRAFT", "DESKTOP_FORM", requestId, null,
					Map.of("issueNumber", issue.getIssueNumber(), "orderNumber", order.getOrderNumber())));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("该生产订单已有未取消领料单或请求编号冲突，请刷新确认");
		}
		return toRecord(access, issue);
	}

	@Transactional
	public MaterialIssueRecord action(String username, UUID id, MaterialIssueRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireControlRole(access);
		String actionRequestId = currentRequestId("material-issue-action-");
		MaterialIssueEntity issue = requireIssue(access, id);
		var completedAction = eventRepository.findFirstByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), actionRequestId);
		if (completedAction.isPresent()) {
			if (!completedAction.get().getIssueId().equals(id)) throw conflict("请求编号已用于其他领料单动作，请刷新后重试");
			return toRecord(access, issue);
		}
		if (issue.getVersion() != request.expectedVersion()) throw conflict("领料单已被其他用户修改，请刷新后重试");
		List<MaterialIssueLineEntity> lines = lineRepository.findByTenantOrganizationIdAndIssueIdOrderByLineNumberAsc(access.tenantOrganizationId(), id);
		var from = issue.getStatus();
		return switch (request.action()) {
			case "ISSUE" -> issueMaterials(access, issue, lines, request, actionRequestId);
			case "CANCEL" -> cancel(access, issue, request, from, actionRequestId);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的领料单动作");
		};
	}

	@Transactional
	public MaterialIssueRecord createReturn(String username, UUID id, MaterialIssueRecord.ReturnRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireControlRole(access);
		MaterialIssueEntity issue = requireIssue(access, id);
		if (!Set.of("PARTIAL", "ISSUED").contains(issue.getStatus())) throw conflict("只有已发料的领料单可以退料");
		LocationOption location = warehouseReferenceProvider.listActiveLocations(access.tenantOrganizationId()).stream()
				.filter(item -> item.id().equals(request.locationId())).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "退料库位不存在、已停用或不在当前租户范围"));
		if (!location.warehouseId().equals(issue.getWarehouseId())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "退料库位不属于领料仓库");
		String requestId = currentRequestId("material-return-create-");
		var duplicateReturn = returnRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicateReturn.isPresent()) {
			if (!duplicateReturn.get().getIssueId().equals(id)) throw conflict("请求编号已用于其他退料单，请刷新后重试");
			return toRecord(access, issue);
		}
		MaterialReturnEntity materialReturn = new MaterialReturnEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextReturnNumber(), issue, location, request.reason().trim(), requestId, access.userId());
		returnRepository.saveAndFlush(materialReturn);
		Map<UUID, MaterialIssueLineEntity> lineMap = lineRepository.findByTenantOrganizationIdAndIssueIdOrderByLineNumberAsc(access.tenantOrganizationId(), id)
				.stream().collect(Collectors.toMap(MaterialIssueLineEntity::getId, Function.identity()));
		List<ReturnLine> stockLines = new ArrayList<>();
		List<MaterialReturnLineEntity> returnLines = new ArrayList<>();
		for (var requestLine : request.lines()) {
			MaterialIssueLineEntity line = lineMap.get(requestLine.lineId());
			if (line == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退料行不属于当前领料单");
			if (line.getVersion() != requestLine.expectedLineVersion()) throw conflict("组件 " + line.getComponentMaterialCode() + " 已被其他事务更新，请刷新后重试");
			try { line.returnMaterial(requestLine.quantity(), access.userId()); }
			catch (IllegalArgumentException | IllegalStateException exception) { throw unprocessable(exception.getMessage()); }
			MaterialReturnLineEntity returnLine = new MaterialReturnLineEntity(access.tenantOrganizationId(), materialReturn.getId(), line,
					requestLine.quantity(), requestLine.reason());
			returnLineRepository.saveAndFlush(returnLine); returnLines.add(returnLine);
			stockLines.add(new ReturnLine(returnLine.getId(), line.getComponentMaterialId(), line.getComponentMaterialCode(),
					line.getComponentMaterialName(), line.getComponentMaterialSpecification(), line.getUnit(),
					requestLine.quantity(), requestLine.reason()));
		}
		var result = stockService.returnMaterials(new ReturnCommand(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), access.userId(), requestId, issue.getWarehouseId(), request.locationId(),
				"PRODUCTION_RETURN", materialReturn.getReturnNumber(), stockLines, request.reason()));
		saveTransactions(access, issue.getId(), null, materialReturn.getId(), returnLines, "RETURN", result.movements(), "DESKTOP_FORM", requestId);
		lineRepository.saveAll(lineMap.values());
		eventRepository.save(new MaterialIssueEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				issue.getId(), "RETURN", issue.getStatus(), issue.getStatus(), "DESKTOP_FORM", requestId, request.reason(),
				Map.of("returnNumber", materialReturn.getReturnNumber(), "lineCount", request.lines().size())));
		return toRecord(access, issue);
	}

	private MaterialIssueRecord issueMaterials(CurrentWorkspaceAccess access, MaterialIssueEntity issue, List<MaterialIssueLineEntity> lines,
			MaterialIssueRecord.ActionRequest request, String actionRequestId) {
		String source = normalizeSource(request.source());
		if (!Set.of("DRAFT", "PARTIAL").contains(issue.getStatus())) throw conflict("当前领料单状态不允许发料");
		if (request.lines() == null || request.lines().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择至少一行发料");
		Map<UUID, MaterialIssueLineEntity> lineMap = lines.stream().collect(Collectors.toMap(MaterialIssueLineEntity::getId, Function.identity()));
		List<IssueLine> stockLines = new ArrayList<>();
		for (var requestLine : request.lines()) {
			MaterialIssueLineEntity line = lineMap.get(requestLine.lineId());
			if (line == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发料行不属于当前领料单");
			if (line.getVersion() != requestLine.expectedLineVersion()) throw conflict("组件 " + line.getComponentMaterialCode() + " 已被其他事务更新，请刷新后重试");
			try { line.issue(requestLine.quantity(), access.userId()); }
			catch (IllegalArgumentException | IllegalStateException exception) { throw unprocessable(exception.getMessage()); }
			stockLines.add(new IssueLine(line.getId(), line.getComponentMaterialId(), line.getComponentMaterialCode(),
					line.getComponentMaterialName(), line.getComponentMaterialSpecification(), line.getUnit(),
					requestLine.quantity(), request.comment(), requestLine.stockBalanceId(), requestLine.expectedStockVersion()));
		}
		var result = stockService.issueMaterials(new IssueCommand(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), access.userId(), actionRequestId, issue.getWarehouseId(),
				"PRODUCTION_ISSUE", issue.getIssueNumber(), stockLines, request.comment() == null ? "生产领料" : request.comment()));
		saveTransactions(access, issue.getId(), lineMap, null, null, "ISSUE", result.movements(), source, actionRequestId);
		lineRepository.saveAll(lineMap.values());
		String from = issue.getStatus();
		issue.markIssuedIfComplete(lines, access.userId());
		issueRepository.saveAndFlush(issue);
		eventRepository.save(new MaterialIssueEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				issue.getId(), "ISSUE", from, issue.getStatus(), source, actionRequestId, request.comment(),
				Map.of("lineCount", request.lines().size(), "complete", "ISSUED".equals(issue.getStatus()))));
		return toRecord(access, issue);
	}

	private MaterialIssueRecord cancel(CurrentWorkspaceAccess access, MaterialIssueEntity issue, MaterialIssueRecord.ActionRequest request, String from, String actionRequestId) {
		if (request.comment() == null || request.comment().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "取消领料单必须填写原因");
		try { issue.cancel(request.comment().trim(), access.userId()); }
		catch (IllegalStateException exception) { throw conflict(exception.getMessage()); }
		issueRepository.saveAndFlush(issue);
		eventRepository.save(new MaterialIssueEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				issue.getId(), "CANCEL", from, "CANCELLED", "DESKTOP_FORM", actionRequestId, request.comment(), Map.of()));
		return toRecord(access, issue);
	}

	private void saveTransactions(CurrentWorkspaceAccess access, UUID issueId, Map<UUID, MaterialIssueLineEntity> issueLines,
			UUID returnId, List<MaterialReturnLineEntity> returnLines, String movementType, List<StockMovementResult> movements,
			String source, String requestId) {
		for (StockMovementResult movement : movements) {
			UUID issueLineId = "ISSUE".equals(movementType) ? movement.sourceId() : null;
			UUID returnLineId = "RETURN".equals(movementType) ? movement.sourceId() : null;
			String materialCode = null;
			BigDecimal lineQuantity = null;
			if ("ISSUE".equals(movementType) && issueLines != null) {
				MaterialIssueLineEntity line = issueLines.get(movement.sourceId());
				materialCode = line.getComponentMaterialCode(); lineQuantity = line.getIssuedQuantity();
			} else if ("RETURN".equals(movementType) && returnLines != null) {
				MaterialReturnLineEntity line = returnLines.stream().filter(item -> item.getId().equals(movement.sourceId())).findFirst().orElseThrow();
				materialCode = line.getComponentMaterialCode(); lineQuantity = line.getQuantity();
			}
			transactionRepository.save(new MaterialStockTransactionEntity(access.tenantOrganizationId(), issueId, issueLineId,
					returnId, returnLineId, movementType, movement.materialId(), materialCode, movement.quantity(),
					movement, source, requestId, access.userId()));
		}
	}

	private void addCandidate(CurrentWorkspaceAccess access, ProductionOrderEntity order, List<MaterialIssueReferenceData.ProductionOrderOption> target) {
		if (issueRepository.existsByTenantOrganizationIdAndProductionOrderIdAndStatusNot(access.tenantOrganizationId(), order.getId(), "CANCELLED")) return;
		target.add(new MaterialIssueReferenceData.ProductionOrderOption(order.getId(), order.getOrderNumber(), order.getMaterialId(),
				order.getMaterialCode(), order.getMaterialName(), order.getMaterialSpecification(), order.getUnit(),
				order.getPlannedQuantity(), order.getPlannedStartDate(), order.getWorkshop(), order.getOwner()));
	}

	private List<MaterialReturnRecord> loadReturns(UUID tenantId, UUID issueId) {
		return returnRepository.findByTenantOrganizationIdAndIssueIdOrderByCreatedAtDesc(tenantId, issueId).stream()
				.map(item -> new MaterialReturnRecord(item.getId(), item.getReturnNumber(), item.getIssueId(), item.getIssueNumber(),
						item.getProductionOrderId(), item.getOrderNumber(), item.getWarehouseId(), item.getWarehouseCode(),
						item.getWarehouseName(), item.getLocationId(), item.getLocationCode(), item.getLocationName(),
						item.getReason(), item.getCreatedAt(),
						returnLineRepository.findByReturnIdOrderByLineNumberAsc(item.getId()).stream().map(item::toLine).toList())).toList();
	}

	private MaterialIssueRecord toRecord(CurrentWorkspaceAccess access, MaterialIssueEntity issue) {
		return toRecord(issue, lineRepository.findByTenantOrganizationIdAndIssueIdOrderByLineNumberAsc(access.tenantOrganizationId(), issue.getId()),
				loadReturns(access.tenantOrganizationId(), issue.getId()), eventRepository.findByIssueIdOrderByOccurredAtDesc(issue.getId()),
				transactionRepository.findByTenantOrganizationIdAndIssueIdOrderByOccurredAtDesc(access.tenantOrganizationId(), issue.getId()));
	}

	private MaterialIssueRecord toRecord(MaterialIssueEntity issue, List<MaterialIssueLineEntity> lines, List<MaterialReturnRecord> returns,
			List<MaterialIssueEventEntity> events, List<MaterialStockTransactionEntity> stockTransactions) {
		return new MaterialIssueRecord(issue.getId(), issue.getIssueNumber(), issue.getProductionOrderId(), issue.getOrderNumber(),
				issue.getMaterialId(), issue.getMaterialCode(), issue.getMaterialName(), issue.getMaterialSpecification(), issue.getUnit(),
				issue.getPlannedQuantity(), issue.getWarehouseId(), issue.getWarehouseCode(), issue.getWarehouseName(), issue.getStatus(),
				issue.getCancellationReason(), issue.getVersion(), issue.getCreatedAt(), issue.getUpdatedAt(),
				lines.stream().map(line -> new MaterialIssueRecord.Line(line.getId(), line.getLineNumber(), line.getComponentMaterialId(),
						line.getComponentMaterialCode(), line.getComponentMaterialName(), line.getComponentMaterialSpecification(),
						line.getUnit(), line.getRequiredQuantity(), line.getIssuedQuantity(), line.getReturnedQuantity(),
						line.issuableQuantity(), line.getBomNote(), line.getVersion())).toList(),
				returns,
				events.stream().map(event -> new MaterialIssueRecord.Event(event.getId(), event.getAction(), event.getFromStatus(),
						event.getToStatus(), event.getSource(), event.getRequestId(), event.getOccurredAt())).toList(),
				stockTransactions.stream().map(txn -> new MaterialIssueRecord.StockTransaction(txn.getId(), txn.getIssueLineId(),
						txn.getReturnLineId(), txn.getMovementType(), txn.getComponentMaterialCode(), txn.getQuantity(),
						txn.getWarehouseId(), txn.getWarehouseCode(), txn.getWarehouseName(), txn.getLocationId(),
						txn.getLocationCode(), txn.getLocationName(), txn.getBalanceId(), txn.getLotNumber(), txn.getMovementId(), txn.getMovementNumber(),
						txn.getSource(), txn.getRequestId(), txn.getOccurredAt())).toList());
	}

	private MaterialIssueEntity requireIssue(CurrentWorkspaceAccess access, UUID id) {
		return issueRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产领料单不存在或不在当前租户范围"));
	}

	private String nextIssueNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('production.material_issue_number_seq')", Long.class);
		return "PI-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private String nextReturnNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('production.material_return_number_seq')", Long.class);
		return "RT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private static String currentRequestId(String prefix) {
		String requestId = MDC.get("requestId");
		return requestId == null || requestId.isBlank() ? prefix + UUID.randomUUID() : requestId;
	}
	private static void requireControlRole(CurrentWorkspaceAccess access) { if (!CONTROL_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护生产备料和领退料"); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private static ResponseStatusException unprocessable(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
	private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static String normalizeSource(String value) { return value == null || value.isBlank() ? "DESKTOP_FORM" : value.trim().toUpperCase(); }
}
