CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    source_event_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,

    CONSTRAINT uk_notifications_source_recipient_type
        UNIQUE (source_event_id, recipient_id, type),
    CONSTRAINT ck_notifications_type CHECK (length(btrim(type)) > 0),
    CONSTRAINT ck_notifications_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_notifications_body CHECK (length(btrim(body)) > 0),
    CONSTRAINT ck_notifications_resource CHECK (
        (resource_type IS NULL AND resource_id IS NULL)
        OR (resource_type IS NOT NULL AND length(btrim(resource_type)) > 0 AND resource_id IS NOT NULL)
    )
);

CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC, id DESC);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_id)
    WHERE read_at IS NULL;
