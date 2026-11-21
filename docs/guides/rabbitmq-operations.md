# RabbitMQ recovery and backlog limits

The application requires RabbitMQ 3.13+ with quorum queue support. Keep the existing
booking, payment, email and email DLQ queues: no queue deletion or type conversion is
required. New email retry queues are quorum queues. For production availability,
deploy a RabbitMQ cluster with enough nodes to retain a quorum during failures.
Durable classic queues remain subject to loss if their storage is permanently lost.

## Apply limits

`docker compose up -d` starts `rabbitmq-policy` after the broker is healthy. That
one-shot service applies `docker/rabbitmq/backlog-policy.json` through the management
API. Check `docker compose logs rabbitmq-policy` and its exit status: application
startup alone does not prove the broker policy was applied. The service retries on
failure. For a custom vhost, set `RABBITMQ_VHOST_URL_ENCODED` to its URL-encoded name
(default `%2F` for `/`), in addition to `RABBITMQ_VIRTUAL_HOST`.

For an existing Docker broker, apply/update the same limits without restarting it:

```sh
sh scripts/configure-rabbitmq.sh --virtual-host '/'
```

For a managed broker, apply the JSON policy using the provider's management tools.
The policy matches the application's booking/payment event queues, email queues,
and booking-notification delivery, retry and DLQ queues. Inspect effective queue policies when combining it
with existing policies; a higher-priority policy can override this policy.

Limits are 10,000 ready messages or 256 MiB of ready message bodies per queue.
Retry queues additionally cap bodies at 64 MiB. Overflow is **reject-publish**, so
confirmed publishers see a NACK and retain their source outbox/delivery. No TTL is
applied to booking/payment events and old messages are not intentionally discarded.
These limits do not include message metadata, unacknowledged deliveries or the
PostgreSQL outbox: monitor broker disk, queue depth and database storage as well.

Booking/payment event queues retain events for independent logical consumers. The
repository does not add a consumer that merely discards this backlog. Each logical
consumer should own its durable queue/binding and deduplicate using the envelope
`eventId`. The separate internal consumers described below do not
drain these legacy event queues.

## Booking payment cancellation consumer

`sports.payment.booking-cancelled` is a separate durable quorum queue bound to
`sports.booking` for `booking.cancelled`. The listener ID is
`bookingPaymentCancellationConsumer`. It validates the event envelope and locks
booking then payment. Only pending payments are cancelled, with actor/reason history;
missing payments and payments already terminal are successful no-ops. Duplicate
deliveries do not create duplicate history. Collected payments and gateway attempt
outcomes are preserved; gateway cancellation/refund remain separate operations.

Transient failures go through `sports.payment.booking-cancelled.retry.1`, `.retry.2`
and `.retry.3` after 10 seconds, 1 minute and 5 minutes, using header
`x-payment-cancellation-retry-count`. Malformed events, unsupported versions, booking
state mismatches and exhausted retries go to `sports.payment.booking-cancelled.dlq`.
Copies are persistent; publisher confirms and successful routing are required before
the original is acknowledged. Failed forwarding requeues the original after a
one-second pause. Retry queues dead-letter at least once through exchange
`sports.payment.booking-cancellation` and routing key `booking.cancelled`.

The main queue and DLQ cap ready messages at 10,000 / 64 MiB; retry queues cap bodies
at 64 MiB. Overflow rejects publications. These limits are declared directly in queue
arguments; the existing backlog policy does not need to match these new queues.
Monitor the DLQ and investigate the booking/payment before replaying through broker
operations; there is no new HTTP replay endpoint. New bindings receive future events
only, so historical cancellations require a controlled backfill.

## Booking notification consumer

`sports.notification.booking` is a separate durable quorum queue bound to
`sports.booking` for `booking.confirmed`, `booking.cancelled` and `booking.expired`.
It does not drain `sports.booking.events`, which remains available for its existing
logical consumers. New bindings receive future publications; existing messages in
the legacy event queue are not backfilled automatically.

The listener decodes the JSON body using a local envelope contract, ignoring Java
type headers. It validates event version 1, aggregate type, payload and matching
booking IDs, then stores a Vietnamese inbox notification for payload `customerId`.
`INSERT ... ON CONFLICT DO NOTHING` makes duplicate and concurrent deliveries safe
without clearing an already stored read time. The original delivery is acknowledged
only after the database transaction commits.

