# API Conventions

This document defines cross-cutting rules for the Sports Booking HTTP API. Module-specific endpoints are documented under [modules](./modules/). The live contract is also published via [OpenAPI](#openapi).

---

## Summary

| Area | Rule |
|------|------|
| **URL** | Prefix `/api/v1` |
| **HTTP methods** | REST semantics |
| **JSON — fields** | camelCase property names |
| **JSON — envelopes** | Success uses `ApiResponse`; errors use `ErrorResponse` |
| **HTTP status** | Standard codes; see [HTTP status codes](#http-status-codes) |
| **Authentication** | `Authorization: Bearer <accessToken>` for protected routes |
| **Versioning** | URL path version; breaking changes require a new major path |
| **Content-Type** | `application/json` (UTF-8) for request and response bodies |
| **Pagination** | Query `page` / `size`; body uses `PageResponse` inside `ApiResponse` |
---

## URL design

### Base prefix

Following RESTFul APIs standard. All REST resources are served under:

```text
/api/v1/{resource}
```
---

## HTTP methods

| Method | Use                                               |
|--------|---------------------------------------------------|
| **GET** | Read a single resource or a paginated/filtered list. |
| **POST** | Create a resource.                                |
| **PUT** | Replace an entire nested collection.              |
| **PATCH** | Partial update of a resource (fields).            |
| **DELETE** | Delete a resource.                                |

---

## Content-Type

### Request

- Send **`Content-Type: application/json`** in JSON payloads.
- Character encoding is **UTF-8**.

### Response

- JSON responses use **`application/json`** unless the endpoint returns **no body** (e.g. `204 No Content`).

---

## JSON field naming and types

### Property names

- JSON properties use **camelCase**.

### Common types

| Type | JSON representation |
|------|---------------------|
| String | JSON string |
| UUID | String (canonical UUID format) |
| Boolean | JSON boolean |
| Enum | String matching the enum constant name (e.g. `ACTIVE`, `PENDING`) |
| Collection | JSON array |
| Optional absent field | Omit the property or `null` according to DTO definition; error payloads omit null optional fields where configured |


---

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

Some operations (e.g. logout) return **HTTP 204** with **no JSON body** and therefore **no** `ApiResponse` wrapper. Clients must not expect an envelope on these responses.

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
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "fieldErrors": {
    "email": "must be a well-formed email address",
    "password": "must be 8-36 characters and contain at least one letter and one digit"
  }
}
```

### Business errors

Application code throws `ApiException` with an enum implementing `ErrorCode`. A catalog of codes may be maintained in [error-codes.md](./error-codes.md) when available.

---

## HTTP status codes

| Status | When |
|--------|------|
| **200 OK** | Default success with `ApiResponse` body |
| **201 Created** | Resource created (e.g. registration, admin user create) |
| **202 Accepted** | Request accepted for processing (e.g. permission change OTP requested) |
| **204 No Content** | Success with no body (e.g. logout) |
| **400 Bad Request** | Validation failure, malformed JSON, invalid parameters |
| **401 Unauthorized** | Missing or invalid authentication (`AUTHENTICATION_REQUIRED`, invalid/expired token) |
| **403 Forbidden** | Authenticated but not allowed (`ACCESS_DENIED`, insufficient authority) |
| **404 Not Found** | Unknown route or resource |
| **405 Method Not Allowed** | HTTP method not supported for the path |
| **409 Conflict** | Data conflict (e.g. duplicate email, constraint violation) |
| **415 Unsupported Media Type** | Request `Content-Type` not supported |
| **429 Too Many Requests** | Rate limit exceeded (`TOO_MANY_REQUESTS`) |
| **500 Internal Server Error** | Unexpected server error (`INTERNAL_ERROR`) |

---

## Authentication and authorization

### Access token

Protected endpoints require:

```http
Authorization: Bearer <accessToken>
```

The access token is a JWT. Invalid, expired, or revoked tokens result in **401** with an appropriate error code (e.g. `TOKEN_INVALID`, `TOKEN_REVOKED`).

### Authorization

Fine-grained access uses permission strings (e.g. `USER_READ`, `USER_CREATE`) checked via method security. Missing permission returns **403** with `ACCESS_DENIED`.

---

## Pagination

List endpoints that support pagination accept query parameters:

| Parameter | Default | Rules |
|-----------|---------|--------|
| `page` | `0` | Zero-based page index |
| `size` | `20` | Page size; clamped to **1–100** (invalid values below 1 fall back to 20) |

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

Sorting and filters are defined per endpoint in module documentation (e.g. user list: optional `status`, sort by `createdAt` descending).

---


---

## API versioning

### Current version

The path segment **`v1`** is the stable public version.

---

## OpenAPI

- OpenAPI JSON: **`/v3/api-docs`**
- Swagger UI: **`/swagger-ui.html`**

---

## Related documentation

- [API overview](./README.md)
- [Identity module API](./modules/identity-api.md)
- [Court module API](./modules/court-api.md)
- [Upload module API](./modules/upload-api.md)
- [Error codes](./error-codes.md)
