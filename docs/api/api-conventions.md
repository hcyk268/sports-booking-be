# API Conventions

This document defines cross-cutting rules for the Sports Booking HTTP API. Module-specific endpoints are listed in the [API overview](./README.md). The live contract is also published via [OpenAPI](#openapi).

## Common rules

- Base path: `/api/v1`; JSON property names use camelCase.
- Send JSON bodies as UTF-8 `application/json`.
- UUIDs and enum names are strings; timestamps use ISO-8601 instants unless a module states otherwise.
- Optional fields may be omitted or null according to their DTO.
- POST also supports documented commands such as cancellation and checkout; PUT replaces the documented resource or editable representation.

## Response envelopes

### Success — `ApiResponse<T>`

Most successful operations return a uniform envelope:

| Field | Type | Description |
|-------|------|-------------|
| `code` | string | `"SUCCESS"` for standard successes |
| `message` | string | Human-readable summary; default `"Request processed successfully"` or a custom message |
| `data` | object \| array \| null | Payload; `null` for message-only successes |
| `timestamp` | string | ISO-8601 instant (UTC) |
| `path` | string | Request URI (includes servlet path) |

Example:

```json
{
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 900000,
    "user": { }
  },
  "timestamp": "2026-10-15T16:00:00.123Z",
  "path": "/api/v1/auth/login"
}
```

### Success without a body — `204 No Content`

Some operations (e.g. logout, image deletion, review deletion) return **HTTP 204** with **no JSON body** and therefore **no** `ApiResponse` wrapper. Clients must not expect an envelope on these responses.

### Other success formats

- Messaging admin endpoints use bare JSON or empty status responses; see the [operations guide](../guides/rabbitmq-operations.md).
- SePay IPN acknowledgement returns bare JSON `{"success": true}` after inbox commit.
- Notification streaming returns `text/event-stream` with SSE frames.
- Court image content may redirect to a signed R2/S3 URL or return legacy image bytes.

These responses do not use `ApiResponse`. Their module documents define the contract.

### Errors — `ErrorResponse`

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | string | ISO-8601 instant (UTC) |
| `status` | number | HTTP status code (duplicated for convenience) |
| `code` | string | Machine-readable error code |
| `message` | string | Human-readable explanation |
| `path` | string | Request URI |
| `fieldErrors` | object \| omitted | Map of field name → message; present for validation failures |

Example (business error):

```json
{
  "timestamp": "2026-10-15T16:00:00.123Z",
  "status": 404,
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "path": "/api/v1/users/550e8400-e29b-41d4-a716-446655440000"
}
```

Example (validation):

```json
{
  "timestamp": "2026-10-15T16:00:00.123Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register",
  "fieldErrors": {
    "email": "must be a well-formed email address",
    "password": "must be 8-36 characters and contain at least one letter and one digit"
  }
}
```

## Authentication and authorization

### Access token

Protected endpoints require:

```http
Authorization: Bearer <accessToken>
```

The access token is a JWT. Invalid, expired, or revoked tokens result in **401** with an appropriate error code (e.g. `TOKEN_INVALID`, `TOKEN_REVOKED`).

### Authorization

Fine-grained access uses permission strings (e.g. `USER_READ`, `USER_CREATE`) checked via method security. Missing permission returns **403** with `ACCESS_DENIED`.

## Pagination

List endpoints that support pagination accept query parameters:

| Parameter | Default | Rules |
|-----------|---------|--------|
| `page` | `0` | Zero-based page index |
| `size` | `20` | Normally **1–100**; invalid bounds are rejected, except identity user-list size normalization described below |

The success payload is:

```json
{
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "items": [ ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 100,
      "totalPages": 5,
      "hasNext": true,
      "hasPrevious": false
    }
  },
  "timestamp": "...",
  "path": "/api/v1/users"
}
```

Court, booking, payment, notification, and review endpoints reject invalid page/size bounds. Identity user listing currently normalizes `size < 1` to 20 and caps `size > 100` at 100; clients should send a nonnegative page and size 1–100 consistently. Module-specific validation can return a business pagination code or `VALIDATION_FAILED`.

Sorting and filters are defined per endpoint in module documentation (e.g. user list: optional `status`, sort by `createdAt` descending).

## OpenAPI

- OpenAPI JSON: **`/v3/api-docs`**
- Swagger UI: **`/swagger-ui.html`**

## Related documentation

- [API overview](README.md)
- [Error codes](error-codes.md)
