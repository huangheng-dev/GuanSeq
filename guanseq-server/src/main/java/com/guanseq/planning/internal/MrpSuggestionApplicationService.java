package com.guanseq.planning.internal;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.planning.api.MrpSuggestionPage;
import com.guanseq.planning.api.MrpSuggestionRecord;
import com.guanseq.procurement.api.PurchaseOrderCommandService;
import com.guanseq.production.api.ProductionOrderCommandService;

@Service
public class MrpSuggestionApplicationService {
	private static final Set<String> PLANNING_ROLES = Set.of("PLANNING_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final MrpRunRepository runRepository;
	private final MrpRunNetRequirementRepository suggestionRepository;
	private final MrpSuggestionEventRepository eventRepository;
	private final PurchaseOrderCommandService purchaseOrderCommandService;
	private final ProductionOrderCommandService productionOrderCommandService;

	MrpSuggestionApplicationService(CurrentWorkspaceProvider workspaceProvider, MrpRunRepository runRepository,
			MrpRunNetRequirementRepository suggestionRepository, MrpSuggestionEventRepository eventRepository,
			PurchaseOrderCommandService purchaseOrderCommandService,
			ProductionOrderCommandService productionOrderCommandService) {
		this.workspaceProvider = workspaceProvider; this.runRepository = runRepository;
		this.suggestionRepository = suggestionRepository; this.eventRepository = eventRepository;
		this.purchaseOrderCommandService = purchaseOrderCommandService;
		this.productionOrderCommandService = productionOrderCommandService;
	}

	@Transactional(readOnly = true)
	public MrpSuggestionPage list(String username, String query, String status, String type, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = suggestionRepository.searchSuggestions(access.tenantOrganizationId(), normalize(query),
				normalizeFilter(status), normalizeFilter(type), PageRequest.of(Math.max(0, page),
						Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.ASC, "requiredDate")
								.and(Sort.by("requirementLevel", "materialCode"))));
		Map<UUID, MrpRunEntity> runs = runRepository.findAllById(result.getContent().stream()
				.map(MrpRunNetRequirementEntity::getRunId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(MrpRunEntity::getId, Function.identity()));
		return new MrpSuggestionPage(result.getContent().stream().map(item -> toRecord(item, runs.get(item.getRunId()))).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public MrpSuggestionRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		MrpRunNetRequirementEntity suggestion = requireSuggestion(access, id);
		return toRecord(suggestion, requireRun(access, suggestion.getRunId()));
	}

	@Transactional
	public MrpSuggestionRecord action(String username, UUID id, MrpSuggestionRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requirePlanningRole(access);
		String requestId = requestId();
		MrpSuggestionRecord replay = replay(access, id, request.action(), requestId);
		if (replay != null) return replay;
		MrpRunNetRequirementEntity suggestion = requireSuggestion(access, id);
		requireVersion(suggestion, request.expectedVersion());
		if (!"PROPOSED".equals(suggestion.getDecisionStatus())) throw conflict("只有待审核建议可以审核或驳回");
		String target;
		if ("APPROVE".equals(request.action())) target = "APPROVED";
		else {
			if (request.comment() == null || request.comment().isBlank())
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回计划建议必须填写原因");
			target = "REJECTED";
		}
		String from = suggestion.getDecisionStatus(); suggestion.decide(target, trim(request.comment()), access.userId());
		suggestionRepository.saveAndFlush(suggestion);
		eventRepository.save(new MrpSuggestionEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				suggestion.getId(), request.action(), from, target, requestId, trim(request.comment()),
				Map.of("materialCode", suggestion.getMaterialCode(), "recommendationType", suggestion.getRecommendationType())));
		return toRecord(suggestion, requireRun(access, suggestion.getRunId()));
	}

	@Transactional
	public MrpSuggestionRecord convert(String username, UUID id, MrpSuggestionRecord.ConvertRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requirePlanningRole(access);
		String requestId = requestId(); MrpSuggestionRecord replay = replay(access, id, "CONVERT", requestId);
		if (replay != null) return replay;
		MrpRunNetRequirementEntity suggestion = requireSuggestion(access, id); requireVersion(suggestion, request.expectedVersion());
		if (!"APPROVED".equals(suggestion.getDecisionStatus())) throw conflict("只有已审核建议可以转为执行单据");
		MrpRunEntity run = requireRun(access, suggestion.getRunId());
		UUID orderId; String orderNumber; String orderType;
		if (Set.of("PURCHASE", "OUTSOURCE").contains(suggestion.getRecommendationType())) {
			if (request.supplierId() == null || request.requestedReceiptDate() == null)
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购或委外建议转单必须选择供应商和要求到货日期");
			var created = purchaseOrderCommandService.createFromMrp(username, new PurchaseOrderCommandService.CreateFromMrpCommand(
					suggestion.getId(), run.getRunNumber(), suggestion.getMaterialId(), suggestion.getNetQuantity(),
					request.supplierId(), request.currency(), request.taxRate(), request.unitPrice(),
					request.requestedReceiptDate(), request.buyer()));
			orderId = created.id(); orderNumber = created.orderNumber(); orderType = "PURCHASE_ORDER";
		} else if ("PRODUCTION".equals(suggestion.getRecommendationType())) {
			if (request.plannedStartDate() == null || request.plannedReceiptDate() == null)
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "生产建议转单必须填写计划开工和完工日期");
			if (request.plannedReceiptDate().isBefore(LocalDate.now()))
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "生产建议转单的计划完工日期不能早于今天");
			var created = productionOrderCommandService.createFromMrp(username, new ProductionOrderCommandService.CreateFromMrpCommand(
					suggestion.getId(), run.getRunNumber(), suggestion.getMaterialId(), suggestion.getNetQuantity(),
					request.plannedStartDate(), request.plannedReceiptDate(), request.workshop(), request.owner()));
			orderId = created.id(); orderNumber = created.orderNumber(); orderType = "PRODUCTION_ORDER";
		} else throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "当前结果不是可转单的计划建议");
		String from = suggestion.getDecisionStatus(); suggestion.convert(orderType, orderId, orderNumber, access.userId());
		suggestionRepository.saveAndFlush(suggestion);
		eventRepository.save(new MrpSuggestionEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
				suggestion.getId(), "CONVERT", from, "CONVERTED", requestId, null,
				Map.of("orderType", orderType, "orderId", orderId.toString(), "orderNumber", orderNumber)));
		return toRecord(suggestion, run);
	}

	private MrpSuggestionRecord replay(CurrentWorkspaceAccess access, UUID suggestionId, String action, String requestId) {
		var event = eventRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (event.isEmpty()) return null;
		if (!event.get().getSuggestionId().equals(suggestionId) || !event.get().getAction().equals(action))
			throw conflict("请求编号已用于其他计划建议操作");
		MrpRunNetRequirementEntity suggestion = requireSuggestion(access, suggestionId);
		return toRecord(suggestion, requireRun(access, suggestion.getRunId()));
	}

	private MrpSuggestionRecord toRecord(MrpRunNetRequirementEntity item, MrpRunEntity run) {
		return new MrpSuggestionRecord(item.getId(), item.getRunId(), run.getRunNumber(), run.getName(),
				item.getRequirementLevel(), item.getSourceType(), item.getParentMaterialCode(), item.getMaterialId(),
				item.getMaterialCode(), item.getMaterialName(), item.getProcurementType(), item.getUnit(),
				item.getGrossQuantity(), item.getNetQuantity(), item.getRequiredDate(), item.getRecommendedReleaseDate(),
				item.getRecommendationType(), item.getDecisionStatus(), item.getDecisionComment(), item.getDecidedAt(),
				item.getConvertedOrderType(), item.getConvertedOrderId(), item.getConvertedOrderNumber(), item.getConvertedAt(),
				item.getVersion(), item.getCreatedAt());
	}

	private MrpRunNetRequirementEntity requireSuggestion(CurrentWorkspaceAccess access, UUID id) {
		return suggestionRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.filter(item -> Set.of("PRODUCTION", "PURCHASE", "OUTSOURCE").contains(item.getRecommendationType()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MRP 计划建议不存在或不在当前租户范围"));
	}
	private MrpRunEntity requireRun(CurrentWorkspaceAccess access, UUID id) {
		return runRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MRP 运算记录不存在或不在当前租户范围"));
	}
	private static void requireVersion(MrpRunNetRequirementEntity item, long expected) {
		if (item.getVersion() != expected) throw conflict("计划建议已被其他用户处理，请刷新后重试");
	}
	private static void requirePlanningRole(CurrentWorkspaceAccess access) {
		if (!PLANNING_ROLES.contains(access.roleCode()))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权审核或转换 MRP 建议");
	}
	private static String requestId() { String value = MDC.get("requestId"); return value == null ? UUID.randomUUID().toString() : value; }
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeFilter(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
