# Identity API

Identity covers registration, sign-in, sessions, user profiles, roles, and permissions. Shared rules: [API conventions](../api-conventions.md). Errors: [error codes](../error-codes.md).

Base paths: `/api/v1/auth`, `/api/v1/users`, `/api/v1/roles`, `/api/v1/permissions`.

---

## Scope

| In scope | Out of scope (for now) |
|----------|-------------------------|
| Self-service sign-up with email verification | OAuth / social login |
| JWT access + refresh tokens, per-device sessions | User self-delete |
| Forgot / change password (OTP) | Booking or venue APIs |
| Admin user lifecycle (create, list, lock, disable) | |
| RBAC: roles, permission catalog, guarded role edits | |

New users from **register** get role `CUSTOMER` and status `PENDING_VERIFICATION`. Admin **create user** sets status `ACTIVE`, assigns chosen roles, and emails a temporary password.

---

## Flows

**Registration → sign-in**

1. `POST /auth/register` → verification email (link token).
2. `GET /auth/verify-email?token=…` → account `ACTIVE`.
3. `POST /auth/login` → access + refresh tokens; permissions embedded in JWT.

**Session**

- `POST /auth/refresh` rotates refresh token on the same device session.
- `POST /auth/logout` (204) ends current session; `POST /auth/logout-all` (204) ends all devices.

**Forgot password**

1. `POST /auth/forgot-password` → OTP email (only if user exists and is `ACTIVE`).
2. `POST /auth/reset-password` → new password; **all sessions revoked**.

**Change password (signed in)**

1. `POST /auth/change-password/request-otp` (current password required) → OTP email.
2. `POST /auth/change-password` → new password; **all sessions revoked**.

**Role permission change (two steps)**

1. Read the role and retain its `version`.
2. `POST /roles/{code}/permissions/change-request` (202) with the permission set and `version` → OTP email.
3. `PUT /roles/{code}/permissions` with OTP, the same permission set, and the current `version` → permissions replaced.

---

## Business rules

- **Sign-in:** `PENDING_VERIFICATION`, `LOCKED`, and `DISABLED` accounts cannot obtain tokens (`ACCOUNT_NOT_VERIFIED`, `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED`).
- **Refresh token:** one-time use; reuse triggers `REFRESH_TOKEN_REUSED` and revokes all sessions for that user.
- **Access token:** checked against server state (revocation / version); invalid or revoked → `401`.
- **Admin actions on users:** lock, disable, and role replace cannot target **your own** account (`CANNOT_MODIFY_SELF`).
- **Concurrent identity updates:**
  - User and role responses include a non-negative `version`.
  - Profile updates, user role replacement, account lock/unlock/disable, and role permission changes require the latest returned `version`.
  - Lock/unlock/disable accept JSON `{ "version": 0 }`; other mutation requests include `version` alongside their existing fields.
  - A stale version or a transaction race returns `409 OPTIMISTIC_LOCK_CONFLICT`; reload the user or role before retrying.
  - Password, verification, and login writes are also protected by the persisted user version, but do not require clients to send it because they are command flows rather than edits of a previously read representation.
- **Role permissions:** `CUSTOMER` role permissions cannot be changed. `ADMIN` must always keep a fixed set of permissions (see `RolePermissionChangePolicy`).
- **OTP:** 6 digits; limited attempts; expiry configured in app settings.

---

## Special cases

| Case | Behavior |
|------|----------|
| Resend verification | Email sent only if account is still `PENDING_VERIFICATION`; response is always success-style (no email enumeration). |
| Forgot password | Same generic success if email unknown or not `ACTIVE`; no OTP sent. |
| Verify email | Invalid/expired token → `VERIFICATION_TOKEN_INVALID`; already-active user can verify again without error (idempotent activate path). |
| Logout | **204 No Content**, no JSON envelope. |
| Permission change request | **202 Accepted** with `ApiResponse` body. |
| Replace user roles | All sessions for that user are revoked immediately. |
| Lock / disable user | All sessions for that user are revoked. |
| Stale user or role version | **409 OPTIMISTIC_LOCK_CONFLICT**; no profile, role, or account-state change is persisted. |
| Rate limits | Login, resend verification, forgot password, change-password OTP, role permission OTP → `429` / `TOO_MANY_REQUESTS` when exceeded. |

---

## Endpoints

Permission column: **Public** = no bearer token; **Authenticated** = valid JWT, no specific permission; otherwise the **authority** required on the JWT.

| Method | Path | Purpose | Permission |
|--------|------|---------|------------|
| POST | `/api/v1/auth/register` | Register; send activation email | Public |
| GET | `/api/v1/auth/verify-email` | Activate account via link token | Public |
| POST | `/api/v1/auth/resend-verification` | Resend activation email | Public |
| POST | `/api/v1/auth/login` | Sign in; issue tokens | Public |
| POST | `/api/v1/auth/refresh` | Rotate refresh token | Public |
| POST | `/api/v1/auth/logout` | Sign out current device | Authenticated |
| POST | `/api/v1/auth/logout-all` | Sign out all devices | Authenticated |
| POST | `/api/v1/auth/forgot-password` | Request password-reset OTP | Public |
| POST | `/api/v1/auth/reset-password` | Reset password with OTP | Public |
| POST | `/api/v1/auth/change-password/request-otp` | Request change-password OTP | Authenticated |
| POST | `/api/v1/auth/change-password` | Change password with OTP | Authenticated |
| GET | `/api/v1/users/me` | Read own profile | `USER_READ_SELF` |
| PATCH | `/api/v1/users/me` | Update own profile | `USER_UPDATE_SELF` |
| POST | `/api/v1/users` | Create user (temp password email) | `USER_CREATE` |
| GET | `/api/v1/users` | List users (paginated, optional `status`) | `USER_READ` |
| GET | `/api/v1/users/{id}` | Get user by id | `USER_READ` |
| PATCH | `/api/v1/users/{id}` | Update user profile | `USER_UPDATE` |
| PUT | `/api/v1/users/{id}/roles` | Replace user roles | `USER_MANAGE_ROLE` |
| POST | `/api/v1/users/{id}/lock` | Lock account | `USER_LOCK` |
| POST | `/api/v1/users/{id}/unlock` | Unlock account | `USER_UNLOCK` |
| POST | `/api/v1/users/{id}/disable` | Disable account | `USER_UPDATE` |
| GET | `/api/v1/roles` | List roles and permissions | `ROLE_READ` |
| GET | `/api/v1/roles/{code}` | Get role by code | `ROLE_READ` |
| POST | `/api/v1/roles/{code}/permissions/change-request` | Start permission change (OTP) | `ROLE_MANAGE` |
| PUT | `/api/v1/roles/{code}/permissions` | Apply permission change (OTP) | `ROLE_MANAGE` |
| GET | `/api/v1/permissions` | List permission catalog (optional `module`) | `ROLE_READ` |

Request/response schemas: OpenAPI at `/v3/api-docs` (tag groups Auth, User, Role, Permission).
