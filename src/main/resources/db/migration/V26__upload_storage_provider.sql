-- Existing uploads belong to R2; keep routing them there after S3 is enabled.
ALTER TABLE shared_upload_assets
    ADD COLUMN storage_provider VARCHAR(20) NOT NULL DEFAULT 'R2'
    CHECK (storage_provider IN ('R2', 'S3'));
