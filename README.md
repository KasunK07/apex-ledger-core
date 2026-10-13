# fintech-ledger-core

Double-entry ledger service for customer balances. Every successful movement is one journal and exactly two immutable entries whose debit and credit amounts are equal. PostgreSQL is the system of record: the application posts under `SERIALIZABLE`, and the database rejects an unbalanced journal at commit.

## Stack

- Java 17, Spring Boot 3.5, Spring Data JPA, Spring Security, Spring Validation
- PostgreSQL 16, Flyway, HikariCP
- Stateless JWT (HMAC) for the API client
- Maven, Testcontainers, springdoc OpenAPI

## Run

Copy the environment template and put in your own database and API credentials. `.env` is gitignored.

```powershell
Copy-Item .env.example .env
```

Then load it in the current shell and start the app:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $name, $value = $_.Split('=', 2)
  Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
}
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`. Interactive docs are at `http://localhost:8080/swagger-ui.html`. Liveness is `http://localhost:8080/actuator/health`.

`docker compose up --build` also builds and starts the application container. Compose reads `.env` for the database name, user, and password.

Flyway creates empty tables. Deposits need a treasury account per currency. Edit `scripts/seed-treasury.example.sql` with your opening balances and run that file yourself. It is not part of the migration.

### Happy path

```bash
curl.exe -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d "{\"clientId\":\"ledger-api\",\"clientSecret\":\"ledger-secret\"}"
```

Use the `accessToken` as `Authorization: Bearer ...` on the calls below.

```bash
curl.exe -s -X POST http://localhost:8080/api/v1/customers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"email\":\"ada@example.com\",\"fullName\":\"Ada Lovelace\"}"

curl.exe -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"customerId\":\"CUSTOMER_UUID\",\"currency\":\"USD\"}"

curl.exe -s -X POST http://localhost:8080/api/v1/accounts/ACCOUNT_UUID/deposits \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: deposit-ada-100" \
  -d "{\"amount\":\"100.0000\"}"

curl.exe -s -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: transfer-ada-grace-40" \
  -d "{\"sourceAccountId\":\"SOURCE_UUID\",\"destinationAccountId\":\"DEST_UUID\",\"amount\":\"40.0000\"}"

curl.exe -s http://localhost:8080/api/v1/accounts/SOURCE_UUID/statement \
  -H "Authorization: Bearer $TOKEN"
```

A currency works after you insert an `ACTIVE` `TREASURY` account for it. The example script uses `USD`, `EUR`, and `LKR`.

## Architecture

```
api  →  application  →  domain
              ↓
        infrastructure  →  PostgreSQL
```

| Package | Responsibility |
| --- | --- |
| `com.ledger.api.controller` | HTTP and OpenAPI |
| `com.ledger.api.dto.request` | Inbound payloads |
| `com.ledger.api.dto.response` | Outbound payloads |
| `com.ledger.api.error` | JSON errors and `@RestControllerAdvice` |
| `com.ledger.application` | Use cases. `LedgerEngine` is the only poster |
| `com.ledger.domain.model` | Entities, enums, and money scale |
| `com.ledger.domain.exception` | Financial failures |
| `com.ledger.infrastructure.persistence` | Spring Data and `PESSIMISTIC_WRITE` lookups |
| `com.ledger.infrastructure.idempotency` | Filter, aspect, and request hash |
| `com.ledger.infrastructure.security` | Stateless JWT filter chain |
| `com.ledger.infrastructure.config` | Jackson, OpenAPI, and security properties |

Controllers do not touch balances. `LedgerEngine.transfer`, `deposit`, and `withdraw` are the only write paths, each annotated:

```java
@Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
```

`open-in-view` is off. Account statements use `REPEATABLE READ` so the balance and the page of entries come from one snapshot.

## Data model

Flyway owns the schema (`src/main/resources/db/migration/V1__init_schema.sql`). Hibernate is set to `validate`.

| Table | Role |
| --- | --- |
| `customers` | Party. Email is unique and stored lowercase |
| `accounts` | One balance per account, optimistic `@Version`, pessimistic row locks on post |
| `idempotency_keys` | One row per successful mutating request |
| `transaction_journals` | Header: reference, type, status, optional memo |
| `transaction_entries` | The two lines of a journal |

