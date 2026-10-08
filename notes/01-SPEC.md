# 01 — SPEC (Source of Truth)

Faithful transcription of `[Finshot_ID] Backend Developer Remote Assignment.docx` (Version 3.11).
If this file and the .docx ever disagree, **the .docx wins** — fix this file.
Requirement IDs (`R-xx`) are ours, added so PLAN / DECISIONS / tests can point at them.

---

## 0. Ground rules

| Item | Value |
|---|---|
| Part 1 | Required. ~3 hours. |
| Part 2 | Optional. ~3 hours. Skipping it does not disqualify. |
| Deadline | 4 calendar days |
| Scope | Backend only. No UI. |
| Language | Java 17+ with Spring Boot preferred (Kotlin, Go, Node.js, PHP accepted) |
| Database | H2 in-memory or SQLite. "We must not install anything." |

- "You can pass with Part 1 alone. Part 1 done well and explained clearly scores higher than Part 2 done badly. Do not rush Part 1 to reach Part 2."
- **AI tools** are allowed. README.md must contain **3 lines**: which tools, where. Not scored. *Not disclosing* is scored (negatively).
- **Interview**: screen share, explain / change / extend own code live. "Submit only code you can defend."
- **English**: grammar not scored. Short and simple preferred. Questions may be emailed in Indonesian; asking is not a penalty.

## 1. Domain

Customer in Korea sends money to the Philippines.
`Customer ──► Finshot ──► Partner (PH)` and `Partner ──callback──► Finshot`.
We record the request, call an external partner, and later receive a callback with the result.

---

## PART 1 — Required

Time split: code ~2h, RESULT.md + DESIGN.md + README.md ~1h.
**RESULT.md and DESIGN.md = 35 of the 70 Part 1 points.**

No points for (do NOT build): authn/authz/user accounts · list endpoints/pagination/filtering · Docker, CI, OpenAPI/Swagger, logging framework setup · admin API, metrics, health checks · own error framework · anything from Part 2 until Part 1 works.

### 1.1 Calculation rules

| ID | Rule | Value |
|---|---|---|
| R-01 | Fee | 1% of sendAmount, **minimum 3,000 KRW** |
| R-02 | Fee rounding | Whole KRW, **HALF_UP** |
| R-03 | FX rate | **1 KRW = 0.0412 PHP** (hard-code) |
| R-04 | receiveAmount | sendAmount × 0.0412, **2 decimals, HALF_UP** |
| R-05 | totalDebit | sendAmount + fee |

Example: sendAmount = 500000 → fee 5000, totalDebit 505000, receiveAmount 20600.00

- R-06 Every amount must be exact.
- R-07 receiveAmount always returned with **exactly 2 decimal places, including trailing zeros**.
- Money representation (code, API, DB) is our decision → DESIGN.md Q3.

### 1.2 States

```
REQUESTED ──► SENDING ──► COMPLETED
    │             │
    │             └──────► FAILED
    │
    └──► CANCELLED
```

- R-08 Any other transition → **409**.
- R-09 In Part 1, a transfer stays in REQUESTED after creation. No partner call yet.

### 1.3 API

**Create** — `POST /api/transfers`
Fields always sent (a minimum, not a closed list; extra headers/body fields allowed if documented):
```json
{ "customerId": "C001", "sendCurrency": "KRW", "sendAmount": "500000",
  "receiveCurrency": "PHP", "recipientName": "Juan Dela Cruz" }
```
`201 Created`:
```json
{ "transferId": "...", "status": "REQUESTED", "sendAmount": "500000", "fee": "5000",
  "totalDebit": "505000", "receiveAmount": "20600.00", "createdAt": "..." }
```
- R-10 Response must contain at least these fields, **with these names and these values**. More allowed.

**Idempotency (design is ours)**
- R-11 Same create request retried → **return the original transfer**, never create a second one.
- R-12 Retry signal reused with **different request data → 409**.
- We decide: how a retry is identified; what happens on same signal + different data; what happens if the client omits what our mechanism needs.
- R-13 README.md: what we added to the request, what a client must send, how a client generates it — enough to reproduce calls. DESIGN.md Q2 explains design.

**Read** — `GET /api/transfers/{transferId}` → R-14 `200`, or `404` if not found.

**Cancel** — `POST /api/transfers/{transferId}/cancel` → R-15 allowed only from REQUESTED, otherwise `409`.

**Error responses** — R-16 every non-2xx response uses this shape (do not design your own):
```json
{ "error": "VALIDATION_ERROR", "message": "sendAmount must be at least 10000" }
```
`error`: `VALIDATION_ERROR` (400), `NOT_FOUND` (404), `CONFLICT` (409). Part 2 adds `LIMIT_EXCEEDED` (422). `message` is free text, not parsed.

**Validation → 400**
- R-17 sendAmount ≤ 0, not a number, or has decimals
- R-18 sendAmount < 10,000
- R-19 recipientName empty or over 100 characters
- R-20 sendCurrency ≠ "KRW", or receiveCurrency ≠ "PHP"
- R-21 unknown customerId

