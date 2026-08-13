# Digital Asset Custody MVP — AI Agent Build Guide

## 0. Purpose

Build a **bank-owned digital asset custody MVP** for an EU/Netherlands bank using:

- Java 25
- Spring Boot 4.x
- PostgreSQL
- liquid base
- Fireblocks

The MVP must provide a bank-owned custody control plane while using Fireblocks for wallet/key/signing/blockchain execution.

### Core principle

> **The bank owns the client/account/position/ledger/transaction/policy/audit model. Fireblocks is an execution and wallet infrastructure provider.**

Do not make Fireblocks the customer position ledger or expose Fireblocks' domain model to consuming bank products.

---

# 1. MVP Scope

## 1.1 In scope

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
18. Authentication/authorization through existing bank IAM
19. Idempotency
20. Operational monitoring and exception handling

## 1.2 Initial assets

Use only assets explicitly approved by the bank.

For the initial technical MVP, configure a small allow-list such as:

- BTC / Bitcoin
- ETH / Ethereum
- USDC / approved network

Do not assume that an asset is approved merely because Fireblocks supports it.

## 1.3 Out of scope for MVP

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

These can be future phases.

---

# 2. Target Architecture

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
                  | Spring Boot          |
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
                  | PostgreSQL            |
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
                  | Blockchain execution   |
                  +-----------+-----------+
                              |
                              v
                         BLOCKCHAINS
```

## 2.1 External systems

```text
Bank IAM
   |
   v
Digital Asset API

AML / Sanctions / Risk
   |
   v
Compliance Adapter

Fireblocks
   |
   +--> Wallets
   +--> MPC/signing
   +--> Blockchain connectivity
   +--> Transaction events

Azure
   |
   +--> PostgreSQL
   +--> Key Vault
   +--> Service Bus
   +--> API Management
   +--> Monitoring
```

---

# 3. Architectural Boundaries

## 3.1 Bank-owned

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

## 3.2 Fireblocks-owned

For MVP, Fireblocks provides:

- Wallet infrastructure
- Vault/wallet infrastructure
- Cryptographic signing/MPC
- Blockchain transaction execution
- Blockchain connectivity
- Provider transaction status
- Provider wallet balances

The bank application must maintain its own mapping to Fireblocks.

## 3.3 Never expose Fireblocks directly

Bank products must NOT call Fireblocks directly.

Correct:

```text
Product
  -> Bank Digital Asset API
  -> Custody Orchestrator
  -> Fireblocks Adapter
  -> Fireblocks
```

Incorrect:

```text
Product
  -> Fireblocks API
```

---

# 4. Recommended MVP Deployment Shape

Do NOT start with many microservices.

Use one Spring Boot application with strict modules/packages.

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
  reconciliation/
  audit/
  api/
```

Split into independent services later only if required.

This reduces MVP complexity and distributed-transaction problems.

---

# 5. Technology Requirements

## Application

- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Validation
- Spring Data JPA
- Spring Security
- PostgreSQL driver
- Flyway
- Actuator
- Jackson

## Testing

- JUnit 5
- Mockito
- Testcontainers
- WireMock or equivalent for Fireblocks API simulation

## Azure

Use existing bank standards where available.

Preferred MVP infrastructure:

- Azure Container Apps or AKS
- Azure Database for PostgreSQL
- Azure Service Bus
- Azure Key Vault
- Azure API Management
- Azure Monitor
- Application Insights
- Log Analytics
- Managed Identity

Do not create duplicate bank infrastructure if existing services are already approved.

---

# 6. Repository Structure

Create:

```text
digital-asset-custody/
├── README.md
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .gitignore
│
├── src/
│   ├── main/
│   │   ├── java/com/bank/custody/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │
│   └── test/
│
└── infrastructure/
    ├── terraform/
    └── deployment/
```

---

# 7. Step 1 — Create Spring Boot Application

Create a Spring Boot application using Java 21.

Required dependencies:

```xml
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-actuator
postgresql
flyway-core
lombok (optional)
```

Use Maven unless the bank standard is Gradle.

---

# 8. Step 2 — Configuration

Use environment variables/secrets.

Never commit:

- Fireblocks private keys
- Fireblocks API credentials
- Azure credentials
- database passwords
- JWT signing secrets

Use:

```text
Azure Managed Identity
       |
       v
Azure Key Vault
       |
       v
Spring Boot
```

Local development may use environment variables.

---

# 9. Step 3 — PostgreSQL and Flyway

Create a PostgreSQL database.

Use Flyway for ALL schema changes.

Never manually modify production schema.

Migration naming:

