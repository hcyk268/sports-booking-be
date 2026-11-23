# Payment endpoints — verification, 24 November 2026

Branch: `feat/payment-endpoints`. Base: `47bfcba` (`origin/main`, rechecked 24 November).
Implementation and local verification complete. Real SePay sandbox acceptance is pending.
This report does not claim a real gateway payment or bank refund.

## Scope

Implemented the seven admin manual-refund endpoints and payment reconciliation that previously returned null.
Added refund completion idempotency (migration V27), locked refund reservations, version checks, audited
transitions and private evidence claiming. Reconciliation queries SePay by invoice and queues financial
observations through the existing inbox settlement flow. It never treats a browser return URL or HTTP 202 as
proof of payment.

Refund completion records a transfer performed separately by an administrator. No API in this change
initiates a transfer of money from a bank account.

## Automated checks

- Full `mvnw -Pintegration verify`: BUILD SUCCESS, 436 tests discovered, 431 passed, 5 skipped.
  Coverage gate passed. This run reported a delayed fork JVM shutdown warning after successful tests.
- Subsequent targeted run with a dedicated PostgreSQL database:
  `PaymentOperationsIntegrationTest,PaymentMappingIntegrationTest,BookingPaymentCreationIntegrationTest`:
  18 passed, zero failed, zero skipped. This covers all five previously skipped tests and reruns
  the 13 new payment-operation integration tests after the final failure-state adjustment.
- Final `mvnw -DskipTests package`: BUILD SUCCESS.
- `git diff --check`: clean.

New operation tests exercise all eight endpoints, ADMIN restrictions, list validation,
creation/completion retries, changed-body conflicts, concurrent budget reservations,
concurrent duplicate creation, concurrent completion, stale versions, invalid evidence and atomic
evidence attachment, cancelled bookings with collected funds, duplicate query/IPN settlement,
missing orders, captured orders without transactions, malformed financial identities and gateway errors.

SePay HTTP client tests use a local HTTP server to verify the invoice path, Basic authentication,
merchant/environment matching, redirect rejection, 404 handling, gateway errors, malformed data and
removal of cardholder details. These are gateway contract tests, not calls to a real merchant account.

## Running-backend HTTP verification

A packaged application ran on loopback port 18093 using dedicated PostgreSQL, Redis and RabbitMQ
containers. Requests used real registration/login and issued JWTs. Email/outbox delivery was disabled.
Booking fixtures were inserted only in the disposable database.

All 24 HTTP checks passed, including:

- Customer/admin login and signed checkout creation.
- Authenticated simulated paid IPN and exact replay.
- Inbox processing changed the payment to SUCCEEDED and the booking to CONFIRMED.
- Customer access to admin refunds returned 403.
- Create/list/read/process/complete/reject/cancel refund.
- Replayed creation/completion did not create an additional financial record.
- Stale mutation returned 409; excess refund returned REFUND_AMOUNT_EXCEEDED.
- Reconciliation with an intentionally unmatched test merchant returned 503
  SEPAY_GATEWAY_NOT_CONFIGURED and preserved the settled payment.

The notification was generated locally with a test-only secret. No checkout was submitted to a real
SePay merchant, and no money was transferred.

Local execution artifacts are stored outside the repository under
`outputs/payment-endpoints-20261124/` in the Codex project workspace:
`manual-http.ps1`, `start-backend.ps1`, `manual-http-results.json` and `backend.log`.

## Outstanding live sandbox acceptance

Repository instructions and scripts were checked, including `.env.example`,
`scripts/sepay-sandbox-checkout.sh`, its helpers, the UAT guide and configuration history.
No usable merchant secret was found in the inspected checkout, local backend environment files or
process environment. The tracked example has empty merchant/secret fields. The sandbox script consumes
backend configuration; it does not provision sandbox credentials.

Before push/release acceptance:

1. Have the team's SePay account owner configure an ignored local environment file with
   SEPAY_ENABLED=true, SEPAY_ENVIRONMENT=SANDBOX, SEPAY_MERCHANT_ID, SEPAY_SECRET_KEY and valid return URLs.
   Do not paste secrets into chat, commits or the test report.
2. Start the backend with that configuration and use the repository sandbox checkout script.
3. Complete a sandbox payment; check authenticated IPN delivery to the backend through the team's
   callback/tunnel setup. Confirm one settlement and one booking confirmation.
4. Query the same paid invoice through the reconciliation endpoint. Verify that it creates no duplicate
   collection/settlement. Exercise a pending/cancelled sandbox order as well.
5. For a missing-IPN scenario, reconcile a paid order before the booking's hold/grace expires and verify
   settlement. Repeat after cancellation/expiry and confirm review rather than resurrected booking slots.

Manual-refund operations can be demonstrated with sandbox receipts, but still only record a manual
transfer. Automatic bank payouts and automatic scheduled gateway polling are outside this change.
