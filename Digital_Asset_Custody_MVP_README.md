# Digital Asset Custody MVP — AI Agent Build Guide

> **Revision notes (this version):** re-scoped for **local-first development**. Azure is deferred to Appendix B — you build and fully test the MVP on your own machine with Docker, then migrate to Azure later without changing the application's abstractions. Concretely: swapped **Flyway → Liquibase**, pinned **PostgreSQL 16**, replaced the Azure-only local setup with a **Docker Compose** stack (Postgres + app + an *optional* local Service Bus emulator), and removed/annotated every Azure-specific instruction so it doesn't block local build/test. All prior architectural rules (bank owns the domain, Fireblocks is execution-only, key-custody model, ledger immutability, idempotency, etc.) are unchanged — only the infrastructure/tooling layer changed.

## 0. Purpose

Build a **bank-owned digital asset custody MVP**, buildable and fully testable **on a local machine with Docker**, using:

- Java 25
- Spring Boot 4.1.x (Spring Framework 7)
- PostgreSQL 16
- Liquibase (schema migrations)
- Docker / Docker Compose (local infra)
- Fireblocks (sandbox, for wallet/key/signing/blockchain execution)

Azure is the intended production target but is **not required to build or test the MVP**. Everything in §1–§72 runs locally. Azure-specific deployment concerns are isolated to Appendix B so they don't block the agent.

> **Version note:** Spring Boot 3.5.x reached end of OSS support on 30 June 2026. Spring Boot 4.0 (Nov 2025) and 4.1 (Jun 2026) are the only lines in active support, both require Java 17 minimum with first-class Java 25 support. This guide standardizes on **Java 25 + Spring Boot 4.1.x** everywhere.

### Core principle

> **The bank owns the client/account/position/ledger/transaction/policy/audit model. Fireblocks is an execution and wallet infrastructure provider.**

Do not make Fireblocks the customer position ledger or expose Fireblocks' domain model to consuming bank products.

---

## 0.1 Key-Custody Model — Decide Before Sprint 3

This MVP's non-negotiable architectural constraint is that **the bank must retain control of key shares regardless of vendor**. This must be resolved by the architecture review board before any Fireblocks adapter code (§27–29) is written, because it determines the workspace/vault topology, the signing-approval flow, and what "the bank owns the transaction lifecycle" means in practice. This decision is infrastructure-agnostic (local vs. Azure doesn't change it), so it can and should be settled in parallel with Sprint 1–2 local build work.

| Model | Description | Bank key-share control |
|---|---|---|
| **Fireblocks-hosted MPC (standard)** | Fireblocks operates the MPC/TSS cluster; the bank co-signs via policy/quorum but Fireblocks holds infrastructure for signing shards. | Partial — bank approves via policy engine, but does not independently hold all key shares. |
| **Fireblocks Off-Exchange / Dedicated / self-hosted co-signer** | Bank operates its own co-signer (on-prem or in its own cloud tenant) holding one or more MPC key shares; Fireblocks cannot complete a signature without the bank's shard. | Bank retains a key share — closer to satisfying "bank retains all key shares." |

Neither configuration is a pure bank-owned HSM mul
ti-sig model. If the non-negotiable is interpreted strictly (bank holds *all* shares, not just one of several), Fireblocks MPC alone cannot satisfy it — the bank would need either a self-hosted co-signer/dedicated workspace where Fireblocks never has quorum without the bank, or HSM-based multi-sig where the bank's own HSM holds a required signer and Fireblocks only provides broadcast/orchestration.

**Action item:** record the chosen model, the resulting vault/workspace topology, and the signing-quorum policy in an ADR before Sprint 3. For local development, the Fireblocks **sandbox** environment can be used regardless of which model is chosen — sandbox testing doesn't require the production key-custody topology to be finalized, only the adapter interface (§27) to be stable.

---

## 1. MVP Scope

### 1.1 In scope

The MVP supports:

1. Custody account creation
2. Asset/network configuration
3. Wallet/address mapping to Fireblocks
4. Client position ledger
5. Deposit address retrieval
6. Deposit detection through Fireblocks webhook/events
7. Deposit confirmation handling
8. AML/risk screening integration
9. Withdrawal request
10. Policy evaluation
11. Funds reservation
12. Fireblocks transaction submission
13. Fireblocks transaction status handling
14. Ledger settlement
15. Reconciliation against Fireblocks balances
16. Immutable/auditable transaction history
17. Product-facing REST APIs
18. Authentication/authorization (local: stubbed/simple JWT; bank IAM integration is a later step)
19. Idempotency
20. Operational monitoring and exception handling

### 1.2 Initial assets

Use only assets explicitly approved by the bank.

For the initial technical MVP, configure a small allow-list such as:

- BTC / Bitcoin
- ETH / Ethereum
- USDC / approved network

Do not assume that an asset is approved merely because Fireblocks supports it. **CASP/MiCA note:** the asset allow-list should trace back to whatever crypto-asset classification and due-diligence record Compliance requires under MiCA before an asset is offered in custody — keep a pointer (`compliance_reference`) from `asset` to that record rather than treating the allow-list as purely technical config.

### 1.3 Out of scope for MVP

Do NOT build:

- Own MPC implementation
- Own HSM implementation
- Own blockchain node infrastructure
- Own blockchain indexer
- Own blockchain analytics engine
- Travel Rule network
- Tokenisation platform
- Trading engine
- Exchange
- Staking
- Lending
- Derivatives
- Corporate actions engine
- Advanced hot/warm/cold optimisation
- Multi-custodian routing
- Customer-facing UI
- Mobile app
- Advanced fee engine
- Complex tax engine
- Automated recovery/failover between custody vendors
- Any Azure-specific deployment automation (defer to Appendix B)

These can be future phases.

---

## 2. Target Architecture

```text
                         BANK PRODUCTS
                +-----------+-----------+-----------+
                |           |           |           |
              Wealth      Trading   Institutional  Other
                |           |           |           |
                +-----------+-----------+-----------+
                            |
                            v
                  +-----------------------+
                  | Digital Asset API     |
                  | Spring Boot           |
                  +-----------+-----------+
                              |
                              v
                  +-----------------------+
                  | Custody Orchestrator  |
                  +-----------+-----------+
                              |
            +-----------------+------------------+
            |                 |                  |
            v                 v                  v
      Account/Asset      Policy/Compliance   Transaction
      Entitlement        AML/Risk            Lifecycle
            |                 |                  |
            +-----------------+------------------+
                              |
                              v
                  +-----------------------+
                  | Position / Ledger     |
                  | PostgreSQL 16         |
                  +-----------+-----------+
                              |
                              v
                  +-----------------------+
                  | Fireblocks Adapter    |
                  +-----------+-----------+
                              |
                              v
                  +-----------------------+
                  | Fireblocks            |
                  | Vaults / Wallets      |
                  | MPC / Signing         |
                  | Blockchain execution  |
                  +-----------+-----------+
                              |
                              v
                         BLOCKCHAINS
```

### 2.1 External systems (local development)

```text
Local stub / mock IAM (JWT, hardcoded roles)
   |
   v
Digital Asset API

AML / Sanctions / Risk  -->  WireMock stub (sandbox provider later)
   |
   v
Compliance Adapter

Fireblocks (sandbox tenant)
   |
   +--> Wallets
   +--> MPC/signing
   +--> Blockchain connectivity (testnets)
   +--> Transaction events (webhook -> local ngrok/tunnel or replayed via test harness)

Docker (local)
   |
   +--> PostgreSQL 16 (container)
   +--> Local outbox table / optional Service Bus emulator (container)
   +--> pgAdmin (optional, container)
```

