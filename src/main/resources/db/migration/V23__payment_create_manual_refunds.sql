CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    payment_transaction_id UUID NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    reason VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    idempotency_request_hash VARCHAR(64) NOT NULL,
    requested_by UUID REFERENCES users(id),
    processed_by UUID REFERENCES users(id),
    processing_started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    refund_reference VARCHAR(255),
    evidence_asset_id UUID REFERENCES shared_upload_assets(id),
    admin_note VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_refunds_transaction
        FOREIGN KEY (payment_transaction_id, payment_id)
        REFERENCES payment_transactions(id, payment_id),
    CONSTRAINT uk_payment_refunds_idempotency UNIQUE (payment_id, idempotency_key),
    CONSTRAINT ck_payment_refunds_amount CHECK (amount > 0 AND amount = trunc(amount)),
    CONSTRAINT ck_payment_refunds_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_payment_refunds_status CHECK (status IN (
        'REQUESTED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'CANCELLED'
    )),
    CONSTRAINT ck_payment_refunds_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_payment_refunds_key CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT ck_payment_refunds_request_hash
        CHECK (idempotency_request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_refunds_processing CHECK (
        status <> 'PROCESSING'
        OR (processed_by IS NOT NULL AND processing_started_at IS NOT NULL)
    ),
    CONSTRAINT ck_payment_refunds_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND processed_by IS NOT NULL
            AND refund_reference IS NOT NULL AND length(btrim(refund_reference)) > 0)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE INDEX idx_payment_refunds_payment ON payment_refunds (payment_id, created_at);
CREATE INDEX idx_payment_refunds_transaction ON payment_refunds (payment_transaction_id);
CREATE INDEX idx_payment_refunds_pending ON payment_refunds (created_at, id)
    WHERE status IN ('REQUESTED', 'PROCESSING');

CREATE TABLE payment_refund_status_history (
    id UUID PRIMARY KEY,
    refund_id UUID NOT NULL REFERENCES payment_refunds(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by UUID REFERENCES users(id),
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_refund_history_from CHECK (
        from_status IS NULL
        OR from_status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_payment_refund_history_to CHECK (
        to_status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'CANCELLED')
    )
);

CREATE INDEX idx_payment_refund_history_created
    ON payment_refund_status_history (refund_id, created_at, id);

COMMENT ON TABLE payment_refunds IS
    'Manual admin refunds. Application must lock the source transaction and enforce currency and total REQUESTED/PROCESSING/COMPLETED refund budget before reserving money.';
