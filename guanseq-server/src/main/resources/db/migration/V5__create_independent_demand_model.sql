CREATE SCHEMA planning;

COMMENT ON SCHEMA planning IS '计划需求、主计划、MRP 与供需平衡事实';

CREATE SEQUENCE planning.demand_number_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE planning.independent_demands (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    demand_number VARCHAR(40) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id UUID,
    source_number VARCHAR(60),
    source_line_id UUID,
    source_line_number INTEGER,
    source_customer VARCHAR(160),
    material_id UUID NOT NULL,
    material_code VARCHAR(80) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(24) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    required_date DATE NOT NULL,
    priority VARCHAR(16) NOT NULL,
    owner VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    note VARCHAR(500),
    cancellation_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_planning_demand_tenant_number UNIQUE (tenant_organization_id, demand_number),
    CONSTRAINT uk_planning_demand_source_line UNIQUE (tenant_organization_id, source_type, source_line_id),
    CONSTRAINT ck_planning_demand_source_type CHECK (source_type IN ('SALES_ORDER', 'MANUAL')),
    CONSTRAINT ck_planning_demand_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_planning_demand_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_planning_demand_quantity CHECK (quantity > 0),
    CONSTRAINT ck_planning_demand_source_fields CHECK (
        (source_type = 'MANUAL' AND source_id IS NULL AND source_number IS NULL AND source_line_id IS NULL AND source_line_number IS NULL)
        OR
        (source_type = 'SALES_ORDER' AND source_id IS NOT NULL AND source_number IS NOT NULL AND source_line_id IS NOT NULL AND source_line_number IS NOT NULL)
    )
);

CREATE INDEX idx_planning_demand_tenant_status_date
    ON planning.independent_demands (tenant_organization_id, status, required_date, updated_at DESC);

CREATE INDEX idx_planning_demand_material_date
    ON planning.independent_demands (tenant_organization_id, material_id, required_date)
    WHERE status = 'ACTIVE';

CREATE TABLE planning.demand_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    demand_id UUID NOT NULL REFERENCES planning.independent_demands(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16),
    request_id VARCHAR(120),
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_planning_demand_event_demand
    ON planning.demand_events (demand_id, occurred_at DESC);

COMMENT ON TABLE planning.independent_demands IS 'MRP 的独立需求输入，保存销售订单或人工需求的受控快照';
COMMENT ON TABLE planning.demand_events IS '独立需求创建、激活和取消的操作证据';
