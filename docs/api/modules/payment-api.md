# Payment API

`POST /api/v1/bookings/{bookingId}/payments` is implemented. It validates ownership,
booking state/deadline, creates the payment and signed SePay form, and transitions the
booking/slots to `PENDING_PAYMENT` atomically. Same-key replays return the stored form
while the booking/payment/attempt remain eligible. It does not collect money itself.

`GET /api/v1/bookings/{bookingId}/payments` is also implemented. It authorizes booking
access before reading payment data, returns `[]` when none exists, and returns attempts,
transactions and refund summaries otherwise. `canResumeCheckout` checks both booking
and payment deadlines/state plus an eligible attempt using the current server time.
It is a read-only local query: no SePay calls, row locks, signing or state changes.

`GET /api/v1/payments` and `GET /api/v1/payments/{id}` are implemented as well.
Lists filter customer/provider/admin visibility before pagination and use a read-only
SQL projection across payments, bookings, slots and courts. Provider access requires
ownership of every court in the booking; customer and provider roles combine with OR.
Client-supplied `customerId` is an additional filter and cannot widen that scope.
Rows are ordered by payment creation time descending, then ID descending. The time
filter applies to payment creation with inclusive `from` and exclusive `to`.
Invalid pagination/date ordering is rejected. Missing or inaccessible payment details
return the same `404 PAYMENT_NOT_FOUND`. Detail responses reuse the booking-payment
read model and never expose signed checkout fields or internal refund notes.
List hydration batches booking access, attempts, transactions and refunds for the
whole page. It does not issue per-payment queries or paginate over collection fetch
joins. Empty pages skip these batch reads.

`POST /api/v1/payments/{id}/checkout` is implemented. Only the booking's customer can
resume; missing or inaccessible payments return `404 PAYMENT_NOT_FOUND`. The read-only
service checks booking/payment state, deadlines and snapshot consistency, then returns
the stored ordered signed form of the active `PREPARED`/`PENDING` attempt. `UNKNOWN`
requires reconciliation. It does not create attempts, sign again, call SePay, change
state or extend the hold. No request body or `Idempotency-Key` is required. If no
eligible active attempt exists, it returns `409 PAYMENT_CHECKOUT_NOT_ALLOWED`.
The browser submits the returned form again; this response is a local eligibility
check, and the gateway outcome may change after the response.

The IPN reception handler is also implemented; see the inbox contract below.
The other 8 handlers still return `null`. Their signatures, role annotations, DTOs,
validation and error-code catalogue are scaffolding only. An empty stub response is
not a completed operation. Inbox processing and atomic booking confirmation are implemented;
manual reconciliation/refund APIs and a SePay query/cancel client are still pending.

Customer/admin APIs declare `ApiResponse<T>`; paginated lists use `PageResponse<T>`.
When implemented, booking/payment reads must enforce resource ownership and provider
scope in addition to the role annotations. The booking payments endpoint returns a
list, currently containing at most one payment because of the unique booking FK.

## Routes

| Method | Route | Role | Declared response data |
| --- | --- | --- | --- |
| POST | `/api/v1/bookings/{bookingId}/payments` | CUSTOMER | `PaymentCheckoutResponse` (201) |
| GET | `/api/v1/bookings/{bookingId}/payments` | CUSTOMER / PROVIDER / ADMIN | List of `PaymentResponse` |
| GET | `/api/v1/payments` | CUSTOMER / PROVIDER / ADMIN | Page of `PaymentResponse` |
| GET | `/api/v1/payments/{id}` | CUSTOMER / PROVIDER / ADMIN | `PaymentResponse` |
| POST | `/api/v1/payments/{id}/checkout` | CUSTOMER | `PaymentCheckoutResponse` |
| POST | `/api/v1/payments/{id}/reconciliation` | ADMIN | `PaymentReconciliationResponse` (202) |
| POST | `/api/v1/payments/sepay/ipn` | SePay secret authentication | Bare `SePayIpnResponse` |
| POST | `/api/v1/payments/refunds` | ADMIN | `PaymentRefundResponse` (201) |
| GET | `/api/v1/payments/refunds` | ADMIN | Page of `PaymentRefundResponse` |
| GET | `/api/v1/payments/refunds/{id}` | ADMIN | `PaymentRefundResponse` |
| POST | `/api/v1/payments/refunds/{id}/processing` | ADMIN | `PaymentRefundResponse` |
| POST | `/api/v1/payments/refunds/{id}/completion` | ADMIN | `PaymentRefundResponse` |
| POST | `/api/v1/payments/refunds/{id}/rejection` | ADMIN | `PaymentRefundResponse` |
| POST | `/api/v1/payments/refunds/{id}/cancellation` | ADMIN | `PaymentRefundResponse` |

