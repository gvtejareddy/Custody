# Setup and Run — Digital Asset Custody MVP

This document explains how to run the MVP locally and in Docker, how to prepare Fireblocks credentials, and what to consider for a production-ready deployment.

- Local (recommended for development)

- Install Java 25 and Maven.
- Create a Postgres database: default connection is `jdbc:postgresql://localhost:5432/custody`.
- Copy `.env.example` to `.env` and fill values.
- Build and run locally:

```bash
mvn -DskipTests package
java -jar target/digital-asset-custody-0.1.0-SNAPSHOT.jar
```

Docker (local)

```bash
docker compose up --build
```

Fireblocks setup (what you must do in the Fireblocks Console)

1. Create a new API Key (or use an existing one) for the bank application. Use the API Key ID and generate a private key file. Do NOT commit this key to source control.
2. Configure a Vault account(s) for the bank and map them to internal `vault_id` values.
3. Configure webhooks to send transaction events and deposit notifications to your `https://<host>/webhook/fireblocks` endpoint. Whitelist the IPs if required.
4. Create an allow-list of approved assets/networks in bank policy and in your adapter config.

Security and secrets

- Store Fireblocks private key and API key in Azure Key Vault for production.
- For local dev you may point to a file referenced by `FIREBLOCKS_PRIVATE_KEY_FILE` but protect it via OS file permissions.
- Use Managed Identity when deploying to Azure; inject secrets into the app via Key Vault.

Production checklist (Azure)

- Use Azure Database for PostgreSQL (single server or flexible) with private network.
- Use Azure Key Vault for Fireblocks keys and database credentials.
- Deploy container to AKS or Azure Container Apps.
- Use Azure API Management in front of the API for product integration.
- Integrate with bank IAM (OAuth2 / OIDC); do not expose endpoints without auth.
- Configure monitoring and alerts with Application Insights and Log Analytics.
