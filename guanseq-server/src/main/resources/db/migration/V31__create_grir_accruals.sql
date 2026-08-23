-- 暂估应付（GR/IR）
-- 面向中小企业：货到票未到时，月末按采购订单单价×已收货未开票数量暂估入账，
-- 下期运行时自动冲回上期暂估（月初冲回法/月度滚调），发票到达时在应付发票模块正常登记，
-- 下一次暂估运行时自然减少余额。不做逐笔收货勾稽，不生成总账凭证。

CREATE SEQUENCE finance.grir_accrual_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE finance.grir_accruals (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    accrual_number VARCHAR(40) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    fiscal_period INTEGER NOT NULL,
    accrual_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    total_net_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    reversed_by_accrual_id UUID,
    reversal_date DATE,
    reversal_reason VARCHAR(500),
    note VARCHAR(500),
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_grir_accrual_tenant_period UNIQUE (tenant_organization_id, fiscal_year, fiscal_period),
    CONSTRAINT uk_grir_accrual_tenant_number UNIQUE (tenant_organization_id, accrual_number),
    CONSTRAINT uk_grir_accrual_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_grir_accrual_period CHECK (fiscal_period BETWEEN 1 AND 12),
    CONSTRAINT ck_grir_accrual_status CHECK (status IN ('POSTED', 'REVERSED')),
    CONSTRAINT ck_grir_accrual_amount CHECK (total_net_amount >= 0),
    CONSTRAINT fk_grir_accrual_reversed_by FOREIGN KEY (reversed_by_accrual_id)
        REFERENCES finance.grir_accruals(id)
);

CREATE INDEX idx_grir_accrual_tenant_status
    ON finance.grir_accruals (tenant_organization_id, status, accrual_date DESC);

CREATE TABLE finance.grir_accrual_lines (
    id UUID PRIMARY KEY,
    accrual_id UUID NOT NULL REFERENCES finance.grir_accruals(id) ON DELETE CASCADE,
    tenant_organization_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_code VARCHAR(40) NOT NULL,
    supplier_name VARCHAR(160) NOT NULL,
    purchase_order_line_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    received_quantity NUMERIC(18, 4) NOT NULL,
    invoiced_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0,
    accrued_quantity NUMERIC(18, 4) NOT NULL,
    unit_price NUMERIC(18, 6) NOT NULL,
    net_amount NUMERIC(18, 2) NOT NULL,
    CONSTRAINT uk_grir_accrual_line UNIQUE (accrual_id, purchase_order_line_id),
    CONSTRAINT ck_grir_accrual_line_qty CHECK (
        received_quantity >= 0 AND invoiced_quantity >= 0 AND accrued_quantity >= 0
        AND accrued_quantity = received_quantity - invoiced_quantity),
    CONSTRAINT ck_grir_accrual_line_amount CHECK (unit_price >= 0 AND net_amount >= 0)
);

CREATE INDEX idx_grir_accrual_line_order
    ON finance.grir_accrual_lines (tenant_organization_id, purchase_order_line_id);
CREATE INDEX idx_grir_accrual_line_po
    ON finance.grir_accrual_lines (tenant_organization_id, purchase_order_id);

CREATE TABLE finance.grir_accrual_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    accrual_id UUID NOT NULL REFERENCES finance.grir_accruals(id),
    action VARCHAR(40) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_grir_accrual_event_action CHECK (action IN ('ACCRUE', 'REVERSE_PRIOR', 'MANUAL_REVERSE'))
);

CREATE INDEX idx_grir_accrual_event_accrual
    ON finance.grir_accrual_events (tenant_organization_id, accrual_id, occurred_at DESC);

COMMENT ON TABLE finance.grir_accruals IS '月末暂估应付（GR/IR）单头，按租户+会计年月唯一，月初冲回法';
COMMENT ON TABLE finance.grir_accrual_lines IS '暂估应付行，按采购订单行汇总已收货未开票数量×订单单价';
COMMENT ON TABLE finance.grir_accrual_events IS '暂估应付审计事件：暂估、自动冲回上期、手动冲回';
