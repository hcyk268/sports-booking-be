-- Retain genuine gateway transactions even when they cannot be matched to an attempt.
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    payment_id UUID,
    payment_attempt_id UUID,
    provider VARCHAR(30) NOT NULL DEFAULT 'SEPAY',
    environment VARCHAR(20) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    provider_transaction_id VARCHAR(150) NOT NULL,
    provider_transaction_reference VARCHAR(150),
    provider_order_id VARCHAR(150),
    invoice_number VARCHAR(100),
    transaction_type VARCHAR(50) NOT NULL,
    provider_status VARCHAR(100) NOT NULL,
    payment_method VARCHAR(50),
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    occurred_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolution_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    resolution_reason VARCHAR(1000),
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_transactions_attempt
        FOREIGN KEY (payment_attempt_id, payment_id)
        REFERENCES payment_attempts(id, payment_id) MATCH FULL,
    CONSTRAINT uk_payment_transactions_provider
        UNIQUE (provider, environment, merchant_id, provider_transaction_id),
    CONSTRAINT uk_payment_transactions_payment_identity UNIQUE (id, payment_id),
    CONSTRAINT ck_payment_transactions_provider CHECK (provider = 'SEPAY'),
    CONSTRAINT ck_payment_transactions_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT ck_payment_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_transactions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_transactions_resolution CHECK (resolution_status IN (
        'PENDING', 'APPLIED', 'REVIEW_REQUIRED', 'RESOLVED'
    )),
    CONSTRAINT ck_payment_transactions_resolved_at CHECK (
        resolution_status <> 'RESOLVED' OR resolved_at IS NOT NULL
    )
);

-- The chosen settlement must belong to this payment; late/extra receipts remain separate.
ALTER TABLE payments ADD CONSTRAINT fk_payments_settlement
    FOREIGN KEY (settled_transaction_id, id)
    REFERENCES payment_transactions(id, payment_id);

CREATE UNIQUE INDEX uk_payment_transactions_applied ON payment_transactions (payment_id)
    WHERE resolution_status = 'APPLIED';
CREATE INDEX idx_payment_transactions_attempt ON payment_transactions (payment_attempt_id);
CREATE INDEX idx_payment_transactions_payment ON payment_transactions (payment_id, created_at);
CREATE INDEX idx_payment_transactions_review ON payment_transactions (received_at, id)
    WHERE resolution_status IN ('PENDING', 'REVIEW_REQUIRED');

CREATE TABLE payment_status_history (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by UUID REFERENCES users(id),
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_history_from_status CHECK (
        from_status IS NULL OR from_status IN ('PENDING', 'SUCCEEDED', 'EXPIRED', 'CANCELLED')
    ),
    CONSTRAINT ck_payment_history_to_status CHECK (
        to_status IN ('PENDING', 'SUCCEEDED', 'EXPIRED', 'CANCELLED')
    )
);

CREATE INDEX idx_payment_status_history_payment_created
    ON payment_status_history (payment_id, created_at, id);

COMMENT ON COLUMN payment_transactions.provider_transaction_id IS
    'Stable gateway transaction identity chosen by the adapter; notification retries must map to the same row. Void notifications can update its provider status; inbox preserves each event.';
COMMENT ON COLUMN payment_transactions.resolution_status IS
    'Business allocation/review state, independent of the gateway financial status.';