```text
V1__create_custody_account.sql
V2__create_asset.sql
V3__create_network.sql
V4__create_position.sql
V5__create_ledger.sql
V6__create_transaction.sql
V7__create_wallet_mapping.sql
V8__create_compliance.sql
V9__create_policy.sql
V10__create_provider_event.sql
V11__create_outbox.sql
V12__create_reconciliation.sql
V13__create_audit.sql
```

---

# 10. Step 4 — Database Model

## 10.1 custody_account

```sql
CREATE TABLE custody_account (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

Statuses:

```text
PENDING
ACTIVE
FROZEN
CLOSED
```

---

# 11. asset

```sql
CREATE TABLE asset (
    id UUID PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    name VARCHAR(100),
    decimals INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
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

The application must reject unsupported assets.

---

# 12. network

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

Example:

```text
USDC + Ethereum
USDC + Solana
```

are different supported asset/network combinations.

---

# 13. asset_network

Create an explicit allow-list:

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

# 14. position

This is the client position system of record.

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

    FOREIGN KEY(custody_account_id)
        REFERENCES custody_account(id),

    FOREIGN KEY(asset_id)
        REFERENCES asset(id)
);
```

Use Java `BigDecimal`.

Never use `double` or `float`.

---

# 15. Ledger

The ledger is immutable.

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

    FOREIGN KEY(custody_account_id)
        REFERENCES custody_account(id),

    FOREIGN KEY(asset_id)
        REFERENCES asset(id)
);
```

Ledger entries must never be updated or deleted.

Corrections are new compensating entries.

---

# 16. Custody Transaction

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

    FOREIGN KEY(custody_account_id)
        REFERENCES custody_account(id),

    FOREIGN KEY(asset_id)
        REFERENCES asset(id),

    FOREIGN KEY(network_id)
        REFERENCES network(id)
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

# 17. Wallet Mapping

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

    FOREIGN KEY(custody_account_id)
        REFERENCES custody_account(id),

    FOREIGN KEY(asset_id)
        REFERENCES asset(id),

    FOREIGN KEY(network_id)
        REFERENCES network(id)
);
```

Provider-specific identifiers are isolated here.

---

# 18. Compliance Screening

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

    FOREIGN KEY(transaction_id)
        REFERENCES custody_transaction(id)
);
```

Do not store unnecessary sensitive personal information.

---

# 19. Policy Decision

```sql
CREATE TABLE policy_decision (
    id UUID PRIMARY KEY,

    transaction_id UUID NOT NULL,

    decision VARCHAR(30) NOT NULL,

    rules JSONB NOT NULL,

    decided_at TIMESTAMPTZ NOT NULL,

    FOREIGN KEY(transaction_id)
        REFERENCES custody_transaction(id)
);
```

Decision:

```text
ALLOW
DENY
REVIEW
```

---

# 20. Provider Event

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

# 21. Outbox

Use a transactional outbox.

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

The database transaction must update the domain state AND create the outbox event atomically.

---

# 22. Reconciliation

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

    FOREIGN KEY(asset_id)
        REFERENCES asset(id),

    FOREIGN KEY(network_id)
        REFERENCES network(id)
);
```

Statuses:

```text
MATCH
BREAK
ERROR
```

---

# 23. Audit

Create an append-only audit table.

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

Audit:

- account creation
- address creation
- deposit
- withdrawal
- policy decision
- AML decision
- funds reservation
- Fireblocks submission
- transaction status changes
- reconciliation
- freeze/unfreeze

Never log private keys or secrets.

---

# 24. Step 5 — Domain Enums

Create enums.

```text
CustodyAccountStatus
AssetStatus
NetworkStatus
TransactionType
TransactionStatus
PositionStatus
PolicyDecision
ComplianceDecision
WalletStatus
ReconciliationStatus
AuditAction
```

Do not scatter raw strings throughout the application.

---

# 25. Step 6 — Custody Account API

Implement:

```http
POST /v1/custody-accounts
GET  /v1/custody-accounts/{id}
```

Create account:

```json
{
  "customerId": "C12345"
}
```

Response:

```json
{
  "custodyAccountId": "UUID",
  "customerId": "C12345",
  "status": "ACTIVE"
}
```

The customer identity must come from the bank's identity/customer system where possible.

Do not build a second customer master.

---

# 26. Step 7 — Position API

Implement:

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

The API must calculate total consistently.

Do not allow clients to directly modify positions.

---

# 27. Step 8 — Fireblocks Adapter

Create an interface:

```java
public interface CustodyExecutionProvider {

    ProviderWallet getWallet(
        Asset asset,
        Network network,
        UUID custodyAccountId
    );

