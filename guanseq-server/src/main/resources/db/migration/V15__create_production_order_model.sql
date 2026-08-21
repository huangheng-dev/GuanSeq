CREATE SCHEMA IF NOT EXISTS production;

COMMENT ON SCHEMA production IS '生产订单、车间执行、报工和完工事实';

CREATE SEQUENCE production.production_order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE production.production_orders (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    planned_quantity NUMERIC(19, 4) NOT NULL,
    completed_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0,
    planned_start_date DATE NOT NULL,
    planned_receipt_date DATE NOT NULL,
    workshop VARCHAR(120) NOT NULL,
    owner VARCHAR(80) NOT NULL,
    source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    source_id UUID,
    source_number VARCHAR(60),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    cancellation_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_production_order_tenant_number UNIQUE (tenant_organization_id, order_number),
    CONSTRAINT ck_production_order_status CHECK (status IN ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_production_order_source CHECK (source_type IN ('MANUAL', 'MRP', 'SALES_ORDER')),
    CONSTRAINT ck_production_order_quantity CHECK (planned_quantity > 0 AND completed_quantity >= 0 AND completed_quantity <= planned_quantity),
    CONSTRAINT ck_production_order_dates CHECK (planned_start_date <= planned_receipt_date)
);

CREATE INDEX idx_production_order_tenant_status_date
    ON production.production_orders (tenant_organization_id, status, planned_receipt_date, order_number);

CREATE INDEX idx_production_order_tenant_material
    ON production.production_orders (tenant_organization_id, material_id, planned_receipt_date);

CREATE TABLE production.production_order_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    order_id UUID NOT NULL REFERENCES production.production_orders(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120),
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_production_order_event_order
    ON production.production_order_events (order_id, occurred_at DESC);

COMMENT ON TABLE production.production_orders IS '生产模块拥有的受控生产订单；已下达和执行中的未完工数量可作为 MRP 计划接收';
COMMENT ON TABLE production.production_order_events IS '生产订单创建、编辑、下达、开工、完工和取消的审计证据';
