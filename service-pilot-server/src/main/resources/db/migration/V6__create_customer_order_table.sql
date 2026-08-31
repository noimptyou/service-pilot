CREATE TABLE customer_order (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    tracking_number VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_customer_order_order_number UNIQUE (order_number)
);

CREATE INDEX idx_customer_order_customer_name
    ON customer_order(customer_name);
