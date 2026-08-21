package com.guanseq.procurement.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.guanseq.procurement.api.PurchaseReceiptPage;
import com.guanseq.procurement.api.PurchaseReceiptRecord;
import com.guanseq.procurement.api.PurchaseReceiptReferenceData;
import com.guanseq.quality.api.IncomingInspectionProvider;
import com.guanseq.warehouse.api.PurchaseReceiptStockService;
import com.guanseq.warehouse.api.WarehouseReferenceProvider;
import com.guanseq.warehouse.api.WarehouseReferenceProvider.LocationOption;
import com.guanseq.warehouse.api.WarehouseReferenceProvider.WarehouseOption;

@Service
public class PurchaseReceiptApplicationService {
	private static final Set<String> RECEIPT_ROLES = Set.of("WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "PROCUREMENT_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final WarehouseReferenceProvider warehouseProvider;
	private final IncomingInspectionProvider inspectionProvider;
	private final PurchaseReceiptStockService stockService;
	private final PurchaseOrderRepository orderRepository;
	private final PurchaseReceiptRepository receiptRepository;
	private final PurchaseReceiptLineRepository lineRepository;
	private final PurchaseReceiptEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	PurchaseReceiptApplicationService(CurrentWorkspaceProvider workspaceProvider, MasterDataReferenceProvider masterDataProvider,
			WarehouseReferenceProvider warehouseProvider, IncomingInspectionProvider inspectionProvider,
			PurchaseReceiptStockService stockService, PurchaseOrderRepository orderRepository,
			PurchaseReceiptRepository receiptRepository, PurchaseReceiptLineRepository lineRepository,
			PurchaseReceiptEventRepository eventRepository, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.masterDataProvider = masterDataProvider;
		this.warehouseProvider = warehouseProvider; this.inspectionProvider = inspectionProvider; this.stockService = stockService;
		this.orderRepository = orderRepository; this.receiptRepository = receiptRepository; this.lineRepository = lineRepository;
		this.eventRepository = eventRepository; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PurchaseReceiptPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = receiptRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new PurchaseReceiptPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public PurchaseReceiptRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireReceipt(access, id));
	}

	@Transactional(readOnly = true)
	public PurchaseReceiptReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Map<UUID, MaterialReference> materials = masterDataProvider.listActiveMaterials(access.tenantOrganizationId()).stream()
				.collect(Collectors.toMap(MaterialReference::id, Function.identity()));
		List<PurchaseReceiptReferenceData.ReleasedOrder> orders = orderRepository
				.findByTenantOrganizationIdAndStatus(access.tenantOrganizationId(), "RELEASED").stream()
				.sorted(java.util.Comparator.comparing(PurchaseOrderEntity::getPromisedReceiptDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
						.thenComparing(PurchaseOrderEntity::getOrderNumber))
				.map(order -> new PurchaseReceiptReferenceData.ReleasedOrder(order.getId(), order.getOrderNumber(), order.getSupplierId(),
						order.getSupplierCode(), order.getSupplierName(), order.getPromisedReceiptDate(),
						order.getLines().stream().filter(line -> line.getOutstandingQuantity().signum() > 0).map(line -> {
							MaterialReference material = materials.get(line.getMaterialId());
							boolean inspectionRequired = material != null && material.incomingInspectionRequired();
							return new PurchaseReceiptReferenceData.ReleasedLine(line.getId(), line.getLineNumber(), line.getMaterialId(),
									line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
									line.getOrderedQuantity(), line.getReceivedQuantity(), line.getOutstandingQuantity(), inspectionRequired);
						}).toList()))
				.filter(order -> !order.lines().isEmpty()).toList();
		return new PurchaseReceiptReferenceData(orders,
				warehouseProvider.listActiveWarehouses(access.tenantOrganizationId()).stream().map(item -> new PurchaseReceiptReferenceData.WarehouseOption(item.id(), item.code(), item.name())).toList(),
				warehouseProvider.listActiveLocations(access.tenantOrganizationId()).stream().map(item -> new PurchaseReceiptReferenceData.LocationOption(item.id(), item.warehouseId(), item.code(), item.name(), item.locationType())).toList());
	}

