package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import com.guanseq.production.api.ProductionWorkReportPage;
import com.guanseq.production.api.ProductionWorkReportRecord;
import com.guanseq.quality.api.FinalInspectionProvider;
import com.guanseq.quality.api.FinalInspectionProvider.Inspection;
import com.guanseq.warehouse.api.FinishedGoodsReceiptService;

@Service
public class ProductionWorkReportApplicationService {
	private static final Set<String> REPORT_ROLES = Set.of("PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final ProductionOrderRepository orderRepository;
	private final ProductionOrderEventRepository orderEventRepository;
	private final ProductionWorkReportRepository reportRepository;
	private final FinalInspectionProvider inspectionProvider;
	private final FinishedGoodsReceiptService receiptService;
	private final OperationTaskRepository operationTaskRepository;
	private final JdbcTemplate jdbcTemplate;

	ProductionWorkReportApplicationService(CurrentWorkspaceProvider workspaceProvider, ProductionOrderRepository orderRepository,
			ProductionOrderEventRepository orderEventRepository, ProductionWorkReportRepository reportRepository,
			FinalInspectionProvider inspectionProvider, FinishedGoodsReceiptService receiptService,
			OperationTaskRepository operationTaskRepository, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.orderRepository = orderRepository;
		this.orderEventRepository = orderEventRepository; this.reportRepository = reportRepository;
		this.inspectionProvider = inspectionProvider; this.receiptService = receiptService;
		this.operationTaskRepository = operationTaskRepository; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public ProductionWorkReportPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = reportRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new ProductionWorkReportPage(result.getContent().stream().map(item -> toRecord(access, item)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ProductionWorkReportRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); return toRecord(access, requireReport(access, id));
	}

	@Transactional
	public ProductionWorkReportRecord create(String username, ProductionWorkReportRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireReportRole(access);
		String requestId = requestId("production-report-");
		String source = normalizeSource(request.source());
		var duplicate = reportRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) return toRecord(access, duplicate.get());
		ProductionOrderEntity order = requireOrder(access, request.orderId());
		if (!"IN_PROGRESS".equals(order.getStatus())) throw conflict("只有执行中的生产订单可以报工");
		if (!operationTaskRepository.existsByTenantOrganizationIdAndOrderId(access.tenantOrganizationId(), order.getId()))
			throw conflict("生产订单缺少工序执行快照，请联系计划员重新下达或补录工艺路线");
		if (operationTaskRepository.existsByTenantOrganizationIdAndOrderIdAndStatusNot(access.tenantOrganizationId(), order.getId(), "COMPLETED"))
			throw conflict("仍有工序未完成，不能提交完工报工");
		UUID operationTaskId = null;
		String operator;
		if ("MOBILE_SCAN".equals(source)) {
			String badge = requireText(request.operatorBadge(), "移动扫码报工必须扫描当前操作人员标签");
			if (!badge.equalsIgnoreCase(access.username())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"人员标签与当前登录账号不一致，不能代替他人提交生产报工");
			OperationTaskEntity task = request.operationTaskId() == null ? null
					: operationTaskRepository.findByIdAndTenantOrganizationId(request.operationTaskId(), access.tenantOrganizationId()).orElse(null);
			if (task == null || !task.getOrderId().equals(order.getId())) throw conflict("扫码工序不属于当前生产订单");
			if (!"COMPLETED".equals(task.getStatus())) throw conflict("扫码工序尚未完工，不能提交生产报工");
			OperationTaskEntity finalTask = operationTaskRepository
					.findFirstByTenantOrganizationIdAndOrderIdOrderBySequenceNumberDesc(access.tenantOrganizationId(), order.getId())
					.orElseThrow(() -> conflict("生产订单缺少最后一道工序快照"));
			if (!finalTask.getId().equals(task.getId())) throw conflict("生产扫码报工必须扫描该订单最后一道已完工工序");
			operationTaskId = task.getId(); operator = access.username();
		} else {
			operator = requireText(request.operatorName(), "生产报工必须填写操作人");
		}
		if (order.getVersion() != request.expectedOrderVersion()) throw conflict("生产订单已被其他用户更新，请刷新后重试");
		try { order.reserveReport(request.quantity(), access.userId()); }
		catch (IllegalStateException exception) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
				"报工数量超过当前可报数量，请核对在检与已完工数量", exception); }
		ProductionWorkReportEntity report = new ProductionWorkReportEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextNumber(), order, request.quantity(),
				request.shiftName(), operator, request.note(), requestId, access.userId(), operationTaskId, source);
		reportRepository.saveAndFlush(report);
		Inspection inspection = inspectionProvider.create(new FinalInspectionProvider.CreateCommand(
				access.tenantOrganizationId(), access.operatingOrganizationId(), access.workspaceId(), access.userId(),
				report.getId(), report.getReportNumber(), order.getId(), order.getOrderNumber(), order.getMaterialId(),
				order.getMaterialCode(), order.getMaterialName(), order.getMaterialSpecification(), order.getUnit(),
				request.quantity(), requestId));
		report.attachInspection(inspection.id()); reportRepository.saveAndFlush(report); orderRepository.saveAndFlush(order);
		Map<String, Object> eventDetails = new java.util.LinkedHashMap<>();
		eventDetails.put("reportNumber", report.getReportNumber()); eventDetails.put("quantity", request.quantity());
		eventDetails.put("inspectionNumber", inspection.inspectionNumber()); eventDetails.put("source", source);
		if (operationTaskId != null) eventDetails.put("operationTaskId", operationTaskId.toString());
		orderEventRepository.save(new ProductionOrderEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), order.getId(), "REPORTED", "IN_PROGRESS", "IN_PROGRESS", requestId, request.note(),
				eventDetails));
		return toRecord(access, report);
	}

	@Transactional
	public ProductionWorkReportRecord settle(String username, UUID id, ProductionWorkReportRecord.SettleRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireReportRole(access);
		String requestId = requestId("production-settle-");
		var duplicate = reportRepository.findByTenantOrganizationIdAndSettlementRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent()) return toRecord(access, duplicate.get());
		ProductionWorkReportEntity report = requireReport(access, id);
		if (report.getVersion() != request.expectedVersion()) throw conflict("报工记录已被其他用户更新，请刷新后重试");
		if (!"PENDING_INSPECTION".equals(report.getStatus())) throw conflict("该报工已经结算，不能重复入库或关闭");
		Inspection inspection = requireInspection(access, report);
		if (!"COMPLETED".equals(inspection.status())) throw conflict("完工检验尚未完成，不能结算报工");
		BigDecimal accepted = inspection.acceptedQuantity(); BigDecimal rejected = inspection.rejectedQuantity();
		UUID balanceId = null; UUID movementId = null; String lot = null;
		if (accepted.signum() > 0) {
			if (request.warehouseId() == null || request.locationId() == null || request.lotNumber() == null || request.lotNumber().isBlank())
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "存在合格数量时必须选择成品仓库、库位并填写批次");
			var receipt = receiptService.receive(new FinishedGoodsReceiptService.Command(access.tenantOrganizationId(),
					access.operatingOrganizationId(), access.workspaceId(), access.userId(), request.warehouseId(),
					request.locationId(), report.getMaterialId(), report.getMaterialCode(), report.getMaterialName(),
					report.getMaterialSpecification(), report.getUnit(), accepted, request.lotNumber().trim(), report.getId(),
					report.getReportNumber(), requestId));
			balanceId = receipt.balanceId(); movementId = receipt.movementId(); lot = receipt.lotNumber();
		}
		ProductionOrderEntity order = requireOrder(access, report.getOrderId()); String from = order.getStatus();
		order.settleReport(report.getReportedQuantity(), accepted, access.userId());
		report.settle(accepted, rejected, balanceId, movementId, lot, requestId, access.userId());
		orderRepository.saveAndFlush(order); reportRepository.saveAndFlush(report);
		orderEventRepository.save(new ProductionOrderEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), order.getId(), "REPORT_SETTLED", from, order.getStatus(), requestId, null,
				Map.of("reportNumber", report.getReportNumber(), "acceptedQuantity", accepted,
						"rejectedQuantity", rejected, "qualityResult", inspection.result())));
		return toRecord(access, report);
	}

	private ProductionWorkReportRecord toRecord(CurrentWorkspaceAccess access, ProductionWorkReportEntity item) {
		Inspection inspection = requireInspection(access, item);
		String effectiveStatus = item.getStatus();
		if ("PENDING_INSPECTION".equals(effectiveStatus) && "COMPLETED".equals(inspection.status()))
			effectiveStatus = inspection.acceptedQuantity().signum() > 0 ? "READY_FOR_RECEIPT" : "READY_TO_CLOSE";
		var receipt = item.getReceiptBalanceId() == null ? java.util.Optional.<FinishedGoodsReceiptService.Receipt>empty()
				: receiptService.findBySource(access.tenantOrganizationId(), item.getId());
		String warehouse = receipt.map(FinishedGoodsReceiptService.Receipt::warehouseName).orElse(null);
		String location = receipt.map(FinishedGoodsReceiptService.Receipt::locationName).orElse(null);
		String operationTaskNumber = item.getOperationTaskId() == null ? null
				: operationTaskRepository.findByIdAndTenantOrganizationId(item.getOperationTaskId(), access.tenantOrganizationId())
						.map(OperationTaskEntity::getTaskNumber).orElse(null);
		return new ProductionWorkReportRecord(item.getId(), item.getReportNumber(), item.getOrderId(), item.getOrderNumber(),
				item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(), item.getMaterialSpecification(), item.getUnit(),
				item.getWorkshop(), item.getShiftName(), item.getOperatorName(), item.getReportedQuantity(), item.getNote(),
				item.getInspectionId(), inspection.inspectionNumber(), inspection.status(), inspection.result(),
				inspection.acceptedQuantity(), inspection.rejectedQuantity(), item.getReceiptBalanceId(), item.getReceiptMovementId(),
				warehouse, location, item.getLotNumber(), effectiveStatus, item.getOperationTaskId(), operationTaskNumber,
				item.getSource(), item.getVersion(), item.getCreatedAt(), item.getSettledAt());
	}

	private Inspection requireInspection(CurrentWorkspaceAccess access, ProductionWorkReportEntity report) {
		return inspectionProvider.find(access.tenantOrganizationId(), report.getInspectionId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "报工关联的完工检验任务缺失，请联系管理员恢复"));
	}
	private ProductionWorkReportEntity requireReport(CurrentWorkspaceAccess access, UUID id) { return reportRepository.findByIdAndTenantOrganizationId(id,
			access.tenantOrganizationId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产报工不存在或不在当前租户范围")); }
	private ProductionOrderEntity requireOrder(CurrentWorkspaceAccess access, UUID id) { return orderRepository.findByIdAndTenantOrganizationId(id,
			access.tenantOrganizationId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产订单不存在或不在当前租户范围")); }
	private String nextNumber() { Long value = jdbcTemplate.queryForObject("select nextval('production.work_report_number_seq')", Long.class);
		return "RPT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value); }
	private static String requestId(String prefix) { String value = MDC.get("requestId"); return value == null || value.isBlank() ? prefix + UUID.randomUUID() : value; }
	private static void requireReportRole(CurrentWorkspaceAccess access) { if (!REPORT_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权提交或结算生产报工"); }
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static String normalizeSource(String value) { return value == null || value.isBlank() ? "DESKTOP_FORM" : value.trim(); }
	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		return value.trim();
	}
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