Azure equivalents (Key Vault, Service Bus, API Management, Monitor) are mapped in **Appendix B** and are not needed to build or test locally.

---

## 3. Architectural Boundaries

### 3.1 Bank-owned

The bank application must own:

- Customer reference
- Custody account
- Entitlements
- Asset allow-list
- Network allow-list
- Client position
- Ledger entries
- Transaction lifecycle
- Withdrawal reservation
- Policy decision
- AML decision integration
- Wallet mapping
- Reconciliation result
- Audit trail
- API contract

### 3.2 Fireblocks-owned

For MVP, Fireblocks provides:

- Wallet infrastructure
- Vault/wallet infrastructure
- Cryptographic signing/MPC (subject to the key-custody model chosen in §0.1)
- Blockchain transaction execution
- Blockchain connectivity
- Provider transaction status
- Provider wallet balances

The bank application must maintain its own mapping to Fireblocks.

### 3.3 Never expose Fireblocks directly

Bank products must NOT call Fireblocks directly.

Correct:

```text
Product -> Bank Digital Asset API -> Custody Orchestrator -> Fireblocks Adapter -> Fireblocks
```

Incorrect:

```text
Product -> Fireblocks API
```

---

## 4. Recommended MVP Deployment Shape

Do NOT start with many microservices.

Use one Spring Boot application with strict modules/packages, runnable entirely via `docker-compose up`.

```text
digital-asset-custody/
  account/
  asset/
  ledger/
  transaction/
  wallet/
  policy/
  compliance/
  fireblocks/
  messaging/          <- new: abstracts local outbox vs. emulator vs. future Azure Service Bus
  reconciliation/
  audit/
  api/
```

Split into independent services later only if required. This reduces MVP complexity and distributed-transaction problems.

---

## 5. Technology Requirements

### 5.1 Application

