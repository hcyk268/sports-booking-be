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

```powershell
.\scripts\configure-rabbitmq.ps1 -VirtualHost '/'
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

Booking/payment queues retain events for consumers outside this repository. No
consumer that discards events has been added. Each real logical consumer should
own its durable queue/binding and deduplicate using the envelope `eventId`.

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
There is no application replay endpoint for this queue in this change.

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

The response contains `replayed`. Each message retains its event ID and receives a
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

Replay returns 202 when reset, or 409 if the ID is absent or is no longer FAILED.
It cannot reset a PENDING/PUBLISHED event. Monitor pending age/depth and failed
counts; alert on terminal-outbox/DLQ error logs. Broker alarms still require the
deployment's monitoring system.

## Guarantees and validation

Email transport is at least once. A remote send accepted by SendGrid followed by
a failed database commit can still cause a duplicate on redelivery. Dedup records
created by the older pre-send implementation remain ambiguous; do not delete them
in bulk. Investigate provider delivery history before any manual repair.

Run focused unit/security tests and Docker-backed recovery tests:

```powershell
.\mvnw.cmd '-Dtest=EmailConsumerTest,EmailConsumerAdapterTest,EmailQueuePublisherTest,EmailRecoveryServiceTest,OutboxRetryTest,MessagingAdminSecurityTest,ArchitectureTest' test
.\mvnw.cmd -P integration '-Dtest=EmailDeliveryDedupServiceTest,EmailRecoveryIntegrationTest,OutboxServiceIntegrationTest' test
.\mvnw.cmd '-Dtest=BookingNotificationConsumerTest,BookingNotificationHandlerTest,BookingNotificationRecoveryServiceTest,ArchitectureTest' test
.\mvnw.cmd -P integration '-Dtest=BookingNotificationConsumerIntegrationTest' test
```

The integration tests cover PostgreSQL rollback/concurrent delivery, atomic outbox
replay, quorum TTL retry return and confirmed DLQ replay. They use disposable
Testcontainers services and need a running Docker daemon. They do not prove
production cluster failover or application-specific alert delivery.
