CREATE SCHEMA IF NOT EXISTS quality;

COMMENT ON SCHEMA quality IS '检验任务、检验判定与质量证据';

ALTER TABLE production.production_orders
    ADD COLUMN reported_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE production.production_orders
    DROP CONSTRAINT ck_production_order_quantity;

ALTER TABLE production.production_orders
    ADD CONSTRAINT ck_production_order_quantity CHECK (
        planned_quantity > 0
        AND completed_quantity >= 0
        AND reported_quantity >= 0
        AND completed_quantity + reported_quantity <= planned_quantity
    );

CREATE SEQUENCE production.work_report_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE production.work_reports (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    report_number VARCHAR(40) NOT NULL,
    order_id UUID NOT NULL REFERENCES production.production_orders(id),
    order_number VARCHAR(40) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    workshop VARCHAR(120) NOT NULL,
    shift_name VARCHAR(80) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    reported_quantity NUMERIC(19, 4) NOT NULL,
    note VARCHAR(500),
    inspection_id UUID,
    accepted_quantity NUMERIC(19, 4),
    rejected_quantity NUMERIC(19, 4),
    receipt_balance_id UUID,
    receipt_movement_id UUID,
    lot_number VARCHAR(80),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_INSPECTION',
    request_id VARCHAR(120) NOT NULL,
    settlement_request_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_by UUID,
    settled_at TIMESTAMPTZ,
    CONSTRAINT uk_work_report_tenant_number UNIQUE (tenant_organization_id, report_number),
    CONSTRAINT uk_work_report_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT uk_work_report_tenant_settlement_request UNIQUE (tenant_organization_id, settlement_request_id),
    CONSTRAINT uk_work_report_inspection UNIQUE (inspection_id),
    CONSTRAINT ck_work_report_quantity CHECK (reported_quantity > 0),
    CONSTRAINT ck_work_report_status CHECK (status IN ('PENDING_INSPECTION', 'RECEIVED', 'REJECTED_CLOSED')),
    CONSTRAINT ck_work_report_result_quantities CHECK (
        (accepted_quantity IS NULL AND rejected_quantity IS NULL)
        OR (accepted_quantity >= 0 AND rejected_quantity >= 0 AND accepted_quantity + rejected_quantity = reported_quantity)
    )
);

CREATE INDEX idx_work_report_tenant_status_time
    ON production.work_reports (tenant_organization_id, status, created_at DESC);

CREATE INDEX idx_work_report_order
    ON production.work_reports (order_id, created_at DESC);

CREATE SEQUENCE quality.inspection_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE quality.inspections (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    inspection_number VARCHAR(40) NOT NULL,
    inspection_type VARCHAR(24) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    source_number VARCHAR(40) NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    inspection_quantity NUMERIC(19, 4) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    result VARCHAR(24),
    accepted_quantity NUMERIC(19, 4),
    rejected_quantity NUMERIC(19, 4),
    inspector VARCHAR(80),
    defect_description VARCHAR(500),
    conclusion VARCHAR(500),
    request_id VARCHAR(120) NOT NULL,
    decision_request_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_by UUID,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_quality_inspection_tenant_number UNIQUE (tenant_organization_id, inspection_number),
    CONSTRAINT uk_quality_inspection_source UNIQUE (tenant_organization_id, source_type, source_id),
    CONSTRAINT uk_quality_inspection_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT uk_quality_inspection_decision_request UNIQUE (tenant_organization_id, decision_request_id),
    CONSTRAINT ck_quality_inspection_type CHECK (inspection_type IN ('FINAL')),
    CONSTRAINT ck_quality_inspection_source CHECK (source_type IN ('PRODUCTION_REPORT')),
    CONSTRAINT ck_quality_inspection_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_quality_inspection_result CHECK (result IS NULL OR result IN ('PASSED', 'PARTIALLY_PASSED', 'FAILED')),
    CONSTRAINT ck_quality_inspection_quantity CHECK (inspection_quantity > 0),
    CONSTRAINT ck_quality_inspection_result_quantities CHECK (
        (status = 'PENDING' AND result IS NULL AND accepted_quantity IS NULL AND rejected_quantity IS NULL)
        OR (status = 'COMPLETED' AND result IS NOT NULL AND accepted_quantity >= 0 AND rejected_quantity >= 0
            AND accepted_quantity + rejected_quantity = inspection_quantity)
    )
);

CREATE INDEX idx_quality_inspection_tenant_status_time
    ON quality.inspections (tenant_organization_id, status, created_at DESC);

CREATE TABLE quality.inspection_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    inspection_id UUID NOT NULL REFERENCES quality.inspections(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quality_inspection_event_inspection
    ON quality.inspection_events (inspection_id, occurred_at DESC);

ALTER TABLE warehouse.stock_movements
    ADD COLUMN source_type VARCHAR(32),
    ADD COLUMN source_id UUID,
    ADD COLUMN source_number VARCHAR(40);

CREATE UNIQUE INDEX uk_stock_movement_tenant_source
    ON warehouse.stock_movements (tenant_organization_id, source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;

COMMENT ON TABLE production.work_reports IS '生产模块拥有的完工报工事实；检验后按合格数量结算';
COMMENT ON TABLE quality.inspections IS '质量模块拥有的完工检验任务和不可覆盖判定';
