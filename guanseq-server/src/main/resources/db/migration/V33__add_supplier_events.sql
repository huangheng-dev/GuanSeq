CREATE TABLE procurement.supplier_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    supplier_id UUID NOT NULL REFERENCES procurement.suppliers(id),
    action VARCHAR(32) NOT NULL,
    request_id VARCHAR(120),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_supplier_event_action CHECK (action IN ('CREATED','UPDATED','ENABLED','DISABLED'))
);

CREATE INDEX idx_supplier_event_supplier_time
    ON procurement.supplier_events (supplier_id, occurred_at DESC);
