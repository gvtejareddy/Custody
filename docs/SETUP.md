# Setup and Run — Digital Asset Custody MVP

The MVP is local-first. Docker Compose supplies PostgreSQL 16 and WireMock; Azure is not required to build or test it.

1. Install Java 25, Maven, and Docker.
2. Copy `.env.example` to `.env` and add sandbox-only Fireblocks values if you are not using WireMock.
3. Build and run the full local stack:

```bash
mvn -DskipTests package
docker compose up --build
```

To run the application outside Docker, start only its database first:

```bash
docker compose up postgres -d
mvn spring-boot:run
```

Liquibase applies the schema automatically at application startup. Do not modify the database manually; add a Liquibase changeset for every schema change.

## Fireblocks sandbox

Create a sandbox API key and a sandbox private-key file, but never commit either. Configure its webhook to point at `https://<host>/api/v1/webhooks/fireblocks` when using a tunnel. Keep the bank’s asset/network allow-list in the application; Fireblocks remains an execution provider, not the system of record.

## Optional messaging emulator

The default `MESSAGING_MODE=local` processes outbox events with an in-process logging publisher. To exercise Azure Service Bus protocol behaviour locally, start the optional overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.messaging.yml up --build
```

The overlay configures the `outbox-events` queue and switches the app to `servicebus-emulator` mode. It is only for local testing.

## Later Azure migration

After the local MVP acceptance and security tests pass, use Azure Database for PostgreSQL, Key Vault/Managed Identity, Service Bus, API Management, and bank IAM as deployment concerns. Those changes must preserve the existing application interfaces and Liquibase changelog.
