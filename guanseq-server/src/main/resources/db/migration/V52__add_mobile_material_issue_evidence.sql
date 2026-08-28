ALTER TABLE production.material_issue_events
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'DESKTOP_FORM';

ALTER TABLE production.material_issue_events
    ADD CONSTRAINT ck_material_issue_event_source
    CHECK (source IN ('DESKTOP_FORM', 'MOBILE_SCAN'));

ALTER TABLE production.material_stock_transactions
    ADD COLUMN lot_number VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'DESKTOP_FORM';

ALTER TABLE production.material_stock_transactions
    ADD CONSTRAINT ck_material_stock_txn_source
    CHECK (source IN ('DESKTOP_FORM', 'MOBILE_SCAN'));

COMMENT ON COLUMN production.material_issue_events.source IS '动作来源：桌面表单或移动扫码';
COMMENT ON COLUMN production.material_stock_transactions.lot_number IS '发退料对应的仓库批次证据';
COMMENT ON COLUMN production.material_stock_transactions.source IS '库存事务来源：桌面表单或移动扫码';
