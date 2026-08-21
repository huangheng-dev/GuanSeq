package com.guanseq.sales.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "sales", name = "shipments")
class SalesShipmentEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "shipment_number") private String shipmentNumber;
	@Column(name = "sales_order_id") private UUID salesOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code") private String warehouseCode;
	@Column(name = "warehouse_name") private String warehouseName;
	@Column(name = "planned_shipping_date") private LocalDate plannedShippingDate;
	@Column(name = "actual_shipped_at") private Instant actualShippedAt;
	private String status;
	private String note;
	@Column(name = "total_shipped_quantity") private BigDecimal totalShippedQuantity;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<SalesShipmentLineEntity> lines = new ArrayList<>();

	protected SalesShipmentEntity() { }

	SalesShipmentEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, String shipmentNumber,
			SalesOrderEntity order, UUID warehouseId, String warehouseCode, String warehouseName,
			LocalDate plannedShippingDate, String note, BigDecimal totalShippedQuantity, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.shipmentNumber = shipmentNumber;
		this.salesOrderId = order.getId();
		this.orderNumber = order.getOrderNumber();
		this.customerId = order.getCustomerId();
		this.customerCode = order.getCustomerCode();
		this.customerName = order.getCustomerName();
		this.warehouseId = warehouseId;
		this.warehouseCode = warehouseCode;
		this.warehouseName = warehouseName;
		this.plannedShippingDate = plannedShippingDate;
		this.actualShippedAt = Instant.now();
		this.status = "SHIPPED";
		this.note = note;
		this.totalShippedQuantity = totalShippedQuantity;
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = this.actualShippedAt;
		this.updatedBy = actorUserId;
		this.updatedAt = this.actualShippedAt;
	}

	void addLine(SalesShipmentLineEntity line) { this.lines.add(line); }
	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getShipmentNumber() { return shipmentNumber; }
	UUID getSalesOrderId() { return salesOrderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getCustomerId() { return customerId; }
	String getCustomerCode() { return customerCode; }
	String getCustomerName() { return customerName; }
	UUID getWarehouseId() { return warehouseId; }
	String getWarehouseCode() { return warehouseCode; }
	String getWarehouseName() { return warehouseName; }
	LocalDate getPlannedShippingDate() { return plannedShippingDate; }
	Instant getActualShippedAt() { return actualShippedAt; }
	String getStatus() { return status; }
	String getNote() { return note; }
	BigDecimal getTotalShippedQuantity() { return totalShippedQuantity; }
	String getRequestId() { return requestId; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	List<SalesShipmentLineEntity> getLines() { return lines; }
}

@Entity
@Table(schema = "sales", name = "shipment_lines")
class SalesShipmentLineEntity {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY) @jakarta.persistence.JoinColumn(name = "shipment_id") private SalesShipmentEntity shipment;
	@Column(name = "order_line_id") private UUID orderLineId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "shipped_quantity") private BigDecimal shippedQuantity;
	@Column(name = "stock_summary") private String stockSummary = "";

	protected SalesShipmentLineEntity() { }

	SalesShipmentLineEntity(SalesShipmentEntity shipment, SalesOrderLineEntity orderLine, BigDecimal shippedQuantity) {
		this.id = UUID.randomUUID();
		this.shipment = shipment;
		this.orderLineId = orderLine.getId();
		this.lineNumber = orderLine.getLineNumber();
		this.materialId = orderLine.getMaterialId();
		this.materialCode = orderLine.getMaterialCode();
		this.materialName = orderLine.getMaterialName();
		this.materialSpecification = orderLine.getMaterialSpecification();
		this.unit = orderLine.getUnit();
		this.shippedQuantity = shippedQuantity;
	}

	void attachStockSummary(String summary) { this.stockSummary = summary; }
	UUID getId() { return id; }
	UUID getOrderLineId() { return orderLineId; }
	int getLineNumber() { return lineNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getShippedQuantity() { return shippedQuantity; }
	String getStockSummary() { return stockSummary; }
}

@Entity
@Table(schema = "sales", name = "shipment_events")
class SalesShipmentEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "shipment_id") private UUID shipmentId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected SalesShipmentEventEntity() { }

	SalesShipmentEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID shipmentId,
			String action, String fromStatus, String toStatus, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.shipmentId = shipmentId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}