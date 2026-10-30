# PointBank Application Flow Report

## Purpose and scope

This report describes the application as it exists in the repository: its main user journeys, request flow, business rules, persistence, and current boundaries. It is based on static inspection of the source; it is not a security audit or a runtime certification.

## At a glance

PointBank is a Java 21 / Spring Boot REST API backed by MySQL and Spring Data JPA. The main business path is:

1. Register a customer.
2. Upload KYC documents and have a reviewer verify or reject them.
3. Open an account once the customer is eligible; an officer activates it.
4. Perform simulated deposits and withdrawals or internal account-to-account transfers. Successful transfers also create a durable outbox event in the financial database transaction.
5. Review transaction history or download a PDF statement.

The inspected source provides API endpoints and backend services; no customer-facing web or mobile UI was found in the application source. External payment rails, settlement, holds, interest, and fees are not implemented in the inspected flows.

## Request and component flow

```text
Client
  -> Spring Security (JWT authentication and role checks)
  -> REST controller (/api/v1/customers, /api/v1/accounts)
  -> Service (validation, identity/ownership, business rules, transaction boundary)
  -> Spring Data JPA repository
  -> MySQL

KYC file upload/download additionally uses:
  CustomerService -> KycDocumentStorageService -> local filesystem
```

Controllers validate and shape HTTP requests/responses. Services apply business rules and coordinate persistence. Repositories query and lock entities. DTOs are used at the API boundary rather than exposing JPA entities directly. Errors are translated by a shared controller advice into structured API error responses.

## Main user journeys

### 1. Customer registration and identity linking

- `POST /api/v1/customers` is public. The service normalizes email/mobile values, checks for duplicates, generates a public customer ID, hashes the supplied password, and saves the customer.
- A new customer starts with `customerStatus=CREATED` and `kycStatus=CREATED`.
- Protected customer operations use JWTs. The identity service first looks for an existing `(issuer, subject)` link. If none exists, it can link an unlinked customer record by a verified JWT email claim.
- Customer-scoped operations resolve the linked customer from the authenticated identity rather than trusting a client-supplied owner ID. Operational roles can access selected account operations without the customer-owner restriction.

### 2. KYC submission and review

- A customer (or reviewer) can upload a PAN/Aadhaar document through the customer KYC endpoints, subject to the customer ownership check for customer-role users.
- The storage service accepts PDF and PNG files, checks extension, declared media type, size, and file signature, then writes the file to the configured local KYC directory. Document metadata and version information are stored in the database.
- Upload moves KYC to `IN_PROGRESS`. A reviewer can inspect status/document metadata and download content, then verify or reject the submission.
- Verification requires at least one current document and `IN_PROGRESS`; it sets KYC and customer status to `VERIFIED` and records reviewer/time information.
- Rejection records the reviewer and reason. A rejected submission can be resubmitted as a new document version; older versions are retained as non-current.
- Document deletion is restricted after a review decision. When a current document is deleted before a decision and none remain, KYC can return to `CREATED`.

### 3. Account opening and lifecycle

- `POST /api/v1/accounts` is for the authenticated customer. The controller obtains the customer ID from the linked JWT identity.
- The service requires verified KYC and a customer status of `VERIFIED` or `ACTIVE`. It prevents duplicate account types for a customer, generates account identifiers, initializes INR balances to zero, and creates the account in `PENDING`.
- An account officer or admin activates accounts. Officer/admin operations also support freeze, unfreeze, block, and close.
- Lifecycle transitions are enforced by the `Account` entity. Closing requires both balances to be zero. Accounts in non-active states cannot post money movements.

### 4. Deposits, withdrawals, and transfers

- Deposit/withdrawal/transfer endpoints require an `Idempotency-Key`. The authenticated customer is restricted to their own account; teller/admin roles can operate without customer ownership binding.
- Services validate positive amounts with at most two decimal places, use pessimistic account-row locks for mutations, and record the actor and transaction reference.
- Repeating an idempotency key with the same operation and amount returns the original result. Reusing the key for a different operation/amount (or a different transfer destination) is rejected as a conflict.
- Deposits are explicitly **simulated**; the API records a deposit but does not confirm funds arrived from an external bank or payment provider. Available and ledger balances move together; holds and pending settlement are not modeled.
- Withdrawals require an active account and sufficient available and ledger balances. Deposits/withdrawals enforce configured rolling-window limits based on completed transactions.
- Internal transfers lock both accounts in account-number order, require both accounts active and in the same currency, and check limits in both directions. A successful transfer writes paired debit/credit transaction records and updates both balances in one database transaction. Failed transfer attempts can be recorded as a failed debit entry.
- A successful transfer now also writes a `MONEY_TRANSFERRED` JSON event to `outbox_event` in that same transaction. The payload includes the event/transfer IDs, source and destination account numbers, amount, currency, occurrence time, and correlation ID. Failed transfers and idempotent replays do not create additional events; the scheduled publisher sends eligible events to Kafka after the transfer transaction commits.
- The default rolling window is 24 hours. Current configuration sets savings deposit/withdrawal limits to INR 99,999.99 and salary limits to INR 300,000.00; current accounts have no configured limit. Only completed transactions count toward limits.

