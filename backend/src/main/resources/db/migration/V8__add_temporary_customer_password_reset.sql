ALTER TABLE customer_accounts
    ADD COLUMN password_reset_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_reset_expires_at TIMESTAMPTZ;
