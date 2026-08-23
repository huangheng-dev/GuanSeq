-- 订单利润冲销与恢复：版本化快照 + IMPACTED 标记 + SUPERSEDED 历史版本
-- 现有 status='SETTLED' 的记录即当前有效版本；重算时旧版本置为 SUPERSEDED，新版本为 SETTLED。
-- 红字发票/退款/反核销过账后，当前版本置为 IMPACTED，等待财务重算。

ALTER TABLE finance.order_profit_settlements
    ADD COLUMN settlement_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN supersedes_id UUID NULL REFERENCES finance.order_profit_settlements(id),
    ADD COLUMN impact_reason VARCHAR(500);

-- 回填：所有既有记录版本号为 1
UPDATE finance.order_profit_settlements SET settlement_version = 1 WHERE settlement_version IS NULL;

-- 替换旧的 (tenant, sales_order_id) 唯一约束为"仅当前版本唯一"的部分唯一索引
ALTER TABLE finance.order_profit_settlements
    DROP CONSTRAINT uk_order_profit_settlement_order;

CREATE UNIQUE INDEX uk_order_profit_current_version
    ON finance.order_profit_settlements (tenant_organization_id, sales_order_id)
    WHERE status IN ('SETTLED', 'IMPACTED');

-- 扩展状态约束：SETTLED=当前有效, IMPACTED=受后续单据影响待重算, SUPERSEDED=已被新版本替代
ALTER TABLE finance.order_profit_settlements
    DROP CONSTRAINT ck_order_profit_settlement_status;
ALTER TABLE finance.order_profit_settlements
    ADD CONSTRAINT ck_order_profit_settlement_status
        CHECK (status IN ('SETTLED', 'IMPACTED', 'SUPERSEDED'));

-- supersedes_id 只能指向同租户同订单的旧版本（应用层保证，这里加索引加速历史查询）
CREATE INDEX idx_order_profit_supersedes
    ON finance.order_profit_settlements (tenant_organization_id, sales_order_id, settlement_version);

-- 台账按状态筛选（IMPACTED 待重算）时的索引
CREATE INDEX idx_order_profit_settlement_lifecycle
    ON finance.order_profit_settlements (tenant_organization_id, status, settled_at DESC);
