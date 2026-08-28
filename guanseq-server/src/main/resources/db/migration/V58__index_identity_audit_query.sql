CREATE INDEX idx_audit_event_workspace_time
    ON identity.audit_events (workspace_id, occurred_at DESC, id DESC);

CREATE INDEX idx_audit_event_workspace_type_time
    ON identity.audit_events (workspace_id, event_type, occurred_at DESC);

CREATE INDEX idx_audit_event_workspace_object_time
    ON identity.audit_events (workspace_id, object_type, occurred_at DESC);

CREATE INDEX idx_audit_event_workspace_request
    ON identity.audit_events (workspace_id, request_id)
    WHERE request_id IS NOT NULL;
