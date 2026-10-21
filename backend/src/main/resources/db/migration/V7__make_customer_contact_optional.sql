ALTER TABLE customer_accounts
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN phone DROP NOT NULL;

ALTER TABLE customer_accounts
    ADD CONSTRAINT customer_accounts_has_contact
    CHECK (email IS NOT NULL OR phone IS NOT NULL);
