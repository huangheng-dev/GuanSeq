package com.guanseq.sales.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.guanseq.sales.api.SalesShipmentPage;
import com.guanseq.sales.api.SalesShipmentRecord;
import com.guanseq.sales.api.SalesShipmentReferenceData;
import com.guanseq.warehouse.api.ProductionMaterialStockService;
import com.guanseq.warehouse.api.WarehouseReferenceProvider;

@Service
public class SalesShipmentApplicationService {
	private static final Set<String> SHIPMENT_ROLES = Set.of("WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "SALES_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final SalesOrderRepository orderRepository;
	private final SalesShipmentRepository shipmentRepository;
	private final SalesShipmentEventRepository shipmentEventRepository;
	private final SalesOrderChangeEventRepository orderEventRepository;
	private final WarehouseReferenceProvider warehouseProvider;
	private final ProductionMaterialStockService stockService;
	private final JdbcTemplate jdbcTemplate;

	SalesShipmentApplicationService(CurrentWorkspaceProvider workspaceProvider, SalesOrderRepository orderRepository,
			SalesShipmentRepository shipmentRepository, SalesShipmentEventRepository shipmentEventRepository,
			SalesOrderChangeEventRepository orderEventRepository, WarehouseReferenceProvider warehouseProvider,
			ProductionMaterialStockService stockService, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.orderRepository = orderRepository;
		this.shipmentRepository = shipmentRepository;
		this.shipmentEventRepository = shipmentEventRepository;
		this.orderEventRepository = orderEventRepository;
		this.warehouseProvider = warehouseProvider;
		this.stockService = stockService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public SalesShipmentPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = shipmentRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new SalesShipmentPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public SalesShipmentRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireShipment(access, id));
	}