### 1.4 schema.sql (15 points, DESIGN.md Q1)
Given table (every submission starts from these customers):
```sql
CREATE TABLE customers (
  customer_id VARCHAR(10) PRIMARY KEY,
  name        VARCHAR(100) NOT NULL
);
INSERT INTO customers (customer_id, name) VALUES
  ('C001', 'Kim'), ('C002', 'Lee'), ('C003', 'Park');
```
Everything else is ours to design.

### 1.5 Required scenarios S1–S6
Run **in order on a freshly started app**. Paste into RESULT.md: request (**complete: headers and body**), status code, response body. A curl script + captured output is acceptable.

| # | Scenario |
|---|---|
| S1 | Create a transfer for C001, amount 500000 |
| S2 | Retry S1 — response never arrived; send the exact same request again, incl. whatever signals a retry |
| S3 | Same retry signal as S1, but amount 600000 |
| S4 | Create another transfer, then cancel it. Show both responses. |
| S5 | Cancel the same transfer again |
| S6 | Create with sendAmount = 5000 |

Format per scenario:
```
### S1
Request:
POST /api/transfers
<headers>
{ "customerId": "C001", ... }
Status: 201
Response:
{ ... }
```

### 1.6 Tests — optional
Not required, no coverage measured. If written: save console output as `test-output.txt`. "Two meaningful tests tell us more than a large suite."

---

## PART 2 — Optional (only after Part 1 works)

### 2.1 Partner client
- Implement `PartnerClient` yourself. Do not call any real service.
- Must be deterministic. The document states **two rules** (see DECISIONS D-12):
  - code block: `fails when abs(partnerRef.hashCode()) % 10 < 3`, `succeeds otherwise`
  - **bold** bullet inserted inside that block: "it fails when the **last digit of partnerRef is 0, 1, or 2**, and succeeds otherwise. For S9, choose a partnerRef that ends in 0–2."
- R-22 When a transfer enters SENDING, generate a `partnerRef` (string we create, stored on the transfer). Same partnerRef for all retry attempts of that transfer.
- R-23 PartnerClient is a pure function of partnerRef. **No random number generator.** Each attempt takes **≥ 200 ms**.
- R-24 Partner success = partner **accepted** the request, NOT money delivered. Never COMPLETED because the partner call succeeded; stays SENDING until callback.
- Flow: move to SENDING → call partner, **up to 3 attempts total with backoff** → accepted: stay SENDING → all 3 fail: **FAILED with a reason**.
- R-25 Log every attempt: attempt number, partnerRef, outcome.

### 2.2 Daily limit
- R-26 Max **3,000,000 KRW of sendAmount** per customer per day.
- R-27 Day boundary: **Asia/Seoul (UTC+9)**.
- R-28 CANCELLED and FAILED do not count.
- R-29 Exceeding → **422** (`LIMIT_EXCEEDED`).
- R-30 Two requests at the same instant must not both pass when together they exceed the limit.

### 2.3 Partner callback — `POST /api/callbacks/partner`
```json
{ "partnerRef": "...", "eventId": "evt_0001", "status": "COMPLETED" }
```
- R-31 Callback is the **only** way to COMPLETED.
- status is always COMPLETED (no need to handle FAILED/CANCELLED values).
- FAILED only when all 3 partner attempts fail.
- R-32 Valid only from SENDING, otherwise 409.
- R-33 Idempotent by eventId: same eventId twice must not change state twice and **must not error on second delivery**.
- R-34 Unknown partnerRef → 404.

### 2.4 Optional scenarios S7–S9
| # | Scenario |
|---|---|
| S7 | With C002, submit transfers until the daily limit is exceeded. Show last success and the rejection. |
| S8 | Send the same callback (same eventId) twice. Show both responses and the final state. |
| S9 | Create a transfer whose partnerRef makes the partner fail. Show the retry log and the final state. |

---

## What to submit (exact names, repo root)
```
/README.md        how to run (ONE command), stack, how a client retries a create safely,
                  AI tools used, what you did not implement
/DESIGN.md        answers below
/schema.sql       CREATE TABLE, indexes, constraints, seed data
/RESULT.md        S1–S6 (+ S7–S9 if Part 2)
/src/...          source code
/test-output.txt  OPTIONAL, only if tests written
```
Delete `target/`, `build/`, `node_modules/`, `vendor/`, `.git/`. Submit a **.zip**.

## DESIGN.md — ≤ 150 words each, simple English
- **Q1** Describe your tables. Why these indexes and constraints? Which constraint stops which problem?
- **Q2** How does idempotency work? What identifies a retry, where is it enforced (app, DB, other)? What if two identical creates arrive at the exact same moment?
- **Q3** How do you represent, store, and round money — code, API, DB? At which exact step does rounding happen, and why there?
- **Q4** (Part 2) How is the daily limit safe under concurrency? Name the mechanism (row lock, unique constraint, isolation level, …) and what you gave up.
- **Q5** (Part 2) Partner callback arrives before your code finished saving the result of the partner call. What happens? If it breaks, say so and how to fix. ("my implementation breaks here, and here is how I would fix it" earns full marks.)

## Honesty
- If a scenario does not work, write what actually happened in RESULT.md. Honest failure > fake success.
- If a requirement is unclear: make a reasonable assumption, implement it, **note it in README.md**.
