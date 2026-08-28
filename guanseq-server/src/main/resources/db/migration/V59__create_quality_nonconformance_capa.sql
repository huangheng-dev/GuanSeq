CREATE SEQUENCE quality.nonconformance_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE quality.nonconformances (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    case_number VARCHAR(40) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    inspection_id UUID NOT NULL REFERENCES quality.inspections(id),
    inspection_number VARCHAR(40) NOT NULL,
    source_document_id UUID NOT NULL,
    source_document_number VARCHAR(40) NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    supplier_id UUID,
    supplier_code VARCHAR(40),
    supplier_name VARCHAR(160),
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    nonconforming_quantity NUMERIC(19, 6) NOT NULL,
    defect_description VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    severity VARCHAR(16),
    immediate_containment VARCHAR(1000),
    review_conclusion VARCHAR(1000),
    capa_required BOOLEAN,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    disposition_type VARCHAR(32),
    disposition_decision VARCHAR(1000),
    disposition_evidence VARCHAR(1000),
    disposition_owner VARCHAR(120),
    disposed_by UUID,
    disposed_at TIMESTAMPTZ,
    root_cause VARCHAR(1000),
    corrective_action VARCHAR(1000),
    action_owner VARCHAR(120),
    action_due_date DATE,
    action_completion_evidence VARCHAR(1000),
    action_completed_by UUID,
    action_completed_at TIMESTAMPTZ,
    verification_effective BOOLEAN,
    verification_conclusion VARCHAR(1000),
    verified_by UUID,
    verified_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    create_request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quality_nc_tenant_number UNIQUE (tenant_organization_id, case_number),
    CONSTRAINT uk_quality_nc_inspection UNIQUE (tenant_organization_id, inspection_id),
    CONSTRAINT uk_quality_nc_create_request UNIQUE (tenant_organization_id, workspace_id, create_request_id),
    CONSTRAINT ck_quality_nc_source CHECK (source_type IN ('INCOMING_INSPECTION', 'FINAL_INSPECTION')),
    CONSTRAINT ck_quality_nc_status CHECK (status IN ('OPEN', 'REVIEWED', 'ACTION_REQUIRED', 'ACTION_IN_PROGRESS', 'VERIFICATION_PENDING', 'CLOSED')),
    CONSTRAINT ck_quality_nc_severity CHECK (severity IS NULL OR severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_quality_nc_disposition CHECK (disposition_type IS NULL OR disposition_type IN ('RETURN_TO_SUPPLIER', 'REWORK', 'SCRAP', 'CONCESSION', 'SORTING', 'OTHER')),
    CONSTRAINT ck_quality_nc_quantity CHECK (nonconforming_quantity > 0),
    CONSTRAINT ck_quality_nc_supplier_snapshot CHECK (
        (source_type = 'INCOMING_INSPECTION' AND supplier_id IS NOT NULL AND supplier_code IS NOT NULL AND supplier_name IS NOT NULL)
        OR source_type = 'FINAL_INSPECTION'
    ),
    CONSTRAINT ck_quality_nc_closed CHECK (
        status <> 'CLOSED' OR (verification_effective = TRUE AND verified_by IS NOT NULL AND verified_at IS NOT NULL AND closed_at IS NOT NULL)
    )
);

CREATE INDEX idx_quality_nc_workspace_status_time
    ON quality.nonconformances (tenant_organization_id, workspace_id, status, created_at DESC);
CREATE INDEX idx_quality_nc_workspace_due
    ON quality.nonconformances (tenant_organization_id, workspace_id, action_due_date)
    WHERE status IN ('ACTION_REQUIRED', 'ACTION_IN_PROGRESS', 'VERIFICATION_PENDING');
CREATE INDEX idx_quality_nc_material
    ON quality.nonconformances (tenant_organization_id, workspace_id, material_id, created_at DESC);

CREATE TABLE quality.nonconformance_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    nonconformance_id UUID NOT NULL REFERENCES quality.nonconformances(id),
    actor_user_id UUID NOT NULL,
    actor_username VARCHAR(160) NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(1000),
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quality_nc_event_request UNIQUE (tenant_organization_id, workspace_id, request_id)
);

CREATE INDEX idx_quality_nc_event_case_time
    ON quality.nonconformance_events (nonconformance_id, occurred_at ASC, id ASC);

INSERT INTO quality.nonconformances (
    id, tenant_organization_id, owning_organization_id, workspace_id, case_number, source_type,
    inspection_id, inspection_number, source_document_id, source_document_number, order_id, order_number,
    supplier_id, supplier_code, supplier_name, material_id, material_code, material_name, material_specification,
    unit, nonconforming_quantity, defect_description, status, create_request_id, created_by, created_at,
    updated_by, updated_at
)
SELECT gen_random_uuid(), i.tenant_organization_id, i.owning_organization_id, i.workspace_id,
       'NCR-' || to_char(COALESCE(i.completed_at, i.created_at) AT TIME ZONE 'UTC', 'YYYYMMDD') || '-' ||
           lpad(nextval('quality.nonconformance_number_seq')::text, 6, '0'),
       CASE i.inspection_type WHEN 'INCOMING' THEN 'INCOMING_INSPECTION' ELSE 'FINAL_INSPECTION' END,
       i.id, i.inspection_number, i.source_id, i.source_number, i.order_id, i.order_number,
       i.supplier_id, i.supplier_code, i.supplier_name, i.material_id, i.material_code, i.material_name,
       i.material_specification, i.unit, i.rejected_quantity, i.defect_description, 'OPEN',
       COALESCE(i.decision_request_id, 'v59-backfill-' || i.id::text),
       COALESCE(i.completed_by, i.created_by), COALESCE(i.completed_at, i.created_at),
       COALESCE(i.completed_by, i.created_by), COALESCE(i.completed_at, i.created_at)
FROM quality.inspections i
WHERE i.status = 'COMPLETED' AND i.rejected_quantity > 0
ON CONFLICT (tenant_organization_id, inspection_id) DO NOTHING;

INSERT INTO quality.nonconformance_events (
    id, tenant_organization_id, workspace_id, nonconformance_id, actor_user_id, actor_username, action,
    from_status, to_status, reason, request_id, details, occurred_at
)
SELECT gen_random_uuid(), n.tenant_organization_id, n.workspace_id, n.id, n.created_by, n.created_by::text, 'CREATED',
       NULL, 'OPEN', '检验发现不合格数量，自动建立不合格记录', n.create_request_id,
       jsonb_build_object('inspectionNumber', n.inspection_number,
                          'nonconformingQuantity', n.nonconforming_quantity,
                          'sourceType', n.source_type), n.created_at
FROM quality.nonconformances n
ON CONFLICT (tenant_organization_id, workspace_id, request_id) DO NOTHING;

COMMENT ON TABLE quality.nonconformances IS '质量模块拥有的不合格评审、处置决定与 CAPA 当前状态；不直接执行下游实物事务';
COMMENT ON TABLE quality.nonconformance_events IS '不合格评审与 CAPA 的不可变责任事件';
