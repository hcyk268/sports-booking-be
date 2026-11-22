# Sports Booking API

Backend for a sports court booking platform, supporting customers, court providers, and administrators.
It covers court discovery, booking holds, payments, notifications, and reviews.

**Java 21 · Spring Boot · PostgreSQL · Redis · RabbitMQ**

## Features

- **Identity:** registration, email verification, JWT authentication, and role-based access.
- **Courts:** search, provider management, operating hours, pricing, closures, and image galleries.
- **Bookings:** slot validation, temporary holds, expiry, cancellation, completion, and booking history.
- **Payments:** SePay checkout, IPN processing, and pending-payment cancellation after booking cancellation.
- **Notifications:** email delivery and in-app notifications.
- **Reviews:** reviews for completed bookings, provider replies, and admin moderation.

## Architecture & Stack

A **modular monolith** built with Spring Modulith. Business modules communicate through public interfaces
and events; background workers handle asynchronous delivery and cleanup.

| Layer | Technologies |
| --- | --- |
| Application | Java 21, Spring Boot, Spring Modulith |
| Persistence | PostgreSQL, Spring Data JPA, Flyway |
| Security & operational state | Spring Security, JWT, Redis |
| Messaging | RabbitMQ, transactional outbox, retry queues and dead-letter queues |
| Integrations | SePay, SendGrid, Cloudflare R2, Amazon S3 |
| Testing | JUnit, Mockito, Testcontainers, JaCoCo |

## Run Locally

Requires **JDK 21** and **Docker with Compose**. Maven is provided through the wrapper.
The demo and operations scripts additionally require a POSIX shell (Linux, macOS, WSL, or Git Bash).

From the repository root:

```sh
# Start PostgreSQL, Redis, and RabbitMQ
docker compose up -d

# Start the API with the default dev profile
sh mvnw spring-boot:run
```

On Windows PowerShell, use `.\mvnw.cmd spring-boot:run`
instead of `sh mvnw spring-boot:run`.

The `dev` profile uses local service defaults; Flyway applies database migrations on startup.
Docker Compose starts the supporting services; run the application separately with Maven.

| Service | Local URL |
| --- | --- |
| API base | `http://localhost:8080/api/v1` |
| Swagger UI | [Open Swagger](http://localhost:8080/swagger-ui.html) |
| OpenAPI schema | [Open JSON schema](http://localhost:8080/v3/api-docs) |
| RabbitMQ management | [Open management UI](http://localhost:15672) |

### Configuration

Set environment variables through your shell or IDE; Compose reads `.env`
automatically, while the backend does not. Keep backend connection settings aligned
with Compose. See [.env.example](.env.example) and [local development](docs/guides/local-development.md)
for configuration, optional SendGrid/R2/S3/SePay setup, and troubleshooting. Amazon S3 setup is documented in the [S3 upload guide](docs/guides/s3-upload.md).

## Testing & Demo Data

```sh
sh mvnw test                 # Default tests, excluding the integration group
sh mvnw -P integration test  # Include integration tests
sh mvnw verify               # Verification with coverage check
```

In PowerShell, replace `sh mvnw` with `.\mvnw.cmd`. Default smoke tests need local
services; integration tests may require Docker or an explicit disposable database.
See [test setup](docs/guides/local-development.md#test-and-verify).

For demo courts, R2 images, booking holds and sandbox checkout, follow the
[UAT guide](docs/guides/uat-seed-data.md).

## Current Limitations

Payment checkout, reads, IPN processing, and booking confirmation are implemented.
The reconciliation endpoint and seven admin refund endpoints are still placeholders;
their declared routes do not perform those operations. Public court schedules show
configured hours, pricing, and closures; they do not expose live booking occupancy.

## Documentation

- [Documentation index](docs/README.md) — reading paths and documentation conventions.
- [Local development](docs/guides/local-development.md) — setup, configuration, and troubleshooting.
- [API reference](docs/api/README.md) — endpoints, conventions, and error codes.
- [System architecture](docs/architecture/architecture.md) — components and data ownership.
- [Module boundaries](docs/architecture/module-boundaries.md) — dependencies and public contracts.
- [Payment database contract](docs/payment-schema.md) — schema, invariants, and database checks.
- [RabbitMQ operations](docs/guides/rabbitmq-operations.md) — queues, retries, and recovery.
- [UAT seed data](docs/guides/uat-seed-data.md) — demo setup and booking/payment smoke tests.
