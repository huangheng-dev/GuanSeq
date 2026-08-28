ALTER TABLE procurement.purchase_receipts
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'DESKTOP_FORM';

ALTER TABLE procurement.purchase_receipts
    ADD CONSTRAINT ck_purchase_receipt_source
    CHECK (source IN ('DESKTOP_FORM', 'MOBILE_SCAN'));

COMMENT ON COLUMN procurement.purchase_receipts.source IS
    '采购收货操作入口；移动扫码仍复用同一采购收货、库存和质量事实';
