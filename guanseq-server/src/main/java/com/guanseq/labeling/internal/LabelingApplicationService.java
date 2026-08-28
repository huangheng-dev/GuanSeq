package com.guanseq.labeling.internal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.identity.api.LabelEmployeeReferenceProvider;
import com.guanseq.labeling.api.LabelPrintRequestPage;
import com.guanseq.labeling.api.LabelPrintRequestRecord;
import com.guanseq.labeling.api.LabelReferenceData;
import com.guanseq.production.api.LabelOperationTaskReferenceProvider;
import com.guanseq.warehouse.api.LabelStockBalanceReferenceProvider;

@Service
public class LabelingApplicationService {
	private static final Set<String> LABEL_ROLES = Set.of("PRODUCTION_OPERATOR", "PRODUCTION_MANAGER",
			"WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN");
	private static final Set<String> OPERATION_ROLES = Set.of("PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> STOCK_ROLES = Set.of("WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN");
	private static final LabelReferenceData.Template OPERATION_TEMPLATE =
			new LabelReferenceData.Template("OPERATION_TASK", "OT", "工序任务标签", "OT-V1", "100×60 mm");
	private static final LabelReferenceData.Template EMPLOYEE_TEMPLATE =
			new LabelReferenceData.Template("EMPLOYEE", "EMP", "现场人员标签", "EMP-V1", "CR80");
	private static final LabelReferenceData.Template STOCK_TEMPLATE =
			new LabelReferenceData.Template("STOCK_BALANCE", "STOCK", "库存余额标签", "STOCK-V1", "100×60 mm");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final LabelEmployeeReferenceProvider employeeProvider;
	private final LabelOperationTaskReferenceProvider operationProvider;
	private final LabelStockBalanceReferenceProvider stockProvider;
	private final LabelPrintRequestRepository repository;
	private final JdbcTemplate jdbcTemplate;

	LabelingApplicationService(CurrentWorkspaceProvider workspaceProvider, LabelEmployeeReferenceProvider employeeProvider,
			LabelOperationTaskReferenceProvider operationProvider, LabelStockBalanceReferenceProvider stockProvider,
			LabelPrintRequestRepository repository, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.employeeProvider = employeeProvider;
		this.operationProvider = operationProvider; this.stockProvider = stockProvider;
		this.repository = repository; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public LabelReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = requireLabelRole(username);
		List<String> allowed = allowedObjectTypes(access);
		List<LabelReferenceData.Template> templates = new ArrayList<>();
		List<LabelReferenceData.Candidate> candidates = new ArrayList<>();
		if (allowed.contains("OPERATION_TASK")) {
			templates.add(OPERATION_TEMPLATE);
			var refs = operationProvider.listLabelTasks(access.tenantOrganizationId(), 100);
			Set<UUID> prepared = preparedIds(access, "OPERATION_TASK", refs.stream().map(item -> item.id()).toList());
			refs.forEach(item -> candidates.add(new LabelReferenceData.Candidate("OPERATION_TASK", item.id(), item.version(),
					item.taskNumber(), item.operationName(), item.orderNumber() + " · " + item.workCenterCode() + " · " + item.status(),
					"OT:" + item.taskNumber(), prepared.contains(item.id()))));
		}
		templates.add(EMPLOYEE_TEMPLATE);
		var employee = employeeProvider.resolveCurrentEmployee(access);
		boolean employeePrepared = repository.existsByTenantOrganizationIdAndWorkspaceIdAndObjectTypeAndObjectId(
				access.tenantOrganizationId(), access.workspaceId(), "EMPLOYEE", employee.id());
		candidates.add(new LabelReferenceData.Candidate("EMPLOYEE", employee.id(), employee.version(), employee.username(),
				employee.displayName(), "当前认证人员 · " + employee.username(), "EMP:" + employee.username(), employeePrepared));
		if (allowed.contains("STOCK_BALANCE")) {
			templates.add(STOCK_TEMPLATE);
			var refs = stockProvider.listLabelStocks(access.tenantOrganizationId(), 100);
			Set<UUID> prepared = preparedIds(access, "STOCK_BALANCE", refs.stream().map(item -> item.id()).toList());
			refs.forEach(item -> candidates.add(new LabelReferenceData.Candidate("STOCK_BALANCE", item.id(), item.version(),
					item.materialCode(), item.materialName(), stockDetail(item), "STOCK:" + item.id(), prepared.contains(item.id()))));
		}
		return new LabelReferenceData(allowed, templates, candidates);
	}

	@Transactional(readOnly = true)
	public LabelPrintRequestPage list(String username, String query, String objectType, int page, int size) {
		CurrentWorkspaceAccess access = requireLabelRole(username);
		String normalizedType = normalizeTypeFilter(objectType);
		if (!normalizedType.isEmpty() && !allowedObjectTypes(access).contains(normalizedType))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权查看该类标签打印请求");
		var result = repository.search(access.tenantOrganizationId(), access.workspaceId(), normalize(query), normalizedType,
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "preparedAt")));
		return new LabelPrintRequestPage(result.getContent().stream().map(LabelingApplicationService::toRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional
	public LabelPrintRequestRecord prepare(String username, LabelPrintRequestRecord.PrepareRequest request) {
		CurrentWorkspaceAccess access = requireLabelRole(username);
		requireObjectPermission(access, request.objectType());
		String requestId = currentRequestId();
		var duplicate = repository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) {
			LabelPrintRequestEntity existing = duplicate.get();
			if (!existing.getWorkspaceId().equals(access.workspaceId()) || !existing.getObjectType().equals(request.objectType())
					|| !existing.getObjectId().equals(request.objectId()) || !existing.getMode().equals(request.mode()))
				throw conflict("请求编号已用于其他标签打印准备动作");
			return toRecord(existing);
		}

		ResolvedLabel label = resolveLabel(access, request.objectType(), request.objectId());
		if (!isCode128BPayload(label.payload()))
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"对象编码包含当前 Code 128B 标签模板不支持的字符");
		if (label.objectVersion() != request.expectedObjectVersion())
			throw conflict("原业务对象已经变化，请刷新后重新生成标签");
		boolean prepared = repository.existsByTenantOrganizationIdAndWorkspaceIdAndObjectTypeAndObjectId(
				access.tenantOrganizationId(), access.workspaceId(), request.objectType(), request.objectId());
		String reason = trimToNull(request.reason());
		if ("INITIAL".equals(request.mode()) && prepared)
			throw conflict("该对象已经生成过标签，请改用补打并填写原因");
		if ("REPRINT".equals(request.mode()) && !prepared)
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "该对象尚未首次生成标签，不能直接补打");
		if ("REPRINT".equals(request.mode()) && (reason == null || reason.length() < 4))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补打原因至少填写 4 个字符");
		if ("INITIAL".equals(request.mode())) reason = null;

