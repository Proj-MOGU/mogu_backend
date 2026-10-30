CREATE TABLE auth_account (
    member_id        UUID         PRIMARY KEY,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SANCTIONED')),
    sanctioned_until TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL
);
CREATE TABLE auth_social_identity (
    provider         VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO', 'GOOGLE')),
    provider_subject VARCHAR(255) NOT NULL,
    member_id        UUID         NOT NULL REFERENCES auth_account (member_id),
    linked_at        TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (provider, provider_subject)
);
CREATE INDEX idx_auth_social_identity_member_id ON auth_social_identity (member_id);
CREATE TABLE auth_refresh_token (
    id          UUID        PRIMARY KEY,
    family_id   UUID        NOT NULL,
    member_id   UUID        NOT NULL REFERENCES auth_account (member_id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    CHECK (expires_at > issued_at)
);
CREATE INDEX idx_auth_refresh_token_family_id ON auth_refresh_token (family_id);
CREATE INDEX idx_auth_refresh_token_member_id ON auth_refresh_token (member_id);
