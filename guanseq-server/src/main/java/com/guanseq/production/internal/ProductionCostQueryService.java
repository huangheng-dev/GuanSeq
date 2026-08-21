package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guanseq.production.api.ProductionCostQueryProvider;

@Service
class ProductionCostQueryService implements ProductionCostQueryProvider {

	private final ProductionOrderRepository orderRepository;
	private final MaterialIssueRepository issueRepository;
	private final MaterialIssueLineRepository issueLineRepository;
	private final OperationTaskRepository operationTaskRepository;
	private final OperationLaborEntryRepository laborEntryRepository;

	ProductionCostQueryService(ProductionOrderRepository orderRepository, MaterialIssueRepository issueRepository,
			MaterialIssueLineRepository issueLineRepository, OperationTaskRepository operationTaskRepository,
			OperationLaborEntryRepository laborEntryRepository) {
		this.orderRepository = orderRepository;
		this.issueRepository = issueRepository;
		this.issueLineRepository = issueLineRepository;
		this.operationTaskRepository = operationTaskRepository;
		this.laborEntryRepository = laborEntryRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProductionCostData> findCostsForSalesOrder(UUID tenantOrganizationId, UUID salesOrderId) {
		return orderRepository.findAllByTenantOrganizationIdAndSourceTypeAndSourceId(tenantOrganizationId, "SALES_ORDER", salesOrderId).stream()
				.filter(order -> !"CANCELLED".equals(order.getStatus()))
				.map(order -> {
					Map<UUID, ConsumedMaterialAccumulator> consumed = new LinkedHashMap<>();
					for (MaterialIssueEntity issue : issueRepository
							.findByTenantOrganizationIdAndProductionOrderIdAndStatusNot(tenantOrganizationId, order.getId(), "CANCELLED")) {
						for (MaterialIssueLineEntity line : issueLineRepository
								.findByTenantOrganizationIdAndIssueIdOrderByLineNumberAsc(tenantOrganizationId, issue.getId())) {
							BigDecimal net = line.getIssuedQuantity().subtract(line.getReturnedQuantity());
							if (net.signum() <= 0) continue;
							consumed.computeIfAbsent(line.getComponentMaterialId(),
									id -> new ConsumedMaterialAccumulator(line.getComponentMaterialId(), line.getComponentMaterialCode(),
											line.getComponentMaterialName(), line.getComponentMaterialSpecification(), line.getUnit()))
									.add(net);
						}
					}
					var operations = operationTaskRepository
							.findByTenantOrganizationIdAndOrderIdOrderBySequenceNumberAsc(tenantOrganizationId, order.getId()).stream()
							.filter(task -> "COMPLETED".equals(task.getStatus()))
							.map(task -> new CompletedOperation(task.getId(), task.getTaskNumber(), task.getOperationCode(),
									task.getOperationName(), task.getWorkCenterCode(), task.getWorkCenterName(), task.getSetupMinutes(),
									task.getRunMinutesPerUnit(), task.getCompletedQuantity(), task.getCompletedAt(),
									laborEntryRepository.sumApprovedMinutes(tenantOrganizationId, task.getId())))
							.toList();
					return new ProductionCostData(order.getId(), order.getOrderNumber(), salesOrderId, order.getMaterialId(),
							order.getCompletedQuantity(), consumed.values().stream().map(ConsumedMaterialAccumulator::toRecord).toList(),
							operations);
				}).toList();
	}

	private static final class ConsumedMaterialAccumulator {
		private final UUID materialId;
		private final String materialCode;
		private final String materialName;
		private final String materialSpecification;
		private final String unit;
		private BigDecimal netQuantity = BigDecimal.ZERO;

		private ConsumedMaterialAccumulator(UUID materialId, String materialCode, String materialName,
				String materialSpecification, String unit) {
			this.materialId = materialId;
			this.materialCode = materialCode;
			this.materialName = materialName;
			this.materialSpecification = materialSpecification;
			this.unit = unit;
		}

		private void add(BigDecimal quantity) { this.netQuantity = netQuantity.add(quantity); }

		private ConsumedMaterial toRecord() {
			return new ConsumedMaterial(materialId, materialCode, materialName, materialSpecification, unit, netQuantity);
		}
	}
}
