ALTER TABLE equipment.telemetry_retention_policies
    ADD COLUMN automatic_cleanup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cleanup_interval_hours INTEGER NOT NULL DEFAULT 24,
    ADD COLUMN next_cleanup_at TIMESTAMPTZ,
    ADD COLUMN last_automation_status VARCHAR(16),
    ADD COLUMN last_automation_completed_at TIMESTAMPTZ,
    ADD COLUMN consecutive_failures INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_equipment_telemetry_cleanup_interval
        CHECK (cleanup_interval_hours BETWEEN 1 AND 720),
    ADD CONSTRAINT ck_equipment_telemetry_automation_status
        CHECK (last_automation_status IS NULL OR last_automation_status IN ('SUCCEEDED', 'PARTIAL', 'FAILED')),
    ADD CONSTRAINT ck_equipment_telemetry_automation_failures
        CHECK (consecutive_failures >= 0),
    ADD CONSTRAINT ck_equipment_telemetry_automation_schedule CHECK (
        (automatic_cleanup_enabled AND next_cleanup_at IS NOT NULL)
        OR (NOT automatic_cleanup_enabled AND next_cleanup_at IS NULL)
    );

ALTER TABLE equipment.telemetry_retention_events
    ADD COLUMN from_automatic_cleanup_enabled BOOLEAN,
    ADD COLUMN to_automatic_cleanup_enabled BOOLEAN,
    ADD COLUMN from_cleanup_interval_hours INTEGER,
    ADD COLUMN to_cleanup_interval_hours INTEGER,
    ADD CONSTRAINT ck_equipment_telemetry_retention_event_intervals CHECK (
        (from_cleanup_interval_hours IS NULL OR from_cleanup_interval_hours BETWEEN 1 AND 720)
        AND (to_cleanup_interval_hours IS NULL OR to_cleanup_interval_hours BETWEEN 1 AND 720)
    );

CREATE TABLE equipment.telemetry_retention_leases (
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    owner_id VARCHAR(160) NOT NULL,
    acquired_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_organization_id, workspace_id),
    CONSTRAINT ck_equipment_telemetry_retention_lease_time CHECK (lease_until > acquired_at)
);

CREATE TABLE equipment.telemetry_retention_runs (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES equipment.telemetry_retention_policies(id),
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(16) NOT NULL,
    initiated_by UUID,
    instance_id VARCHAR(160) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    deleted_sample_count BIGINT NOT NULL DEFAULT 0,
    remaining_expired_count BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64),
    failure_summary VARCHAR(500),
    attention_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    acknowledgement_request_id VARCHAR(120),
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    acknowledgement_note VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_equipment_telemetry_retention_run_request
        UNIQUE (tenant_organization_id, workspace_id, request_id),
    CONSTRAINT uk_equipment_telemetry_retention_ack_request
        UNIQUE (tenant_organization_id, workspace_id, acknowledgement_request_id),
    CONSTRAINT ck_equipment_telemetry_retention_run_trigger
        CHECK (trigger_type IN ('SCHEDULED', 'USER_RETRY')),
    CONSTRAINT ck_equipment_telemetry_retention_run_status
        CHECK (status IN ('SUCCEEDED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_equipment_telemetry_retention_run_counts
        CHECK (deleted_sample_count >= 0 AND remaining_expired_count >= 0),
    CONSTRAINT ck_equipment_telemetry_retention_attention
        CHECK (attention_status IN ('NONE', 'OPEN', 'ACKNOWLEDGED')),
    CONSTRAINT ck_equipment_telemetry_retention_run_shape CHECK (
        (status IN ('SUCCEEDED', 'PARTIAL')
            AND failure_code IS NULL AND failure_summary IS NULL AND attention_status = 'NONE')
        OR (status = 'FAILED'
            AND failure_code IS NOT NULL AND failure_summary IS NOT NULL
            AND attention_status IN ('OPEN', 'ACKNOWLEDGED'))
    ),
    CONSTRAINT ck_equipment_telemetry_retention_ack_shape CHECK (
        (attention_status <> 'ACKNOWLEDGED'
            AND acknowledgement_request_id IS NULL AND acknowledged_by IS NULL
            AND acknowledged_at IS NULL AND acknowledgement_note IS NULL)
        OR (attention_status = 'ACKNOWLEDGED'
            AND acknowledgement_request_id IS NOT NULL AND acknowledged_by IS NOT NULL
            AND acknowledged_at IS NOT NULL AND acknowledgement_note IS NOT NULL)
    ),
    CONSTRAINT ck_equipment_telemetry_retention_run_time CHECK (completed_at >= started_at)
);

CREATE INDEX idx_equipment_telemetry_retention_policy_due
    ON equipment.telemetry_retention_policies (next_cleanup_at)
    WHERE automatic_cleanup_enabled;

CREATE INDEX idx_equipment_telemetry_retention_run_workspace
    ON equipment.telemetry_retention_runs
        (tenant_organization_id, workspace_id, started_at DESC);

CREATE INDEX idx_equipment_telemetry_retention_cleanup_scan
    ON equipment.telemetry_samples
        (tenant_organization_id, workspace_id, received_at, sequence_number);

COMMENT ON TABLE equipment.telemetry_retention_leases IS
    '同一工作区自动样本清理的数据库互斥租约；进程退出后由到期时间恢复';
COMMENT ON TABLE equipment.telemetry_retention_runs IS
    '样本自动清理的不可变运行、失败责任与确认恢复证据';