    ProviderAddress getDepositAddress(
        WalletReference wallet,
        Asset asset,
        Network network
    );

    ProviderTransaction submitWithdrawal(
        WithdrawalExecutionRequest request
    );

    ProviderTransaction getTransaction(
        String providerTransactionId
    );

    List<ProviderBalance> getBalances();
}
```

Then implement:

```java
@Component
public class FireblocksExecutionProvider
        implements CustodyExecutionProvider {
}
```

The rest of the application must depend only on the interface.

---

# 28. Step 9 — Fireblocks API Client

Create:

```text
FireblocksClient
FireblocksRequestSigner
FireblocksMapper
FireblocksExecutionProvider
FireblocksWebhookController
FireblocksWebhookProcessor
```

Keep all Fireblocks-specific DTOs inside the `fireblocks` module.

Do not use Fireblocks DTOs in the core domain.

Use the Fireblocks tenant/developer documentation as the authoritative source for the exact API authentication, request signing, endpoint names, asset identifiers, transaction parameters and webhook formats.

Do not hard-code undocumented Fireblocks API assumptions.

---

# 29. Step 10 — Deposit Address

API:

```http
POST /v1/custody-accounts/{accountId}/deposit-addresses
```

Request:

```json
{
  "asset": "BTC",
  "network": "BITCOIN"
}
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

# 30. Step 11 — Fireblocks Webhook

Create:

```http
POST /internal/providers/fireblocks/events
```

The webhook endpoint must:

1. Validate authenticity according to Fireblocks documentation
2. Validate payload
3. Generate correlation ID
4. Store `provider_event`
5. Reject duplicate provider event IDs safely
6. Publish an internal event
7. Return quickly

Do NOT execute a long transaction workflow synchronously in the HTTP webhook.

---

# 31. Step 12 — Azure Service Bus

Use:

```text
fireblocks-events
custody-events
```

At minimum.

Flow:

```text
Fireblocks
   |
   v
Webhook
   |
   v
provider_event
   |
   v
Azure Service Bus
   |
   v
Event Processor
```

Configure retries and dead-letter queues.

Never silently discard failed events.

---

# 32. Step 13 — Deposit Lifecycle

Implement:

```text
DEPOSIT_DETECTED
        |
        v
SCREENING
        |
        v
PENDING_CONFIRMATION
        |
        v
CONFIRMED
        |
        v
POSTED
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

Unknown addresses must go to an exception state.

Never automatically credit an unidentified client account.

---

# 33. Step 14 — Ledger Deposit Posting

Example:

Client receives 1 BTC.

Create an immutable ledger entry representing the client credit.

Update position transactionally:

```text
available += 1 BTC
```

The ledger entry and position update must be in the same PostgreSQL transaction.

Use an idempotency key derived from the unique provider transaction/event identity.

---

# 34. Step 15 — Withdrawal API

Implement:

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

# 35. Step 16 — Withdrawal State Machine

Implement:

```text
REQUESTED
   |
   v
VALIDATING
   |
   v
SCREENING
   |
   v
POLICY_PENDING
   |
   +--> DENIED
   |
   v
APPROVED
   |
   v
FUNDS_RESERVED
   |
   v
SUBMITTING
   |
   v
SUBMITTED
   |
   v
CONFIRMING
   |
   v
SETTLED
```

Failure states:

```text
REJECTED
CANCELLED
FAILED
```

Do not collapse all failure scenarios into one state.

---

# 36. Step 17 — Withdrawal Validation

Validate:

1. Account exists
2. Account is ACTIVE
3. Asset is ACTIVE
4. Network is ACTIVE
5. Asset/network combination is approved
6. Amount > 0
7. Amount respects asset decimals
8. Destination address is valid for the selected network
9. Client has sufficient available position
10. Account is not frozen
11. Withdrawal limits
12. AML/sanctions/risk
13. Policy

Do not trust the product caller to perform these checks.

Custody must enforce them.

---

# 37. Step 18 — AML Integration

Create an interface:

```java
public interface ComplianceProvider {

    ScreeningResult screenWithdrawal(
        ComplianceRequest request
    );

    ScreeningResult screenDeposit(
        ComplianceRequest request
    );
}
```

Implement an adapter for the bank's existing AML/blockchain intelligence provider.

Do not hard-code a particular AML vendor into the custody domain.

The provider supplies intelligence.

The bank policy engine makes the final custody decision.

---

# 38. Step 19 — Policy Engine

Implement an MVP policy engine.

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
    {
      "name": "ACCOUNT_ACTIVE",
      "result": "PASS"
    },
    {
      "name": "AMOUNT_LIMIT",
      "result": "PASS"
    },
    {
      "name": "AML",
      "result": "PASS"
    }
  ]
}
```

