-- One full-payment obligation per booking. A retry creates an attempt, not a payment.
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    settled_transaction_id UUID,
    settled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payments_booking UNIQUE (booking_id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0 AND amount = trunc(amount)),
    CONSTRAINT ck_payments_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_payments_status CHECK (status IN (
        'PENDING', 'SUCCEEDED', 'EXPIRED', 'CANCELLED'
    )),
    CONSTRAINT ck_payments_settlement CHECK (
        (status = 'SUCCEEDED' AND settled_transaction_id IS NOT NULL
            AND settled_at IS NOT NULL)
        OR (status <> 'SUCCEEDED' AND settled_transaction_id IS NULL
            AND settled_at IS NULL)
    )
);

CREATE INDEX idx_payments_pending_expiry ON payments (expires_at, id)
    WHERE status = 'PENDING';

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    attempt_number INT NOT NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'SEPAY',
    environment VARCHAR(20) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    provider_order_id VARCHAR(150),
    provider_order_status VARCHAR(100),
    payment_method VARCHAR(50),
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'PREPARED',
    expires_at TIMESTAMPTZ NOT NULL,
    -- Exact ordered form fields, including signature, never the merchant secret.
    checkout_payload JSONB NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    idempotency_request_hash VARCHAR(64) NOT NULL,
    last_reconciled_at TIMESTAMPTZ,
    next_reconcile_at TIMESTAMPTZ,
    reconciliation_attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payment_attempts_number UNIQUE (payment_id, attempt_number),
    CONSTRAINT uk_payment_attempts_idempotency UNIQUE (payment_id, idempotency_key),
    CONSTRAINT uk_payment_attempts_invoice
        UNIQUE (provider, environment, merchant_id, invoice_number),
    CONSTRAINT uk_payment_attempts_order
        UNIQUE (provider, environment, merchant_id, provider_order_id),
    CONSTRAINT uk_payment_attempts_payment_identity UNIQUE (id, payment_id),
    CONSTRAINT ck_payment_attempts_number CHECK (attempt_number > 0),
    CONSTRAINT ck_payment_attempts_provider CHECK (provider = 'SEPAY'),
    CONSTRAINT ck_payment_attempts_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT ck_payment_attempts_amount CHECK (amount > 0 AND amount = trunc(amount)),
    CONSTRAINT ck_payment_attempts_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_payment_attempts_status CHECK (status IN (
        'PREPARED', 'PENDING', 'UNKNOWN', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'
    )),
    CONSTRAINT ck_payment_attempts_payload CHECK (jsonb_typeof(checkout_payload) = 'array'),
    CONSTRAINT ck_payment_attempts_idempotency_key CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT ck_payment_attempts_request_hash
        CHECK (idempotency_request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_attempts_reconciliation CHECK (reconciliation_attempts >= 0)
);

-- UNKNOWN blocks a new attempt until the old outcome has been reconciled.
CREATE UNIQUE INDEX uk_payment_attempts_active ON payment_attempts (payment_id)
    WHERE status IN ('PREPARED', 'PENDING', 'UNKNOWN');

CREATE INDEX idx_payment_attempts_reconcile ON payment_attempts (next_reconcile_at, id)
    WHERE next_reconcile_at IS NOT NULL;

COMMENT ON COLUMN payment_attempts.checkout_payload IS
    'Ordered array of {name, value} form fields; JSONB objects do not preserve field order. Restrict access and do not log signed payloads.';
COMMENT ON COLUMN payment_attempts.expires_at IS
    'Local booking hold deadline, not evidence of expiration or cancellation at SePay.';
COMMENT ON COLUMN payment_attempts.status IS
    'Local workflow state. EXPIRED/CANCELLED do not prove the gateway can no longer collect money.';
