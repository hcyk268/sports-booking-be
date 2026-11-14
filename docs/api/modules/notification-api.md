# Notification API

All endpoints require an authenticated user and operate only on that user's inbox,
including for ADMIN users. The recipient ID is obtained from the authenticated
principal; there is no recipient override. Success responses use `ApiResponse`.

| Method | Route | Response data (200) |
| --- | --- | --- |
| GET | `/api/v1/notifications?page=0&size=20` | `PageResponse<NotificationResponse>` |
| GET | `/api/v1/notifications/unread-count` | `{ "unreadCount": 3 }` |
| PATCH | `/api/v1/notifications/{id}/read` | Updated `NotificationResponse` |
| PATCH | `/api/v1/notifications/read-all` | `null` with a success message |

## SSE stream and Redis Pub/Sub

`GET /api/v1/notifications/stream` requires `Authorization: Bearer <accessToken>`
and returns a raw `text/event-stream`, not an `ApiResponse` envelope. Query-string
tokens are not accepted. Browser clients using Bearer authentication need fetch
streaming or another SSE client that supports request headers.
Errors before opening the stream use the usual JSON `ErrorResponse`, including
401 for invalid credentials and 429 for connection limits, even with SSE Accept headers.

Each accepted stream first receives:

```text
event: ready
data: {"connectionId":"..."}

```

The notification update event is:

```text
id: <eventId>
event: notification.changed
data: {"eventId":"...","action":"CREATED","notificationId":"..."}

```

Actions are `CREATED`, `READ`, and `READ_ALL`; `notificationId` is null only for
`READ_ALL`. The internal event also carries `recipientId`, which is omitted from
the browser payload. The registry sends only to that recipient's local connections.
Event IDs identify events, but this endpoint does not implement replay or honor
`Last-Event-ID` as a replay cursor. Synchronize through REST after `ready`/reconnect.

Defaults under `app.notification.sse`:

| Setting | Default |
| --- | --- |
| `heartbeat-interval` | 15 seconds |
| `connection-timeout` | 5 minutes, capped by access-token expiry |
| `send-timeout` | 10 seconds, including time queued for a sender |
| `max-connections-per-user` | 5 per instance |
| `max-connections` | 1,000 per instance |
| `connection-queue-capacity` | 32 frames |
| `sender-threads` | 4 |
| `sender-queue-capacity` | 128 tasks |

Heartbeats are SSE comments. The registry uses a dedicated bounded sender executor,
serializes frames within each connection, and cleans up on completion/error/timeout.
Overflow, rejected sends, authentication-store failures and send deadlines close the
affected connection. A one-second watchdog checks local deadlines; queued/in-flight
sends recheck token version and access-token blacklist before writing. Idle sessions
are rechecked on heartbeat, so revocation is not instantaneous.

The bearer token is not retained; only user/session IDs, token version and expiry
are stored. Refresh tokens are not accepted. After stream expiry, refresh the access
token via the existing auth flow and reconnect. Limits are local, not cluster-wide.

The endpoint sets `Cache-Control: no-store` and `X-Accel-Buffering: no`. Deployment
must still configure proxy idle/write timeouts, buffering and CORS. Interrupting a
stalled write is best effort; servlet/container write timeouts remain necessary.

### Publication after commit

Newly inserted booking notifications raise `CREATED`. Marking one previously unread
notification raises `READ`; marking one or more unread rows through read-all raises
`READ_ALL`. Repeated no-op writes and missing/inaccessible IDs emit no update.
The application event is registered inside the transaction; the Redis listener runs
only after a successful commit. Rollback or publication outside a transaction does
not send a message. There is no realtime Outbox.

`NotificationRedisPublisher` schedules publication on a separate bounded executor.
Redis/serialization failures and queue rejection are logged, without failing a
committed booking/read request or delaying it with network I/O. Zero subscribers
is a normal successful publish. Email continues to use its durable Outbox separately.

All instances subscribe to the same channel; each sends only to the recipient's
local SSE connections. Defaults under `app.notification.realtime`:

| Setting | Default |
| --- | --- |
| `channel` | `${spring.application.name}:${spring.profiles.active}:notification.changed` |
| `publisher-threads` / `publisher-queue-capacity` | 2 / 128 |
| `subscriber-threads` / `subscriber-queue-capacity` | 2 / 256 |

