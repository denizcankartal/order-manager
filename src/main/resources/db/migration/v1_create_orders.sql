CREATE TABLE orders (
    client_order_id   VARCHAR(64) PRIMARY KEY,
    order_id          BIGINT UNIQUE,
    symbol            VARCHAR(32) NOT NULL,
    side              VARCHAR(4)  NOT NULL,
    price             NUMERIC(20, 8) NOT NULL,
    orig_qty          NUMERIC(20, 8) NOT NULL,
    executed_qty      NUMERIC(20, 8) NOT NULL,
    status            VARCHAR(32)    NOT NULL,
    time              BIGINT,
    update_time       BIGINT,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    last_modified     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_symbol_status
    ON orders (status);

CREATE INDEX idx_orders_order_id
    ON orders (order_id);
