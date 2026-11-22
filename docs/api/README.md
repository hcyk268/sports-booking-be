# API reference

The HTTP API uses `/api/v1`. Start with [API conventions](api-conventions.md) for
authentication, response envelopes, pagination, and status codes, then use the
module documents below for authorization and business behavior.

With the backend running locally:

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

OpenAPI describes declared routes and DTOs. Payment reconciliation and refund
routes are currently placeholders; their presence in Swagger does not mean they work.

## Modules

| Module | Implemented behavior |
| --- | --- |
| [Identity](modules/identity-api.md) | Registration, verification, JWT sessions, profiles, users, roles, and permissions |
| [Court](modules/court-api.md) | Sports, public discovery, provider court management, schedules, pricing, closures, and galleries |
| [Booking](modules/booking-api.md) | Holds, role-scoped queries, cancellation, expiry, and completion |
| [Payment](modules/payment-api.md) | Checkout creation/resume, scoped reads, authenticated IPN inbox, settlement, and pending-payment cancellation |
| [Notification](modules/notification-api.md) | Personal inbox, unread/read state, SSE hints, and asynchronous booking emails |
| [Review](modules/review-api.md) | Completed-booking reviews, public ratings, provider replies, and admin visibility moderation |
| [Upload](modules/upload-api.md) | Direct R2/S3 image upload, verification, asset lifecycle, and private read access |

## Related references

- [Error codes](error-codes.md)
- [Messaging admin endpoints and recovery](../guides/rabbitmq-operations.md)
- [Payment database contract](../payment-schema.md)
- [Documentation index](../README.md)
