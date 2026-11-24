# Payment endpoints — verification, 24 November 2026

Branch: `feat/payment-endpoints`. Base: `47bfcba` (`origin/main`, rechecked 24 November).
Implementation and local verification complete. A SePay sandbox checkout, authenticated payment notification and reconciliation of the paid invoice passed. All payment activity was in the test environment.

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

The notification for these 24 checks was generated locally with a test-only secret. The separate SePay sandbox run is recorded below.

Local execution artifacts are stored outside the repository under
`outputs/payment-endpoints-20261124/` in the Codex project workspace:
`manual-http.ps1`, `start-backend.ps1`, `manual-http-results.json` and `backend.log`.

## SePay sandbox verification

- SePay accepted the onboarding IPN test after `ca92542` added support for the observed `user_agent: {}`. The follow-up run passed 124 Payment and SePay tests.
- The backend generated a signed BANK_TRANSFER checkout for a 50,000 VND fixture in the dedicated test database. The tester completed payment in the SePay sandbox checkout.
- At 17:16:24 Vietnam time, the backend received the `ORDER_PAID` IPN from SePay via the tunnel. The inbox recorded `PROCESSED` on the first attempt. The payment and attempt reached `SUCCEEDED`. The booking and its slot reached `CONFIRMED`.
- Admin reconciliation of the same invoice called the SePay sandbox API and returned HTTP 202. The query notification reached `PROCESSED`. The invoice had one transaction before and after reconciliation, and the booking stayed confirmed.

The sandbox run covered successful checkout and reconciliation after IPN. Missing IPN, pending or cancelled orders, and late settlement were exercised through local tests and were not repeated with this sandbox merchant.

Manual-refund operations can be demonstrated with sandbox receipts, but still only record a manual
transfer. Automatic bank payouts and automatic scheduled gateway polling are outside this change.
