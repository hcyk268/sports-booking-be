ALTER TABLE payment_refunds ADD COLUMN completion_key VARCHAR(100);
ALTER TABLE payment_refunds ADD COLUMN completion_hash VARCHAR(64);
CREATE UNIQUE INDEX uk_refund_completion_key ON payment_refunds(payment_id,completion_key) WHERE completion_key IS NOT NULL;
ALTER TABLE payment_refunds ADD CONSTRAINT ck_refund_completion_hash CHECK (
 (completion_key IS NULL AND completion_hash IS NULL) OR
 (completion_key IS NOT NULL AND completion_hash IS NOT NULL AND length(btrim(completion_key)) > 0 AND completion_hash ~ '^[0-9a-f]{64}$'));
