# Review API

Review supports ratings and comments for completed bookings, provider replies, and
admin visibility moderation. Shared rules: [API conventions](../api-conventions.md).
Errors: [error codes](../error-codes.md).

Base paths: `/api/v1/courts`, `/api/v1/bookings`, `/api/v1/reviews`,
`/api/v1/provider/reviews`, and `/api/v1/admin/reviews`.

## Scope and authorization

- A customer may create one review per booking after it is `COMPLETED`, only for a court included in that booking. Deleting the review does not allow a replacement.
- Only the author may read the booking's own-review endpoint, edit, or delete the review.
- A provider may reply to a visible review of a court they own. Repeating the operation replaces the reply.
- An admin may list nondeleted reviews, including hidden reviews, and change visibility with a reason.
- Public feeds require an active published court and omit hidden/deleted reviews. Ratings use only visible, nondeleted reviews.

## Endpoints

| Method | Path | Access | Success data |
| --- | --- | --- | --- |
| GET | `/api/v1/courts/{courtId}/reviews` | Public | Feed: `averageStars` and paginated `reviews` |
| GET | `/api/v1/bookings/{bookingId}/review` | CUSTOMER, own review | Managed review |
| POST | `/api/v1/bookings/{bookingId}/review` | CUSTOMER, eligible booking | Managed review (201) |
| PUT | `/api/v1/reviews/{id}` | CUSTOMER, author | Managed review |
| DELETE | `/api/v1/reviews/{id}?version=0` | CUSTOMER, author | 204, no body |
| PUT | `/api/v1/provider/reviews/{id}/reply` | PROVIDER, court owner | Review entry |
| GET | `/api/v1/admin/reviews` | ADMIN | Page of managed reviews |
| PUT | `/api/v1/admin/reviews/{id}/visibility` | ADMIN | Managed review |

Success data is wrapped in `ApiResponse` except the 204 response. Lists accept
zero-based `page` (default 0) and `size` (default 20, range 1–100), ordered by
`createdAt DESC, id DESC`. Invalid bounds fail request validation. `averageStars`
is null when there are no visible reviews. A managed review contains `review`,
`hidden`, and moderation `reason`; public entries omit moderation fields, author
identity, and booking ID.

## Requests and response shape

Create a review after a provider/admin completes the booking:

```http
POST /api/v1/bookings/{bookingId}/review
Authorization: Bearer <accessToken>
Content-Type: application/json

{"courtId":"11111111-1111-1111-1111-111111111111","stars":5,"comment":"Sân sạch, đúng giờ."}
```

| Operation | JSON body |
| --- | --- |
| Edit | `{"version":0,"stars":4,"comment":"Updated comment"}` |
| Provider reply | `{"version":0,"text":"Thank you for your feedback."}` |
| Admin visibility | `{"version":0,"hidden":true,"reason":"Moderation reason"}` |

Stars must be an integer from 1 to 5. Comments and replies must be nonblank and at
most 2,000 characters; moderation reasons must be nonblank and at most 500 characters.
Text is stripped of surrounding whitespace. Existing-review mutations require the
latest nonnegative `version`; deletion sends it as a query parameter.

Example managed-review `data`:

```json
{
  "review": {
    "id": "22222222-2222-2222-2222-222222222222",
    "courtId": "11111111-1111-1111-1111-111111111111",
    "stars": 5,
    "comment": "Sân sạch, đúng giờ.",
    "reply": null,
    "createdAt": "2026-11-20T10:00:00Z",
    "version": 0
  },
  "hidden": false,
  "reason": null
}
```

## Important errors

| Status/code | Meaning |
| --- | --- |
| `404 REVIEW_NOT_FOUND` | Review/court missing, deleted, or inaccessible for this operation |
| `409 BOOKING_NOT_REVIEWABLE` | Booking is not completed by this customer at the supplied court |
| `409 REVIEW_ALREADY_EXISTS` | Booking already has a review, including a deleted one |
| `409 REVIEW_CONFLICT` | Supplied version is stale; reload before retrying |

The database also enforces one review per booking; a concurrent duplicate insert may
return shared `409 DATA_CONFLICT`. See [Booking completion](booking-api.md#complete-a-booking)
for the prerequisite lifecycle transition.
