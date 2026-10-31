# Payment database contract

V20-V23 add the payment schema without changing existing booking migrations.

- `payments`: one full-payment obligation per booking, created when checkout starts.
- `payment_attempts`: scoped merchant/environment invoice identity, exact signed form
  fields, retry/idempotency metadata and reconciliation scheduling. Reopening a pending
  checkout reuses the attempt. `UNKNOWN` blocks another active attempt.
- `payment_transactions`: gateway financial facts, including unmatched, late and extra
  receipts. Provider states/types are strings so a new gateway value can be retained
  for review. An unmatched transaction can be linked once its attempt is identified.
- `payments.settled_transaction_id`: the single receipt allocated to the obligation.
  Refunds and reversals do not erase the original settlement history.
- `payment_webhook_inbox`: authenticated IPNs, deduplication and recoverable worker
  leases. Paid and void notifications must have different deduplication keys.
- `payment_status_history` and `payment_refund_status_history`: audit records.
- `payment_refunds`: manual admin refunds with an optional existing upload asset as
  evidence. Completed refunds require an actor, completion time and reference.

## Application invariants

These cross-row rules are deliberately enforced by the future application transaction,
not by SQL CHECK constraints or assumptions about SePay cancellation:

1. Authorize via the booking customer/provider; validate admin role for refund actions.
2. Lock booking before payment, then transaction/refund rows in consistent order.
3. Copy price/currency/deadline from the booking; validate an attempt against payment.
   Voucher changes must not mutate previously issued checkout fields.
4. Validate the transaction merchant/environment/amount/currency and successful gateway
   status before allocation. Update settlement, allocation state, booking, audit and
   shared outbox atomically. A receipt can still be retained when confirmation fails.
5. Keep settlement and receipt allocation consistent. Reversal handling must preserve
   financial history rather than deleting a settlement or replaying old paid events.
6. Lock the source transaction when reserving a refund. Match currency and ensure the
   total REQUESTED/PROCESSING/COMPLETED refund amount does not exceed refundable funds,
   accounting for gateway reversals. A timeout is not evidence that money was not sent.
7. Commit the inbox before ACK. On duplicate keys compare payloads for material
   conflicts; do not silently replace an already authenticated event. Resolve exact
   gateway transaction identity and dedup key mapping in the adapter.
8. A lease takeover changes `lock_token`; the old worker must not commit using an
   expired token. Claim, recover and complete inbox jobs transactionally.
9. Local EXPIRED/CANCELLED states do not prove the gateway stopped accepting money.
   Continue ingesting late IPNs and reconciling old attempts. Do not create a retry
   until the gateway's prior outcome and retry rules are verified.
10. Never store merchant secrets or auth headers. The ordered checkout JSON array
    stores signed field names/values and must not be logged. Minimize/redact sensitive
    IPN fields and restrict raw payload access.

No SePay cancel API, checkout TTL, or automatic refund capability is assumed here.
Existing `shared.outbox_events` is reused; no second outbox is created.

## Schema smoke check

On a disposable PostgreSQL database, apply migrations V1-V23 in numeric version order,
then run `src/test/resources/db/payment-schema-smoke.sql` with
`psql -v ON_ERROR_STOP=1`. Fixtures roll back. This checks uniqueness, matching foreign
keys, single allocation, inbox dedup/leases, and manual refund completion requirements.

## Domain model

`payment/domain/model` maps the six business tables using JPA entities with protected
constructors and explicit transition methods. Cross-module links use UUIDs instead of
importing booking/user/upload entities. History rows are appended through their parent.

- `Payment.settle` accepts an exact, matched, approved collection only while the local
  deadline is open. Replaying the allocated receipt is idempotent. The application must
  also authorize, lock, confirm the booking and enqueue events in the same transaction.
- `PaymentAttempt` snapshots the obligation, preserves ordered immutable checkout
  fields and records authoritative gateway outcomes separately from local expiry.
- `PaymentTransaction` retains unmatched or anomalous receipts. `PAYMENT`/`APPROVED`
  is the currently recognized collection pair; other provider values remain stored for
  review. Gateway status changes after allocation do not erase settlement history.
- `PaymentRefund` supports request, processing, completion, rejection and cancellation.
  Once processing starts, an unknown bank-transfer outcome must not release the budget.
  Authorization and total refund budget checks remain application responsibilities.
- `payment/infrastructure/inbox/PaymentWebhookInbox` maps the technical inbox, with
  explicit lease claim/takeover/retry/completion. It does not authenticate HTTP requests
  or independently guarantee database fencing; workers must lock or condition updates
  on the token in the same database transaction.

The create-payment API is implemented with a booking public facade, repositories,
idempotency lease, atomic booking/payment creation and locally signed SePay checkout
fields. Booking payment reads are also implemented through the booking public access
policy, with attempts/transactions/refund summaries and checkout availability.
Authenticated IPN reception, resumable checkout and the leased inbox worker are also
implemented. Settlement confirms booking/slots and writes both booking/payment outbox
events atomically; duplicate, anomalous, void and late receipts are preserved safely.
Some admin/refund APIs still return `null`; SePay query/cancel reconciliation is pending.
See `api/modules/payment-api.md` for behavior and required configuration.
Existing migrations are unchanged.

For a real JPA/Flyway round-trip check, set `PAYMENT_TEST_JDBC_URL` to a **disposable**
PostgreSQL database, optionally set `PAYMENT_TEST_DB_USER` and `PAYMENT_TEST_DB_PASSWORD`,
and run `mvnw.cmd -P integration -Dtest=PaymentMappingIntegrationTest test`.
This opt-in test migrates the database and writes fixtures; do not point it at application
data. Without the URL it is skipped. Pure payment/inbox unit tests need no database.

`BookingPaymentCreationIntegrationTest` uses the same opt-in disposable database. It
checks real Spring/JPA transactions, booking/slot updates, JSONB replay, rollback after
flushed inserts and concurrent requests with different idempotency keys. It needs no
Redis/RabbitMQ or live SePay credentials. Run both database tests with
`mvnw.cmd -P integration -Dtest=PaymentMappingIntegrationTest,BookingPaymentCreationIntegrationTest test`.

`PaymentInboxWorkerIntegrationTest` checks actual PostgreSQL/JPA settlement, composite
FK ordering, invoice routing, duplicate and extra collections, mismatches, late/void
events, lost leases, retry exhaustion, concurrent workers and rollback after booking
confirmation. `PaymentWebhookInboxRepositoryIntegrationTest` covers atomic concurrent
reception. Both use the same opt-in disposable database convention.
