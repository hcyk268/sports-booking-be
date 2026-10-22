# Court API

Court covers sports catalog lookup, public court discovery and search, daily operating schedules and pricing calculation, and provider-side court lifecycle management (profiles, weekly operating hours, price rules, temporary closures, and image gallery). Shared rules: [API conventions](../api-conventions.md). Errors: [error codes](../error-codes.md). Image upload lifecycle: [Upload API](./upload-api.md).

Base paths: `/api/v1/sports`, `/api/v1/courts`, `/api/v1/provider/courts`.

---

## Scope

| In scope | Out of scope (for now) |
|----------|-------------------------|
| Sports catalog listing | Booking availability checks and reservation holds |
| Public court search (keyword, city, sport) | Provider onboarding and business verification (KYC) |
| Advanced multi-filter search (date, slot time, total price) | Geolocation radius / distance-based court search |
| Public court details and daily slot schedules with price calculation | Accent-insensitive / Vietnamese unaccented full-text search |
| Provider court lifecycle: create `DRAFT`, update, publish to `ACTIVE` | Customer reviews, ratings, and public provider profile pages |
| Weekly operating hours management (slot duration 15–240 min) | |
| Day-of-week and hourly price rules (non-overlapping per day) | |
| Temporary date/time closures with overlap prevention | |
| Court image gallery (attach verified R2 upload, cover selection, delete) | |
| Short-lived presigned read URLs for private court images | |

New courts created by providers start in status `DRAFT`. Publishing changes status to `ACTIVE`. Only `ACTIVE` courts are visible to the public.

---

## Flows

**Court creation & onboarding → publishing (Provider)**

1. `POST /courts` (with `sportId`, `name`, `address`, `city`, optional `amenities`, `rules`) → court created with status `DRAFT`.
2. `PUT /provider/courts/{id}/operating-hours` → configure weekly operating days, opening/closing hours, and slot duration.
3. `PUT /provider/courts/{id}/price-rules` → configure hourly rates by day of week and time range.
4. Upload images via Shared Upload API (`POST /uploads` → direct PUT to R2 → `POST /uploads/{uploadId}/complete`).
5. `POST /provider/courts/{id}/images` (with `uploadId`) → attach image; the first attached image automatically becomes the cover image.
6. `POST /courts/{id}/publish` → status changes from `DRAFT` to `ACTIVE`; court becomes publicly discoverable.

**Discovery → schedule inspection (Customer / Public)**

1. `GET /sports` → list available sports for filtering.
2. `GET /courts` (basic search) or `GET /courts/search` (advanced search by date, time range, price range) → find active courts.
3. `GET /courts/{id}` → view court details, amenities, rules, and image gallery.
4. `GET /courts/{id}/schedule?date=YYYY-MM-DD` → view daily time slots, prices, and closure flags.

*Note: Search results and `closed: false` slots reflect schedule and closures only. Booking availability must be verified by the booking module prior to confirming a reservation.*

**Temporary closures (Provider)**

1. `POST /provider/courts/{id}/closures` (date, start/end time, reason) → creates closure; overlapping slots on that date are marked `closed: true`.
2. `GET /provider/courts/{id}/closures?date=YYYY-MM-DD` → inspect closures for that date.
3. `DELETE /provider/courts/{id}/closures/{closureId}` (204) → removes closure; slots reopen unless covered by another closure.

**Gallery & cover management (Provider)**

1. `POST /provider/courts/{id}/images` → attach verified upload (max 10 images).
2. `PUT /provider/courts/{id}/images/{imageId}/cover` → designate cover image.
3. `DELETE /provider/courts/{id}/images/{imageId}` (204) → deletes image; if it was cover, the first remaining image in display order becomes cover.
4. `GET /provider/courts/{id}/images/{imageId}/access` → short-lived presigned GET URL (60s) for private preview.

---

## Business rules

