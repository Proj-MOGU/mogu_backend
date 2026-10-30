ALTER TABLE auth_account ADD COLUMN di_hash VARCHAR(64) NOT NULL;
ALTER TABLE auth_account ADD CONSTRAINT uk_auth_account_di_hash UNIQUE (di_hash);

CREATE TABLE auth_sanctioned_di (
    di_hash VARCHAR(64) PRIMARY KEY,
    member_id UUID NOT NULL,
    sanctioned_until TIMESTAMPTZ,
    sanctioned_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE auth_used_identity_verification (
    verification_id VARCHAR(64) PRIMARY KEY,
    used_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE auth_mock_identity_verification (
    verification_id VARCHAR(64) PRIMARY KEY,
    registration_id UUID NOT NULL,
    mock_di VARCHAR(64) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('VERIFIED', 'FAILED')),
    verified_at TIMESTAMPTZ NOT NULL
);