## Create payment behavior

The service acquires a customer/key Redis lease using a SHA-256 digest of the
`Idempotency-Key` and retains the raw key for durable database replay checks. It then
calls a separate `REQUIRES_NEW` transaction. Redis is released after that transaction
commits or rolls back. Booking is locked first through `booking :: api`, then payment.
This also serializes requests with different keys for the same booking.

- Same key and changed payment method: `409 PAYMENT_IDEMPOTENCY_KEY_REUSED`.
- Same key and eligible attempt: return its original ordered signed payload, no new
  invoice. The current payment/booking deadline is still enforced on every replay.
- Different key while an attempt is active: `409 PAYMENT_ATTEMPT_IN_PROGRESS`; an
  unknown outcome returns `409 PAYMENT_OUTCOME_UNKNOWN`. A new key is not silently
  accepted as an alias for an existing attempt. Keep the initial key for retries.
- An old attempt that was authoritatively failed/cancelled and has gateway order
  metadata can be followed by a new attempt with a new key/invoice. Local expiry or
  unknown outcomes are not sufficient evidence for creating another invoice.
- Snapshot price/currency/deadline changes return `409 PAYMENT_BOOKING_CHANGED`.
- Disabled SePay returns `503 SEPAY_GATEWAY_NOT_CONFIGURED`; the transaction rolls
  back. When enabled, credentials and return URLs are assumed complete and valid.
  Unsupported currency/fractional dong is rejected.
- Only the server determines amount, expiry, merchant, invoice and callback URLs.
  Creating checkout does not extend the hold or confirm the booking. There are no
  outbound SePay calls in this operation; the browser must submit the signed form.

Enable checkout signing with `SEPAY_ENABLED=true`, `SEPAY_ENVIRONMENT=SANDBOX` (default)
or `PRODUCTION`, `SEPAY_MERCHANT_ID`, `SEPAY_SECRET_KEY`, `SEPAY_SUCCESS_URL`,
`SEPAY_ERROR_URL`, `SEPAY_CANCEL_URL`. Defaults do not require credentials at startup;
new checkout creation is blocked while disabled. Fill all fields before enabling.
Return URLs should point to
your result UI and must be publicly reachable for real sandbox/production redirects.
The form action is chosen from the persisted attempt environment during replay.
The server adds/replaces a `paymentId` query parameter on each configured return URL,
so the result UI can identify the payment without relying on a browser's last-opened
booking. This parameter identifies the resource; it does not prove payment success.