Processing/database failures retry through `sports.notification.booking.retry.1`,
`.retry.2`, and `.retry.3` after 10 seconds, 1 minute and 5 minutes. Retry count is
stored in `x-booking-notification-retry-count`. Malformed messages, unsupported
contracts and exhausted retries go to `sports.notification.booking.dlq`.
Forwarding uses a persistent copy and publisher confirms before acknowledging the
source. If forwarding fails, the original is requeued after a one-second pause.
Retry queues use quorum at-least-once dead-lettering to the notification exchange;
the main queue also has an at-least-once DLQ fallback for broker dead-lettering.
Queue lengths and bodies are bounded with reject-publish overflow. Main/DLQ limits
are 10,000 messages / 256 MiB; retry queues cap bodies at 64 MiB. Queue arguments
are immutable; changing them requires a planned migration.

Monitor this DLQ separately from the email DLQ. The existing email replay endpoint
does not replay booking notifications. After fixing the cause, use broker tooling
to republish the preserved envelope to the main notification queue with its retry
header reset to zero. Do not remove the DLQ source until republishing is confirmed.
There is no application replay endpoint for this queue.

## Email retry and recovery

Network failures, HTTP 408/429/5xx and unexpected processing/database errors retry
after 10 seconds, 1 minute and 5 minutes. Other SendGrid rejection responses and
malformed messages go directly to the DLQ. Retry count is preserved in
`x-email-retry-count`; three retries exhaust the budget. Retry queue TTL arguments
are immutable: changing delays later requires a planned queue migration.

The consumer publishes a persistent copy to retry/DLQ, waits for a successful route
and publisher confirm, then ACKs the original. A publish failure keeps the original
delivery for another attempt. The listener uses a receive-only converter so malformed
JSON reaches the same confirmed DLQ path. The retry queues return expired messages
using quorum `at-least-once` dead-lettering with `reject-publish` overflow. This avoids
relying on the legacy email queue's default, unconfirmed DLX forwarding.

After fixing the delivery/configuration problem, an authenticated ADMIN can replay
at most 100 DLQ messages per request:

```http
POST /api/v1/admin/notifications/email-dlq/replay?limit=25
```

The response is a bare JSON object containing `replayed` (no `ApiResponse` envelope).
A limit outside 1–100 returns 400 with no body. Each message retains its event ID and receives a
fresh retry budget. It is ACKed in the DLQ only after its publication to the main
queue is confirmed. Concurrent replay requests settle their own deliveries. A
partial batch failure can have already replayed earlier messages; inspect queue
depth before retrying. Existing successful delivery IDs continue to suppress sends.
Inspect malformed messages in RabbitMQ management before replaying them, as their
payload is preserved rather than repaired by this endpoint.

## Outbox retry and recovery

Outbox retries use exponential backoff, default 5 seconds up to 5 minutes, configured
through `OUTBOX_RETRY_INITIAL_DELAY` and `OUTBOX_RETRY_MAX_DELAY`. After ten failed
attempts an event becomes `FAILED` and logs an error. Terminal failures require
explicit replay, avoiding endless retries of invalid payloads. Outbox work has a
dedicated scheduler and does not block booking expiry or payment inbox schedules.

Authenticated ADMIN endpoints:

| Request | Result |
| --- | --- |
| `GET /api/v1/admin/outbox/stats` | Pending and failed event counts |
| `GET /api/v1/admin/outbox/failed?page=0` | Up to 50 event IDs/types/attempt counts; no payloads |
| `POST /api/v1/admin/outbox/{eventId}/replay` | Atomically reset one FAILED event's attempts and retry time; keep its event ID |

These admin routes use bare responses: stats is a JSON object, failed events is a
JSON array (not `PageResponse`), and replay has no body. Negative failed-list `page`
returns 400 with no body. Replay returns 202 when reset, or 409 with no body if the
ID is absent or is no longer FAILED.
It cannot reset a PENDING/PUBLISHED event. Monitor pending age/depth and failed
counts; alert on terminal-outbox/DLQ error logs. Broker alarms still require the
deployment's monitoring system.

## Delivery guarantee

Email transport is at least once. A send accepted by SendGrid followed by a failed
database commit can still cause a duplicate on redelivery. Investigate provider
delivery history before manually changing deduplication records.

Test setup and verification commands are in [local development](local-development.md#test-and-verify).
