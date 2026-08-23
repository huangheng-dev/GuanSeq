package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.finance.api.OrderProfitPage;
import com.guanseq.finance.api.OrderProfitRecord;
import com.guanseq.finance.api.OrderProfitReferenceData;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.production.api.ProductionCostQueryProvider;
import com.guanseq.production.api.ProductionCostQueryProvider.ConsumedMaterial;
import com.guanseq.production.api.ProductionCostQueryProvider.CompletedOperation;
import com.guanseq.production.api.ProductionCostQueryProvider.ProductionCostData;
import com.guanseq.sales.api.SalesProfitQueryProvider;
import com.guanseq.sales.api.SalesProfitQueryProvider.ProfitLine;
import com.guanseq.sales.api.SalesProfitQueryProvider.ProfitOrder;

@Service
public class OrderProfitApplicationService {
	private static final Set<String> SETTLE_ROLES = Set.of("ADMIN", "FINANCE_MANAGER");
	private static final int MONEY_SCALE = 2;
	private static final int QUANTITY_SCALE = 4;
	private static final int COST_SCALE = 6;

	private final CurrentWorkspaceProvider workspaceProvider;
	private final SalesProfitQueryProvider salesProfitQueryProvider;
	private final ProductionCostQueryProvider productionCostQueryProvider;
	private final OrderProfitSettlementRepository settlementRepository;
	private final ItemStandardCostRepository standardCostRepository;
	private final WorkCenterCostRateRepository workCenterCostRateRepository;
	private final OrderProfitEventRepository eventRepository;
	private final AccountingPeriodGuard periodGuard;
	private final JdbcTemplate jdbcTemplate;

	OrderProfitApplicationService(CurrentWorkspaceProvider workspaceProvider, SalesProfitQueryProvider salesProfitQueryProvider,
			ProductionCostQueryProvider productionCostQueryProvider, OrderProfitSettlementRepository settlementRepository,
			ItemStandardCostRepository standardCostRepository, WorkCenterCostRateRepository workCenterCostRateRepository,
			OrderProfitEventRepository eventRepository, AccountingPeriodGuard periodGuard,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.salesProfitQueryProvider = salesProfitQueryProvider;
		this.productionCostQueryProvider = productionCostQueryProvider;
		this.settlementRepository = settlementRepository;
		this.standardCostRepository = standardCostRepository;
		this.workCenterCostRateRepository = workCenterCostRateRepository;
		this.eventRepository = eventRepository;
		this.periodGuard = periodGuard;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public OrderProfitPage list(String username, String query, String costStatus, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Map<UUID, String> orderStatuses = salesProfitQueryProvider.listShippedOrders(access.tenantOrganizationId()).stream()
				.collect(Collectors.toMap(ProfitOrder::id, ProfitOrder::status));
		Page<OrderProfitSettlementEntity> result = settlementRepository.search(access.tenantOrganizationId(), normalize(query),
				normalizeCostStatus(costStatus), PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "settledAt")));
		return new OrderProfitPage(result.getContent().stream().map(item -> toRecord(item, orderStatuses.get(item.getSalesOrderId()))).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public OrderProfitRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		OrderProfitSettlementEntity settlement = settlementRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单利润结算不存在或不在当前租户范围"));
		String orderStatus = salesProfitQueryProvider.findShippedOrder(access.tenantOrganizationId(), settlement.getSalesOrderId())
				.map(ProfitOrder::status).orElse("UNKNOWN");
		return toRecord(settlement, orderStatus);
	}

	@Transactional(readOnly = true)
	public OrderProfitReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Map<UUID, OrderProfitSettlementEntity> settlements = settlementRepository
				.findByTenantOrganizationIdOrderBySettledAtDesc(access.tenantOrganizationId()).stream()
				.collect(Collectors.toMap(OrderProfitSettlementEntity::getSalesOrderId, item -> item, (left, right) -> left));
		return new OrderProfitReferenceData(salesProfitQueryProvider.listShippedOrders(access.tenantOrganizationId()).stream()
				.map(order -> {
					OrderProfitSettlementEntity settlement = settlements.get(order.id());
					BigDecimal shipped = order.lines().stream().map(ProfitLine::deliveredQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal revenue = order.lines().stream()
							.map(line -> line.deliveredQuantity().multiply(line.unitPrice())).reduce(BigDecimal.ZERO, BigDecimal::add)
							.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
					return new OrderProfitReferenceData.SettlableOrder(order.id(), order.orderNumber(), order.customerName(), order.status(),
							order.lines().stream().map(ProfitLine::orderedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add), shipped, revenue,
							settlement != null, settlement == null ? null : settlement.getId().toString(),
							settlement == null ? null : settlement.getSettlementNumber(),
							settlement == null ? null : settlement.getCostStatus());
				}).toList());
	}

