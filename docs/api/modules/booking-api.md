# Booking API

Booking supports creating a temporary hold, reading bookings, and cancelling them. Shared response and error rules are documented in [API conventions](../api-conventions.md).

Base path: `/api/v1/bookings`.

## Create a booking hold

`POST /api/v1/bookings`

Requires an authenticated `CUSTOMER` and an `Idempotency-Key` header (nonblank, at most 100 characters). The body selects one court and 1–20 configured slots:

```json
{
  "courtId": "11111111-1111-1111-1111-111111111111",
  "slots": [
    { "date": "2026-10-28", "startTime": "08:00", "endTime": "10:00" },
    { "date": "2026-10-28", "startTime": "18:00", "endTime": "20:00" }
  ]
}
```

Each requested interval must match one configured, priced and open schedule slot. Slots must be in the future and cannot overlap each other. The server calculates the price from the court configuration; the client does not submit a price. Successful requests return `201` with a `HELD` booking and its price snapshots. The hold expires after `app.booking.hold.duration` (default 10 minutes), or at the earliest slot start if sooner. A background worker expires holds and frees their slots.

Repeating the same request with the same customer and idempotency key returns the existing booking, even if its status has changed. Reusing that key for a different court or selection returns `409 IDEMPOTENCY_KEY_REUSED`. Selected slots are locked in stable order across instances. A PostgreSQL exclusion constraint is the final protection against concurrent overlapping bookings.

Invalid or overlapping selections return `400 INVALID_BOOKING_SLOTS`; an unconfigured, closed or unpriced slot returns `409 BOOKING_SLOT_NOT_BOOKABLE`; an occupied or currently locked slot returns `409 BOOKING_SLOT_UNAVAILABLE`. Concurrent use of the same idempotency key may return `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`; retry with the same key.

## List my bookings

`GET /api/v1/bookings/me`

Requires an authenticated user with the `CUSTOMER` role.

| Query parameter | Required | Description |
| --- | --- | --- |
| `status` | No | Exact booking status: `HELD`, `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, or `COMPLETED` |
| `date` | No | Local calendar date in `YYYY-MM-DD` format; matches a booking when any slot overlaps that day |
| `page` | No | Zero-based page number; defaults to `0` |
| `size` | No | Page size from `1` to `100`; defaults to `20` |

The `date` boundary uses `app.booking.zone-id`, which defaults to `Asia/Ho_Chi_Minh`. Results are ordered by booking creation time descending, with booking ID descending as a stable tie-breaker. Each item includes all selected slots.

Example:

```http
GET /api/v1/bookings/me?status=CONFIRMED&date=2026-10-23&page=0&size=20
Authorization: Bearer <access-token>
```

Invalid pagination returns `400 INVALID_BOOKING_PAGINATION`. Invalid enum or date syntax follows the shared invalid-parameter response.

## List manageable bookings

`GET /api/v1/bookings`

Requires an authenticated user with the `PROVIDER` or `ADMIN` role.

| Query parameter | Required | Description |
| --- | --- | --- |
| `customerId` | No | Exact customer ID |
| `courtId` | No | Matches bookings containing a slot for this court |
| `status` | No | Exact booking status |
| `date` | No | Local calendar date in `YYYY-MM-DD` format; matches when any slot overlaps that day |
| `page` | No | Zero-based page number; defaults to `0` |
| `size` | No | Page size from `1` to `100`; defaults to `20` |

Admins can list every matching booking. Providers only receive bookings for which they own every referenced court. A provider filtering by a court they do not own receives an empty page. Results use the same timezone, ordering, pagination rules, and response shape as `GET /api/v1/bookings/me`.

## Get a booking

`GET /api/v1/bookings/{id}`

Requires an authenticated user with at least one of the `CUSTOMER`, `PROVIDER`, or `ADMIN` roles. Access is granted when any applicable rule succeeds:

- a customer owns the booking;
- a provider owns every court referenced by its slots;
- an admin may read any booking.

The response contains the booking and all of its slots. A missing or inaccessible booking returns `404 BOOKING_NOT_FOUND`, so the endpoint does not reveal whether another user's booking exists.

## Cancel a booking

`POST /api/v1/bookings/{id}/cancellations`

Requires an authenticated customer who owns the booking, a provider who owns every court in its slots, or an admin. Send the current booking `version` and a reason of 1–500 nonblank characters:

```json
{ "version": 0, "reason": "Change of plans" }
```

The endpoint returns the updated booking with status `CANCELLED`. Every slot becomes `CANCELLED` and is available for a new booking. The change, status history, and `booking.cancelled` outbox event commit together. `HELD`, `PENDING_PAYMENT`, and `CONFIRMED` bookings can be cancelled; `CANCELLED`, `EXPIRED`, and `COMPLETED` bookings return `409 BOOKING_CANCELLATION_NOT_ALLOWED`. A stale version returns `409 OPTIMISTIC_LOCK_CONFLICT`. A missing or inaccessible booking returns `404 BOOKING_NOT_FOUND`.

## Current scope

Payment, booking confirmation, and refunds are not implemented yet. Cancelling a confirmed booking currently records the state change and publishes its event; no payment reversal is performed by this module.
