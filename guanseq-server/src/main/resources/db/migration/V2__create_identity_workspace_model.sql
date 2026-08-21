CREATE TABLE identity.organization_units (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    unit_type VARCHAR(24) NOT NULL,
    parent_id UUID REFERENCES identity.organization_units (id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_organization_unit_type CHECK (unit_type IN ('COMPANY', 'PLANT', 'SITE')),
    CONSTRAINT ck_organization_unit_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE identity.workspaces (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    tenant_organization_id UUID NOT NULL REFERENCES identity.organization_units (id),
    operating_organization_id UUID NOT NULL REFERENCES identity.organization_units (id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workspace_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE identity.user_accounts (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_account_status CHECK (status IN ('ACTIVE', 'LOCKED', 'INACTIVE'))
);

CREATE TABLE identity.workspace_memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity.user_accounts (id),
    workspace_id UUID NOT NULL REFERENCES identity.workspaces (id),
    role_code VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workspace_membership UNIQUE (user_id, workspace_id),
    CONSTRAINT ck_workspace_membership_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_workspace_membership_user
    ON identity.workspace_memberships (user_id, status);

CREATE TABLE identity.user_workspace_preferences (
    user_id UUID PRIMARY KEY REFERENCES identity.user_accounts (id),
    current_workspace_id UUID NOT NULL REFERENCES identity.workspaces (id),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity.audit_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES identity.user_accounts (id),
    workspace_id UUID REFERENCES identity.workspaces (id),
    event_type VARCHAR(80) NOT NULL,
    object_type VARCHAR(80) NOT NULL,
    object_id VARCHAR(120),
    request_id VARCHAR(64),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_event_user_time
    ON identity.audit_events (user_id, occurred_at DESC);

COMMENT ON TABLE identity.organization_units IS '公司、工厂与站点的受控组织层级';
COMMENT ON TABLE identity.workspaces IS '用户进入产品时的数据与业务操作边界';
COMMENT ON TABLE identity.workspace_memberships IS '用户可访问工作区及其基础角色';
COMMENT ON TABLE identity.user_workspace_preferences IS '用户当前工作区及并发版本';
COMMENT ON TABLE identity.audit_events IS '身份、授权与工作区变更的服务端审计事实';
