-- Additive migration: V15 local images keep upload_id NULL and remain readable.
CREATE TABLE shared_upload_assets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    file_name VARCHAR(120) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    staging_key VARCHAR(255) NOT NULL UNIQUE,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','READY','ATTACHED','DELETE_PENDING','DELETED')),
    expires_at TIMESTAMPTZ NOT NULL,
    etag VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_upload_cleanup ON shared_upload_assets (status, expires_at);
CREATE INDEX ix_upload_owner ON shared_upload_assets (owner_id, status, expires_at);
ALTER TABLE court_images ADD COLUMN upload_id UUID REFERENCES shared_upload_assets(id);
CREATE UNIQUE INDEX uk_court_images_upload ON court_images (upload_id) WHERE upload_id IS NOT NULL;
