# Local development

Run Spring Boot on your machine with PostgreSQL, Redis, and RabbitMQ in Docker.
Commands start from the repository root.

## Prerequisites

- JDK 21, with `JAVA_HOME` pointing to that JDK.
- Docker Engine/Desktop with Compose and a running daemon.
- Network access for Maven wrapper and dependency downloads on the first build.
- For `scripts/*.sh`: POSIX `sh`, `curl`, and `jq` through Linux/macOS, WSL, or Git Bash. The R2 smoke script also requires `openssl`.

```sh
java -version
docker compose version
```

## Start services and the application

```sh
docker compose up -d
docker compose ps
sh mvnw spring-boot:run
```

In PowerShell, use the Windows wrapper:

```powershell
docker compose up -d
docker compose ps
.\mvnw.cmd spring-boot:run
```

Compose also runs a one-shot `rabbitmq-policy` service. Its successful exit is
expected; inspect `docker compose logs rabbitmq-policy` to verify policy application.
The application runs outside Compose. The default profile is `dev`; Flyway applies
migrations before Hibernate validates the schema.

| Dependency | Development default |
| --- | --- |
| PostgreSQL | `localhost:5432`, database `sports_booking`, user/password `sports` |
| Redis | `localhost:6379`, database `0`, no password or TLS |
| RabbitMQ | `localhost:5672`, user/password `sports`, vhost `/` |
| API | `http://localhost:8080/api/v1` |
| Swagger UI | [Swagger](http://localhost:8080/swagger-ui.html) |
| OpenAPI | [JSON schema](http://localhost:8080/v3/api-docs) |
| Health | [Actuator health](http://localhost:8080/actuator/health) |
| RabbitMQ management | [Management UI](http://localhost:15672), same broker credentials |

## Configuration

Connection defaults come from `application-dev.yml`; common settings come from
`application.yml`. Use [.env.example](../../.env.example) as an example variable
list and inspect the YAML files for the complete configuration.

Compose reads a repository `.env` automatically. Spring Boot does not load that file
here: set variables in the shell or your IDE run configuration. Existing `.env`
overrides may change Compose ports and credentials; pass matching values to Spring.
Do not export empty placeholders wholesale: an empty value can suppress a default.

For example, to use another application port:

```sh
SERVER_PORT=8081 sh mvnw spring-boot:run
```

```powershell
$env:SERVER_PORT = '8081'
.\mvnw.cmd spring-boot:run
```

| Capability | Required configuration |
| --- | --- |
| Registration/OTP email delivery | `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, optional `SENDGRID_FROM_NAME`; RabbitMQ and outbox delivery must also work |
| Email verification links | `APP_FRONTEND_BASE_URL` and optional frontend path settings |
| Image upload | `R2_ENABLED=true` plus credentials, private bucket, CORS and lifecycle rules from the [Upload API](../api/modules/upload-api.md) |
| New payment checkout | `SEPAY_ENABLED=true` plus merchant, secret and return URLs from the [Payment API](../api/modules/payment-api.md) |
| Production profile | Explicit connections and JWT secrets from `application-prod.yml` and `application.yml` |

R2 and SePay checkout are disabled by default. Email is asynchronous: HTTP success
does not prove delivery. Configure SendGrid before testing activation and OTP flows.
The `dev` JWT defaults are for local development only.

## Test and verify

| Purpose | POSIX shell | PowerShell |
| --- | --- | --- |
| Default tests, excluding the `integration` group | `sh mvnw test` | `.\mvnw.cmd test` |
| Include integration tests | `sh mvnw -P integration test` | `.\mvnw.cmd -P integration test` |
| CI-style verification with coverage check | `sh mvnw verify` | `.\mvnw.cmd verify` |
| Module boundaries | `sh mvnw -Dtest=ArchitectureTest test` | `.\mvnw.cmd -Dtest=ArchitectureTest test` |

Default tests include Spring smoke tests using the `test` profile. Compose PostgreSQL
initialization creates `sports_booking_test` on first database initialization. Test
connection overrides use `DB_TEST_*`, `REDIS_TEST_*`, and `RABBITMQ_TEST_*` variables
in `application-test.yml`. Testcontainers integration tests need Docker. Some payment
tests also need an explicit `PAYMENT_TEST_JDBC_URL`; follow the
[payment database contract](../payment-schema.md).

Reports are in `target/surefire-reports`; coverage is in `target/site/jacoco/index.html`.

## Troubleshooting and shutdown

| Symptom | Check |
| --- | --- |
| Java release/compiler error | `java -version` and `JAVA_HOME` both select JDK 21 |
| Database/broker connection refused | `docker compose ps`, service health, ports, and matching backend environment variables |
| Authentication or refresh fails | Redis availability and account activation/status |
| No activation/OTP email | SendGrid configuration, failed outbox rows and email DLQ; see [RabbitMQ operations](rabbitmq-operations.md) |
| Upload or checkout returns 503 | Enable and configure its integration |
| Checkout exists but booking is not confirmed | Inspect IPN delivery/inbox processing; frontend return URLs do not confirm payment |
| Repeated seed reuses expired bookings | Booking keys replay stored bookings; see the [UAT guide](uat-seed-data.md) |

Stop the application with Ctrl+C. Stop services while retaining data:

```sh
docker compose down
```

Named volumes retain database and broker data. PostgreSQL initialization scripts run
only when its data directory is empty; changing environment variables does not rewrite
credentials or create databases in an existing volume.
