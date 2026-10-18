CREATE TABLE court_operating_hours (
    id UUID PRIMARY KEY,
    court_id UUID NOT NULL REFERENCES courts(id),
    day_of_week VARCHAR(9) NOT NULL,
    opening_time TIME NOT NULL,
    closing_time TIME NOT NULL,
    slot_duration_minutes INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_court_operating_hours_day
        UNIQUE (court_id, day_of_week),

    CONSTRAINT ck_court_operating_hours_day
        CHECK (day_of_week IN (
            'MONDAY',
            'TUESDAY',
            'WEDNESDAY',
            'THURSDAY',
            'FRIDAY',
            'SATURDAY',
            'SUNDAY'
        )),

    CONSTRAINT ck_court_operating_hours_time
        CHECK (opening_time < closing_time),

    CONSTRAINT ck_court_operating_hours_slot
        CHECK (slot_duration_minutes BETWEEN 15 AND 240)
);