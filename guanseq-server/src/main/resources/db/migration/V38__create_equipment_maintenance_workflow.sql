CREATE TABLE equipment.maintenance_work_orders (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    work_order_number VARCHAR(40) NOT NULL,
    creation_request_id VARCHAR(120) NOT NULL,
    work_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_work_order_id UUID REFERENCES equipment.maintenance_work_orders(id),
    asset_id UUID NOT NULL REFERENCES equipment.assets(id),
    asset_code_snapshot VARCHAR(40) NOT NULL,
    asset_name_snapshot VARCHAR(120) NOT NULL,
    asset_location_snapshot VARCHAR(160) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    planned_start_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    assignee VARCHAR(80) NOT NULL,
    outcome VARCHAR(16),
    completion_notes VARCHAR(1000),
    started_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_work_order_tenant_number UNIQUE (tenant_organization_id, work_order_number),
    CONSTRAINT uk_equipment_work_order_creation_request UNIQUE (tenant_organization_id, creation_request_id),
    CONSTRAINT ck_equipment_work_order_type CHECK (work_type IN ('INSPECTION', 'PREVENTIVE_MAINTENANCE', 'REPAIR')),
    CONSTRAINT ck_equipment_work_order_source CHECK (source_type IN ('MANUAL', 'BREAKDOWN', 'INSPECTION_FAILURE', 'MAINTENANCE_FAILURE')),
    CONSTRAINT ck_equipment_work_order_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_equipment_work_order_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'WAITING_ACCEPTANCE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_equipment_work_order_outcome CHECK (outcome IS NULL OR outcome IN ('PASS', 'FAIL')),
    CONSTRAINT ck_equipment_work_order_schedule CHECK (planned_start_at <= due_at)
);

CREATE INDEX idx_equipment_work_order_workspace_type_status
    ON equipment.maintenance_work_orders (tenant_organization_id, workspace_id, work_type, status, due_at);
CREATE INDEX idx_equipment_work_order_asset_history
    ON equipment.maintenance_work_orders (asset_id, created_at DESC);
CREATE UNIQUE INDEX uk_equipment_active_intervention
    ON equipment.maintenance_work_orders (asset_id)
    WHERE work_type IN ('PREVENTIVE_MAINTENANCE', 'REPAIR')
      AND status IN ('IN_PROGRESS', 'WAITING_ACCEPTANCE');

CREATE TABLE equipment.maintenance_work_order_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    work_order_id UUID NOT NULL REFERENCES equipment.maintenance_work_orders(id),
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    outcome VARCHAR(16),
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_equipment_work_order_event_action CHECK (action IN ('CREATED', 'STARTED', 'EXECUTION_COMPLETED', 'SUBMITTED_FOR_ACCEPTANCE', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'REPAIR_GENERATED')),
    CONSTRAINT ck_equipment_work_order_event_outcome CHECK (outcome IS NULL OR outcome IN ('PASS', 'FAIL')),
    CONSTRAINT uk_equipment_work_order_event_request UNIQUE (tenant_organization_id, work_order_id, request_id)
);

CREATE INDEX idx_equipment_work_order_event_order
    ON equipment.maintenance_work_order_events (work_order_id, occurred_at DESC);

COMMENT ON TABLE equipment.maintenance_work_orders IS '一次性点检、预防性保养和维修工单及其受控执行状态';
COMMENT ON TABLE equipment.maintenance_work_order_events IS '设备运维工单创建、执行、送验、验收和异常转维修的不可变证据';
