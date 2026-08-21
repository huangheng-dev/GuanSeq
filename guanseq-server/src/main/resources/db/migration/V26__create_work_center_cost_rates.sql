CREATE TABLE finance.work_center_cost_rates (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    work_center_code VARCHAR(40) NOT NULL,
    work_center_name VARCHAR(120) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    labor_rate_per_hour NUMERIC(18, 6) NOT NULL,
    overhead_rate_per_hour NUMERIC(18, 6) NOT NULL,
    effective_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_work_center_cost_rate_effective UNIQUE
        (tenant_organization_id, owning_organization_id, work_center_code, effective_date),
    CONSTRAINT uk_work_center_cost_rate_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_work_center_cost_rate_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_work_center_cost_rate_amounts CHECK
        (labor_rate_per_hour >= 0 AND overhead_rate_per_hour >= 0
         AND labor_rate_per_hour + overhead_rate_per_hour > 0)
);

CREATE INDEX idx_work_center_cost_rate_lookup ON finance.work_center_cost_rates
    (tenant_organization_id, owning_organization_id, work_center_code, status, effective_date DESC);

CREATE TABLE finance.work_center_cost_rate_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    rate_id UUID NOT NULL REFERENCES finance.work_center_cost_rates(id),
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_work_center_cost_rate_event_request UNIQUE (tenant_organization_id, request_id)
);

CREATE INDEX idx_work_center_cost_rate_event ON finance.work_center_cost_rate_events
    (tenant_organization_id, rate_id, occurred_at DESC);

ALTER TABLE finance.order_profit_settlements
    ADD COLUMN labor_cost NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN overhead_cost NUMERIC(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE finance.order_profit_settlements
    ALTER COLUMN cost_basis TYPE VARCHAR(160);

ALTER TABLE finance.order_profit_settlement_lines
    ADD COLUMN labor_cost NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN overhead_cost NUMERIC(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE finance.order_profit_settlements
    ADD CONSTRAINT ck_order_profit_processing_cost_breakdown
        CHECK (processing_cost = labor_cost + overhead_cost);

ALTER TABLE finance.order_profit_settlement_lines
    ADD CONSTRAINT ck_order_profit_line_processing_cost_breakdown
        CHECK (processing_cost = labor_cost + overhead_cost);

COMMENT ON TABLE finance.work_center_cost_rates IS '财务模块发布的工作中心人工与制造费用标准小时费率';
COMMENT ON TABLE finance.work_center_cost_rate_events IS '工作中心成本费率发布与停用审计事件';
COMMENT ON COLUMN finance.order_profit_settlements.labor_cost IS '按已完成工序标准分钟和有效工作中心费率归集的人工成本';
COMMENT ON COLUMN finance.order_profit_settlements.overhead_cost IS '按已完成工序标准分钟和有效工作中心费率归集的制造费用';