- **Visibility & status:**
  - Public endpoints only return `ACTIVE` courts (`COURT_NOT_FOUND` if court is `DRAFT` or nonexistent).
  - New courts start in `DRAFT`. Publishing transitions `DRAFT` to `ACTIVE`. Publishing an already `ACTIVE` court is idempotent.
  - Provider endpoints (`/provider/courts`) return both `DRAFT` and `ACTIVE` courts owned by the caller.
- **Ownership & security:**
  - Provider endpoints require `ROLE_PROVIDER` (`hasRole('PROVIDER')`).
  - The provider identity (`ownerId`) is always extracted from the JWT principal, never accepted from request bodies.
  - On `/courts/{id}` (update/publish): updating a court owned by another user returns `403 COURT_ACCESS_DENIED`.
  - On `/provider/courts/{id}/**` sub-resources: operations on a court not owned by the caller return `404 COURT_NOT_FOUND`.
- **Operating hours & slot generation:**
  - Weekly schedule allows at most 7 days (one entry per `DayOfWeek`; duplicates return `DUPLICATE_OPERATING_DAY`).
  - Days omitted from the schedule are days off (`slots: []`).
  - Opening and closing times must use whole minutes (`second == 0 && nano == 0`) and opening must precede closing (`openingTime < closingTime`).
  - `slotDurationMinutes` must be between 15 and 240 minutes. The opening duration must fit at least one complete slot (`INVALID_OPERATING_HOURS`).
  - Slots are generated sequentially starting from opening time; remainder time smaller than the slot duration is discarded.
- **Pricing rules & calculation:**
  - Maximum 100 price rules per court.
  - Rules specify `dayOfWeek`, `startTime`, `endTime`, and positive `pricePerHour` in VND (max 10 integer digits, 2 decimal places).
  - Time intervals on the same day must not overlap (`PRICE_RULE_OVERLAP`).
  - Slot prices are calculated by prorating the hourly rate over the duration falling within each matching price rule window.
  - If a slot is not completely covered by price rules, `priceConfigured: false` and `totalPrice: null`.
- **Temporary closures:**
  - Closures require date, whole-minute start/end times (`startTime < endTime`), and a reason (max 255 chars).
  - Overlapping closures on the same date are rejected (`COURT_CLOSURE_OVERLAP`, 409).
  - Any partial overlap with a closure marks the entire slot as `closed: true`. Adjacent boundaries do not intersect (e.g. closure ending at 12:00 does not close a slot starting at 12:00).
- **Search & filtering:**
  - Basic search (`/courts`): case-insensitive substring on keyword (`%` and `_` treated as literal characters); exact city match after trimming.
  - Advanced search (`/courts/search`): `date` is mandatory if `startTime`, `endTime`, `minPrice`, or `maxPrice` is provided (`INVALID_COURT_SEARCH_FILTER`).
  - Time range filter requires both `startTime` and `endTime`, whole minutes, and must align with complete consecutive slots of the court.
  - Price filter checks the **total price of the requested interval** in VND, not per-hour rates. Without a time range, it matches against single slots.
  - Courts with closures or unconfigured prices during the requested range are excluded.
  - Pagination is applied after slot/price matching; `totalElements` represents the true matching count.
- **Image management:**
  - Maximum 10 images per court (`COURT_IMAGE_LIMIT`, 409).
  - Image files must be uploaded via the Shared Upload API (`purpose: "COURT_IMAGE"`, `resourceId: courtId`).
  - Attach endpoint accepts JSON `{ "uploadId": "..." }`, NOT `multipart/form-data`.
  - The first attached image is automatically set as `cover = true`.
  - Deleting the cover image promotes the next image in display order to cover.
  - Images of `DRAFT` courts are private and cannot be viewed via public endpoints.

---

## Special cases

