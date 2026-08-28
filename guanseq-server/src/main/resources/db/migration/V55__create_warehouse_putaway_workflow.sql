CREATE SEQUENCE warehouse.putaway_task_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE warehouse.putaway_tasks (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    task_number VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_balance_id UUID NOT NULL,
    source_warehouse_id UUID NOT NULL,
    source_warehouse_code VARCHAR(80) NOT NULL,
    source_warehouse_name VARCHAR(160) NOT NULL,
    source_location_id UUID NOT NULL,
    source_location_code VARCHAR(80) NOT NULL,
    source_location_name VARCHAR(160) NOT NULL,
    target_location_id UUID NOT NULL,
    target_location_code VARCHAR(80) NOT NULL,
    target_location_name VARCHAR(160) NOT NULL,
    target_balance_id UUID,
    material_id UUID NOT NULL,
    material_code VARCHAR(120) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    material_specification VARCHAR(300),
    lot_number VARCHAR(120) NOT NULL DEFAULT '',
    unit VARCHAR(32) NOT NULL,
    quality_status VARCHAR(24) NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    source_out_movement_id UUID,
    source_out_movement_number VARCHAR(32),
    target_in_movement_id UUID,
    target_in_movement_number VARCHAR(32),
    reverse_out_movement_id UUID,
    reverse_out_movement_number VARCHAR(32),
    reverse_in_movement_id UUID,
    reverse_in_movement_number VARCHAR(32),
    created_by UUID NOT NULL,
    created_by_username VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_by UUID,
    completed_by_username VARCHAR(80),
    completed_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancelled_by_username VARCHAR(80),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(300),
    reversed_by UUID,
    reversed_by_username VARCHAR(80),
    reversed_at TIMESTAMPTZ,
    reversal_reason VARCHAR(300),
    create_request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_putaway_task_number UNIQUE (task_number),
    CONSTRAINT uk_putaway_task_create_request UNIQUE (tenant_organization_id, create_request_id),
    CONSTRAINT ck_putaway_task_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_putaway_task_quantity CHECK (quantity > 0),
    CONSTRAINT ck_putaway_task_quality CHECK (quality_status = 'AVAILABLE'),
    CONSTRAINT ck_putaway_task_locations CHECK (source_location_id <> target_location_id)
);

CREATE TABLE warehouse.putaway_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES warehouse.putaway_tasks(id),
    action VARCHAR(16) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(300),
    actor_user_id UUID NOT NULL,
    actor_username VARCHAR(80) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_putaway_event_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_putaway_event_action CHECK (action IN ('CREATE', 'COMPLETE', 'CANCEL', 'REVERSE'))
);

CREATE INDEX idx_putaway_tasks_tenant_status_time
    ON warehouse.putaway_tasks (tenant_organization_id, workspace_id, status, created_at DESC);
CREATE INDEX idx_putaway_tasks_source_open
    ON warehouse.putaway_tasks (tenant_organization_id, source_balance_id, status);
CREATE INDEX idx_putaway_events_task_time
    ON warehouse.putaway_events (task_id, occurred_at ASC);

COMMENT ON TABLE warehouse.putaway_tasks IS '同仓收货/待检区至正式存储库位的上架任务事实';
COMMENT ON TABLE warehouse.putaway_events IS '上架任务不可变状态流转与请求幂等证据';

