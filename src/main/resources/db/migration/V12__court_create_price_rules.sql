CREATE TABLE court_price_rules (
    id UUID PRIMARY KEY,
    court_id UUID NOT NULL REFERENCES courts(id),
    day_of_week VARCHAR(9) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    price_per_hour NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_court_price_rules_start
        UNIQUE (court_id, day_of_week, start_time),

    CONSTRAINT ck_court_price_rules_day
        CHECK (day_of_week IN (
            'MONDAY',
            'TUESDAY',
            'WEDNESDAY',
            'THURSDAY',
            'FRIDAY',
            'SATURDAY',
            'SUNDAY'
        )),

    CONSTRAINT ck_court_price_rules_time
        CHECK (start_time < end_time),

    CONSTRAINT ck_court_price_rules_amount
        CHECK (price_per_hour > 0)
);