package com.guanseq.sales.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.sales.api.SalesFulfillmentChangedEvent;
import com.guanseq.sales.api.SalesReturnPage;
import com.guanseq.sales.api.SalesReturnRecord;
import com.guanseq.sales.api.SalesReturnReferenceData;
import com.guanseq.warehouse.api.SalesReturnStockService;
import com.guanseq.warehouse.api.WarehouseReferenceProvider;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesReturnApplicationService {
	private static final Set<String> CREATE_ROLES = Set.of("SALES_MANAGER", "ADMIN");
	private static final Set<String> RECEIPT_ROLES = Set.of("WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN");
	private static final Set<String> QUALITY_ROLES = Set.of("QUALITY_INSPECTOR", "QUALITY_MANAGER", "ADMIN");
	private static final Set<String> RETURNABLE_ORDER_STATUSES = Set.of("PARTIALLY_SHIPPED", "SHIPPED", "PARTIALLY_RETURNED", "RETURNED");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final SalesOrderRepository orderRepository;
	private final SalesOrderChangeEventRepository orderEventRepository;
	private final SalesReturnRepository returnRepository;
	private final SalesReturnLineRepository lineRepository;
	private final SalesReturnEventRepository eventRepository;
	private final WarehouseReferenceProvider warehouseProvider;
	private final SalesReturnStockService stockService;
	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher eventPublisher;

	SalesReturnApplicationService(CurrentWorkspaceProvider workspaceProvider, SalesOrderRepository orderRepository,
			SalesOrderChangeEventRepository orderEventRepository, SalesReturnRepository returnRepository,
			SalesReturnLineRepository lineRepository, SalesReturnEventRepository eventRepository,
			WarehouseReferenceProvider warehouseProvider, SalesReturnStockService stockService, JdbcTemplate jdbcTemplate,
			ApplicationEventPublisher eventPublisher) {
		this.workspaceProvider = workspaceProvider; this.orderRepository = orderRepository;
		this.orderEventRepository = orderEventRepository; this.returnRepository = returnRepository;
		this.lineRepository = lineRepository; this.eventRepository = eventRepository;
		this.warehouseProvider = warehouseProvider; this.stockService = stockService;
		this.jdbcTemplate = jdbcTemplate; this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public SalesReturnPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = returnRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new SalesReturnPage(result.getContent().stream().map(item -> toRecord(access, item)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(), canCreate(access));
	}

	@Transactional(readOnly = true)
	public SalesReturnRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(access, requireReturn(access, id));
	}

	@Transactional(readOnly = true)
	public SalesReturnReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		List<SalesReturnReferenceData.ReturnableOrder> orders = orderRepository
				.findByTenantOrganizationIdAndStatusIn(access.tenantOrganizationId(), new ArrayList<>(RETURNABLE_ORDER_STATUSES),
						Sort.by(Sort.Order.desc("updatedAt"))).stream().map(order -> toReturnableOrder(access, order))
				.filter(item -> !item.lines().isEmpty()).toList();
		var warehouses = warehouseProvider.listActiveWarehouses(access.tenantOrganizationId()).stream()
				.map(item -> new SalesReturnReferenceData.WarehouseOption(item.id(), item.code(), item.name())).toList();
		var locations = warehouseProvider.listActiveLocations(access.tenantOrganizationId()).stream()
				.map(item -> new SalesReturnReferenceData.LocationOption(item.id(), item.warehouseId(), item.code(), item.name(),
						item.locationType())).toList();
		return new SalesReturnReferenceData(orders, warehouses, locations, canCreate(access));
	}

	@Transactional
	public SalesReturnRecord create(String username, SalesReturnRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireRole(access, CREATE_ROLES, "当前角色无权建立销售退货授权");
		String requestId = requestId("sales-return-create");
		var duplicate = returnRepository.findByTenantOrganizationIdAndCreateRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) return toRecord(access, duplicate.get());
		lockBusinessKey("sales-return-order:" + request.salesOrderId());
		SalesOrderEntity order = requireOrder(access, request.salesOrderId());
		requireOrderVersion(order, request.expectedOrderVersion());
		if (!RETURNABLE_ORDER_STATUSES.contains(order.getStatus()))
			throw conflict("只有已经发生发货的销售订单可以建立退货授权");
		Map<UUID, SalesOrderLineEntity> orderLines = order.getLines().stream()
				.collect(Collectors.toMap(SalesOrderLineEntity::getId, item -> item));
		Set<UUID> unique = new HashSet<>();
		Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
		BigDecimal total = BigDecimal.ZERO;
		for (SalesReturnRecord.LineInput input : request.lines()) {
			if (!unique.add(input.orderLineId())) throw invalid("同一销售订单行不能重复授权退货，请合并数量");
			SalesOrderLineEntity line = orderLines.get(input.orderLineId());
			if (line == null) throw invalid("退货行不属于所选销售订单");
			BigDecimal pending = money4(lineRepository.sumPendingQuantity(access.tenantOrganizationId(), line.getId()));
			BigDecimal returnable = line.getNetDeliveredQuantity().subtract(pending).setScale(4, RoundingMode.HALF_UP);
			if (input.returnQuantity().compareTo(returnable) > 0)
				throw invalid("物料 " + line.getMaterialCode() + " 本次授权退货数量超过剩余可退数量 " + returnable.stripTrailingZeros().toPlainString());
			quantities.put(line.getId(), money4(input.returnQuantity())); total = total.add(input.returnQuantity());
		}
		String returnNumber = nextReturnNumber();
		SalesReturnEntity salesReturn = new SalesReturnEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), returnNumber, order, request.returnDate(), request.reason(), request.note(), total, requestId,
				access.userId());
		quantities.forEach((lineId, quantity) -> salesReturn.addLine(new SalesReturnLineEntity(salesReturn, orderLines.get(lineId), quantity)));
		try {
			returnRepository.saveAndFlush(salesReturn);
			eventRepository.saveAndFlush(new SalesReturnEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), salesReturn.getId(), "CREATED", null, "PENDING_RECEIPT", request.reason(), requestId,
					Map.of("salesOrderId", order.getId(), "orderNumber", order.getOrderNumber(), "totalReturnQuantity", total)));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("销售退货请求编号或退货单号冲突，请刷新确认结果", exception);
		}
		return toRecord(access, salesReturn);
	}

	@Transactional
	public SalesReturnRecord act(String username, UUID id, SalesReturnRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String requestId = requestId("sales-return-action");
		var duplicate = eventRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) {
			if (!duplicate.get().getReturnId().equals(id)) throw conflict("请求编号已经用于其他销售退货单");
			return toRecord(access, requireReturn(access, id));
		}
		SalesReturnEntity salesReturn = requireReturn(access, id);
		requireVersion(salesReturn, request.expectedVersion());
		return switch (request.action()) {
			case "CANCEL" -> cancel(access, salesReturn, request.reason(), requestId);
			case "RECEIVE" -> receive(username, access, salesReturn, request, requestId);
			case "INSPECT" -> inspect(access, salesReturn, request, requestId);
			case "REVERSE_RECEIPT" -> reverseReceipt(username, access, salesReturn, request.reason(), requestId);
			default -> throw invalid("不支持的销售退货动作");
		};
	}

	private SalesReturnRecord cancel(CurrentWorkspaceAccess access, SalesReturnEntity salesReturn, String reason, String requestId) {
		requireRole(access, CREATE_ROLES, "当前角色无权取消销售退货授权");
		requireStatus(salesReturn, "PENDING_RECEIPT", "只有待收货退货单可以取消");
		salesReturn.cancel(access.userId()); saveAction(access, salesReturn, "CANCELLED", "PENDING_RECEIPT", reason, requestId, Map.of());
		return toRecord(access, salesReturn);
	}

	private SalesReturnRecord receive(String username, CurrentWorkspaceAccess access, SalesReturnEntity salesReturn,
			SalesReturnRecord.ActionRequest request, String requestId) {
		requireRole(access, RECEIPT_ROLES, "当前角色无权登记销售退货收货");
		requireStatus(salesReturn, "PENDING_RECEIPT", "只有待收货退货单可以登记收货");
		if (request.warehouseId() == null || request.locationId() == null) throw invalid("销售退货收货必须选择仓库和库位");
		Map<UUID, SalesReturnRecord.ActionLineInput> inputs = actionInputs(request.lines(), salesReturn.getLines());
		var warehouse = warehouseProvider.listActiveWarehouses(access.tenantOrganizationId()).stream()
				.filter(item -> item.id().equals(request.warehouseId())).findFirst()
				.orElseThrow(() -> invalid("销售退货仓库不存在、已停用或不在当前租户范围"));
		var location = warehouseProvider.listActiveLocations(access.tenantOrganizationId()).stream()
				.filter(item -> item.id().equals(request.locationId()) && item.warehouseId().equals(warehouse.id())).findFirst()
				.orElseThrow(() -> invalid("销售退货库位不存在、已停用或不属于所选仓库"));
		lockBusinessKey("sales-return-order:" + salesReturn.getSalesOrderId());
		SalesOrderEntity order = requireOrder(access, salesReturn.getSalesOrderId());
		Map<UUID, BigDecimal> returnedByLine = new LinkedHashMap<>();
		for (SalesReturnLineEntity line : salesReturn.getLines()) {
			SalesOrderLineEntity orderLine = order.getLines().stream().filter(item -> item.getId().equals(line.getOrderLineId())).findFirst().orElseThrow();
			if (line.getAuthorizedQuantity().compareTo(orderLine.getNetDeliveredQuantity()) > 0)
				throw conflict("订单净发货数量已经变化，物料 " + line.getMaterialCode() + " 无法按原授权数量收货");
			String lot = normalize(inputs.get(line.getId()).lotNumber());
			var receipt = stockService.receiveSalesReturn(new SalesReturnStockService.ReceiptCommand(access.tenantOrganizationId(),
					access.operatingOrganizationId(), access.workspaceId(), access.userId(), warehouse.id(), location.id(),
					line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
					line.getAuthorizedQuantity(), lot, salesReturn.getId(), salesReturn.getReturnNumber(), line.getId(),
					requestId + "-" + line.getId()));
			line.markReceived(receipt.balanceId(), receipt.movementId(), lot,
					receipt.warehouseCode() + "/" + receipt.locationCode() + " · 待检 × " + quantityText(line.getAuthorizedQuantity()));
			returnedByLine.put(line.getOrderLineId(), line.getAuthorizedQuantity());
		}
		String orderFrom = order.getStatus(); order.applyReturn(returnedByLine, access.userId());
		salesReturn.markReceived(warehouse.id(), warehouse.code(), warehouse.name(), location.id(), location.code(), location.name(), access.userId());
		try { orderRepository.saveAndFlush(order); }
		catch (ObjectOptimisticLockingFailureException exception) { throw conflict("销售订单或退货单已变化，请刷新后重试", exception); }
		orderEventRepository.save(new SalesOrderChangeEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				order.getId(), "RETURN_RECEIVED", orderFrom, order.getStatus(), requestId, request.reason(),
				Map.of("returnId", salesReturn.getId(), "returnNumber", salesReturn.getReturnNumber(),
						"totalReturnQuantity", salesReturn.getTotalReturnQuantity())));
		saveAction(access, salesReturn, "RECEIVED", "PENDING_RECEIPT", request.reason(), requestId,
				Map.of("warehouseCode", warehouse.code(), "locationCode", location.code()));
		eventPublisher.publishEvent(new SalesFulfillmentChangedEvent(username, order.getId(), "SALES_RETURN_RECEIPT", salesReturn.getReturnNumber()));
		return toRecord(access, salesReturn);
	}

	private SalesReturnRecord inspect(CurrentWorkspaceAccess access, SalesReturnEntity salesReturn,
			SalesReturnRecord.ActionRequest request, String requestId) {
		requireRole(access, QUALITY_ROLES, "当前角色无权提交销售退货质量判定");
		requireStatus(salesReturn, "RECEIVED", "只有待检中的销售退货单可以提交质量判定");
		Map<UUID, SalesReturnRecord.ActionLineInput> inputs = actionInputs(request.lines(), salesReturn.getLines());
		BigDecimal acceptedTotal = BigDecimal.ZERO; BigDecimal rejectedTotal = BigDecimal.ZERO;
		for (SalesReturnLineEntity line : salesReturn.getLines()) {
			SalesReturnRecord.ActionLineInput input = inputs.get(line.getId());
			BigDecimal accepted = money4(input.acceptedQuantity() == null ? BigDecimal.ZERO : input.acceptedQuantity());
			BigDecimal rejected = money4(input.rejectedQuantity() == null ? BigDecimal.ZERO : input.rejectedQuantity());
			if (accepted.add(rejected).compareTo(line.getReceivedQuantity()) != 0)
				throw invalid("物料 " + line.getMaterialCode() + " 的合格与不合格数量之和必须等于待检数量");
			var settlement = stockService.inspectSalesReturn(new SalesReturnStockService.InspectionCommand(
					access.tenantOrganizationId(), access.operatingOrganizationId(), access.workspaceId(), access.userId(),
					line.getInspectionBalanceId(), line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(),
					line.getMaterialSpecification(), line.getUnit(), accepted, rejected, line.getLotNumber(), salesReturn.getId(),
					salesReturn.getReturnNumber(), line.getId(), requestId + "-" + line.getId()));
			List<String> summaries = new ArrayList<>();
			if (settlement.accepted() != null) summaries.add("合格 " + quantityText(accepted) + " → AVAILABLE");
			if (settlement.rejected() != null) summaries.add("不合格 " + quantityText(rejected) + " → BLOCKED");
			line.markInspected(accepted, rejected, String.join("；", summaries));
			acceptedTotal = acceptedTotal.add(accepted); rejectedTotal = rejectedTotal.add(rejected);
		}
		salesReturn.markInspected(access.userId());
		saveAction(access, salesReturn, "INSPECTED", "RECEIVED", request.reason(), requestId,
				Map.of("acceptedQuantity", acceptedTotal, "rejectedQuantity", rejectedTotal));
		return toRecord(access, salesReturn);
	}

	private SalesReturnRecord reverseReceipt(String username, CurrentWorkspaceAccess access, SalesReturnEntity salesReturn,
			String reason, String requestId) {
		requireRole(access, RECEIPT_ROLES, "当前角色无权冲回销售退货收货");
		requireStatus(salesReturn, "RECEIVED", "只有尚未质量判定的销售退货收货可以冲回");
		lockBusinessKey("sales-return-order:" + salesReturn.getSalesOrderId());
		SalesOrderEntity order = requireOrder(access, salesReturn.getSalesOrderId());
		Map<UUID, BigDecimal> returnedByLine = new LinkedHashMap<>();
		for (SalesReturnLineEntity line : salesReturn.getLines()) {
			stockService.reverseSalesReturnReceipt(new SalesReturnStockService.ReverseCommand(access.tenantOrganizationId(),
					access.workspaceId(), access.userId(), line.getInspectionBalanceId(), line.getReceivedQuantity(),
					salesReturn.getId(), salesReturn.getReturnNumber(), line.getId(), requestId + "-" + line.getId(), reason));
			returnedByLine.put(line.getOrderLineId(), line.getReceivedQuantity());
		}
		String orderFrom = order.getStatus(); order.reverseReturn(returnedByLine, access.userId()); salesReturn.reverseReceipt(access.userId());
		try { orderRepository.saveAndFlush(order); }
		catch (ObjectOptimisticLockingFailureException exception) { throw conflict("销售订单或退货单已变化，请刷新后重试", exception); }
		orderEventRepository.save(new SalesOrderChangeEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				order.getId(), "RETURN_RECEIPT_REVERSED", orderFrom, order.getStatus(), requestId, reason,
				Map.of("returnId", salesReturn.getId(), "returnNumber", salesReturn.getReturnNumber(),
						"totalReturnQuantity", salesReturn.getTotalReturnQuantity())));
		saveAction(access, salesReturn, "RECEIPT_REVERSED", "RECEIVED", reason, requestId, Map.of());
		eventPublisher.publishEvent(new SalesFulfillmentChangedEvent(username, order.getId(), "SALES_RETURN_REVERSAL", salesReturn.getReturnNumber()));
		return toRecord(access, salesReturn);
	}

	private void saveAction(CurrentWorkspaceAccess access, SalesReturnEntity salesReturn, String action, String fromStatus,
			String reason, String requestId, Map<String, Object> details) {
		try {
			returnRepository.saveAndFlush(salesReturn);
			eventRepository.saveAndFlush(new SalesReturnEventEntity(access.tenantOrganizationId(), access.workspaceId(),
					access.userId(), salesReturn.getId(), action, fromStatus, salesReturn.getStatus(), reason, requestId, details));
		} catch (ObjectOptimisticLockingFailureException exception) { throw conflict("销售退货单已被其他用户更新，请刷新后重试", exception); }
		catch (DataIntegrityViolationException exception) { throw conflict("销售退货动作请求编号冲突，请刷新确认结果", exception); }
	}

	private SalesReturnReferenceData.ReturnableOrder toReturnableOrder(CurrentWorkspaceAccess access, SalesOrderEntity order) {
		List<SalesReturnReferenceData.ReturnableLine> lines = order.getLines().stream()
				.sorted(java.util.Comparator.comparingInt(SalesOrderLineEntity::getLineNumber)).map(line -> {
					BigDecimal pending = money4(lineRepository.sumPendingQuantity(access.tenantOrganizationId(), line.getId()));
					BigDecimal returnable = line.getNetDeliveredQuantity().subtract(pending).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
					return new SalesReturnReferenceData.ReturnableLine(line.getId(), line.getLineNumber(), line.getMaterialId(),
							line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
							line.getDeliveredQuantity(), line.getReturnedQuantity(), pending, line.getNetDeliveredQuantity(), returnable);
				}).filter(line -> line.returnableQuantity().signum() > 0).toList();
		return new SalesReturnReferenceData.ReturnableOrder(order.getId(), order.getOrderNumber(), order.getCustomerId(),
				order.getCustomerCode(), order.getCustomerName(), order.getStatus(), order.getVersion(), lines);
	}

	private SalesReturnRecord toRecord(CurrentWorkspaceAccess access, SalesReturnEntity salesReturn) {
		List<String> actions = new ArrayList<>();
		if ("PENDING_RECEIPT".equals(salesReturn.getStatus()) && CREATE_ROLES.contains(access.roleCode())) actions.add("CANCEL");
		if ("PENDING_RECEIPT".equals(salesReturn.getStatus()) && RECEIPT_ROLES.contains(access.roleCode())) actions.add("RECEIVE");
		if ("RECEIVED".equals(salesReturn.getStatus()) && QUALITY_ROLES.contains(access.roleCode())) actions.add("INSPECT");
		if ("RECEIVED".equals(salesReturn.getStatus()) && RECEIPT_ROLES.contains(access.roleCode())) actions.add("REVERSE_RECEIPT");
		List<SalesReturnRecord.Event> events = eventRepository.findByReturnIdOrderByOccurredAtDesc(salesReturn.getId()).stream()
				.map(event -> new SalesReturnRecord.Event(event.getId(), event.getAction(), event.getFromStatus(), event.getToStatus(),
						event.getReason(), event.getRequestId(), event.getOccurredAt())).toList();
		return new SalesReturnRecord(salesReturn.getId(), salesReturn.getReturnNumber(), salesReturn.getSalesOrderId(),
				salesReturn.getOrderNumber(), salesReturn.getCustomerId(), salesReturn.getCustomerCode(), salesReturn.getCustomerName(),
				salesReturn.getReturnDate(), salesReturn.getStatus(), salesReturn.getReason(), salesReturn.getNote(),
				salesReturn.getWarehouseId(), salesReturn.getWarehouseCode(), salesReturn.getWarehouseName(), salesReturn.getLocationId(),
				salesReturn.getLocationCode(), salesReturn.getLocationName(), salesReturn.getTotalReturnQuantity(),
				salesReturn.getReceivedAt(), salesReturn.getInspectedAt(), salesReturn.getVersion(), salesReturn.getCreatedAt(),
				salesReturn.getUpdatedAt(), actions, salesReturn.getLines().stream()
						.sorted(java.util.Comparator.comparingInt(SalesReturnLineEntity::getLineNumber))
						.map(line -> new SalesReturnRecord.Line(line.getId(), line.getOrderLineId(), line.getLineNumber(), line.getMaterialId(),
								line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
								line.getAuthorizedQuantity(), line.getReceivedQuantity(), line.getAcceptedQuantity(), line.getRejectedQuantity(),
								line.getLotNumber(), line.getInspectionBalanceId(), line.getReceiptMovementId(), line.getStockSummary())).toList(), events);
	}

	private Map<UUID, SalesReturnRecord.ActionLineInput> actionInputs(List<SalesReturnRecord.ActionLineInput> inputs,
			List<SalesReturnLineEntity> expectedLines) {
		if (inputs == null || inputs.size() != expectedLines.size()) throw invalid("必须提交退货单的全部行");
		Map<UUID, SalesReturnRecord.ActionLineInput> result = new HashMap<>();
		for (SalesReturnRecord.ActionLineInput input : inputs) {
			if (result.put(input.returnLineId(), input) != null) throw invalid("同一退货行不能重复提交");
		}
		if (expectedLines.stream().anyMatch(line -> !result.containsKey(line.getId()))) throw invalid("提交行与退货单不一致");
		return result;
	}

	private SalesReturnEntity requireReturn(CurrentWorkspaceAccess access, UUID id) {
		return returnRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售退货单不存在或不在当前租户范围"));
	}
	private SalesOrderEntity requireOrder(CurrentWorkspaceAccess access, UUID id) {
		return orderRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在或不在当前租户范围"));
	}
	private void lockBusinessKey(String key) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
				statement -> statement.setString(1, key), resultSet -> null);
	}
	private String nextReturnNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('sales.return_number_seq')", Long.class);
		return "SR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}
	private static boolean canCreate(CurrentWorkspaceAccess access) { return CREATE_ROLES.contains(access.roleCode()); }
	private static void requireRole(CurrentWorkspaceAccess access, Set<String> roles, String message) { if (!roles.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
	private static void requireStatus(SalesReturnEntity salesReturn, String expected, String message) { if (!expected.equals(salesReturn.getStatus())) throw conflict(message); }
	private static void requireVersion(SalesReturnEntity salesReturn, long expected) { if (salesReturn.getVersion() != expected) throw conflict("销售退货单已经被其他用户更新，请刷新后重试"); }
	private static void requireOrderVersion(SalesOrderEntity order, long expected) { if (order.getVersion() != expected) throw conflict("销售订单已经变化，请刷新可退数量后重试"); }
	private static BigDecimal money4(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP); }
	private static String quantityText(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
	private static String requestId(String prefix) { String value = MDC.get("requestId"); return value == null || value.isBlank() ? prefix + "-" + UUID.randomUUID() : value; }
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private static ResponseStatusException conflict(String message, Throwable cause) { return new ResponseStatusException(HttpStatus.CONFLICT, message, cause); }
}
