CREATE SEQUENCE product.routing_number_seq START WITH 1;

CREATE TABLE product.routings (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    routing_number VARCHAR(40) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    material_unit VARCHAR(20) NOT NULL,
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
    CONSTRAINT uk_routing_tenant_number UNIQUE (tenant_organization_id, routing_number),
    CONSTRAINT uk_routing_material_usage_version UNIQUE (tenant_organization_id, material_id, usage_type, version_code),
    CONSTRAINT ck_routing_usage CHECK (usage_type IN ('PRODUCTION')),
    CONSTRAINT ck_routing_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'INACTIVE')),
    CONSTRAINT ck_routing_base_quantity CHECK (base_quantity > 0),
    CONSTRAINT ck_routing_effective_window CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_routing_publish_evidence CHECK (
        (status = 'DRAFT' AND published_by IS NULL AND published_at IS NULL)
        OR (status IN ('PUBLISHED', 'INACTIVE') AND published_by IS NOT NULL AND published_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_routing_one_published_material_usage
    ON product.routings (tenant_organization_id, material_id, usage_type)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_routing_tenant_status_updated
    ON product.routings (tenant_organization_id, status, updated_at DESC);

CREATE TABLE product.routing_operations (
    id UUID PRIMARY KEY,
    routing_id UUID NOT NULL REFERENCES product.routings(id) ON DELETE CASCADE,
    tenant_organization_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    operation_code VARCHAR(40) NOT NULL,
    operation_name VARCHAR(120) NOT NULL,
    work_center_code VARCHAR(40) NOT NULL,
    work_center_name VARCHAR(120) NOT NULL,
    setup_minutes NUMERIC(12, 3) NOT NULL DEFAULT 0,
    run_minutes_per_unit NUMERIC(12, 3) NOT NULL DEFAULT 0,
    queue_minutes NUMERIC(12, 3) NOT NULL DEFAULT 0,
    inspection_required BOOLEAN NOT NULL DEFAULT FALSE,
    instruction_summary VARCHAR(500),
    CONSTRAINT uk_routing_operation_sequence UNIQUE (routing_id, sequence_number),
    CONSTRAINT ck_routing_operation_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_routing_operation_times CHECK (
        setup_minutes >= 0 AND run_minutes_per_unit >= 0 AND queue_minutes >= 0
        AND (setup_minutes > 0 OR run_minutes_per_unit > 0)
    )
);

CREATE INDEX idx_routing_operation_work_center
    ON product.routing_operations (tenant_organization_id, work_center_code);

CREATE TABLE product.routing_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    routing_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    request_id VARCHAR(64),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_routing_event_action CHECK (action IN ('CREATED', 'UPDATED', 'PUBLISHED', 'INACTIVATED'))
);

CREATE INDEX idx_routing_event_entity
    ON product.routing_events (tenant_organization_id, routing_id, occurred_at DESC);

COMMENT ON TABLE product.routings IS '产品模块拥有的受控工艺路线版本表头';
COMMENT ON TABLE product.routing_operations IS '工艺路线版本内按顺序执行的工序快照';
COMMENT ON TABLE product.routing_events IS '工艺路线创建、修改、发布与停用的审计证据';