Persist the decision.

---

# 39. Step 20 — Funds Reservation

Before Fireblocks submission:

```text
available -= amount
locked += amount
```

Do this atomically with the ledger reservation entries.

Use PostgreSQL locking:

```sql
SELECT *
FROM position
WHERE custody_account_id = ?
  AND asset_id = ?
FOR UPDATE;
```

Then validate balance and update.

Never submit a withdrawal to Fireblocks before funds are successfully reserved.

---

# 40. Step 21 — Fireblocks Withdrawal

After successful reservation:

```text
Transaction
    |
    v
Fireblocks Adapter
    |
    v
Fireblocks
    |
    v
Signing/MPC
    |
    v
Blockchain
```

Store the provider transaction ID.

Never use the provider transaction ID as your bank transaction ID.

You own:

```text
custody_transaction.id
```

and map:

```text
custody_transaction.provider_transaction_id
```

---

# 41. Step 22 — Transaction Status Events

Process provider updates:

```text
SUBMITTED
BROADCAST
CONFIRMING
COMPLETED
FAILED
```

Map provider-specific statuses into your internal statuses.

Do not expose Fireblocks-specific statuses to bank products.

---

# 42. Step 23 — Withdrawal Settlement

When successfully settled:

```text
locked -= amount
```

The total client position decreases.

Create the final immutable ledger entry.

If a transaction fails after reservation:

```text
locked -= amount
available += amount
```

and create compensating ledger entries.

Never simply delete the original reservation.

---

# 43. Step 24 — Reconciliation

Run scheduled reconciliation.

MVP frequency:

- at least daily
- preferably more frequently for operational monitoring

Compare:

```text
Bank client positions
        vs
Fireblocks wallet balances
```

For each asset/network.

Expected invariant:

```text
Sum(client positions)
<=
Controlled custody assets
```

The exact treatment of fees, operational wallets, pending transactions and omnibus balances must be explicitly configured.

Do not assume the two totals must always be identical at every instant.

---

# 44. Step 25 — Reconciliation Break Handling

If:

```text
bank balance != provider balance
```

create:

```text
RECONCILIATION BREAK
```

Do NOT automatically modify the client ledger to force a match.

Create an operational exception.

Require investigation/resolution.

---

# 45. Step 26 — API Security

All product APIs must use bank-standard authentication.

Recommended:

```text
OAuth2/OIDC
JWT
mTLS where required
```

Authorization must be enforced by custody account/customer entitlement.

Example:

```text
Product A
    |
    v
Can access CA123?
    |
    +-- NO --> 403
    |
    +-- YES --> continue
```

Do not rely solely on API gateway authorization.

---

# 46. Step 27 — Idempotency

Every mutating API must support idempotency.

Minimum:

```text
POST /withdrawals
POST /deposit-addresses
POST /custody-accounts
```

Use:

```http
Idempotency-Key: UUID
```

Persist the key and resulting resource.

Same key + same request:

```text
return original result
```

Same key + different request:

```text
409 CONFLICT
```

---

# 47. Step 28 — Concurrency

The most important concurrency control is position reservation.

Never do:

```java
position.getAvailable()
position.setAvailable(...)
repository.save()
```

without concurrency protection.

Use:

- PostgreSQL row locks
- optimistic versioning
- serializable/appropriate transaction boundaries

The selected approach must be tested with concurrent withdrawal requests.

---

# 48. Step 29 — Outbox Processing

When domain state changes:

```text
DB transaction:

1. Update position
2. Insert ledger entry
3. Update transaction
4. Insert outbox event
```

Then:

```text
Outbox processor
   |
   v
Azure Service Bus
```

Events may be delivered more than once.

Consumers must be idempotent.

---

# 49. Step 30 — Observability

Every request/event must have:

```text
correlation_id
```

For a withdrawal, the audit chain should connect:

```text
API request
   |
custody transaction
   |
policy decision
   |
AML screening
   |
fund reservation
   |
Fireblocks provider transaction
   |
blockchain transaction hash
   |
settlement
   |
reconciliation
```

Never log:

- private keys
- authentication secrets
- access tokens
- sensitive personal information unnecessarily

---

# 50. Step 31 — Error Handling

Use consistent HTTP errors.

Examples:

```text
400 INVALID_REQUEST
401 UNAUTHENTICATED
403 NOT_AUTHORIZED
404 NOT_FOUND
409 IDEMPOTENCY_CONFLICT
409 INSUFFICIENT_FUNDS
409 ACCOUNT_FROZEN
422 POLICY_REJECTED
422 AML_REJECTED
422 UNSUPPORTED_ASSET
422 INVALID_ADDRESS
500 INTERNAL_ERROR
503 PROVIDER_UNAVAILABLE
```