### 5. History, status lookup, and statements

- Customers and operational roles can query paginated account transaction history, a transaction by reference, and the result for an idempotency key.
- Transfers have a separate status lookup by the source account and idempotency key.
- PDF statements can cover a calendar year or a month range, with optional transaction-status/account-type filters. They are generated in UTC and capped at 5,000 transactions per export.

## API surface summary

| Area | Routes (all under `/api/v1`) | Typical access |
|---|---|---|
| Customer | `POST /customers`; customer lookup/list/update routes | Registration is public; lookups and updates require customer/reviewer access checks |
| KYC | `/customers/{customerId}/kyc/...` | Customer/reviewer for upload/content; reviewer for review decisions and review listings |
| Account | `/accounts`, `/accounts/me`, `/accounts/customers/{customerId}`, `/accounts/search`, `/accounts/{accountNumber}` | Customer for own account actions; operational roles for account search and lifecycle |
| Money movement | `/accounts/{accountNumber}/deposits`, `/withdrawals`, `/transfers` | Customer, bank teller, or admin |
| Transaction inquiry | `/accounts/{accountNumber}/transactions...`, `/accounts/transactions/{transactionReference}` | Customer ownership checked; operational roles permitted |

Exact route behavior and role annotations are in [AccountController.java](src/main/java/com/pointbank/bankplatform/controller/AccountController.java) and [CustomerController.java](src/main/java/com/pointbank/bankplatform/controller/CustomerController.java).

## Data model and state

- `Customer` owns addresses and KYC documents and can own multiple accounts.
- `Account` stores account number/type/status, currency, available and ledger balances, lifecycle timestamps, and a version field.
- `AccountTransaction` stores amount, type/status, resulting balances, idempotency key, actor, timestamp, failure reason, and optional transfer/counterparty references.
- `OutboxEvent` stores a typed, JSON-payload event with publication state, retry scheduling, and claim-lease fields. `ConsumerEventProcessing` and `NotificationRequest` provide unique event/channel records for consumer-side idempotency and simulated notification work.
- KYC document metadata is persisted in MySQL; file bytes are stored separately on disk.
- Account number and account ID are unique. A database uniqueness constraint also enforces one account per customer/account type. Transaction reference is unique, and idempotency keys are unique per account.

## Transactional outbox rollout status

Stages 1 through 4 establish the schema, atomic transfer-to-outbox path, scheduled Kafka publisher, and idempotent consumer:

- `V1__baseline_existing_pointbank_schema.sql` describes the existing application tables for a fresh database. With `baseline-on-migrate` at version 1, an existing non-empty database is baselined without recreating those tables.
- `V2__create_outbox_and_notification_tables.sql` adds the outbox, consumer event-processing, and notification-request tables.
- Hibernate uses `ddl-auto: validate`; Flyway owns schema evolution. Database passwords must be supplied through `DB_PASSWORD`.
- Spring Kafka and Flyway dependencies are installed. Successful transfers write their outbox records atomically with the financial transaction.
- `AccountServiceImpl.transfer` calls the outbox service only after the successful debit/credit postings. The outbox service requires an existing transaction; serialization or persistence errors therefore roll back the transfer. Failed transfers and idempotent replays bypass outbox creation.
- The scheduled publisher claims eligible rows in short database transactions using `FOR UPDATE SKIP LOCKED`, then publishes outside the transaction using the event ID as the Kafka key. Rows are marked `PUBLISHED` only after broker acknowledgment.
- Failed sends are rescheduled with capped exponential backoff; attempts are bounded and ultimately move to `FAILED`. Expired processing leases are recovered, and claim ownership prevents an old publisher from overwriting a newer claim. Publisher outcomes are available through the `pointbank.outbox.events` Actuator metrics.
- The topic, batch size, polling interval, lease duration, acknowledgment timeout, maximum attempts, and retry delays are configurable through `pointbank.outbox.*` settings and the corresponding `OUTBOX_*` environment variables. Kafka uses string key/value serializers, all-replica acknowledgments, and idempotent producer mode.
- The Kafka consumer validates and deserializes `MONEY_TRANSFERRED` events. A database transaction records the event ID in `consumer_event_processing` and creates one simulated email and SMS notification request. Duplicate event IDs are ignored; the uniqueness constraint protects against concurrent redelivery. These requests are stored only; no external email or SMS is sent.
- Consumer delivery uses bounded blocking retries followed by acknowledgment-aware publication to the configured dead-letter topic. Configure the DLT with the same partition count as the source topic. Consumer outcomes, retries, and dead-letter handoffs are exposed through `pointbank.outbox.consumer.events` metrics.
- Consumer group, enablement, retry count/interval, acknowledgment timeout, and DLT topic are configurable through `pointbank.outbox.consumer.*` and the corresponding `OUTBOX_CONSUMER_*` / `OUTBOX_DLT_TOPIC` environment variables.

