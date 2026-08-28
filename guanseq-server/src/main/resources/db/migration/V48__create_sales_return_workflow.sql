ALTER TABLE sales.order_lines
    ADD COLUMN returned_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE sales.order_lines
    DROP CONSTRAINT IF EXISTS ck_sales_order_line_delivered_quantity;
ALTER TABLE sales.order_lines
    ADD CONSTRAINT ck_sales_order_line_fulfillment_quantity CHECK (
        delivered_quantity >= 0
        AND returned_quantity >= 0
        AND returned_quantity <= delivered_quantity
        AND delivered_quantity - returned_quantity <= quantity
    );

ALTER TABLE sales.orders
    DROP CONSTRAINT IF EXISTS ck_sales_order_status;
ALTER TABLE sales.orders
    ADD CONSTRAINT ck_sales_order_status CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'RELEASED',
        'PARTIALLY_SHIPPED', 'SHIPPED', 'PARTIALLY_RETURNED', 'RETURNED'
    ));

CREATE SEQUENCE sales.return_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE sales.returns (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    owning_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    return_number VARCHAR(40) NOT NULL,
    sales_order_id UUID NOT NULL REFERENCES sales.orders(id),
    order_number VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code VARCHAR(40) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    return_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    note VARCHAR(500),
    warehouse_id UUID,
    warehouse_code VARCHAR(40),
    warehouse_name VARCHAR(120),
    location_id UUID,
    location_code VARCHAR(40),
    location_name VARCHAR(120),
    total_return_quantity NUMERIC(18, 4) NOT NULL,
    received_at TIMESTAMPTZ,
    inspected_at TIMESTAMPTZ,
    completed_by UUID,
    create_request_id VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_tenant_number UNIQUE (tenant_organization_id, return_number),
    CONSTRAINT uk_sales_return_tenant_create_request UNIQUE (tenant_organization_id, create_request_id),
    CONSTRAINT ck_sales_return_status CHECK (status IN (
        'PENDING_RECEIPT', 'RECEIVED', 'COMPLETED', 'CANCELLED', 'REVERSED'
    )),
    CONSTRAINT ck_sales_return_quantity CHECK (total_return_quantity > 0),
    CONSTRAINT ck_sales_return_receipt_location CHECK (
        (status IN ('PENDING_RECEIPT', 'CANCELLED') AND warehouse_id IS NULL AND location_id IS NULL)
        OR (status IN ('RECEIVED', 'COMPLETED', 'REVERSED') AND warehouse_id IS NOT NULL AND location_id IS NOT NULL)
    )
);

CREATE INDEX idx_sales_return_tenant_status
    ON sales.returns (tenant_organization_id, status, created_at DESC);
CREATE INDEX idx_sales_return_order
    ON sales.returns (tenant_organization_id, sales_order_id, created_at DESC);

CREATE TABLE sales.return_lines (
    id UUID PRIMARY KEY,
    return_id UUID NOT NULL REFERENCES sales.returns(id) ON DELETE CASCADE,
    order_line_id UUID NOT NULL REFERENCES sales.order_lines(id),
    line_number INTEGER NOT NULL,
    material_id UUID NOT NULL,
    material_code VARCHAR(60) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_specification VARCHAR(240),
    unit VARCHAR(20) NOT NULL,
    authorized_quantity NUMERIC(18, 4) NOT NULL,
    received_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0,
    accepted_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0,
    rejected_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0,
    lot_number VARCHAR(80) NOT NULL DEFAULT '',
    inspection_balance_id UUID,
    receipt_movement_id UUID,
    stock_summary VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uk_sales_return_line_order_line UNIQUE (return_id, order_line_id),
    CONSTRAINT ck_sales_return_line_quantities CHECK (
        authorized_quantity > 0
        AND received_quantity >= 0
        AND accepted_quantity >= 0
        AND rejected_quantity >= 0
        AND received_quantity <= authorized_quantity
        AND accepted_quantity + rejected_quantity <= received_quantity
    )
);

CREATE INDEX idx_sales_return_line_order_line
    ON sales.return_lines (order_line_id);

CREATE TABLE sales.return_events (
    id UUID PRIMARY KEY,
    tenant_organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    return_id UUID NOT NULL REFERENCES sales.returns(id),
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_event_request UNIQUE (tenant_organization_id, request_id),
    CONSTRAINT ck_sales_return_event_action CHECK (action IN (
        'CREATED', 'CANCELLED', 'RECEIVED', 'INSPECTED', 'RECEIPT_REVERSED'
    )),
    CONSTRAINT ck_sales_return_event_status CHECK (
        from_status IS NULL OR from_status IN ('PENDING_RECEIPT', 'RECEIVED', 'COMPLETED', 'CANCELLED', 'REVERSED')
    )
);

CREATE INDEX idx_sales_return_event_record
    ON sales.return_events (tenant_organization_id, return_id, occurred_at DESC);

COMMENT ON COLUMN sales.order_lines.delivered_quantity IS '累计毛发货数量；允许替换发货后超过订单数量，但与累计退货相减后的净发货不得超过订单数量';
COMMENT ON COLUMN sales.order_lines.returned_quantity IS '客户实物已入库且尚未冲回的累计销售退货数量';
COMMENT ON TABLE sales.returns IS '销售经理授权、仓库待检收货与质量处置的客户退货事实';
COMMENT ON TABLE sales.return_lines IS '销售退货行及待检、合格、隔离库存证据';
COMMENT ON TABLE sales.return_events IS '销售退货创建、收货、质量判定、取消和收货冲回的不可变责任证据';