Do not leak provider-specific errors to consumers.

---

# 51. Step 32 — Health Checks

Expose:

```text
/actuator/health
/actuator/info
/actuator/metrics
```

Health checks should include:

- PostgreSQL
- Azure Service Bus where appropriate
- Fireblocks connectivity
- Key Vault availability where appropriate

Do not expose secrets or sensitive dependency details.

---

# 52. Step 33 — Testing Strategy

The MVP is not complete without automated tests.

## Unit tests

Test:

- ledger calculations
- position reservation
- policy rules
- transaction state transitions
- idempotency
- asset/network validation

## Integration tests

Use Testcontainers PostgreSQL.

Test:

- ledger transaction
- concurrent withdrawal
- outbox
- Flyway
- reconciliation

## Provider tests

Use WireMock or equivalent.

Test:

- Fireblocks success
- Fireblocks timeout
- Fireblocks 4xx
- Fireblocks 5xx
- duplicate event
- malformed event
- transaction status updates

## End-to-end

Test:

```text
Create account
  -> create address
  -> simulate deposit
  -> credit position
  -> request withdrawal
  -> AML
  -> policy
  -> reserve
  -> Fireblocks mock
  -> settlement
  -> reconcile
```

---

# 53. Step 34 — Mandatory Security Tests

Test:

- unauthorized account access
- cross-customer access
- replayed idempotency key
- duplicate webhook
- forged webhook
- malformed provider payload
- negative amounts
- excessive precision
- unsupported network
- unsupported asset
- frozen account withdrawal
- concurrent withdrawals
- duplicate settlement
- provider timeout
- provider transaction mismatch

---

# 54. Step 35 — MVP API Contract

Expose:

```http
POST /v1/custody-accounts

GET /v1/custody-accounts/{accountId}

GET /v1/custody-accounts/{accountId}/positions

GET /v1/custody-accounts/{accountId}/transactions

POST /v1/custody-accounts/{accountId}/deposit-addresses

GET /v1/custody-accounts/{accountId}/deposit-addresses

POST /v1/custody-accounts/{accountId}/withdrawals

GET /v1/withdrawals/{transactionId}

GET /v1/deposits/{transactionId}

GET /v1/assets

GET /v1/assets/{asset}/networks
```

Internal endpoint:

```http
POST /internal/providers/fireblocks/events
```

Do not expose internal provider endpoints through the public product API.

---

# 55. Step 36 — MVP Events

Publish:

```text
CustodyAccountCreated
DepositDetected
DepositConfirmed
DepositPosted
WithdrawalRequested
WithdrawalApproved
WithdrawalRejected
FundsReserved
WithdrawalSubmitted
WithdrawalConfirmed
WithdrawalFailed
PositionChanged
ReconciliationMatched
ReconciliationBreakDetected
```

Events should include:

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

# 56. Step 37 — MVP Operational Controls

Provide basic operational capabilities:

- transaction search
- transaction status
- account status
- position lookup
- reconciliation status
- failed transaction visibility
- provider outage visibility
- webhook/event failure visibility
- dead-letter visibility

A full operations UI is out of scope.

These can initially be exposed through secure APIs/logging/monitoring.

---

# 57. Step 38 — Freeze Controls

Support account freeze.

When:

```text
custody_account.status = FROZEN
```

then:

- withdrawals are rejected
- new outbound transactions cannot be created
- existing transactions follow defined incident policy
- deposits may still be accepted only if explicitly permitted by policy

Do not hard-code the operational treatment of in-flight transactions.

---

# 58. Step 39 — Asset/Network Kill Switch

Provide configuration to suspend an asset/network:

```text
BTC / Bitcoin = ACTIVE

USDC / Ethereum = SUSPENDED
```

When suspended:

- new deposit addresses may be blocked
- withdrawals blocked
- new transactions blocked

Existing positions remain visible.

This is an important operational safety control.

---

# 59. Step 40 — Fireblocks Isolation

All Fireblocks code must live under:

```text
com.bank.custody.fireblocks
```

Core domain must not import:

```text
com.fireblocks.*
```

except inside the adapter/integration layer.

This is mandatory for vendor portability.

---

# 60. Step 41 — Configuration Model

MVP configuration should include:

```text
Supported assets
Supported networks
Asset/network combinations
Withdrawal limits
Deposit confirmation policy
Account status policy
Provider configuration
AML thresholds
Policy rules
```

Do not hard-code these in Java.

