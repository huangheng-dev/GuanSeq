CREATE TABLE planning.mrp_run_net_requirements (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES planning.mrp_runs(id),
    tenant_organization_id UUID NOT NULL,
    requirement_level INTEGER NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    parent_material_id UUID,
    parent_material_code VARCHAR(60),
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    procurement_type VARCHAR(20) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    gross_quantity NUMERIC(19, 6) NOT NULL,
    available_consumed NUMERIC(19, 6) NOT NULL,
    scheduled_receipt_consumed NUMERIC(19, 6) NOT NULL,
    net_quantity NUMERIC(19, 6) NOT NULL,
    required_date DATE NOT NULL,
    recommended_release_date DATE,
    recommendation_type VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mrp_net_requirement_level CHECK (requirement_level BETWEEN 0 AND 50),
    CONSTRAINT ck_mrp_net_requirement_source CHECK (source_type IN ('INDEPENDENT_DEMAND', 'BOM_COMPONENT')),
    CONSTRAINT ck_mrp_net_requirement_procurement CHECK (procurement_type IN ('MAKE', 'BUY', 'OUTSOURCE')),
    CONSTRAINT ck_mrp_net_requirement_quantities CHECK (
        gross_quantity > 0 AND available_consumed >= 0 AND scheduled_receipt_consumed >= 0 AND net_quantity >= 0
        AND available_consumed + scheduled_receipt_consumed + net_quantity = gross_quantity
    ),
    CONSTRAINT ck_mrp_net_requirement_recommendation CHECK (recommendation_type IN ('NONE', 'PRODUCTION', 'PURCHASE', 'OUTSOURCE', 'BLOCKED'))
);

CREATE INDEX idx_mrp_net_requirement_run_date
    ON planning.mrp_run_net_requirements (run_id, required_date, requirement_level, material_code);

COMMENT ON TABLE planning.mrp_run_net_requirements IS 'MRP 按需求日期冻结的净需求运算结果与建议，不直接创建业务订单';
