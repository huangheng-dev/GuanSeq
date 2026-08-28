CREATE TABLE equipment.maintenance_plans (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    plan_code VARCHAR(40) NOT NULL,
    creation_request_id VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    work_type VARCHAR(32) NOT NULL,
    asset_id UUID NOT NULL REFERENCES equipment.assets(id),
    asset_code_snapshot VARCHAR(40) NOT NULL,
    asset_name_snapshot VARCHAR(120) NOT NULL,
    asset_location_snapshot VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    interval_days INTEGER NOT NULL,
    lead_days INTEGER NOT NULL,
    first_due_date DATE NOT NULL,
    next_due_date DATE NOT NULL,
    planned_start_time TIME NOT NULL,
    due_time TIME NOT NULL,
    assignee VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_plan_tenant_code UNIQUE (tenant_organization_id, plan_code),
    CONSTRAINT uk_equipment_plan_creation_request UNIQUE (tenant_organization_id, creation_request_id),
    CONSTRAINT ck_equipment_plan_type CHECK (work_type IN ('INSPECTION', 'PREVENTIVE_MAINTENANCE')),
    CONSTRAINT ck_equipment_plan_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_equipment_plan_interval CHECK (interval_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_equipment_plan_lead CHECK (lead_days BETWEEN 0 AND 365),
    CONSTRAINT ck_equipment_plan_times CHECK (planned_start_time <= due_time),
    CONSTRAINT ck_equipment_plan_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_equipment_plan_workspace_status_due
    ON equipment.maintenance_plans (tenant_organization_id, workspace_id, status, next_due_date);

CREATE TABLE equipment.maintenance_plan_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES equipment.maintenance_plans(id),
    action VARCHAR(24) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_equipment_plan_event_action CHECK (action IN ('CREATED', 'ACTIVATED', 'INACTIVATED')),
    CONSTRAINT ck_equipment_plan_event_status CHECK (
        (from_status IS NULL OR from_status IN ('ACTIVE', 'INACTIVE'))
        AND to_status IN ('ACTIVE', 'INACTIVE')
    ),
    CONSTRAINT uk_equipment_plan_event_request UNIQUE (tenant_organization_id, plan_id, request_id)
);

CREATE INDEX idx_equipment_plan_event_plan
    ON equipment.maintenance_plan_events (plan_id, occurred_at DESC);

CREATE TABLE equipment.maintenance_generation_runs (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    as_of_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    generated_count INTEGER NOT NULL DEFAULT 0,
    existing_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    actor_user_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_equipment_generation_run_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_equipment_generation_run_status CHECK (status IN ('RUNNING', 'COMPLETED')),
    CONSTRAINT ck_equipment_generation_run_counts CHECK (
        generated_count >= 0 AND existing_count >= 0 AND skipped_count >= 0
    )
);

CREATE INDEX idx_equipment_generation_run_workspace
    ON equipment.maintenance_generation_runs (tenant_organization_id, workspace_id, started_at DESC);

CREATE TABLE equipment.maintenance_generation_items (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES equipment.maintenance_generation_runs(id),
    plan_id UUID NOT NULL REFERENCES equipment.maintenance_plans(id),
    due_date DATE NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    work_order_id UUID REFERENCES equipment.maintenance_work_orders(id),
    message VARCHAR(500) NOT NULL,
    CONSTRAINT uk_equipment_generation_item UNIQUE (run_id, plan_id, due_date),
    CONSTRAINT ck_equipment_generation_item_outcome CHECK (outcome IN ('GENERATED', 'ALREADY_EXISTS', 'SKIPPED_INACTIVE_ASSET'))
);

ALTER TABLE equipment.maintenance_work_orders
    ADD COLUMN source_plan_id UUID REFERENCES equipment.maintenance_plans(id),
    ADD COLUMN source_due_date DATE;

ALTER TABLE equipment.maintenance_work_orders
    DROP CONSTRAINT ck_equipment_work_order_source,
    ADD CONSTRAINT ck_equipment_work_order_source CHECK (
        source_type IN ('MANUAL', 'BREAKDOWN', 'INSPECTION_FAILURE', 'MAINTENANCE_FAILURE', 'MAINTENANCE_PLAN')
    ),
    ADD CONSTRAINT ck_equipment_work_order_plan_source CHECK (
        (source_type = 'MAINTENANCE_PLAN' AND source_plan_id IS NOT NULL AND source_due_date IS NOT NULL)
        OR (source_type <> 'MAINTENANCE_PLAN' AND source_plan_id IS NULL AND source_due_date IS NULL)
    );

CREATE UNIQUE INDEX uk_equipment_work_order_plan_due
    ON equipment.maintenance_work_orders (tenant_organization_id, source_plan_id, source_due_date)
    WHERE source_plan_id IS NOT NULL;

COMMENT ON TABLE equipment.maintenance_plans IS '点检与预防性保养周期模板，按设备、周期、提前期和责任人定义';
COMMENT ON TABLE equipment.maintenance_generation_runs IS '人工触发到期任务生成的批次级幂等与责任证据';
COMMENT ON TABLE equipment.maintenance_generation_items IS '每个模板到期日的生成、复用或跳过结果证据';
COMMENT ON COLUMN equipment.maintenance_work_orders.source_due_date IS '周期模板对应的业务到期日，按模板与到期日保证唯一生成';
