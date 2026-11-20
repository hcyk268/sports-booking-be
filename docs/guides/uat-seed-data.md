# UAT seed data and sandbox checkout

Use the API scripts to create Vietnamese demo courts, R2 images, user profiles,
booking holds, and optional SePay checkout. This modifies the selected accounts and
court configuration; run it against a development or UAT environment.

## Prerequisites

- A running backend and PostgreSQL/Redis/RabbitMQ; see [local development](local-development.md).
- POSIX `sh`, `curl`, and `jq` (Linux/macOS, WSL, or Git Bash). `test-r2-upload.sh` also needs `openssl`.
- R2 enabled and configured with a private bucket, CORS, and lifecycle rules; see [Upload API](../api/modules/upload-api.md).
- Active provider/customer accounts with their respective roles. The script signs in existing users; it does not register or activate them.
- SePay sandbox configured for checkout, unless seeding with `--skip-payment`.

Run `sh scripts/<script-name>.sh --help` for supported options. Shared environment
variables are `UAT_SEED_API_BASE` (default `http://localhost:8080/api/v1`),
`UAT_SEED_PROVIDER_EMAIL`, `UAT_SEED_CUSTOMER_EMAIL`, and `UAT_SEED_PASSWORD`.
Set these explicitly for your test accounts; built-in demo account values in
`scripts/lib/common.sh` do not provision users in a fresh database.

## Seed data

From the repository root in a POSIX shell:

```sh
export UAT_SEED_PROVIDER_EMAIL='provider@example.com'
export UAT_SEED_CUSTOMER_EMAIL='customer@example.com'
export UAT_SEED_PASSWORD='<test-account-password>'
sh scripts/seed-uat-data.sh
```

Both accounts must use the supplied password. These are placeholders for accounts
you have prepared. PowerShell users should run these shell commands through Git
Bash or WSL. The booking date defaults to tomorrow in `Asia/Ho_Chi_Minh`.

| Seed option | Behavior |
| --- | --- |
| `--api-base URL` | Override the complete API base, including `/api/v1` |
| `--provider-email EMAIL`, `--customer-email EMAIL`, `--password PASSWORD` | Override login credentials |
| `--booking-date YYYY-MM-DD` | Select a future booking date |
| `--skip-payment` | Create holds without payment checkout |
| `--refresh-gallery` | Add images where capacity remains; existing images are retained |
| `--manifest-path FILE` | Override `scripts/uat-seed-manifest.json` |

Court definitions come from [uat-courts.json](../../scripts/uat-courts.json).

| Key | Court | Seed behavior |
| --- | --- | --- |
| `football-q9` | Saigon Seven Park - Vinhomes Grand Park | Two consecutive slots; checkout when enabled |
| `badminton-q7` | Phú Mỹ Badminton Club | One slot; hold only |
| `football-hn` | Hanoi Arena Mini - Cầu Giấy | One slot; hold only |

The script searches configured, priced, nonclosed schedule slots, then attempts a
hold. Conflicts cause it to try another selection; exact booking times are not
fixed to the example times in the JSON definitions. Prices depend on the chosen
weekday and slots. Profile names become Trần Minh Khoa (provider) and Lê Thị Mai
(customer), with demo phone numbers.

Courts are matched by `(uat-seed:<key>)` in their description. Repeated runs update
profiles, courts, operating hours, and prices; they retain galleries unless requested
to add more images. The marker remains in API-visible description text. Courts are
published to `ACTIVE` by the script.

## Manifest and repeat runs

The manifest records the API base, booking date, account emails, court IDs, bookings,
selected slots, amounts, hold deadlines, and payment/checkout metadata. It does not
store the full signed checkout form. Inspect it and the script logs: individual image
uploads or payment creation can fail while seeding continues, so a completed script
does not prove every integration succeeded.

Hold keys depend on court key, date, and selection. Repeating a run may replay an
existing booking, including a cancelled or expired booking; it does not renew the
hold. Choose another future date/selection or use the sandbox checkout script for a
new scenario. Checkout creation does not extend the original hold deadline.

## Booking and payment smoke test

```sh
# Seed and exercise the implemented booking/payment flow
sh scripts/test-booking-payment-flow.sh --seed-first

# Reuse the existing manifest
sh scripts/test-booking-payment-flow.sh

# Simulate an authenticated paid IPN in a local sandbox
sh scripts/test-booking-payment-flow.sh --seed-first --simulate-ipn
```

The script covers court search/schedule, booking queries, payment creation when
missing, payment reads, and checkout resume. It does not cover every endpoint,
refund placeholders, review flows, or real gateway collection.

| Smoke-test option | Behavior |
| --- | --- |
| `--seed-first` | Run seed before checks |
| `--manifest-path FILE` | Select the manifest |
| `--simulate-ipn` | Submit a synthetic authenticated paid IPN and check confirmation |
| `--sepay-secret SECRET` | Supply the IPN secret; otherwise use `SEPAY_SECRET_KEY` or read that key from repository `.env` |
| `--ipn-wait-seconds N` | Wait for worker processing (default 4 seconds) |

Use synthetic IPNs only for test payments in your sandbox. The same secret must be
configured in the backend. This tests application processing and does not transfer
money or verify real SePay delivery. Spring itself does not load `.env` automatically.

Login is limited to 10 attempts per 15 minutes. Scripts retry 429 responses within
a bounded retry budget; if the limit remains active, wait for its window to expire.

## R2 smoke test

```sh
sh scripts/test-r2-upload.sh --court-id '<owned-court-uuid>'
```

Use a provider owning that court. This checks upload, private read, and test-image
cleanup. It requires `openssl` in addition to the shared script prerequisites.

## Real SePay sandbox checkout

```sh
sh scripts/sepay-sandbox-checkout.sh --seed-first --open-browser
```

The script creates a checkout HTML form at `scripts/sepay-checkout.html`. If no
supported browser launcher is available, open the generated file manually. The
backend must be configured for `SEPAY_ENVIRONMENT=SANDBOX`. Complete the gateway's
test flow before the hold deadline; do not treat a frontend redirect as payment proof.

Real confirmation needs authenticated IPN delivery to
`/api/v1/payments/sepay/ipn`; a local backend normally needs a public tunnel for the
gateway to reach it. Local simulation is an alternative for application tests.
The hold defaults to ten minutes (or the earliest slot start, if sooner); pending
payment slots have a bounded settlement grace for on-time receipts, without extending
checkout eligibility. See [Payment API](../api/modules/payment-api.md#inbox-worker).

## Court visibility

Provider listing includes `DRAFT` and `ACTIVE` courts. Public discovery includes
only `ACTIVE` courts; date/time/price search also depends on configured hours,
prices, and closures. To publish all drafts owned by the configured test provider:

```sh
sh scripts/publish-draft-courts.sh
```

Publishing alone does not configure prices or hours. Review existing UAT courts in
the provider listing before running broad scripts; do not remove historical data
just to hide duplicate demo names.