Normalization:

- Customer name and status are not copied onto accounts.
- A journal is the header; amounts live only on entries. That is the normal header/line split, and it is what makes "one business action, two legs" enforceable.
- `currency` is stored again on each entry. That is deliberate. A posted line must stay understandable even if the account row is later closed. The currency is not a second source of truth for posting: a `BEFORE INSERT` trigger rejects an entry whose currency differs from its account.
- `accounts.balance` is a materialized position, not an independent number. It moves only inside the same transaction as the entries. `NUMERIC(19,4)` and a `balance >= 0` check back the application rule.
- `account_role` (`CUSTOMER` or `TREASURY`) is an extension beyond a pure wallet table. Deposits and withdrawals need a contra account or they are single-sided. One treasury account per currency is enforced by a partial unique index.
- `transaction_journals.memo` is an optional narrative. It is not part of the accounting identity.

`PENDING` and `FAILED` exist on the journal status check so a later hold/capture or durable failure log can use them. This service inserts `COMPLETED` only. A failed attempt rolls back, so a declined transfer leaves no journal. That is the right outcome for a single-database ACID post: callers retry with the same idempotency key after the cause is fixed (for example after a deposit arrives).

Entries are immutable. An update or delete trigger raises, and the entity is marked `@Immutable`. Corrections are new journals.

### Opening balances

`V1__init_schema.sql` does not insert customers or balances. After you load treasury accounts from your own script, every later movement is a balanced journal, so the sum of balances in a currency stays equal to the opening total you inserted. Add funds with a new journal rather than editing `accounts.balance`.

## Double-entry rules

Balance convention on every account, treasury included:

- a `CREDIT` increases `balance`
- a `DEBIT` decreases `balance`
- amounts on entries are always positive
- a debit that would cross zero is `INSUFFICIENT_FUNDS` (`422`)

| Operation | Debit | Credit |
| --- | --- | --- |
| Deposit | Treasury of that currency | Customer account |
| Withdrawal | Customer account | Treasury of that currency |
| Transfer | Source customer account | Destination customer account |

The engine rejects a journal that is not exactly two legs or whose debit total differs from its credit total. The database repeats that rule with a **deferred** constraint trigger on `transaction_journals`. The trigger has to be deferred: the journal row is inserted before its lines, and an immediate check would reject every post. At commit the trigger requires exactly two entries and `sum(debits) = sum(credits)`.

Customer transfers cannot name a treasury account. Cross-currency transfers are rejected; there is no FX book. The source and destination of a transfer must differ.

A customer who is `SUSPENDED` or `KYC_PENDING` cannot move money. An account that is `FROZEN` or `CLOSED` cannot move money. New customers default to `ACTIVE` so the API is usable; pass `"status": "KYC_PENDING"` to model onboarding. Status changes after registration are operational updates, not part of the public money API.

## Concurrency

Two transfers that share an account must not both read the old balance and both succeed.

1. The posting transaction is `SERIALIZABLE`.
2. Before any balance change, the engine takes `PESSIMISTIC_WRITE` locks (`SELECT FOR UPDATE`).
3. Lock order is fixed: retail customer rows ordered by id, then account rows ordered by id. The shared treasury *customer* is not locked, because that single row would serialize deposits in every currency. The treasury *account* for the currency is locked with the customer account, which is the actual balance hotspot.
4. `lock_timeout` is set to 10 seconds for the transaction (`SET LOCAL` via `set_config`). A lock that is not acquired becomes a concurrency failure and is retried.
5. `@Version` on `accounts` still increments. A writer that bypassed the explicit lock fails the optimistic check instead of silently overwriting the balance.
6. `PostingRetryExecutor` retries the whole posting up to five times on Spring `ConcurrencyFailureException` (serialization failure, deadlock, lock timeout, optimistic conflict). Business exceptions such as insufficient funds are not retried.

