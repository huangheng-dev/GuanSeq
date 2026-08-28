CREATE TABLE equipment.oee_records (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    record_number VARCHAR(48) NOT NULL,
    asset_id UUID NOT NULL REFERENCES equipment.assets(id),
    asset_code_snapshot VARCHAR(48) NOT NULL,
    asset_name_snapshot VARCHAR(120) NOT NULL,
    work_center_code_snapshot VARCHAR(40),
    work_center_name_snapshot VARCHAR(120),
    location_snapshot VARCHAR(160) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    planned_production_minutes NUMERIC(12, 2) NOT NULL,
    downtime_minutes NUMERIC(12, 2) NOT NULL DEFAULT 0,
    run_minutes NUMERIC(12, 2) NOT NULL,
    ideal_cycle_seconds NUMERIC(12, 4) NOT NULL,
    total_count BIGINT NOT NULL,
    good_count BIGINT NOT NULL,
    availability_rate NUMERIC(9, 4) NOT NULL,
    performance_rate NUMERIC(9, 4) NOT NULL,
    quality_rate NUMERIC(9, 4) NOT NULL,
    oee_rate NUMERIC(9, 4) NOT NULL,
    shift_name VARCHAR(80) NOT NULL,
    production_reference VARCHAR(120),
    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(160),
    status VARCHAR(16) NOT NULL,
    rejection_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    creation_request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by UUID,
    submitted_at TIMESTAMPTZ,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    rejected_by UUID,
    rejected_at TIMESTAMPTZ,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_oee_record_number UNIQUE (tenant_organization_id, record_number),
    CONSTRAINT uk_equipment_oee_creation_request UNIQUE (tenant_organization_id, creation_request_id),
    CONSTRAINT ck_equipment_oee_window CHECK (window_end > window_start),
    CONSTRAINT ck_equipment_oee_minutes CHECK (
        planned_production_minutes > 0 AND downtime_minutes >= 0
        AND run_minutes >= 0 AND run_minutes = planned_production_minutes - downtime_minutes),
    CONSTRAINT ck_equipment_oee_counts CHECK (total_count >= 0 AND good_count >= 0 AND good_count <= total_count),
    CONSTRAINT ck_equipment_oee_cycle CHECK (ideal_cycle_seconds > 0),
    CONSTRAINT ck_equipment_oee_rates CHECK (
        availability_rate >= 0 AND performance_rate >= 0 AND quality_rate >= 0 AND oee_rate >= 0),
    CONSTRAINT ck_equipment_oee_source CHECK (source_type = 'MANUAL_VERIFIED'),
    CONSTRAINT ck_equipment_oee_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_equipment_oee_workspace
    ON equipment.oee_records (tenant_organization_id, workspace_id, status, window_start DESC);
CREATE INDEX idx_equipment_oee_asset_window
    ON equipment.oee_records (asset_id, window_start, window_end);

CREATE TABLE equipment.oee_downtimes (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    oee_record_id UUID NOT NULL REFERENCES equipment.oee_records(id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    duration_minutes NUMERIC(12, 2) NOT NULL,
    reason_category VARCHAR(32) NOT NULL,
    responsible_party VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_equipment_oee_downtime_window CHECK (ended_at > started_at),
    CONSTRAINT ck_equipment_oee_downtime_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_equipment_oee_downtime_category CHECK (reason_category IN (
        'EQUIPMENT_FAILURE', 'SETUP_CHANGEOVER', 'MATERIAL_WAIT', 'QUALITY_HOLD',
        'PERSONNEL_WAIT', 'PLANNED_MAINTENANCE', 'OTHER'))
);

CREATE INDEX idx_equipment_oee_downtime_record
    ON equipment.oee_downtimes (oee_record_id, started_at);

CREATE TABLE equipment.oee_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    oee_record_id UUID NOT NULL REFERENCES equipment.oee_records(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_oee_event_request UNIQUE (oee_record_id, request_id),
    CONSTRAINT ck_equipment_oee_event_action CHECK (action IN (
        'CREATED', 'UPDATED', 'DOWNTIME_ADDED', 'DOWNTIME_UPDATED', 'DOWNTIME_REMOVED',
        'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_equipment_oee_event_status CHECK (
        (from_status IS NULL OR from_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
        AND to_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_equipment_oee_event_record
    ON equipment.oee_events (oee_record_id, occurred_at DESC);

COMMENT ON TABLE equipment.oee_records IS '设备 OEE 核实与审批事实；v1 仅支持明确标记的人工核实来源';
COMMENT ON TABLE equipment.oee_downtimes IS 'OEE 统计窗口内的停机原因与责任证据；不得越界或重叠';
COMMENT ON TABLE equipment.oee_events IS 'OEE 编辑、停机维护与审批动作的不可变请求证据';
