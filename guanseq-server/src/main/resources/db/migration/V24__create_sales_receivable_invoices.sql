CREATE SEQUENCE finance.receivable_invoice_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance.receivable_receipt_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE finance.receivable_invoices (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    invoice_number VARCHAR(40) NOT NULL,
    sales_order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    tax_rate NUMERIC(9, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    received_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receivable_invoice_tenant_number UNIQUE (tenant_organization_id, invoice_number),
    CONSTRAINT uk_receivable_invoice_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_receivable_invoice_status CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID')),
    CONSTRAINT ck_receivable_invoice_dates CHECK (due_date >= invoice_date),
    CONSTRAINT ck_receivable_invoice_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_receivable_invoice_amounts CHECK (
        net_amount >= 0 AND tax_amount >= 0 AND gross_amount = net_amount + tax_amount
        AND received_amount >= 0 AND received_amount <= gross_amount)
);

CREATE INDEX idx_receivable_invoice_customer ON finance.receivable_invoices
    (tenant_organization_id, customer_id, due_date, status);
CREATE INDEX idx_receivable_invoice_order ON finance.receivable_invoices
    (tenant_organization_id, sales_order_id, created_at DESC);

CREATE TABLE finance.receivable_invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES finance.receivable_invoices(id) ON DELETE CASCADE,
    sales_order_line_id UUID NOT NULL,
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
    CONSTRAINT uk_receivable_invoice_line_order_line UNIQUE (invoice_id, sales_order_line_id),
    CONSTRAINT ck_receivable_invoice_line_quantity CHECK (invoice_quantity > 0),
    CONSTRAINT ck_receivable_invoice_line_amounts CHECK (
        unit_price >= 0 AND net_amount >= 0 AND tax_amount >= 0 AND gross_amount = net_amount + tax_amount)
);

CREATE INDEX idx_receivable_invoice_line_order_line ON finance.receivable_invoice_lines (sales_order_line_id);

CREATE TABLE finance.receivable_receipts (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    invoice_id UUID NOT NULL REFERENCES finance.receivable_invoices(id),
    invoice_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    receipt_date DATE NOT NULL,
    payment_method VARCHAR(24) NOT NULL,
    bank_reference VARCHAR(120),
    note VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receivable_receipt_tenant_number UNIQUE (tenant_organization_id, receipt_number),
    CONSTRAINT uk_receivable_receipt_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_receivable_receipt_amount CHECK (amount > 0),
    CONSTRAINT ck_receivable_receipt_method CHECK (payment_method IN ('BANK_TRANSFER', 'CASH', 'BILL', 'OTHER')),
    CONSTRAINT ck_receivable_receipt_status CHECK (status IN ('POSTED'))
);

CREATE INDEX idx_receivable_receipt_invoice ON finance.receivable_receipts
    (tenant_organization_id, invoice_id, receipt_date DESC);

CREATE TABLE finance.receivable_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    invoice_id UUID NOT NULL REFERENCES finance.receivable_invoices(id),
    receipt_id UUID REFERENCES finance.receivable_receipts(id),
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_receivable_event_invoice ON finance.receivable_events
    (tenant_organization_id, invoice_id, occurred_at DESC);

COMMENT ON TABLE finance.receivable_invoices IS '以已发货销售订单为依据形成的应收发票业务事实';
COMMENT ON TABLE finance.receivable_receipts IS '已过账收款及其单张应收发票核销事实';
COMMENT ON TABLE finance.receivable_events IS '开票与收款核销审计事件';