## Technology and runtime configuration

- Java 21, Spring Boot 4.1.1, Spring MVC, Spring Security OAuth2 resource server/JWT, Spring Data JPA, MySQL, Spring Validation, PDFBox, springdoc OpenAPI, Spring Kafka, and Flyway.
- Default HTTP port is `9898`; health endpoint exposure is configured under Actuator.
- JWT issuer, audience, and roles claim are configurable. Protected routes are enabled only when issuer and audience are configured.
- Flyway manages the schema. V1 records the existing PointBank schema for fresh databases; populated databases are baselined at version 1, and V2 adds outbox/consumer tables. Hibernate is configured with `ddl-auto: validate`. Database credentials must be supplied with environment configuration; there is no password fallback. See [application.yaml](src/main/resources/application.yaml), [pom.xml](pom.xml), and [db/migration/](src/main/resources/db/migration/).

## Review notes and boundaries

1. **Database credentials:** `DB_PASSWORD` is required; no password default is configured. Keep it in deployment secret/environment configuration.
2. **Schema change strategy:** Hibernate `ddl-auto: update` is convenient for local development, but production schema evolution should use reviewed, versioned database migrations.
3. **KYC storage is instance-local by default:** the default is `./var/kyc-documents`. Multi-instance deployments need durable shared/object storage and a backup/retention strategy so documents remain available across restarts and instances.
4. **Banking functionality is a simulation:** deposits are not reconciled against an external source, transfers are internal to this service, and balances do not represent holds or settlement states. Do not treat these paths as production payment processing without the missing integrations and controls.
5. **Current test inventory:** the repository includes tests for account/customer controllers and services, concurrency/transaction limits, KYC storage, statements, identity, security configuration, and exception handling. Consumer tests cover deserialization, invalid payloads, duplicate suppression, processing records, and simulated notification requests; publisher tests cover claiming, lease recovery, acknowledgment, retries, and disabled publishing. The regression suite excluding the database-dependent application-context test passed 85 tests with five environment-gated skips. The full suite's context test could not connect because `DB_PASSWORD` was not present; DB-gated integration tests therefore also remain unrun in this environment.

## Source guide

- Account rules, lifecycle, transaction processing, ownership checks, limits, and response mapping: [AccountServiceImpl.java](src/main/java/com/pointbank/bankplatform/service/AccountServiceImpl.java)
- Atomic transfer event creation and JSON payload persistence: [OutboxService.java](src/main/java/com/pointbank/bankplatform/service/OutboxService.java), [MoneyTransferredEvent.java](src/main/java/com/pointbank/bankplatform/dto/MoneyTransferredEvent.java)
- Scheduled publication, leasing, retries, and Kafka acknowledgment handling: [OutboxPublisher.java](src/main/java/com/pointbank/bankplatform/service/OutboxPublisher.java), [OutboxClaimService.java](src/main/java/com/pointbank/bankplatform/service/OutboxClaimService.java), [OutboxEventStateService.java](src/main/java/com/pointbank/bankplatform/service/OutboxEventStateService.java)
- Consumer deduplication, simulated notification requests, retry, and dead-letter configuration: [MoneyTransferredEventConsumer.java](src/main/java/com/pointbank/bankplatform/service/MoneyTransferredEventConsumer.java), [TransferNotificationService.java](src/main/java/com/pointbank/bankplatform/service/TransferNotificationService.java), [KafkaConsumerConfiguration.java](src/main/java/com/pointbank/bankplatform/config/KafkaConsumerConfiguration.java)
- Customer registration, profile, KYC review, and document versioning: [CustomerServiceImpl.java](src/main/java/com/pointbank/bankplatform/service/CustomerServiceImpl.java)
- JWT and role configuration: [SecurityConfiguration.java](src/main/java/com/pointbank/bankplatform/config/SecurityConfiguration.java)
- JWT-to-customer identity linking: [CustomerIdentityService.java](src/main/java/com/pointbank/bankplatform/service/CustomerIdentityService.java)
- Transaction limits: [DailyTransactionLimitService.java](src/main/java/com/pointbank/bankplatform/service/DailyTransactionLimitService.java)
- KYC file validation and local storage: [KycDocumentStorageService.java](src/main/java/com/pointbank/bankplatform/service/KycDocumentStorageService.java)
- API error mapping: [GlobalExceptionHandler.java](src/main/java/com/pointbank/bankplatform/exception/GlobalExceptionHandler.java)
