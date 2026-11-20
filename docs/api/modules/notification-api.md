# Notification API

Base path: `/api/v1/notifications`.

All endpoints below require an authenticated user and operate only on that user's inbox,
including for ADMIN users. The recipient ID is obtained from the authenticated
principal; there is no recipient override. REST success responses use `ApiResponse`; the SSE stream uses its own format. Admin messaging recovery endpoints are documented separately in the [operations guide](../../guides/rabbitmq-operations.md).

| Method | Route | Response data (200) |
| --- | --- | --- |
| GET | `/api/v1/notifications?page=0&size=20` | `PageResponse<NotificationResponse>` |
| GET | `/api/v1/notifications/unread-count` | `{ "unreadCount": 3 }` |
| PATCH | `/api/v1/notifications/{id}/read` | Updated `NotificationResponse` |
| PATCH | `/api/v1/notifications/read-all` | `null` with a success message |
| GET | `/api/v1/notifications/stream` | SSE stream (`text/event-stream`) |

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
`READ_ALL`. Events are delivered only to the authenticated recipient; internal recipient IDs
are omitted from browser payloads.
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

Heartbeats are SSE comments. Connections close on expiry, overflow, send timeout,
or authentication-store failure. Revocation is rechecked before writes and on idle
heartbeats, so it is not instantaneous. Limits are per instance. Refresh the access
token and reconnect after expiry; refresh tokens are not accepted by this endpoint.

The endpoint sets `Cache-Control: no-store` and `X-Accel-Buffering: no`. Deployment
must still configure proxy idle/write timeouts, buffering and CORS. Interrupting a
stalled write is best effort; servlet/container write timeouts remain necessary.

### Delivery and resynchronization

Change hints publish after the database transaction commits. No-op read updates and
rollbacks emit no hint. Redis failure does not fail the committed request; hints have
no durable outbox or replay. All replicas must use the same channel, isolated from
other environments sharing Redis (database numbers do not isolate Pub/Sub):

```text
app.notification.realtime.channel = ${spring.application.name}:${spring.profiles.active}:notification.changed
```

Override with `APP_NOTIFICATION_REALTIME_CHANNEL` when needed. Executor sizing and
queue defaults are defined in `application.yml`.

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
REST. Frontend stream handling must implement this resynchronization behavior.

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
  "title": "Đặt sân thành công",
  "body": "Lịch đặt sân của bạn đã được xác nhận.",
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

Records start unread, contain Vietnamese titles/bodies, and appear after consumer
commit. Event ID, recipient and type deduplicate deliveries while preserving read
state. This consumer targets customers; held/payment events do not create entries.

The same transaction saves an email intent with recipient/content snapshots. A
rollback saves neither; duplicate delivery creates no additional intent. Emails
include booking details and local slot times using `app.booking.zone-id`.

The recipient address and rendered content are fixed when the intent is created;
later profile changes do not rewrite queued emails. Delivery remains at least once:
a provider-accepted email followed by a failed dedup transaction can be sent again.
Retry and dead-letter handling are documented in the
[RabbitMQ operations guide](../../guides/rabbitmq-operations.md).
