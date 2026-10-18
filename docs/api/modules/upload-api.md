# Upload API

Upload provides a shared direct-to-storage upload mechanism backed by Cloudflare R2 presigned URLs. It handles pre-authorized upload tickets, server-side content verification (dimensions, MIME, and image integrity), conditional object promotion, asset claiming by consuming modules, and temporary signed read access. Shared rules: [API conventions](../api-conventions.md). Errors: [error codes](../error-codes.md). Consuming modules: [Court API](./court-api.md).

Base path: `/api/v1/uploads`.

---

## Scope

| In scope | Out of scope (for now) |
|----------|-------------------------|
| Direct-to-storage presigned PUT ticket generation | Multipart / chunked file uploads |
| Purpose-based policy authorization and resource checks | Non-image files (PDF, video, documents) |
| Two-phase storage workflow (staging → verification → assets) | Public CDN distribution or public bucket endpoints |
| Server-side image decoding validation (PNG/JPEG, dimensions) | Antivirus scanning |
| Object promotion via S3 conditional copy (`CopySourceIfMatch`) | |
| Completion and verification endpoint (`/uploads/{id}/complete`) | |
| Internal claim and release protocol for consuming domain modules | |
| Short-lived presigned read URLs (60s) for private assets | |
| Cancellation of unattached uploads and background cleanup | |

All uploaded files bypass the application server on upload: clients stream bytes directly to Cloudflare R2 staging, and the backend verifies and promotes the object upon completion.

---

## Flows

**Direct upload & verification**

1. `POST /uploads` with `purpose`, `resourceId`, `fileName`, `contentType`, `sizeBytes` → backend verifies caller permissions via `UploadPolicy`, checks user concurrency limits, registers a `PENDING` asset (15m expiry), and issues a presigned PUT URL with required headers (5m expiry).
2. Frontend sends direct HTTP `PUT <uploadUrl>` to Cloudflare R2 with raw binary file bytes and exact headers (no JWT bearer token sent to R2).
3. `POST /uploads/{uploadId}/complete` (no request body) → backend queries R2 staging metadata, downloads bytes, validates PNG/JPEG structure and pixel dimensions via `ImageIO`, and performs conditional COPY to permanent storage key. Asset status transitions to `READY`.
4. Consuming module attaches asset (e.g. `POST /provider/courts/{courtId}/images` with `{ "uploadId": "..." }`) → backend claims asset under resource lock, transitioning status to `ATTACHED`.

**Cancel & cleanup**

1. `DELETE /uploads/{uploadId}` (204) before attachment → asset marked `DELETE_PENDING`.
2. Scheduled background worker purges R2 objects for `DELETE_PENDING` assets every 5 minutes and marks them `DELETED`.
3. R2 staging lifecycle rule auto-deletes orphaned staging objects after 1 day.

**Private asset read**

1. Consuming module authorizes the caller's access to the parent resource.
2. Backend calls internal `UploadService.readUrl(uploadId)` → issues presigned GET URL valid for 60 seconds.
3. Client accesses the signed URL directly without authentication headers.

---

## Business rules

- **Direct upload architecture:**
  - File bytes never transit the Spring application server during the client upload step.
  - The application server only downloads the file from staging during `/complete` to perform validation.
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
  - Rate limits: 20 initiates, 30 completes.
  - Concurrent unfinished limit: at most 20 uploads in `PENDING` or `READY` per user (`UPLOAD_LIMIT`, 429).
- **Asset binding:**
  - An upload is bound to the creating user (`ownerId`), `purpose`, and `resourceId`.
  - Only the creating user can complete, claim, or cancel the upload.
  - An asset cannot be claimed by a different resource, module, or user.
- **Private read access:**
  - Private assets are read via short-lived presigned GET URLs (60-second expiration).
  - Consuming modules must verify read authorization before issuing a read URL.
- **Infrastructure requirements:**
  - Private Cloudflare R2 bucket with S3-compatible API credentials.
  - Bucket CORS must allow `PUT`, `GET`, `HEAD`, headers `content-type`, `x-amz-meta-upload-id`, and expose header `ETag`.
  - R2 lifecycle rule must auto-delete `staging/` prefix after 1 day.
  - If `R2_ENABLED=false`, initiation returns `503 UPLOAD_UNAVAILABLE`.

---

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
| Storage service disabled (`R2_ENABLED=false`) | **503 Service Unavailable** (`UPLOAD_UNAVAILABLE`). |

---

## Endpoints

Permission column: **Authenticated** = valid JWT bearer token.

| Method | Path | Purpose | Permission |
|--------|------|---------|------------|
| POST | `/api/v1/uploads` | Initiate upload ticket (presigned PUT URL) | Authenticated |
| POST | `/api/v1/uploads/{uploadId}/complete` | Verify and promote uploaded asset | Authenticated |
| DELETE | `/api/v1/uploads/{uploadId}` | Cancel unattached upload session | Authenticated |

Request/response schemas: OpenAPI at `/v3/api-docs` (tag group Upload).