Override the channel (for example `APP_NOTIFICATION_REALTIME_CHANNEL`) consistently
across replicas and isolate environments sharing Redis. Pub/Sub is not isolated by
Redis database number. Subscriber dispatch uses a bounded executor with caller-runs
backpressure; actual SSE writes use the separate bounded SSE executor.

When Redis confirms a subscription, including after reconnect, all local streams
receive a resync hint:

```text
event: notification.sync
data: {"reason":"SUBSCRIPTION_READY"}

```

Clients should refresh the list and unread count after `ready`, `notification.sync`
and `notification.changed`, coalescing bursts. Also resync on reconnect, tab focus
and periodically. Events can be missed, duplicated or arrive in a different order
than commits; fetch authoritative state instead of incrementing counters locally.
Redis subscription recovery does not replay missed messages. A process crash after
DB commit but before publish is also possible; persisted data remains available via
REST. This change implements the backend; frontend stream handling is separate.

## Listing and unread count

`page` is zero-based (default 0); `size` defaults to 20 and must be between 1 and
100. Invalid bounds return `400 INVALID_NOTIFICATION_PAGINATION`. Malformed numeric
parameters or UUIDs return `400 INVALID_PARAMETER`. Notifications are ordered by
`createdAt DESC, id DESC`. An empty or out-of-range page returns an empty `items`
array with pagination metadata. The unread count includes only rows whose `readAt`
is null for the current user.

Each notification contains:

```json
{
  "id": "343a5322-c7a8-4612-9b15-91f520d33d68",
  "type": "BOOKING_CONFIRMED",
  "title": "Booking confirmed",
  "body": "Your court booking is confirmed.",
  "resourceType": "BOOKING",
  "resourceId": "95b09c92-c0fd-4f17-9b88-0b08b066894a",
  "createdAt": "2026-11-12T10:00:00Z",
  "readAt": null
}
```

`resourceType` and `resourceId` may both be null for a general notification.
Recipient IDs and internal source-event IDs are not returned.

## Marking notifications as read

Neither PATCH endpoint requires a request body. The server sets the read time.
Repeated requests succeed and preserve the first stored read time. Updates are
atomic database statements scoped to the current recipient and unread rows.

Marking a missing notification or another user's notification returns the same
`404 NOTIFICATION_NOT_FOUND`, without changing another user's inbox. Read-all
succeeds even if there are no unread notifications. It marks rows visible to its
UPDATE statement; notifications arriving afterward remain unread.

## Event-created notifications

A dedicated consumer creates customer inbox entries from version 1 booking events:

| Event | Notification type | Recipient |
| --- | --- | --- |
| `booking.confirmed` | `BOOKING_CONFIRMED` | Payload `customerId` |
| `booking.cancelled` | `BOOKING_CANCELLED` | Payload `customerId` |
| `booking.expired` | `BOOKING_EXPIRED` | Payload `customerId` |

The notification references the payload's booking ID; that ID must match the
envelope aggregate ID. Titles and bodies are in Vietnamese. Records start unread
and appear in these APIs after the consumer's database transaction commits.
Redelivered and concurrent copies of an event produce at most one inbox entry for
the same recipient and notification type, preserving existing read state.

`booking.held` and payment events do not create inbox entries through this consumer.
A successful payment already leads to `booking.confirmed`; consuming both as a
confirmation would create overlapping notifications. Provider/admin recipients and
realtime delivery remain separate flows.

For each newly inserted notification, the same database transaction snapshots the
customer's email/name through `identity :: api`, renders a booking email, and saves
`notification.booking-email-requested` in the shared Outbox. The email contains the
booking code, status, order value, slot times in `app.booking.zone-id` and cancellation
reason when present. User-controlled text is HTML-escaped by Thymeleaf.

The Outbox relay publishes the snapshot to the existing email queue using its Outbox
event ID as the email deduplication key. Email retries, DLQ and ADMIN replay use the
existing email delivery flow. Duplicate booking deliveries create no additional email
intent. A transaction failure rolls back both the notification and its email intent.
Notifications created before this feature are not automatically backfilled with email.

The recipient address and rendered content are fixed when the intent is created;
later profile changes do not rewrite queued emails. Delivery remains at least once:
a provider-accepted email followed by a failed dedup transaction can be sent again.
Retry and dead-letter handling are documented in the
[RabbitMQ operations guide](../../guides/rabbitmq-operations.md).
