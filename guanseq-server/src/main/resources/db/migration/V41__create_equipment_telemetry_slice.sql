CREATE TABLE equipment.telemetry_connections (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    asset_id UUID NOT NULL REFERENCES equipment.assets(id),
    connection_code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    protocol VARCHAR(24) NOT NULL,
    endpoint_type VARCHAR(24) NOT NULL,
    host VARCHAR(253) NOT NULL,
    port INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    connect_timeout_ms INTEGER NOT NULL,
    read_timeout_ms INTEGER NOT NULL,
    poll_interval_seconds INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creation_request_id VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_telemetry_connection_code UNIQUE (tenant_organization_id, connection_code),
    CONSTRAINT uk_equipment_telemetry_creation_request UNIQUE (tenant_organization_id, creation_request_id),
    CONSTRAINT ck_equipment_telemetry_protocol CHECK (protocol IN ('MODBUS_TCP')),
    CONSTRAINT ck_equipment_telemetry_endpoint_type CHECK (endpoint_type IN ('SIMULATOR', 'PHYSICAL_DEVICE')),
    CONSTRAINT ck_equipment_telemetry_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_equipment_telemetry_unit CHECK (unit_id BETWEEN 0 AND 247),
    CONSTRAINT ck_equipment_telemetry_connect_timeout CHECK (connect_timeout_ms BETWEEN 100 AND 10000),
    CONSTRAINT ck_equipment_telemetry_read_timeout CHECK (read_timeout_ms BETWEEN 100 AND 10000),
    CONSTRAINT ck_equipment_telemetry_poll_interval CHECK (poll_interval_seconds BETWEEN 1 AND 3600),
    CONSTRAINT ck_equipment_telemetry_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED'))
);

CREATE INDEX idx_equipment_telemetry_connection_workspace
    ON equipment.telemetry_connections (tenant_organization_id, workspace_id, status, updated_at DESC);

CREATE TABLE equipment.telemetry_points (
    id UUID PRIMARY KEY,
    connection_id UUID NOT NULL REFERENCES equipment.telemetry_connections(id),
    point_code VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    register_type VARCHAR(24) NOT NULL,
    address INTEGER NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    scale NUMERIC(20, 10) NOT NULL DEFAULT 1,
    value_offset NUMERIC(20, 10) NOT NULL DEFAULT 0,
    engineering_unit VARCHAR(24),
    valid_min NUMERIC(30, 10),
    valid_max NUMERIC(30, 10),
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_telemetry_point_code UNIQUE (connection_id, point_code),
    CONSTRAINT uk_equipment_telemetry_point_order UNIQUE (connection_id, sort_order),
    CONSTRAINT ck_equipment_telemetry_register_type CHECK (register_type IN ('COIL', 'HOLDING_REGISTER')),
    CONSTRAINT ck_equipment_telemetry_address CHECK (address BETWEEN 0 AND 65535),
    CONSTRAINT ck_equipment_telemetry_value_type CHECK (value_type IN ('BOOLEAN', 'UINT16', 'INT16', 'UINT32', 'INT32')),
    CONSTRAINT ck_equipment_telemetry_point_shape CHECK (
        (register_type = 'COIL' AND value_type = 'BOOLEAN')
        OR (register_type = 'HOLDING_REGISTER' AND value_type <> 'BOOLEAN')
    ),
    CONSTRAINT ck_equipment_telemetry_valid_range CHECK (valid_min IS NULL OR valid_max IS NULL OR valid_min <= valid_max),
    CONSTRAINT ck_equipment_telemetry_sort_order CHECK (sort_order BETWEEN 1 AND 1000)
);

CREATE INDEX idx_equipment_telemetry_point_connection
    ON equipment.telemetry_points (connection_id, sort_order);

CREATE TABLE equipment.telemetry_connection_runtime (
    connection_id UUID PRIMARY KEY REFERENCES equipment.telemetry_connections(id),
    communication_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    last_tested_at TIMESTAMPTZ,
    last_test_succeeded_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    next_poll_at TIMESTAMPTZ,
    last_error_code VARCHAR(60),
    last_error_message VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_equipment_telemetry_communication_status CHECK (communication_status IN ('UNKNOWN', 'ONLINE', 'OFFLINE'))
);

CREATE TABLE equipment.telemetry_samples (
    sequence_number BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id UUID NOT NULL UNIQUE,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    asset_id UUID NOT NULL REFERENCES equipment.assets(id),
    connection_id UUID NOT NULL REFERENCES equipment.telemetry_connections(id),
    point_id UUID NOT NULL REFERENCES equipment.telemetry_points(id),
    point_code VARCHAR(60) NOT NULL,
    raw_value VARCHAR(120) NOT NULL,
    numeric_value NUMERIC(30, 10),
    boolean_value BOOLEAN,
    quality VARCHAR(16) NOT NULL,
    device_time TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    message_version INTEGER NOT NULL DEFAULT 1,
    source_protocol VARCHAR(24) NOT NULL,
    CONSTRAINT ck_equipment_telemetry_sample_quality CHECK (quality IN ('GOOD', 'UNCERTAIN', 'BAD')),
    CONSTRAINT ck_equipment_telemetry_sample_value CHECK (
        (numeric_value IS NOT NULL AND boolean_value IS NULL)
        OR (numeric_value IS NULL AND boolean_value IS NOT NULL)
    ),
    CONSTRAINT ck_equipment_telemetry_message_version CHECK (message_version >= 1)
);

CREATE INDEX idx_equipment_telemetry_sample_point_time
    ON equipment.telemetry_samples (point_id, received_at DESC, sequence_number DESC);
CREATE INDEX idx_equipment_telemetry_sample_workspace_time
    ON equipment.telemetry_samples (tenant_organization_id, workspace_id, received_at DESC);

CREATE TABLE equipment.telemetry_connection_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES equipment.telemetry_connections(id),
    action VARCHAR(24) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_equipment_telemetry_event_request UNIQUE (tenant_organization_id, connection_id, request_id),
    CONSTRAINT ck_equipment_telemetry_event_action CHECK (action IN ('CREATED', 'TEST_SUCCEEDED', 'TEST_FAILED', 'ACTIVATED', 'PAUSED', 'POLL_REQUESTED')),
    CONSTRAINT ck_equipment_telemetry_event_status CHECK (
        (from_status IS NULL OR from_status IN ('DRAFT', 'ACTIVE', 'PAUSED'))
        AND to_status IN ('DRAFT', 'ACTIVE', 'PAUSED')
    )
);

CREATE INDEX idx_equipment_telemetry_event_connection
    ON equipment.telemetry_connection_events (connection_id, occurred_at DESC);

COMMENT ON TABLE equipment.telemetry_connections IS '设备模块拥有的只读协议连接配置；端点类型明确区分仿真与物理设备';
COMMENT ON TABLE equipment.telemetry_points IS '连接下可配置的只读点位映射，地址使用协议零基偏移';
COMMENT ON TABLE equipment.telemetry_connection_runtime IS '通讯运行状态，独立于连接启停状态和人工设备运行状态';
COMMENT ON TABLE equipment.telemetry_samples IS '协议适配器归一化后的原始采集证据，不直接修改核心业务事实';
