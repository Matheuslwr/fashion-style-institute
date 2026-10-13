CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(140) NOT NULL,
    slug VARCHAR(160) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
    promotional_price_cents BIGINT CHECK (promotional_price_cents IS NULL OR (promotional_price_cents >= 0 AND promotional_price_cents < price_cents)),
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    category_id UUID NOT NULL REFERENCES categories(id)
);

CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(180) NOT NULL,
    color VARCHAR(80),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0)
);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(80) NOT NULL UNIQUE,
    color VARCHAR(80) NOT NULL,
    size VARCHAR(40) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0),
    UNIQUE (product_id, color, size)
);

CREATE INDEX idx_products_active_category ON products(active, category_id);
CREATE INDEX idx_products_featured ON products(featured) WHERE active = TRUE;
CREATE INDEX idx_product_variants_product ON product_variants(product_id);