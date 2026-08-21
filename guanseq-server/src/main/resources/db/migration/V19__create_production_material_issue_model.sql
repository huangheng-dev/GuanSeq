CREATE SEQUENCE production.material_issue_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE production.material_return_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE production.material_issues (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    issue_number VARCHAR(40) NOT NULL,
    production_order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    planned_quantity NUMERIC(19, 6) NOT NULL,
    warehouse_id UUID NOT NULL,
    warehouse_code VARCHAR(40) NOT NULL,
    warehouse_name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL,
    cancellation_reason VARCHAR(500),
    request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_material_issue_tenant_number UNIQUE (tenant_organization_id, issue_number),
    CONSTRAINT uk_material_issue_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_material_issue_status CHECK (status IN ('DRAFT', 'PARTIAL', 'ISSUED', 'CANCELLED')),
    CONSTRAINT ck_material_issue_planned_quantity CHECK (planned_quantity > 0)
);

CREATE UNIQUE INDEX uk_material_issue_active_order ON production.material_issues (production_order_id) WHERE status <> 'CANCELLED';
CREATE INDEX idx_material_issue_tenant_status_time
    ON production.material_issues (tenant_organization_id, status, updated_at DESC);
CREATE INDEX idx_material_issue_order
    ON production.material_issues (production_order_id, updated_at DESC);

CREATE TABLE production.material_issue_lines (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    issue_id UUID NOT NULL REFERENCES production.material_issues(id),
    line_number INTEGER NOT NULL,
    component_material_id UUID NOT NULL,
    component_material_code VARCHAR(60) NOT NULL,
    component_material_name VARCHAR(160) NOT NULL,
    component_material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    required_quantity NUMERIC(19, 6) NOT NULL,
    issued_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    returned_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    bom_note VARCHAR(240),
    version BIGINT NOT NULL DEFAULT 0,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_material_issue_line_issue_line UNIQUE (issue_id, line_number),
    CONSTRAINT ck_material_issue_line_quantities CHECK (
        required_quantity > 0
        AND issued_quantity >= 0
        AND returned_quantity >= 0
        AND issued_quantity <= required_quantity
        AND returned_quantity <= issued_quantity
    )
);

CREATE INDEX idx_material_issue_line_issue
    ON production.material_issue_lines (issue_id, line_number);
CREATE INDEX idx_material_issue_line_material
    ON production.material_issue_lines (tenant_organization_id, component_material_id);

CREATE TABLE production.material_issue_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    issue_id UUID NOT NULL REFERENCES production.material_issues(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120),
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_material_issue_event_issue
    ON production.material_issue_events (issue_id, occurred_at DESC);

CREATE UNIQUE INDEX uk_material_issue_event_tenant_request
    ON production.material_issue_events (tenant_organization_id, request_id)
    WHERE request_id IS NOT NULL;

CREATE TABLE production.material_returns (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    return_number VARCHAR(40) NOT NULL,
    issue_id UUID NOT NULL REFERENCES production.material_issues(id),
    issue_number VARCHAR(40) NOT NULL,
    production_order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    warehouse_id UUID NOT NULL,
    warehouse_code VARCHAR(40) NOT NULL,
    warehouse_name VARCHAR(120) NOT NULL,
    location_id UUID NOT NULL,
    location_code VARCHAR(60) NOT NULL,
    location_name VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_material_return_tenant_number UNIQUE (tenant_organization_id, return_number),
    CONSTRAINT uk_material_return_tenant_request UNIQUE (tenant_organization_id, request_id)
);

CREATE INDEX idx_material_return_tenant_time
    ON production.material_returns (tenant_organization_id, created_at DESC);
CREATE INDEX idx_material_return_issue
    ON production.material_returns (issue_id, created_at DESC);

CREATE TABLE production.material_return_lines (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    return_id UUID NOT NULL REFERENCES production.material_returns(id),
    issue_line_id UUID NOT NULL REFERENCES production.material_issue_lines(id),
    line_number INTEGER NOT NULL,
    component_material_id UUID NOT NULL,
    component_material_code VARCHAR(60) NOT NULL,
    component_material_name VARCHAR(160) NOT NULL,
    component_material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT uk_material_return_line_return_line UNIQUE (return_id, line_number),
    CONSTRAINT ck_material_return_line_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_material_return_line_return
    ON production.material_return_lines (return_id, line_number);

CREATE TABLE production.material_stock_transactions (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    issue_id UUID NOT NULL REFERENCES production.material_issues(id),
    issue_line_id UUID REFERENCES production.material_issue_lines(id),
    return_id UUID REFERENCES production.material_returns(id),
    return_line_id UUID REFERENCES production.material_return_lines(id),
    movement_type VARCHAR(24) NOT NULL,
    component_material_id UUID NOT NULL,
    component_material_code VARCHAR(60) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    warehouse_id UUID NOT NULL,
    warehouse_code VARCHAR(40) NOT NULL,
    warehouse_name VARCHAR(120) NOT NULL,
    location_id UUID NOT NULL,
    location_code VARCHAR(60) NOT NULL,
    location_name VARCHAR(120) NOT NULL,
    balance_id UUID NOT NULL,
    movement_id UUID NOT NULL,
    movement_number VARCHAR(40) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    actor_user_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_material_stock_txn_direction CHECK (movement_type IN ('ISSUE', 'RETURN')),
    CONSTRAINT ck_material_stock_txn_quantity CHECK (quantity > 0),
    CONSTRAINT ck_material_stock_txn_reference CHECK (
        (issue_line_id IS NOT NULL AND return_line_id IS NULL)
        OR (issue_line_id IS NULL AND return_line_id IS NOT NULL)
    )
);

CREATE INDEX idx_material_stock_txn_issue
    ON production.material_stock_transactions (issue_id, occurred_at DESC);
CREATE INDEX idx_material_stock_txn_line
    ON production.material_stock_transactions (issue_line_id, occurred_at DESC);
CREATE INDEX idx_material_stock_txn_return
    ON production.material_stock_transactions (return_id, occurred_at DESC);

ALTER TABLE warehouse.stock_movements ADD COLUMN source_line_id UUID;
DROP INDEX IF EXISTS warehouse.uk_stock_movement_tenant_source;
CREATE UNIQUE INDEX uk_stock_movement_tenant_source_line
    ON warehouse.stock_movements (tenant_organization_id, source_type, source_id, source_line_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL AND source_line_id IS NOT NULL;

ALTER TABLE warehouse.stock_movements
    DROP CONSTRAINT ck_stock_movement_type;
ALTER TABLE warehouse.stock_movements
    ADD CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'RETURN', 'ALLOCATE', 'DEALLOCATE', 'FREEZE', 'UNFREEZE'));

COMMENT ON TABLE production.material_issues IS '生产模块拥有的按单备料/领料单；库存扣减和回补只由仓库模块执行';
COMMENT ON TABLE production.material_returns IS '生产模块拥有的组件退料事实；对应仓库 RETURN 流水';
COMMENT ON TABLE production.material_stock_transactions IS '生产领退料与仓库不可变流水的逐笔关联证据';

