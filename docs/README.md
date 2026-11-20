# Documentation

Start with the [project README](../README.md) for features and quick-start commands.
These documents describe the code currently in this repository. Planned APIs must
be marked explicitly; a controller signature or OpenAPI entry alone does not mean
an operation is implemented.

## Reading paths

| Task | Read |
| --- | --- |
| Start the backend | [Local development](guides/local-development.md) |
| Integrate a client | [API overview](api/README.md), [conventions](api/api-conventions.md), and [errors](api/error-codes.md) |
| Change business logic or dependencies | [System architecture](architecture/architecture.md) and [module boundaries](architecture/module-boundaries.md) |
| Change payment persistence | [Payment database contract](payment-schema.md) and [Payment API](api/modules/payment-api.md) |
| Diagnose asynchronous delivery | [RabbitMQ operations](guides/rabbitmq-operations.md) |
| Prepare demo data or sandbox checkout | [UAT seed data](guides/uat-seed-data.md) |

## Updating documentation

Keep API behavior in module references, setup commands in guides, and dependencies
in architecture documents. Link to the owning reference instead of repeating it.
Use relative links, fenced examples, and explicit labels for unimplemented routes.
Update documentation alongside the code it describes.
