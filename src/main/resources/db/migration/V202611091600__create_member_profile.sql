CREATE TABLE member_profile (
    member_id     UUID        PRIMARY KEY,
    registered_at TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);
