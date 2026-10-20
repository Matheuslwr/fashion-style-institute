CREATE TABLE customer_accounts (
    id UUID PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    phone VARCHAR(24) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customer_account_sessions (
    token_hash CHAR(64) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES customer_accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_customer_account_sessions_expiry ON customer_account_sessions(expires_at);
CREATE INDEX idx_customer_account_sessions_account ON customer_account_sessions(account_id);

CREATE TABLE customer_account_email_codes (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES customer_accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    code_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    resend_after TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    UNIQUE (account_id, purpose)
);

ALTER TABLE store_orders
    ADD COLUMN customer_account_id UUID REFERENCES customer_accounts(id) ON DELETE SET NULL;

CREATE INDEX idx_store_orders_customer_account ON store_orders(customer_account_id, created_at DESC);
