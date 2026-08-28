ALTER TABLE production.operation_events
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'DESKTOP_FORM';

UPDATE production.operation_events
SET source = 'SYSTEM'
WHERE action = 'CREATED';

ALTER TABLE production.operation_events
    ADD CONSTRAINT ck_operation_event_source
    CHECK (source IN ('SYSTEM', 'DESKTOP_FORM', 'MOBILE_SCAN'));

ALTER TABLE production.work_reports
    ADD COLUMN operation_task_id UUID REFERENCES production.operation_tasks(id),
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'DESKTOP_FORM',
    ADD CONSTRAINT ck_work_report_source
        CHECK (source IN ('DESKTOP_FORM', 'MOBILE_SCAN')),
    ADD CONSTRAINT ck_work_report_mobile_task
        CHECK (source <> 'MOBILE_SCAN' OR operation_task_id IS NOT NULL);

CREATE INDEX idx_work_report_operation_task
    ON production.work_reports (tenant_organization_id, operation_task_id)
    WHERE operation_task_id IS NOT NULL;

COMMENT ON COLUMN production.operation_events.source IS '工序动作来源：系统生成、桌面表单或移动扫码';
COMMENT ON COLUMN production.work_reports.operation_task_id IS '移动扫码报工关联的最后一道已完工工序证据';
COMMENT ON COLUMN production.work_reports.source IS '生产报工输入来源；移动扫码仍复用正式生产报工事实';
