-- 会计期间与关账
-- 为每个租户提供月度会计期间，关账后阻止财务写入。
-- 本迁移只建表并为开发租户预生成 2024-2027 共 48 个 OPEN 期间。
-- 后续年度由应用层在首次访问时自动补建。

CREATE TABLE finance.accounting_periods (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    fiscal_year INTEGER NOT NULL,
    fiscal_period INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    reopened_at TIMESTAMPTZ,
    reopened_by UUID,
    reopen_reason VARCHAR(500),
    request_id VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_accounting_period_tenant_year_period
        UNIQUE (tenant_organization_id, fiscal_year, fiscal_period),
    CONSTRAINT ck_accounting_period_year
        CHECK (fiscal_year BETWEEN 2020 AND 2099),
    CONSTRAINT ck_accounting_period_month
        CHECK (fiscal_period BETWEEN 1 AND 12),
    CONSTRAINT ck_accounting_period_status
        CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_accounting_period_close_audit
        CHECK (
            (status = 'OPEN' AND closed_at IS NULL)
            OR (status = 'CLOSED' AND closed_at IS NOT NULL)
        )
);

CREATE INDEX idx_accounting_period_tenant_year
    ON finance.accounting_periods (tenant_organization_id, fiscal_year, fiscal_period);

-- 为开发租户预生成 2024-2027 共 48 个月度期间
INSERT INTO finance.accounting_periods (
    id, tenant_organization_id, workspace_id,
    fiscal_year, fiscal_period, status,
    created_by, updated_by
)
SELECT
    gen_random_uuid(),
    '00000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000101',
    y.year,
    m.month,
    'OPEN',
    '20000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001'
FROM generate_series(2024, 2027) AS y(year)
CROSS JOIN generate_series(1, 12) AS m(month)
ON CONFLICT (tenant_organization_id, fiscal_year, fiscal_period) DO NOTHING;