	@Transactional
	public PurchaseReceiptRecord create(String username, PurchaseReceiptRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireReceiptRole(access);
		String requestId = requestId("purchase-receipt-");
		var duplicate = receiptRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) return toRecord(duplicate.get());
		if (request.lines() == null || request.lines().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收货明细不能为空");
		PurchaseOrderEntity order = orderRepository.findByIdAndTenantOrganizationId(request.purchaseOrderId(), access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购订单不存在或不在当前租户范围"));
		if (!"RELEASED".equals(order.getStatus())) throw conflict("只有已下达采购订单可以登记到货");
		WarehouseOption warehouse = requireWarehouse(access, request.warehouseId());
		LocationOption location = requireLocation(access, request.locationId(), warehouse.id());
		Map<UUID, PurchaseOrderLineEntity> orderLines = order.getLines().stream().collect(Collectors.toMap(PurchaseOrderLineEntity::getId, Function.identity()));
		Set<UUID> uniqueLines = new HashSet<>();
		PurchaseReceiptEntity receipt = new PurchaseReceiptEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextReceiptNumber(), order,
				new PurchaseReceiptEntity.WarehouseSnapshot(warehouse.id(), warehouse.code(), warehouse.name()),
				new PurchaseReceiptEntity.StorageLocationSnapshot(location.id(), location.code(), location.name()),
				request.note() == null || request.note().isBlank() ? null : request.note().trim(), requestId, access.userId());
		for (PurchaseReceiptRecord.LineInput input : request.lines()) {
			if (!uniqueLines.add(input.orderLineId())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "同一采购订单行不能重复登记到货");
			PurchaseOrderLineEntity orderLine = orderLines.get(input.orderLineId());
			if (orderLine == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "收货明细不属于所选采购订单");
			MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), orderLine.getMaterialId());
			if (input.receivedQuantity().compareTo(orderLine.getOutstandingQuantity()) > 0)
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "物料 " + material.code() + " 本次合格收货数量不能超过未收数量 " + orderLine.getOutstandingQuantity());
			PurchaseReceiptLineEntity line = receipt.addLine(orderLine, input.receivedQuantity(), material.incomingInspectionRequired(), input.lotNumber(), requestId, access.userId());
			UUID sourceLineId = line.getId();
			if (material.incomingInspectionRequired()) {
				var inspection = inspectionProvider.create(new IncomingInspectionProvider.CreateCommand(access.tenantOrganizationId(),
						access.operatingOrganizationId(), access.workspaceId(), access.userId(), receipt.getId(), receipt.getReceiptNumber(),
						line.getId(), order.getId(), order.getOrderNumber(), order.getSupplierId(), order.getSupplierCode(),
						order.getSupplierName(), material.id(), material.code(), material.name(), orderLine.getMaterialSpecification(),
						orderLine.getUnit(), input.receivedQuantity(), requestId + "-IQC-" + line.getLineNumber()));
				var stock = stockService.receiveInspection(new PurchaseReceiptStockService.Command(access.tenantOrganizationId(),
						access.operatingOrganizationId(), access.workspaceId(), access.userId(), warehouse.id(), location.id(),
						material.id(), material.code(), material.name(), orderLine.getMaterialSpecification(), orderLine.getUnit(),
						input.receivedQuantity(), input.lotNumber(), receipt.getId(), receipt.getReceiptNumber(), sourceLineId,
						requestId + "-" + line.getLineNumber()));
				line.attachInspection(inspection.id(), stock.balanceId(), stock.movementId());
			} else {
				var stock = stockService.receiveAvailable(new PurchaseReceiptStockService.Command(access.tenantOrganizationId(),
						access.operatingOrganizationId(), access.workspaceId(), access.userId(), warehouse.id(), location.id(),
						material.id(), material.code(), material.name(), orderLine.getMaterialSpecification(), orderLine.getUnit(),
						input.receivedQuantity(), input.lotNumber(), receipt.getId(), receipt.getReceiptNumber(), sourceLineId,
						requestId + "-" + line.getLineNumber()));
				line.markDirectReceived(stock.balanceId(), stock.movementId());
				orderLine.applyAcceptedReceipt(input.receivedQuantity());
			}
		}
		orderRepository.saveAndFlush(order);
		receiptRepository.saveAndFlush(receipt);
		audit(access, receipt, null, null, "CREATED", null, Map.of("lineCount", receipt.getLines().size()));
		return toRecord(receipt);
	}

	@Transactional
	public void settleIncomingInspection(IncomingInspectionProvider.CompletedEvent event) {
		PurchaseReceiptLineEntity line = lineRepository.findByTenantOrganizationIdAndInspectionId(event.tenantOrganizationId(), event.inspectionId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "来料检验对应的收货行不存在"));
		if (!"PENDING_INSPECTION".equals(line.getStatus())) return;
		PurchaseReceiptEntity receipt = line.getReceipt();
		PurchaseOrderEntity order = orderRepository.findByIdAndTenantOrganizationId(receipt.getPurchaseOrderId(), event.tenantOrganizationId()).orElseThrow();
		PurchaseOrderLineEntity orderLine = order.getLines().stream().filter(item -> item.getId().equals(line.getPurchaseOrderLineId())).findFirst().orElseThrow();
		var settlement = stockService.settleInspection(new PurchaseReceiptStockService.SettleCommand(event.tenantOrganizationId(),
				receipt.getOwningOrganizationId(), receipt.getWorkspaceId(), event.actorUserId(), line.getInspectionBalanceId(),
				receipt.getWarehouseId(), receipt.getLocationId(), line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(),
				line.getMaterialSpecification(), line.getUnit(), event.acceptedQuantity(), event.rejectedQuantity(), line.getLotNumber(),
				receipt.getId(), receipt.getReceiptNumber(), line.getId(), event.requestId()));
		if (settlement.accepted() != null) line.attachAcceptedStock(settlement.accepted().balanceId(), settlement.accepted().movementId());
		if (settlement.rejected() != null) line.attachRejectedStock(settlement.rejected().balanceId(), settlement.rejected().movementId());
		receipt.settleLine(line, event.acceptedQuantity(), event.rejectedQuantity(), event.actorUserId());
		orderLine.applyAcceptedReceipt(event.acceptedQuantity());
		orderRepository.saveAndFlush(order);
		receiptRepository.saveAndFlush(receipt);
		eventRepository.save(new PurchaseReceiptEventEntity(event.tenantOrganizationId(), receipt.getWorkspaceId(), event.actorUserId(),
				receipt.getId(), line.getId(), "IQC_COMPLETED", "PENDING_INSPECTION", line.getStatus(), event.requestId(), null,
				Map.of("inspectionId", event.inspectionId(), "acceptedQuantity", event.acceptedQuantity(), "rejectedQuantity", event.rejectedQuantity())));
	}

	private PurchaseReceiptRecord toRecord(PurchaseReceiptEntity receipt) {
		return new PurchaseReceiptRecord(receipt.getId(), receipt.getReceiptNumber(), receipt.getPurchaseOrderId(), receipt.getOrderNumber(),
				receipt.getSupplierId(), receipt.getSupplierCode(), receipt.getSupplierName(), receipt.getWarehouseId(),
				receipt.getWarehouseCode(), receipt.getWarehouseName(), receipt.getLocationId(), receipt.getLocationCode(),
				receipt.getLocationName(), receipt.getNote(), receipt.getStatus(), receipt.getTotalReceivedQuantity(),
				receipt.getAcceptedQuantity(), receipt.getRejectedQuantity(), receipt.getVersion(), receipt.getCreatedAt(),
				receipt.getLines().stream().sorted(java.util.Comparator.comparingInt(PurchaseReceiptLineEntity::getLineNumber))
						.map(line -> new PurchaseReceiptRecord.Line(line.getId(), line.getLineNumber(), line.getPurchaseOrderLineId(),
								line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(),
								line.getUnit(), line.getReceivedQuantity(), line.isInspectionRequired(), line.getLotNumber(), line.getStatus(),
								line.getInspectionId(), null, line.getAcceptedQuantity(), line.getRejectedQuantity(),
								line.getAcceptedBalanceId(), line.getRejectedBalanceId(), line.stockSummary(), line.getVersion())).toList());
	}

	private PurchaseReceiptEntity requireReceipt(CurrentWorkspaceAccess access, UUID id) {
		return receiptRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购收货单不存在或不在当前租户范围"));
	}
	private WarehouseOption requireWarehouse(CurrentWorkspaceAccess access, UUID id) {
		return warehouseProvider.listActiveWarehouses(access.tenantOrganizationId()).stream().filter(item -> item.id().equals(id)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "收货仓库不存在、未启用或不在当前租户范围"));
	}
	private LocationOption requireLocation(CurrentWorkspaceAccess access, UUID id, UUID warehouseId) {
		LocationOption location = warehouseProvider.listActiveLocations(access.tenantOrganizationId()).stream().filter(item -> item.id().equals(id)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "收货库位不存在、未启用或不在当前租户范围"));
		if (!location.warehouseId().equals(warehouseId)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "收货库位不属于所选仓库");
		return location;
	}
	private void audit(CurrentWorkspaceAccess access, PurchaseReceiptEntity receipt, UUID lineId, String from, String action, String to, Map<String, Object> details) {
		eventRepository.save(new PurchaseReceiptEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				receipt.getId(), lineId, action, from, to, MDC.get("requestId"), null, details));
	}
	private String nextReceiptNumber() { Long sequence = jdbcTemplate.queryForObject("select nextval('procurement.purchase_receipt_number_seq')", Long.class); return "PR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence); }
	private static void requireReceiptRole(CurrentWorkspaceAccess access) { if (!RECEIPT_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权登记采购到货"); }
	private static String requestId(String prefix) { String value = MDC.get("requestId"); return value == null || value.isBlank() ? prefix + UUID.randomUUID() : value; }
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