| Case | Behavior |
|------|----------|
| Re-publish active court | **200 OK**, idempotent; returns court details without error. |
| Empty operating hours (`days: []`) | **200 OK**, clears schedule; court has no operating slots (all days off). |
| Empty price rules (`rules: []`) | **200 OK**, clears price rules; slots show `priceConfigured: false` and `totalPrice: null`. |
| Re-attach same uploadId | **201 Created**, idempotent on the same court; returns the existing image record. |
| Attach uploadId belonging to another court | **404 COURT_IMAGE_NOT_FOUND**. |
| Advanced search without date | Returns matching courts with `match: null`. |
| Day off or duration < slot size | Schedule returns `slots: []`. |
| Partial closure intersection | Entire slot marked `closed: true`. |
| Image content redirect | **302 Found** redirect to short-lived R2 presigned GET URL (or **200 OK** raw bytes for legacy local images). |
| Private image access (`/access`) | **200 OK** with `{ "url": "<presigned URL>" }` (valid for 60s; `url: null` for legacy local images). |
| Delete closure / delete image | **204 No Content**, no JSON envelope. |

---

## Endpoints

Permission column: **Public** = no bearer token; **ROLE_PROVIDER** = valid JWT with `ROLE_PROVIDER` authority.

| Method | Path | Purpose | Permission |
|--------|------|---------|------------|
| GET | `/api/v1/sports` | List available sports | Public |
| GET | `/api/v1/courts` | Search public courts (keyword, city, sport) | Public |
| GET | `/api/v1/courts/search` | Advanced search (date, time range, total price) | Public |
| GET | `/api/v1/courts/{id}` | Get public court details | Public |
| GET | `/api/v1/courts/{id}/schedule` | Get daily court slots, prices, and closures | Public |
| GET | `/api/v1/courts/{courtId}/images` | List images of an active public court | Public |
| GET | `/api/v1/courts/{courtId}/images/{imageId}/content` | View image content of an active court | Public |
| POST | `/api/v1/courts` | Create a draft court | `ROLE_PROVIDER` |
| PUT | `/api/v1/courts/{id}` | Update an owned court profile | `ROLE_PROVIDER` |
| POST | `/api/v1/courts/{id}/publish` | Publish a draft court to active | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts` | List owned courts (draft and active) | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts/{id}` | Get details of an owned court | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts/{id}/operating-hours` | Get weekly operating hours | `ROLE_PROVIDER` |
| PUT | `/api/v1/provider/courts/{id}/operating-hours` | Replace weekly operating hours | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts/{id}/price-rules` | Get price rules | `ROLE_PROVIDER` |
| PUT | `/api/v1/provider/courts/{id}/price-rules` | Replace price rules | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts/{id}/closures` | Get court closures for a date | `ROLE_PROVIDER` |
| POST | `/api/v1/provider/courts/{id}/closures` | Create a temporary court closure | `ROLE_PROVIDER` |
| DELETE | `/api/v1/provider/courts/{id}/closures/{closureId}` | Delete a temporary court closure | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts/{courtId}/images` | List images of an owned court | `ROLE_PROVIDER` |
| POST | `/api/v1/provider/courts/{courtId}/images` | Attach a verified upload asset | `ROLE_PROVIDER` |
| PUT | `/api/v1/provider/courts/{courtId}/images/{imageId}/cover` | Choose cover image | `ROLE_PROVIDER` |
| DELETE | `/api/v1/provider/courts/{courtId}/images/{imageId}` | Delete an image | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts/{courtId}/images/{imageId}/access` | Get short-lived presigned read URL | `ROLE_PROVIDER` |
| GET | `/api/v1/provider/courts/{courtId}/images/{imageId}/content` | View image content of an owned court | `ROLE_PROVIDER` |

Request/response schemas: OpenAPI at `/v3/api-docs` (tag groups Court, Provider Court, Court Operating Hours, Court Price Rules, Court Closures, Court Images, Provider Court Images, Court Schedule, Court Search, Sport).
