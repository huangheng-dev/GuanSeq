ALTER TABLE masterdata.materials
    ADD COLUMN IF NOT EXISTS incoming_inspection_required BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE masterdata.materials
SET incoming_inspection_required = TRUE
WHERE code = 'BR-6204';

UPDATE masterdata.materials
SET status = 'ACTIVE'
WHERE code = 'PK-GS800';

CREATE SEQUENCE IF NOT EXISTS procurement.purchase_receipt_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE procurement.purchase_receipts (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    purchase_order_id UUID NOT NULL REFERENCES procurement.purchase_orders(id),
    order_number VARCHAR(40) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_code VARCHAR(40) NOT NULL,
    supplier_name VARCHAR(160) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouse.warehouses(id),
    warehouse_code VARCHAR(40) NOT NULL,
    warehouse_name VARCHAR(120) NOT NULL,
    location_id UUID NOT NULL REFERENCES warehouse.storage_locations(id),
    location_code VARCHAR(60) NOT NULL,
    location_name VARCHAR(120) NOT NULL,
    note VARCHAR(500),
    status VARCHAR(32) NOT NULL,
    total_received_quantity NUMERIC(19, 6) NOT NULL,
    accepted_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    rejected_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_receipt_tenant_number UNIQUE (tenant_organization_id, receipt_number),
    CONSTRAINT uk_purchase_receipt_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_purchase_receipt_status CHECK (status IN ('PENDING_INSPECTION', 'PARTIALLY_RECEIVED', 'RECEIVED', 'REJECTED_CLOSED')),
    CONSTRAINT ck_purchase_receipt_quantities CHECK (
        total_received_quantity > 0
        AND accepted_quantity >= 0
        AND rejected_quantity >= 0
        AND accepted_quantity + rejected_quantity <= total_received_quantity
    )
);

CREATE INDEX idx_purchase_receipt_tenant_status
    ON procurement.purchase_receipts (tenant_organization_id, status, created_at DESC);
CREATE INDEX idx_purchase_receipt_order
    ON procurement.purchase_receipts (purchase_order_id, created_at DESC);

CREATE TABLE procurement.purchase_receipt_lines (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    receipt_id UUID NOT NULL REFERENCES procurement.purchase_receipts(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    purchase_order_line_id UUID NOT NULL REFERENCES procurement.purchase_order_lines(id),
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    received_quantity NUMERIC(19, 6) NOT NULL,
    inspection_required BOOLEAN NOT NULL,
    lot_number VARCHAR(80) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    inspection_id UUID,
    accepted_quantity NUMERIC(19, 6),
    rejected_quantity NUMERIC(19, 6),
    inspection_balance_id UUID,
    inspection_movement_id UUID,
    accepted_balance_id UUID,
    accepted_movement_id UUID,
    rejected_balance_id UUID,
    rejected_movement_id UUID,
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_receipt_line_number UNIQUE (receipt_id, line_number),
    CONSTRAINT uk_purchase_receipt_line_inspection UNIQUE (inspection_id),
    CONSTRAINT ck_purchase_receipt_line_status CHECK (status IN ('PENDING_INSPECTION', 'PARTIALLY_RECEIVED', 'RECEIVED', 'REJECTED_CLOSED')),
    CONSTRAINT ck_purchase_receipt_line_received CHECK (received_quantity > 0),
    CONSTRAINT ck_purchase_receipt_line_result_quantities CHECK (
        (status = 'PENDING_INSPECTION' AND accepted_quantity IS NULL AND rejected_quantity IS NULL)
        OR (accepted_quantity IS NOT NULL AND rejected_quantity IS NOT NULL
            AND accepted_quantity >= 0 AND rejected_quantity >= 0
            AND accepted_quantity + rejected_quantity = received_quantity)
    )
);

CREATE INDEX idx_purchase_receipt_line_order_line
    ON procurement.purchase_receipt_lines (purchase_order_line_id, created_at DESC);
CREATE INDEX idx_purchase_receipt_line_status
    ON procurement.purchase_receipt_lines (tenant_organization_id, status, created_at DESC);

CREATE TABLE procurement.purchase_receipt_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    receipt_id UUID NOT NULL REFERENCES procurement.purchase_receipts(id),
    receipt_line_id UUID,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    request_id VARCHAR(120),
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchase_receipt_event_receipt
    ON procurement.purchase_receipt_events (tenant_organization_id, receipt_id, occurred_at DESC);

ALTER TABLE quality.inspections
    ADD COLUMN IF NOT EXISTS supplier_id UUID,
    ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(160);

ALTER TABLE quality.inspections
    DROP CONSTRAINT IF EXISTS ck_quality_inspection_type;
ALTER TABLE quality.inspections
    ADD CONSTRAINT ck_quality_inspection_type CHECK (inspection_type IN ('FINAL', 'INCOMING'));

ALTER TABLE quality.inspections
    DROP CONSTRAINT IF EXISTS ck_quality_inspection_source;
ALTER TABLE quality.inspections
    ADD CONSTRAINT ck_quality_inspection_source CHECK (source_type IN ('PRODUCTION_REPORT', 'PURCHASE_RECEIPT_LINE'));

CREATE INDEX IF NOT EXISTS idx_quality_inspection_tenant_type_status
    ON quality.inspections (tenant_organization_id, inspection_type, status, created_at DESC);

COMMENT ON TABLE procurement.purchase_receipts IS '采购模块拥有的到货收货单；质量结论和库存入库由对应模块写入事实后回写';
COMMENT ON TABLE procurement.purchase_receipt_lines IS '采购收货行，按采购订单行控制超收、IQC 和合格入库数量';
COMMENT ON TABLE procurement.purchase_receipt_events IS '采购到货登记、检验结论回写和入库结算的审计证据';
COMMENT ON COLUMN masterdata.materials.incoming_inspection_required IS '为 TRUE 的采购物料到货后必须创建来料检验任务';
