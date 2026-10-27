CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    booking_code VARCHAR(30) NOT NULL,
    customer_id UUID NOT NULL REFERENCES users(id),
    total_amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL,
    hold_expires_at TIMESTAMPTZ,
    idempotency_key VARCHAR(100) NOT NULL,
    idempotency_request_hash VARCHAR(64) NOT NULL,
    cancelled_by UUID REFERENCES users(id),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    confirmed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_bookings_code UNIQUE (booking_code),
    CONSTRAINT uk_bookings_customer_idempotency
        UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ck_bookings_idempotency_request_hash
        CHECK (idempotency_request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_bookings_total_amount CHECK (total_amount > 0),
    CONSTRAINT ck_bookings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_bookings_status CHECK (status IN (
        'HELD',
        'PENDING_PAYMENT',
        'CONFIRMED',
        'CANCELLED',
        'EXPIRED',
        'COMPLETED'
    )),
    CONSTRAINT ck_bookings_hold_expiry CHECK (
        status <> 'HELD' OR hold_expires_at IS NOT NULL
    ),
    CONSTRAINT ck_bookings_cancellation CHECK (
        (cancelled_at IS NULL AND cancelled_by IS NULL
            AND cancellation_reason IS NULL)
        OR cancelled_at IS NOT NULL
    )
);

CREATE TABLE booking_slots (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    court_id UUID NOT NULL REFERENCES courts(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    price_amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_booking_slots_selection
        UNIQUE (booking_id, court_id, start_at, end_at),
    CONSTRAINT ck_booking_slots_time CHECK (start_at < end_at),
    CONSTRAINT ck_booking_slots_price CHECK (price_amount > 0),
    CONSTRAINT ck_booking_slots_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_slots_status CHECK (status IN (
        'HELD',
        'PENDING_PAYMENT',
        'CONFIRMED',
        'CANCELLED',
        'EXPIRED',
        'COMPLETED'
    )),
    CONSTRAINT ex_booking_slots_active_time
        EXCLUDE USING gist (
            court_id WITH =,
            tstzrange(start_at, end_at, '[)') WITH &&
        )
        WHERE (status IN ('HELD', 'PENDING_PAYMENT', 'CONFIRMED'))
);

CREATE TABLE booking_status_history (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by UUID REFERENCES users(id),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_booking_history_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'HELD',
            'PENDING_PAYMENT',
            'CONFIRMED',
            'CANCELLED',
            'EXPIRED',
            'COMPLETED'
        )
    ),
    CONSTRAINT ck_booking_history_to_status CHECK (to_status IN (
        'HELD',
        'PENDING_PAYMENT',
        'CONFIRMED',
        'CANCELLED',
        'EXPIRED',
        'COMPLETED'
    ))
);

CREATE INDEX idx_bookings_customer_created
    ON bookings (customer_id, created_at DESC);

CREATE INDEX idx_bookings_status_hold_expiry
    ON bookings (status, hold_expires_at)
    WHERE status IN ('HELD', 'PENDING_PAYMENT');

CREATE INDEX idx_booking_slots_booking
    ON booking_slots (booking_id);

CREATE INDEX idx_booking_slots_court_start
    ON booking_slots (court_id, start_at);

CREATE INDEX idx_booking_status_history_booking_created
    ON booking_status_history (booking_id, created_at);
