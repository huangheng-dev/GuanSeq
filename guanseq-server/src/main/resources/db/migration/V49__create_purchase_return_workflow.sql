ALTER TABLE procurement.purchase_order_lines
    ADD COLUMN returned_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0;

ALTER TABLE procurement.purchase_order_lines
    ADD CONSTRAINT ck_purchase_order_line_returned_quantity
    CHECK (returned_quantity >= 0 AND returned_quantity <= received_quantity
        AND received_quantity - returned_quantity <= ordered_quantity);

ALTER TABLE finance.payable_invoices
    ADD COLUMN purchase_return_impact_status VARCHAR(24) NOT NULL DEFAULT 'NONE';

ALTER TABLE finance.payable_invoices
    ADD CONSTRAINT ck_payable_invoice_purchase_return_impact
    CHECK (purchase_return_impact_status IN ('NONE', 'REVIEW_REQUIRED'));

CREATE SEQUENCE procurement.purchase_return_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE procurement.purchase_returns (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    return_number VARCHAR(40) NOT NULL,
    purchase_order_id UUID NOT NULL REFERENCES procurement.purchase_orders(id),
    order_number VARCHAR(40) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_code VARCHAR(40) NOT NULL,
    supplier_name VARCHAR(160) NOT NULL,
    return_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    note VARCHAR(500),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_SHIPMENT',
    total_return_quantity NUMERIC(19, 6) NOT NULL,
    accepted_return_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    blocked_return_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_return_tenant_number UNIQUE (tenant_organization_id, return_number),
    CONSTRAINT uk_purchase_return_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_purchase_return_status CHECK (status IN ('PENDING_SHIPMENT', 'SHIPPED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_purchase_return_quantities CHECK (total_return_quantity > 0
        AND accepted_return_quantity >= 0 AND blocked_return_quantity >= 0
        AND accepted_return_quantity + blocked_return_quantity = total_return_quantity),
    CONSTRAINT ck_purchase_return_reason CHECK (char_length(trim(reason)) >= 4)
);

CREATE INDEX idx_purchase_return_tenant_status
    ON procurement.purchase_returns (tenant_organization_id, status, created_at DESC);
CREATE INDEX idx_purchase_return_order
    ON procurement.purchase_returns (tenant_organization_id, purchase_order_id, created_at DESC);

CREATE TABLE procurement.purchase_return_lines (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    return_id UUID NOT NULL REFERENCES procurement.purchase_returns(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    purchase_receipt_line_id UUID NOT NULL REFERENCES procurement.purchase_receipt_lines(id),
    purchase_order_line_id UUID NOT NULL REFERENCES procurement.purchase_order_lines(id),
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    quality_status VARCHAR(24) NOT NULL,
    authorized_quantity NUMERIC(19, 6) NOT NULL,
    shipped_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    stock_balance_id UUID NOT NULL,
    stock_movement_id UUID,
    warehouse_code VARCHAR(40),
    location_code VARCHAR(60),
    lot_number VARCHAR(80) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_return_line_number UNIQUE (return_id, line_number),
    CONSTRAINT uk_purchase_return_line_source UNIQUE (return_id, purchase_receipt_line_id, quality_status),
    CONSTRAINT ck_purchase_return_line_quality CHECK (quality_status IN ('AVAILABLE', 'BLOCKED')),
    CONSTRAINT ck_purchase_return_line_quantities CHECK (authorized_quantity > 0
        AND shipped_quantity >= 0 AND shipped_quantity <= authorized_quantity)
);

CREATE INDEX idx_purchase_return_line_receipt
    ON procurement.purchase_return_lines (tenant_organization_id, purchase_receipt_line_id, quality_status);

CREATE TABLE procurement.purchase_return_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    return_id UUID NOT NULL REFERENCES procurement.purchase_returns(id),
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchase_return_event_return
    ON procurement.purchase_return_events (tenant_organization_id, return_id, occurred_at DESC);

COMMENT ON TABLE procurement.purchase_returns IS '供应商退货授权、退回出库、取消与冲回的采购责任事实';
COMMENT ON TABLE procurement.purchase_return_lines IS '按原采购收货行、质量状态和精确库存余额记录的退货明细';
COMMENT ON COLUMN procurement.purchase_order_lines.returned_quantity IS '已合格入库后退回供应商的累计数量；净合格收货为 received_quantity-returned_quantity';
COMMENT ON COLUMN finance.payable_invoices.purchase_return_impact_status IS '采购退货导致净开票数量可能超过净合格收货时要求财务复核';
