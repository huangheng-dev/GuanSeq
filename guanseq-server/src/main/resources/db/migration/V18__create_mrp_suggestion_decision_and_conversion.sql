ALTER TABLE planning.mrp_run_net_requirements
    ADD COLUMN decision_status VARCHAR(24) NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN decision_comment VARCHAR(500),
    ADD COLUMN decided_by UUID,
    ADD COLUMN decided_at TIMESTAMPTZ,
    ADD COLUMN converted_order_type VARCHAR(24),
    ADD COLUMN converted_order_id UUID,
    ADD COLUMN converted_order_number VARCHAR(60),
    ADD COLUMN converted_by UUID,
    ADD COLUMN converted_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE planning.mrp_run_net_requirements
SET decision_status = 'PROPOSED'
WHERE recommendation_type IN ('PRODUCTION', 'PURCHASE', 'OUTSOURCE')
  AND net_quantity > 0;

ALTER TABLE planning.mrp_run_net_requirements
    ADD CONSTRAINT ck_mrp_suggestion_decision_status
        CHECK (decision_status IN ('NOT_APPLICABLE', 'PROPOSED', 'APPROVED', 'REJECTED', 'CONVERTED')),
    ADD CONSTRAINT ck_mrp_suggestion_conversion
        CHECK (
            (decision_status = 'CONVERTED'
                AND converted_order_type IN ('PURCHASE_ORDER', 'PRODUCTION_ORDER')
                AND converted_order_id IS NOT NULL
                AND converted_order_number IS NOT NULL
                AND converted_by IS NOT NULL
                AND converted_at IS NOT NULL)
            OR
            (decision_status <> 'CONVERTED'
                AND converted_order_type IS NULL
                AND converted_order_id IS NULL
                AND converted_order_number IS NULL
                AND converted_by IS NULL
                AND converted_at IS NULL)
        );

CREATE INDEX idx_mrp_suggestion_tenant_status_date
    ON planning.mrp_run_net_requirements (tenant_organization_id, decision_status, required_date, material_code)
    WHERE recommendation_type IN ('PRODUCTION', 'PURCHASE', 'OUTSOURCE');

CREATE TABLE planning.mrp_suggestion_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    suggestion_id UUID NOT NULL REFERENCES planning.mrp_run_net_requirements(id),
    action VARCHAR(24) NOT NULL,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    comment VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mrp_suggestion_event_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_mrp_suggestion_event_action CHECK (action IN ('APPROVE', 'REJECT', 'CONVERT'))
);

CREATE INDEX idx_mrp_suggestion_event_suggestion
    ON planning.mrp_suggestion_events (suggestion_id, occurred_at DESC);

ALTER TABLE procurement.purchase_orders
    ADD COLUMN source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_id UUID,
    ADD COLUMN source_number VARCHAR(60),
    ADD CONSTRAINT ck_purchase_order_source CHECK (source_type IN ('MANUAL', 'MRP'));

CREATE UNIQUE INDEX uk_purchase_order_mrp_source
    ON procurement.purchase_orders (tenant_organization_id, source_type, source_id)
    WHERE source_type = 'MRP' AND source_id IS NOT NULL;

CREATE UNIQUE INDEX uk_production_order_mrp_source
    ON production.production_orders (tenant_organization_id, source_type, source_id)
    WHERE source_type = 'MRP' AND source_id IS NOT NULL;

COMMENT ON COLUMN planning.mrp_run_net_requirements.decision_status IS '计划建议审核与转单状态；无建议和阻断结果为 NOT_APPLICABLE';
COMMENT ON TABLE planning.mrp_suggestion_events IS 'MRP 建议审核、驳回和转单的幂等审计证据';
COMMENT ON COLUMN procurement.purchase_orders.source_id IS '来源为 MRP 时保存计划建议 ID，确保一条建议只生成一张采购草稿';
