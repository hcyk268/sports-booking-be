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

These endpoints expose persisted notifications. Event-to-notification consumers,
email delivery and realtime delivery are separate flows.
