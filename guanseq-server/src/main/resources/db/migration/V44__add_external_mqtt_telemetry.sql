ALTER TABLE equipment.telemetry_connections
    DROP CONSTRAINT ck_equipment_telemetry_protocol,
    DROP CONSTRAINT ck_equipment_telemetry_endpoint_type;

ALTER TABLE equipment.telemetry_connections
    ADD COLUMN mqtt_transport VARCHAR(8),
    ADD COLUMN mqtt_client_id VARCHAR(128),
    ADD COLUMN mqtt_qos INTEGER,
    ADD COLUMN credential_reference VARCHAR(80),
    ADD COLUMN mqtt_message_id_pointer VARCHAR(253),
    ADD COLUMN mqtt_device_time_pointer VARCHAR(253),
    ADD CONSTRAINT ck_equipment_telemetry_protocol
        CHECK (protocol IN ('MODBUS_TCP', 'MQTT_3_1_1')),
    ADD CONSTRAINT ck_equipment_telemetry_endpoint_type
        CHECK (endpoint_type IN ('SIMULATOR', 'PHYSICAL_DEVICE', 'EXTERNAL_BROKER')),
    ADD CONSTRAINT ck_equipment_telemetry_mqtt_shape CHECK (
        (protocol = 'MODBUS_TCP'
            AND endpoint_type IN ('SIMULATOR', 'PHYSICAL_DEVICE')
            AND mqtt_transport IS NULL
            AND mqtt_client_id IS NULL
            AND mqtt_qos IS NULL
            AND credential_reference IS NULL
            AND mqtt_message_id_pointer IS NULL
            AND mqtt_device_time_pointer IS NULL)
        OR
        (protocol = 'MQTT_3_1_1'
            AND endpoint_type IN ('SIMULATOR', 'EXTERNAL_BROKER')
            AND unit_id = 0
            AND mqtt_transport IN ('TCP', 'TLS')
            AND mqtt_client_id IS NOT NULL
            AND mqtt_qos BETWEEN 0 AND 1
            AND mqtt_message_id_pointer LIKE '/%')
    );

ALTER TABLE equipment.telemetry_points
    DROP CONSTRAINT ck_equipment_telemetry_register_type,
    DROP CONSTRAINT ck_equipment_telemetry_value_type,
    DROP CONSTRAINT ck_equipment_telemetry_point_shape;

ALTER TABLE equipment.telemetry_points
    ADD COLUMN mqtt_topic VARCHAR(512),
    ADD COLUMN mqtt_value_pointer VARCHAR(253),
    ADD CONSTRAINT ck_equipment_telemetry_register_type
        CHECK (register_type IN ('COIL', 'HOLDING_REGISTER', 'MQTT_JSON')),
    ADD CONSTRAINT ck_equipment_telemetry_value_type
        CHECK (value_type IN ('BOOLEAN', 'UINT16', 'INT16', 'UINT32', 'INT32', 'DECIMAL')),
    ADD CONSTRAINT ck_equipment_telemetry_point_shape CHECK (
        (register_type = 'COIL' AND value_type = 'BOOLEAN'
            AND mqtt_topic IS NULL AND mqtt_value_pointer IS NULL)
        OR
        (register_type = 'HOLDING_REGISTER' AND value_type IN ('UINT16', 'INT16', 'UINT32', 'INT32')
            AND mqtt_topic IS NULL AND mqtt_value_pointer IS NULL)
        OR
        (register_type = 'MQTT_JSON' AND address = 0 AND value_type IN ('BOOLEAN', 'DECIMAL')
            AND mqtt_topic IS NOT NULL AND mqtt_value_pointer LIKE '/%')
    );

ALTER TABLE equipment.telemetry_samples
    ADD COLUMN source_message_id VARCHAR(160);

CREATE UNIQUE INDEX uk_equipment_telemetry_sample_source_message
    ON equipment.telemetry_samples (connection_id, point_id, source_message_id)
    WHERE source_message_id IS NOT NULL;

COMMENT ON COLUMN equipment.telemetry_connections.credential_reference
    IS '部署环境中的 MQTT 凭据别名；数据库不保存 Broker 用户名或密码';
COMMENT ON COLUMN equipment.telemetry_points.mqtt_topic
    IS '外部 Broker 上的精确 Topic；首切片不接受通配订阅';
COMMENT ON COLUMN equipment.telemetry_samples.source_message_id
    IS '协议来源的稳定消息编号，用于 MQTT 点位级幂等去重';
