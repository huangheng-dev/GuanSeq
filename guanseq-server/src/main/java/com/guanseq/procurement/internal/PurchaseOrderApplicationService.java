package com.guanseq.procurement.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
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
import com.guanseq.procurement.api.PurchaseOrderPage;
import com.guanseq.procurement.api.PurchaseOrderCommandService;
import com.guanseq.procurement.api.PurchaseOrderRecord;
import com.guanseq.procurement.api.PurchaseOrderReferenceData;
import com.guanseq.procurement.api.ScheduledReceiptProvider;

@Service
public class PurchaseOrderApplicationService implements ScheduledReceiptProvider, PurchaseOrderCommandService {
	private static final Set<String> APPROVAL_ROLES = Set.of("PROCUREMENT_MANAGER", "PLANNING_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final SupplierRepository supplierRepository;
	private final PurchaseOrderRepository orderRepository;
	private final PurchaseOrderEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	PurchaseOrderApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider, SupplierRepository supplierRepository,
			PurchaseOrderRepository orderRepository, PurchaseOrderEventRepository eventRepository,
			JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.masterDataProvider = masterDataProvider;
		this.supplierRepository = supplierRepository; this.orderRepository = orderRepository;
		this.eventRepository = eventRepository; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PurchaseOrderPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = orderRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new PurchaseOrderPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public PurchaseOrderRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireOrder(access, id));
	}

	@Transactional(readOnly = true)
	public PurchaseOrderReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return new PurchaseOrderReferenceData(
			supplierRepository.findByTenantOrganizationIdAndStatusOrderByCodeAsc(access.tenantOrganizationId(), "ACTIVE")
				.stream().map(item -> new PurchaseOrderReferenceData.SupplierOption(item.getId(), item.getCode(), item.getName(), item.getContactName(), item.getContactPhone())).toList(),
			masterDataProvider.listActiveMaterials(access.tenantOrganizationId()).stream()
				.filter(item -> Set.of("BUY", "OUTSOURCE").contains(item.procurementType()))
				.map(item -> new PurchaseOrderReferenceData.MaterialOption(item.id(), item.code(), item.name(), item.specification(), item.baseUnit())).toList());
	}

	@Transactional
	public PurchaseOrderRecord create(String username, PurchaseOrderRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		validateDates(request.requestedReceiptDate(), request.promisedReceiptDate());
		SupplierEntity supplier = requireSupplier(access, request.supplierId());
		PurchaseOrderEntity order = new PurchaseOrderEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextOrderNumber(), access.userId());
		order.updateHeader(supplier, request.currency(), request.taxRate(), request.requestedReceiptDate(),
				request.promisedReceiptDate(), request.buyer(), access.userId());
		order.replaceLines(buildLines(access, order, request.lines(), request.taxRate()), access.userId());
		orderRepository.saveAndFlush(order);
		audit(access, order, "CREATED", null, "DRAFT", null, Map.of("orderNumber", order.getOrderNumber(), "lineCount", order.getLines().size()));
		return toRecord(order);
	}

	@Override
	@Transactional
	public CreatedOrder createFromMrp(String username, CreateFromMrpCommand command) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access);
		var existing = orderRepository.findByTenantOrganizationIdAndSourceTypeAndSourceId(
				access.tenantOrganizationId(), "MRP", command.suggestionId());
		if (existing.isPresent()) return new CreatedOrder(existing.get().getId(), existing.get().getOrderNumber());
		if (command.quantity() == null || command.quantity().signum() <= 0)
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购建议转单数量必须大于零");
		if (command.unitPrice() == null || command.unitPrice().signum() < 0)
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购建议转单价格不能小于零");
		if (command.taxRate() == null || command.taxRate().signum() < 0 || command.taxRate().compareTo(BigDecimal.ONE) > 0)
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购建议转单税率必须在 0 到 1 之间");
		if (!Set.of("CNY", "USD", "EUR").contains(command.currency()))
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购建议转单币种不受支持");
		if (command.buyer() == null || command.buyer().isBlank())
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "采购建议转单必须指定采购员");
		validateDates(command.requestedReceiptDate(), null);
		SupplierEntity supplier = requireSupplier(access, command.supplierId());
		PurchaseOrderEntity order = new PurchaseOrderEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), nextOrderNumber(), access.userId());
		order.attachMrpSource(command.suggestionId(), command.sourceNumber());
		order.updateHeader(supplier, command.currency(), command.taxRate(), command.requestedReceiptDate(),
				null, command.buyer(), access.userId());
		order.replaceLines(buildLines(access, order, List.of(new PurchaseOrderRecord.LineInput(
				command.materialId(), command.quantity(), command.unitPrice())), command.taxRate()), access.userId());
		orderRepository.saveAndFlush(order);
		audit(access, order, "CREATED_FROM_MRP", null, "DRAFT", null,
				Map.of("sourceNumber", command.sourceNumber(), "suggestionId", command.suggestionId().toString()));
		return new CreatedOrder(order.getId(), order.getOrderNumber());
	}

	@Transactional
	public PurchaseOrderRecord update(String username, UUID id, PurchaseOrderRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		PurchaseOrderEntity order = requireOrder(access, id); requireVersion(order, request.expectedVersion());
		if (!Set.of("DRAFT", "REJECTED").contains(order.getStatus())) throw conflict("只有草稿或已驳回采购订单可以编辑");
		validateDates(request.requestedReceiptDate(), request.promisedReceiptDate());
		SupplierEntity supplier = requireSupplier(access, request.supplierId()); String from = order.getStatus();
		order.updateHeader(supplier, request.currency(), request.taxRate(), request.requestedReceiptDate(), request.promisedReceiptDate(), request.buyer(), access.userId());
		order.replaceLines(buildLines(access, order, request.lines(), request.taxRate()), access.userId());
		if ("REJECTED".equals(from)) order.transition("DRAFT", null, access.userId());
		orderRepository.saveAndFlush(order); audit(access, order, "UPDATED", from, order.getStatus(), null, Map.of("lineCount", order.getLines().size()));
		return toRecord(order);
	}

	@Transactional
	public PurchaseOrderRecord action(String username, UUID id, PurchaseOrderRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); PurchaseOrderEntity order = requireOrder(access, id);
		requireVersion(order, request.expectedVersion()); String from = order.getStatus();
		String target = switch (request.action()) {
			case "SUBMIT" -> { requireStatus(order, "DRAFT", "只有草稿采购订单可以提交审核"); yield "PENDING_APPROVAL"; }
			case "APPROVE" -> { requireRole(access); requireStatus(order, "PENDING_APPROVAL", "只有待审核采购订单可以通过审核"); yield "APPROVED"; }
			case "REJECT" -> {
				requireRole(access); requireStatus(order, "PENDING_APPROVAL", "只有待审核采购订单可以驳回");
				if (request.comment() == null || request.comment().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回必须填写原因");
				yield "REJECTED";
			}
			case "RELEASE" -> {
				requireRole(access); requireStatus(order, "APPROVED", "只有已审核采购订单可以下达");
				if (order.getPromisedReceiptDate() == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "下达前必须确认供应商承诺到货日期");
				yield "RELEASED";
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的采购订单动作");
		};
		order.transition(target, request.comment(), access.userId()); orderRepository.saveAndFlush(order);
		audit(access, order, request.action(), from, target, request.comment(), Map.of("orderNumber", order.getOrderNumber()));
		return toRecord(order);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScheduledReceipt> listReleasedPurchaseReceipts(UUID tenantId, Collection<UUID> materialIds, LocalDate horizonEnd) {
		if (materialIds.isEmpty()) return List.of();
		return orderRepository.findReleasedReceipts(tenantId, materialIds, horizonEnd).stream()
				.flatMap(order -> order.getLines().stream().filter(line -> materialIds.contains(line.getMaterialId()))
						.filter(line -> line.getOutstandingQuantity().signum() > 0)
						.map(line -> new ScheduledReceipt(order.getId(), order.getOrderNumber(), line.getId(), order.getSupplierName(),
								line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getUnit(),
								line.getOutstandingQuantity(), order.getPromisedReceiptDate())))
				.sorted(java.util.Comparator.comparing(ScheduledReceipt::expectedReceiptDate).thenComparing(ScheduledReceipt::orderNumber)).toList();
	}

	private List<PurchaseOrderLineEntity> buildLines(CurrentWorkspaceAccess access, PurchaseOrderEntity order,
			List<PurchaseOrderRecord.LineInput> inputs, BigDecimal taxRate) {
		Set<UUID> materialIds = new HashSet<>();
		return java.util.stream.IntStream.range(0, inputs.size()).mapToObj(index -> {
			var input = inputs.get(index); if (!materialIds.add(input.materialId())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "同一物料不能在采购订单中重复");
			MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), input.materialId());
			if (!Set.of("BUY", "OUTSOURCE").contains(material.procurementType())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "自制物料不能直接建立采购订单");
			return new PurchaseOrderLineEntity(order, index + 1, material.id(), material.code(), material.name(), material.specification(), material.baseUnit(), input.orderedQuantity(), input.unitPrice(), taxRate);
		}).toList();
	}

	private PurchaseOrderRecord toRecord(PurchaseOrderEntity order) {
		return new PurchaseOrderRecord(order.getId(), order.getOrderNumber(), order.getSupplierId(), order.getSupplierCode(),
				order.getSupplierName(), order.getCurrency(), order.getTaxRate(), order.getRequestedReceiptDate(),
				order.getPromisedReceiptDate(), order.getBuyer(), order.getStatus(), order.getTotalNetAmount(),
				order.getTotalTaxAmount(), order.getTotalGrossAmount(), order.getRejectionReason(), order.getSourceType(),
				order.getSourceId(), order.getSourceNumber(), order.getVersion(),
				order.getUpdatedAt(), order.getLines().stream().sorted(java.util.Comparator.comparingInt(PurchaseOrderLineEntity::getLineNumber))
						.map(line -> new PurchaseOrderRecord.Line(line.getId(), line.getLineNumber(), line.getMaterialId(),
								line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
								line.getOrderedQuantity(), line.getReceivedQuantity(), line.getOutstandingQuantity(), line.getUnitPrice(),
								line.getNetAmount(), line.getTaxAmount(), line.getGrossAmount())).toList());
	}

	private SupplierEntity requireSupplier(CurrentWorkspaceAccess access, UUID id) { return supplierRepository.findByIdAndTenantOrganizationIdAndStatus(id, access.tenantOrganizationId(), "ACTIVE").orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "供应商不存在、未启用或不在当前租户范围")); }
	private PurchaseOrderEntity requireOrder(CurrentWorkspaceAccess access, UUID id) { return orderRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购订单不存在或不在当前租户范围")); }
	private void audit(CurrentWorkspaceAccess access, PurchaseOrderEntity order, String action, String from, String to, String comment, Map<String, Object> details) { eventRepository.save(new PurchaseOrderEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(), order.getId(), action, from, to, MDC.get("requestId"), comment, details)); }
	private String nextOrderNumber() { Long sequence = jdbcTemplate.queryForObject("select nextval('procurement.purchase_order_number_seq')", Long.class); return "PO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence); }
	private static void validateDates(LocalDate requested, LocalDate promised) { if (requested.isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "要求到货日期不能早于今天"); if (promised != null && promised.isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "承诺到货日期不能早于今天"); }
	private static void requireVersion(PurchaseOrderEntity order, long expected) { if (order.getVersion() != expected) throw conflict("采购订单已经被其他用户修改，请刷新后重试"); }
	private static void requireStatus(PurchaseOrderEntity order, String expected, String message) { if (!expected.equals(order.getStatus())) throw conflict(message); }
	private static void requireRole(CurrentWorkspaceAccess access) { if (!APPROVAL_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权审核或下达采购订单"); }
	private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase(); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
