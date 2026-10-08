# Remittance Transfer API

Finshot backend take-home. Part 1 and Part 2 are both implemented.

## How to run

Needs only a JDK 17 or newer. Maven is downloaded by the wrapper. The database is H2 in-memory.

```bash
./mvnw spring-boot:run
```

On Windows: `mvnw.cmd spring-boot:run`. The app listens on `http://localhost:8080`. Every start begins with an empty
database: only the customers C001, C002 and C003 exist.

- Run the scenarios S1–S9: `./scripts/scenarios.sh` (bash + curl, on a freshly started app). See RESULT.md.
- Run the tests: `./mvnw test` (output saved in `test-output.txt`).

## Stack

Java 17, Spring Boot 4.1.1 (Web MVC, Validation, Data JPA), H2 in-memory, JUnit 5 + MockMvc.
The schema comes only from `schema.sql`. Hibernate does not create tables; it only checks that the entities match (`ddl-auto=validate`).

## API

| Method | Path | Result |
|---|---|---|
| POST | `/api/transfers` | 201 — create (needs `Idempotency-Key`) |
| GET | `/api/transfers/{transferId}` | 200 / 404 |
| POST | `/api/transfers/{transferId}/cancel` | 200 / 409 (only from REQUESTED) |
| POST | `/api/transfers/{transferId}/send` | 200 — **added by me**, see Assumptions (Part 2) |
| POST | `/api/callbacks/partner` | 200 / 404 / 409 (Part 2) |

All errors use `{ "error": "...", "message": "..." }`. Money is sent as JSON strings: `"5000"`, `"20600.00"`.
The transfer response has the required fields, plus `customerId`, `sendCurrency`, `receiveCurrency`, `fxRate`,
`recipientName`, `updatedAt`, and `partnerRef` / `failureReason` when they are set.

## How a client retries a create request safely

Send the header **`Idempotency-Key`** with every `POST /api/transfers`. It is required.

- **Generate it once per user action.** For example, create a UUID v4 when the user taps "Send". Allowed: 1–64 characters, letters, digits, `-` and `_`.
- **On a retry** (timeout, dropped connection, double tap), send the same key with the same body. You get back the original
  transfer: the same `201`, the same body, and the header `Idempotent-Replayed: true`. No second transfer is created.
- **Same key, different body** → `409 CONFLICT`. A key belongs to exactly one request.
- **No key** → `400 VALIDATION_ERROR`.

```bash
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```

The design is explained in DESIGN.md Q2.

## Assumptions

1. **PartnerClient rule.** The brief gives two rules that disagree: `abs(partnerRef.hashCode()) % 10 < 3` and "fails when the
   last digit of partnerRef is 0, 1, or 2". For example, `PR0000000010` fails with the second rule, but its hashCode
   ends in 5 (so the first rule says it succeeds). I used the **last-digit rule**, because it is the bold sentence, and S9 ("choose a partnerRef that ends in 0–2")
   only works with it.
2. **How a transfer enters SENDING.** The brief does not say. I added `POST /api/transfers/{id}/send`. I did not send
   automatically on create, for two reasons: the create response must say `REQUESTED`, and S4 must be able to cancel a
   REQUESTED transfer. The endpoint waits for the partner attempts (up to about 1.2 s) and returns the transfer: `SENDING`
   if the partner accepted, or `FAILED` if all 3 attempts failed.
3. **partnerRef** is `PR` plus the internal row id, padded to 10 digits (`PR0000000010`). It is deterministic on a fresh app,
   so S8 and S9 always behave the same.
4. **Retry backoff** is 200 ms, then 400 ms. Each partner attempt takes 200 ms. An exception from the partner client also
   counts as a failed attempt.
5. **sendAmount** may be a JSON string or a JSON integer. `"500000.00"` is rejected because it has decimals. Numbers longer than
   15 digits are rejected. Leading zeros are allowed (`"0500000"` means 500000).
6. **Currencies** must match exactly and are case-sensitive: `KRW` and `PHP`. A `recipientName` that is only spaces counts as empty.
7. **Unknown customerId** returns `400 VALIDATION_ERROR`, as the brief lists it under validation.
8. **Idempotency-Key is global**, not per customer. There is no login, so customerId is only request data. The same key with
   another customerId returns 409; it does not create a second transfer.
9. **Daily limit.** The limit includes the boundary: a day total of exactly 3,000,000 is allowed. The day is the transfer's
   creation date in Asia/Seoul. A retried request (same key) is still answered after the limit is full, because it was
   already counted.
10. **Callback.**
    - A repeated `eventId` returns 200 with the current transfer and changes nothing.
    - An `eventId` that was already used for another partnerRef returns 409.
    - A new `eventId` for a transfer that is not SENDING returns 409, even if the transfer is already COMPLETED.
11. **Other error codes.**
    - 405 and 415 keep their real status and use `VALIDATION_ERROR`.
    - An unknown URL returns 404 `NOT_FOUND`.
    - An unexpected error returns 500 `INTERNAL_ERROR`.
    - A database constraint that stops a race returns 409 `CONFLICT`.

## Not implemented

- Out of scope in the brief: authentication, list and search endpoints, Swagger, Docker, CI, metrics, health checks.
- Expiry of idempotency keys. In production I would keep them for about 24 hours, then delete them.
- Recovery for a transfer stuck in SENDING. This can happen if the app stops after saving SENDING but before saving the
  partner result. It needs a scheduled job, or an outbox table with a background worker.
- Asynchronous partner calls. `/send` calls the partner inside the HTTP request, to keep the results reproducible.
- Verifying that a callback really comes from the partner (for example, an HMAC signature).
- A persistent database. H2 in-memory loses all data on restart.

## Project layout

```
schema.sql                 tables, constraints, indexes, seed customers
scripts/scenarios.sh       S1–S9 as curl calls (output = RESULT.md)
src/main/java/com/finshot/remittance/
  transfer/                create, read, cancel, send; money math (TransferQuote); state machine (TransferStatus)
  partner/                 PartnerClient, simulated partner, retry loop (PartnerSender)
  callback/                partner callback, idempotent by eventId
  customer/                customer entity and row lock
  common/                  error shape, exception handler, Clock
```

