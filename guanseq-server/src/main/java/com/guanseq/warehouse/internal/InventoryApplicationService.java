package com.guanseq.warehouse.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.warehouse.api.InventoryPage;
import com.guanseq.warehouse.api.InventoryRecord;
import com.guanseq.warehouse.api.InventoryReferenceData;
import com.guanseq.warehouse.api.ProductionMaterialStockService;
import com.guanseq.warehouse.api.PurchaseReceiptStockService;
import com.guanseq.warehouse.api.FinishedGoodsReceiptService;
import com.guanseq.warehouse.api.StockPositionProvider;
import com.guanseq.warehouse.api.WarehouseReferenceProvider;

@Service
public class InventoryApplicationService implements StockPositionProvider, FinishedGoodsReceiptService, ProductionMaterialStockService, PurchaseReceiptStockService, WarehouseReferenceProvider {

	private static final Set<String> MOVEMENT_ROLES = Set.of("WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final WarehouseRepository warehouseRepository;
	private final StorageLocationRepository locationRepository;
	private final StockBalanceRepository balanceRepository;
	private final StockMovementRepository movementRepository;
	private final JdbcTemplate jdbcTemplate;

	InventoryApplicationService(CurrentWorkspaceProvider workspaceProvider, WarehouseRepository warehouseRepository,
			StorageLocationRepository locationRepository, StockBalanceRepository balanceRepository,
			StockMovementRepository movementRepository, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.warehouseRepository = warehouseRepository;
		this.locationRepository = locationRepository; this.balanceRepository = balanceRepository;
		this.movementRepository = movementRepository; this.jdbcTemplate = jdbcTemplate;
	}

		@Override
	@Transactional(readOnly = true)
	public List<WarehouseOption> listActiveWarehouses(UUID tenantOrganizationId) {
		return warehouseRepository.findByTenantOrganizationIdAndStatusOrderByCodeAsc(tenantOrganizationId, "ACTIVE").stream()
				.map(item -> new WarehouseOption(item.getId(), item.getCode(), item.getName())).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<LocationOption> listActiveLocations(UUID tenantOrganizationId) {
		return locationRepository.findByTenantOrganizationIdAndStatusOrderByCodeAsc(tenantOrganizationId, "ACTIVE").stream()
				.map(item -> new LocationOption(item.getId(), item.getWarehouseId(), item.getCode(), item.getName(), item.getLocationType())).toList();
	}
@Transactional(readOnly = true)
	public InventoryPage list(String username, String query, String qualityStatus, String warehouseCode, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = balanceRepository.search(access.tenantOrganizationId(), normalize(query), normalizeFilter(qualityStatus),
				normalizeFilter(warehouseCode), PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new InventoryPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public InventoryRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireBalance(access, id));
	}

	@Transactional(readOnly = true)
	public InventoryReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return new InventoryReferenceData(
				warehouseRepository.findByTenantOrganizationIdAndStatusOrderByCodeAsc(access.tenantOrganizationId(), "ACTIVE")
						.stream().map(item -> new InventoryReferenceData.WarehouseOption(item.getId(), item.getCode(), item.getName())).toList(),
				locationRepository.findByTenantOrganizationIdAndStatusOrderByCodeAsc(access.tenantOrganizationId(), "ACTIVE")
						.stream().map(item -> new InventoryReferenceData.LocationOption(item.getId(), item.getWarehouseId(), item.getCode(), item.getName(), item.getLocationType())).toList());
	}

	@Transactional
	public InventoryRecord postMovement(String username, UUID id, InventoryRecord.MovementRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMovementRole(access);
		String requestId = MDC.get("requestId");
		if (requestId == null || requestId.isBlank()) requestId = "inventory-" + UUID.randomUUID();
		var existing = movementRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (existing.isPresent()) {
			if (!existing.get().getBalanceId().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "该请求编号已用于其他库存余额");
			return toRecord(requireBalance(access, id));
		}
		StockBalanceEntity balance = requireBalance(access, id);
		requireVersion(balance.getVersion(), request.expectedVersion());
		validateMovementQuality(balance, request.movementType());
		StockBalanceEntity.Change change;
		try {
			change = balance.apply(request.movementType(), request.quantity(), access.userId());
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"事务会导致现存量、已分配量或冻结量越界，请核对数量", exception);
		}
		try {
			balanceRepository.saveAndFlush(balance);
			movementRepository.saveAndFlush(new StockMovementEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), balance.getId(), nextMovementNumber(), request.movementType(), request.quantity(),
					request.reason(), requestId, change));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "库存余额已被其他事务更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "库存事务请求编号冲突，请刷新确认结果", exception);
		}
		return toRecord(balance);
	}

	@Override
	@Transactional
	public Receipt receive(FinishedGoodsReceiptService.Command command) {
		var existing = movementRepository.findByTenantOrganizationIdAndSourceTypeAndSourceId(
				command.tenantOrganizationId(), "PRODUCTION_REPORT", command.sourceId());
		if (existing.isPresent()) {
			StockMovementEntity movement = existing.get();
			StockBalanceEntity balance = balanceRepository.findByIdAndTenantOrganizationId(movement.getBalanceId(),
					command.tenantOrganizationId()).orElseThrow();
			return receipt(balance, movement);
		}
		WarehouseEntity warehouse = warehouseRepository.findByIdAndTenantOrganizationIdAndStatus(command.warehouseId(),
				command.tenantOrganizationId(), "ACTIVE").orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
						"成品入库仓库不存在、已停用或不在当前租户范围"));
		StorageLocationEntity location = locationRepository.findByIdAndTenantOrganizationIdAndStatus(command.locationId(),
				command.tenantOrganizationId(), "ACTIVE").orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
						"成品入库库位不存在、已停用或不在当前租户范围"));
		if (!location.getWarehouseId().equals(warehouse.getId())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
				"成品入库库位不属于所选仓库");
		String lot = command.lotNumber() == null ? "" : command.lotNumber().trim();
		StockBalanceEntity balance = balanceRepository
				.findByTenantOrganizationIdAndWarehouseIdAndLocationIdAndMaterialIdAndLotNumberAndQualityStatus(
						command.tenantOrganizationId(), warehouse.getId(), location.getId(), command.materialId(), lot, "AVAILABLE")
				.orElseGet(() -> new StockBalanceEntity(command.tenantOrganizationId(), command.owningOrganizationId(),
						command.workspaceId(), warehouse, location, command.materialId(), command.materialCode(), command.materialName(),
						command.materialSpecification(), command.unit(), lot, command.actorUserId()));
		StockBalanceEntity.Change change = balance.apply("RECEIPT", command.quantity(), command.actorUserId());
		balanceRepository.saveAndFlush(balance);
		StockMovementEntity movement = new StockMovementEntity(command.tenantOrganizationId(), command.workspaceId(),
				command.actorUserId(), balance.getId(), nextMovementNumber(), "RECEIPT", command.quantity(),
				"生产报工检验合格入库 · " + command.sourceNumber(), command.requestId(), change);
		movement.attachSource("PRODUCTION_REPORT", command.sourceId(), command.sourceNumber(), null);
		movementRepository.saveAndFlush(movement);
		return receipt(balance, movement);
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.Optional<Receipt> findBySource(UUID tenantOrganizationId, UUID sourceId) {
		return movementRepository.findByTenantOrganizationIdAndSourceTypeAndSourceId(tenantOrganizationId,
				"PRODUCTION_REPORT", sourceId).flatMap(movement -> balanceRepository
					.findByIdAndTenantOrganizationId(movement.getBalanceId(), tenantOrganizationId)
					.map(balance -> receipt(balance, movement)));
	}

	@Override
	@Transactional(readOnly = true)
	public List<StockPosition> getPositions(UUID tenantOrganizationId, Collection<UUID> materialIds) {
		Map<UUID, MutablePosition> positions = new LinkedHashMap<>();
		materialIds.forEach(id -> positions.putIfAbsent(id, new MutablePosition()));
		for (StockBalanceEntity balance : balanceRepository.findByTenantOrganizationIdAndMaterialIdIn(tenantOrganizationId, materialIds)) {
			MutablePosition position = positions.computeIfAbsent(balance.getMaterialId(), ignored -> new MutablePosition());
			position.onHand = position.onHand.add(balance.getOnHandQuantity());
			position.allocated = position.allocated.add(balance.getAllocatedQuantity());
			position.frozen = position.frozen.add(balance.getFrozenQuantity());
			position.available = position.available.add(balance.availableQuantity());
			position.count++;
		}
		List<StockPosition> result = new ArrayList<>();
		positions.forEach((materialId, item) -> result.add(new StockPosition(materialId, item.onHand, item.allocated,
				item.frozen, item.available, item.count)));
		return result;
	}

	private InventoryRecord toRecord(StockBalanceEntity balance) {
		List<InventoryRecord.Movement> movements = movementRepository.findByBalanceIdOrderByOccurredAtDesc(balance.getId())
				.stream().map(item -> new InventoryRecord.Movement(item.getId(), item.getMovementNumber(), item.getMovementType(),
						item.getQuantity(), item.getReason(), item.getRequestId(), item.getBeforeOnHand(), item.getAfterOnHand(),
						item.getBeforeAllocated(), item.getAfterAllocated(), item.getBeforeFrozen(), item.getAfterFrozen(),
						item.getOccurredAt())).toList();
		return new InventoryRecord(balance.getId(), balance.getWarehouseId(), balance.getWarehouseCode(), balance.getWarehouseName(),
				balance.getLocationId(), balance.getLocationCode(), balance.getLocationName(), balance.getMaterialId(),
				balance.getMaterialCode(), balance.getMaterialName(), balance.getMaterialSpecification(), balance.getUnit(),
				balance.getLotNumber(), balance.getQualityStatus(), balance.getOnHandQuantity(), balance.getAllocatedQuantity(),
				balance.getFrozenQuantity(), balance.availableQuantity(), balance.getVersion(), balance.getUpdatedAt(), movements);
	}

	@Override
	@Transactional
	public IssueResult issueMaterials(IssueCommand command) {
		if (command.lines() == null || command.lines().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发料行不能为空");
		WarehouseEntity warehouse = requireActiveWarehouse(command.tenantOrganizationId(), command.warehouseId(), "发料仓库不存在、已停用或不在当前租户范围");
		List<StockMovementResult> results = new ArrayList<>();
		for (int lineIndex = 0; lineIndex < command.lines().size(); lineIndex++) {
			IssueLine line = command.lines().get(lineIndex);
			if (line.quantity() == null || line.quantity().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发料数量必须大于零");
			BigDecimal remaining = line.quantity();
			List<StockBalanceEntity> balances = balanceRepository
					.findByTenantOrganizationIdAndWarehouseIdAndMaterialIdAndQualityStatusOrderByLocationCodeAscLotNumberAscUpdatedAtAsc(
							command.tenantOrganizationId(), warehouse.getId(), line.materialId(), "AVAILABLE");
			int movementIndex = 0;
			for (StockBalanceEntity balance : balances) {
				if (remaining.signum() <= 0) break;
				BigDecimal available = balance.availableQuantity();
				if (available.signum() <= 0) continue;
				BigDecimal quantity = remaining.min(available);
				StockBalanceEntity.Change change;
				try { change = balance.apply("ISSUE", quantity, command.actorUserId()); }
				catch (IllegalStateException exception) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "合格库存不足以完成发料", exception); }
				balanceRepository.saveAndFlush(balance);
				UUID sourceLineId = UUID.randomUUID();
				String requestId = command.requestId() + "-I" + lineIndex + "-" + movementIndex++;
				StockMovementEntity movement = new StockMovementEntity(command.tenantOrganizationId(), command.workspaceId(),
						command.actorUserId(), balance.getId(), nextMovementNumber(), "ISSUE", quantity,
						line.reason() == null || line.reason().isBlank() ? command.reason() : line.reason(), requestId, change);
				movement.attachSource(command.sourceType(), line.sourceId(), command.sourceNumber(), sourceLineId);
				movementRepository.saveAndFlush(movement);
				results.add(new StockMovementResult(line.sourceId(), sourceLineId, line.materialId(), balance.getId(), movement.getId(),
						movement.getMovementNumber(), balance.getWarehouseId(), balance.getWarehouseCode(), balance.getWarehouseName(),
						balance.getLocationId(), balance.getLocationCode(), balance.getLocationName(), balance.getLotNumber(), quantity));
				remaining = remaining.subtract(quantity);
			}
			if (remaining.signum() > 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"物料 " + line.materialCode() + " 在所选仓库合格库存不足，无法发料 " + line.quantity());
		}
		return new IssueResult(results);
	}

	@Override
	@Transactional
	public ReturnResult returnMaterials(ReturnCommand command) {
		if (command.lines() == null || command.lines().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退料行不能为空");
		WarehouseEntity warehouse = requireActiveWarehouse(command.tenantOrganizationId(), command.warehouseId(), "退料仓库不存在、已停用或不在当前租户范围");
		StorageLocationEntity location = locationRepository.findByIdAndTenantOrganizationIdAndStatus(command.locationId(),
				command.tenantOrganizationId(), "ACTIVE").orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
						"退料库位不存在、已停用或不在当前租户范围"));
		if (!location.getWarehouseId().equals(warehouse.getId())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "退料库位不属于所选仓库");
		List<StockMovementResult> results = new ArrayList<>();
		for (int lineIndex = 0; lineIndex < command.lines().size(); lineIndex++) {
			ReturnLine line = command.lines().get(lineIndex);
			if (line.quantity() == null || line.quantity().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退料数量必须大于零");
			StockBalanceEntity balance = balanceRepository
					.findByTenantOrganizationIdAndWarehouseIdAndLocationIdAndMaterialIdAndLotNumberAndQualityStatus(
							command.tenantOrganizationId(), warehouse.getId(), location.getId(), line.materialId(), "", "AVAILABLE")
					.orElseGet(() -> new StockBalanceEntity(command.tenantOrganizationId(), command.owningOrganizationId(),
							command.workspaceId(), warehouse, location, line.materialId(), line.materialCode(), line.materialName(),
							line.materialSpecification(), line.unit(), "", command.actorUserId()));
			StockBalanceEntity.Change change = balance.apply("RETURN", line.quantity(), command.actorUserId());
			balanceRepository.saveAndFlush(balance);
			UUID sourceLineId = UUID.randomUUID();
			String requestId = command.requestId() + "-R" + lineIndex;
			StockMovementEntity movement = new StockMovementEntity(command.tenantOrganizationId(), command.workspaceId(),
					command.actorUserId(), balance.getId(), nextMovementNumber(), "RETURN", line.quantity(),
					line.reason() == null || line.reason().isBlank() ? command.reason() : line.reason(), requestId, change);
			movement.attachSource(command.sourceType(), line.sourceId(), command.sourceNumber(), sourceLineId);
			movementRepository.saveAndFlush(movement);
			results.add(new StockMovementResult(line.sourceId(), sourceLineId, line.materialId(), balance.getId(), movement.getId(),
					movement.getMovementNumber(), balance.getWarehouseId(), balance.getWarehouseCode(), balance.getWarehouseName(),
					balance.getLocationId(), balance.getLocationCode(), balance.getLocationName(), balance.getLotNumber(), line.quantity()));
		}
		return new ReturnResult(results);
	}

	@Override
	@Transactional
	public StockReceipt receiveInspection(PurchaseReceiptStockService.Command command) {
		return receivePurchaseReceipt(command, "INSPECTION", command.requestId() + "-INSP");
	}

	@Override
	@Transactional
	public StockReceipt receiveAvailable(PurchaseReceiptStockService.Command command) {
		return receivePurchaseReceipt(command, "AVAILABLE", command.requestId() + "-AVAIL");
	}

	@Override
	@Transactional
	public Settlement settleInspection(SettleCommand command) {
		var existingAccepted = command.acceptedQuantity().signum() == 0 ? java.util.Optional.<StockMovementEntity>empty()
				: movementRepository.findByTenantOrganizationIdAndRequestId(command.tenantOrganizationId(), command.requestId() + "-ACCEPT");
		var existingRejected = command.rejectedQuantity().signum() == 0 ? java.util.Optional.<StockMovementEntity>empty()
				: movementRepository.findByTenantOrganizationIdAndRequestId(command.tenantOrganizationId(), command.requestId() + "-REJECT");
		if (existingAccepted.isPresent() || existingRejected.isPresent()) {
			StockMovementEntity acceptedMovement = existingAccepted.orElse(null);
			StockMovementEntity rejectedMovement = existingRejected.orElse(null);
			return new Settlement(acceptedMovement == null ? null : receiptForMovement(command.tenantOrganizationId(), acceptedMovement),
					rejectedMovement == null ? null : receiptForMovement(command.tenantOrganizationId(), rejectedMovement));
		}
		StockBalanceEntity inspection = balanceRepository.findByIdAndTenantOrganizationId(command.inspectionBalanceId(), command.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "来料待检库存不存在或不在当前租户范围"));
		if (!"INSPECTION".equals(inspection.getQualityStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只有待检库存可以结算来料检验");
		WarehouseEntity warehouse = requireActiveWarehouse(command.tenantOrganizationId(), inspection.getWarehouseId(), "来料入库仓库不存在、已停用或不在当前租户范围");
		StorageLocationEntity location = locationRepository.findByIdAndTenantOrganizationIdAndStatus(inspection.getLocationId(), command.tenantOrganizationId(), "ACTIVE")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "来料入库库位不存在、已停用或不在当前租户范围"));
		BigDecimal total = command.acceptedQuantity().add(command.rejectedQuantity());
		StockBalanceEntity.Change issueChange;
		try { issueChange = inspection.apply("ISSUE", total, command.actorUserId()); }
		catch (IllegalStateException ex) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "待检库存不足，无法完成检验结算", ex); }
		balanceRepository.saveAndFlush(inspection);
		StockMovementEntity issueMovement = new StockMovementEntity(command.tenantOrganizationId(), command.workspaceId(), command.actorUserId(),
				inspection.getId(), nextMovementNumber(), "ISSUE", total, "来料检验结算 · " + command.sourceNumber(),
				command.requestId() + "-SETTLE-ISSUE", issueChange);
		issueMovement.attachSource("PURCHASE_RECEIPT_LINE", command.sourceId(), command.sourceNumber(), UUID.randomUUID());
		movementRepository.saveAndFlush(issueMovement);
		StockReceipt accepted = null;
		if (command.acceptedQuantity().signum() > 0)
			accepted = receiveToBalance(command.tenantOrganizationId(), command.owningOrganizationId(), command.workspaceId(),
					command.actorUserId(), warehouse, location, command.materialId(), command.materialCode(), command.materialName(),
					command.materialSpecification(), command.unit(), command.lotNumber(), "AVAILABLE", command.acceptedQuantity(),
					"来料检验合格入库 · " + command.sourceNumber(), command.requestId() + "-ACCEPT", command.sourceId(),
					command.sourceNumber(), UUID.randomUUID());
		StockReceipt rejected = null;
		if (command.rejectedQuantity().signum() > 0)
			rejected = receiveToBalance(command.tenantOrganizationId(), command.owningOrganizationId(), command.workspaceId(),
					command.actorUserId(), warehouse, location, command.materialId(), command.materialCode(), command.materialName(),
					command.materialSpecification(), command.unit(), command.lotNumber(), "BLOCKED", command.rejectedQuantity(),
					"来料检验不合格隔离 · " + command.sourceNumber(), command.requestId() + "-REJECT", command.sourceId(),
					command.sourceNumber(), UUID.randomUUID());
		return new Settlement(accepted, rejected);
	}

	private StockReceipt receivePurchaseReceipt(PurchaseReceiptStockService.Command command, String qualityStatus, String requestId) {
		var existing = movementRepository.findByTenantOrganizationIdAndSourceTypeAndSourceIdAndSourceLineId(
				command.tenantOrganizationId(), "PURCHASE_RECEIPT_LINE", command.sourceId(), command.sourceLineId());
		if (existing.isPresent()) return receiptForMovement(command.tenantOrganizationId(), existing.get());
		WarehouseEntity warehouse = requireActiveWarehouse(command.tenantOrganizationId(), command.warehouseId(), "采购收货仓库不存在、已停用或不在当前租户范围");
		StorageLocationEntity location = locationRepository.findByIdAndTenantOrganizationIdAndStatus(command.locationId(), command.tenantOrganizationId(), "ACTIVE")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购收货库位不存在、已停用或不在当前租户范围"));
		if (!location.getWarehouseId().equals(warehouse.getId())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购收货库位不属于所选仓库");
		String reason = "INSPECTION".equals(qualityStatus) ? "采购到货待检 · " + command.sourceNumber() : "采购免检入库 · " + command.sourceNumber();
		return receiveToBalance(command.tenantOrganizationId(), command.owningOrganizationId(), command.workspaceId(), command.actorUserId(),
				warehouse, location, command.materialId(), command.materialCode(), command.materialName(), command.materialSpecification(),
				command.unit(), command.lotNumber(), qualityStatus, command.quantity(), reason, requestId, command.sourceId(),
				command.sourceNumber(), command.sourceLineId());
	}

	private StockReceipt receiveToBalance(UUID tenantId, UUID organizationId, UUID workspaceId, UUID actorId, WarehouseEntity warehouse,
			StorageLocationEntity location, UUID materialId, String materialCode, String materialName, String specification,
			String unit, String lot, String qualityStatus, BigDecimal quantity, String reason, String requestId, UUID sourceId,
			String sourceNumber, UUID sourceLineId) {
		String normalizedLot = lot == null ? "" : lot.trim();
		StockBalanceEntity balance = balanceRepository
				.findByTenantOrganizationIdAndWarehouseIdAndLocationIdAndMaterialIdAndLotNumberAndQualityStatus(
						tenantId, warehouse.getId(), location.getId(), materialId, normalizedLot, qualityStatus)
				.orElseGet(() -> new StockBalanceEntity(tenantId, organizationId, workspaceId, warehouse, location, materialId,
						materialCode, materialName, specification, unit, normalizedLot, qualityStatus, actorId));
		StockBalanceEntity.Change change = balance.apply("RECEIPT", quantity, actorId);
		balanceRepository.saveAndFlush(balance);
		StockMovementEntity movement = new StockMovementEntity(tenantId, workspaceId, actorId, balance.getId(), nextMovementNumber(),
				"RECEIPT", quantity, reason, requestId, change);
		movement.attachSource("PURCHASE_RECEIPT_LINE", sourceId, sourceNumber, sourceLineId);
		movementRepository.saveAndFlush(movement);
		return new StockReceipt(balance.getId(), movement.getId(), movement.getMovementNumber(), balance.getWarehouseCode(),
				balance.getWarehouseName(), balance.getLocationCode(), balance.getLocationName(), balance.getLotNumber(), qualityStatus);
	}

	private StockReceipt receiptForMovement(UUID tenantId, StockMovementEntity movement) {
		StockBalanceEntity balance = balanceRepository.findByIdAndTenantOrganizationId(movement.getBalanceId(), tenantId).orElseThrow();
		return new StockReceipt(balance.getId(), movement.getId(), movement.getMovementNumber(), balance.getWarehouseCode(),
				balance.getWarehouseName(), balance.getLocationCode(), balance.getLocationName(), balance.getLotNumber(),
				balance.getQualityStatus());
	}

	private WarehouseEntity requireActiveWarehouse(UUID tenantId, UUID warehouseId, String message) {
		return warehouseRepository.findByIdAndTenantOrganizationIdAndStatus(warehouseId, tenantId, "ACTIVE")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message));
	}
	private static Receipt receipt(StockBalanceEntity balance, StockMovementEntity movement) {
		return new Receipt(balance.getId(), movement.getId(), movement.getMovementNumber(), balance.getWarehouseCode(),
				balance.getWarehouseName(), balance.getLocationCode(), balance.getLocationName(), balance.getLotNumber());
	}

	private StockBalanceEntity requireBalance(CurrentWorkspaceAccess access, UUID id) {
		return balanceRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "库存余额不存在或不在当前租户范围"));
	}

	private static void validateMovementQuality(StockBalanceEntity balance, String movementType) {
		if (("ISSUE".equals(movementType) || "ALLOCATE".equals(movementType))
				&& !"AVAILABLE".equals(balance.getQualityStatus())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只有合格库存可以出库或分配");
		}
	}

	private String nextMovementNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('warehouse.movement_number_seq')", Long.class);
		return "MOV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private static void requireMovementRole(CurrentWorkspaceAccess access) {
		if (!MOVEMENT_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权过账库存事务");
	}
	private static void requireVersion(long current, long expected) {
		if (current != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "库存余额已变化，请刷新后重试");
	}
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeFilter(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }

	private static final class MutablePosition {
		private BigDecimal onHand = BigDecimal.ZERO;
		private BigDecimal allocated = BigDecimal.ZERO;
		private BigDecimal frozen = BigDecimal.ZERO;
		private BigDecimal available = BigDecimal.ZERO;
		private int count;
	}
}







