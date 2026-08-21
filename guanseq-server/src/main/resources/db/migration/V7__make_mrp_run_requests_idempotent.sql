CREATE UNIQUE INDEX uk_planning_mrp_run_tenant_request
    ON planning.mrp_runs (tenant_organization_id, request_id)
    WHERE request_id IS NOT NULL;

COMMENT ON INDEX planning.uk_planning_mrp_run_tenant_request IS '同一租户使用相同请求号重试时不得重复创建 MRP 需求快照';
