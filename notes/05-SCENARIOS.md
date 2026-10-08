# 05 — SCENARIOS (expected results)

Run **in this order on a freshly started app** (H2 in-memory → restart = clean DB).
Plan: `scripts/scenarios.sh` (bash + curl, `curl -sS -i` so status + headers + body are captured), output pasted into RESULT.md.
Internal `id` values below assume no row was inserted by anything else — this is what makes partnerRef (D-03) predictable.

## Part 1

| # | Request | Expected status | Expected body / check | Internal id |
|---|---|---|---|---|
| S1 | POST /api/transfers, `Idempotency-Key: s1-...`, C001, 500000 | 201 | status REQUESTED, fee "5000", totalDebit "505000", receiveAmount "20600.00" | 1 |
| S2 | exact same request as S1 (same key, same body) | 201 | **identical** body to S1 (same transferId, same createdAt), header `Idempotent-Replayed: true` | — |
| S3 | same key as S1, sendAmount "600000" | 409 | `{"error":"CONFLICT",...}` | — |
| S4a | POST /api/transfers, new key, C001, e.g. 100000 | 201 | REQUESTED, fee "3000" (min fee), receiveAmount "4120.00" | 2 |
| S4b | POST /api/transfers/{S4 id}/cancel | 200 | status CANCELLED | — |
| S5 | POST /api/transfers/{S4 id}/cancel again | 409 | CONFLICT | — |
| S6 | POST /api/transfers, new key, sendAmount "5000" | 400 | VALIDATION_ERROR, "sendAmount must be at least 10000" | — |

Extra (optional, strengthens RESULT.md): GET S1 → 200; GET unknown → 404; create without `Idempotency-Key` → 400.

## Part 2

| # | Steps | Expected |
|---|---|---|
| S7 | C002: 6 × create 500000 (new key each) → ids 3–8, all 201. 7th create 500000 | 6th = 201 (day total exactly 3,000,000 — inclusive), 7th = **422 LIMIT_EXCEEDED** (no row inserted) |
| S8 | C003: create 100000 (id **9**) → `/send` → 200, SENDING, partnerRef `PR0000000009` (ends 9 → accepted 1st attempt). Callback `evt_0001` → 200 COMPLETED. Same callback again → 200, still COMPLETED. GET → COMPLETED | both callbacks 200, state changed once |
| S9 | C003: create 100000 (id **10**) → `/send` → partnerRef `PR0000000010` (ends 0 → rejected) | 200, status **FAILED**, failureReason set; app log shows attempt 1/3, 2/3, 3/3 REJECTED with backoff gaps |

S9 log excerpt must be copied from the application console into RESULT.md.

## If something does not match
Write what actually happened in RESULT.md (spec: honesty scores better than fake success). Do not edit output by hand.
