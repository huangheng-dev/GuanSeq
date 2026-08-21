package com.guanseq.sales.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.guanseq.masterdata.api.MasterDataReferenceProvider;
import com.guanseq.masterdata.api.MasterDataReferenceProvider.CustomerReference;
import com.guanseq.masterdata.api.MasterDataReferenceProvider.MaterialReference;
import com.guanseq.sales.api.SalesOrderPage;
import com.guanseq.sales.api.SalesOrderRecord;
import com.guanseq.sales.api.SalesOrderReleasedEvent;
import com.guanseq.sales.api.SalesOrderReferenceData;

@Service
public class SalesOrderApplicationService {

	private static final Set<String> APPROVAL_ROLES = Set.of("SALES_MANAGER", "PLANNING_MANAGER", "ADMIN");
	private static final Set<String> RELEASE_ROLES = Set.of("SALES_MANAGER", "PLANNING_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final SalesOrderRepository orderRepository;
	private final SalesOrderChangeEventRepository changeEventRepository;
	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher eventPublisher;

	SalesOrderApplicationService(
			CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider,
			SalesOrderRepository orderRepository,
			SalesOrderChangeEventRepository changeEventRepository,
			JdbcTemplate jdbcTemplate,
			ApplicationEventPublisher eventPublisher) {
		this.workspaceProvider = workspaceProvider;
		this.masterDataProvider = masterDataProvider;
		this.orderRepository = orderRepository;
		this.changeEventRepository = changeEventRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public SalesOrderPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = orderRepository.search(
				access.tenantOrganizationId(),
				normalize(query),
				normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new SalesOrderPage(result.getContent().stream().map(this::toRecord).toList(), result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public SalesOrderRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireOrder(access, id));
	}

	@Transactional(readOnly = true)
	public SalesOrderReferenceData referenceData(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return new SalesOrderReferenceData(
				masterDataProvider.listActiveCustomers(access.tenantOrganizationId()).stream()
						.map(item -> new SalesOrderReferenceData.CustomerOption(item.id(), item.code(), item.name(), item.creditLevel())).toList(),
				masterDataProvider.listActiveMaterials(access.tenantOrganizationId()).stream()
						.map(item -> new SalesOrderReferenceData.MaterialOption(item.id(), item.code(), item.name(), item.specification(), item.baseUnit())).toList());
	}

	@Transactional
	public SalesOrderRecord create(String username, SalesOrderRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		validateOrderDates(request.requestedDeliveryDate(), request.promisedDeliveryDate());
		CustomerReference customer = masterDataProvider.requireActiveCustomer(access.tenantOrganizationId(), request.customerId());
		SalesOrderEntity order = new SalesOrderEntity(
				access.tenantOrganizationId(),
				access.operatingOrganizationId(),
				nextOrderNumber(),
				access.userId());
		order.updateHeader(customer.id(), customer.code(), customer.name(), request.currency(), request.taxRate(), request.requestedDeliveryDate(), request.promisedDeliveryDate(), request.owner(), access.userId());
		order.replaceLines(buildLines(access, order, request.lines(), request.taxRate()), access.userId());
		orderRepository.saveAndFlush(order);
		audit(access, order, "CREATED", null, "DRAFT", null, Map.of("orderNumber", order.getOrderNumber(), "lineCount", order.getLines().size()));
		return toRecord(order);
	}

	@Transactional
	public SalesOrderRecord update(String username, UUID id, SalesOrderRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		SalesOrderEntity order = requireOrder(access, id);
		requireVersion(order, request.expectedVersion());
		if (!Set.of("DRAFT", "REJECTED").contains(order.getStatus())) {
			throw conflict("只有草稿或已驳回订单可以编辑");
		}
		validateOrderDates(request.requestedDeliveryDate(), request.promisedDeliveryDate());
		CustomerReference customer = masterDataProvider.requireActiveCustomer(access.tenantOrganizationId(), request.customerId());
		String fromStatus = order.getStatus();
		order.updateHeader(customer.id(), customer.code(), customer.name(), request.currency(), request.taxRate(), request.requestedDeliveryDate(), request.promisedDeliveryDate(), request.owner(), access.userId());
		order.replaceLines(buildLines(access, order, request.lines(), request.taxRate()), access.userId());
		if ("REJECTED".equals(fromStatus)) order.transition("DRAFT", null, access.userId());
		orderRepository.saveAndFlush(order);
		audit(access, order, "UPDATED", fromStatus, order.getStatus(), null, Map.of("lineCount", order.getLines().size()));
		return toRecord(order);
	}

	@Transactional
	public SalesOrderRecord action(String username, UUID id, SalesOrderRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		SalesOrderEntity order = requireOrder(access, id);
		requireVersion(order, request.expectedVersion());
		String from = order.getStatus();
		String target = switch (request.action()) {
			case "SUBMIT" -> {
				requireStatus(order, "DRAFT", "只有草稿订单可以提交审核");
				if (order.getPromisedDeliveryDate() == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "提交审核前必须填写承诺交期");
				yield "PENDING_APPROVAL";
			}
			case "APPROVE" -> {
				requireRole(access, APPROVAL_ROLES, "当前角色无权审核销售订单");
				requireStatus(order, "PENDING_APPROVAL", "只有待审核订单可以通过审核");
				yield "APPROVED";
			}
			case "REJECT" -> {
				requireRole(access, APPROVAL_ROLES, "当前角色无权驳回销售订单");
				requireStatus(order, "PENDING_APPROVAL", "只有待审核订单可以驳回");
				if (request.comment() == null || request.comment().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回必须填写原因");
				yield "REJECTED";
			}
			case "RELEASE" -> {
				requireRole(access, RELEASE_ROLES, "当前角色无权下达销售订单");
				requireStatus(order, "APPROVED", "只有已审核订单可以下达");
				yield "RELEASED";
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的订单动作");
		};
		order.transition(target, request.comment(), access.userId());
		orderRepository.saveAndFlush(order);
		audit(access, order, request.action(), from, target, request.comment(), Map.of("orderNumber", order.getOrderNumber()));
		if ("RELEASED".equals(target)) publishReleased(access, order);
		return toRecord(order);
	}

	private void publishReleased(CurrentWorkspaceAccess access, SalesOrderEntity order) {
		eventPublisher.publishEvent(new SalesOrderReleasedEvent(
				access.tenantOrganizationId(), order.getOwningOrganizationId(), access.workspaceId(), access.userId(),
				order.getId(), order.getOrderNumber(), order.getCustomerName(), order.getPromisedDeliveryDate(),
				order.getOwner(), Instant.now(), order.getLines().stream().map(line -> new SalesOrderReleasedEvent.Line(
						line.getId(), line.getLineNumber(), line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(),
						line.getMaterialSpecification(), line.getUnit(), line.getQuantity())).toList()));
	}

	private List<SalesOrderLineEntity> buildLines(CurrentWorkspaceAccess access, SalesOrderEntity order, List<SalesOrderRecord.LineInput> inputs, BigDecimal taxRate) {
		Set<UUID> materials = new HashSet<>();
		return java.util.stream.IntStream.range(0, inputs.size()).mapToObj(index -> {
			SalesOrderRecord.LineInput input = inputs.get(index);
			if (!materials.add(input.materialId())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "同一物料不能在订单中重复，请合并数量");
			MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), input.materialId());
			return new SalesOrderLineEntity(order, index + 1, material.id(), material.code(), material.name(), material.specification(), material.baseUnit(), input.quantity(), input.unitPrice(), taxRate);
		}).toList();
	}

	private SalesOrderEntity requireOrder(CurrentWorkspaceAccess access, UUID id) {
		return orderRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在或不在当前租户范围"));
	}

	private void audit(CurrentWorkspaceAccess access, SalesOrderEntity order, String action, String from, String to, String comment, Map<String, Object> details) {
		changeEventRepository.save(new SalesOrderChangeEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(), order.getId(), action, from, to, MDC.get("requestId"), comment, details));
	}

	private SalesOrderRecord toRecord(SalesOrderEntity order) {
		return new SalesOrderRecord(
				order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getCustomerCode(), order.getCustomerName(),
				order.getCurrency(), order.getTaxRate(), order.getRequestedDeliveryDate(), order.getPromisedDeliveryDate(),
				order.getOwner(), order.getStatus(), order.getTotalNetAmount(), order.getTotalTaxAmount(), order.getTotalGrossAmount(),
				order.getRejectionReason(), order.getVersion(), order.getUpdatedAt(),
				order.getLines().stream().sorted(java.util.Comparator.comparingInt(SalesOrderLineEntity::getLineNumber)).map(line -> new SalesOrderRecord.Line(
						line.getId(), line.getLineNumber(), line.getMaterialId(), line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(), line.getQuantity(), line.getUnitPrice(), line.getNetAmount(), line.getTaxAmount(), line.getGrossAmount(), line.getDeliveredQuantity())).toList());
	}

	private String nextOrderNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('sales.order_number_seq')", Long.class);
		return "SO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}

	private static void validateOrderDates(LocalDate requested, LocalDate promised) {
		if (requested.isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "客户要求交期不能早于今天");
		if (promised != null && promised.isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "承诺交期不能早于今天");
	}

	private static void requireVersion(SalesOrderEntity order, long expected) {
		if (order.getVersion() != expected) throw conflict("订单已经被其他用户修改，请刷新后重试");
	}

	private static void requireStatus(SalesOrderEntity order, String expected, String message) {
		if (!expected.equals(order.getStatus())) throw conflict(message);
	}

	private static void requireRole(CurrentWorkspaceAccess access, Set<String> allowed, String message) {
		if (!allowed.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
	}

	private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() || "ALL".equals(value) ? "" : value; }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
