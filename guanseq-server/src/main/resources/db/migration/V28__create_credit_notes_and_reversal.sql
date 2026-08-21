-- 红字发票与反核销
-- 依赖 V24（应收）、V25（应付）。本迁移不删除任何已过账事实，只新增反向业务单据，
-- 并扩展现有状态机以支持红冲后的待退余额和核销的反向撤销。

CREATE SEQUENCE finance.receivable_credit_note_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance.receivable_reversal_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance.payable_credit_note_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance.payable_reversal_number_seq START WITH 1 INCREMENT BY 1;

-- ============================================================
-- 应收红字发票
-- ============================================================

CREATE TABLE finance.receivable_credit_notes (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    credit_note_number VARCHAR(40) NOT NULL,
    original_invoice_id UUID NOT NULL REFERENCES finance.receivable_invoices(id),
    original_invoice_number VARCHAR(40) NOT NULL,
    sales_order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    tax_notice_number VARCHAR(80),
    credit_note_date DATE NOT NULL,
    due_date DATE NOT NULL,
    tax_rate NUMERIC(9, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receivable_credit_note_tenant_number UNIQUE (tenant_organization_id, credit_note_number),
    CONSTRAINT uk_receivable_credit_note_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_receivable_credit_note_status CHECK (status IN ('POSTED')),
    CONSTRAINT ck_receivable_credit_note_dates CHECK (due_date >= credit_note_date),
    CONSTRAINT ck_receivable_credit_note_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_receivable_credit_note_amounts CHECK (
        net_amount <= 0
        AND tax_amount <= 0
        AND gross_amount = net_amount + tax_amount
        AND gross_amount < 0
    ),
    CONSTRAINT ck_receivable_credit_note_reason CHECK (char_length(trim(reason)) >= 4)
);

CREATE INDEX idx_receivable_credit_note_original
    ON finance.receivable_credit_notes (tenant_organization_id, original_invoice_id, credit_note_date DESC);
CREATE INDEX idx_receivable_credit_note_customer
    ON finance.receivable_credit_notes (tenant_organization_id, customer_id, credit_note_date DESC);

CREATE TABLE finance.receivable_credit_note_lines (
    id UUID PRIMARY KEY,
    credit_note_id UUID NOT NULL REFERENCES finance.receivable_credit_notes(id) ON DELETE CASCADE,
    original_invoice_line_id UUID NOT NULL REFERENCES finance.receivable_invoice_lines(id),
    sales_order_line_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    credit_quantity NUMERIC(18, 4) NOT NULL,
    unit_price NUMERIC(18, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    CONSTRAINT uk_receivable_credit_note_line_original UNIQUE (credit_note_id, original_invoice_line_id),
    CONSTRAINT ck_receivable_credit_note_line_quantity CHECK (credit_quantity > 0),
    CONSTRAINT ck_receivable_credit_note_line_amounts CHECK (
        unit_price >= 0
        AND net_amount <= 0
        AND tax_amount <= 0
        AND gross_amount = net_amount + tax_amount
        AND gross_amount < 0
    )
);

CREATE INDEX idx_receivable_credit_note_line_order_line
    ON finance.receivable_credit_note_lines (sales_order_line_id);

-- ============================================================
-- 应付红字发票
-- ============================================================

CREATE TABLE finance.payable_credit_notes (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    credit_note_number VARCHAR(40) NOT NULL,
    original_invoice_id UUID NOT NULL REFERENCES finance.payable_invoices(id),
    original_invoice_number VARCHAR(40) NOT NULL,
    supplier_credit_note_number VARCHAR(80),
    purchase_order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_code VARCHAR(40) NOT NULL,
    supplier_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    tax_notice_number VARCHAR(80),
    credit_note_date DATE NOT NULL,
    due_date DATE NOT NULL,
    tax_rate NUMERIC(9, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payable_credit_note_tenant_number UNIQUE (tenant_organization_id, credit_note_number),
    CONSTRAINT uk_payable_credit_note_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_payable_credit_note_status CHECK (status IN ('POSTED')),
    CONSTRAINT ck_payable_credit_note_dates CHECK (due_date >= credit_note_date),
    CONSTRAINT ck_payable_credit_note_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_payable_credit_note_amounts CHECK (
        net_amount <= 0
        AND tax_amount <= 0
        AND gross_amount = net_amount + tax_amount
        AND gross_amount < 0
    ),
    CONSTRAINT ck_payable_credit_note_reason CHECK (char_length(trim(reason)) >= 4)
);

CREATE INDEX idx_payable_credit_note_original
    ON finance.payable_credit_notes (tenant_organization_id, original_invoice_id, credit_note_date DESC);
CREATE INDEX idx_payable_credit_note_supplier
    ON finance.payable_credit_notes (tenant_organization_id, supplier_id, credit_note_date DESC);

CREATE TABLE finance.payable_credit_note_lines (
    id UUID PRIMARY KEY,
    credit_note_id UUID NOT NULL REFERENCES finance.payable_credit_notes(id) ON DELETE CASCADE,
    original_invoice_line_id UUID NOT NULL REFERENCES finance.payable_invoice_lines(id),
    purchase_order_line_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    credit_quantity NUMERIC(18, 4) NOT NULL,
    unit_price NUMERIC(18, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    gross_amount NUMERIC(18, 2) NOT NULL,
    CONSTRAINT uk_payable_credit_note_line_original UNIQUE (credit_note_id, original_invoice_line_id),
    CONSTRAINT ck_payable_credit_note_line_quantity CHECK (credit_quantity > 0),
    CONSTRAINT ck_payable_credit_note_line_amounts CHECK (
        unit_price >= 0
        AND net_amount <= 0
        AND tax_amount <= 0
        AND gross_amount = net_amount + tax_amount
        AND gross_amount < 0
    )
);

CREATE INDEX idx_payable_credit_note_line_order_line
    ON finance.payable_credit_note_lines (purchase_order_line_id);

-- ============================================================
-- 反核销记录
-- ============================================================

CREATE TABLE finance.receivable_reversals (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    reversal_number VARCHAR(40) NOT NULL,
    receipt_id UUID NOT NULL REFERENCES finance.receivable_receipts(id),
    receipt_number VARCHAR(40) NOT NULL,
    invoice_id UUID NOT NULL REFERENCES finance.receivable_invoices(id),
    invoice_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    reversal_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receivable_reversal_tenant_number UNIQUE (tenant_organization_id, reversal_number),
    CONSTRAINT uk_receivable_reversal_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT uk_receivable_reversal_receipt UNIQUE (receipt_id),
    CONSTRAINT ck_receivable_reversal_status CHECK (status IN ('POSTED')),
    CONSTRAINT ck_receivable_reversal_amount CHECK (amount > 0),
    CONSTRAINT ck_receivable_reversal_reason CHECK (char_length(trim(reason)) >= 4)
);

CREATE INDEX idx_receivable_reversal_invoice
    ON finance.receivable_reversals (tenant_organization_id, invoice_id, reversal_date DESC);

CREATE TABLE finance.payable_reversals (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    reversal_number VARCHAR(40) NOT NULL,
    payment_id UUID NOT NULL REFERENCES finance.payable_payments(id),
    payment_number VARCHAR(40) NOT NULL,
    invoice_id UUID NOT NULL REFERENCES finance.payable_invoices(id),
    invoice_number VARCHAR(40) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_code VARCHAR(40) NOT NULL,
    supplier_name VARCHAR(160) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    reversal_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payable_reversal_tenant_number UNIQUE (tenant_organization_id, reversal_number),
    CONSTRAINT uk_payable_reversal_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT uk_payable_reversal_payment UNIQUE (payment_id),
    CONSTRAINT ck_payable_reversal_status CHECK (status IN ('POSTED')),
    CONSTRAINT ck_payable_reversal_amount CHECK (amount > 0),
    CONSTRAINT ck_payable_reversal_reason CHECK (char_length(trim(reason)) >= 4)
);

CREATE INDEX idx_payable_reversal_invoice
    ON finance.payable_reversals (tenant_organization_id, invoice_id, reversal_date DESC);

-- ============================================================
-- 扩展现有表：状态机与待退余额
-- ============================================================

ALTER TABLE finance.receivable_invoices
    ADD COLUMN credit_balance NUMERIC(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE finance.payable_invoices
    ADD COLUMN credit_balance NUMERIC(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE finance.receivable_invoices
    DROP CONSTRAINT ck_receivable_invoice_status;
ALTER TABLE finance.receivable_invoices
    ADD CONSTRAINT ck_receivable_invoice_status
    CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERPAID'));

ALTER TABLE finance.payable_invoices
    DROP CONSTRAINT ck_payable_invoice_status;
ALTER TABLE finance.payable_invoices
    ADD CONSTRAINT ck_payable_invoice_status
    CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERPAID'));

ALTER TABLE finance.receivable_invoices
    ADD CONSTRAINT ck_receivable_invoice_credit_balance CHECK (credit_balance >= 0);
ALTER TABLE finance.payable_invoices
    ADD CONSTRAINT ck_payable_invoice_credit_balance CHECK (credit_balance >= 0);

ALTER TABLE finance.receivable_receipts
    ADD COLUMN reversal_id UUID REFERENCES finance.receivable_reversals(id);
ALTER TABLE finance.receivable_receipts
    ADD COLUMN reversed_at TIMESTAMPTZ;

ALTER TABLE finance.payable_payments
    ADD COLUMN reversal_id UUID REFERENCES finance.payable_reversals(id);
ALTER TABLE finance.payable_payments
    ADD COLUMN reversed_at TIMESTAMPTZ;

ALTER TABLE finance.receivable_receipts
    DROP CONSTRAINT ck_receivable_receipt_status;
ALTER TABLE finance.receivable_receipts
    ADD CONSTRAINT ck_receivable_receipt_status
    CHECK (status IN ('POSTED', 'REVERSED'));

ALTER TABLE finance.payable_payments
    DROP CONSTRAINT ck_payable_payment_status;
ALTER TABLE finance.payable_payments
    ADD CONSTRAINT ck_payable_payment_status
    CHECK (status IN ('POSTED', 'REVERSED'));

COMMENT ON TABLE finance.receivable_credit_notes IS '对已过账应收发票按行或整票开具的红字发票，金额为负，不可修改或删除';
COMMENT ON TABLE finance.receivable_credit_note_lines IS '应收红字发票行，引用原蓝字发票行，记录红冲数量与单价';
COMMENT ON TABLE finance.payable_credit_notes IS '对已过账应付发票按行或整票开具的红字发票，金额为负，不可修改或删除';
COMMENT ON TABLE finance.payable_credit_note_lines IS '应付红字发票行，引用原蓝字发票行，记录红冲数量与单价';
COMMENT ON TABLE finance.receivable_reversals IS '收款核销反操作；不删除原收款，将其状态置为 REVERSED 并恢复应收余额';
COMMENT ON TABLE finance.payable_reversals IS '付款核销反操作；不删除原付款，将其状态置为 REVERSED 并恢复应付余额';
COMMENT ON COLUMN finance.receivable_invoices.credit_balance IS '红冲后形成的应退客户余额，由后续退款单切片消费';
COMMENT ON COLUMN finance.payable_invoices.credit_balance IS '红冲后形成的应退供应商余额，由后续退款单切片消费';
