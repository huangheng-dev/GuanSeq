CREATE SEQUENCE production.operation_task_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE production.operation_tasks (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    task_number VARCHAR(40) NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    planned_quantity NUMERIC(19,6) NOT NULL,
    workshop VARCHAR(120),
    routing_id UUID NOT NULL,
    routing_number VARCHAR(40) NOT NULL,
    routing_version_code VARCHAR(32) NOT NULL,
    source_operation_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    operation_code VARCHAR(40) NOT NULL,
    operation_name VARCHAR(120) NOT NULL,
    work_center_code VARCHAR(40) NOT NULL,
    work_center_name VARCHAR(120) NOT NULL,
    setup_minutes NUMERIC(12,2) NOT NULL DEFAULT 0,
    run_minutes_per_unit NUMERIC(12,2) NOT NULL DEFAULT 0,
    queue_minutes NUMERIC(12,2) NOT NULL DEFAULT 0,
    inspection_required BOOLEAN NOT NULL DEFAULT FALSE,
    instruction_summary VARCHAR(500),
    status VARCHAR(24) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    completed_quantity NUMERIC(19,6),
    shift_name VARCHAR(80),
    operator_name VARCHAR(80),
    note VARCHAR(500),
    start_request_id VARCHAR(120),
    complete_request_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_operation_task_tenant_number UNIQUE (tenant_organization_id, task_number),
    CONSTRAINT uk_operation_task_order_sequence UNIQUE (tenant_organization_id, order_id, sequence_number),
    CONSTRAINT ck_operation_task_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_operation_task_quantity CHECK (planned_quantity > 0 AND (completed_quantity IS NULL OR completed_quantity > 0))
);

CREATE INDEX idx_operation_task_tenant_status_time
    ON production.operation_tasks (tenant_organization_id, status, updated_at DESC);
CREATE INDEX idx_operation_task_order
    ON production.operation_tasks (tenant_organization_id, order_id, sequence_number);

CREATE TABLE production.operation_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES production.operation_tasks(id),
    order_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120),
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_operation_event_action CHECK (action IN ('CREATED', 'START', 'COMPLETE'))
);

CREATE UNIQUE INDEX uk_operation_event_tenant_task_request
    ON production.operation_events (tenant_organization_id, task_id, request_id)
    WHERE request_id IS NOT NULL;
CREATE INDEX idx_operation_event_task
    ON production.operation_events (task_id, occurred_at DESC);

COMMENT ON TABLE production.operation_tasks IS '生产模块拥有的订单工序执行快照；下达时由产品工艺路线复制，不随路线版本变更';
COMMENT ON TABLE production.operation_events IS '车间工序开工/完工动作审计与请求号幂等证据';

