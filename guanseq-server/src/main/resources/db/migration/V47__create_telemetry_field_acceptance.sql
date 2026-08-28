CREATE TABLE equipment.telemetry_field_acceptances (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES equipment.telemetry_connections(id),
    acceptance_number VARCHAR(48) NOT NULL,
    status VARCHAR(16) NOT NULL,
    network_approved BOOLEAN NOT NULL DEFAULT FALSE,
    security_validated BOOLEAN NOT NULL DEFAULT FALSE,
    read_only_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    disconnect_recovery_verified BOOLEAN NOT NULL DEFAULT FALSE,
    capacity_verified BOOLEAN NOT NULL DEFAULT FALSE,
    point_mapping_approved BOOLEAN NOT NULL DEFAULT FALSE,
    responsible_owner VARCHAR(80),
    test_window_start TIMESTAMPTZ,
    test_window_end TIMESTAMPTZ,
    evidence_reference VARCHAR(240),
    notes VARCHAR(1000),
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
    CONSTRAINT uk_equipment_telemetry_field_acceptance_connection UNIQUE (connection_id),
    CONSTRAINT uk_equipment_telemetry_field_acceptance_number UNIQUE (tenant_organization_id, acceptance_number),
    CONSTRAINT uk_equipment_telemetry_field_acceptance_creation UNIQUE (tenant_organization_id, creation_request_id),
    CONSTRAINT ck_equipment_telemetry_field_acceptance_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_equipment_telemetry_field_acceptance_window CHECK (
        (test_window_start IS NULL AND test_window_end IS NULL)
        OR (test_window_start IS NOT NULL AND test_window_end IS NOT NULL AND test_window_end > test_window_start))
);

CREATE INDEX idx_equipment_telemetry_field_acceptance_workspace
    ON equipment.telemetry_field_acceptances (tenant_organization_id, workspace_id, status, updated_at DESC);

CREATE TABLE equipment.telemetry_field_acceptance_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    acceptance_id UUID NOT NULL REFERENCES equipment.telemetry_field_acceptances(id),
    action VARCHAR(16) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_telemetry_field_acceptance_event_request UNIQUE (acceptance_id, request_id),
    CONSTRAINT ck_equipment_telemetry_field_acceptance_event_action CHECK (
        action IN ('CREATED', 'UPDATED', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_equipment_telemetry_field_acceptance_event_status CHECK (
        (from_status IS NULL OR from_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
        AND to_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_equipment_telemetry_field_acceptance_event_record
    ON equipment.telemetry_field_acceptance_events (acceptance_id, occurred_at DESC);

COMMENT ON TABLE equipment.telemetry_field_acceptances IS '真实设备或用户 Broker 的现场接入验收事实；仿真端点不得建立验收单';
COMMENT ON TABLE equipment.telemetry_field_acceptance_events IS '现场接入验收创建、修改与审批动作的不可变请求证据';
