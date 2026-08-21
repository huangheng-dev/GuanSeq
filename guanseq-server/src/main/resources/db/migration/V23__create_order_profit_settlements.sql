CREATE SCHEMA IF NOT EXISTS finance;
COMMENT ON SCHEMA finance IS '订单利润、成本快照与财务结算事实';

CREATE SEQUENCE IF NOT EXISTS finance.order_profit_settlement_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE finance.item_standard_costs (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    unit_cost NUMERIC(18, 6) NOT NULL,
    effective_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_item_standard_cost_tenant_material_effective UNIQUE (tenant_organization_id, material_id, effective_date),
    CONSTRAINT ck_item_standard_cost_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_item_standard_cost_unit_cost CHECK (unit_cost > 0)
);

CREATE UNIQUE INDEX uk_item_standard_cost_active_material
    ON finance.item_standard_costs (tenant_organization_id, material_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_item_standard_cost_material
    ON finance.item_standard_costs (tenant_organization_id, material_id, effective_date DESC);

CREATE TABLE finance.order_profit_settlements (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    settlement_number VARCHAR(40) NOT NULL,
    sales_order_id UUID NOT NULL REFERENCES sales.orders(id),
    order_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    shipped_quantity NUMERIC(18, 4) NOT NULL,
    revenue NUMERIC(18, 2) NOT NULL,
    material_cost NUMERIC(18, 2) NOT NULL,
    processing_cost NUMERIC(18, 2) NOT NULL,
    total_cost NUMERIC(18, 2) NOT NULL,
    gross_profit NUMERIC(18, 2) NOT NULL,
    gross_margin NUMERIC(9, 6),
    cost_basis VARCHAR(80) NOT NULL,
    cost_status VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SETTLED',
    missing_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_profit_settlement_tenant_number UNIQUE (tenant_organization_id, settlement_number),
    CONSTRAINT uk_order_profit_settlement_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT uk_order_profit_settlement_order UNIQUE (tenant_organization_id, sales_order_id),
    CONSTRAINT ck_order_profit_settlement_status CHECK (status IN ('SETTLED')),
    CONSTRAINT ck_order_profit_settlement_cost_status CHECK (cost_status IN ('COMPLETE', 'MISSING_COST')),
    CONSTRAINT ck_order_profit_settlement_quantity CHECK (shipped_quantity > 0),
    CONSTRAINT ck_order_profit_settlement_amounts CHECK (
        revenue >= 0
        AND material_cost >= 0
        AND processing_cost >= 0
        AND total_cost = material_cost + processing_cost
        AND gross_profit = revenue - total_cost
    )
);

CREATE INDEX idx_order_profit_settlement_customer
    ON finance.order_profit_settlements (tenant_organization_id, customer_id, settled_at DESC);
CREATE INDEX idx_order_profit_settlement_status
    ON finance.order_profit_settlements (tenant_organization_id, cost_status, settled_at DESC);

CREATE TABLE finance.order_profit_settlement_lines (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES finance.order_profit_settlements(id) ON DELETE CASCADE,
    sales_order_line_id UUID NOT NULL REFERENCES sales.order_lines(id),
    line_number INTEGER NOT NULL,
    production_order_id UUID,
    production_order_number VARCHAR(40),
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    ordered_quantity NUMERIC(18, 4) NOT NULL,
    shipped_quantity NUMERIC(18, 4) NOT NULL,
    accepted_quantity NUMERIC(18, 4),
    consumed_quantity NUMERIC(18, 6),
    unit_price NUMERIC(18, 6) NOT NULL,
    revenue NUMERIC(18, 2) NOT NULL,
    material_cost NUMERIC(18, 2) NOT NULL,
    processing_cost NUMERIC(18, 2) NOT NULL,
    total_cost NUMERIC(18, 2) NOT NULL,
    gross_profit NUMERIC(18, 2) NOT NULL,
    gross_margin NUMERIC(9, 6),
    cost_status VARCHAR(24) NOT NULL,
    cost_details JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uk_order_profit_settlement_line_order_line UNIQUE (settlement_id, sales_order_line_id),
    CONSTRAINT ck_order_profit_settlement_line_quantities CHECK (
        ordered_quantity > 0
        AND shipped_quantity > 0
        AND (accepted_quantity IS NULL OR accepted_quantity >= 0)
        AND (consumed_quantity IS NULL OR consumed_quantity >= 0)
    ),
    CONSTRAINT ck_order_profit_settlement_line_amounts CHECK (
        unit_price >= 0
        AND revenue >= 0
        AND material_cost >= 0
        AND processing_cost >= 0
        AND total_cost = material_cost + processing_cost
        AND gross_profit = revenue - total_cost
    ),
    CONSTRAINT ck_order_profit_settlement_line_cost_status CHECK (cost_status IN ('COMPLETE', 'MISSING_COST'))
);

CREATE INDEX idx_order_profit_settlement_line_material
    ON finance.order_profit_settlement_lines (settlement_id, material_code);
CREATE INDEX idx_order_profit_settlement_line_production_order
    ON finance.order_profit_settlement_lines (production_order_id);

CREATE TABLE finance.order_profit_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    settlement_id UUID NOT NULL REFERENCES finance.order_profit_settlements(id),
    action VARCHAR(40) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    cost_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_profit_event_settlement
    ON finance.order_profit_events (tenant_organization_id, settlement_id, occurred_at DESC);

COMMENT ON TABLE finance.item_standard_costs IS '财务模块拥有的物料标准成本；第一版作为实际领料金额的计价来源';
COMMENT ON TABLE finance.order_profit_settlements IS '销售订单利润结算快照，不回写销售、生产或库存事实';
COMMENT ON TABLE finance.order_profit_settlement_lines IS '订单行收入、材料成本、加工成本和毛利快照';
COMMENT ON TABLE finance.order_profit_events IS '订单利润结算动作审计事件';

