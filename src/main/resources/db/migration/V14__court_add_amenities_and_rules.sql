ALTER TABLE courts ADD COLUMN rules TEXT;

ALTER TABLE courts ADD CONSTRAINT ck_courts_rules_length
    CHECK (rules IS NULL OR char_length(rules) <= 5000);

CREATE TABLE court_amenities (
    court_id UUID NOT NULL REFERENCES courts(id),
    amenity VARCHAR(100) NOT NULL,
    PRIMARY KEY (court_id, amenity),
    CONSTRAINT ck_court_amenities_name
        CHECK (char_length(trim(amenity)) > 0)
);
