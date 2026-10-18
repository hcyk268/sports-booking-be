CREATE TABLE court_closures (
    id UUID PRIMARY KEY,
    court_id UUID NOT NULL REFERENCES courts(id),
    closure_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_court_closures_time
        CHECK (start_time < end_time)
);

CREATE INDEX idx_court_closures_court_date
    ON court_closures (court_id, closure_date);