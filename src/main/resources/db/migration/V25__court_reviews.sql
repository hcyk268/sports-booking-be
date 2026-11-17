CREATE TABLE court_reviews (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    court_id UUID NOT NULL REFERENCES courts(id),
    author_id UUID NOT NULL REFERENCES users(id),
    stars INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment VARCHAR(2000) NOT NULL,
    reply VARCHAR(2000),
    deleted BOOLEAN NOT NULL DEFAULT false,
    hidden BOOLEAN NOT NULL DEFAULT false,
    moderation_reason VARCHAR(500),
    moderated_by UUID REFERENCES users(id),
    moderated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_reviews_court_visible ON court_reviews(court_id, created_at DESC, id DESC) WHERE NOT hidden AND NOT deleted;
