CREATE TABLE court_images (
    id UUID PRIMARY KEY,
    court_id UUID NOT NULL REFERENCES courts(id),
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_court_images_size CHECK (size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT ck_court_images_type CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_court_images_order CHECK (display_order >= 0),
    CONSTRAINT uk_court_images_order UNIQUE (court_id, display_order)
);
CREATE UNIQUE INDEX uk_court_images_cover
    ON court_images (court_id) WHERE is_cover = TRUE;
