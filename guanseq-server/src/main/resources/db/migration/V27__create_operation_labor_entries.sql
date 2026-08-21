CREATE SEQUENCE production.operation_labor_entry_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE production.operation_labor_entries (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    entry_number VARCHAR(40) NOT NULL,
    task_id UUID NOT NULL REFERENCES production.operation_tasks(id),
    task_number VARCHAR(40) NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    operation_code VARCHAR(40) NOT NULL,
    operation_name VARCHAR(120) NOT NULL,
    work_center_code VARCHAR(40) NOT NULL,
    work_center_name VARCHAR(120) NOT NULL,
    work_date DATE NOT NULL,
    shift_name VARCHAR(80) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    actual_minutes NUMERIC(12,2) NOT NULL,
    status VARCHAR(24) NOT NULL,
    note VARCHAR(500),
    request_id VARCHAR(120) NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    approve_request_id VARCHAR(120),
    voided_by UUID,
    voided_at TIMESTAMPTZ,
    void_reason VARCHAR(500),
    void_request_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_operation_labor_tenant_number UNIQUE (tenant_organization_id, entry_number),
    CONSTRAINT uk_operation_labor_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_operation_labor_status CHECK (status IN ('RECORDED', 'APPROVED', 'VOIDED')),
    CONSTRAINT ck_operation_labor_minutes CHECK (actual_minutes > 0 AND actual_minutes <= 1440),
    CONSTRAINT ck_operation_labor_approval CHECK (
        (status = 'RECORDED' AND approved_by IS NULL AND approved_at IS NULL AND approve_request_id IS NULL AND voided_by IS NULL AND voided_at IS NULL AND void_request_id IS NULL)
        OR (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND approve_request_id IS NOT NULL AND voided_by IS NULL AND voided_at IS NULL AND void_request_id IS NULL)
        OR (status = 'VOIDED' AND voided_by IS NOT NULL AND voided_at IS NOT NULL AND void_request_id IS NOT NULL AND void_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_operation_labor_tenant_approve_request
    ON production.operation_labor_entries (tenant_organization_id, approve_request_id)
    WHERE approve_request_id IS NOT NULL;
CREATE UNIQUE INDEX uk_operation_labor_tenant_void_request
    ON production.operation_labor_entries (tenant_organization_id, void_request_id)
    WHERE void_request_id IS NOT NULL;
CREATE INDEX idx_operation_labor_tenant_status_date
    ON production.operation_labor_entries (tenant_organization_id, status, work_date DESC, created_at DESC);
CREATE INDEX idx_operation_labor_task
    ON production.operation_labor_entries (tenant_organization_id, task_id, status);
CREATE INDEX idx_operation_labor_order
    ON production.operation_labor_entries (tenant_organization_id, order_id, work_date DESC);

CREATE TABLE production.operation_labor_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    entry_id UUID NOT NULL REFERENCES production.operation_labor_entries(id),
    task_id UUID NOT NULL,
    order_id UUID NOT NULL,
    action VARCHAR(24) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_operation_labor_event_action CHECK (action IN ('RECORDED', 'APPROVED', 'VOIDED')),
    CONSTRAINT uk_operation_labor_event_request UNIQUE (tenant_organization_id, request_id)
);

CREATE INDEX idx_operation_labor_event_entry
    ON production.operation_labor_events (entry_id, occurred_at DESC);

COMMENT ON TABLE production.operation_labor_entries IS '生产模块拥有的实际人工工时事实；已审核记录供财务订单成本归集，错误通过冲销保留证据';
COMMENT ON TABLE production.operation_labor_events IS '实际人工工时登记、审核与冲销的不可变审计证据';
