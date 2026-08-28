CREATE TABLE equipment.telemetry_retention_policies (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    retention_days INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_telemetry_retention_workspace
        UNIQUE (tenant_organization_id, workspace_id),
    CONSTRAINT ck_equipment_telemetry_retention_days
        CHECK (retention_days BETWEEN 7 AND 3650)
);

CREATE TABLE equipment.telemetry_retention_events (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES equipment.telemetry_retention_policies(id),
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_retention_days INTEGER NOT NULL,
    to_retention_days INTEGER NOT NULL,
    cutoff_at TIMESTAMPTZ,
    deleted_sample_count BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_telemetry_retention_event_request
        UNIQUE (tenant_organization_id, workspace_id, request_id),
    CONSTRAINT ck_equipment_telemetry_retention_event_action
        CHECK (action IN ('POLICY_UPDATED', 'CLEANUP_COMPLETED')),
    CONSTRAINT ck_equipment_telemetry_retention_event_days
        CHECK (from_retention_days BETWEEN 7 AND 3650 AND to_retention_days BETWEEN 7 AND 3650),
    CONSTRAINT ck_equipment_telemetry_retention_deleted_count
        CHECK (deleted_sample_count >= 0),
    CONSTRAINT ck_equipment_telemetry_retention_event_shape CHECK (
        (action = 'POLICY_UPDATED' AND cutoff_at IS NULL AND deleted_sample_count = 0)
        OR (action = 'CLEANUP_COMPLETED' AND cutoff_at IS NOT NULL)
    )
);

CREATE INDEX idx_equipment_telemetry_sample_connection_time
    ON equipment.telemetry_samples (connection_id, received_at DESC, sequence_number DESC);

CREATE INDEX idx_equipment_telemetry_retention_event_workspace
    ON equipment.telemetry_retention_events
        (tenant_organization_id, workspace_id, occurred_at DESC);

COMMENT ON TABLE equipment.telemetry_retention_policies IS
    '设备原始采集样本的工作区保留策略；未配置时业务层使用 30 天默认值';
COMMENT ON TABLE equipment.telemetry_retention_events IS
    '保留策略修改与人工清理的不可变责任和结果证据';
