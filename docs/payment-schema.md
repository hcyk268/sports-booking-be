# Payment database contract

Flyway migrations V20–V23 define payment persistence. HTTP behavior and implementation
status are documented in the [Payment API](api/modules/payment-api.md).

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

These cross-row rules are deliberately enforced by application transactions as those flows are implemented,
not by SQL CHECK constraints or assumptions about SePay cancellation:

1. Authorize via the booking customer/provider; validate admin role for refund actions.
2. Lock booking before payment, then transaction/refund rows in consistent order.
3. Copy price/currency/deadline from the booking; validate an attempt against payment.
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

Manual refund/reconciliation application flows are pending; their invariants above
remain implementation requirements.

No SePay cancel API, checkout TTL, or automatic refund capability is assumed here.
Existing `shared.outbox_events` is reused; no second outbox is created.

## Schema smoke check

On a disposable PostgreSQL database, apply the repository Flyway migrations (payment tables are introduced in V20–V23),
then run [payment-schema-smoke.sql](../src/test/resources/db/payment-schema-smoke.sql),
using a PostgreSQL connection URI:

```sh
psql "postgresql://<user>:<password>@localhost:5432/<disposable-db>" -v ON_ERROR_STOP=1 -f src/test/resources/db/payment-schema-smoke.sql
```

Replace the URI placeholders with disposable database connection values; `psql` uses
a PostgreSQL URI, whereas `PAYMENT_TEST_JDBC_URL` uses a JDBC URL. Fixtures roll back. This checks uniqueness, matching foreign
keys, single allocation, inbox dedup/leases, and manual refund completion requirements.

## Database integration checks

For a real JPA/Flyway round-trip check, set `PAYMENT_TEST_JDBC_URL` to a **disposable**
PostgreSQL database, optionally set `PAYMENT_TEST_DB_USER` and `PAYMENT_TEST_DB_PASSWORD`,
and run `.\mvnw.cmd -P integration -Dtest=PaymentMappingIntegrationTest test` in PowerShell.
This opt-in test migrates the database and writes fixtures; do not point it at application
data. Without the URL it is skipped. Pure payment/inbox unit tests need no database.

`BookingPaymentCreationIntegrationTest` uses the same disposable database and checks
atomic booking/payment creation, replay, rollback, and concurrent idempotency keys.
Run both database tests with:

```powershell
.\mvnw.cmd -P integration "-Dtest=PaymentMappingIntegrationTest,BookingPaymentCreationIntegrationTest" test
```

Use `sh mvnw` instead of `.\mvnw.cmd` in POSIX shells.

`PaymentInboxProcessorTest`, `PaymentInboxWorkerTest`, and `PaymentInboxFailuresTest`
cover inbox settlement/retry behavior with unit-test doubles. These unit tests do not establish PostgreSQL locking or concurrent-worker behavior.
Use the existing mapping and booking/payment creation tests for the available
PostgreSQL coverage; dedicated inbox-worker database coverage remains a gap.
