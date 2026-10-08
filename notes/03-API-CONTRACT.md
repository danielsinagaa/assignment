# 03 — API CONTRACT

Exact behaviour of every endpoint, including our additions. Tests and RESULT.md are checked against this file.
Error body is always `{ "error": "<CODE>", "message": "<free text>" }`.

## Transfer response body (used by every endpoint that returns a transfer)
```json
{
  "transferId": "3f1c9a3e-7c1b-4b8e-9a51-2d0f5f3e8b11",
  "customerId": "C001",
  "status": "REQUESTED",
  "sendCurrency": "KRW",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveCurrency": "PHP",
  "receiveAmount": "20600.00",
  "fxRate": "0.0412",
  "recipientName": "Juan Dela Cruz",
  "createdAt": "2026-09-30T01:23:45.123Z",
  "updatedAt": "2026-09-30T01:23:45.123Z"
}
```
Only present when set: `partnerRef` (from SENDING on), `failureReason` (FAILED only).

---

## POST /api/transfers — create (Part 1, idempotent)

Headers: `Content-Type: application/json`, **`Idempotency-Key: <1-64 chars [A-Za-z0-9_-]>`** (required)

| Case | Status | error |
|---|---|---|
| New key, valid body | **201** + transfer | — |
| Same key, same body (retry) | **201** + identical body + `Idempotent-Replayed: true` | — |
| Same key, different body | 409 | CONFLICT |
| Missing / invalid Idempotency-Key | 400 | VALIDATION_ERROR |
| Malformed JSON / missing field | 400 | VALIDATION_ERROR |
| sendAmount not a number, decimals, ≤ 0, < 10000, > 15 digits | 400 | VALIDATION_ERROR |
| recipientName blank or > 100 chars | 400 | VALIDATION_ERROR |
| sendCurrency ≠ KRW / receiveCurrency ≠ PHP | 400 | VALIDATION_ERROR |
| Unknown customerId | 400 | VALIDATION_ERROR |
| (Part 2) Daily total would exceed 3,000,000 KRW | 422 | LIMIT_EXCEEDED |

Check order: header → body → customer exists → [tx: lock customer → idempotency lookup → daily limit → insert].
Note: a replay is returned even if the limit is now full (the original was already counted).

## GET /api/transfers/{transferId}
| Case | Status |
|---|---|
| Found | 200 + transfer |
| Not found (incl. malformed id) | 404 NOT_FOUND |

## POST /api/transfers/{transferId}/cancel
| Case | Status |
|---|---|
| Status REQUESTED | 200 + transfer (status CANCELLED) |
| Any other status | 409 CONFLICT |
| Not found | 404 NOT_FOUND |

## POST /api/transfers/{transferId}/send — **our addition (Part 2)**
No body. See DECISIONS D-11.
| Case | Status |
|---|---|
| REQUESTED, partner accepts within 3 attempts | 200 + transfer (status **SENDING**, partnerRef set) |
| REQUESTED, partner rejects 3/3 | 200 + transfer (status **FAILED**, failureReason set) |
| Any other status | 409 CONFLICT |
| Not found | 404 NOT_FOUND |

## POST /api/callbacks/partner (Part 2)
```json
{ "partnerRef": "PR0000000009", "eventId": "evt_0001", "status": "COMPLETED" }
```
| Case | Status |
|---|---|
| Transfer SENDING, new eventId | 200 + transfer (status COMPLETED) |
| eventId already processed (same partnerRef) | 200 + current transfer, no state change |
| eventId already processed for a different partnerRef | 409 CONFLICT |
| Transfer not SENDING (REQUESTED, CANCELLED, FAILED, COMPLETED by another event) | 409 CONFLICT |
| Unknown partnerRef | 404 NOT_FOUND |
| Missing field / status ≠ "COMPLETED" | 400 VALIDATION_ERROR |

## Other
| Case | Status | error |
|---|---|---|
| Unknown URL | 404 | NOT_FOUND |
| Wrong method (405), wrong content type (415) | real code | VALIDATION_ERROR |
| Unexpected exception | 500 | INTERNAL_ERROR (no stack trace) |