Use approved configuration storage.

For MVP, database-backed configuration is acceptable if properly secured/audited.

---

# 61. Step 42 — Blockchain Confirmation

Do not assume:

```text
provider says transaction submitted
=
client deposit/withdrawal settled
```

Define a clear settlement rule.

For MVP:

```text
Submitted
    !=
Settled
```

Settlement requires the configured confirmation/finality condition.

The exact confirmation policy must be configurable per network.

---

# 62. Step 43 — Fees

Keep fee handling minimal for MVP.

Support:

```text
network/provider fee
```

if required by the bank's Fireblocks configuration.

Do not build a sophisticated fee engine.

However, preserve enough transaction data to distinguish:

```text
requested amount
executed amount
network fee
provider fee
client debit
```

before going to production.

---

# 63. Step 44 — Decimal Handling

Every asset has its own decimals.

Example:

```text
BTC = 8
ETH = 18
```

The application must reject amounts with unsupported precision.

Use:

```java
BigDecimal
```

and normalize using the asset's configured decimal precision.

Never use floating-point arithmetic.

---

# 64. Step 45 — API Versioning

Use:

```text
/v1/
```

from day one.

Do not expose database entities directly through REST.

Use:

```text
Controller
  -> DTO
  -> Application Service
  -> Domain
  -> Repository
```

---

# 65. Step 46 — Transaction Service

Create:

```java
TransactionOrchestrator
```

Responsibilities:

- validate
- create transaction
- invoke compliance
- invoke policy
- reserve funds
- call provider
- process provider updates
- finalize ledger

It must NOT contain Fireblocks-specific implementation details.

---

# 66. Step 47 — Ledger Service

Create:

```java
LedgerService
```

Responsibilities:

```text
credit()
reserve()
release()
settleWithdrawal()
reverse()
```

Every operation must:

1. validate
2. lock position
3. create immutable ledger entry
4. update position
5. create outbox event
6. commit atomically

---

# 67. Step 48 — Position Invariants

The application must enforce:

```text
available >= 0
locked >= 0
pending >= 0
```

and:

```text
total = available + locked + pending
```

subject to the chosen transaction/settlement model.

Never permit an API caller to directly set a position.

---

# 68. Step 49 — MVP Reconciliation Invariant

At minimum:

```text
Client position total
+
bank-controlled operational position
=
Fireblocks-controlled balance
```

where the scope includes only the same:

- asset
- network
- wallet population
- settlement status

Define exclusions explicitly.

Do not compare incomparable balances.

---

# 69. Step 50 — Build Order

The AI agent must implement in this exact order.

### Sprint 1

```text
1. Spring Boot project
2. PostgreSQL
3. Flyway
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
12. Outbox
```

### Sprint 3

```text
13. Fireblocks adapter
14. Fireblocks authentication
15. Wallet mapping
16. Deposit address
17. Fireblocks webhook
```

### Sprint 4

```text
18. Deposit lifecycle
19. AML integration
20. Deposit ledger posting
21. Position update
```

### Sprint 5

```text
22. Withdrawal API
23. Policy
24. Funds reservation
25. Fireblocks submission
26. Provider status handling
27. Settlement
```

### Sprint 6

```text
28. Reconciliation
29. Exception handling
30. Monitoring
31. Security tests
32. End-to-end tests
```

### Sprint 7

```text
33. API hardening
34. Azure deployment
35. operational runbook
36. MVP acceptance test
```

---

# 70. Definition of Done

The MVP is DONE only when all of the following work.

## Account

- [ ] Create custody account
- [ ] Activate account
- [ ] Freeze account
- [ ] Retrieve account

## Asset

- [ ] Asset allow-list
- [ ] Network allow-list
- [ ] Asset/network allow-list

## Wallet

- [ ] Fireblocks wallet mapping
- [ ] Deposit address
- [ ] Unknown address handling

## Deposit

- [ ] Provider event received
- [ ] Event deduplicated
- [ ] Account identified
- [ ] AML/risk executed
- [ ] Confirmation tracked
- [ ] Ledger credited
- [ ] Position updated
- [ ] Audit created

## Withdrawal

- [ ] API request
- [ ] Idempotency
- [ ] Account validation
- [ ] Asset/network validation
- [ ] Balance validation
- [ ] AML/risk
- [ ] Policy
- [ ] Funds reservation
- [ ] Fireblocks submission
- [ ] Provider ID stored
- [ ] Confirmation
- [ ] Ledger settlement
- [ ] Failure reversal
- [ ] Audit

## Reconciliation

