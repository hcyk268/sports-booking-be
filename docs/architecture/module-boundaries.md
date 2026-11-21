# Module boundaries

Modules live under `dev.booking.sports`. Spring Modulith checks their declared
`allowedDependencies` through [ArchitectureTest](../../src/test/java/dev/booking/sports/ArchitectureTest.java).
See [system architecture](architecture.md) for runtime flows and [API reference](../api/README.md)
for HTTP contracts.

## Dependency policy

A public HTTP endpoint does not expose its Java types to other modules. Cross-module
imports use named interfaces declared in `package-info.java`; a Java `public`
modifier alone is insufficient. `shared` is an open module.

| Module | Allowed dependencies | Owns |
| --- | --- | --- |
| `identity` | `shared` | Accounts, credentials, roles, permissions, tokens, sessions, verification and OTP |
| `court` | `shared` | Sports, court profiles, ownership, hours, pricing, closures, schedules and galleries |
| `booking` | `shared`, `court :: api` | Holds, occupancy, price snapshots, lifecycle and status history |
| `payment` | `shared`, `booking :: api`, `booking :: events` | Obligations, attempts, receipts, IPN inbox, settlement and refund records |
| `review` | `shared`, `booking :: api`, `court :: api` | Ratings, comments, replies, soft deletion and visibility moderation |
| `notification` | `shared`, `identity :: events`, `identity :: api`, `booking :: events` | User inbox, realtime hints, email rendering, delivery and deduplication |
| `shared` | No feature module | Envelopes, errors, persistence helpers, rate limiting, outbox and upload infrastructure |

Other feature-module entities, repositories, controllers and HTTP DTOs are internal.
Cross-module identifiers are UUID values rather than JPA entity relationships.

## Public contracts

| Named interface | Contracts | Consumers |
| --- | --- | --- |
| `identity :: api` | `EmailRecipientDirectory` for recipient snapshots; `StreamSessionVerifier` for stream authentication checks | `notification` |
| `identity :: events` | Identity email intents and `IdentityOutboxMessageTypes` | `notification` |
| `court :: api` | `CourtFacade` for ownership, publication and priced schedule quotations | `booking`, `review` |
| `booking :: api` | `BookingPaymentFacade` and immutable snapshots; `ReviewEligibility` | `payment`, `review` |
| `booking :: events` | Held, confirmed, cancelled and expired payloads; `BookingOutboxMessageTypes` | `payment`, `notification` |
| `payment :: events` | Payment succeeded/review-required payloads and message types | No current feature-module consumer |

### Booking and payment

Booking owns reservation state; payment owns financial state. Payment uses the booking
facade to authorize access, lock booking before payment, and apply validated transitions
within the same transaction. Cancellation cleanup consumes `booking.cancelled` and
verifies the cancelled booking through `lockCancelledBooking` before updating payment.
Payment must not write booking tables directly.

Payment query repositories currently join booking/court tables through read-only
projections for visibility filtering. This is explicit schema coupling; new access
should use narrow public facades rather than extend that coupling implicitly.

### Notifications and email

Identity emits email intents; booking emits lifecycle events. Notification consumes
those contracts, owns the inbox and email content, and uses `EmailRecipientDirectory`
without importing identity persistence. Booking notification and email intent commit
together. Notification exposes no named interface for direct feature-module calls.

Realtime delivery uses `StreamSessionVerifier` and after-commit Redis Pub/Sub. REST
remains authoritative; there is no durable hint replay. Client behavior belongs in
the [Notification API](../api/modules/notification-api.md).

### Court images and shared uploads

Court implements `UploadPolicy` through `CourtImageUploadPolicy` for `COURT_IMAGE`.
Shared owns upload verification, storage and asset state; court owns authorization,
gallery capacity and gallery records. Court calls `UploadService.claim`, `release`,
and `readUrl` after checking access. Shared receives the policy interface and does
not import court implementation classes.

## Shared contracts

| Contract | Usage |
| --- | --- |
| `OutboxService.enqueue(...)` | Identity, booking, payment and notification save intents in their business transaction |
| `OutboxPublicationHandler` | Booking, payment and notification route persisted events for the relay |
| `UploadPolicy`, `UploadService` | Court authorizes uploads and manages asset attachment/read access |
| `ApiResponse`, `PageResponse`, `ApiException`, `ErrorCode`, `RateLimit` | Common HTTP, error and rate-limit behavior |

Because `shared` is open, these are documented usage contracts rather than a class
allowlist. Keep domain rules in their owning feature modules.

## Changing a boundary

1. Keep invariants and writes in the owning module; expose only the required values or operations.
2. Add a narrow named interface and update `allowedDependencies` when a new cross-module import is needed.
3. For event changes, update publishers and consumers together, preserving compatibility with already persisted events.
4. Run `ArchitectureTest` and focused tests, then update the relevant API/architecture documentation.
