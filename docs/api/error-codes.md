# Error Codes

Machine-readable `code` values in error responses. See [API Conventions](./api-conventions.md) for the `ErrorResponse` shape.

| HTTP status | Error code | Meaning |
|-------------|------------|---------|
| 400 | `ADMIN_PERMISSIONS_INSUFFICIENT` | ADMIN role must retain all required permissions |
| 400 | `CANNOT_MODIFY_SELF` | You cannot perform this action on your own account |
| 400 | `DUPLICATE_OPERATING_DAY` | Each day can appear only once |
| 400 | `FULL_NAME_BLANK` | Full name cannot be blank |
| 400 | `INVALID_CLOSURE_TIME` | Closure start time must precede end time and times must use whole minutes |
| 400 | `INVALID_COURT_IMAGE` | Upload a valid PNG or JPEG image, at most 6000 pixels per side and 20 million pixels |
| 400 | `INVALID_COURT_SEARCH_FILTER` | Date is required for time or price filters; provide both startTime and endTime in whole minutes, start before end, and a non-negative ordered price range |
| 400 | `INVALID_CURRENT_PASSWORD` | Current password is incorrect |
| 400 | `INVALID_OPERATING_HOURS` | Opening time must precede closing time, times must use whole minutes, and at least one slot must fit |
| 400 | `INVALID_PAGINATION` | Page must be non-negative and size must be between 1 and 100 |
| 400 | `INVALID_PARAMETER` | Query or path parameter has the wrong type |
| 400 | `INVALID_PRICE_RULE_TIME` | Start time must precede end time and times must use whole minutes |
| 400 | `MALFORMED_JSON` | Request body is not valid JSON or cannot be parsed |
| 400 | `MISSING_PARAMETER` | Required query parameter is missing |
| 400 | `NO_FIELDS_TO_UPDATE` | At least one field must be provided |
| 400 | `OTP_INVALID` | Verification code is incorrect |
| 400 | `OTP_NOT_FOUND` | Verification code is missing or has expired |
| 400 | `PASSWORD_REUSED` | New password must differ from the current one |
| 400 | `PERMISSION_NOT_FOUND` | One or more permissions do not exist |
| 400 | `PRICE_RULE_OVERLAP` | Price intervals must not overlap within the same day |
| 400 | `ROLE_NOT_FOUND` | One or more roles do not exist |
| 400 | `ROLE_PERMISSION_CHANGE_MISMATCH` | Permission codes do not match the pending change request |
| 400 | `ROLE_PERMISSION_CHANGE_PENDING_NOT_FOUND` | No pending role permission change was found or it has expired |
| 400 | `SPORT_NOT_FOUND` | Sport does not exist |
| 400 | `UPLOAD_INVALID` | Invalid image or upload metadata |
| 400 | `VALIDATION_FAILED` | Request validation failed; see `fieldErrors` |
| 400 | `VERIFICATION_TOKEN_INVALID` | Verification link is invalid or has expired |
| 401 | `AUTHENTICATION_REQUIRED` | Authentication is required |
| 401 | `INVALID_CREDENTIALS` | Email or password is incorrect |
| 401 | `REFRESH_TOKEN_REUSED` | Refresh token was already used; all sessions were revoked |
| 401 | `TOKEN_INVALID` | Token is invalid or has expired |
| 401 | `TOKEN_REVOKED` | Token has been revoked; sign in again |
| 403 | `ACCESS_DENIED` | You do not have permission to perform this action |
| 403 | `ACCOUNT_DISABLED` | Account is disabled |
| 403 | `ACCOUNT_LOCKED` | Account is locked |
| 403 | `ACCOUNT_NOT_VERIFIED` | Account has not been activated yet |
| 403 | `COURT_ACCESS_DENIED` | You do not own this court |
| 403 | `ROLE_PERMISSION_CHANGE_NOT_ALLOWED` | Permissions for this role cannot be changed |
| 404 | `COURT_CLOSURE_NOT_FOUND` | Court closure does not exist |
| 404 | `COURT_IMAGE_NOT_FOUND` | Court image does not exist |
| 404 | `COURT_NOT_FOUND` | Court does not exist |
| 404 | `RESOURCE_NOT_FOUND` | No API resource exists for the requested path |
| 404 | `UPLOAD_NOT_FOUND` | Upload does not exist |
| 404 | `USER_NOT_FOUND` | User not found |
| 405 | `METHOD_NOT_ALLOWED` | HTTP method is not supported for this endpoint |
| 409 | `ACCOUNT_ALREADY_VERIFIED` | Account has already been verified |
| 409 | `COURT_CLOSURE_OVERLAP` | This interval overlaps an existing court closure |
| 409 | `COURT_IMAGE_LIMIT` | A court can have at most 10 images |
| 409 | `COURT_PUBLISH_NOT_ALLOWED` | Only draft courts can be published |
| 409 | `DATA_CONFLICT` | A database conflict occurred |
| 409 | `EMAIL_ALREADY_EXISTS` | Email is already registered |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | Resource was modified by another request; reload and retry |
| 409 | `UPLOAD_CHANGED` | Object changed during verification; retry completion |
| 409 | `UPLOAD_EXPIRED` | Upload expired; start a new upload |
| 409 | `UPLOAD_NOT_READY` | Upload has not been verified or is already attached |
| 409 | `USER_ALREADY_DISABLED` | Account is already disabled |
| 409 | `USER_ALREADY_LOCKED` | Account is already locked |
| 409 | `USER_NOT_LOCKED` | Account is not locked |
| 413 | `COURT_IMAGE_TOO_LARGE` | Image must not exceed 5 MiB |
| 413 | `UPLOAD_TOO_LARGE` | Image exceeds the permitted size |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Request content type is not supported |
| 429 | `OTP_TOO_MANY_ATTEMPTS` | Too many incorrect OTP attempts; request a new code |
| 429 | `TOO_MANY_REQUESTS` | Too many requests; try again later |
| 429 | `UPLOAD_LIMIT` | Too many unfinished uploads; retry later |
| 500 | `COURT_IMAGE_STORAGE_ERROR` | Image storage is temporarily unavailable |
| 500 | `INTERNAL_ERROR` | Unexpected server error |
| 503 | `UPLOAD_UNAVAILABLE` | Object storage is not configured or is unavailable |
