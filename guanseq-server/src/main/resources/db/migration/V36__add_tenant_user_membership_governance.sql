ALTER TABLE identity.user_accounts
    ADD COLUMN tenant_organization_id UUID,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT memberships.user_id
        FROM identity.workspace_memberships memberships
        JOIN identity.workspaces workspaces ON workspaces.id = memberships.workspace_id
        GROUP BY memberships.user_id
        HAVING COUNT(DISTINCT workspaces.tenant_organization_id) > 1
    ) THEN
        RAISE EXCEPTION 'identity user has memberships in multiple tenant organizations; resolve ownership before V36';
    END IF;
END $$;

UPDATE identity.user_accounts users
SET tenant_organization_id = ownership.tenant_organization_id
FROM (
    SELECT memberships.user_id, MIN(workspaces.tenant_organization_id::text)::uuid AS tenant_organization_id
    FROM identity.workspace_memberships memberships
    JOIN identity.workspaces workspaces ON workspaces.id = memberships.workspace_id
    GROUP BY memberships.user_id
) ownership
WHERE ownership.user_id = users.id;

ALTER TABLE identity.user_accounts
    ALTER COLUMN tenant_organization_id SET NOT NULL,
    ADD CONSTRAINT fk_identity_user_tenant
        FOREIGN KEY (tenant_organization_id) REFERENCES identity.organization_units (id);

CREATE INDEX idx_identity_user_tenant_status
    ON identity.user_accounts (tenant_organization_id, status, username);

ALTER TABLE identity.workspace_memberships
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_workspace_membership_workspace_status
    ON identity.workspace_memberships (workspace_id, status, role_code);

COMMENT ON COLUMN identity.user_accounts.tenant_organization_id IS '内部账号的唯一租户所有权，不由外部身份提供者写入';
COMMENT ON COLUMN identity.user_accounts.version IS '账号显示信息的乐观并发版本';
COMMENT ON COLUMN identity.workspace_memberships.version IS '工作区角色与成员状态的乐观并发版本';
