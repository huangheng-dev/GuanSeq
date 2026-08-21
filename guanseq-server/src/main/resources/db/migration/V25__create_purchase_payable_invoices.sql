CREATE SEQUENCE finance.payable_invoice_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance.payable_payment_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE finance.payable_invoices (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    invoice_number VARCHAR(40) NOT NULL,
    supplier_invoice_number VARCHAR(80) NOT NULL,
    purchase_order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_code VARCHAR(40) NOT NULL,
    supplier_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    tax_rate NUMERIC(9, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    paid_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payable_invoice_tenant_number UNIQUE (tenant_organization_id, invoice_number),
    CONSTRAINT uk_payable_invoice_tenant_supplier_number UNIQUE
        (tenant_organization_id, supplier_id, supplier_invoice_number),
    CONSTRAINT uk_payable_invoice_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_payable_invoice_status CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID')),
    CONSTRAINT ck_payable_invoice_dates CHECK (due_date >= invoice_date),
    CONSTRAINT ck_payable_invoice_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_payable_invoice_amounts CHECK (
        net_amount >= 0 AND tax_amount >= 0 AND gross_amount = net_amount + tax_amount
        AND paid_amount >= 0 AND paid_amount <= gross_amount)
);

CREATE INDEX idx_payable_invoice_supplier ON finance.payable_invoices
    (tenant_organization_id, supplier_id, due_date, status);
CREATE INDEX idx_payable_invoice_order ON finance.payable_invoices
    (tenant_organization_id, purchase_order_id, created_at DESC);

CREATE TABLE finance.payable_invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES finance.payable_invoices(id) ON DELETE CASCADE,
    purchase_order_line_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    invoice_quantity NUMERIC(18, 4) NOT NULL,
    unit_price NUMERIC(18, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    CONSTRAINT uk_payable_invoice_line_order_line UNIQUE (invoice_id, purchase_order_line_id),
    CONSTRAINT ck_payable_invoice_line_quantity CHECK (invoice_quantity > 0),
    CONSTRAINT ck_payable_invoice_line_amounts CHECK (
        unit_price >= 0 AND net_amount >= 0 AND tax_amount >= 0 AND gross_amount = net_amount + tax_amount)
);

CREATE INDEX idx_payable_invoice_line_order_line ON finance.payable_invoice_lines (purchase_order_line_id);

CREATE TABLE finance.payable_payments (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    payment_number VARCHAR(40) NOT NULL,
    invoice_id UUID NOT NULL REFERENCES finance.payable_invoices(id),
    invoice_number VARCHAR(40) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_code VARCHAR(40) NOT NULL,
    supplier_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(24) NOT NULL,
    bank_reference VARCHAR(120),
    note VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payable_payment_tenant_number UNIQUE (tenant_organization_id, payment_number),
    CONSTRAINT uk_payable_payment_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_payable_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payable_payment_method CHECK (payment_method IN ('BANK_TRANSFER', 'CASH', 'BILL', 'OTHER')),
    CONSTRAINT ck_payable_payment_status CHECK (status IN ('POSTED'))
);

CREATE INDEX idx_payable_payment_invoice ON finance.payable_payments
    (tenant_organization_id, invoice_id, payment_date DESC);

CREATE TABLE finance.payable_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    invoice_id UUID NOT NULL REFERENCES finance.payable_invoices(id),
    payment_id UUID REFERENCES finance.payable_payments(id),
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payable_event_invoice ON finance.payable_events
    (tenant_organization_id, invoice_id, occurred_at DESC);

COMMENT ON TABLE finance.payable_invoices IS '以采购订单合格收货数量为依据形成的应付发票业务事实';
COMMENT ON TABLE finance.payable_payments IS '已过账付款及其单张应付发票核销事实';
COMMENT ON TABLE finance.payable_events IS '应付开票与付款核销审计事件';
