# Upload API

Upload provides a shared direct-to-storage upload mechanism backed by Cloudflare R2 or Amazon S3 presigned URLs. It handles pre-authorized upload tickets, server-side content verification (dimensions, MIME, and image integrity), conditional object promotion, asset claiming by consuming modules, and temporary signed read access. Shared rules: [API conventions](../api-conventions.md). Errors: [error codes](../error-codes.md). Consuming modules: [Court API](./court-api.md). S3 setup: [Amazon S3 upload](../../guides/s3-upload.md).

Base path: `/api/v1/uploads`.

## Flows

**Direct upload & verification**

1. `POST /uploads` with `purpose`, `resourceId`, `fileName`, `contentType`, `sizeBytes` → backend verifies caller permissions via `UploadPolicy`, checks user concurrency limits, registers a `PENDING` asset (15m expiry), and issues a presigned PUT URL with required headers (5m expiry).
2. Frontend sends direct HTTP `PUT <uploadUrl>` to the selected storage provider with raw binary file bytes and exact headers (no JWT bearer token sent to storage).
3. `POST /uploads/{uploadId}/complete` (no request body) → backend queries that asset's provider for staging metadata, downloads bytes, validates PNG/JPEG structure and pixel dimensions via `ImageIO`, and performs conditional COPY to permanent storage key. Asset status transitions to `READY`.
4. Consuming module attaches asset (e.g. `POST /provider/courts/{courtId}/images` with `{ "uploadId": "..." }`) → backend claims asset under resource lock, transitioning status to `ATTACHED`.

**Cancel & cleanup**

1. `DELETE /uploads/{uploadId}` (204) before attachment → asset marked `DELETE_PENDING`.
2. Scheduled background worker purges objects for `DELETE_PENDING` and expired unattached assets every 5 minutes and marks them `DELETED`. It uses each asset's persisted provider and selects only enabled providers, so a disabled provider cannot block cleanup of another provider's files.
3. Each bucket's staging lifecycle rule auto-deletes orphaned staging objects after 1 day.

**Private asset read**

1. Consuming module authorizes the caller's access to the parent resource.
2. Backend calls internal `UploadService.readUrl(uploadId)` → issues presigned GET URL valid for 60 seconds.
3. Client accesses the signed URL directly without authentication headers.

## Business rules

- **Policies & permissions:**
  - Every upload request must specify a valid registered `purpose` (e.g. `COURT_IMAGE`).
  - The purpose policy verifies caller authorization and resource ownership (e.g. provider owns the target court).
  - The purpose policy enforces capacity limits (e.g. maximum 10 images per court).
- **File constraints:**
  - Supported content types: `image/png`, `image/jpeg`.
  - Size: minimum 1 byte, maximum 5,242,880 bytes (5 MiB).
  - Image dimensions: width and height <= 6000 pixels, total pixels <= 20,000,000.
  - File name: 1–120 characters, no ISO control characters.
- **Two-phase staging & security:**
  - Presigned PUT grants write access only to `staging/{uuid}` key for 5 minutes.
  - Backend verifies file bytes, metadata, and ETag, then uses S3 conditional COPY (`CopySourceIfMatch`) to promote file to `assets/{uuid}.ext`.
  - Clients have no write access to `assets/` keys.
  - Reusing an expired or old presigned PUT URL cannot alter an already accepted asset.
- **Lifecycles & state machine:**
  - States: `PENDING` → `READY` → `ATTACHED` → `DELETE_PENDING` → `DELETED`.
  - Ticket validity: presigned PUT URL expires in 5 minutes; complete/claim must occur within 15 minutes of initiation.
  - Rate limits: 20 initiates and 30 completes per 60-second window (separate actions).
  - Concurrent unfinished limit: at most 20 uploads in `PENDING` or `READY` per user (`UPLOAD_LIMIT`, 429).
- **Asset binding:**
  - An upload is bound to the creating user (`ownerId`), `purpose`, and `resourceId`.
  - Only the creating user can complete, claim, or cancel the upload.
  - An asset cannot be claimed by a different resource, module, or user.
- **Infrastructure requirements:**
  - `STORAGE_PROVIDER=r2` (default) or `s3` selects the provider for new uploads. Enable the selected provider with `R2_ENABLED=true` or `S3_ENABLED=true`; selecting a disabled provider while another provider is enabled fails startup.
  - Both providers may be enabled together. Each upload persists `storageProvider`; completing, reading, and deleting always use that original provider, even after changing the default. Migration V26 assigns existing uploads to R2.
  - Keep the original provider and bucket configuration enabled while those assets remain in use. Changing the default does not copy files between buckets. Disabling an asset's provider returns `503 UPLOAD_UNAVAILABLE` for its storage operations.
  - Private Cloudflare R2 bucket with S3-compatible API credentials, or private Amazon S3 bucket with its region and AWS credentials/IAM role.
  - Bucket CORS must allow `PUT`, `GET`, `HEAD`, headers `content-type`, `x-amz-meta-upload-id`, and expose header `ETag`.
  - Each bucket's lifecycle rule must auto-delete the `staging/` prefix after 1 day.
  - If both providers are disabled (default), initiation returns `503 UPLOAD_UNAVAILABLE`.
  - Cleanup interval: `app.storage.cleanup-interval` (defaults to legacy `app.storage.r2.cleanup-interval`, then `5m`).

## Special cases

| Case | Behavior |
|------|----------|
| Complete already `ATTACHED` upload | **200 OK**, idempotent; returns `VerifiedUpload`. |
| Complete already `READY` upload | **200 OK**, idempotent; returns `VerifiedUpload`. |
| Complete after 15m expiration | **409 Conflict** (`UPLOAD_EXPIRED`). |
| Complete with mismatched metadata / corrupted image | **400 Bad Request** (`UPLOAD_INVALID`). |
| Cancel unattached upload (`PENDING` or `READY`) | **204 No Content**, marks `DELETE_PENDING`; background worker cleans up. |
| Cancel already `ATTACHED` upload | **409 Conflict** (`UPLOAD_NOT_READY`). |
| Concurrent unfinished uploads >= 20 | **429 Too Many Requests** (`UPLOAD_LIMIT`). |
| Both storage providers disabled, or an existing asset's provider disabled | **503 Service Unavailable** (`UPLOAD_UNAVAILABLE`). |

## Endpoints

Permission column: **Authenticated** = valid JWT bearer token.

| Method | Path | Purpose | Permission |
|--------|------|---------|------------|
| POST | `/api/v1/uploads` | Initiate upload ticket (presigned PUT URL) | Authenticated |
| POST | `/api/v1/uploads/{uploadId}/complete` | Verify and promote uploaded asset | Authenticated |
| DELETE | `/api/v1/uploads/{uploadId}` | Cancel unattached upload session | Authenticated |

Request/response schemas: OpenAPI at `/v3/api-docs` (tag group Upload).
