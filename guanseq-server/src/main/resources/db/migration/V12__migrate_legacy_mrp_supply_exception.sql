UPDATE planning.mrp_run_exceptions
SET code = 'SCHEDULED_RECEIPTS_UNAVAILABLE',
    message = '采购订单和生产订单尚未形成计划接收事实，当前可用量不包含可信在途。',
    resolution_path = '先建设采购订单或生产订单的计划接收，再进入净需求展开。'
WHERE code = 'SUPPLY_POSITION_UNAVAILABLE';

COMMENT ON COLUMN planning.mrp_run_exceptions.code IS 'MRP 前置条件异常码；V12 将旧供给位置缺失归并为计划接收缺失';
