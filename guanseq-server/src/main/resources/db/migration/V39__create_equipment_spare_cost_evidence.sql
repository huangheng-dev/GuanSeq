CREATE TABLE equipment.spare_parts (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    creation_request_id VARCHAR(120) NOT NULL,
    material_id UUID NOT NULL,
    material_code_snapshot VARCHAR(60) NOT NULL,
    material_name_snapshot VARCHAR(160) NOT NULL,
    material_specification_snapshot VARCHAR(240),
    unit_snapshot VARCHAR(20) NOT NULL,
    preferred_warehouse_id UUID NOT NULL,
    preferred_warehouse_code_snapshot VARCHAR(40) NOT NULL,
    preferred_warehouse_name_snapshot VARCHAR(120) NOT NULL,
    reorder_point NUMERIC(18, 4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_spare_workspace_material UNIQUE (tenant_organization_id, workspace_id, material_id),
    CONSTRAINT uk_equipment_spare_creation_request UNIQUE (tenant_organization_id, creation_request_id),
    CONSTRAINT ck_equipment_spare_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_equipment_spare_reorder_point CHECK (reorder_point >= 0)
);

CREATE INDEX idx_equipment_spare_workspace_status
    ON equipment.spare_parts (tenant_organization_id, workspace_id, status, material_code_snapshot);

CREATE TABLE equipment.maintenance_spare_transactions (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    work_order_id UUID NOT NULL REFERENCES equipment.maintenance_work_orders(id),
    spare_part_id UUID NOT NULL REFERENCES equipment.spare_parts(id),
    transaction_type VARCHAR(16) NOT NULL,
    return_of_issue_id UUID REFERENCES equipment.maintenance_spare_transactions(id),
    material_id UUID NOT NULL,
    material_code_snapshot VARCHAR(60) NOT NULL,
    material_name_snapshot VARCHAR(160) NOT NULL,
    material_specification_snapshot VARCHAR(240),
    unit_snapshot VARCHAR(20) NOT NULL,
    quantity NUMERIC(18, 4) NOT NULL,
    unit_cost NUMERIC(18, 6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    warehouse_id UUID NOT NULL,
    warehouse_code_snapshot VARCHAR(40) NOT NULL,
    warehouse_name_snapshot VARCHAR(120) NOT NULL,
    warehouse_evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_spare_transaction_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_equipment_spare_transaction_type CHECK (transaction_type IN ('ISSUE', 'RETURN')),
    CONSTRAINT ck_equipment_spare_transaction_quantity CHECK (quantity > 0),
    CONSTRAINT ck_equipment_spare_transaction_cost CHECK (unit_cost > 0 AND amount > 0),
    CONSTRAINT ck_equipment_spare_transaction_return_ref CHECK (
        (transaction_type = 'ISSUE' AND return_of_issue_id IS NULL)
        OR (transaction_type = 'RETURN' AND return_of_issue_id IS NOT NULL)
    )
);

CREATE INDEX idx_equipment_spare_transaction_order
    ON equipment.maintenance_spare_transactions (work_order_id, occurred_at DESC);
CREATE INDEX idx_equipment_spare_transaction_return
    ON equipment.maintenance_spare_transactions (return_of_issue_id, transaction_type);

CREATE TABLE equipment.maintenance_labor_transactions (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    work_order_id UUID NOT NULL REFERENCES equipment.maintenance_work_orders(id),
    transaction_type VARCHAR(16) NOT NULL,
    reversal_of_entry_id UUID REFERENCES equipment.maintenance_labor_transactions(id),
    technician_name VARCHAR(80) NOT NULL,
    hours NUMERIC(9, 2) NOT NULL,
    hourly_rate NUMERIC(18, 6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_labor_transaction_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT uk_equipment_labor_reversal UNIQUE (reversal_of_entry_id),
    CONSTRAINT ck_equipment_labor_transaction_type CHECK (transaction_type IN ('ENTRY', 'REVERSAL')),
    CONSTRAINT ck_equipment_labor_transaction_values CHECK (hours > 0 AND hours <= 24 AND hourly_rate > 0 AND amount > 0),
    CONSTRAINT ck_equipment_labor_transaction_reversal_ref CHECK (
        (transaction_type = 'ENTRY' AND reversal_of_entry_id IS NULL)
        OR (transaction_type = 'REVERSAL' AND reversal_of_entry_id IS NOT NULL)
    )
);

CREATE INDEX idx_equipment_labor_transaction_order
    ON equipment.maintenance_labor_transactions (work_order_id, occurred_at DESC);

COMMENT ON TABLE equipment.spare_parts IS '设备模块拥有的备件用途台账；库存数量仍由仓储模块拥有';
COMMENT ON TABLE equipment.maintenance_spare_transactions IS '维修工单备件领用与退回的不可变成本快照及仓库流水证据';
COMMENT ON TABLE equipment.maintenance_labor_transactions IS '维修工单人工投入与冲销的不可变运维估算证据，不是工资或财务凭证';
