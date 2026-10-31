-- Run with psql -v ON_ERROR_STOP=1 after applying all migrations to a disposable DB.
-- No fixture data survives a successful run.
BEGIN;
DO $$
DECLARE
    customer UUID := gen_random_uuid();
    booking UUID := gen_random_uuid();
    payment UUID := gen_random_uuid();
    attempt UUID := gen_random_uuid();
    receipt UUID := gen_random_uuid();
BEGIN
    INSERT INTO users (id, email, password_hash, full_name)
    VALUES (customer, customer || '@payment.test', 'test-only', 'Payment migration test');
    INSERT INTO bookings (id, booking_code, customer_id, total_amount, status,
                          hold_expires_at, idempotency_key, idempotency_request_hash)
    VALUES (booking, 'PAYMENT-SCHEMA-TEST', customer, 300000, 'HELD',
            now() + interval '10 minutes', 'booking-key', repeat('a', 64));
    INSERT INTO payments (id, booking_id, amount, expires_at)
    VALUES (payment, booking, 300000, now() + interval '10 minutes');

    BEGIN
        INSERT INTO payments (id, booking_id, amount, expires_at)
        VALUES (gen_random_uuid(), booking, 300000, now());
        RAISE EXCEPTION 'Duplicate booking payment was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    INSERT INTO payment_attempts (id, payment_id, attempt_number, environment,
        merchant_id, invoice_number, amount, expires_at, checkout_payload,
        idempotency_key, idempotency_request_hash)
    VALUES (attempt, payment, 1, 'SANDBOX', 'merchant-test', 'INVOICE-1',
        300000, now() + interval '10 minutes',
        '[{"name":"order_amount","value":"300000"}]', 'checkout-1', repeat('b', 64));
    BEGIN
        INSERT INTO payment_attempts (id, payment_id, attempt_number, environment,
            merchant_id, invoice_number, amount, expires_at, checkout_payload,
            idempotency_key, idempotency_request_hash)
        VALUES (gen_random_uuid(), payment, 2, 'SANDBOX', 'merchant-test', 'INVOICE-2',
            300000, now(), '[]', 'checkout-2', repeat('b', 64));
        RAISE EXCEPTION 'Concurrent active checkout was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE payment_attempts SET checkout_payload = '{}' WHERE id = attempt;
        RAISE EXCEPTION 'Unordered checkout fields were accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO payment_transactions (id, payment_id, payment_attempt_id,
        environment, merchant_id, provider_transaction_id, transaction_type,
        provider_status, amount, resolution_status)
    VALUES (receipt, payment, attempt, 'SANDBOX', 'merchant-test', 'TX-1',
        'PAYMENT', 'APPROVED', 300000, 'APPLIED');
    BEGIN
        INSERT INTO payment_transactions (id, environment, merchant_id,
            provider_transaction_id, transaction_type, provider_status, amount)
        VALUES (gen_random_uuid(), 'SANDBOX', 'merchant-test', 'TX-1',
            'PAYMENT', 'APPROVED', 300000);
        RAISE EXCEPTION 'Duplicate provider receipt was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO payment_transactions (id, payment_id, payment_attempt_id,
            environment, merchant_id, provider_transaction_id, transaction_type,
            provider_status, amount, resolution_status)
        VALUES (gen_random_uuid(), payment, attempt, 'SANDBOX', 'merchant-test',
            'TX-2', 'PAYMENT', 'APPROVED', 300000, 'APPLIED');
        RAISE EXCEPTION 'Two applied receipts were accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO payment_transactions (id, payment_id, payment_attempt_id,
            environment, merchant_id, provider_transaction_id, transaction_type,
            provider_status, amount)
        VALUES (gen_random_uuid(), gen_random_uuid(), attempt, 'SANDBOX',
            'merchant-test', 'TX-WRONG-PAYMENT', 'PAYMENT', 'APPROVED', 300000);
        RAISE EXCEPTION 'Mismatched attempt/payment was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;

    UPDATE payments SET status = 'SUCCEEDED', settled_transaction_id = receipt,
        settled_at = now() WHERE id = payment;
    -- Extra and unmatched receipts must remain recordable after settlement.
    INSERT INTO payment_transactions (id, payment_id, payment_attempt_id,
        environment, merchant_id, provider_transaction_id, transaction_type,
        provider_status, amount, resolution_status)
    VALUES (gen_random_uuid(), payment, attempt, 'SANDBOX', 'merchant-test',
        'TX-EXTRA', 'PAYMENT', 'APPROVED', 300000, 'REVIEW_REQUIRED');
    INSERT INTO payment_transactions (id, environment, merchant_id,
        provider_transaction_id, transaction_type, provider_status, amount,
        resolution_status)
    VALUES (gen_random_uuid(), 'SANDBOX', 'merchant-test', 'TX-UNMATCHED',
        'PAYMENT', 'APPROVED', 300000, 'REVIEW_REQUIRED');

    INSERT INTO payment_webhook_inbox (id, environment, merchant_id,
        notification_type, deduplication_key, payload_hash, payload)
    VALUES (gen_random_uuid(), 'SANDBOX', 'merchant-test', 'ORDER_PAID',
        'ORDER_PAID:TX-1', repeat('c', 64), '{}'),
        (gen_random_uuid(), 'SANDBOX', 'merchant-test', 'TRANSACTION_VOID',
        'TRANSACTION_VOID:TX-1', repeat('d', 64), '{}');
    BEGIN
        INSERT INTO payment_webhook_inbox (id, environment, merchant_id,
            notification_type, deduplication_key, payload_hash, payload)
        VALUES (gen_random_uuid(), 'SANDBOX', 'merchant-test', 'ORDER_PAID',
            'ORDER_PAID:TX-1', repeat('c', 64), '{}');
        RAISE EXCEPTION 'Duplicate IPN was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE payment_webhook_inbox SET status = 'PROCESSING';
        RAISE EXCEPTION 'Processing without a recoverable lease was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO payment_refunds (id, payment_id, payment_transaction_id, amount,
        reason, idempotency_key, idempotency_request_hash)
    VALUES (gen_random_uuid(), payment, receipt, 300000, 'Customer cancellation',
        'refund-1', repeat('e', 64));
    BEGIN
        UPDATE payment_refunds SET status = 'COMPLETED', completed_at = now();
        RAISE EXCEPTION 'Refund completion without admin/reference was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    UPDATE payment_refunds SET status = 'COMPLETED', completed_at = now(),
        processed_by = customer, refund_reference = 'MANUAL-BANK-REF';
END $$;
ROLLBACK;
