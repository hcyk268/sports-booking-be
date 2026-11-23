# Payment API

Base paths: `/api/v1/bookings/{bookingId}/payments` and `/api/v1/payments`.
Shared rules: [API conventions](../api-conventions.md). Persistence: [payment database contract](../../payment-schema.md).

## Implementation status

Checkout creation/resume, scoped reads, authenticated IPN reception, settlement,
pending-payment cancellation, admin reconciliation and all seven manual refund handlers are implemented.
Refund endpoints record an administrator's manual transfer; they do not initiate a bank transfer.

## Routes

| Method | Route | Role | Declared response data | Status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/bookings/{bookingId}/payments` | CUSTOMER | `PaymentCheckoutResponse` (201) | Implemented |
| GET | `/api/v1/bookings/{bookingId}/payments` | CUSTOMER / PROVIDER / ADMIN | List of `PaymentResponse` | Implemented |
| GET | `/api/v1/payments` | CUSTOMER / PROVIDER / ADMIN | Page of `PaymentResponse` | Implemented |
| GET | `/api/v1/payments/{id}` | CUSTOMER / PROVIDER / ADMIN | `PaymentResponse` | Implemented |
| POST | `/api/v1/payments/{id}/checkout` | CUSTOMER | `PaymentCheckoutResponse` | Implemented |
| POST | `/api/v1/payments/{id}/reconciliation` | ADMIN | `PaymentReconciliationResponse` (202) | Implemented |
| POST | `/api/v1/payments/sepay/ipn` | SePay secret authentication | Bare `SePayIpnResponse` | Implemented |
| POST | `/api/v1/payments/refunds` | ADMIN | `PaymentRefundResponse` (201) | Implemented |
| GET | `/api/v1/payments/refunds` | ADMIN | Page of `PaymentRefundResponse` | Implemented |
| GET | `/api/v1/payments/refunds/{id}` | ADMIN | `PaymentRefundResponse` | Implemented |
| POST | `/api/v1/payments/refunds/{id}/processing` | ADMIN | `PaymentRefundResponse` | Implemented |
| POST | `/api/v1/payments/refunds/{id}/completion` | ADMIN | `PaymentRefundResponse` | Implemented |
| POST | `/api/v1/payments/refunds/{id}/rejection` | ADMIN | `PaymentRefundResponse` | Implemented |
| POST | `/api/v1/payments/refunds/{id}/cancellation` | ADMIN | `PaymentRefundResponse` | Implemented |

## Create payment behavior

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

## Requests

- Create payment: JSON `{}` or an optional `paymentMethod` of `CARD`, `BANK_TRANSFER`
  or `NAPAS_BANK_TRANSFER`. Required nonblank `Idempotency-Key`, at most 100 characters.
  Amount, currency, merchant, callback URLs and hold deadline come from the server.

Payment lists accept `bookingId`, `customerId`, `status`, `from`, `to`, `page`, and
`size`. Timestamps are ISO-8601 instants; `from` is inclusive and `to` exclusive,
filtering payment creation time. Date ordering is validated. Pagination defaults to
page 0, size 20; allowed size is 1–100. Results sort by creation time then ID descending.

## Payment reads and checkout resume

Customers see their own payments. Providers must own every court in the booking;
admins can read all. Customer/provider roles combine with OR, and `customerId` is
an additional filter that cannot widen access. Missing/inaccessible payment details
return `404 PAYMENT_NOT_FOUND`. Booking payment reads return a list of at most one
payment, or `[]` if none exists.

Responses include attempts, transactions, refund summaries, and `canResumeCheckout`.
General reads omit signed checkout fields and internal refund notes.

Only the booking's customer can resume checkout. The endpoint needs no body or
idempotency key and returns the stored form of an eligible `PREPARED`/`PENDING`
attempt. It does not create an invoice, call SePay, or extend the hold. Ineligible
state/deadlines return `409 PAYMENT_CHECKOUT_NOT_ALLOWED`; `UNKNOWN` needs reconciliation.
The browser must submit the returned form; eligibility can change after the response.

`PaymentCheckoutResponse.checkout.fields` is an ordered array of `{name, value}`
for the browser to submit unchanged.

## SePay Gateway IPN

The endpoint authenticates `X-Secret-Key` against `SEPAY_SECRET_KEY`, then validates
the JSON payload. Configure merchant authentication as `SECRET_KEY`. Missing/invalid
secrets return `401 SEPAY_IPN_UNAUTHORIZED`; invalid JSON/DTO returns
`400 SEPAY_IPN_INVALID`. No user JWT is required. Disabling new checkout creation
does not disable IPN reception for existing payments.

The full JSON content is retained in the inbox. HTTP 200 with bare JSON
`{"success": true}` acknowledges a committed inbox record, not a confirmed booking.
Database/commit failures do not return a successful acknowledgement.

`transaction.id` identifies the gateway transaction; `transaction.transaction_id`
is a separate reference. Paid and void notifications are distinct events. Financial
content is compared after normalization, so JSON formatting and delivery metadata do
not create conflicts.

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
max attempts 8. Each job is processed in its own transaction.

Leases fence stale workers; processing locks booking before payment and commits
receipt, settlement, booking/slots/history, outbox and inbox completion atomically.
Failure rolls back business changes; duplicates cannot allocate or publish success twice.

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
abandoned leases enter review as well.

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
Reconciliation queries SePay and persists financial observations for the same inbox worker.
Gateway order cancellation is not performed by reconciliation.

## Payment state after booking cancellation

After `booking.cancelled`, a consumer cancels an existing `PENDING` payment and
records the event actor/reason. No payment or an already terminal payment is a
successful no-op. Duplicate delivery does not create additional history.

This update is asynchronous. Checkout creation/resume still reject a cancelled
booking before the event is consumed. Attempts retain their recorded gateway state;
local payment cancellation does not prove the SePay order was cancelled. A later
authenticated paid IPN is retained for review without confirming the cancelled booking.
Gateway cancellation and refunds are not performed by this consumer.

Queue names, retries and recovery are documented in
[RabbitMQ operations](../../guides/rabbitmq-operations.md#booking-payment-cancellation-consumer).

## Errors

Missing the required `Idempotency-Key` header on payment creation returns
`400 MISSING_HEADER`, with the missing header name in the message.

See the [error catalog](../error-codes.md) for payment state, deadline, idempotency,
gateway and validation errors. Placeholder handlers do not implement their declared
business errors.

## Admin reconciliation

POST `/api/v1/payments/{id}/reconciliation` queries the newest attempt using its stored
environment, merchant and invoice. The configured credentials must match that attempt.
The network call has a 10-second timeout and runs without database row locks.
A per-payment lease rejects concurrent calls with `PAYMENT_RECONCILIATION_IN_PROGRESS`.

202 means the lookup was recorded and any payment transactions were durably queued.
It does **not** mean the payment succeeded. Read the payment again after the inbox worker runs.
Pending, missing and cancelled gateway orders cannot settle a booking. Missing orders and
failed lookups preserve the existing checkout state. Ambiguous verified gateway states
pause active checkout as UNKNOWN.
Captured orders without approved transactions require review. A failed gateway call returns
502/503, records the failure and never fabricates success. An UNKNOWN attempt is not reopened
by a pending result alone. A cancelled booking with collected funds stays cancelled and the
receipt requires review/refund.

Only the newest attempt is queried by this endpoint. Older attempts still accept authenticated
IPNs; investigating a missing IPN for an older invoice requires operational follow-up.
`nextReconcileAt` records the suggested follow-up time; automatic scheduled gateway polling
is not included. The existing inbox worker retries processing of already-received observations.

Source: [SePay order detail API](https://developer.sepay.vn/vi/cong-thanh-toan/API/don-hang/chi-tiet-don-hang).
The lookup uses `/v1/order/detail/{order_invoice_number}`, not the gateway order UUID.

## Manual refunds

All seven refund routes require ADMIN. A refund must reference a matched, approved PAYMENT
receipt in VND. It may be a late/extra collection requiring review; a cancelled booking does
not make the collected money unrefundable.

- Creation requires `Idempotency-Key`; same key/body returns the existing refund, changed body returns 409.
- REQUESTED, PROCESSING and COMPLETED amounts reserve the receipt's refund budget.
  Payment/receipt locks serialize reservations, including concurrent administrators.
- REQUESTED can enter PROCESSING, REJECTED or CANCELLED. PROCESSING can enter COMPLETED.
  Reject/cancel releases the reservation; completed transfers cannot be cancelled through these routes.
- Mutations require the current `version`; stale versions return 409.
- Completion also requires `Idempotency-Key` and `refundReference`. Retry with the original
  body/key after a lost response returns the recorded completion without a second transfer record.
- Optional evidence uses shared uploads with purpose `REFUND_EVIDENCE`, resourceId=refund ID,
  PNG/JPEG up to 5 MiB. Completion accepts only the acting admin's READY, unexpired upload for
  that exact refund. Claim and completion commit together. Evidence upload IDs are stored in
  admin refund details; these routes do not expose a public evidence URL.
- List filters: paymentId, paymentTransactionId, status, from inclusive, to exclusive.
  Page starts at 0; size is 1–100; order is createdAt then ID descending.
- History retains the actor, transitions and rejection/cancellation reasons. Ordinary customer/
  provider payment reads keep the existing refund summary and do not expose admin notes.
