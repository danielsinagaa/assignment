# 02 — DECISIONS (ADR-lite)

Every design choice and assumption, with the reason. Written in English because this text is
reused in README.md ("Assumptions") and DESIGN.md (Q1–Q5).
Status: **Accepted** unless marked otherwise. Change a decision here first, then in code.

Guiding principle: *correct under concurrency, enforced by the database where possible,
simple enough to explain and change live in the interview.*

---

## D-01 Stack
- Java **17** bytecode target (spec says 17+; reviewers may only have 17). Built/run locally on JDK 21.
- Spring Boot **4.1.1** (latest stable on start.spring.io at 2026-09-30; 3.x no longer offered; Boot 4 baseline is Java 17).
  Maven **wrapper** (`mvnw`) — reviewer installs nothing but a JDK. Config format: `application.yml`.
- Boot 4 notes: Jackson 3 (`tools.jackson.*` packages), modular auto-config/starters — check imports when copying Boot 3 examples.
- Dependencies: spring-boot-starter-webmvc, -validation, -data-jpa, H2. Test: spring-boot-starter-test + spring-boot-starter-webmvc-test (JUnit 5, AssertJ, Mockito, MockMvc).
- **No Lombok** — Java records for DTOs/value objects; less magic to explain live.
- Run: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`). Port 8080.

## D-02 Schema source
- `/schema.sql` at repo root is the **only** schema definition (spec requires it at root).
- Maven copies it onto the classpath (pom `<resources>`), Spring runs it on startup (`spring.sql.init`).
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never creates tables; it fails fast if entities drift from schema.sql.
- H2 in-memory: `jdbc:h2:mem:remittance;DB_CLOSE_DELAY=-1` → every start is a fresh DB (needed for "freshly started app").

## D-03 Identifiers
- `transfers.id` BIGINT identity — internal PK, used for FKs and joins.
- `transferId` (API) = `transfers.transfer_id` UUID string, UNIQUE. Not sequential → not guessable, no leak of volume.
- `partnerRef` = `"PR"` + transfer `id` zero-padded to 10 digits, e.g. `PR0000000010`. Generated when entering SENDING.
  Deterministic on a fresh app → S9 is reproducible (see D-12, 06-SCENARIOS).

## D-04 Idempotency mechanism
- Client sends header **`Idempotency-Key`** on `POST /api/transfers`. **Required**: missing/blank → 400 VALIDATION_ERROR.
  Format: 1–64 chars `[A-Za-z0-9_-]`. Client generates a **UUID v4 once per user intent** (per "Send" tap) and reuses it for every retry of that intent.
- Stored on the transfer row: `idempotency_key` (**UNIQUE, global**) + `request_hash` (SHA-256 of the canonical request:
  `customerId|sendCurrency|normalized sendAmount|receiveCurrency|recipientName`).
- Why global and not per customer: there is no authentication, so `customerId` is just body data. Scoping by it would let
  "same key + different customerId" silently create a second transfer instead of 409.
- Lookup order inside the create transaction: lock customer row (D-13) → find by key →
  - found + same hash → **replay**: same status **201**, identical body, header `Idempotent-Replayed: true` (first call sends `false`)
  - found + different hash → **409 CONFLICT**
  - not found → daily limit check → insert.
- Race backstop: the UNIQUE constraint. If two requests with the same key still both reach INSERT (e.g. different customers),
  the loser catches the constraint violation, re-reads in a new transaction, and returns replay or 409.
- Validation failures (400) are not stored → retrying an invalid request just returns 400 again.
- Keys never expire (in-memory DB). Production: TTL (e.g. 24h) + cleanup job. → "not implemented".

## D-05 Replay status code
Replay returns **201 + identical body** (not 200). A retry should look identical to the original to the client; the
`Idempotent-Replayed: true` header tells the difference for anyone who cares. (Same approach as Stripe.)

## D-06 Money representation
| Layer | KRW (sendAmount, fee, totalDebit) | PHP (receiveAmount) | FX rate |
|---|---|---|---|
| API | JSON **string**, integer (`"5000"`) | JSON **string**, always 2 decimals (`"20600.00"`) | string `"0.0412"` (extra field) |
| Code | `BigDecimal` scale 0 (never `double`/`float`) | `BigDecimal` scale 2 | `BigDecimal` constant |
| DB | `BIGINT` | `DECIMAL(15,2)` | `DECIMAL(10,4)` stored **per transfer** |

- Strings in the API: JSON numbers are often parsed as IEEE double by clients (JS) → precision loss and lost trailing zeros.
- Rounding happens **exactly once, at quote time** (inside `TransferQuote.of(sendAmount)`), before persisting. Stored values
  are final; reads never recalculate. Why: the amount the customer agreed to must never change if the rate/rules change later.
- fee = `max(3000, sendAmount × 0.01 rounded HALF_UP to scale 0)`. receiveAmount = `sendAmount × 0.0412` → `setScale(2, HALF_UP)`.
- Serialize with `toPlainString()` (never scientific notation like `2.06E+4`).

## D-07 sendAmount parsing (assumptions)
- Accept JSON string (spec example) **or** JSON integer. Both normalized to a whole number.
- Rejected with 400: non-numeric, blank, decimals (**including `"500000.00"`** — spec says "has decimals"), ≤ 0, < 10000,
  more than 15 digits (overflow guard; not in spec → documented assumption).
- Leading zeros (`"0500000"`) accepted and normalized to `500000` (also for the idempotency hash).

## D-08 Other validation (assumptions)
- Currencies are exact, case-sensitive: `"KRW"`, `"PHP"` (`"krw"` → 400).
- recipientName: blank/whitespace-only counts as empty → 400. Length checked on the value as sent (max 100).
- Unknown customerId → **400** VALIDATION_ERROR (spec R-21), not 404.
- Validation order: header present → body fields (all field errors joined, sorted) → Idempotency-Key format → sendAmount → customer exists.

## D-09 State machine
- `TransferStatus` enum owns the allowed transitions (`canTransitionTo`). One place to read, one place to change live.
- DB writes are **compare-and-set**: `UPDATE transfers SET status=:to ... WHERE id=:id AND status=:from`.
  0 rows updated → 409 (the state changed under us). No lost updates without needing a `version` column.
- DB `CHECK` limits `status` to the 5 known values.

## D-10 Error handling
- One `@RestControllerAdvice`, one `ErrorResponse(error, message)` record. No custom framework (spec R-16).
- Mapping: validation/malformed JSON/missing header → 400 `VALIDATION_ERROR`; not found (incl. unknown URL) → 404 `NOT_FOUND`;
  bad transition / idempotency mismatch → 409 `CONFLICT`; daily limit → 422 `LIMIT_EXCEEDED`.
- Assumption: statuses outside the spec list keep their real HTTP code with the same shape:
  405/415 → `VALIDATION_ERROR`, unexpected 500 → `INTERNAL_ERROR` (no stack trace in the body).
- Any `DataIntegrityViolationException` that reaches the handler (a DB constraint caught a race) → 409 `CONFLICT`.

## D-11 How a transfer enters SENDING (Part 2) — the spec does not say
- New endpoint **`POST /api/transfers/{transferId}/send`**. Only from REQUESTED, otherwise 409.
- Why not automatically on create: the create response must say `"status": "REQUESTED"` (R-10), and S4 must be able to cancel
  from REQUESTED. Auto-send would break one or race the other.
- Flow (no DB transaction is held while calling the partner):
  1. **Tx 1**: CAS REQUESTED→SENDING, set `partner_ref`, `sending_at`. **Commit.**
  2. Call partner outside any transaction, up to 3 attempts (D-12).
  3. **Tx 2**: accepted → set `partner_accepted_at` (status stays SENDING). All failed → CAS SENDING→FAILED + `failure_reason`.
  4. Return 200 with the current transfer.
- Synchronous for reproducible RESULT.md (~1.2s worst case). Production: return 202 and run step 2–3 in a worker / outbox.
- Q5 answer comes from step 1: SENDING is committed *before* the partner call, so an early callback finds SENDING and completes
  the transfer. Step 3 is conditional (`WHERE status='SENDING'`) so it never overwrites COMPLETED. Known gap: crash between
  step 1 and 3 leaves SENDING forever → production needs a recovery job (documented, not implemented).

## D-12 PartnerClient rule (spec contradiction)
- The brief states two rules: `abs(partnerRef.hashCode()) % 10 < 3` and "last digit of partnerRef is 0, 1, or 2".
  They disagree (e.g. `PR-0010`: hash rule → success, last-digit rule → fail).
- **Decision: last-digit rule.** It is the bold sentence, and S9 says "choose a partnerRef that ends in 0–2", which only makes
  sense with it. The hashCode formula is kept as a comment. → README assumption.
- `SimulatedPartnerClient`: sleeps 200 ms per attempt (R-23), no `Random`.
- Retry: 3 attempts total, backoff 200 ms then 400 ms (exponential). Hand-written loop (not Spring Retry) — easy to read and change live.
- Log line per attempt: `partner attempt={}/3 partnerRef={} outcome={ACCEPTED|REJECTED}`.
- failure_reason: `"Partner rejected the request (3 of 3 attempts failed)"`. A client exception counts as a failed attempt.

## D-13 Daily limit concurrency (Part 2)
- Inside the create transaction: `SELECT ... FROM customers WHERE customer_id=? FOR UPDATE` (JPA `PESSIMISTIC_WRITE`),
  then `SUM(send_amount)` for that customer + `business_date` + status IN (REQUESTED, SENDING, COMPLETED), then insert.
- Limit is inclusive: total **= 3,000,000 is allowed**, > 3,000,000 → 422.
- `business_date` DATE column = creation instant converted to Asia/Seoul, computed from an injected `Clock` (testable).
- What we give up: all creates of the **same customer** are serialized (throughput per customer), and the lock is held for the
  whole short transaction. Different customers are not blocked. Alternative considered: a `daily_usage` counter row with a
  conditional `UPDATE ... WHERE used + :amt <= limit` — less locking, but needs decrements on cancel/fail (more places to get wrong).
- The same customer lock also serializes same-key idempotency races (D-04).

## D-14 Partner callback (Part 2)
- Table `partner_callback_events` (`event_id` PK). One transaction:
  1. find transfer by partnerRef **FOR UPDATE** → none → 404
  2. eventId already stored → 200 with current transfer, **no change** (R-33). Same eventId but different partnerRef → 409.
  3. status ≠ SENDING → 409
  4. CAS SENDING→COMPLETED, set `completed_at`; insert event row; commit → 200.
- Body validation: partnerRef, eventId required; status must be `"COMPLETED"` → else 400.
- Event is stored only on success, so a 409 callback can be redelivered later.

## D-15 Time
- `createdAt`/`updatedAt` = ISO-8601 UTC instants with milliseconds (`2026-09-30T01:23:45.123Z`). DB `TIMESTAMP` stored in UTC.
- Injected `Clock` bean everywhere (never `Instant.now()` directly) → daily-limit tests can pin the time.

## D-16 Response body (superset of R-10)
`transferId, customerId, status, sendCurrency, sendAmount, fee, totalDebit, receiveCurrency, receiveAmount, fxRate,
recipientName, partnerRef?, failureReason?, createdAt, updatedAt` — null fields omitted.

## D-17 Package structure (feature-first)
```
com.finshot.remittance
 ├─ transfer/   TransferController, TransferService, Transfer (entity), TransferRepository,
 │              TransferStatus, TransferQuote, dto/ (CreateTransferRequest, TransferResponse)
 ├─ customer/   Customer, CustomerRepository
 ├─ partner/    PartnerClient, SimulatedPartnerClient, PartnerSender (retry loop)
 ├─ callback/   PartnerCallbackController, PartnerCallbackService, CallbackEvent(+Repository)
 └─ common/     ErrorResponse, GlobalExceptionHandler, exceptions, ClockConfig
```

## D-18 Tests (few, meaningful — spec 1.6)
1. `TransferQuoteTest` — parameterized: 500000, min-fee boundary (299999/300000/300050), HALF_UP on fee and PHP.
2. `IdempotencyConcurrencyTest` — N threads, same key → exactly 1 row; different body → 409.
3. `DailyLimitConcurrencyTest` (Part 2) — two parallel creates that together exceed → exactly one 201, one 422.
4. `CallbackIdempotencyTest` (Part 2) — same eventId twice → both 200, state changed once.

## D-19 Not implemented (goes to README)
Auth, list/pagination, Swagger, Docker, metrics — per spec. Idempotency key TTL. Recovery of stuck SENDING. Async partner
worker/outbox. Callback authentication (signature/HMAC). Multi-currency / live FX.
