package com.guanseq.quality.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.quality.api.FinalInspectionPage;
import com.guanseq.quality.api.FinalInspectionProvider;
import com.guanseq.quality.api.FinalInspectionRecord;
import com.guanseq.quality.api.IncomingInspectionPage;
import com.guanseq.quality.api.IncomingInspectionProvider;
import com.guanseq.quality.api.IncomingInspectionRecord;

@Service
public class FinalInspectionApplicationService implements FinalInspectionProvider, IncomingInspectionProvider {
	private static final Set<String> QUALITY_ROLES = Set.of("QUALITY_INSPECTOR", "QUALITY_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final FinalInspectionRepository inspectionRepository;
	private final FinalInspectionEventRepository eventRepository;
	private final NonconformanceApplicationService nonconformanceService;
	private final ApplicationEventPublisher eventPublisher;
	private final JdbcTemplate jdbcTemplate;

	FinalInspectionApplicationService(CurrentWorkspaceProvider workspaceProvider,
			FinalInspectionRepository inspectionRepository, FinalInspectionEventRepository eventRepository,
			NonconformanceApplicationService nonconformanceService,
			ApplicationEventPublisher eventPublisher, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.inspectionRepository = inspectionRepository;
		this.eventRepository = eventRepository; this.nonconformanceService = nonconformanceService;
		this.eventPublisher = eventPublisher; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public FinalInspectionPage listFinal(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = inspectionRepository.search(access.tenantOrganizationId(), access.workspaceId(), "FINAL", normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new FinalInspectionPage(result.getContent().stream().map(this::toFinalRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public FinalInspectionRecord getFinal(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		FinalInspectionEntity inspection = require(access, id);
		if (!"FINAL".equals(inspection.getInspectionType())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "完工检验任务不存在或不在当前租户范围");
		return toFinalRecord(inspection);
	}

	@Transactional
	public FinalInspectionRecord completeFinal(String username, UUID id, FinalInspectionRecord.CompleteRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireQualityRole(access);
		FinalInspectionEntity inspection = require(access, id);
		if (!"FINAL".equals(inspection.getInspectionType())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "完工检验任务不存在或不在当前租户范围");
		completeInspection(access, inspection, request.acceptedQuantity(), request.rejectedQuantity(), request.inspector(),
				request.defectDescription(), request.conclusion(), request.expectedVersion());
		return toFinalRecord(inspection);
	}

	@Transactional(readOnly = true)
	public IncomingInspectionPage listIncoming(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = inspectionRepository.search(access.tenantOrganizationId(), access.workspaceId(), "INCOMING", normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new IncomingInspectionPage(result.getContent().stream().map(this::toIncomingRecord).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public IncomingInspectionRecord getIncoming(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		FinalInspectionEntity inspection = require(access, id);
		if (!"INCOMING".equals(inspection.getInspectionType())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "来料检验任务不存在或不在当前租户范围");
		return toIncomingRecord(inspection);
	}

	@Transactional
	public IncomingInspectionRecord completeIncoming(String username, UUID id, IncomingInspectionRecord.CompleteRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireQualityRole(access);
		FinalInspectionEntity inspection = require(access, id);
		if (!"INCOMING".equals(inspection.getInspectionType())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "来料检验任务不存在或不在当前租户范围");
		String requestId = completeInspection(access, inspection, request.acceptedQuantity(), request.rejectedQuantity(),
				request.inspector(), request.defectDescription(), request.conclusion(), request.expectedVersion());
		eventPublisher.publishEvent(new CompletedEvent(inspection.getTenantOrganizationId(), access.userId(), inspection.getId(),
				inspection.getSourceId(), inspection.getOrderId(), inspection.getMaterialId(),
				inspection.getAcceptedQuantity(), inspection.getRejectedQuantity(), requestId));
		return toIncomingRecord(inspection);
	}

	@Override
	@Transactional
	public FinalInspectionProvider.Inspection create(FinalInspectionProvider.CreateCommand command) {
		var sourceDuplicate = inspectionRepository.findByTenantOrganizationIdAndSourceTypeAndSourceId(
				command.tenantOrganizationId(), "PRODUCTION_REPORT", command.sourceId());
		if (sourceDuplicate.isPresent()) return toInspection(sourceDuplicate.get());
		var requestDuplicate = inspectionRepository.findByTenantOrganizationIdAndRequestId(
				command.tenantOrganizationId(), command.requestId());
		if (requestDuplicate.isPresent()) return toInspection(requestDuplicate.get());
		FinalInspectionEntity entity = new FinalInspectionEntity(command.tenantOrganizationId(),
				command.owningOrganizationId(), command.workspaceId(), nextNumber("FQI"), command.actorUserId(),
				command.sourceId(), command.sourceNumber(), command.orderId(), command.orderNumber(), command.materialId(),
				command.materialCode(), command.materialName(), command.materialSpecification(), command.unit(),
				command.inspectionQuantity(), command.requestId());
		inspectionRepository.saveAndFlush(entity);
		eventRepository.save(new FinalInspectionEventEntity(command.tenantOrganizationId(), command.workspaceId(),
				command.actorUserId(), entity.getId(), "CREATED", null, "PENDING", command.requestId(),
				Map.of("sourceNumber", command.sourceNumber(), "orderNumber", command.orderNumber())));
		return toInspection(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<FinalInspectionProvider.Inspection> find(UUID tenantOrganizationId, UUID inspectionId) {
		return inspectionRepository.findByIdAndTenantOrganizationId(inspectionId, tenantOrganizationId).map(this::toInspection);
	}

	@Override
	@Transactional
	public IncomingInspectionProvider.Inspection create(IncomingInspectionProvider.CreateCommand command) {
		var sourceDuplicate = inspectionRepository.findByTenantOrganizationIdAndSourceTypeAndSourceId(
				command.tenantOrganizationId(), "PURCHASE_RECEIPT_LINE", command.receiptLineId());
		if (sourceDuplicate.isPresent()) return toIncomingInspection(sourceDuplicate.get());
		var requestDuplicate = inspectionRepository.findByTenantOrganizationIdAndRequestId(
				command.tenantOrganizationId(), command.requestId());
		if (requestDuplicate.isPresent()) return toIncomingInspection(requestDuplicate.get());
		FinalInspectionEntity entity = new FinalInspectionEntity(command.tenantOrganizationId(),
				command.owningOrganizationId(), command.workspaceId(), nextNumber("IQC"), command.actorUserId(),
				command.receiptLineId(), command.receiptNumber(), command.purchaseOrderId(), command.purchaseOrderNumber(),
				command.supplierId(), command.supplierCode(), command.supplierName(), command.materialId(),
				command.materialCode(), command.materialName(), command.materialSpecification(), command.unit(),
				command.inspectionQuantity(), command.requestId());
		inspectionRepository.saveAndFlush(entity);
		eventRepository.save(new FinalInspectionEventEntity(command.tenantOrganizationId(), command.workspaceId(),
				command.actorUserId(), entity.getId(), "CREATED", null, "PENDING", command.requestId(),
				Map.of("sourceNumber", command.receiptNumber(), "orderNumber", command.purchaseOrderNumber(),
						"supplierCode", command.supplierCode())));
		return toIncomingInspection(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<IncomingInspectionProvider.Inspection> findIncoming(UUID tenantOrganizationId, UUID inspectionId) {
		return inspectionRepository.findByIdAndTenantOrganizationId(inspectionId, tenantOrganizationId).map(this::toIncomingInspection);
	}

	private String completeInspection(CurrentWorkspaceAccess access, FinalInspectionEntity inspection, BigDecimal accepted,
			BigDecimal rejected, String inspector, String defect, String conclusion, long expectedVersion) {
		String requestId = requestId("quality-decision-");
		var duplicate = inspectionRepository.findByTenantOrganizationIdAndDecisionRequestId(access.tenantOrganizationId(), requestId);
		if (duplicate.isPresent() && duplicate.get().getId().equals(inspection.getId())) return requestId;
		if (duplicate.isPresent()) throw conflict("该检验结论请求编号已用于其他任务");
		if (inspection.getVersion() != expectedVersion) throw conflict("检验任务已被其他用户更新，请刷新后重试");
		if (!"PENDING".equals(inspection.getStatus())) throw conflict("只有待检任务可以提交检验结论");
		if (accepted.add(rejected).compareTo(inspection.getInspectionQuantity()) != 0)
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "合格数量与不合格数量之和必须等于送检数量");
		if (rejected.signum() > 0 && (defect == null || defect.isBlank()))
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "存在不合格数量时必须填写缺陷说明");
		inspection.complete(accepted, rejected, inspector, defect, conclusion, requestId, access.userId());
		inspectionRepository.saveAndFlush(inspection);
		eventRepository.save(new FinalInspectionEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), inspection.getId(), "COMPLETED", "PENDING", "COMPLETED", requestId,
				Map.of("result", inspection.getResult(), "acceptedQuantity", inspection.getAcceptedQuantity(),
						"rejectedQuantity", inspection.getRejectedQuantity())));
		nonconformanceService.createFromInspection(access, inspection, requestId);
		return requestId;
	}

	private FinalInspectionRecord toFinalRecord(FinalInspectionEntity item) {
		return new FinalInspectionRecord(item.getId(), item.getInspectionNumber(), item.getSourceType(), item.getSourceId(),
				item.getSourceNumber(), item.getOrderId(), item.getOrderNumber(), item.getMaterialId(), item.getMaterialCode(),
				item.getMaterialName(), item.getMaterialSpecification(), item.getUnit(), item.getInspectionQuantity(),
				item.getStatus(), item.getResult(), item.getAcceptedQuantity(), item.getRejectedQuantity(), item.getInspector(),
				item.getDefectDescription(), item.getConclusion(), item.getVersion(), item.getCreatedAt(), item.getCompletedAt());
	}

	private IncomingInspectionRecord toIncomingRecord(FinalInspectionEntity item) {
		return new IncomingInspectionRecord(item.getId(), item.getInspectionNumber(), item.getSourceId(), item.getSourceNumber(),
				item.getOrderId(), item.getOrderNumber(), item.getSupplierId(), item.getSupplierCode(), item.getSupplierName(),
				item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(), item.getMaterialSpecification(),
				item.getUnit(), item.getInspectionQuantity(), item.getStatus(), item.getResult(), item.getAcceptedQuantity(),
				item.getRejectedQuantity(), item.getInspector(), item.getDefectDescription(), item.getConclusion(),
				item.getVersion(), item.getCreatedAt(), item.getCompletedAt());
	}

	private FinalInspectionProvider.Inspection toInspection(FinalInspectionEntity item) {
		return new FinalInspectionProvider.Inspection(item.getId(), item.getInspectionNumber(), item.getStatus(),
				item.getResult(), item.getAcceptedQuantity(), item.getRejectedQuantity());
	}
	private IncomingInspectionProvider.Inspection toIncomingInspection(FinalInspectionEntity item) {
		return new IncomingInspectionProvider.Inspection(item.getId(), item.getInspectionNumber(), item.getStatus(),
				item.getResult(), item.getAcceptedQuantity(), item.getRejectedQuantity());
	}
	private FinalInspectionEntity require(CurrentWorkspaceAccess access, UUID id) {
		return inspectionRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "检验任务不存在或不在当前租户范围"));
	}
	private String nextNumber(String prefix) {
		Long value = jdbcTemplate.queryForObject("select nextval('quality.inspection_number_seq')", Long.class);
		return prefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}
	private static String requestId(String prefix) { String value = MDC.get("requestId"); return value == null || value.isBlank() ? prefix + UUID.randomUUID() : value; }
	private static void requireQualityRole(CurrentWorkspaceAccess access) { if (!QUALITY_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权提交检验结论"); }
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
