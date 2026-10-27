CREATE TABLE auth_used_nonce (
    nonce_hash CHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auth_used_nonce_expires_at ON auth_used_nonce (expires_at);