- Java 25
- Spring Boot 4.1.x (Spring Framework 7)
- Spring Web (Spring Boot 4's API Versioning support can back `/v1/`)
- Spring Validation
- Spring Data JPA
- Spring Security
- PostgreSQL JDBC driver
- **Liquibase** (`liquibase-core`, `liquibase-maven-plugin` or Spring Boot's `spring-boot-starter-liquibase` auto-wiring)
- Actuator
- Jackson

> If Java 25 is not yet approved in your environment, Java 21 + Spring Boot 4.1.x (still supports Java 17+) is an acceptable fallback baseline. Do **not** fall back to Spring Boot 3.x — that line is EOL.

### 5.2 Local infrastructure (Docker)

- **PostgreSQL 16** (`postgres:16` official image)
- **Docker Compose** to orchestrate Postgres + app (+ optional emulator, see §5.4)
- **pgAdmin** or any Postgres GUI (optional, for manual inspection)
- **WireMock** (standalone or embedded) to simulate Fireblocks and the AML provider for local runs without hitting sandbox APIs
- **Testcontainers** for automated integration tests (spins up its own Postgres, independent of the dev Compose stack)

### 5.3 Testing

- JUnit 5
- Mockito
- Testcontainers (PostgreSQL 16 module)
- WireMock or equivalent for Fireblocks API simulation

### 5.4 Messaging — local options ("emulator if needed")

The outbox pattern (§21, §48) needs *something* to publish to. For local dev you have two options; pick based on how much you want to test messaging behavior itself vs. just the domain logic:

| Option | What it is | When to use |
|---|---|---|
| **A — Local outbox poller (default, no extra container)** | A scheduled `@Component` polls `outbox_event` rows with `status = PENDING` and marks them `PUBLISHED`, invoking an in-process `EventPublisher` interface (logs the event, or calls a no-op). No broker needed. | Default for MVP local dev. Fastest to start, zero extra moving parts, fully exercises the outbox-write transaction guarantee (§21/§48) which is the part that actually matters for correctness. |
| **B — Azure Service Bus Emulator (Docker, optional)** | Microsoft's official local emulator (`mcr.microsoft.com/azure-messaging/servicebus-emulator`, requires a paired `azure-sql-edge` container). Speaks the real Service Bus AMQP protocol, so the app can use the actual `azure-messaging-servicebus` SDK locally. | Turn this on (`docker compose --profile messaging up`) when you want to test topic/subscription fan-out, retries, or dead-lettering behavior before moving to Azure — see Appendix B. It is **not required** to complete the MVP scope in §1.1. |

Implement `EventPublisher` as an interface from day one so Option A and Option B are interchangeable without touching domain code — this is the same isolation principle as §59 for Fireblocks.

```java
public interface EventPublisher {
    void publish(OutboxEvent event);
}
```

- `LocalLoggingEventPublisher` — Option A, always available, default Spring profile.
- `ServiceBusEventPublisher` — Option B, active only under a `messaging` Spring profile, wraps the Azure Service Bus SDK pointed at the local emulator's connection string (and, later, at real Azure Service Bus — same code, different connection string).

---

## 6. Repository Structure

```text
digital-asset-custody/
├── README.md
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── docker-compose.messaging.yml     <- optional Service Bus emulator overlay (§5.4 Option B)
├── config/
│   └── servicebus-emulator/
│       └── Config.json              <- only needed if using Option B
├── .env.example
├── .gitignore
│
├── src/
│   ├── main/
│   │   ├── java/com/bank/custody/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── db/changelog/
│   │           ├── db.changelog-master.yaml
│   │           └── changes/
│   │
│   └── test/
│
└── infrastructure/                  <- placeholder for later Azure IaC (Appendix B), empty for MVP
```

---

## 7. Step 1 — Create Spring Boot Application

Create a Spring Boot application using Java 25 and Spring Boot 4.1.x.

Required dependencies:

```xml
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-actuator
postgresql
liquibase-core
lombok (optional)
```

Use Maven unless you prefer Gradle.

---

## 8. Step 2 — Configuration

Use environment variables/`.env` for local secrets. Never commit real credentials.

Never commit:

- Fireblocks private/sandbox keys
- database passwords
- JWT signing secrets

For MVP local dev, environment variables (via `.env`, loaded by Docker Compose) are the source of truth. Key Vault/Managed Identity are Azure-phase concerns — see Appendix B.

### 8.1 Local environment / configuration

| Variable | Purpose | Local default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Active profile | `local` |
| `SPRING_DATASOURCE_URL` | Postgres JDBC URL | `jdbc:postgresql://localhost:5432/custody` |
| `SPRING_DATASOURCE_USERNAME` | DB user | `custody_app` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | set in `.env`, never committed |
| `FIREBLOCKS_BASE_PATH` | Fireblocks API base | `https://sandbox-api.fireblocks.io/v1` (or WireMock URL, e.g. `http://localhost:9561`) |
| `FIREBLOCKS_API_KEY` | Fireblocks sandbox API key | `.env`, sandbox-only |
| `FIREBLOCKS_SECRET_KEY` | Fireblocks sandbox signing key path | `.env` / mounted file, sandbox-only |
| `FIREBLOCKS_WEBHOOK_PUBLIC_KEY` | Verify inbound webhook signatures | `.env`, sandbox-only |
| `MESSAGING_MODE` | `local` (Option A) or `servicebus-emulator` (Option B) | `local` |
| `SERVICEBUS_CONNECTION_STRING` | Only used when `MESSAGING_MODE=servicebus-emulator` | `Endpoint=sb://localhost:5672;...;UseDevelopmentEmulator=true;` |

`.env.example` should list all of the above with placeholder values and comments — never a real secret.

---

## 9. Step 3 — PostgreSQL 16 and Liquibase

Run PostgreSQL 16 via Docker (see §75 for `docker-compose.yml`).

Use **Liquibase** for ALL schema changes. Never manually modify the schema, even locally — treat local Postgres the same as you'll treat prod later, so migrations are proven before they matter.

### 9.1 Changelog structure

```text
src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── changes/
    ├── 001-create-custody-account.yaml
    ├── 002-create-asset.yaml
    ├── 003-create-network.yaml
    ├── 004-create-asset-network.yaml
    ├── 005-create-position.yaml
    ├── 006-create-ledger-entry.yaml
    ├── 007-create-custody-transaction.yaml
    ├── 008-create-wallet-mapping.yaml
    ├── 009-create-compliance-screening.yaml
    ├── 010-create-policy-decision.yaml
    ├── 011-create-provider-event.yaml
    ├── 012-create-outbox-event.yaml
    ├── 013-create-reconciliation-result.yaml
    └── 014-create-audit-event.yaml
```

`db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-custody-account.yaml
  - include:
      file: db/changelog/changes/002-create-asset.yaml
  - include:
      file: db/changelog/changes/003-create-network.yaml
  - include:
      file: db/changelog/changes/004-create-asset-network.yaml
  - include:
      file: db/changelog/changes/005-create-position.yaml
  - include:
      file: db/changelog/changes/006-create-ledger-entry.yaml
  - include:
      file: db/changelog/changes/007-create-custody-transaction.yaml
  - include:
      file: db/changelog/changes/008-create-wallet-mapping.yaml
  - include:
      file: db/changelog/changes/009-create-compliance-screening.yaml
  - include:
      file: db/changelog/changes/010-create-policy-decision.yaml
  - include:
      file: db/changelog/changes/011-create-provider-event.yaml
  - include:
      file: db/changelog/changes/012-create-outbox-event.yaml
  - include:
      file: db/changelog/changes/013-create-reconciliation-result.yaml
  - include:
      file: db/changelog/changes/014-create-audit-event.yaml
```

Example changeset (`001-create-custody-account.yaml`):

```yaml
databaseChangeLog:
  - changeSet:
      id: 001-create-custody-account
      author: custody-mvp
      changes:
        - createTable:
            tableName: custody_account
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: customer_id
                  type: varchar(100)
                  constraints:
                    nullable: false
              - column:
                  name: status
                  type: varchar(30)
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamptz
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamptz
                  constraints:
                    nullable: false
```

Spring Boot auto-runs Liquibase on startup against `SPRING_DATASOURCE_URL` when `liquibase-core` is on the classpath and `spring.liquibase.change-log` points at the master file (default location `classpath:/db/changelog/db.changelog-master.yaml` works if you don't override it).

Rollback: every changeset should include a `rollback:` block for anything beyond a plain `createTable` (e.g. column adds/drops later) — this is what makes `mvn liquibase:rollback` usable locally when iterating on the schema.

---

## 10. Step 4 — Database Model

### 10.1 custody_account

```sql
CREATE TABLE custody_account (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

(Shown here as SQL for readability — implement as the Liquibase changeset per §9.1.)

Statuses:

```text
PENDING
ACTIVE
FROZEN
CLOSED
```

---

## 11. asset

```sql
CREATE TABLE asset (
    id UUID PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    name VARCHAR(100),
    decimals INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    compliance_reference VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(symbol)
);
```

Statuses:

```text
ACTIVE
SUSPENDED
DEPRECATED
```

The application must reject unsupported assets. `compliance_reference` points to the bank's internal record of the MiCA/CASP due-diligence decision for that asset (see §1.2) — nullable for MVP, but keep the column so it isn't a later migration.

---

## 12. network

```sql
CREATE TABLE network (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(code)
);
```

Do not use asset symbol as network identity.

Example: `USDC + Ethereum` and `USDC + Solana` are different supported asset/network combinations.

---

## 13. asset_network

```sql
CREATE TABLE asset_network (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    network_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE(asset_id, network_id),

    FOREIGN KEY(asset_id) REFERENCES asset(id),
    FOREIGN KEY(network_id) REFERENCES network(id)
);
```

Only ACTIVE asset/network combinations may be transacted.

---

## 14. position

```sql
CREATE TABLE position (
    id UUID PRIMARY KEY,
    custody_account_id UUID NOT NULL,
    asset_id UUID NOT NULL,

    available NUMERIC(38,18) NOT NULL DEFAULT 0,
    locked NUMERIC(38,18) NOT NULL DEFAULT 0,
    pending NUMERIC(38,18) NOT NULL DEFAULT 0,

    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    UNIQUE(custody_account_id, asset_id),

    FOREIGN KEY(custody_account_id) REFERENCES custody_account(id),
    FOREIGN KEY(asset_id) REFERENCES asset(id),

    CONSTRAINT chk_position_non_negative
        CHECK (available >= 0 AND locked >= 0 AND pending >= 0)
);
```

Use Java `BigDecimal`. Never use `double` or `float`. The `CHECK` constraint enforces the invariant from §67 at the database level, not just in application code.

---

## 15. Ledger

```sql
CREATE TABLE ledger_entry (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    custody_account_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(38,18) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,

    FOREIGN KEY(custody_account_id) REFERENCES custody_account(id),
    FOREIGN KEY(asset_id) REFERENCES asset(id)
);
```

Ledger entries must never be updated or deleted. Locally, revoke `UPDATE`/`DELETE` on this table from the app's Postgres role in a changeset (`REVOKE UPDATE, DELETE ON ledger_entry FROM custody_app;`) so immutability is enforced at the DB level from day one, not bolted on before production.

Corrections are new compensating entries.

---

## 16. Custody Transaction

```sql
CREATE TABLE custody_transaction (
    id UUID PRIMARY KEY,
    custody_account_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    network_id UUID,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(50) NOT NULL,
    amount NUMERIC(38,18) NOT NULL,
    destination_address VARCHAR(500),
    provider_transaction_id VARCHAR(200),
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    FOREIGN KEY(custody_account_id) REFERENCES custody_account(id),
    FOREIGN KEY(asset_id) REFERENCES asset(id),
    FOREIGN KEY(network_id) REFERENCES network(id)
);
```

Types:

```text
DEPOSIT
WITHDRAWAL
INTERNAL_TRANSFER
```

MVP only needs DEPOSIT and WITHDRAWAL.

---

## 17. Wallet Mapping

```sql
CREATE TABLE wallet_mapping (
    id UUID PRIMARY KEY,
    custody_account_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    network_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_vault_id VARCHAR(200) NOT NULL,
    provider_wallet_id VARCHAR(200),
    blockchain_address VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    UNIQUE(custody_account_id, asset_id, network_id),

    FOREIGN KEY(custody_account_id) REFERENCES custody_account(id),
    FOREIGN KEY(asset_id) REFERENCES asset(id),
    FOREIGN KEY(network_id) REFERENCES network(id)
);
```

Provider-specific identifiers are isolated here.

---

## 18. Compliance Screening

```sql
CREATE TABLE compliance_screening (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    screening_type VARCHAR(50) NOT NULL,
    provider VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    risk_score NUMERIC(18,8),
    decision VARCHAR(30),
    provider_reference VARCHAR(200),
    result JSONB,
    created_at TIMESTAMPTZ NOT NULL,

    FOREIGN KEY(transaction_id) REFERENCES custody_transaction(id)
);
```

Do not store unnecessary sensitive personal information. For local dev, this provider is a WireMock stub (§37) — no real AML vendor is called.

---

## 19. Policy Decision

```sql
CREATE TABLE policy_decision (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    decision VARCHAR(30) NOT NULL,
    rules JSONB NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,

    FOREIGN KEY(transaction_id) REFERENCES custody_transaction(id)
);
```

Decision: `ALLOW`, `DENY`, `REVIEW`.

---

## 20. Provider Event

```sql
CREATE TABLE provider_event (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,

    UNIQUE(provider, provider_event_id)
);
```

This provides webhook/event idempotency.

---

## 21. Outbox

```sql
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
```

The database transaction must update the domain state AND create the outbox event atomically. Locally, this row is what the §5.4 Option A poller reads — the correctness of *this* table/transaction is what actually matters for MVP; which broker eventually reads from it (none, emulator, or real Azure Service Bus) is a swappable detail behind `EventPublisher`.

---

## 22. Reconciliation

```sql
CREATE TABLE reconciliation_result (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    network_id UUID NOT NULL,
    bank_balance NUMERIC(38,18) NOT NULL,
    provider_balance NUMERIC(38,18) NOT NULL,
    difference NUMERIC(38,18) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reconciliation_time TIMESTAMPTZ NOT NULL,

    FOREIGN KEY(asset_id) REFERENCES asset(id),
    FOREIGN KEY(network_id) REFERENCES network(id)
);
```

Statuses: `MATCH`, `BREAK`, `ERROR`.

---

## 23. Audit

```sql
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_type VARCHAR(50) NOT NULL,
    actor_id VARCHAR(200),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(200),
    correlation_id VARCHAR(200),
    data JSONB,
    created_at TIMESTAMPTZ NOT NULL
);
```

Audit: account creation, address creation, deposit, withdrawal, policy decision, AML decision, funds reservation, Fireblocks submission, transaction status changes, reconciliation, freeze/unfreeze.

Never log private keys or secrets.

---

## 24. Step 5 — Domain Enums

```text
CustodyAccountStatus
AssetStatus
NetworkStatus
AssetNetworkStatus
TransactionType
TransactionStatus
WalletStatus
ComplianceDecision
PolicyDecision
ReconciliationStatus
AuditAction
```

Do not scatter raw strings throughout the application.

---

## 25. Step 6 — Custody Account API

```http
POST /v1/custody-accounts
GET  /v1/custody-accounts/{id}
```

Create account request:

```json
{ "customerId": "C12345" }
```

Response:

```json
{
  "custodyAccountId": "UUID",
  "customerId": "C12345",
  "status": "ACTIVE"
}
```

For MVP local dev, `customerId` can be an arbitrary string you pass in manually — real bank customer-master integration is a later step. Do not build a second customer master even for MVP; treat `customerId` as a foreign reference, not a place to store customer attributes.

---

## 26. Step 7 — Position API

```http
GET /v1/custody-accounts/{id}/positions
```

Response:

```json
{
  "accountId": "UUID",
  "positions": [
    {
      "asset": "BTC",
      "available": "1.50000000",
      "locked": "0.25000000",
      "pending": "0",
      "total": "1.75000000"
    }
  ]
}
```

`total = available + locked + pending`, computed in the read path — never stored as a separate mutable column. Do not allow clients to directly modify positions.

---

## 27. Step 8 — Fireblocks Adapter

```java
public interface CustodyExecutionProvider {
    ProviderWallet getWallet(Asset asset, Network network, UUID custodyAccountId);
    ProviderAddress getDepositAddress(WalletReference wallet, Asset asset, Network network);
    ProviderTransaction submitWithdrawal(WithdrawalExecutionRequest request);
    ProviderTransaction getTransaction(String providerTransactionId);
    List<ProviderBalance> getBalances();
}
```

```java
@Component
public class FireblocksExecutionProvider implements CustodyExecutionProvider { }
```

The rest of the application must depend only on the interface. This is what makes local testing possible without hitting real Fireblocks: implement a second `WireMockExecutionProvider`-friendly config (i.e. point `FireblocksExecutionProvider` at a local WireMock server via `FIREBLOCKS_BASE_PATH`) for fast local iteration, and the real Fireblocks **sandbox** tenant for end-to-end verification before any production conversation.

---

## 28. Step 9 — Fireblocks API Client

```text
FireblocksClient
FireblocksRequestSigner
FireblocksMapper
FireblocksExecutionProvider
FireblocksWebhookController
FireblocksWebhookProcessor
```

Keep all Fireblocks-specific DTOs inside the `fireblocks` module. Do not use Fireblocks DTOs in the core domain. Use the Fireblocks tenant/developer documentation as the authoritative source for authentication, request signing, endpoint names, asset identifiers, transaction parameters and webhook formats. Do not hard-code undocumented assumptions.

**Local webhook delivery:** Fireblocks sandbox needs a publicly reachable URL to deliver webhooks to your local machine. Use a tunnel (e.g. `ngrok http 8080`) pointed at `/v1/internal/providers/fireblocks/events`, or — for pure offline development — skip live webhooks entirely and drive the deposit lifecycle (§32) from a test harness that inserts synthetic `provider_event` rows / calls the webhook controller directly with fixture payloads.

---

## 29. Step 10 — Deposit Address

```http
POST /v1/custody-accounts/{accountId}/deposit-addresses
```

Request:

```json
{ "asset": "BTC", "network": "BITCOIN" }
```

Flow:

```text
Validate account
     |
Validate asset/network
     |
Check existing mapping
     |
     +-- exists --> return address
     |
     +-- missing --> Fireblocks
                       |
                       v
                  create/get wallet/address
                       |
                       v
                  save mapping
                       |
                       v
                  return address
```

The API must be idempotent for the same account/asset/network.

---

## 30. Step 11 — Fireblocks Webhook

```http
POST /v1/internal/providers/fireblocks/events
```

> Keep this under the same `/v1` prefix as the rest of the app, but exclude it from any future public-facing gateway (see §54, Appendix B).

The webhook endpoint must:

1. Validate authenticity according to Fireblocks documentation
2. Validate payload
3. Generate correlation ID
4. Store `provider_event`
5. Reject duplicate provider event IDs safely
6. Publish an internal event (via `EventPublisher`, §5.4)
7. Return quickly

Do NOT execute a long transaction workflow synchronously in the HTTP webhook.

---

## 31. Step 12 — Local Event Publishing

Replaces the Azure-only "Service Bus setup" step. Implement the `EventPublisher` interface from §5.4 and wire it by profile:

```yaml
# application-local.yml
messaging:
  mode: local   # or servicebus-emulator
```

```text
Fireblocks / domain event
   |
   v
outbox_event (row written in same DB transaction as domain change)
   |
   v
Outbox poller (@Scheduled)
   |
   v
EventPublisher.publish(event)
   |
   +-- mode=local --> LocalLoggingEventPublisher (logs, marks PUBLISHED)
   |
   +-- mode=servicebus-emulator --> ServiceBusEventPublisher (real AMQP call to emulator container)
```

Configure retry-with-backoff in the poller and a `FAILED`/dead-letter status on `outbox_event` after N attempts. Never silently discard failed events — this rule doesn't change just because there's no Azure Service Bus locally.

---

## 32. Step 13 — Deposit Lifecycle

```text
DEPOSIT_DETECTED -> SCREENING -> PENDING_CONFIRMATION -> CONFIRMED -> POSTED
```

Processing:

```text
Provider event
    |
Identify Fireblocks vault/address
    |
Find wallet_mapping
    |
Find custody account
    |
Create custody transaction
    |
AML/risk
    |
Wait for required confirmation state
    |
Post ledger entries
    |
Update position
    |
Emit DepositPosted
```

Unknown addresses must go to an exception state. Never automatically credit an unidentified client account.

---

## 33. Step 14 — Ledger Deposit Posting

Client receives 1 BTC → create an immutable ledger entry representing the client credit → update position transactionally (`available += 1 BTC`) in the **same** PostgreSQL transaction as the ledger insert.

Use an idempotency key derived from the unique provider transaction/event identity.

---

## 34. Step 15 — Withdrawal API

```http
POST /v1/custody-accounts/{accountId}/withdrawals
```

Request:

```json
{
  "asset": "BTC",
  "network": "BITCOIN",
  "amount": "0.40000000",
  "destinationAddress": "bc1..."
}
```

Require:

```http
Idempotency-Key: <unique-value>
```

The same idempotency key must not create two withdrawals.

---

## 35. Step 16 — Withdrawal State Machine

```text
REQUESTED -> VALIDATING -> SCREENING -> POLICY_PENDING -> (DENIED | APPROVED)
APPROVED -> FUNDS_RESERVED -> SUBMITTING -> SUBMITTED -> CONFIRMING -> SETTLED
```

Failure states: `REJECTED`, `CANCELLED`, `FAILED`. Do not collapse all failure scenarios into one state.

---

## 36. Step 17 — Withdrawal Validation

Validate: account exists and is ACTIVE; asset ACTIVE; network ACTIVE; asset/network approved; amount > 0 and respects asset decimals; destination address valid for the network; sufficient available position; account not frozen; withdrawal limits; AML/sanctions/risk; policy.

Do not trust the product caller to perform these checks. Custody must enforce them.

---

## 37. Step 18 — AML Integration

```java
public interface ComplianceProvider {
    ScreeningResult screenWithdrawal(ComplianceRequest request);
    ScreeningResult screenDeposit(ComplianceRequest request);
}
```

For local dev, implement `WireMockComplianceProvider` (or a `StubComplianceProvider` returning configurable canned decisions) so the withdrawal/deposit flow is fully testable without a live AML vendor connection. Swap in the bank's real AML/blockchain intelligence adapter later — the interface doesn't change.

Do not hard-code a particular AML vendor into the custody domain. The provider supplies intelligence; the bank policy engine makes the final custody decision.

---

## 38. Step 19 — Policy Engine

Minimum rules:

```text
ACCOUNT_ACTIVE
ASSET_ALLOWED
NETWORK_ALLOWED
AMOUNT_LIMIT
CUSTOMER_LIMIT
DESTINATION_ALLOWED
AML_PASS
SANCTIONS_PASS
ACCOUNT_NOT_FROZEN
```

Return:

```json
{
  "decision": "ALLOW",
  "rules": [
    { "name": "ACCOUNT_ACTIVE", "result": "PASS" },
    { "name": "AMOUNT_LIMIT", "result": "PASS" },
    { "name": "AML", "result": "PASS" }
  ]
}
```

Persist the decision.

---

## 39. Step 20 — Funds Reservation

```text
available -= amount
locked += amount
```

Atomic with the ledger reservation entries. Use PostgreSQL locking:

```sql
SELECT * FROM position
WHERE custody_account_id = ? AND asset_id = ?
FOR UPDATE;
```

Then validate balance and update. Never submit a withdrawal to Fireblocks before funds are successfully reserved.

---

## 40. Step 21 — Fireblocks Withdrawal

```text
Transaction -> Fireblocks Adapter -> Fireblocks -> Signing/MPC (per §0.1) -> Blockchain
```

Store the provider transaction ID. Never use it as your bank transaction ID — you own `custody_transaction.id` and map `custody_transaction.provider_transaction_id`.

---

## 41. Step 22 — Transaction Status Events

Process provider updates: `SUBMITTED`, `BROADCAST`, `CONFIRMING`, `COMPLETED`, `FAILED`. Map provider-specific statuses into internal statuses. Do not expose Fireblocks-specific statuses to bank products.

---

## 42. Step 23 — Withdrawal Settlement

Settled: `locked -= amount`, create the final immutable ledger entry.

Failed after reservation: `locked -= amount`, `available += amount`, create compensating ledger entries. Never simply delete the original reservation.

---

## 43. Step 24 — Reconciliation

Run scheduled reconciliation locally via `@Scheduled` (no Azure Function needed for MVP — a simple cron-style scheduled method in the Spring app is sufficient).

MVP frequency: at least daily; more frequently for operational monitoring during development.

Compare bank client positions vs. Fireblocks (sandbox) wallet balances per asset/network.

Expected invariant: `Sum(client positions) <= Controlled custody assets`. The exact treatment of fees, operational wallets, pending transactions and omnibus balances must be explicitly configured. Do not assume the two totals must always be identical at every instant.

---

## 44. Step 25 — Reconciliation Break Handling

If `bank balance != provider balance`, create a `RECONCILIATION BREAK`. Do NOT automatically modify the client ledger to force a match. Create an operational exception requiring investigation/resolution.

---

## 45. Step 26 — API Security (local)

For MVP local dev, implement a simple stateless JWT filter (self-issued tokens, e.g. via a `/v1/dev/token` endpoint gated behind the `local` profile only) so authorization logic (§45.1) can be built and tested without a real IAM/OIDC provider. Do **not** ship the dev-token endpoint outside the `local` profile.

Authorization must still be enforced by custody account/customer entitlement regardless of how authentication is wired:

```text
Product A -> Can access CA123? -> NO --> 403 / YES --> continue
```

Do not rely solely on gateway-level authorization. Real bank IAM (OAuth2/OIDC, mTLS) integration is an Appendix B / later-phase concern — keep the authorization logic decoupled from the authentication mechanism so swapping it in later doesn't touch the entitlement checks.

---

## 46. Step 27 — Idempotency

Every mutating API must support idempotency. Minimum:

```text
POST /v1/custody-accounts
POST /v1/custody-accounts/{accountId}/deposit-addresses
POST /v1/custody-accounts/{accountId}/withdrawals
```

```http
Idempotency-Key: UUID
```

Persist the key and resulting resource. Same key + same request → return original result. Same key + different request → `409 CONFLICT`.

---

## 47. Step 28 — Concurrency

Never do `position.getAvailable(); position.setAvailable(...); repository.save();` without concurrency protection.

Use PostgreSQL row locks (`SELECT ... FOR UPDATE`) and/or optimistic versioning (`position.version`). Test with concurrent withdrawal requests locally (§71) — Testcontainers makes this fully reproducible without any cloud dependency.

---

## 48. Step 29 — Outbox Processing

```text
DB transaction: 1. Update position  2. Insert ledger entry  3. Update transaction  4. Insert outbox event
```

Then the local poller (§31) or emulator picks it up. Events may be delivered more than once — consumers must be idempotent regardless of which `EventPublisher` implementation is active.

---

## 49. Step 30 — Observability (local)

Every request/event must have a `correlation_id`. Locally, Actuator + structured console logging (JSON via `logstash-logback-encoder` or similar) is sufficient — ship to a real log aggregator only when you get to Appendix B.

Audit chain: API request → custody transaction → policy decision → AML screening → fund reservation → Fireblocks provider transaction → blockchain transaction hash → settlement → reconciliation.

Never log private keys, authentication secrets, access tokens, or unnecessary personal information.

---

## 50. Step 31 — Error Handling

```text
400 INVALID_REQUEST          409 IDEMPOTENCY_CONFLICT     422 POLICY_REJECTED
401 UNAUTHENTICATED          409 INSUFFICIENT_FUNDS        422 AML_REJECTED
403 NOT_AUTHORIZED           409 ACCOUNT_FROZEN             422 UNSUPPORTED_ASSET
404 NOT_FOUND                                                422 INVALID_ADDRESS
500 INTERNAL_ERROR           503 PROVIDER_UNAVAILABLE
```

Do not leak provider-specific errors to consumers.

---

## 51. Step 32 — Health Checks

```text
/actuator/health
/actuator/info
/actuator/metrics
```

Local health checks should cover: PostgreSQL, Fireblocks connectivity (sandbox or WireMock), and — if Option B is active — the Service Bus emulator. Do not expose secrets or sensitive dependency details.

---

## 52. Step 33 — Testing Strategy

### Unit tests
Ledger calculations, position reservation, policy rules, transaction state transitions, idempotency, asset/network validation.

### Integration tests
Testcontainers **PostgreSQL 16**. Test: ledger transaction, concurrent withdrawal, outbox, Liquibase changelog application, reconciliation.

### Provider tests
WireMock. Test: Fireblocks success, timeout, 4xx, 5xx, duplicate event, malformed event, transaction status updates.

### End-to-end (fully local, no Azure/live Fireblocks required)
```text
Create account -> create address -> simulate deposit (fixture provider_event or WireMock)
  -> credit position -> request withdrawal -> AML (stub) -> policy -> reserve
  -> Fireblocks mock -> settlement -> reconcile
```

---

## 53. Step 34 — Mandatory Security Tests

Unauthorized account access, cross-customer access, replayed idempotency key, duplicate webhook, forged webhook, malformed provider payload, negative amounts, excessive precision, unsupported network, unsupported asset, frozen account withdrawal, concurrent withdrawals, duplicate settlement, provider timeout, provider transaction mismatch.

---

## 54. Step 35 — MVP API Contract

```http
POST /v1/custody-accounts
GET  /v1/custody-accounts/{accountId}
GET  /v1/custody-accounts/{accountId}/positions
GET  /v1/custody-accounts/{accountId}/transactions
POST /v1/custody-accounts/{accountId}/deposit-addresses
GET  /v1/custody-accounts/{accountId}/deposit-addresses
POST /v1/custody-accounts/{accountId}/withdrawals
GET  /v1/withdrawals/{transactionId}
GET  /v1/deposits/{transactionId}
GET  /v1/assets
GET  /v1/assets/{asset}/networks
```

Internal endpoint (never expose through a public gateway, later or now):

```http
POST /v1/internal/providers/fireblocks/events
```

---

## 55. Step 36 — MVP Events

```text
CustodyAccountCreated, DepositDetected, DepositConfirmed, DepositPosted,
WithdrawalRequested, WithdrawalApproved, WithdrawalRejected, FundsReserved,
WithdrawalSubmitted, WithdrawalConfirmed, WithdrawalFailed, PositionChanged,
ReconciliationMatched, ReconciliationBreakDetected
```

```json
{
  "eventId": "UUID",
  "eventType": "WithdrawalConfirmed",
  "aggregateId": "transaction-id",
  "occurredAt": "timestamp",
  "correlationId": "correlation-id",
  "version": 1
}
```

Never put secrets into events.

---

## 56. Step 37 — MVP Operational Controls

Transaction search, transaction status, account status, position lookup, reconciliation status, failed transaction visibility, provider outage visibility, webhook/event failure visibility, dead-letter/failed-outbox visibility. A full ops UI is out of scope — expose via secure APIs/logging locally (Actuator + `outbox_event`/`provider_event` query endpoints are enough for MVP).

---

## 57. Step 38 — Freeze Controls

`custody_account.status = FROZEN` → withdrawals rejected, no new outbound transactions, existing transactions follow defined incident policy, deposits only if explicitly permitted by policy. Do not hard-code the treatment of in-flight transactions.

---

## 58. Step 39 — Asset/Network Kill Switch

Suspend an asset/network (e.g. `USDC/Ethereum = SUSPENDED`) → new deposit addresses/withdrawals/new transactions blocked; existing positions remain visible. Important operational safety control, and fully testable locally by flipping the `asset_network.status` row.

---

## 59. Step 40 — Fireblocks Isolation

All Fireblocks code lives under `com.bank.custody.fireblocks`. Core domain must not import `com.fireblocks.*` outside the adapter layer. Mandatory for vendor portability, and it's also what makes local WireMock-based testing (§27) drop-in simple.

---

## 60. Step 41 — Configuration Model

Supported assets/networks/combinations, withdrawal limits, deposit confirmation policy, account status policy, provider configuration, AML thresholds, policy rules. Do not hard-code in Java. Database-backed configuration is acceptable for MVP if audited (§23).

---

## 61. Step 42 — Blockchain Confirmation

`Submitted != Settled`. Settlement requires the configured confirmation/finality condition, configurable per network. For local testing against Fireblocks sandbox/testnets, confirmation counts can be set low (e.g. 1) to keep iteration fast — document this clearly as a **non-production** setting.

---

## 62. Step 43 — Fees

Keep minimal for MVP. Support `network/provider fee` if the sandbox config requires it. Preserve enough transaction data to distinguish: requested amount, executed amount, network fee, provider fee, client debit — before going to production.

---

## 63. Step 44 — Decimal Handling

Every asset has its own decimals (`BTC = 8`, `ETH = 18`). Reject amounts with unsupported precision. Use `BigDecimal`, normalized to the asset's configured decimal precision. Never use floating-point arithmetic.

---

## 64. Step 45 — API Versioning

`/v1/` from day one, consistently, including the internal webhook path. Do not expose database entities directly through REST:

```text
Controller -> DTO -> Application Service -> Domain -> Repository
```

---

## 65. Step 46 — Transaction Service

```java
TransactionOrchestrator
```

Responsibilities: validate, create transaction, invoke compliance, invoke policy, reserve funds, call provider, process provider updates, finalize ledger. Must NOT contain Fireblocks-specific implementation details.

---

## 66. Step 47 — Ledger Service

```java
LedgerService
```

Responsibilities: `credit()`, `reserve()`, `release()`, `settleWithdrawal()`, `reverse()`. Every operation must: validate → lock position → create immutable ledger entry → update position → create outbox event → commit atomically.

---

## 67. Step 48 — Position Invariants

`available >= 0`, `locked >= 0`, `pending >= 0` (enforced via DB `CHECK`, §14), and `total = available + locked + pending`. Never permit an API caller to directly set a position.

---

## 68. Step 49 — MVP Reconciliation Invariant

`Client position total + bank-controlled operational position = Fireblocks-controlled balance`, scoped to the same asset/network/wallet population/settlement status. Define exclusions explicitly. Do not compare incomparable balances.

---

## 69. Step 50 — Build Order

**Pre-flight:** §0.1 (key-custody model) should be an approved ADR before Sprint 3 — it can run in parallel with Sprint 1–2 since local build/test doesn't depend on it.

### Sprint 1 — Local foundation
```text
1. Spring Boot project (Java 25, Spring Boot 4.1.x)
2. docker-compose.yml: PostgreSQL 16 + app
3. Liquibase changelog structure + first changesets
4. Domain model
5. Account API
6. Asset/network API
```

### Sprint 2
```text
7. Position
8. Ledger
9. Transaction
10. Idempotency
11. Audit
12. Outbox (with local logging EventPublisher, §5.4 Option A)
```

### Sprint 3
```text
13. Key-custody model ADR sign-off (§0.1)
14. Fireblocks adapter (against WireMock, then sandbox)
15. Fireblocks authentication (sandbox keys via .env)
16. Wallet mapping
17. Deposit address
18. Fireblocks webhook (local tunnel or fixture-driven)
```

### Sprint 4
```text
19. Deposit lifecycle
20. AML integration (stub/WireMock)
21. Deposit ledger posting
22. Position update
```

### Sprint 5
```text
23. Withdrawal API
24. Policy
25. Funds reservation
26. Fireblocks submission (sandbox)
27. Provider status handling
28. Settlement
```

### Sprint 6
```text
29. Reconciliation
30. Exception handling
31. Monitoring (Actuator + logs)
32. Security tests
33. End-to-end tests (fully local via Testcontainers + WireMock)
```

### Sprint 7 — hardening, still local
```text
34. API hardening
35. Local dev-JWT auth cleanup / entitlement checks finalized
36. Operational runbook (local version)
37. MVP acceptance test (§71)
```

Azure migration (Appendix B) becomes Sprint 8+, once the MVP passes Sprint 7 locally.

---

## 70. Definition of Done (local MVP)

### Account
- [ ] Create custody account
- [ ] Activate account
- [ ] Freeze account
- [ ] Retrieve account

### Asset
- [ ] Asset allow-list
- [ ] Network allow-list
- [ ] Asset/network allow-list

### Wallet
- [ ] Key-custody model ADR approved (§0.1)
- [ ] Fireblocks (sandbox) wallet mapping
- [ ] Deposit address
- [ ] Unknown address handling

### Deposit
- [ ] Provider event received (sandbox or fixture)
- [ ] Event deduplicated
- [ ] Account identified
- [ ] AML/risk executed (stub or sandbox)
- [ ] Confirmation tracked
- [ ] Ledger credited
- [ ] Position updated
- [ ] Audit created

### Withdrawal
- [ ] API request
- [ ] Idempotency
- [ ] Account validation
- [ ] Asset/network validation
- [ ] Balance validation
- [ ] AML/risk
- [ ] Policy
- [ ] Funds reservation
- [ ] Fireblocks submission (sandbox)
- [ ] Provider ID stored
- [ ] Confirmation
- [ ] Ledger settlement
- [ ] Failure reversal
- [ ] Audit

### Reconciliation
- [ ] Provider (sandbox) balances imported
- [ ] Client positions calculated
- [ ] Comparison
- [ ] Break detection
- [ ] Break alert (log/console acceptable for MVP)
- [ ] No automatic balance manipulation

### Security
- [ ] Local dev-JWT auth
- [ ] Authorization (entitlement checks)
- [ ] Secret management via `.env` (never committed)
- [ ] Provider authentication (Fireblocks sandbox)
- [ ] Webhook validation
- [ ] Audit
- [ ] No secrets in logs

### Reliability
- [ ] Idempotency
- [ ] Outbox (local poller, §5.4 Option A minimum)
- [ ] Retry
- [ ] Dead-letter / failed-event visibility
- [ ] Transaction locking
- [ ] Provider timeout handling

### Testing
- [ ] Unit tests
- [ ] Integration tests (Testcontainers, Postgres 16)
- [ ] Provider mock tests (WireMock)
- [ ] Concurrent withdrawal test
- [ ] Duplicate event test
- [ ] E2E happy path (fully local)
- [ ] E2E failure paths

---

## 71. Required E2E Acceptance Test (runs entirely locally)

```text
1. Create customer C001
2. Create custody account CA001
3. Activate CA001
4. Configure BTC/Bitcoin
5. Create/retrieve Fireblocks wallet mapping (sandbox or WireMock)
6. Generate BTC deposit address
7. Simulate Fireblocks deposit event (fixture payload or WireMock stub)
8. Verify provider event stored
9. Verify deposit transaction created
10. Run AML/risk (stub)
11. Mark deposit confirmed
12. Post 1 BTC to ledger
13. Verify CA001 position = 1 BTC
14. Request 0.4 BTC withdrawal
15. Run AML (stub)
16. Run policy
17. Reserve 0.4 BTC
18. Verify available = 0.6 BTC
19. Submit Fireblocks transaction (sandbox or WireMock)
20. Store provider transaction ID
21. Simulate confirmation
22. Settle withdrawal
23. Verify final position = 0.6 BTC
24. Run reconciliation
25. Verify MATCH
```

Also test — two simultaneous withdrawals of 0.7 BTC against a 1 BTC balance. Expected: one succeeds, one fails, total reservation never exceeds 1 BTC. This test is exactly why Testcontainers (real Postgres locking semantics) matters more than an in-memory DB for this suite.

---

## 72. AI Agent Rules

**Rule 1** — Do not invent Fireblocks API endpoints or request/response fields. Use the Fireblocks developer documentation.
**Rule 2** — Do not put Fireblocks-specific types in the core domain.
**Rule 3** — Do not use floating-point types for asset amounts.
**Rule 4** — Do not mutate ledger entries.
**Rule 5** — Do not directly modify positions from controllers.
**Rule 6** — Do not allow products to call Fireblocks.
**Rule 7** — Do not bypass policy/AML for withdrawals.
**Rule 8** — Do not automatically fix reconciliation breaks.
**Rule 9** — Do not silently process unknown blockchain addresses.
**Rule 10** — Do not log secrets/private keys/tokens.
**Rule 11** — Do not create a microservice for every domain object.
**Rule 12** — Do not add out-of-scope functionality to the MVP.
**Rule 13** — Do not implement Fireblocks signing/vault topology before the key-custody model (§0.1) is confirmed.
**Rule 14** — Do not add Azure-specific code/config to the local MVP path. Keep all Azure concerns in Appendix B until explicitly asked to build them.
**Rule 15** — Do not write raw SQL migrations outside Liquibase changesets, and do not hand-edit the schema of a running local database.

---

## 73. Production Gate — Regulatory/Operational Review

Before production, the MVP must be reviewed by the bank's Compliance, Legal, Information Security, Operational Risk, DORA/ICT Risk, Internal Audit, Data Protection, Architecture, Business Continuity, and Custody Operations functions. This README is not a legal opinion or regulatory approval.

MiCA custody requirements and applicable RTS/technical standards must be mapped by the bank's regulatory/legal teams to concrete controls before production. `compliance_reference` (§11), `audit_event` (§23), and `policy_decision`/`compliance_screening` (§18–19) are the primary evidence trail.

For ICT outsourcing, perform the required third-party risk assessment of Fireblocks (resilience, incident handling, audit/access, data handling, exit/termination, concentration risk) before production — the key-custody model (§0.1) is a primary input, since it determines actual dependency on Fireblocks for signing availability.

---

## 74. What NOT to build after this MVP

tokenisation, trading, staking, exchange, lending, DeFi, NFT custody, multi-custodian routing, advanced fee engine, Travel Rule platform, blockchain indexer, custom MPC, custom HSM, customer UI, or any Azure automation beyond what Appendix B calls for. Those are separate initiatives.

> **The MVP goal:** a bank-owned custody control plane, buildable and testable entirely on a local machine with Docker, that can securely create custody accounts, maintain client positions, accept deposits, execute withdrawals through Fireblocks, enforce AML/policy controls, maintain an auditable transaction lifecycle, and reconcile bank positions against custody infrastructure.

---

## 75. Local Docker Compose Stack

`docker-compose.yml` (baseline — Postgres + app, always used):

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:16
    container_name: custody-postgres
    environment:
      POSTGRES_DB: custody
      POSTGRES_USER: custody_app
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-localdevpassword}
    ports:
      - "5432:5432"
    volumes:
      - custody_pg_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U custody_app -d custody"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build: .
    container_name: custody-app
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/custody
      SPRING_DATASOURCE_USERNAME: custody_app
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-localdevpassword}
      FIREBLOCKS_BASE_PATH: ${FIREBLOCKS_BASE_PATH:-http://wiremock:8080}
      FIREBLOCKS_API_KEY: ${FIREBLOCKS_API_KEY:-dummy}
      MESSAGING_MODE: ${MESSAGING_MODE:-local}
    ports:
      - "8080:8080"

  wiremock:
    image: wiremock/wiremock:latest
    container_name: custody-wiremock
    ports:
      - "9561:8080"
    volumes:
      - ./wiremock/mappings:/home/wiremock/mappings
      - ./wiremock/__files:/home/wiremock/__files

  pgadmin:
    image: dpage/pgadmin4
    container_name: custody-pgadmin
    profiles: ["tools"]
    environment:
      PGADMIN_DEFAULT_EMAIL: [email protected]
      PGADMIN_DEFAULT_PASSWORD: admin
    ports:
      - "5050:80"
    depends_on:
      - postgres

volumes:
  custody_pg_data:
```

Optional messaging overlay `docker-compose.messaging.yml` (§5.4 Option B — only start this if you want emulator-backed Service Bus locally):

```yaml
version: "3.9"

services:
  sql-edge:
    image: mcr.microsoft.com/azure-sql-edge:latest
    container_name: custody-sql-edge
    environment:
      ACCEPT_EULA: "Y"
      MSSQL_SA_PASSWORD: ${SQL_EDGE_PASSWORD:-LocalDevP@ss1}
    ports:
      - "1433:1433"

  servicebus-emulator:
    image: mcr.microsoft.com/azure-messaging/servicebus-emulator:latest
    container_name: custody-servicebus-emulator
    depends_on:
      - sql-edge
    environment:
      SQL_SERVER: sql-edge
      MSSQL_SA_PASSWORD: ${SQL_EDGE_PASSWORD:-LocalDevP@ss1}
      ACCEPT_EULA: "Y"
    ports:
      - "5672:5672"   # AMQP
      - "5300:5300"   # management
    volumes:
      - ./config/servicebus-emulator/Config.json:/ServiceBus_Emulator/ConfigFiles/Config.json
```

Run baseline stack:

```bash
docker compose up --build
```

Run with the optional messaging emulator:

```bash
docker compose -f docker-compose.yml -f docker-compose.messaging.yml up --build
```

Then set `MESSAGING_MODE=servicebus-emulator` and `SERVICEBUS_CONNECTION_STRING=Endpoint=sb://localhost:5672;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;` in `.env`.

Run just Postgres for `mvn spring-boot:run` outside Docker:

```bash
docker compose up postgres -d
mvn spring-boot:run
```

---

## 76. Local Build & Run Cheatsheet

```bash
# 1. Copy env template and fill in Fireblocks sandbox creds
cp .env.example .env

# 2. Build (skip tests during iterative dev)
mvn -U -DskipTests clean package

# 3. Bring up local infra + app
docker compose up --build

# 4. Apply/verify Liquibase changelog manually (optional — Spring Boot runs it on startup)
mvn liquibase:update

# 5. Roll back the last changeset if you need to iterate on schema
mvn liquibase:rollback -Dliquibase.rollbackCount=1
```

**API examples:**

```bash
# Create account
curl -X POST http://localhost:8080/v1/custody-accounts \
    -H "Content-Type: application/json" \
    -d '{"customerId":"C001"}'

# Create deposit address (idempotent)
curl -X POST http://localhost:8080/v1/custody-accounts/{accountId}/deposit-addresses \
    -H "Content-Type: application/json" \
    -d '{"asset":"BTC","network":"BITCOIN"}'

# Request withdrawal (Idempotency-Key required)
curl -X POST http://localhost:8080/v1/custody-accounts/{accountId}/withdrawals \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"asset":"BTC","network":"BITCOIN","amount":"0.4","destinationAddress":"bc1..."}'
```

**Local production-readiness checklist (before touching Azure):**

- [ ] Key-custody model ADR approved (§0.1)
- [ ] Full E2E test (§71) green via Testcontainers
- [ ] Concurrency test (two simultaneous withdrawals) green
- [ ] Security test suite (§53) green
- [ ] Liquibase changelog fully reproducible from empty DB (`docker compose down -v && docker compose up --build`)
- [ ] No secrets committed anywhere in the repo (`git log -p | grep -i` sanity pass, or a secret-scanning pre-commit hook)
- [ ] Reconciliation break path manually exercised at least once

---

## Appendix A — Glossary

| Term | Meaning |
|---|---|
| **MPC/TSS** | Multi-Party Computation / Threshold Signature Scheme — key material is split into shares across parties; no single party ever holds the full private key. |
| **HSM** | Hardware Security Module — dedicated hardware for key storage/signing. |
| **Vault/Workspace** | Fireblocks' unit of wallet/key organisation for a client or asset. |
| **Co-signer** | A party (or system) that must approve/sign a transaction as part of a multi-party or MPC scheme. |
| **CASP** | Crypto-Asset Service Provider, the MiCA authorisation category this custody platform falls under. |
| **Omnibus balance** | A pooled on-chain balance representing multiple clients' positions, reconciled against the sum of individual client ledger positions. |
| **Idempotency key** | A caller-supplied unique token ensuring a repeated request does not create duplicate side effects. |
| **Outbox pattern** | Writing an event to a DB table in the same transaction as the state change, then publishing it asynchronously — avoids dual-write inconsistency between DB and message bus. |
| **Liquibase changeset** | A single, uniquely-identified, versioned unit of schema change, tracked in `DATABASECHANGELOG` so it's applied exactly once. |

---

## Appendix B — Later: Moving to Azure

This section is intentionally short — it's a pointer, not a build guide, since Azure work is deferred until the local MVP (§70) is done.

| Local (MVP) | Azure equivalent (later) | Notes |
|---|---|---|
| Docker Compose `postgres:16` | Azure Database for PostgreSQL Flexible Server (v16) | Liquibase changelogs are identical — same schema, same tool, different `SPRING_DATASOURCE_URL`. |
| `.env` file | Azure Key Vault + Managed Identity | Swap the config source, not the config keys. |
| Local `EventPublisher` (Option A) or Service Bus emulator (Option B) | Real Azure Service Bus | If you built against the emulator's SDK/connection-string pattern, this is a connection-string change only. |
| WireMock stub for Fireblocks/AML | Same sandbox/production endpoints, same adapter code | Only `FIREBLOCKS_BASE_PATH` / credentials change — the `CustodyExecutionProvider`/`ComplianceProvider` interfaces don't. |
| Local dev-JWT | Bank IAM via OAuth2/OIDC, mTLS | Only the authentication filter changes; entitlement/authorization logic (§45) stays the same. |
| Console/file logs | Application Insights, Log Analytics | Actuator + structured logging already produces what's needed; just add the shipping. |
| No API gateway | Azure API Management | Public API contract (§54) doesn't change — only where it's fronted. |
| No IaC | Terraform under `infrastructure/terraform` | Placeholder directory already reserved in §6. |

**When you're ready to move:** the production gate in §73 (Compliance/Legal/InfoSec/Risk/Audit/DPO/Architecture/BCM/Custody Ops review) still applies before anything in this table touches production data — moving from local Docker to Azure does not substitute for that review, and the ICT-outsourcing risk assessment for Fireblocks should already be underway in parallel with local development, not started only once Azure work begins.
