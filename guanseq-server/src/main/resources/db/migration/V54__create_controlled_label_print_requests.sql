CREATE SCHEMA IF NOT EXISTS labeling;

CREATE SEQUENCE labeling.print_request_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE labeling.print_requests (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    request_number VARCHAR(32) NOT NULL,
    object_type VARCHAR(32) NOT NULL,
    object_id UUID NOT NULL,
    object_version BIGINT NOT NULL,
    object_code VARCHAR(120) NOT NULL,
    object_name VARCHAR(200) NOT NULL,
    object_detail VARCHAR(500) NOT NULL,
    payload VARCHAR(160) NOT NULL,
    template_code VARCHAR(32) NOT NULL,
    template_version VARCHAR(24) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    copies INTEGER NOT NULL,
    reason VARCHAR(300),
    status VARCHAR(24) NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_username VARCHAR(80) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    prepared_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_label_print_request_number UNIQUE (request_number),
    CONSTRAINT uk_label_print_request_tenant_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_label_print_object_type CHECK (object_type IN ('OPERATION_TASK', 'EMPLOYEE', 'STOCK_BALANCE')),
    CONSTRAINT ck_label_print_mode CHECK (mode IN ('INITIAL', 'REPRINT')),
    CONSTRAINT ck_label_print_status CHECK (status = 'PREPARED'),
    CONSTRAINT ck_label_print_copies CHECK (copies BETWEEN 1 AND 10),
    CONSTRAINT ck_label_print_reason CHECK (
        (mode = 'INITIAL' AND reason IS NULL)
        OR (mode = 'REPRINT' AND length(trim(reason)) >= 4)
    )
);

CREATE INDEX idx_label_print_requests_workspace_time
    ON labeling.print_requests (tenant_organization_id, workspace_id, prepared_at DESC);
CREATE INDEX idx_label_print_requests_object
    ON labeling.print_requests (tenant_organization_id, workspace_id, object_type, object_id, prepared_at DESC);

COMMENT ON TABLE labeling.print_requests IS '不可变标签打印准备请求；PREPARED 不代表物理打印机出纸成功';
COMMENT ON COLUMN labeling.print_requests.payload IS '由原业务对象确定的可扫描载荷，不允许用户任意输入';
COMMENT ON COLUMN labeling.print_requests.object_version IS '生成凭证时原业务对象的乐观版本快照';

