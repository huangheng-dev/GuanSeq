-- V32: 预收预付（Advance Receipts & Payments）
-- 预收（客户先付款后开票）和预付（供应商先收款后供货）的余额池管理。

CREATE SEQUENCE IF NOT EXISTS finance.advance_number_seq START 1;

CREATE TABLE finance.advances (
    id                          UUID PRIMARY KEY,
    tenant_organization_id      UUID NOT NULL,
    owning_organization_id      UUID NOT NULL,
    workspace_id                UUID NOT NULL,
    advance_number              VARCHAR(32) NOT NULL,
    type                        VARCHAR(16) NOT NULL,
    party_type                  VARCHAR(16) NOT NULL,
    party_id                    UUID NOT NULL,
    party_code                  VARCHAR(64) NOT NULL,
    party_name                  VARCHAR(256) NOT NULL,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'CNY',
    advance_date                DATE NOT NULL,
    total_amount                NUMERIC(18,2) NOT NULL,
    applied_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    refunded_amount             NUMERIC(18,2) NOT NULL DEFAULT 0,
    status                      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    note                        TEXT,
    request_id                  VARCHAR(120) NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    created_by                  UUID NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_advance_number UNIQUE (tenant_organization_id, advance_number),
    CONSTRAINT uq_advance_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT chk_advance_type CHECK (type IN ('RECEIVABLE','PAYABLE')),
    CONSTRAINT chk_advance_party_type CHECK (party_type IN ('CUSTOMER','SUPPLIER')),
    CONSTRAINT chk_advance_total_positive CHECK (total_amount > 0),
    CONSTRAINT chk_advance_applied_nonneg CHECK (applied_amount >= 0),
    CONSTRAINT chk_advance_refunded_nonneg CHECK (refunded_amount >= 0),
    CONSTRAINT chk_advance_balance CHECK (applied_amount + refunded_amount <= total_amount),
    CONSTRAINT chk_advance_status CHECK (status IN ('OPEN','PARTIALLY_USED','CLOSED'))
);

CREATE INDEX idx_advances_tenant_party ON finance.advances (tenant_organization_id, type, party_id);
CREATE INDEX idx_advances_tenant_status ON finance.advances (tenant_organization_id, type, status);
CREATE INDEX idx_advances_tenant_date ON finance.advances (tenant_organization_id, advance_date DESC);

CREATE TABLE finance.advance_applications (
    id                          UUID PRIMARY KEY,
    tenant_organization_id      UUID NOT NULL,
    workspace_id                UUID NOT NULL,
    advance_id                  UUID NOT NULL REFERENCES finance.advances(id),
    invoice_id                  UUID NOT NULL,
    invoice_number              VARCHAR(32) NOT NULL,
    applied_amount              NUMERIC(18,2) NOT NULL,
    application_date            DATE NOT NULL,
    request_id                  VARCHAR(120) NOT NULL,
    created_by                  UUID NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_advance_app_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT chk_advance_app_positive CHECK (applied_amount > 0)
);

CREATE INDEX idx_advance_app_advance ON finance.advance_applications (advance_id);
CREATE INDEX idx_advance_app_invoice ON finance.advance_applications (invoice_id);

CREATE TABLE finance.advance_refunds (
    id                          UUID PRIMARY KEY,
    tenant_organization_id      UUID NOT NULL,
    workspace_id                UUID NOT NULL,
    advance_id                  UUID NOT NULL REFERENCES finance.advances(id),
    refund_amount               NUMERIC(18,2) NOT NULL,
    refund_date                 DATE NOT NULL,
    reason                      VARCHAR(500) NOT NULL,
    request_id                  VARCHAR(120) NOT NULL,
    created_by                  UUID NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_advance_refund_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT chk_advance_refund_positive CHECK (refund_amount > 0)
);

CREATE INDEX idx_advance_refund_advance ON finance.advance_refunds (advance_id);

CREATE TABLE finance.advance_events (
    id                          UUID PRIMARY KEY,
    tenant_organization_id      UUID NOT NULL,
    workspace_id                UUID NOT NULL,
    actor_user_id               UUID NOT NULL,
    advance_id                  UUID NOT NULL,
    application_id              UUID,
    refund_id                   UUID,
    action                      VARCHAR(32) NOT NULL,
    from_status                 VARCHAR(20),
    to_status                   VARCHAR(20),
    request_id                  VARCHAR(120),
    details                     JSONB,
    occurred_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_advance_event_action CHECK (action IN ('REGISTER','APPLY','REFUND'))
);

CREATE INDEX idx_advance_events_advance ON finance.advance_events (advance_id, occurred_at);
