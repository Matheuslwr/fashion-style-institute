ALTER TABLE products
    ADD COLUMN product_type VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN watch_width_cm NUMERIC(6, 2),
    ADD COLUMN watch_length_cm NUMERIC(6, 2),
    ADD COLUMN watch_height_cm NUMERIC(6, 2),
    ADD COLUMN watch_dial_cm NUMERIC(6, 2);

ALTER TABLE products
    ADD CONSTRAINT chk_product_type_measurements CHECK (
        (product_type = 'STANDARD'
            AND watch_width_cm IS NULL
            AND watch_length_cm IS NULL
            AND watch_height_cm IS NULL
            AND watch_dial_cm IS NULL)
        OR
        (product_type = 'WATCH'
            AND watch_width_cm IS NOT NULL
            AND watch_width_cm > 0
            AND watch_length_cm IS NOT NULL
            AND watch_length_cm > 0
            AND watch_height_cm IS NOT NULL
            AND watch_height_cm > 0
            AND watch_dial_cm IS NOT NULL
            AND watch_dial_cm > 0)
    );
