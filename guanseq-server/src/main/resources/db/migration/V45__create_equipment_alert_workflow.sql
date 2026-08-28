CREATE TABLE equipment.alert_rules (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    rule_code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    connection_id UUID NOT NULL REFERENCES equipment.telemetry_connections(id),
    point_id UUID REFERENCES equipment.telemetry_points(id),
    rule_type VARCHAR(32) NOT NULL,
    threshold_value NUMERIC(30, 10),
    severity VARCHAR(16) NOT NULL,
    default_assignee VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creation_request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_alert_rule_code UNIQUE (tenant_organization_id, rule_code),
    CONSTRAINT uk_equipment_alert_rule_request UNIQUE (tenant_organization_id, creation_request_id),
    CONSTRAINT ck_equipment_alert_rule_type CHECK (
        rule_type IN ('HIGH_LIMIT', 'LOW_LIMIT', 'COMMUNICATION_FAILURE')),
    CONSTRAINT ck_equipment_alert_rule_severity CHECK (severity IN ('WARNING', 'CRITICAL')),
    CONSTRAINT ck_equipment_alert_rule_status CHECK (status IN ('ACTIVE', 'PAUSED')),
    CONSTRAINT ck_equipment_alert_rule_shape CHECK (
        (rule_type IN ('HIGH_LIMIT', 'LOW_LIMIT') AND point_id IS NOT NULL AND threshold_value IS NOT NULL)
        OR (rule_type = 'COMMUNICATION_FAILURE' AND point_id IS NULL AND threshold_value IS NULL)
    )
);

CREATE INDEX idx_equipment_alert_rule_workspace
    ON equipment.alert_rules (tenant_organization_id, workspace_id, status, updated_at DESC);
CREATE INDEX idx_equipment_alert_rule_connection
    ON equipment.alert_rules (connection_id, status);

CREATE TABLE equipment.alert_rule_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    rule_id UUID NOT NULL REFERENCES equipment.alert_rules(id),
    action VARCHAR(24) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_alert_rule_event_request UNIQUE (rule_id, request_id),
    CONSTRAINT ck_equipment_alert_rule_event_action CHECK (action IN ('CREATED', 'ACTIVATED', 'PAUSED')),
    CONSTRAINT ck_equipment_alert_rule_event_status CHECK (
        (from_status IS NULL OR from_status IN ('ACTIVE', 'PAUSED'))
        AND to_status IN ('ACTIVE', 'PAUSED'))
);

CREATE INDEX idx_equipment_alert_rule_event_rule
    ON equipment.alert_rule_events (rule_id, occurred_at DESC);

CREATE TABLE equipment.alerts (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    alert_number VARCHAR(48) NOT NULL,
    rule_id UUID NOT NULL REFERENCES equipment.alert_rules(id),
    rule_code_snapshot VARCHAR(40) NOT NULL,
    rule_name_snapshot VARCHAR(120) NOT NULL,
    asset_id UUID NOT NULL REFERENCES equipment.assets(id),
    asset_code_snapshot VARCHAR(48) NOT NULL,
    asset_name_snapshot VARCHAR(120) NOT NULL,
    connection_id UUID NOT NULL REFERENCES equipment.telemetry_connections(id),
    connection_code_snapshot VARCHAR(40) NOT NULL,
    point_id UUID REFERENCES equipment.telemetry_points(id),
    point_code_snapshot VARCHAR(60),
    point_name_snapshot VARCHAR(120),
    rule_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    condition_active BOOLEAN NOT NULL,
    observed_value NUMERIC(30, 10),
    observed_quality VARCHAR(16),
    failure_code VARCHAR(60),
    assignee VARCHAR(80) NOT NULL,
    resolution_notes VARCHAR(1000),
    linked_work_order_id UUID REFERENCES equipment.maintenance_work_orders(id),
    version BIGINT NOT NULL DEFAULT 0,
    first_occurred_at TIMESTAMPTZ NOT NULL,
    last_occurred_at TIMESTAMPTZ NOT NULL,
    recovered_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    processing_started_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_alert_number UNIQUE (tenant_organization_id, alert_number),
    CONSTRAINT ck_equipment_alert_type CHECK (
        rule_type IN ('HIGH_LIMIT', 'LOW_LIMIT', 'COMMUNICATION_FAILURE')),
    CONSTRAINT ck_equipment_alert_severity CHECK (severity IN ('WARNING', 'CRITICAL')),
    CONSTRAINT ck_equipment_alert_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_equipment_alert_quality CHECK (
        observed_quality IS NULL OR observed_quality IN ('GOOD', 'UNCERTAIN', 'BAD'))
);

CREATE UNIQUE INDEX uk_equipment_alert_open_rule
    ON equipment.alerts (rule_id)
    WHERE status <> 'CLOSED';
CREATE INDEX idx_equipment_alert_workspace
    ON equipment.alerts (tenant_organization_id, workspace_id, status, severity, last_occurred_at DESC);
CREATE INDEX idx_equipment_alert_asset
    ON equipment.alerts (asset_id, status, last_occurred_at DESC);

CREATE TABLE equipment.alert_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID,
    alert_id UUID NOT NULL REFERENCES equipment.alerts(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(160) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_alert_event_request UNIQUE (alert_id, request_id),
    CONSTRAINT ck_equipment_alert_event_action CHECK (action IN (
        'OCCURRED', 'REOPENED', 'CONDITION_CLEARED', 'ACKNOWLEDGED',
        'PROCESSING_STARTED', 'RESOLVED', 'CLOSED', 'REPAIR_LINKED')),
    CONSTRAINT ck_equipment_alert_event_status CHECK (
        (from_status IS NULL OR from_status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
        AND to_status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_equipment_alert_event_alert
    ON equipment.alert_events (alert_id, occurred_at DESC);

COMMENT ON TABLE equipment.alert_rules IS '设备采集报警规则；v1 支持即时阈值与通讯失败，不包含复杂规则引擎';
COMMENT ON TABLE equipment.alerts IS '设备报警责任事实；条件恢复与人工处置状态分离，不直接修改设备业务状态';
COMMENT ON TABLE equipment.alert_events IS '报警触发、恢复和处置的不可变证据；系统触发事件允许操作者为空';
