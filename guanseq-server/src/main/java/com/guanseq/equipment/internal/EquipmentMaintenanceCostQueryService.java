package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.CostEvidence;
import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.LaborTransaction;
import com.guanseq.equipment.api.EquipmentMaintenanceCostRecord.SpareTransaction;

@Service
public class EquipmentMaintenanceCostQueryService {
	static final String BASIS = "备件按领用日有效标准成本；人工按本次登记小时费率估算；不生成财务凭证";

	private final MaintenanceSpareTransactionRepository spareRepository;
	private final MaintenanceLaborTransactionRepository laborRepository;

	EquipmentMaintenanceCostQueryService(MaintenanceSpareTransactionRepository spareRepository,
			MaintenanceLaborTransactionRepository laborRepository) {
		this.spareRepository = spareRepository;
		this.laborRepository = laborRepository;
	}

	@Transactional(readOnly = true)
	public CostEvidence get(EquipmentWorkOrderEntity order) {
		List<MaintenanceSpareTransactionEntity> spareEntities = spareRepository
				.findByWorkOrderIdOrderByOccurredAtDesc(order.getId());
		Map<UUID, BigDecimal> returned = new HashMap<>();
		for (MaintenanceSpareTransactionEntity item : spareEntities) {
			if ("RETURN".equals(item.getTransactionType())) returned.merge(item.getReturnOfIssueId(), item.getQuantity(), BigDecimal::add);
		}
		List<SpareTransaction> spareTransactions = spareEntities.stream().map(item -> {
			BigDecimal returnedQuantity = "ISSUE".equals(item.getTransactionType())
					? returned.getOrDefault(item.getId(), BigDecimal.ZERO) : BigDecimal.ZERO;
			BigDecimal returnable = "ISSUE".equals(item.getTransactionType())
					? item.getQuantity().subtract(returnedQuantity).max(BigDecimal.ZERO) : BigDecimal.ZERO;
			return new SpareTransaction(item.getId(), item.getTransactionType(), item.getReturnOfIssueId(),
					item.getSparePartId(), item.getMaterialCode(), item.getMaterialName(), item.getMaterialSpecification(),
					item.getUnit(), item.getQuantity(), returnedQuantity, returnable, item.getUnitCost(), item.getCurrency(),
					item.getAmount(), item.getWarehouseId(), item.getWarehouseCode(), item.getWarehouseName(),
					item.getWarehouseEvidence(), item.getReason(), item.getRequestId(), item.getActorUserId(), item.getOccurredAt());
		}).toList();

		List<MaintenanceLaborTransactionEntity> laborEntities = laborRepository
				.findByWorkOrderIdOrderByOccurredAtDesc(order.getId());
		var reversedIds = laborEntities.stream().filter(item -> "REVERSAL".equals(item.getTransactionType()))
				.map(MaintenanceLaborTransactionEntity::getReversalOfEntryId).collect(java.util.stream.Collectors.toSet());
		List<LaborTransaction> laborTransactions = laborEntities.stream().map(item -> new LaborTransaction(item.getId(),
				item.getTransactionType(), item.getReversalOfEntryId(), item.getTechnicianName(), item.getHours(),
				item.getHourlyRate(), item.getCurrency(), item.getAmount(), reversedIds.contains(item.getId()), item.getReason(),
				item.getRequestId(), item.getActorUserId(), item.getOccurredAt())).toList();

		BigDecimal spareCost = spareEntities.stream().map(item -> "RETURN".equals(item.getTransactionType())
				? item.getAmount().negate() : item.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal laborCost = laborEntities.stream().map(item -> "REVERSAL".equals(item.getTransactionType())
				? item.getAmount().negate() : item.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
		String currency = spareEntities.stream().map(MaintenanceSpareTransactionEntity::getCurrency).findFirst()
				.orElseGet(() -> laborEntities.stream().map(MaintenanceLaborTransactionEntity::getCurrency).findFirst().orElse("CNY"));
		List<String> actions = "REPAIR".equals(order.getWorkType()) && "IN_PROGRESS".equals(order.getStatus())
				? List.of("ISSUE_SPARE", "RETURN_SPARE", "RECORD_LABOR", "REVERSE_LABOR") : List.of();
		return new CostEvidence(spareCost, laborCost, spareCost.add(laborCost), currency, BASIS,
				spareTransactions, laborTransactions, actions);
	}
}
