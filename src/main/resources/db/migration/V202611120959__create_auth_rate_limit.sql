CREATE TABLE auth_rate_limit_window (
    rule_name VARCHAR(40) NOT NULL,
    limit_key VARCHAR(80) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    PRIMARY KEY (rule_name, limit_key, window_start)
);
CREATE INDEX idx_auth_rate_limit_window_start ON auth_rate_limit_window (window_start);

CREATE TABLE auth_rate_limit_bucket (
    rule_name VARCHAR(40) NOT NULL,
    limit_key VARCHAR(80) NOT NULL,
    tokens INTEGER NOT NULL,
    refilled_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (rule_name, limit_key)
);

CREATE TABLE auth_rate_limit_streak (
    rule_name VARCHAR(40) NOT NULL,
    limit_key VARCHAR(80) NOT NULL,
    consecutive_failures INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (rule_name, limit_key)
);