`SERIALIZABLE` in PostgreSQL uses a snapshot taken at the first statement. A second request that blocks on the idempotency advisory lock can wake up still unable to *see* the first request's committed key. The unique index sees it, the insert fails, the transaction rolls back, and the retry starts a new snapshot that finds the stored response and replays it. The retry treats `uq_idempotency_key` violations as concurrency conflicts for that reason. Do not catch that violation inside the posting transaction: PostgreSQL aborts the transaction, and the only legal next step is rollback.

This combination favors correctness over throughput. The treasury account of a busy currency is a write hotspot because every deposit and withdrawal updates that one balance. The next scale step is striped treasury accounts or an append-only entry log with a derived balance, not a weaker isolation level.

## Idempotency

`POST /api/v1/transfers`, `POST /api/v1/accounts/{id}/deposits`, and `POST /api/v1/accounts/{id}/withdrawals` require `Idempotency-Key`.

- The key is 8–200 characters: letters, digits, `.`, `_`, `:`, `-`.
- `IdempotencyFilter` reads the raw body once (64KiB limit), wraps the request, and stores `SHA-256(method + path + body)`.
- `IdempotencyAspect` rejects a missing or malformed key and, after a replay, sets `Idempotency-Replayed: true`.
- Inside the posting transaction, `IdempotencyGuard` takes `pg_advisory_xact_lock` on the key, then either returns the stored response or inserts the key and posts.
- The same key and the same raw body return the original response. The first success is `201`; a replay is `200`. The stored payload is the original response, including the balances from that post, not a live re-read.
- The same key with a different body is `409 IDEMPOTENCY_CONFLICT`.
- A failed post rolls the key back with the journal, so a later retry is evaluated again.
- Keys are global, not per endpoint. Reusing a key on another route conflicts because the hash includes the path.
- Clients must retry the identical bytes. A re-serialized JSON body with different whitespace is a different hash.
- There is no purge job. Deleting a key makes a retry able to post a second time. Keep keys for at least as long as clients retry.

The replay decision lives in the transaction, not only in the aspect. An aspect that returned a cached response before the transaction started would race with the request that is still committing.

## Security

`POST /api/v1/auth/token` exchanges the configured client id and secret for an HMAC JWT. Comparison is SHA-256 then `MessageDigest.isEqual`. Every other business route requires `Authorization: Bearer`. Sessions are not created. CSRF is disabled because the client is not a browser cookie session. Swagger and health are public. `application.yml` reads the database and JWT settings from the environment. Put real values in `.env`, which is not committed. `.env.example` only shows the names.

This is service authentication for the ledger core, not end-user OAuth or step-up authorization.

## Errors

Failures use a single JSON shape: `timestamp`, `status`, `error`, `code`, `message`, `path`, and `violations` when field validation fails. Unknown JSON properties are rejected. Amounts use scale 4 (`NUMERIC(19,4)`); more fractional digits are a validation error. Responses render money as a JSON number with four decimal places.

## Push to GitHub

From the project folder, after `.env` exists only on your machine:

```powershell
git init
git add .
git status
git commit -m "Add double-entry fintech ledger core"
gh repo create fintech-ledger-core --private --source=. --remote=origin --push
```

`git status` should not list `.env`. If `gh` is not logged in, run `gh auth login` first. To use a repository you already created:

```powershell
git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
git branch -M main
git push -u origin main
```

## Tests

```bash
./mvnw test
```

`MoneyAmountsTests` and `RunningBalanceCalculatorTests` do not need Docker. `LedgerApiTests` starts PostgreSQL 16 with Testcontainers and is skipped when Docker is not available. It covers deposit, transfer, withdrawal, statements, idempotent replay, idempotency conflict, concurrent same-key deposit, concurrent transfers, frozen and KYC rules, and the database rejecting a one-sided journal.

## Operational limits

- One treasury row per currency is correct and will queue under heavy deposit load.
- Optimistic versions and row locks assume this service is the only writer. Do not update `accounts.balance` from another application.
- Journal status `FAILED` is reserved. Declines are not stored.
- Multi-currency FX, holds, fees, and outbox publication are out of scope. A fee would be a third entry and would require relaxing the "exactly two entries" trigger on purpose.
