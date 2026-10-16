CREATE TABLE store_orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(20) NOT NULL UNIQUE,
    idempotency_key UUID NOT NULL UNIQUE,
    customer_name VARCHAR(100) NOT NULL,
    customer_email VARCHAR(120) NOT NULL,
    customer_phone VARCHAR(24) NOT NULL,
    postal_code VARCHAR(8) NOT NULL,
    state VARCHAR(2) NOT NULL,
    city VARCHAR(80) NOT NULL,
    address VARCHAR(120) NOT NULL,
    address_number VARCHAR(12) NOT NULL,
    complement VARCHAR(80),
    neighborhood VARCHAR(80) NOT NULL,
    distance_km INTEGER NOT NULL CHECK (distance_km >= 0),
    subtotal_cents BIGINT NOT NULL CHECK (subtotal_cents >= 0),
    shipping_cents BIGINT NOT NULL CHECK (shipping_cents >= 0),
    total_cents BIGINT NOT NULL CHECK (total_cents > 0),
    payment_status VARCHAR(24) NOT NULL,
    pix_payload TEXT,
    pix_qr_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMPTZ
);

CREATE TABLE store_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
    product_slug VARCHAR(160) NOT NULL,
    product_name VARCHAR(140) NOT NULL,
    color VARCHAR(80) NOT NULL,
    size VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents >= 0)
);

CREATE INDEX idx_store_orders_created_at ON store_orders(created_at DESC);
CREATE INDEX idx_store_orders_payment_status ON store_orders(payment_status);
CREATE INDEX idx_store_order_items_variant ON store_order_items(product_slug, color, size, order_id);
