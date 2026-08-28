ALTER TABLE identity.organization_units
    ADD COLUMN responsible_user_id UUID NULL REFERENCES identity.user_accounts(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE identity.workspaces
    ADD COLUMN responsible_user_id UUID NULL REFERENCES identity.user_accounts(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE identity.workspace_memberships
    ADD COLUMN organization_unit_id UUID NULL REFERENCES identity.organization_units(id);

UPDATE identity.workspace_memberships membership
SET organization_unit_id = workspace.operating_organization_id
FROM identity.workspaces workspace
WHERE workspace.id = membership.workspace_id
  AND membership.organization_unit_id IS NULL;

ALTER TABLE identity.workspace_memberships
    ALTER COLUMN organization_unit_id SET NOT NULL;

CREATE INDEX idx_organization_units_parent_status
    ON identity.organization_units(parent_id, status);
CREATE INDEX idx_workspace_memberships_organization_status
    ON identity.workspace_memberships(workspace_id, organization_unit_id, status);
