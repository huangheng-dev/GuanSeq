CREATE SCHEMA warehouse;

COMMENT ON SCHEMA warehouse IS '仓库、库位、库存余额与不可变库存流水事实';

CREATE SEQUENCE warehouse.movement_number_seq START WITH 1;

CREATE TABLE warehouse.warehouses (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    operating_organization_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_warehouse_tenant_code UNIQUE (tenant_organization_id, code),
    CONSTRAINT ck_warehouse_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE warehouse.storage_locations (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouse.warehouses(id),
    code VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    location_type VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_location_warehouse_code UNIQUE (warehouse_id, code),
    CONSTRAINT ck_location_type CHECK (location_type IN ('STORAGE', 'RECEIVING', 'INSPECTION', 'PRODUCTION', 'SHIPPING')),
    CONSTRAINT ck_location_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE warehouse.stock_balances (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouse.warehouses(id),
    warehouse_code VARCHAR(40) NOT NULL,
    warehouse_name VARCHAR(120) NOT NULL,
    location_id UUID NOT NULL REFERENCES warehouse.storage_locations(id),
    location_code VARCHAR(60) NOT NULL,
    location_name VARCHAR(120) NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    lot_number VARCHAR(80) NOT NULL DEFAULT '',
    quality_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    on_hand_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    allocated_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    frozen_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_balance_dimension UNIQUE (tenant_organization_id, warehouse_id, location_id, material_id, lot_number, quality_status),
    CONSTRAINT ck_stock_quality_status CHECK (quality_status IN ('AVAILABLE', 'INSPECTION', 'BLOCKED')),
    CONSTRAINT ck_stock_quantities CHECK (
        on_hand_quantity >= 0 AND allocated_quantity >= 0 AND frozen_quantity >= 0
        AND allocated_quantity + frozen_quantity <= on_hand_quantity
    )
);

CREATE INDEX idx_stock_balance_tenant_material
    ON warehouse.stock_balances (tenant_organization_id, material_id, quality_status);

CREATE INDEX idx_stock_balance_tenant_location
    ON warehouse.stock_balances (tenant_organization_id, warehouse_id, location_id);

CREATE TABLE warehouse.stock_movements (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    balance_id UUID NOT NULL REFERENCES warehouse.stock_balances(id),
    movement_number VARCHAR(40) NOT NULL,
    movement_type VARCHAR(24) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    before_on_hand NUMERIC(19, 6) NOT NULL,
    after_on_hand NUMERIC(19, 6) NOT NULL,
    before_allocated NUMERIC(19, 6) NOT NULL,
    after_allocated NUMERIC(19, 6) NOT NULL,
    before_frozen NUMERIC(19, 6) NOT NULL,
    after_frozen NUMERIC(19, 6) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_movement_tenant_number UNIQUE (tenant_organization_id, movement_number),
    CONSTRAINT uk_stock_movement_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'ALLOCATE', 'DEALLOCATE', 'FREEZE', 'UNFREEZE')),
    CONSTRAINT ck_stock_movement_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_stock_movement_balance_time
    ON warehouse.stock_movements (tenant_organization_id, balance_id, occurred_at DESC);

COMMENT ON TABLE warehouse.stock_balances IS '按仓库、库位、物料、批次和质量状态汇总的库存余额；不得直接手工覆盖';
COMMENT ON TABLE warehouse.stock_movements IS '驱动库存余额变化的不可变事务证据；错误通过反向事务恢复';
