ALTER TABLE auth_account DROP CONSTRAINT auth_account_status_check;
ALTER TABLE auth_account ADD CONSTRAINT auth_account_status_check
    CHECK (status IN ('ACTIVE', 'SANCTIONED', 'WITHDRAWN'));
ALTER TABLE auth_account ADD COLUMN withdrawn_at TIMESTAMPTZ;
ALTER TABLE auth_account ALTER COLUMN di_hash DROP NOT NULL;
ALTER TABLE auth_account ADD CONSTRAINT ck_auth_account_withdrawn_at
    CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL));
ALTER TABLE auth_account ADD CONSTRAINT ck_auth_account_di_hash
    CHECK (status = 'WITHDRAWN' OR di_hash IS NOT NULL);
CREATE INDEX idx_auth_account_withdrawn_at ON auth_account (withdrawn_at)
    WHERE di_hash IS NOT NULL AND status = 'WITHDRAWN';
