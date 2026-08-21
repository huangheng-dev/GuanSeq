ALTER TABLE sales.order_lines
    ADD COLUMN IF NOT EXISTS delivered_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE sales.order_lines
    DROP CONSTRAINT IF EXISTS ck_sales_order_line_delivered_quantity;
ALTER TABLE sales.order_lines
    ADD CONSTRAINT ck_sales_order_line_delivered_quantity CHECK (delivered_quantity >= 0 AND delivered_quantity <= quantity);

ALTER TABLE sales.orders
    DROP CONSTRAINT IF EXISTS ck_sales_order_status;
ALTER TABLE sales.orders
    ADD CONSTRAINT ck_sales_order_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'RELEASED', 'PARTIALLY_SHIPPED', 'SHIPPED'));

CREATE SEQUENCE IF NOT EXISTS sales.shipment_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE sales.shipments (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    shipment_number VARCHAR(40) NOT NULL,
    sales_order_id UUID NOT NULL REFERENCES sales.orders(id),
    order_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouse.warehouses(id),
    warehouse_code VARCHAR(40) NOT NULL,
    warehouse_name VARCHAR(120) NOT NULL,
    planned_shipping_date DATE NOT NULL,
    actual_shipped_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'SHIPPED',
    note VARCHAR(500),
    total_shipped_quantity NUMERIC(18, 4) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_shipment_tenant_number UNIQUE (tenant_organization_id, shipment_number),
    CONSTRAINT uk_sales_shipment_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_sales_shipment_status CHECK (status IN ('SHIPPED')),
    CONSTRAINT ck_sales_shipment_quantity CHECK (total_shipped_quantity > 0)
);

CREATE INDEX idx_sales_shipment_order
    ON sales.shipments (tenant_organization_id, sales_order_id, created_at DESC);
CREATE INDEX idx_sales_shipment_status
    ON sales.shipments (tenant_organization_id, status, planned_shipping_date DESC);

CREATE TABLE sales.shipment_lines (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES sales.shipments(id) ON DELETE CASCADE,
    order_line_id UUID NOT NULL REFERENCES sales.order_lines(id),
    line_number INTEGER NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    shipped_quantity NUMERIC(18, 4) NOT NULL,
    stock_summary VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uk_sales_shipment_line_order_line UNIQUE (shipment_id, order_line_id),
    CONSTRAINT ck_sales_shipment_line_quantity CHECK (shipped_quantity > 0)
);

CREATE INDEX idx_sales_shipment_line_order_line
    ON sales.shipment_lines (order_line_id);

CREATE TABLE sales.shipment_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    shipment_id UUID NOT NULL REFERENCES sales.shipments(id),
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    request_id VARCHAR(64),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sales_shipment_event_shipment
    ON sales.shipment_events (tenant_organization_id, shipment_id, occurred_at DESC);

COMMENT ON TABLE sales.shipments IS '销售发货单，记录客户发货、出库仓库和审计证据';
COMMENT ON TABLE sales.shipment_lines IS '销售发货单行，对应销售订单行和本次出库数量';
COMMENT ON TABLE sales.shipment_events IS '销售发货动作审计事件';
COMMENT ON COLUMN sales.order_lines.delivered_quantity IS '累计合格成品出库数量，后续成本利润切片以此作为履约数量';