		LabelPrintRequestEntity entity = new LabelPrintRequestEntity(access.tenantOrganizationId(), access.workspaceId(),
				nextRequestNumber(), label, request.mode(), request.copies(), reason, access.userId(), access.username(), requestId);
		try {
			repository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("标签打印准备请求发生并发冲突，请刷新确认结果");
		}
		return toRecord(entity);
	}

	private CurrentWorkspaceAccess requireLabelRole(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		if (!LABEL_ROLES.contains(access.roleCode()))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权生成或补打现场标签");
		return access;
	}

	private static List<String> allowedObjectTypes(CurrentWorkspaceAccess access) {
		List<String> allowed = new ArrayList<>();
		if (OPERATION_ROLES.contains(access.roleCode())) allowed.add("OPERATION_TASK");
		allowed.add("EMPLOYEE");
		if (STOCK_ROLES.contains(access.roleCode())) allowed.add("STOCK_BALANCE");
		return List.copyOf(allowed);
	}

	private static void requireObjectPermission(CurrentWorkspaceAccess access, String objectType) {
		if (!allowedObjectTypes(access).contains(objectType))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权生成或补打该类标签");
	}

	private ResolvedLabel resolveLabel(CurrentWorkspaceAccess access, String objectType, UUID objectId) {
		return switch (objectType) {
			case "OPERATION_TASK" -> operationProvider.findLabelTask(access.tenantOrganizationId(), objectId)
					.map(item -> new ResolvedLabel(objectType, item.id(), item.version(), item.taskNumber(), item.operationName(),
							item.orderNumber() + " · " + item.workCenterCode() + " · " + item.status(),
							"OT:" + item.taskNumber(), OPERATION_TEMPLATE.code(), OPERATION_TEMPLATE.version()))
					.orElseThrow(() -> notFound("工序任务不存在或不在当前租户范围"));
			case "EMPLOYEE" -> {
				var item = employeeProvider.resolveCurrentEmployee(access);
				if (!item.id().equals(objectId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能生成当前认证人员的本人标签");
				yield new ResolvedLabel(objectType, item.id(), item.version(), item.username(), item.displayName(),
						"当前认证人员 · " + item.username(), "EMP:" + item.username(), EMPLOYEE_TEMPLATE.code(), EMPLOYEE_TEMPLATE.version());
			}
			case "STOCK_BALANCE" -> stockProvider.findLabelStock(access.tenantOrganizationId(), objectId)
					.map(item -> new ResolvedLabel(objectType, item.id(), item.version(), item.materialCode(), item.materialName(),
							stockDetail(item), "STOCK:" + item.id(), STOCK_TEMPLATE.code(), STOCK_TEMPLATE.version()))
					.orElseThrow(() -> notFound("库存余额不存在、数量为零或不在当前租户范围"));
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签对象类型无效");
		};
	}

	private Set<UUID> preparedIds(CurrentWorkspaceAccess access, String objectType, List<UUID> ids) {
		if (ids.isEmpty()) return Set.of();
		Set<UUID> result = new HashSet<>();
		repository.findByTenantOrganizationIdAndWorkspaceIdAndObjectTypeAndObjectIdIn(access.tenantOrganizationId(),
				access.workspaceId(), objectType, ids).forEach(item -> result.add(item.getObjectId()));
		return result;
	}

	private String nextRequestNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('labeling.print_request_number_seq')", Long.class);
		return "LPR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private static String stockDetail(LabelStockBalanceReferenceProvider.StockBalanceLabelReference item) {
		String lot = item.lotNumber() == null || item.lotNumber().isBlank() ? "无批次" : item.lotNumber();
		return item.warehouseCode() + "/" + item.locationCode() + " · 批次 " + lot + " · " + item.qualityStatus()
				+ " · 现存 " + item.onHandQuantity().stripTrailingZeros().toPlainString() + " " + item.unit();
	}

	private static LabelPrintRequestRecord toRecord(LabelPrintRequestEntity item) {
		return new LabelPrintRequestRecord(item.getId(), item.getRequestNumber(), item.getObjectType(), item.getObjectId(),
				item.getObjectVersion(), item.getObjectCode(), item.getObjectName(), item.getObjectDetail(), item.getPayload(),
				item.getTemplateCode(), item.getTemplateVersion(), item.getMode(), item.getCopies(), item.getReason(),
				item.getStatus(), item.getActorUsername(), item.getRequestId(), item.getPreparedAt());
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static boolean isCode128BPayload(String value) {
		return value != null && !value.isEmpty() && value.chars().allMatch(character -> character >= 32 && character <= 126);
	}
	private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static String normalizeTypeFilter(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!Set.of("OPERATION_TASK", "EMPLOYEE", "STOCK_BALANCE").contains(normalized))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签对象类型筛选无效");
		return normalized;
	}
	private static String currentRequestId() {
		String requestId = MDC.get("requestId");
		return requestId == null || requestId.isBlank() ? "label-print-" + UUID.randomUUID() : requestId;
	}
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
