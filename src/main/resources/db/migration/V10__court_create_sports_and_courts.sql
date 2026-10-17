CREATE TABLE sports (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sports_code UNIQUE (code)
);

CREATE TABLE courts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    sport_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    ward VARCHAR(100),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_courts_owner
        FOREIGN KEY (owner_id) REFERENCES users(id),

    CONSTRAINT fk_courts_sport
        FOREIGN KEY (sport_id) REFERENCES sports(id),

    CONSTRAINT chk_courts_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_courts_owner ON courts(owner_id);
CREATE INDEX idx_courts_sport ON courts(sport_id);
CREATE INDEX idx_courts_city_status ON courts(city, status);

INSERT INTO sports (id, code, name, description)
VALUES
    ('d1000000-0000-0000-0000-000000000001',
     'FOOTBALL', 'Bóng đá', 'Sân dành cho môn bóng đá'),

    ('d1000000-0000-0000-0000-000000000002',
     'BADMINTON', 'Cầu lông', 'Sân dành cho môn cầu lông');