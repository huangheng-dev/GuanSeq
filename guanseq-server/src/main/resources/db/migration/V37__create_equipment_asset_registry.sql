CREATE SCHEMA equipment;

CREATE TABLE equipment.assets (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    asset_code VARCHAR(40) NOT NULL,
    asset_name VARCHAR(120) NOT NULL,
    category VARCHAR(24) NOT NULL,
    manufacturer VARCHAR(120),
    model VARCHAR(120),
    serial_number VARCHAR(120),
    work_center_code VARCHAR(40),
    work_center_name VARCHAR(120),
    location VARCHAR(160) NOT NULL,
    responsible_person VARCHAR(80) NOT NULL,
    commissioning_date DATE,
    operating_status VARCHAR(24) NOT NULL,
    status_changed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_asset_tenant_code UNIQUE (tenant_organization_id, asset_code),
    CONSTRAINT ck_equipment_asset_category CHECK (category IN ('PRODUCTION', 'QUALITY', 'UTILITY', 'LOGISTICS', 'OTHER')),
    CONSTRAINT ck_equipment_asset_status CHECK (operating_status IN ('IDLE', 'RUNNING', 'DOWN', 'MAINTENANCE', 'INACTIVE'))
);

CREATE INDEX idx_equipment_asset_workspace_status
    ON equipment.assets (tenant_organization_id, workspace_id, operating_status, updated_at DESC);
CREATE INDEX idx_equipment_asset_workspace_category
    ON equipment.assets (tenant_organization_id, workspace_id, category, asset_code);

CREATE TABLE equipment.asset_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    asset_id UUID NOT NULL REFERENCES equipment.assets(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_equipment_asset_event_action CHECK (action IN ('CREATED', 'UPDATED', 'STARTED', 'STOPPED', 'BREAKDOWN_REPORTED', 'MAINTENANCE_STARTED', 'MAINTENANCE_COMPLETED', 'INACTIVATED')),
    CONSTRAINT uk_equipment_asset_event_request UNIQUE (tenant_organization_id, request_id)
);

CREATE INDEX idx_equipment_asset_event_asset
    ON equipment.asset_events (asset_id, occurred_at DESC);

COMMENT ON SCHEMA equipment IS '设备台账、状态、点检、维护与停机事实；当前仅实现台账和人工受控状态';
COMMENT ON TABLE equipment.assets IS '设备模块拥有的设备台账与人工受控当前状态，不代表自动遥测';
COMMENT ON TABLE equipment.asset_events IS '设备建档、台账变更和人工状态流转的不可变审计证据';
