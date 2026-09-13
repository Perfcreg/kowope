CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    mfa_secret VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_user_role (
    user_id UUID NOT NULL REFERENCES app_user (id),
    role VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role)
);
