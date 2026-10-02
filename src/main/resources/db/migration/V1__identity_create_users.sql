CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_status
       CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED','PENDING_VERIFICATION'))
);

CREATE UNIQUE INDEX uq_users_email
    ON users (LOWER(email));