Signing follows the [official checkout contract](https://developer.sepay.vn/vi/cong-thanh-toan/API/don-hang/form-thanh-toan):
documented field order, raw values joined with commas, UTF-8 HMAC-SHA256 and Base64.
No undocumented expiry/cancellation parameter is sent to SePay.

## Requests

- Create payment: JSON `{}` or an optional `paymentMethod` of `CARD`, `BANK_TRANSFER`
  or `NAPAS_BANK_TRANSFER`. Required nonblank `Idempotency-Key`, at most 100 characters.
  Amount, currency, merchant, callback URLs and hold deadline come from the server.
- Resume checkout: no request body. Reuse the existing eligible attempt and signed
  payload; do not extend the hold or silently create another invoice.
- Reconciliation: no request body. The response represents a requested asynchronous
  check, not confirmation that payment succeeded.
- Create refund: `paymentId`, `paymentTransactionId`, positive whole-dong `amount`,
  and nonblank `reason` up to 1000 characters; required `Idempotency-Key`.
- Start refund processing: non-negative `version`.
- Complete refund: non-negative `version`, nonblank `refundReference` up to 255
  characters, optional `evidenceAssetId` and `adminNote` up to 2000 characters;
  required `Idempotency-Key`. This records a manual transfer, not an automated payout.
- Reject/cancel refund: non-negative `version` and nonblank `reason` up to 1000
  characters. Future handlers must validate the current refund state.

Payment lists accept `bookingId`, `customerId`, `status`, `from`, `to`, `page`, `size`.
Refund lists accept `paymentId`, `paymentTransactionId`, `status`, `from`, `to`,
`page`, `size`. Timestamps are ISO-8601 instants. Pagination defaults to page 0,
size 20; allowed size 1-100. Date ordering is an application check still pending.

`PaymentCheckoutResponse.checkout.fields` is an ordered array of `{name, value}`.
Signed fields are not included in general payment/attempt responses. Payment readers
receive refund summaries; detailed refund responses with admin notes/evidence are
available through the admin refund endpoints.

## SePay Gateway IPN

The endpoint accepts a JSON string so the full payload, including fields unknown to
the DTO, reaches the inbox. It authenticates `X-Secret-Key` against `SEPAY_SECRET_KEY`
using a constant-time comparison, then parses and validates `SePayIpnRequest`. Missing
or invalid secrets return `401 SEPAY_IPN_UNAUTHORIZED`; invalid JSON/DTO returns
`400 SEPAY_IPN_INVALID`. Configure SePay merchant authentication as `SECRET_KEY`.
Merchant and environment come from server configuration. No user JWT is required.
Disabling checkout creation does not disable reception of already pending payments.

Reception runs in its own transaction. PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`
and the inbox unique constraint serialize concurrent duplicates without aborting the
transaction. ACK is bare JSON `{"success": true}` with HTTP 200, only after commit.
Database/commit failures produce an error response, never a successful ACK.

The adapter hashes notification type, `transaction.id`, order status and transaction
status for `deduplication_key`. `transaction.id` is the chosen gateway transaction
identity; `transaction.transaction_id` remains a separate reference. `payload_hash`
is a hash of normalized financial identifiers/content, not raw request bytes. Delivery
timestamp, JSON formatting, amount scale and optional metadata do not create conflicts.
`transaction.transaction_date` is part of the financial content; a conflicting value
is retained for review rather than silently replacing the first receipt.
Paid and void notifications remain separate events.

Identical retries ACK without another row. Conflicting financial content is retained
as an additional `REVIEW_REQUIRED` row keyed by the original key plus content hash;
the first row/payload is preserved and retries of that conflict are deduplicated.
Unknown notification types are also retained as `REVIEW_REQUIRED`. Other notifications
start as `PENDING`; reception does not update payments, attempts or bookings.

`timestamp` is delivery time, not payment time. The adapter parses
`transaction.transaction_date` (`yyyy-MM-dd HH:mm:ss`) using
`SEPAY_TRANSACTION_ZONE_ID` (default `Asia/Ho_Chi_Minh`) and persists
`provider_occurred_at`; confirm the gateway's timezone before production use.
Malformed dates are retained for review. If the date is missing, an authenticated
IPN received before the deadline can use `received_at` as a conservative upper
bound for payment time; reception after the deadline requires review. SePay may send
`customer: null` for bank transfers;
invoice, amount and transaction checks remain mandatory. JSONB preserves JSON
content, not exact original whitespace/key order. Authorization headers are not
stored or logged.

## Inbox worker

The scheduled worker is enabled by default (`PAYMENT_INBOX_ENABLED=true`), independently
of checkout creation, and polls every second (`PAYMENT_INBOX_POLL_INTERVAL`).
`app.payment.inbox` defaults: batch size 25, lease 60 seconds, retry delay 15 seconds,
max attempts 8. A batch claims one job at a time and processes each job in its own
transaction; it does not load a page of entities and lazily hydrate their relations.

Claiming uses PostgreSQL `FOR UPDATE SKIP LOCKED` and a new lease token. Processing
locks the inbox row and checks the token/deadline; a stale worker cannot complete it.
The gateway transaction identity is serialized with a transaction-scoped advisory
lock, including when no receipt exists yet. Booking and payment locks are taken in
that order, consistent with checkout creation. Invoice routing uses scalar projections
before loading the locked entities, so a waiter does not reuse stale cached state.

For `ORDER_PAID`, only `CAPTURED` + `PAYMENT` + `APPROVED` can settle. The worker
checks order/transaction identity, amount, currency, payment method when present,
customer reference when present, booking snapshot and attempt/payment deadlines.
The original deadline is checked against the gateway transaction time. An on-time
payment can settle during `PAYMENT_SETTLEMENT_GRACE` (default two minutes) only while
the booking and slots remain reserved. The booking expiry worker defers releasing a
`PENDING_PAYMENT` booking for that bounded period; new checkouts still stop at the
original deadline. A payment after the original deadline is `LATE_PAYMENT` even
during grace. An on-time payment discovered after grace, cancellation or release is
`ON_TIME_PAYMENT_UNFULFILLABLE` and requires review/refund, not slot resurrection.
If the gateway omitted transaction time and the IPN arrived after the original
deadline, the result is `PAYMENT_TIME_UNKNOWN` and requires review.
Matching the receipt is flushed before setting the payment's composite settlement FK.
Receipt, attempt outcome, booking/slots/history, settlement, outbox and inbox completion
commit together. Failure rolls all business changes back. Duplicates never allocate
the same receipt or publish success twice.

Late/cancelled bookings, expired payments, extra collections, mismatches, unknown
invoices and conflicting financial facts retain the receipt and enter review. Valid
late collections may mark the attempt succeeded without settling the booking's payment.
`TRANSACTION_VOID` always requires reconciliation; it never cancels a booking or
silently reverses a settled payment. A subsequent paid notification cannot overwrite
the recorded void. Manual resolutions are not automatically applied again.
Active attempts with a void or matched financial/customer mismatch are paused as
`UNKNOWN` so the customer cannot keep resubmitting a disputed checkout. Previously
collected attempts retain `SUCCEEDED`; a void does not erase that financial history.
Inbox rows already marked `REVIEW_REQUIRED` at reception remain for manual handling;
the automatic worker claims only due `PENDING` or abandoned `PROCESSING` rows.

Transient failures retry after rollback, with exponential delay capped at 64 times
the base delay. Exhausted attempts become `FAILED` and emit a review event; exhausted
abandoned leases enter review as well. Retry metadata stores exception class names,
not messages that might contain request/SQL data.

Transactional outbox events:

- `payment.succeeded`: aggregate `PAYMENT`; payload includes payment/booking/attempt/
  receipt IDs, amount, currency and settlement time.
- `payment.review_required`: aggregate `PAYMENT_INBOX`; payload includes inbox ID,
  optional payment/receipt IDs, invoice, reason code and occurrence time.
- `booking.confirmed`: emitted by the booking module in the same transaction.

Payment events publish to durable topic exchange `sports.payment`, using the event
type as routing key and the persisted event ID in the envelope. Consumers bind their
queues to this exchange and must deduplicate event IDs. Outbox delivery is at least
once and requires publisher confirms and successful routing. Missing routes/nacks/
timeouts keep delivery in the outbox retry/failure flow, not marked published.
SePay query/cancel reconciliation and manual refund handling remain separate work.

## Errors

Missing the required `Idempotency-Key` header on payment creation returns
`400 MISSING_HEADER`, with the missing header name in the message.

`PaymentErrorCode` defines payment/attempt state conflicts, deadline, idempotency,
transaction matching, gateway/IPN, reconciliation and manual refund errors.
It is not thrown by the placeholder handlers. Reuse the shared
`OPTIMISTIC_LOCK_CONFLICT` for stale versions and shared validation error handling
for malformed DTOs/parameters instead of introducing another version error.
