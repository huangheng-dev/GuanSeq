CREATE SEQUENCE planning.mrp_run_number_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE planning.mrp_runs (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    run_number VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    horizon_start DATE NOT NULL,
    horizon_end DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    demand_count INTEGER NOT NULL,
    total_quantity NUMERIC(19, 4) NOT NULL,
    exception_count INTEGER NOT NULL,
    started_by UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    request_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_planning_mrp_run_tenant_number UNIQUE (tenant_organization_id, run_number),
    CONSTRAINT ck_planning_mrp_run_status CHECK (status IN ('PREPARING', 'BLOCKED', 'COMPLETED')),
    CONSTRAINT ck_planning_mrp_run_horizon CHECK (horizon_start <= horizon_end),
    CONSTRAINT ck_planning_mrp_run_counts CHECK (demand_count > 0 AND total_quantity > 0 AND exception_count >= 0)
);

CREATE INDEX idx_planning_mrp_run_tenant_started
    ON planning.mrp_runs (tenant_organization_id, started_at DESC);

CREATE TABLE planning.mrp_run_demands (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES planning.mrp_runs(id),
    tenant_organization_id UUID NOT NULL,
    demand_id UUID NOT NULL,
    demand_number VARCHAR(40) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_number VARCHAR(60),
    material_id UUID NOT NULL,
    material_code VARCHAR(80) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    procurement_type VARCHAR(16) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    required_date DATE NOT NULL,
    priority VARCHAR(16) NOT NULL,
    owner VARCHAR(80) NOT NULL,
    snapshotted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_planning_mrp_run_demand UNIQUE (run_id, demand_id),
    CONSTRAINT ck_planning_mrp_snapshot_procurement CHECK (procurement_type IN ('MAKE', 'BUY', 'OUTSOURCE')),
    CONSTRAINT ck_planning_mrp_snapshot_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_planning_mrp_snapshot_run_date
    ON planning.mrp_run_demands (run_id, required_date, material_code);

CREATE TABLE planning.mrp_run_exceptions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES planning.mrp_runs(id),
    tenant_organization_id UUID NOT NULL,
    code VARCHAR(48) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    material_id UUID,
    material_code VARCHAR(80),
    material_name VARCHAR(160),
    message VARCHAR(500) NOT NULL,
    resolution_path VARCHAR(240) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_planning_mrp_exception_severity CHECK (severity IN ('BLOCKER', 'WARNING'))
);

CREATE INDEX idx_planning_mrp_exception_run
    ON planning.mrp_run_exceptions (run_id, severity, code);

CREATE TABLE planning.mrp_run_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    run_id UUID NOT NULL REFERENCES planning.mrp_runs(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_planning_mrp_run_event_run
    ON planning.mrp_run_events (run_id, occurred_at DESC);

COMMENT ON TABLE planning.mrp_runs IS 'MRP 运算准备记录；在净需求引擎具备真实输入前只允许形成可追溯的阻断结果';
COMMENT ON TABLE planning.mrp_run_demands IS 'MRP 发起时冻结的有效独立需求，不随来源需求后续变更';
COMMENT ON TABLE planning.mrp_run_exceptions IS 'MRP 前置条件检查产生的阻断与警告，不伪造供需建议';
COMMENT ON TABLE planning.mrp_run_events IS 'MRP 运算准备动作的审计证据';
