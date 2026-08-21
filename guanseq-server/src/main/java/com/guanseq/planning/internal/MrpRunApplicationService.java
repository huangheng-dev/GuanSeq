package com.guanseq.planning.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.guanseq.planning.api.MrpRunPage;
import com.guanseq.planning.api.MrpRunRecord;
import com.guanseq.procurement.api.ScheduledReceiptProvider;
import com.guanseq.product.api.BomReferenceProvider;
import com.guanseq.product.api.BomReferenceProvider.EffectiveBom;
import com.guanseq.product.api.RoutingReferenceProvider;
import com.guanseq.production.api.ProductionScheduledReceiptProvider;
import com.guanseq.warehouse.api.StockPositionProvider;
import com.guanseq.warehouse.api.StockPositionProvider.StockPosition;

@Service
public class MrpRunApplicationService {
	private static final Set<String> PLANNING_ROLES = Set.of("PLANNING_MANAGER", "ADMIN");
	private static final int MAX_BOM_DEPTH = 50;
	private static final BigDecimal ONE_HUNDRED_PERCENT = BigDecimal.ONE;

	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final BomReferenceProvider bomReferenceProvider;
	private final RoutingReferenceProvider routingReferenceProvider;
	private final StockPositionProvider stockPositionProvider;
	private final ScheduledReceiptProvider purchaseReceiptProvider;
	private final ProductionScheduledReceiptProvider productionReceiptProvider;
	private final MaterialPlanningParameterRepository planningParameterRepository;
	private final IndependentDemandRepository demandRepository;
	private final MrpRunRepository runRepository;
	private final MrpRunDemandRepository runDemandRepository;
	private final MrpRunSupplyRepository runSupplyRepository;
	private final MrpRunScheduledReceiptRepository scheduledReceiptRepository;
	private final MrpRunNetRequirementRepository netRequirementRepository;
	private final MrpRunExceptionRepository exceptionRepository;
	private final MrpRunEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	MrpRunApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider, BomReferenceProvider bomReferenceProvider,
			RoutingReferenceProvider routingReferenceProvider, StockPositionProvider stockPositionProvider,
			ScheduledReceiptProvider purchaseReceiptProvider,
			ProductionScheduledReceiptProvider productionReceiptProvider,
			MaterialPlanningParameterRepository planningParameterRepository,
			IndependentDemandRepository demandRepository, MrpRunRepository runRepository,
			MrpRunDemandRepository runDemandRepository, MrpRunSupplyRepository runSupplyRepository,
			MrpRunScheduledReceiptRepository scheduledReceiptRepository,
			MrpRunNetRequirementRepository netRequirementRepository,
			MrpRunExceptionRepository exceptionRepository, MrpRunEventRepository eventRepository,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.masterDataProvider = masterDataProvider;
		this.bomReferenceProvider = bomReferenceProvider; this.routingReferenceProvider = routingReferenceProvider;
		this.stockPositionProvider = stockPositionProvider; this.purchaseReceiptProvider = purchaseReceiptProvider;
		this.productionReceiptProvider = productionReceiptProvider;
		this.planningParameterRepository = planningParameterRepository; this.demandRepository = demandRepository;
		this.runRepository = runRepository; this.runDemandRepository = runDemandRepository;
		this.runSupplyRepository = runSupplyRepository; this.scheduledReceiptRepository = scheduledReceiptRepository;
		this.netRequirementRepository = netRequirementRepository; this.exceptionRepository = exceptionRepository;
		this.eventRepository = eventRepository; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public MrpRunPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = runRepository.search(access.tenantOrganizationId(), normalize(query), normalizeFilter(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "startedAt")));
		return new MrpRunPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public MrpRunRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); return toRecord(requireRun(access, id));
	}

	@Transactional
	public MrpRunRecord create(String username, MrpRunRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requirePlanningRole(access);
		validateHorizon(request.horizonStart(), request.horizonEnd()); String requestId = MDC.get("requestId");
		if (requestId != null) {
			var existing = runRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
			if (existing.isPresent()) return toRecord(existing.get());
		}

		List<IndependentDemandEntity> demands = demandRepository
				.findByTenantOrganizationIdAndStatusAndRequiredDateBetweenOrderByRequiredDateAscDemandNumberAsc(
						access.tenantOrganizationId(), "ACTIVE", request.horizonStart(), request.horizonEnd());
		if (demands.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "所选期间没有有效独立需求，未创建 MRP 记录");

		Map<UUID, MaterialReference> materials = new LinkedHashMap<>(); Map<UUID, Integer> lowLevels = new HashMap<>();
		for (IndependentDemandEntity demand : demands) {
			MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), demand.getMaterialId());
			materials.putIfAbsent(material.id(), material); discoverBomUniverse(access.tenantOrganizationId(), material,
					demand.getRequiredDate(), 0, materials, lowLevels, new HashSet<>());
		}

		BigDecimal totalQuantity = demands.stream().map(IndependentDemandEntity::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
		MrpRunEntity run = runRepository.saveAndFlush(new MrpRunEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextRunNumber(), request.name(), request.horizonStart(),
				request.horizonEnd(), demands.size(), totalQuantity, access.userId(), requestId));
		runDemandRepository.saveAll(demands.stream().map(demand -> new MrpRunDemandEntity(run.getId(),
				access.tenantOrganizationId(), demand, materials.get(demand.getMaterialId()).procurementType())).toList());

		Map<UUID, StockPosition> stockPositions = new LinkedHashMap<>();
		stockPositionProvider.getPositions(access.tenantOrganizationId(), materials.keySet())
				.forEach(position -> stockPositions.put(position.materialId(), position));
		runSupplyRepository.saveAll(materials.values().stream().map(material -> new MrpRunSupplyEntity(run.getId(),
				access.tenantOrganizationId(), material, stockPositions.getOrDefault(material.id(), emptyPosition(material.id())))).toList());

		List<MrpRunScheduledReceiptEntity> receiptSnapshots = new ArrayList<>();
		purchaseReceiptProvider.listReleasedPurchaseReceipts(access.tenantOrganizationId(), materials.keySet(), request.horizonEnd())
				.forEach(receipt -> receiptSnapshots.add(new MrpRunScheduledReceiptEntity(run.getId(), access.tenantOrganizationId(), receipt)));
		productionReceiptProvider.listReleasedProductionReceipts(access.tenantOrganizationId(), materials.keySet(), request.horizonEnd())
				.forEach(receipt -> receiptSnapshots.add(new MrpRunScheduledReceiptEntity(run.getId(), access.tenantOrganizationId(), receipt)));
		scheduledReceiptRepository.saveAll(receiptSnapshots);

		Map<UUID, Integer> leadTimes = new HashMap<>();
		planningParameterRepository.findByTenantOrganizationIdAndMaterialIdIn(access.tenantOrganizationId(), materials.keySet())
				.forEach(item -> leadTimes.put(item.getMaterialId(), item.getLeadTimeDays()));
		PlanningResult planningResult = calculateNetRequirements(run, access, demands, materials, lowLevels,
				stockPositions, receiptSnapshots, leadTimes);
		netRequirementRepository.saveAll(planningResult.results()); exceptionRepository.saveAll(planningResult.exceptions());
		String targetStatus;
		if (planningResult.exceptions().isEmpty()) { run.complete(); targetStatus = "COMPLETED"; }
		else { run.block(planningResult.exceptions().size()); targetStatus = "BLOCKED"; }
		runRepository.saveAndFlush(run);
		eventRepository.save(new MrpRunEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				run.getId(), "PREPARING", targetStatus, requestId, Map.of("demandCount", demands.size(),
						"resultCount", planningResult.results().size(), "exceptionCount", planningResult.exceptions().size(),
						"horizonStart", request.horizonStart().toString(), "horizonEnd", request.horizonEnd().toString())));
		return toRecord(run);
	}

	private void discoverBomUniverse(UUID tenantId, MaterialReference material, LocalDate effectiveDate, int level,
			Map<UUID, MaterialReference> materials, Map<UUID, Integer> lowLevels, Set<UUID> path) {
		if (level > MAX_BOM_DEPTH || !path.add(material.id())) return;
		lowLevels.merge(material.id(), level, Math::max);
		if ("MAKE".equals(material.procurementType())) {
			bomReferenceProvider.findEffectiveBom(tenantId, material.id(), effectiveDate).ifPresent(bom -> bom.components().forEach(component -> {
				MaterialReference child = masterDataProvider.requireActiveMaterial(tenantId, component.materialId());
				materials.putIfAbsent(child.id(), child);
				discoverBomUniverse(tenantId, child, effectiveDate, level + 1, materials, lowLevels, new HashSet<>(path));
			}));
		}
	}

	private PlanningResult calculateNetRequirements(MrpRunEntity run, CurrentWorkspaceAccess access,
			List<IndependentDemandEntity> demands, Map<UUID, MaterialReference> materials, Map<UUID, Integer> lowLevels,
			Map<UUID, StockPosition> stockPositions, List<MrpRunScheduledReceiptEntity> receipts,
			Map<UUID, Integer> leadTimes) {
		Map<UUID, BigDecimal> availableRemaining = new HashMap<>();
		materials.keySet().forEach(id -> availableRemaining.put(id,
				stockPositions.getOrDefault(id, emptyPosition(id)).availableQuantity()));
		Map<UUID, List<ReceiptBalance>> receiptBalances = new HashMap<>();
		for (MrpRunScheduledReceiptEntity receipt : receipts) receiptBalances
				.computeIfAbsent(receipt.getMaterialId(), ignored -> new ArrayList<>()).add(new ReceiptBalance(receipt));
		receiptBalances.values().forEach(items -> items.sort(Comparator.comparing(item -> item.entity.getExpectedReceiptDate())));

		Map<UUID, List<RequirementInput>> requirements = new HashMap<>();
		for (IndependentDemandEntity demand : demands) requirements.computeIfAbsent(demand.getMaterialId(), ignored -> new ArrayList<>())
				.add(new RequirementInput(demand.getMaterialId(), demand.getQuantity(), demand.getRequiredDate(),
						"INDEPENDENT_DEMAND", null, null));
		List<MrpRunNetRequirementEntity> results = new ArrayList<>(); List<MrpRunExceptionEntity> exceptions = new ArrayList<>();
		Set<String> exceptionKeys = new HashSet<>(); int maximumLevel = lowLevels.values().stream().max(Integer::compareTo).orElse(0);

		for (int level = 0; level <= maximumLevel; level++) {
			final int currentLevel = level;
			List<UUID> levelMaterials = lowLevels.entrySet().stream().filter(item -> item.getValue() == currentLevel)
					.map(Map.Entry::getKey).sorted(Comparator.comparing(id -> materials.get(id).code())).toList();
			for (UUID materialId : levelMaterials) {
				MaterialReference material = materials.get(materialId); List<RequirementInput> materialRequirements = requirements.getOrDefault(materialId, List.of()).stream()
						.sorted(Comparator.comparing(RequirementInput::requiredDate)).toList();
				for (RequirementInput requirement : materialRequirements) {
					BigDecimal gross = scale(requirement.quantity()); BigDecimal available = availableRemaining.getOrDefault(materialId, BigDecimal.ZERO);
					BigDecimal fromAvailable = gross.min(available); availableRemaining.put(materialId, available.subtract(fromAvailable));
					BigDecimal remaining = gross.subtract(fromAvailable); BigDecimal fromReceipts = consumeReceipts(receiptBalances.getOrDefault(materialId, List.of()), requirement.requiredDate(), remaining);
					BigDecimal net = scale(remaining.subtract(fromReceipts)); String recommendation = "NONE"; LocalDate releaseDate = null;
					if (net.signum() > 0) {
						boolean blocked = false; StockPosition position = stockPositions.getOrDefault(materialId, emptyPosition(materialId));
						if (position.balanceCount() == 0) { blocked = true; addException(exceptions, exceptionKeys, run, access, "STOCK_POSITION_UNAVAILABLE", material,
								"物料 " + material.code() + " 尚无库存余额事实，无法确认净需求基线。", "在库存现存量中建立该物料的真实入库或期初事务。"); }
						Integer leadTime = leadTimes.get(materialId);
						if (leadTime == null) { blocked = true; addException(exceptions, exceptionKeys, run, access, "LEAD_TIME_UNAVAILABLE", material,
								"物料 " + material.code() + " 尚无受控提前期，无法反推建议下达日期。", "在物料计划参数中维护采购、生产或委外提前期。"); }
						else releaseDate = requirement.requiredDate().minusDays(leadTime);
						EffectiveBom bom = null;
						if ("MAKE".equals(material.procurementType())) {
							bom = bomReferenceProvider.findEffectiveBom(access.tenantOrganizationId(), materialId, requirement.requiredDate()).orElse(null);
							if (bom == null) { blocked = true; addException(exceptions, exceptionKeys, run, access, "BOM_UNAVAILABLE", material,
									"自制物料 " + material.code() + " 尚无已生效 BOM，无法展开净需求。", "建立并发布该物料的有效 BOM 版本。"); }
							if (!routingReferenceProvider.hasEffectiveRouting(access.tenantOrganizationId(), materialId, requirement.requiredDate())) {
								blocked = true; addException(exceptions, exceptionKeys, run, access, "ROUTING_UNAVAILABLE", material,
										"自制物料 " + material.code() + " 尚无已生效工艺路线，无法形成可信生产建议。", "建立并发布该物料的有效工艺路线版本。");
							}
						}
						recommendation = blocked ? "BLOCKED" : switch (material.procurementType()) { case "MAKE" -> "PRODUCTION"; case "OUTSOURCE" -> "OUTSOURCE"; default -> "PURCHASE"; };
						if (!blocked && "MAKE".equals(material.procurementType()) && bom != null) {
							LocalDate componentDate = releaseDate == null ? requirement.requiredDate() : releaseDate;
							for (BomReferenceProvider.Component component : bom.components()) {
								BigDecimal componentGross = net.divide(bom.baseQuantity(), 12, RoundingMode.HALF_UP)
										.multiply(component.quantity()).multiply(ONE_HUNDRED_PERCENT.add(component.scrapRate()));
								requirements.computeIfAbsent(component.materialId(), ignored -> new ArrayList<>())
										.add(new RequirementInput(component.materialId(), scale(componentGross), componentDate,
												"BOM_COMPONENT", material.id(), material.code()));
							}
						}
					}
					results.add(new MrpRunNetRequirementEntity(run.getId(), access.tenantOrganizationId(), level,
							requirement.sourceType(), requirement.parentMaterialId(), requirement.parentMaterialCode(), material,
							gross, scale(fromAvailable), scale(fromReceipts), net, requirement.requiredDate(), releaseDate, recommendation));
				}
			}
		}
		return new PlanningResult(results, exceptions);
	}

	private static BigDecimal consumeReceipts(List<ReceiptBalance> receipts, LocalDate requiredDate, BigDecimal required) {
		BigDecimal remaining = required; BigDecimal consumed = BigDecimal.ZERO;
		for (ReceiptBalance receipt : receipts) {
			if (remaining.signum() <= 0 || receipt.entity.getExpectedReceiptDate().isAfter(requiredDate)) break;
			BigDecimal quantity = remaining.min(receipt.remaining); receipt.remaining = receipt.remaining.subtract(quantity);
			remaining = remaining.subtract(quantity); consumed = consumed.add(quantity);
		}
		return scale(consumed);
	}

	private static void addException(List<MrpRunExceptionEntity> exceptions, Set<String> keys, MrpRunEntity run,
			CurrentWorkspaceAccess access, String code, MaterialReference material, String message, String resolution) {
		if (keys.add(code + ":" + material.id())) exceptions.add(new MrpRunExceptionEntity(run.getId(),
				access.tenantOrganizationId(), code, material.id(), material.code(), material.name(), message, resolution));
	}

	private MrpRunRecord toRecord(MrpRunEntity run) {
		List<MrpRunRecord.DemandSnapshot> demands = runDemandRepository.findByRunIdOrderByRequiredDateAscDemandNumberAsc(run.getId()).stream()
				.map(item -> new MrpRunRecord.DemandSnapshot(item.getId(), item.getDemandId(), item.getDemandNumber(), item.getSourceType(),
						item.getSourceNumber(), item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(), item.getMaterialSpecification(),
						item.getProcurementType(), item.getUnit(), item.getQuantity(), item.getRequiredDate(), item.getPriority(), item.getOwner(), item.getSnapshottedAt())).toList();
		List<MrpRunRecord.SupplySnapshot> supplies = runSupplyRepository.findByRunIdOrderByMaterialCodeAsc(run.getId()).stream()
				.map(item -> new MrpRunRecord.SupplySnapshot(item.getId(), item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(),
						item.getUnit(), item.getOnHandQuantity(), item.getAllocatedQuantity(), item.getFrozenQuantity(), item.getAvailableQuantity(),
						item.getBalanceCount(), item.getSnapshottedAt())).toList();
		List<MrpRunRecord.ScheduledReceiptSnapshot> scheduledReceipts = scheduledReceiptRepository.findByRunIdOrderByExpectedReceiptDateAscSourceOrderNumberAsc(run.getId()).stream()
				.map(item -> new MrpRunRecord.ScheduledReceiptSnapshot(item.getId(), item.getSourceType(), item.getSourceOrderId(), item.getSourceOrderNumber(),
						item.getSourceLineId(), item.getSourceName(), item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(), item.getUnit(),
						item.getOutstandingQuantity(), item.getExpectedReceiptDate(), item.getSnapshottedAt())).toList();
		List<MrpRunRecord.NetRequirement> netRequirements = netRequirementRepository.findByRunIdOrderByRequiredDateAscRequirementLevelAscMaterialCodeAsc(run.getId()).stream()
				.map(item -> new MrpRunRecord.NetRequirement(item.getId(), item.getRequirementLevel(), item.getSourceType(), item.getParentMaterialId(),
						item.getParentMaterialCode(), item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(), item.getProcurementType(),
						item.getUnit(), item.getGrossQuantity(), item.getAvailableConsumed(), item.getScheduledReceiptConsumed(), item.getNetQuantity(),
						item.getRequiredDate(), item.getRecommendedReleaseDate(), item.getRecommendationType(), item.getDecisionStatus(),
						item.getConvertedOrderType(), item.getConvertedOrderId(), item.getConvertedOrderNumber(), item.getVersion(),
						item.getCreatedAt())).toList();
		List<MrpRunRecord.RunException> exceptions = exceptionRepository.findByRunIdOrderByCodeAscMaterialCodeAsc(run.getId()).stream()
				.map(item -> new MrpRunRecord.RunException(item.getId(), item.getCode(), item.getSeverity(), item.getMaterialId(), item.getMaterialCode(),
						item.getMaterialName(), item.getMessage(), item.getResolutionPath(), item.getCreatedAt())).toList();
		return new MrpRunRecord(run.getId(), run.getRunNumber(), run.getName(), run.getHorizonStart(), run.getHorizonEnd(), run.getStatus(),
				run.getDemandCount(), run.getTotalQuantity(), run.getExceptionCount(), run.getStartedAt(), run.getFinishedAt(), run.getRequestId(),
				run.getVersion(), demands, supplies, scheduledReceipts, netRequirements, exceptions);
	}

	private MrpRunEntity requireRun(CurrentWorkspaceAccess access, UUID id) { return runRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MRP 运算记录不存在或不在当前租户范围")); }
	private String nextRunNumber() { Long sequence = jdbcTemplate.queryForObject("select nextval('planning.mrp_run_number_seq')", Long.class); return "MRP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence); }
	private static StockPosition emptyPosition(UUID materialId) { return new StockPosition(materialId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0); }
	private static BigDecimal scale(BigDecimal value) { return value.setScale(6, RoundingMode.HALF_UP); }
	private static void validateHorizon(LocalDate start, LocalDate end) { if (start.isAfter(end)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "计划开始日期不能晚于结束日期"); if (end.isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "计划期间不能全部早于今天"); if (start.plusYears(1).isBefore(end)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "单次 MRP 计划期间不能超过一年"); }
	private static void requirePlanningRole(CurrentWorkspaceAccess access) { if (!PLANNING_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权发起 MRP 运算"); }
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeFilter(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }

	private record RequirementInput(UUID materialId, BigDecimal quantity, LocalDate requiredDate, String sourceType,
			UUID parentMaterialId, String parentMaterialCode) { }
	private record PlanningResult(List<MrpRunNetRequirementEntity> results, List<MrpRunExceptionEntity> exceptions) { }
	private static final class ReceiptBalance {
		private final MrpRunScheduledReceiptEntity entity; private BigDecimal remaining;
		private ReceiptBalance(MrpRunScheduledReceiptEntity entity) { this.entity = entity; this.remaining = entity.getOutstandingQuantity(); }
	}
}