	@Transactional(readOnly = true)
	public SalesShipmentReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		List<SalesShipmentReferenceData.ReleasedOrder> orders = orderRepository
				.findByTenantOrganizationIdAndStatusIn(access.tenantOrganizationId(), List.of("RELEASED", "PARTIALLY_SHIPPED"),
						Sort.by(Sort.Order.asc("promisedDeliveryDate").nullsLast(), Sort.Order.asc("orderNumber"))).stream()
				.map(order -> new SalesShipmentReferenceData.ReleasedOrder(order.getId(), order.getOrderNumber(), order.getCustomerId(),
						order.getCustomerCode(), order.getCustomerName(), order.getPromisedDeliveryDate(),
						order.getLines().stream().sorted(java.util.Comparator.comparingInt(SalesOrderLineEntity::getLineNumber))
								.map(line -> new SalesShipmentReferenceData.ReleasedLine(line.getId(), line.getLineNumber(),
										line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(),
										line.getUnit(), line.getQuantity(), line.getDeliveredQuantity(),
										line.getQuantity().subtract(line.getDeliveredQuantity()))).toList()))
				.toList();
		List<SalesShipmentReferenceData.WarehouseOption> warehouses = warehouseProvider.listActiveWarehouses(access.tenantOrganizationId()).stream()
				.map(item -> new SalesShipmentReferenceData.WarehouseOption(item.id(), item.code(), item.name())).toList();
		return new SalesShipmentReferenceData(orders, warehouses);
	}

	@Transactional
	public SalesShipmentRecord create(String username, SalesShipmentRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireShipmentRole(access);
		String requestId = requestId();
		var duplicate = shipmentRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) return toRecord(duplicate.get());
		if (request.plannedShippingDate().isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "计划发货日期不能早于今天");
		SalesOrderEntity order = orderRepository.findByIdAndTenantOrganizationId(request.salesOrderId(), access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在或不在当前租户范围"));
		if (!Set.of("RELEASED", "PARTIALLY_SHIPPED").contains(order.getStatus())) throw conflict("只有已下达或部分发货的销售订单可以发货");
		var warehouse = warehouseProvider.listActiveWarehouses(access.tenantOrganizationId()).stream().filter(item -> item.id().equals(request.warehouseId())).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "发货仓库不存在、未启用或不在当前租户范围"));
		Map<UUID, SalesOrderLineEntity> orderLines = order.getLines().stream().collect(Collectors.toMap(SalesOrderLineEntity::getId, item -> item));
		Set<UUID> uniqueLineIds = new HashSet<>();
		Map<UUID, BigDecimal> shippedByLine = new LinkedHashMap<>();
		BigDecimal total = BigDecimal.ZERO;
		for (SalesShipmentRecord.LineInput input : request.lines()) {
			if (!uniqueLineIds.add(input.orderLineId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同一销售订单行不能重复发货，请合并数量");
			SalesOrderLineEntity line = orderLines.get(input.orderLineId());
			if (line == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "发货行不属于所选销售订单");
			BigDecimal outstanding = line.getQuantity().subtract(line.getDeliveredQuantity());
			if (input.shippedQuantity().compareTo(outstanding) > 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "物料 " + line.getMaterialCode() + " 本次发货数量超过未发数量");
			shippedByLine.put(line.getId(), input.shippedQuantity());
			total = total.add(input.shippedQuantity());
		}
		String shipmentNumber = nextShipmentNumber();
		SalesShipmentEntity shipment = new SalesShipmentEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), shipmentNumber, order, warehouse.id(), warehouse.code(), warehouse.name(),
				request.plannedShippingDate(), request.note(), total, requestId, access.userId());
		List<SalesShipmentLineEntity> shipmentLines = request.lines().stream().map(input -> new SalesShipmentLineEntity(shipment, orderLines.get(input.orderLineId()), input.shippedQuantity())).toList();
		shipmentLines.forEach(shipment::addLine);
		shipmentRepository.saveAndFlush(shipment);

		ProductionMaterialStockService.IssueResult issueResult;
		try {
			issueResult = stockService.issueMaterials(new ProductionMaterialStockService.IssueCommand(access.tenantOrganizationId(),
					access.operatingOrganizationId(), access.workspaceId(), access.userId(), requestId, warehouse.id(),
					"SALES_SHIPMENT_LINE", shipmentNumber, shipmentLines.stream().map(line -> new ProductionMaterialStockService.IssueLine(
							line.getId(), line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(),
							line.getMaterialSpecification(), line.getUnit(), line.getShippedQuantity(), "销售发货 · " + shipmentNumber)).toList(),
					"销售发货 · " + shipmentNumber));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "发货请求编号冲突，请刷新确认结果", exception);
		}
		Map<UUID, String> summaries = summarizeMovements(issueResult);
		shipmentLines.forEach(line -> line.attachStockSummary(summaries.getOrDefault(line.getId(), "")));

		String fromStatus = order.getStatus();
		order.applyShipment(shippedByLine, access.userId());
		try {
			orderRepository.saveAndFlush(order);
			shipmentRepository.saveAndFlush(shipment);
			shipmentEventRepository.save(new SalesShipmentEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
					shipment.getId(), "SHIPPED", null, "SHIPPED", requestId,
					Map.of("orderId", order.getId(), "orderNumber", order.getOrderNumber(), "totalShippedQuantity", total, "warehouseCode", warehouse.code())));
			orderEventRepository.save(new SalesOrderChangeEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
					order.getId(), "SHIPPED", fromStatus, order.getStatus(), requestId, null,
					Map.of("shipmentId", shipment.getId(), "shipmentNumber", shipmentNumber, "totalShippedQuantity", total)));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "销售订单或发货单已被其他用户更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "发货请求编号冲突，请刷新确认结果", exception);
		}
		return toRecord(shipment);
	}

	private Map<UUID, String> summarizeMovements(ProductionMaterialStockService.IssueResult result) {
		Map<UUID, String> summaries = new LinkedHashMap<>();
		for (var movement : result.movements()) {
			String qty = movement.quantity().stripTrailingZeros().toPlainString();
			String item = movement.warehouseCode() + "/" + movement.locationCode() + " " + (movement.lotNumber() == null || movement.lotNumber().isBlank() ? "无批次" : movement.lotNumber()) + " × " + qty;
			summaries.merge(movement.sourceId(), item, (left, right) -> left + "；" + right);
		}
		return summaries;
	}

	private SalesShipmentRecord toRecord(SalesShipmentEntity shipment) {
		return new SalesShipmentRecord(shipment.getId(), shipment.getShipmentNumber(), shipment.getSalesOrderId(),
				shipment.getOrderNumber(), shipment.getCustomerId(), shipment.getCustomerCode(), shipment.getCustomerName(),
				shipment.getWarehouseId(), shipment.getWarehouseCode(), shipment.getWarehouseName(), shipment.getPlannedShippingDate(),
				shipment.getActualShippedAt(), shipment.getStatus(), shipment.getNote(), shipment.getTotalShippedQuantity(),
				shipment.getVersion(), shipment.getCreatedAt(), shipment.getLines().stream()
						.sorted(java.util.Comparator.comparingInt(SalesShipmentLineEntity::getLineNumber))
						.map(line -> new SalesShipmentRecord.Line(line.getId(), line.getOrderLineId(), line.getLineNumber(),
								line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(),
								line.getUnit(), line.getShippedQuantity(), line.getStockSummary())).toList());
	}

	private SalesShipmentEntity requireShipment(CurrentWorkspaceAccess access, UUID id) {
		return shipmentRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售发货单不存在或不在当前租户范围"));
	}
	private String nextShipmentNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('sales.shipment_number_seq')", Long.class);
		return "SH-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}
	private static void requireShipmentRole(CurrentWorkspaceAccess access) { if (!SHIPMENT_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权登记销售发货"); }
	private static String requestId() { String value = MDC.get("requestId"); return value == null || value.isBlank() ? "shipment-" + UUID.randomUUID() : value; }
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}