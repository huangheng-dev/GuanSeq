CREATE TABLE identity.system_bootstrap (
    singleton_key BOOLEAN PRIMARY KEY DEFAULT TRUE,
    status VARCHAR(16) NOT NULL,
    initial_user_id UUID REFERENCES identity.user_accounts (id),
    initial_workspace_id UUID REFERENCES identity.workspaces (id),
    request_id VARCHAR(64),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_identity_system_bootstrap_singleton CHECK (singleton_key),
    CONSTRAINT ck_identity_system_bootstrap_status CHECK (status IN ('PENDING', 'COMPLETED'))
);

INSERT INTO identity.system_bootstrap (singleton_key, status, completed_at)
SELECT TRUE,
       CASE
           WHEN EXISTS (SELECT 1 FROM identity.user_accounts)
             OR EXISTS (SELECT 1 FROM identity.organization_units)
             OR EXISTS (SELECT 1 FROM identity.workspaces)
               THEN 'COMPLETED'
           ELSE 'PENDING'
       END,
       CASE
           WHEN EXISTS (SELECT 1 FROM identity.user_accounts)
             OR EXISTS (SELECT 1 FROM identity.organization_units)
             OR EXISTS (SELECT 1 FROM identity.workspaces)
               THEN CURRENT_TIMESTAMP
           ELSE NULL
       END;

COMMENT ON TABLE identity.system_bootstrap IS '生产环境首次身份与工作区初始化的单例完成事实';
