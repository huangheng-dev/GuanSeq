CREATE TABLE planning.material_planning_parameters (
    material_id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    procurement_type VARCHAR(20) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    lead_time_days INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_material_planning_parameter_tenant_code UNIQUE (tenant_organization_id, material_code),
    CONSTRAINT ck_material_planning_procurement_type CHECK (procurement_type IN ('MAKE', 'BUY', 'OUTSOURCE')),
    CONSTRAINT ck_material_planning_lead_time CHECK (lead_time_days BETWEEN 1 AND 3650)
);

CREATE INDEX idx_material_planning_parameter_tenant
    ON planning.material_planning_parameters (tenant_organization_id, material_code);

CREATE TABLE planning.mrp_run_scheduled_receipt_snapshots (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES planning.mrp_runs(id),
    tenant_organization_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_order_id UUID NOT NULL,
    source_order_number VARCHAR(40) NOT NULL,
    source_line_id UUID NOT NULL,
    supplier_name VARCHAR(160),
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    outstanding_quantity NUMERIC(18, 4) NOT NULL,
    expected_receipt_date DATE NOT NULL,
    snapshotted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mrp_receipt_snapshot_source_line UNIQUE (run_id, source_type, source_line_id),
    CONSTRAINT ck_mrp_receipt_snapshot_source_type CHECK (source_type IN ('PURCHASE_ORDER', 'PRODUCTION_ORDER')),
    CONSTRAINT ck_mrp_receipt_snapshot_quantity CHECK (outstanding_quantity > 0)
);

CREATE INDEX idx_mrp_receipt_snapshot_run
    ON planning.mrp_run_scheduled_receipt_snapshots (run_id, expected_receipt_date, material_code);

COMMENT ON TABLE planning.material_planning_parameters IS '计划模块拥有的物料级受控提前期；其他计划参数按真实需求增量引入';
COMMENT ON TABLE planning.mrp_run_scheduled_receipt_snapshots IS 'MRP 发起时从业务模块公开接口冻结的计划接收事实';