- [ ] Provider balances imported
- [ ] Client positions calculated
- [ ] Comparison
- [ ] Break detection
- [ ] Break alert
- [ ] No automatic balance manipulation

## Security

- [ ] IAM
- [ ] Authorization
- [ ] Secret management
- [ ] Provider authentication
- [ ] Webhook validation
- [ ] Audit
- [ ] No secrets in logs

## Reliability

- [ ] Idempotency
- [ ] Outbox
- [ ] Retry
- [ ] Dead-letter
- [ ] Transaction locking
- [ ] Provider timeout handling

## Testing

- [ ] Unit tests
- [ ] Integration tests
- [ ] Provider mock tests
- [ ] Concurrent withdrawal test
- [ ] Duplicate event test
- [ ] E2E happy path
- [ ] E2E failure paths

---

# 71. Required E2E Acceptance Test

The AI agent must create an automated test for:

```text
1. Create customer C001
2. Create custody account CA001
3. Activate CA001
4. Configure BTC/Bitcoin
5. Create/retrieve Fireblocks wallet mapping
6. Generate BTC deposit address
7. Simulate Fireblocks deposit event
8. Verify provider event stored
9. Verify deposit transaction created
10. Run AML/risk
11. Mark deposit confirmed
12. Post 1 BTC to ledger
13. Verify CA001 position = 1 BTC
14. Request 0.4 BTC withdrawal
15. Run AML
16. Run policy
17. Reserve 0.4 BTC
18. Verify available = 0.6 BTC
19. Submit Fireblocks transaction
20. Store provider transaction ID
21. Simulate confirmation
22. Settle withdrawal
23. Verify final position = 0.6 BTC
24. Run reconciliation
25. Verify MATCH
```

Also test:

```text
Two simultaneous withdrawals of 0.7 BTC
against a 1 BTC balance.
```

Expected:

```text
One succeeds.
One fails.
Never allow total reservation > 1 BTC.
```

---

# 72. AI Agent Rules

The AI coding agent must follow these rules.

## Rule 1

Do not invent Fireblocks API endpoints or request/response fields.

Use the Fireblocks developer documentation available to the project.

## Rule 2

Do not put Fireblocks-specific types in the core domain.

## Rule 3

Do not use floating-point types for asset amounts.

## Rule 4

Do not mutate ledger entries.

## Rule 5

Do not directly modify positions from controllers.

## Rule 6

Do not allow products to call Fireblocks.

## Rule 7

Do not bypass policy/AML for withdrawals.

## Rule 8

Do not automatically fix reconciliation breaks.

## Rule 9

Do not silently process unknown blockchain addresses.

## Rule 10

Do not log secrets/private keys/tokens.

## Rule 11

Do not create a microservice for every domain object.

## Rule 12

Do not add out-of-scope functionality to the MVP.

---

# 73. Production Gate — Regulatory/Operational Review

Before production, the engineering MVP must be reviewed by the bank's:

- Compliance
- Legal
- Information Security
- Operational Risk
- DORA/ICT Risk
- Internal Audit
- Data Protection
- Architecture
- Business Continuity
- Custody Operations

The engineering team must not interpret this README as a legal opinion or regulatory approval.

MiCA custody requirements and applicable RTS/technical standards must be mapped by the bank's regulatory/legal teams to concrete controls before production. The technical implementation should retain evidence needed to demonstrate those controls.

For ICT outsourcing, perform the bank's required third-party risk assessment and contractual review of Fireblocks, including resilience, incident handling, audit/access, data handling, exit/termination and concentration/third-party risk.

---

# 74. Final MVP Architecture

The final MVP should look like:

```text
                         BANK PRODUCTS
                              |
                              v
                    +-------------------+
                    | API Management    |
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    | Spring Boot       |
                    | Custody Platform  |
                    +---------+---------+
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
   ACCOUNT/ASSET          COMPLIANCE           TRANSACTION
   ENTITLEMENT             + POLICY             ORCHESTRATOR
        |                     |                     |
        +---------------------+---------------------+
                              |
                              v
                    +-------------------+
                    | POSITION + LEDGER |
                    | PostgreSQL        |
                    +---------+---------+
                              |
                    +---------+---------+
                    |                   |
                    v                   v
              Outbox/Event        Reconciliation
                    |
                    v
              Azure Service Bus
                    |
                    |
                    v
             Fireblocks Adapter
                    |
                    v
                Fireblocks
                    |
          +---------+---------+
          |         |         |
       Wallets    MPC      Blockchain
```

---

# 75. What NOT to build after this MVP

Do not let the coding agent expand scope into:

