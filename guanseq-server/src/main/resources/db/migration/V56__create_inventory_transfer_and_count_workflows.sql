CREATE SEQUENCE warehouse.transfer_task_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE warehouse.stock_count_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE warehouse.transfer_tasks (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    task_number VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_balance_id UUID NOT NULL REFERENCES warehouse.stock_balances(id),
    source_warehouse_id UUID NOT NULL REFERENCES warehouse.warehouses(id),
    source_warehouse_code VARCHAR(80) NOT NULL,
    source_warehouse_name VARCHAR(160) NOT NULL,
    source_location_id UUID NOT NULL REFERENCES warehouse.storage_locations(id),
    source_location_code VARCHAR(80) NOT NULL,
    source_location_name VARCHAR(160) NOT NULL,
    target_location_id UUID NOT NULL REFERENCES warehouse.storage_locations(id),
    target_location_code VARCHAR(80) NOT NULL,
    target_location_name VARCHAR(160) NOT NULL,
    target_balance_id UUID REFERENCES warehouse.stock_balances(id),
    material_id UUID NOT NULL,
    material_code VARCHAR(120) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    material_specification VARCHAR(300),
    lot_number VARCHAR(120) NOT NULL DEFAULT '',
    unit VARCHAR(32) NOT NULL,
    quality_status VARCHAR(24) NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    transfer_reason VARCHAR(300) NOT NULL,
    source_out_movement_id UUID,
    source_out_movement_number VARCHAR(40),
    target_in_movement_id UUID,
    target_in_movement_number VARCHAR(40),
    reverse_out_movement_id UUID,
    reverse_out_movement_number VARCHAR(40),
    reverse_in_movement_id UUID,
    reverse_in_movement_number VARCHAR(40),
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
    CONSTRAINT uk_transfer_task_number UNIQUE (task_number),
    CONSTRAINT uk_transfer_task_create_request UNIQUE (tenant_organization_id, create_request_id),
    CONSTRAINT ck_transfer_task_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_transfer_task_quantity CHECK (quantity > 0),
    CONSTRAINT ck_transfer_task_quality CHECK (quality_status = 'AVAILABLE'),
    CONSTRAINT ck_transfer_task_locations CHECK (source_location_id <> target_location_id)
);

CREATE TABLE warehouse.transfer_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES warehouse.transfer_tasks(id),
    action VARCHAR(16) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(300),
    actor_user_id UUID NOT NULL,
    actor_username VARCHAR(80) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_event_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_transfer_event_action CHECK (action IN ('CREATE', 'COMPLETE', 'CANCEL', 'REVERSE'))
);

CREATE TABLE warehouse.stock_count_tasks (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    count_number VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    balance_id UUID NOT NULL REFERENCES warehouse.stock_balances(id),
    warehouse_id UUID NOT NULL REFERENCES warehouse.warehouses(id),
    warehouse_code VARCHAR(80) NOT NULL,
    warehouse_name VARCHAR(160) NOT NULL,
    location_id UUID NOT NULL REFERENCES warehouse.storage_locations(id),
    location_code VARCHAR(80) NOT NULL,
    location_name VARCHAR(160) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(120) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    material_specification VARCHAR(300),
    lot_number VARCHAR(120) NOT NULL DEFAULT '',
    unit VARCHAR(32) NOT NULL,
    quality_status VARCHAR(24) NOT NULL,
    book_on_hand NUMERIC(19,6) NOT NULL,
    book_allocated NUMERIC(19,6) NOT NULL,
    book_frozen NUMERIC(19,6) NOT NULL,
    counted_quantity NUMERIC(19,6),
    difference_quantity NUMERIC(19,6),
    snapshot_balance_version BIGINT NOT NULL,
    adjustment_movement_id UUID,
    adjustment_movement_number VARCHAR(40),
    adjustment_movement_type VARCHAR(24),
    reverse_movement_id UUID,
    reverse_movement_number VARCHAR(40),
    reverse_movement_type VARCHAR(24),
    count_note VARCHAR(300),
    approval_comment VARCHAR(300),
    created_by UUID NOT NULL,
    created_by_username VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    counted_by UUID,
    counted_by_username VARCHAR(80),
    counted_at TIMESTAMPTZ,
    approved_by UUID,
    approved_by_username VARCHAR(80),
    approved_at TIMESTAMPTZ,
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
    CONSTRAINT uk_stock_count_number UNIQUE (count_number),
    CONSTRAINT uk_stock_count_create_request UNIQUE (tenant_organization_id, create_request_id),
    CONSTRAINT ck_stock_count_status CHECK (status IN ('OPEN', 'COUNTED', 'APPROVED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_stock_count_book_values CHECK (book_on_hand >= 0 AND book_allocated >= 0 AND book_frozen >= 0),
    CONSTRAINT ck_stock_count_counted_quantity CHECK (counted_quantity IS NULL OR counted_quantity >= 0),
    CONSTRAINT ck_stock_count_adjustment_type CHECK (adjustment_movement_type IS NULL OR adjustment_movement_type IN ('RECEIPT', 'ISSUE')),
    CONSTRAINT ck_stock_count_reverse_type CHECK (reverse_movement_type IS NULL OR reverse_movement_type IN ('RECEIPT', 'ISSUE'))
);

CREATE TABLE warehouse.stock_count_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES warehouse.stock_count_tasks(id),
    action VARCHAR(24) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(300),
    actor_user_id UUID NOT NULL,
    actor_username VARCHAR(80) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_count_event_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_stock_count_event_action CHECK (action IN ('CREATE', 'RECORD_COUNT', 'APPROVE', 'CANCEL', 'REVERSE'))
);

CREATE INDEX idx_transfer_tasks_tenant_status_time
    ON warehouse.transfer_tasks (tenant_organization_id, workspace_id, status, created_at DESC);
CREATE INDEX idx_transfer_tasks_source_open
    ON warehouse.transfer_tasks (tenant_organization_id, source_balance_id, status);
CREATE INDEX idx_transfer_events_task_time
    ON warehouse.transfer_events (task_id, occurred_at ASC);
CREATE INDEX idx_stock_count_tasks_tenant_status_time
    ON warehouse.stock_count_tasks (tenant_organization_id, workspace_id, status, created_at DESC);
CREATE INDEX idx_stock_count_tasks_balance_status
    ON warehouse.stock_count_tasks (tenant_organization_id, balance_id, status);
CREATE INDEX idx_stock_count_events_task_time
    ON warehouse.stock_count_events (task_id, occurred_at ASC);

COMMENT ON TABLE warehouse.transfer_tasks IS '同仓活动存储库位之间的受控调拨任务；库存事实仍由余额与不可变流水拥有';
COMMENT ON TABLE warehouse.transfer_events IS '调拨任务不可变状态流转与请求幂等证据';
COMMENT ON TABLE warehouse.stock_count_tasks IS '单库存余额的账面快照、实盘数量、差异审批与补偿冲回任务';
COMMENT ON TABLE warehouse.stock_count_events IS '盘点任务不可变状态流转与请求幂等证据';
