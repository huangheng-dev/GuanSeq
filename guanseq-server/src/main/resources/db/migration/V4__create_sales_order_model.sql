CREATE SEQUENCE sales.order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE sales.orders (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    tax_rate NUMERIC(7, 4) NOT NULL,
    requested_delivery_date DATE NOT NULL,
    promised_delivery_date DATE,
    owner VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    total_net_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_gross_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    rejection_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_order_tenant_number UNIQUE (tenant_organization_id, order_number),
    CONSTRAINT ck_sales_order_currency CHECK (currency IN ('CNY', 'USD', 'EUR')),
    CONSTRAINT ck_sales_order_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_sales_order_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'RELEASED')),
    CONSTRAINT ck_sales_order_amounts CHECK (total_net_amount >= 0 AND total_tax_amount >= 0 AND total_gross_amount >= 0)
);

CREATE INDEX idx_sales_order_tenant_status
    ON sales.orders (tenant_organization_id, status, updated_at DESC);

CREATE TABLE sales.order_lines (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES sales.orders(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    quantity NUMERIC(18, 4) NOT NULL,
    unit_price NUMERIC(18, 4) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    CONSTRAINT uk_sales_order_line_number UNIQUE (order_id, line_number) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_sales_order_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sales_order_line_unit_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_sales_order_line_material
    ON sales.order_lines (material_id, order_id);

CREATE TABLE sales.change_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    order_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    request_id VARCHAR(64),
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sales_change_order
    ON sales.change_events (tenant_organization_id, order_id, occurred_at DESC);

COMMENT ON TABLE sales.orders IS '销售订单聚合根，保存客户快照、交期、金额和受控状态';
COMMENT ON TABLE sales.order_lines IS '销售订单物料行与成交价格快照';
COMMENT ON TABLE sales.change_events IS '销售订单创建、修改、审核、驳回和下达的审计证据';
