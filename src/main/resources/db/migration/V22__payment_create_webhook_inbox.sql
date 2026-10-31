CREATE TABLE payment_webhook_inbox (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL DEFAULT 'SEPAY',
    environment VARCHAR(20) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    -- Adapter-generated stable identity, including event type/state as needed.
    deduplication_key VARCHAR(255) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    provider_transaction_id VARCHAR(150),
    invoice_number VARCHAR(100),
    provider_occurred_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMPTZ,
    lock_token UUID,
    processed_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payment_webhook_inbox_dedup
        UNIQUE (provider, environment, merchant_id, deduplication_key),
    CONSTRAINT ck_payment_webhook_inbox_provider CHECK (provider = 'SEPAY'),
    CONSTRAINT ck_payment_webhook_inbox_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT ck_payment_webhook_inbox_key CHECK (length(btrim(deduplication_key)) > 0),
    CONSTRAINT ck_payment_webhook_inbox_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_webhook_inbox_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_payment_webhook_inbox_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'PROCESSED', 'REVIEW_REQUIRED', 'FAILED'
    )),
    CONSTRAINT ck_payment_webhook_inbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_payment_webhook_inbox_lock CHECK (
        (status = 'PROCESSING' AND locked_until IS NOT NULL AND lock_token IS NOT NULL)
        OR (status <> 'PROCESSING' AND locked_until IS NULL AND lock_token IS NULL)
    ),
    CONSTRAINT ck_payment_webhook_inbox_processed CHECK (
        status <> 'PROCESSED' OR processed_at IS NOT NULL
    )
);

CREATE INDEX idx_payment_webhook_inbox_pending
    ON payment_webhook_inbox (available_at, received_at, id) WHERE status = 'PENDING';
CREATE INDEX idx_payment_webhook_inbox_expired_lease
    ON payment_webhook_inbox (locked_until, id) WHERE status = 'PROCESSING';
CREATE INDEX idx_payment_webhook_inbox_review
    ON payment_webhook_inbox (received_at, id) WHERE status IN ('REVIEW_REQUIRED', 'FAILED');
CREATE INDEX idx_payment_webhook_inbox_invoice
    ON payment_webhook_inbox (provider, environment, merchant_id, invoice_number);

COMMENT ON TABLE payment_webhook_inbox IS
    'Authenticated IPNs only. Commit before ACK; retain payload without authorization headers. Never deduplicate solely by transaction ID because paid and void notifications differ.';
