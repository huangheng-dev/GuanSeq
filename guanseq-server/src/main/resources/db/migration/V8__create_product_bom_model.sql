CREATE SCHEMA product;

COMMENT ON SCHEMA masterdata IS '客户与物料主数据事实';
COMMENT ON SCHEMA product IS '产品结构、BOM 版本、工艺路线与工程变更事实';

CREATE SEQUENCE product.bom_number_seq START WITH 1;

CREATE TABLE product.boms (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    bom_number VARCHAR(40) NOT NULL,
    parent_material_id UUID NOT NULL,
    parent_material_code VARCHAR(60) NOT NULL,
    parent_material_name VARCHAR(160) NOT NULL,
    parent_material_specification VARCHAR(240),
    parent_unit VARCHAR(20) NOT NULL,
    usage_type VARCHAR(24) NOT NULL DEFAULT 'PRODUCTION',
    version_code VARCHAR(32) NOT NULL,
    base_quantity NUMERIC(19, 6) NOT NULL DEFAULT 1,
    effective_from DATE NOT NULL,
    effective_to DATE,
    owner VARCHAR(80) NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by UUID,
    published_at TIMESTAMPTZ,
    CONSTRAINT uk_bom_tenant_number UNIQUE (tenant_organization_id, bom_number),
    CONSTRAINT uk_bom_parent_usage_version UNIQUE (tenant_organization_id, parent_material_id, usage_type, version_code),
    CONSTRAINT ck_bom_usage CHECK (usage_type IN ('PRODUCTION')),
    CONSTRAINT ck_bom_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'INACTIVE')),
    CONSTRAINT ck_bom_base_quantity CHECK (base_quantity > 0),
    CONSTRAINT ck_bom_effective_window CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_bom_publish_evidence CHECK (
        (status = 'DRAFT' AND published_by IS NULL AND published_at IS NULL)
        OR (status IN ('PUBLISHED', 'INACTIVE') AND published_by IS NOT NULL AND published_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_bom_one_published_parent_usage
    ON product.boms (tenant_organization_id, parent_material_id, usage_type)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_bom_tenant_status_updated
    ON product.boms (tenant_organization_id, status, updated_at DESC);

CREATE TABLE product.bom_lines (
    id UUID PRIMARY KEY,
    bom_id UUID NOT NULL REFERENCES product.boms(id) ON DELETE CASCADE,
    tenant_organization_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    component_material_id UUID NOT NULL,
    component_material_code VARCHAR(60) NOT NULL,
    component_material_name VARCHAR(160) NOT NULL,
    component_material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    scrap_rate NUMERIC(7, 6) NOT NULL DEFAULT 0,
    note VARCHAR(240),
    CONSTRAINT uk_bom_line_number UNIQUE (bom_id, line_number),
    CONSTRAINT uk_bom_component UNIQUE (bom_id, component_material_id),
    CONSTRAINT ck_bom_line_number CHECK (line_number > 0),
    CONSTRAINT ck_bom_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_bom_line_scrap_rate CHECK (scrap_rate >= 0 AND scrap_rate < 1)
);

CREATE INDEX idx_bom_line_component
    ON product.bom_lines (tenant_organization_id, component_material_id);

CREATE TABLE product.bom_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    bom_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    request_id VARCHAR(64),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bom_event_action CHECK (action IN ('CREATED', 'UPDATED', 'PUBLISHED', 'INACTIVATED'))
);

CREATE INDEX idx_bom_event_entity
    ON product.bom_events (tenant_organization_id, bom_id, occurred_at DESC);

COMMENT ON TABLE product.boms IS '产品模块拥有的受控 BOM 版本表头';
COMMENT ON TABLE product.bom_lines IS 'BOM 版本内不可脱离表头存在的组件明细';
COMMENT ON TABLE product.bom_events IS 'BOM 创建、修改、发布与停用的审计证据';
