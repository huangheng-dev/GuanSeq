CREATE TABLE masterdata.customers (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    customer_type VARCHAR(24) NOT NULL,
    credit_level VARCHAR(8) NOT NULL,
    contact_name VARCHAR(80),
    contact_phone VARCHAR(40),
    owner VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_tenant_code UNIQUE (tenant_organization_id, code),
    CONSTRAINT ck_customer_type CHECK (customer_type IN ('ENTERPRISE', 'DISTRIBUTOR', 'INTERNAL')),
    CONSTRAINT ck_customer_credit_level CHECK (credit_level IN ('A', 'B', 'C')),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_customer_tenant_status
    ON masterdata.customers (tenant_organization_id, status, updated_at DESC);

CREATE TABLE masterdata.materials (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    specification VARCHAR(240),
    material_type VARCHAR(32) NOT NULL,
    base_unit VARCHAR(20) NOT NULL,
    procurement_type VARCHAR(24) NOT NULL,
    owner VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_material_tenant_code UNIQUE (tenant_organization_id, code),
    CONSTRAINT ck_material_type CHECK (material_type IN ('FINISHED_GOOD', 'SEMI_FINISHED', 'RAW_MATERIAL', 'PACKAGING', 'CONSUMABLE')),
    CONSTRAINT ck_material_procurement_type CHECK (procurement_type IN ('MAKE', 'BUY', 'OUTSOURCE')),
    CONSTRAINT ck_material_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_material_tenant_status
    ON masterdata.materials (tenant_organization_id, status, updated_at DESC);

CREATE TABLE masterdata.change_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    request_id VARCHAR(64),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_masterdata_change_entity
    ON masterdata.change_events (tenant_organization_id, entity_type, entity_id, occurred_at DESC);

COMMENT ON TABLE masterdata.customers IS '按租户组织隔离的客户主数据事实';
COMMENT ON TABLE masterdata.materials IS '按租户组织隔离的物料主数据事实';
COMMENT ON TABLE masterdata.change_events IS '客户与物料创建、修改、停用和恢复的审计证据';
