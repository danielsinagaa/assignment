# 04 — SCHEMA DESIGN (15 points + DESIGN.md Q1)

The real schema is `/schema.sql` at the repo root (this file used to hold the draft).
Differences from the first draft: `VARCHAR` instead of `CHAR` (Hibernate `validate` expects VARCHAR for String),
and `TIMESTAMP(3) WITH TIME ZONE` for `Instant` columns (stored as UTC).

## Constraint → problem it stops (material for Q1)

| Constraint / index | Problem it stops |
|---|---|
| `uq_transfers_idempotency_key` | Two concurrent retries creating two transfers — last line of defence even if app checks race |
| `uq_transfers_transfer_id` | Two transfers sharing a public id (GET/cancel ambiguity) |
| `uq_transfers_partner_ref` | A callback matching two transfers |
| `fk_transfers_customer` | Transfer for a customer that does not exist |
| `ck_transfers_status` | Typos / unknown states written by a bug |
| `ck_transfers_total_debit` | Stored amounts that do not add up (debit ≠ send + fee) |
| `ck_transfers_send_amount`, `ck_transfers_fee` | Business minimums bypassed by a code path that skips validation |
| `ck_transfers_partner_ref` | Transfer in SENDING/COMPLETED/FAILED that can never be matched to a callback |
| `ck_transfers_failure_reason` | FAILED without a reason (spec 2.1) |
| `partner_callback_events.event_id` PK | Same callback applied twice (idempotency by eventId) |
| `ix_transfers_customer_day` | Full table scan for the daily-limit SUM inside a locked transaction |

## Deliberately NOT done (be ready to explain)
- No separate `idempotency_keys` table: the key lives on the row it created, inserted in the same statement → atomic, no orphan keys.
  Trade-off: can't remember keys of requests that failed validation (fine — they created nothing).
- No `version` column: state changes are compare-and-set on `status` (D-09).
- No status history / audit table: nice-to-have if time allows (`transfer_events`).
- Money for KRW as `BIGINT`, not DECIMAL: KRW has no minor unit; integer math is exact and fast.
