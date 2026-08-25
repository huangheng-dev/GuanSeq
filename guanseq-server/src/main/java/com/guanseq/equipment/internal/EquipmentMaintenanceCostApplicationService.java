package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.IssueRequest;
import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.LaborEntryRequest;
import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.LaborReversalRequest;
import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.MutationResult;
import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.ReturnRequest;
import com.guanseq.finance.api.StandardCostProvider;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.warehouse.api.ProductionMaterialStockService;
import com.guanseq.warehouse.api.WarehouseReferenceProvider;

@Service
public class EquipmentMaintenanceCostApplicationService {
	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentWorkOrderRepository workOrderRepository;
	private final EquipmentSparePartRepository sparePartRepository;
	private final MaintenanceSpareTransactionRepository spareTransactionRepository;
	private final MaintenanceLaborTransactionRepository laborTransactionRepository;
	private final StandardCostProvider standardCosts;
	private final WarehouseReferenceProvider warehouseReferences;
	private final ProductionMaterialStockService stockService;
	private final EquipmentMaintenanceCostQueryService queryService;

	EquipmentMaintenanceCostApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentWorkOrderRepository workOrderRepository, EquipmentSparePartRepository sparePartRepository,
			MaintenanceSpareTransactionRepository spareTransactionRepository,
			MaintenanceLaborTransactionRepository laborTransactionRepository, StandardCostProvider standardCosts,
			WarehouseReferenceProvider warehouseReferences, ProductionMaterialStockService stockService,
			EquipmentMaintenanceCostQueryService queryService) {
		this.workspaceProvider = workspaceProvider; this.workOrderRepository = workOrderRepository;
		this.sparePartRepository = sparePartRepository; this.spareTransactionRepository = spareTransactionRepository;
		this.laborTransactionRepository = laborTransactionRepository; this.standardCosts = standardCosts;
		this.warehouseReferences = warehouseReferences; this.stockService = stockService; this.queryService = queryService;
	}

	@Transactional
	public MutationResult issue(String username, UUID workOrderId, IssueRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		EquipmentSparePartApplicationService.requireMaintain(access);
		EquipmentWorkOrderEntity order = requireOpenRepair(access, workOrderId);
		String requestId = EquipmentSparePartApplicationService.requestId();
		var replay = spareTransactionRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return replay(access, order, replay.get().getWorkOrderId(), "该请求编号已用于其他维修备件事务");
		requireVersion(order, request.expectedVersion());
		EquipmentSparePartEntity spare = requireSpare(access, request.sparePartId());
		var warehouse = warehouseReferences.listActiveWarehouses(access.tenantOrganizationId()).stream()
				.filter(item -> item.id().equals(request.warehouseId())).findFirst()
				.orElseThrow(() -> invalid("领用仓库不存在、已停用或不在当前租户范围"));
		var cost = standardCosts.findEffectiveCost(access.tenantOrganizationId(), spare.getMaterialId(), LocalDate.now(ZoneOffset.UTC))
				.orElseThrow(() -> invalid("该备件缺少当前生效的标准成本，不能生成完整维修成本证据"));
		requireCurrency(order.getId(), cost.currency());
		UUID transactionId = UUID.randomUUID();
		var stockResult = stockService.issueMaterials(new ProductionMaterialStockService.IssueCommand(
				access.tenantOrganizationId(), access.operatingOrganizationId(), access.workspaceId(), access.userId(),
				requestId, warehouse.id(), "EQUIPMENT_SPARE_TRANSACTION", order.getWorkOrderNumber(),
				List.of(new ProductionMaterialStockService.IssueLine(transactionId, spare.getMaterialId(), spare.getMaterialCode(),
						spare.getMaterialName(), spare.getMaterialSpecification(), spare.getUnit(), request.quantity(), request.reason())),
				request.reason()));
		BigDecimal amount = money(request.quantity().multiply(cost.unitCost()));
		var transaction = new MaintenanceSpareTransactionEntity(transactionId, access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), order.getId(), spare, "ISSUE", null, request.quantity(), cost.unitCost(), cost.currency(), amount,
				warehouse.id(), warehouse.code(), warehouse.name(), evidence(stockResult.movements()), request.reason(), requestId);
		return save(access, order, transaction, null);
	}

	@Transactional
	public MutationResult returnSpare(String username, UUID workOrderId, ReturnRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		EquipmentSparePartApplicationService.requireMaintain(access);
		EquipmentWorkOrderEntity order = requireOpenRepair(access, workOrderId);
		String requestId = EquipmentSparePartApplicationService.requestId();
		var replay = spareTransactionRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return replay(access, order, replay.get().getWorkOrderId(), "该请求编号已用于其他维修备件事务");
		requireVersion(order, request.expectedVersion());
		MaintenanceSpareTransactionEntity issue = spareTransactionRepository
				.findByIdAndTenantOrganizationIdAndWorkspaceId(request.issueTransactionId(), access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> notFound("原领用事务不存在或不在当前工作区范围"));
		if (!issue.getWorkOrderId().equals(order.getId()) || !"ISSUE".equals(issue.getTransactionType())) {
			throw invalid("退回必须引用当前维修工单的原领用事务");
		}
		BigDecimal returned = spareTransactionRepository.returnedQuantity(issue.getId());
		if (returned.add(request.quantity()).compareTo(issue.getQuantity()) > 0) throw invalid("累计退回数量不能超过原领用数量");
		EquipmentSparePartEntity spare = requireSpare(access, issue.getSparePartId());
		UUID transactionId = UUID.randomUUID();
		var result = stockService.returnMaterials(new ProductionMaterialStockService.ReturnCommand(
				access.tenantOrganizationId(), access.operatingOrganizationId(), access.workspaceId(), access.userId(), requestId,
				issue.getWarehouseId(), request.locationId(), "EQUIPMENT_SPARE_TRANSACTION", order.getWorkOrderNumber(),
				List.of(new ProductionMaterialStockService.ReturnLine(transactionId, spare.getMaterialId(), spare.getMaterialCode(),
						spare.getMaterialName(), spare.getMaterialSpecification(), spare.getUnit(), request.quantity(), request.reason())),
				request.reason()));
		BigDecimal amount = money(request.quantity().multiply(issue.getUnitCost()));
		var transaction = new MaintenanceSpareTransactionEntity(transactionId, access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), order.getId(), spare, "RETURN", issue.getId(), request.quantity(), issue.getUnitCost(),
				issue.getCurrency(), amount, issue.getWarehouseId(), issue.getWarehouseCode(), issue.getWarehouseName(),
				evidence(result.movements()), request.reason(), requestId);
		return save(access, order, transaction, null);
	}

	@Transactional
	public MutationResult recordLabor(String username, UUID workOrderId, LaborEntryRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		EquipmentSparePartApplicationService.requireMaintain(access);
		EquipmentWorkOrderEntity order = requireOpenRepair(access, workOrderId);
		String requestId = EquipmentSparePartApplicationService.requestId();
		var replay = laborTransactionRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return replay(access, order, replay.get().getWorkOrderId(), "该请求编号已用于其他维修人工事务");
		requireVersion(order, request.expectedVersion());
		String currency = request.currency().trim().toUpperCase();
		if (!currency.matches("[A-Z]{3}")) throw invalid("币种必须是三个大写字母");
		requireCurrency(order.getId(), currency);
		BigDecimal amount = money(request.hours().multiply(request.hourlyRate()));
		var transaction = new MaintenanceLaborTransactionEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), order.getId(), "ENTRY", null, request.technicianName(), request.hours(), request.hourlyRate(),
				currency, amount, request.reason(), requestId);
		return save(access, order, null, transaction);
	}

	@Transactional
	public MutationResult reverseLabor(String username, UUID workOrderId, UUID entryId, LaborReversalRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		EquipmentSparePartApplicationService.requireMaintain(access);
		EquipmentWorkOrderEntity order = requireOpenRepair(access, workOrderId);
		String requestId = EquipmentSparePartApplicationService.requestId();
		var replay = laborTransactionRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return replay(access, order, replay.get().getWorkOrderId(), "该请求编号已用于其他维修人工事务");
		requireVersion(order, request.expectedVersion());
		MaintenanceLaborTransactionEntity entry = laborTransactionRepository
				.findByIdAndTenantOrganizationIdAndWorkspaceId(entryId, access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> notFound("人工登记不存在或不在当前工作区范围"));
		if (!entry.getWorkOrderId().equals(order.getId()) || !"ENTRY".equals(entry.getTransactionType())) {
			throw invalid("只能冲销当前维修工单的原人工登记");
		}
		if (laborTransactionRepository.existsByReversalOfEntryId(entry.getId())) throw conflict("该人工登记已经冲销");
		var reversal = new MaintenanceLaborTransactionEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				order.getId(), "REVERSAL", entry.getId(), entry.getTechnicianName(), entry.getHours(), entry.getHourlyRate(),
				entry.getCurrency(), entry.getAmount(), request.reason(), requestId);
		return save(access, order, null, reversal);
	}

	private MutationResult save(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order,
			MaintenanceSpareTransactionEntity spare, MaintenanceLaborTransactionEntity labor) {
		try {
			if (spare != null) spareTransactionRepository.saveAndFlush(spare);
			if (labor != null) laborTransactionRepository.saveAndFlush(labor);
			order.touch(access.userId());
			workOrderRepository.saveAndFlush(order);
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "维修工单已被其他用户修改，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "请求编号、退回或人工冲销与已有事实冲突，请刷新确认结果", exception);
		}
		return new MutationResult(order.getVersion(), queryService.get(order));
	}

	private MutationResult replay(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order, UUID transactionOrderId, String message) {
		if (!order.getId().equals(transactionOrderId)) throw conflict(message);
		return new MutationResult(order.getVersion(), queryService.get(order));
	}

	private void requireCurrency(UUID workOrderId, String currency) {
		String existing = spareTransactionRepository.findByWorkOrderIdOrderByOccurredAtDesc(workOrderId).stream()
				.map(MaintenanceSpareTransactionEntity::getCurrency).findFirst()
				.orElseGet(() -> laborTransactionRepository.findByWorkOrderIdOrderByOccurredAtDesc(workOrderId).stream()
						.map(MaintenanceLaborTransactionEntity::getCurrency).findFirst().orElse(null));
		if (existing != null && !existing.equals(currency)) throw invalid("同一维修工单只能使用一种成本币种，当前币种为 " + existing);
	}

	private EquipmentWorkOrderEntity requireOpenRepair(CurrentWorkspaceAccess access, UUID id) {
		EquipmentWorkOrderEntity order = workOrderRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id,
				access.tenantOrganizationId(), access.workspaceId()).orElseThrow(() -> notFound("维修工单不存在或不在当前工作区范围"));
		if (!"REPAIR".equals(order.getWorkType()) || !"IN_PROGRESS".equals(order.getStatus())) {
			throw conflict("只有执行中的维修工单可以变更备件或人工成本证据");
		}
		return order;
	}

	private EquipmentSparePartEntity requireSpare(CurrentWorkspaceAccess access, UUID id) {
		EquipmentSparePartEntity spare = sparePartRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id,
				access.tenantOrganizationId(), access.workspaceId()).orElseThrow(() -> notFound("备件不存在或不在当前工作区范围"));
		if (!"ACTIVE".equals(spare.getStatus())) throw invalid("备件已停用，不能领用或退回");
		return spare;
	}

	private static List<Map<String, Object>> evidence(List<ProductionMaterialStockService.StockMovementResult> movements) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (var item : movements) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("movementId", item.movementId()); row.put("movementNumber", item.movementNumber());
			row.put("balanceId", item.balanceId()); row.put("warehouseId", item.warehouseId());
			row.put("warehouseCode", item.warehouseCode()); row.put("locationId", item.locationId());
			row.put("locationCode", item.locationCode()); row.put("locationName", item.locationName());
			row.put("lotNumber", item.lotNumber()); row.put("quantity", item.quantity());
			result.add(row);
		}
		return result;
	}

	private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
	private static void requireVersion(EquipmentWorkOrderEntity order, long expected) {
		if (order.getVersion() != expected) throw conflict("维修工单已被其他用户修改，请刷新后重试");
	}
	private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
	private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
