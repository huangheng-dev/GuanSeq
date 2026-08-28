ALTER TABLE procurement.purchase_order_lines
    DROP CONSTRAINT ck_purchase_order_line_quantities,
    DROP CONSTRAINT ck_purchase_order_line_returned_quantity;

ALTER TABLE procurement.purchase_order_lines
    ADD CONSTRAINT ck_purchase_order_line_quantities
    CHECK (
        ordered_quantity > 0
        AND received_quantity >= 0
        AND returned_quantity >= 0
        AND returned_quantity <= received_quantity
        AND received_quantity - returned_quantity <= ordered_quantity
    );

COMMENT ON CONSTRAINT ck_purchase_order_line_quantities ON procurement.purchase_order_lines IS
    '采购订单行按净收货控制数量：累计毛收货减累计已退货不得超过订购量，允许退货后补收';