	@Transactional
	public OrderProfitRecord settle(String username, UUID salesOrderId, String requestIdHeader) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireSettleRole(access);
		periodGuard.requireOpen(access.tenantOrganizationId(), access.workspaceId(), access.userId(), LocalDate.now());
		String requestId = normalizeRequestId(requestIdHeader);
		OrderProfitSettlementEntity duplicateRequest = settlementRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId).orElse(null);
		if (duplicateRequest != null) return toRecord(duplicateRequest, findOrderStatus(access, duplicateRequest.getSalesOrderId()));
		OrderProfitSettlementEntity existing = settlementRepository.findByTenantOrganizationIdAndSalesOrderId(access.tenantOrganizationId(), salesOrderId).orElse(null);
		if (existing != null) return toRecord(existing, findOrderStatus(access, existing.getSalesOrderId()));

		ProfitOrder order = salesProfitQueryProvider.findShippedOrder(access.tenantOrganizationId(), salesOrderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只有已部分发货或全部发货的销售订单可以结算利润"));
		List<ProductionCostData> productionCosts = productionCostQueryProvider.findCostsForSalesOrder(access.tenantOrganizationId(), salesOrderId);
		Map<UUID, ProductionCostSnapshot> productionByMaterial = aggregateProductionCosts(access.tenantOrganizationId(), productionCosts);

		BigDecimal shippedQuantity = BigDecimal.ZERO;
		BigDecimal revenue = BigDecimal.ZERO.setScale(MONEY_SCALE);
		BigDecimal materialCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
		BigDecimal laborCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
		BigDecimal overheadCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
		BigDecimal processingCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
		List<String> missingItems = new ArrayList<>();
		List<LineCalculation> calculations = new ArrayList<>();
		for (ProfitLine line : order.lines()) {
			if (line.deliveredQuantity().signum() <= 0) continue;
			LineCalculation calculation = calculateLine(access, order.currency(), line, productionByMaterial.get(line.materialId()));
			calculations.add(calculation);
			shippedQuantity = shippedQuantity.add(calculation.line().shippedQuantity());
			revenue = revenue.add(calculation.revenue());
			materialCost = materialCost.add(calculation.materialCost());
			laborCost = laborCost.add(calculation.laborCost());
			overheadCost = overheadCost.add(calculation.overheadCost());
			processingCost = processingCost.add(calculation.processingCost());
			missingItems.addAll(calculation.missingItems());
		}
		if (shippedQuantity.signum() <= 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "销售订单没有可结算的已发数量");

		String costStatus = missingItems.isEmpty() ? "COMPLETE" : "MISSING_COST";
		SalesOrderSnapshot orderSnapshot = new SalesOrderSnapshot(order.id(), order.orderNumber(), order.customerId(), order.customerCode(),
				order.customerName(), order.currency(), order.status(), order.lines().stream()
						.map(line -> new SalesOrderLineSnapshot(line.id(), line.lineNumber(), line.materialId(), line.materialCode(),
								line.materialName(), line.materialSpecification(), line.unit(), line.orderedQuantity(),
								line.deliveredQuantity(), line.unitPrice())).toList());
		OrderProfitSettlementEntity settlement;
		try {
			settlement = new OrderProfitSettlementEntity(access.tenantOrganizationId(), access.operatingOrganizationId(), access.workspaceId(),
					nextSettlementNumber(), orderSnapshot, shippedQuantity, revenue, materialCost, laborCost, overheadCost, costStatus,
					missingItems.stream().distinct().toList(), requestId, access.userId());
			for (LineCalculation calculation : calculations) settlement.addLine(calculation.toEntity(settlement));
			settlementRepository.saveAndFlush(settlement);
			eventRepository.saveAndFlush(new OrderProfitEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
					settlement.getId(), "SETTLE", "SETTLED", costStatus, requestId,
					Map.of("salesOrderId", salesOrderId, "orderNumber", order.orderNumber(), "revenue", revenue,
							"materialCost", materialCost, "laborCost", laborCost, "overheadCost", overheadCost,
							"processingCost", processingCost, "grossProfit", settlement.getGrossProfit())));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "订单利润结算被其他事务更新，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			var duplicateAfterConflict = settlementRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
			if (duplicateAfterConflict.isPresent()) return toRecord(duplicateAfterConflict.get(), order.status());
			var existingAfterConflict = settlementRepository.findByTenantOrganizationIdAndSalesOrderId(access.tenantOrganizationId(), salesOrderId);
			if (existingAfterConflict.isPresent()) return toRecord(existingAfterConflict.get(), order.status());
			throw new ResponseStatusException(HttpStatus.CONFLICT, "订单利润结算请求冲突，请刷新确认结果", exception);
		}
		return toRecord(settlement, order.status());
	}

	private Map<UUID, ProductionCostSnapshot> aggregateProductionCosts(UUID tenantId, List<ProductionCostData> productionCosts) {
		Map<UUID, ProductionCostSnapshot> result = new LinkedHashMap<>();
		Map<UUID, List<ConsumedComponent>> componentsByMaterial = new LinkedHashMap<>();
		Map<UUID, List<CompletedOperationSnapshot>> operationsByMaterial = new LinkedHashMap<>();
		for (ProductionCostData data : productionCosts) {
			ProductionCostSnapshot previous = result.get(data.materialId());
			BigDecimal accepted = data.acceptedQuantity() == null ? BigDecimal.ZERO : data.acceptedQuantity();
			BigDecimal previousAccepted = previous == null ? BigDecimal.ZERO : previous.acceptedQuantity();
			BigDecimal previousConsumed = previous == null ? BigDecimal.ZERO : previous.consumedQuantity();
			List<ConsumedComponent> components = componentsByMaterial.computeIfAbsent(data.materialId(), id -> new ArrayList<>());
			List<CompletedOperationSnapshot> operations = operationsByMaterial.computeIfAbsent(data.materialId(), id -> new ArrayList<>());
			BigDecimal consumed = BigDecimal.ZERO;
			for (ConsumedMaterial material : data.materials()) {
				ConsumedComponent existing = components.stream().filter(item -> item.materialId().equals(material.materialId())).findFirst().orElse(null);
				BigDecimal unitCost = standardCostRepository
						.findFirstByTenantOrganizationIdAndMaterialIdAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
								tenantId, material.materialId(), "ACTIVE", LocalDate.now())
						.map(ItemStandardCostEntity::getUnitCost).orElse(null);
				BigDecimal amount = unitCost == null ? null : material.netQuantity().multiply(unitCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
				if (existing == null) components.add(new ConsumedComponent(material.materialId(), material.materialCode(), material.materialName(),
						material.materialSpecification(), material.unit(), material.netQuantity(), unitCost, amount));
				else {
					BigDecimal mergedAmount = existing.amount() == null ? amount : amount == null ? existing.amount() : existing.amount().add(amount);
					components.set(components.indexOf(existing), new ConsumedComponent(existing.materialId(), existing.materialCode(),
							existing.materialName(), existing.materialSpecification(), existing.unit(),
							existing.netQuantity().add(material.netQuantity()), unitCost, mergedAmount));
				}
				consumed = consumed.add(material.netQuantity());
			}
			for (CompletedOperation operation : data.operations()) {
				operations.add(new CompletedOperationSnapshot(operation.taskId(), operation.taskNumber(), operation.operationCode(),
						operation.operationName(), operation.workCenterCode(), operation.workCenterName(), operation.setupMinutes(),
						operation.runMinutesPerUnit(), operation.completedQuantity(), operation.completedAt(),
						operation.approvedActualLaborMinutes()));
			}
			UUID productionOrderId = previous == null ? data.productionOrderId() : previous.productionOrderId();
			String productionOrderNumber = previous == null ? data.productionOrderNumber()
					: previous.productionOrderNumber() + "," + data.productionOrderNumber();
			result.put(data.materialId(), new ProductionCostSnapshot(productionOrderId, productionOrderNumber,
					previousAccepted.add(accepted), previousConsumed.add(consumed), components, operations));
		}
		return result;
	}

	private LineCalculation calculateLine(CurrentWorkspaceAccess access, String currency, ProfitLine line,
			ProductionCostSnapshot productionCost) {
		BigDecimal shipped = line.deliveredQuantity().setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
		BigDecimal revenue = shipped.multiply(line.unitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		List<String> missingItems = new ArrayList<>();
		List<String> materialMissing = new ArrayList<>();
		List<String> laborMissing = new ArrayList<>();
		List<String> overheadMissing = new ArrayList<>();
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("materialCode", line.materialCode());
		details.put("shippedQuantity", shipped);
		details.put("unitPrice", line.unitPrice());
		BigDecimal materialCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
		BigDecimal laborCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
		BigDecimal overheadCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
		if (productionCost == null) {
			missingItems.add(line.materialCode() + " 缺少关联生产订单，无法归集生产成本");
		} else {
			details.put("productionOrderNumber", productionCost.productionOrderNumber());
			details.put("acceptedQuantity", productionCost.acceptedQuantity());
			details.put("consumedQuantity", productionCost.consumedQuantity());
			if (productionCost.acceptedQuantity() == null || productionCost.acceptedQuantity().signum() <= 0) {
				materialMissing.add(line.materialCode() + " 生产订单尚无合格入库数量");
				laborMissing.add(line.materialCode() + " 生产订单尚无合格入库数量");
				overheadMissing.add(line.materialCode() + " 生产订单尚无合格入库数量");
			}
			if (productionCost.components().isEmpty()) {
				materialMissing.add(line.materialCode() + " 没有实际领料记录");
			}
			List<Map<String, Object>> componentDetails = new ArrayList<>();
			BigDecimal totalConsumedCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
			for (ConsumedComponent component : productionCost.components()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("materialCode", component.materialCode());
				item.put("netQuantity", component.netQuantity());
				item.put("unitCost", component.unitCost());
				item.put("amount", component.amount());
				componentDetails.add(item);
				if (component.unitCost() == null) materialMissing.add(component.materialCode() + " 缺少有效标准成本");
				else if (component.amount() != null) totalConsumedCost = totalConsumedCost.add(component.amount());
			}
			details.put("components", componentDetails);
			if (productionCost.acceptedQuantity() != null && productionCost.acceptedQuantity().signum() > 0 && shipped.compareTo(productionCost.acceptedQuantity()) > 0) {
				materialMissing.add(line.materialCode() + " 发货数量超过生产合格入库数量");
				laborMissing.add(line.materialCode() + " 发货数量超过生产合格入库数量");
				overheadMissing.add(line.materialCode() + " 发货数量超过生产合格入库数量");
			}
			if (materialMissing.isEmpty()) {
				BigDecimal unitMaterialCost = totalConsumedCost.divide(productionCost.acceptedQuantity(), COST_SCALE, RoundingMode.HALF_UP);
				materialCost = unitMaterialCost.multiply(shipped).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
				details.put("unitMaterialCost", unitMaterialCost);
				details.put("materialCost", materialCost);
			}

			List<Map<String, Object>> operationDetails = new ArrayList<>();
			BigDecimal totalLaborCost = BigDecimal.ZERO.setScale(COST_SCALE);
			BigDecimal totalOverheadCost = BigDecimal.ZERO.setScale(COST_SCALE);
			if (productionCost.operations().isEmpty()) {
				laborMissing.add(line.materialCode() + " 没有已完工工序记录");
				overheadMissing.add(line.materialCode() + " 没有已完工工序记录");
			}
			for (CompletedOperationSnapshot operation : productionCost.operations()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("taskNumber", operation.taskNumber());
				item.put("operationCode", operation.operationCode());
				item.put("workCenterCode", operation.workCenterCode());
				BigDecimal completedQuantity = operation.completedQuantity() == null ? BigDecimal.ZERO : operation.completedQuantity();
				BigDecimal setupMinutes = operation.setupMinutes() == null ? BigDecimal.ZERO : operation.setupMinutes();
				BigDecimal runMinutes = operation.runMinutesPerUnit() == null ? BigDecimal.ZERO : operation.runMinutesPerUnit();
				BigDecimal standardMinutes = setupMinutes.add(runMinutes.multiply(completedQuantity));
				item.put("standardMinutes", standardMinutes);
				BigDecimal actualLaborMinutes = operation.approvedActualLaborMinutes();
				item.put("approvedActualLaborMinutes", actualLaborMinutes);
				if (actualLaborMinutes == null || actualLaborMinutes.signum() <= 0) {
					laborMissing.add(operation.taskNumber() + " 缺少已审核实际人工工时");
				}
				if (operation.completedAt() == null) {
					laborMissing.add(operation.taskNumber() + " 缺少工序完工时间");
					overheadMissing.add(operation.taskNumber() + " 缺少工序完工时间");
					operationDetails.add(item);
					continue;
				}
				LocalDate costDate = operation.completedAt().atZone(ZoneOffset.UTC).toLocalDate();
				WorkCenterCostRateEntity rate = workCenterCostRateRepository
						.findFirstByTenantOrganizationIdAndOwningOrganizationIdAndWorkCenterCodeAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
								access.tenantOrganizationId(), access.operatingOrganizationId(), operation.workCenterCode(), "ACTIVE", costDate)
						.orElse(null);
				item.put("costDate", costDate.toString());
				if (rate == null) {
					laborMissing.add(operation.workCenterCode() + " 缺少在 " + costDate + " 生效的工作中心成本费率");
					overheadMissing.add(operation.workCenterCode() + " 缺少在 " + costDate + " 生效的工作中心成本费率");
					operationDetails.add(item);
					continue;
				}
				item.put("rateEffectiveDate", rate.getEffectiveDate().toString());
				item.put("laborRatePerHour", rate.getLaborRatePerHour());
				item.put("overheadRatePerHour", rate.getOverheadRatePerHour());
				if (!currency.equalsIgnoreCase(rate.getCurrency())) {
					laborMissing.add(operation.workCenterCode() + " 成本费率币种与销售订单币种不一致");
					overheadMissing.add(operation.workCenterCode() + " 成本费率币种与销售订单币种不一致");
					operationDetails.add(item);
					continue;
				}
				BigDecimal laborHours = actualLaborMinutes == null ? BigDecimal.ZERO
						: actualLaborMinutes.divide(BigDecimal.valueOf(60), COST_SCALE, RoundingMode.HALF_UP);
				BigDecimal overheadHours = standardMinutes.divide(BigDecimal.valueOf(60), COST_SCALE, RoundingMode.HALF_UP);
				BigDecimal operationLaborCost = laborHours.multiply(rate.getLaborRatePerHour()).setScale(COST_SCALE, RoundingMode.HALF_UP);
				BigDecimal operationOverheadCost = overheadHours.multiply(rate.getOverheadRatePerHour()).setScale(COST_SCALE, RoundingMode.HALF_UP);
				item.put("laborTimeBasis", "APPROVED_ACTUAL_MINUTES");
				item.put("overheadTimeBasis", "STANDARD_OPERATION_MINUTES");
				item.put("laborCost", operationLaborCost);
				item.put("overheadCost", operationOverheadCost);
				totalLaborCost = totalLaborCost.add(operationLaborCost);
				totalOverheadCost = totalOverheadCost.add(operationOverheadCost);
				operationDetails.add(item);
			}
			details.put("operations", operationDetails);
			if ((laborMissing.isEmpty() || overheadMissing.isEmpty())
					&& productionCost.acceptedQuantity() != null && productionCost.acceptedQuantity().signum() > 0) {
				BigDecimal allocationFactor = shipped.divide(productionCost.acceptedQuantity(), COST_SCALE, RoundingMode.HALF_UP);
				if (laborMissing.isEmpty()) laborCost = totalLaborCost.multiply(allocationFactor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
				if (overheadMissing.isEmpty()) overheadCost = totalOverheadCost.multiply(allocationFactor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
				details.put("allocationFactor", allocationFactor);
				details.put("laborCost", laborCost);
				details.put("overheadCost", overheadCost);
			}
			missingItems.addAll(materialMissing);
			missingItems.addAll(laborMissing);
			missingItems.addAll(overheadMissing);
		}
		BigDecimal processingCost = laborCost.add(overheadCost);
		details.put("processingCost", processingCost);
		details.put("missingItems", missingItems);
		String costStatus = missingItems.isEmpty() ? "COMPLETE" : "MISSING_COST";
		return new LineCalculation(new SalesOrderLineSnapshot(line.id(), line.lineNumber(), line.materialId(), line.materialCode(),
				line.materialName(), line.materialSpecification(), line.unit(), line.orderedQuantity(), shipped, line.unitPrice()),
				productionCost, revenue, materialCost, laborCost, overheadCost, processingCost, costStatus, missingItems, details);
	}

	private OrderProfitRecord toRecord(OrderProfitSettlementEntity settlement, String orderStatus) {
		return new OrderProfitRecord(settlement.getId(), settlement.getSettlementNumber(), settlement.getSalesOrderId(),
				settlement.getOrderNumber(), settlement.getCustomerId(), settlement.getCustomerCode(), settlement.getCustomerName(),
				settlement.getCurrency(), orderStatus, settlement.getLines().stream().map(OrderProfitSettlementLineEntity::getOrderedQuantity)
						.reduce(BigDecimal.ZERO, BigDecimal::add), settlement.getShippedQuantity(), settlement.getRevenue(),
				settlement.getMaterialCost(), settlement.getLaborCost(), settlement.getOverheadCost(), settlement.getProcessingCost(),
				settlement.getTotalCost(), settlement.getGrossProfit(),
				settlement.getGrossMargin(), settlement.getCostBasis(), settlement.getCostStatus(), settlement.getStatus(),
				settlement.getMissingItems(), settlement.getVersion(), settlement.getSettledAt(),
				settlement.getLines().stream().sorted(java.util.Comparator.comparingInt(OrderProfitSettlementLineEntity::getLineNumber))
						.map(this::toLineRecord).toList());
	}

	private OrderProfitRecord.Line toLineRecord(OrderProfitSettlementLineEntity line) {
		Object missing = line.getCostDetails().get("missingItems");
		List<String> missingItems = missing instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
		return new OrderProfitRecord.Line(line.getId(), line.getSalesOrderLineId(), line.getLineNumber(), line.getProductionOrderId(),
				line.getProductionOrderNumber(), line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(),
				line.getMaterialSpecification(), line.getUnit(), line.getOrderedQuantity(), line.getShippedQuantity(),
				line.getAcceptedQuantity(), line.getConsumedQuantity(), line.getUnitPrice(), line.getRevenue(), line.getMaterialCost(),
				line.getLaborCost(), line.getOverheadCost(), line.getProcessingCost(), line.getTotalCost(), line.getGrossProfit(),
				line.getGrossMargin(), line.getCostStatus(), missingItems);
	}

	private String findOrderStatus(CurrentWorkspaceAccess access, UUID salesOrderId) {
		return salesProfitQueryProvider.findShippedOrder(access.tenantOrganizationId(), salesOrderId).map(ProfitOrder::status).orElse("UNKNOWN");
	}

	private String nextSettlementNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('finance.order_profit_settlement_number_seq')", Long.class);
		return "OP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private static void requireSettleRole(CurrentWorkspaceAccess access) {
		if (!SETTLE_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权结算订单利润");
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeCostStatus(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		return value.trim().toUpperCase();
	}
	private static String normalizeRequestId(String value) {
		if (value != null && !value.isBlank()) return value.trim();
		String mdc = MDC.get("requestId");
		return mdc != null && !mdc.isBlank() ? mdc : "order-profit-" + UUID.randomUUID();
	}

	private record LineCalculation(
			SalesOrderLineSnapshot line,
			ProductionCostSnapshot productionCost,
			BigDecimal revenue,
			BigDecimal materialCost,
			BigDecimal laborCost,
			BigDecimal overheadCost,
			BigDecimal processingCost,
			String costStatus,
			List<String> missingItems,
			Map<String, Object> costDetails) {
		private OrderProfitSettlementLineEntity toEntity(OrderProfitSettlementEntity settlement) {
			return new OrderProfitSettlementLineEntity(settlement, line, productionCost, revenue, materialCost, laborCost,
					overheadCost, costStatus, costDetails);
		}
	}
}

