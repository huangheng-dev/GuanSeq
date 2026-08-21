CREATE TABLE planning.mrp_run_supply_snapshots (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES planning.mrp_runs(id),
    tenant_organization_id UUID NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(80) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    on_hand_quantity NUMERIC(19, 6) NOT NULL,
    allocated_quantity NUMERIC(19, 6) NOT NULL,
    frozen_quantity NUMERIC(19, 6) NOT NULL,
    available_quantity NUMERIC(19, 6) NOT NULL,
    balance_count INTEGER NOT NULL,
    snapshotted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mrp_supply_snapshot_material UNIQUE (run_id, material_id),
    CONSTRAINT ck_mrp_supply_snapshot_quantities CHECK (
        on_hand_quantity >= 0 AND allocated_quantity >= 0 AND frozen_quantity >= 0
        AND available_quantity >= 0 AND balance_count >= 0
    )
);

CREATE INDEX idx_mrp_supply_snapshot_run
    ON planning.mrp_run_supply_snapshots (run_id, material_code);

COMMENT ON TABLE planning.mrp_run_supply_snapshots IS 'MRP 发起时从仓储公开接口冻结的库存供给位置，不随后续库存事务变化';