- tokenisation
- trading
- staking
- exchange
- lending
- DeFi
- NFT custody
- multi-custodian routing
- advanced fee engine
- Travel Rule platform
- blockchain indexer
- custom MPC
- custom HSM
- customer UI

Those are separate initiatives.

The MVP goal is simply:

> **A bank-owned custody control plane that can securely create custody accounts, maintain client positions, accept deposits, execute withdrawals through Fireblocks, enforce AML/policy controls, maintain an auditable transaction lifecycle, and reconcile bank positions against custody infrastructure.**

---

## Appendix — Runbook, Integration Steps, and Checklist

Below are actionable steps to run the project locally, integrate with Fireblocks (sandbox), and a compact production checklist.

**Environment & secrets**: use environment variables or a secrets store (Azure Key Vault). Required variables (local `.env` or environment):

- **Postgres**: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- **Fireblocks**: `FIREBLOCKS_BASE_PATH`, `FIREBLOCKS_API_KEY`, `FIREBLOCKS_SECRET_KEY` (private key or path)
- **Spring profile**: `SPRING_PROFILES_ACTIVE=local`

Do NOT commit real keys. Use `.env.example` as a template for local development.

**Build & run (local)**

1. Build the project (skip tests during iterative development):

```bash
mvn -U -DskipTests clean package
```

2. Run the app:

```bash
mvn spring-boot:run
# or
java -jar target/digital-asset-custody-0.1.0-SNAPSHOT.jar
```

**Docker / Compose (local)**

- Use `docker-compose.yml` (repo) to start Postgres and the app. Example:

```bash
docker-compose up --build
```

Ensure the app reads DB URL and credentials from environment variables passed to the container.

**Fireblocks sandbox setup (high level)**

1. Request a Fireblocks sandbox account / tenant (developer onboarding).
2. Create an API key in Fireblocks and download the signing key (private key). Store this key securely (Key Vault). Do NOT commit it.
3. Configure the app to use Fireblocks sandbox base path (default `https://sandbox-api.fireblocks.io/v1`) and supply the API key and key material via environment variables or Key Vault.
4. Register a webhook endpoint in Fireblocks pointing to `/api/v1/webhooks/fireblocks` (or internal provider endpoint if behind gateway). Configure Fireblocks to sign webhooks — implement signature verification before processing.

**Webhook handling (MVP guidance)**

- Webhook endpoint must: validate signature, persist `provider_event` (unique provider_event_id), publish an internal message (outbox / Service Bus) and return quickly (202/200). Do not perform lengthy business processing in the HTTP handler.
- Maintain idempotency by enforcing UNIQUE(provider, provider_event_id) in `provider_event` table.

**API examples (minimal)**

- Create account:

```bash
curl -X POST http://localhost:8080/api/v1/accounts -H "Content-Type: application/json" \
    -d '{"externalCustomerId":"C001","name":"Customer 1"}'
```

- Create deposit address (idempotent):

```bash
curl -X POST http://localhost:8080/api/v1/custody-accounts/1/deposit-addresses \
    -H "Content-Type: application/json" \
    -d '{"asset":"BTC","network":"BITCOIN"}'
```

- Request withdrawal (Idempotency-Key required header):

```bash
curl -X POST http://localhost:8080/api/v1/custody-accounts/1/withdrawals \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"asset":"BTC","network":"BITCOIN","amount":"0.4","destinationAddress":"bc1..."}'
```

**Production checklist (short)**

- Secrets: move Fireblocks keys, DB credentials to Azure Key Vault and access via Managed Identity.
- Networking: expose only API gateway; use mTLS / OAuth2 for product APIs.
- Monitoring: enable Spring Boot Actuator endpoints, Application Insights, and log shipping to Log Analytics.
- Resilience: implement outbox + Service Bus for provider events, retries, and DLQ.
- Security: webhook signature verification, idempotency enforcement, no secrets in logs.
- Operational: reconciliation job, alerting (reconciliation breaks), and runbook for freeze/unfreeze.

**Testing & CI guidance**

- Unit tests: JUnit 5 + Mockito for services and policy rules.
- Integration tests: Testcontainers for Postgres and WireMock for Fireblocks.
- CI pipeline: run `mvn -DskipTests=false verify` in PRs; run integration tests on a dedicated runner.

**Next steps for me (if you want):**

- Wire real Fireblocks SDK integration (requires sandbox keys) and implement request signing / webhook verification.
- Implement `provider_event` persistence and outbox publishing to Azure Service Bus.
- Add simple policy engine stub and AML provider interface.

If you'd like, I will now wire the Fireblocks SDK calls (you said you have access) and implement webhook verification and provider_event persistence — confirm and provide sandbox keys or allow me to use a WireMock-based stub.
