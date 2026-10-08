# RESULT

All scenarios were run in order on a freshly started app (H2 in-memory, empty database), on 2026-09-30.
The text below is the unedited output of `scripts/scenarios.sh`, so the calls can be reproduced exactly:

```bash
./mvnw spring-boot:run > app.log          # terminal 1
APP_LOG=app.log ./scripts/scenarios.sh    # terminal 2
```

Notes:
- The create endpoint needs the `Idempotency-Key` header (see README.md). All scenarios passed as expected.
- S8/S9 use `POST /api/transfers/{transferId}/send`, which is not in the brief (see README.md, Assumptions).
- `partnerRef` is built from the internal row id, so on a fresh app S8 gets `PR0000000009` (ends in 9 → partner
  accepts) and S9 gets `PR0000000010` (ends in 0 → partner rejects).
- Timestamps are UTC. Response bodies are pretty-printed by the server.

## Part 1

### S1 — Create a transfer for C001, amount 500000
Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response header: `Idempotent-Replayed: false`
Response:
```json
{
  "transferId" : "45f450b5-2fc6-48f7-b4e7-e63f126773ff",
  "customerId" : "C001",
  "status" : "REQUESTED",
  "sendCurrency" : "KRW",
  "sendAmount" : "500000",
  "fee" : "5000",
  "totalDebit" : "505000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "20600.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "createdAt" : "2026-09-30T09:56:16.150Z",
  "updatedAt" : "2026-09-30T09:56:16.150Z"
}
```

### S2 — Retry S1 (same Idempotency-Key, same body)
Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response header: `Idempotent-Replayed: true`
Response:
```json
{
  "transferId" : "45f450b5-2fc6-48f7-b4e7-e63f126773ff",
  "customerId" : "C001",
  "status" : "REQUESTED",
  "sendCurrency" : "KRW",
  "sendAmount" : "500000",
  "fee" : "5000",
  "totalDebit" : "505000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "20600.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "createdAt" : "2026-09-30T09:56:16.150Z",
  "updatedAt" : "2026-09-30T09:56:16.150Z"
}
```

### S3 — Same Idempotency-Key as S1, amount 600000
Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"600000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 409
Response:
```json
{
  "error" : "CONFLICT",
  "message" : "Idempotency-Key was already used with a different request"
}
```

### S4 — Create another transfer, then cancel it
Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 1b4e28ba-2fa1-41d2-883f-0016d3cca427

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response header: `Idempotent-Replayed: false`
Response:
```json
{
  "transferId" : "93112a60-760d-42e4-b41e-b79942547af0",
  "customerId" : "C001",
  "status" : "REQUESTED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "createdAt" : "2026-09-30T09:56:24.875Z",
  "updatedAt" : "2026-09-30T09:56:24.875Z"
}
```

Request:
```http
POST /api/transfers/93112a60-760d-42e4-b41e-b79942547af0/cancel
```
Status: 200
Response:
```json
{
  "transferId" : "93112a60-760d-42e4-b41e-b79942547af0",
  "customerId" : "C001",
  "status" : "CANCELLED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "createdAt" : "2026-09-30T09:56:24.875Z",
  "updatedAt" : "2026-09-30T09:56:26.094Z"
}
```

### S5 — Cancel the same transfer again
Request:
```http
POST /api/transfers/93112a60-760d-42e4-b41e-b79942547af0/cancel
```
Status: 409
Response:
```json
{
  "error" : "CONFLICT",
  "message" : "cannot change transfer from CANCELLED to CANCELLED"
}
```

### S6 — Create with sendAmount = 5000
Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 6fa459ea-ee8a-4ca4-894e-db77e160355e

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"5000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 400
Response:
```json
{
  "error" : "VALIDATION_ERROR",
  "message" : "sendAmount must be at least 10000"
}
```

## Part 2

### S7 — C002 submits 500000 until the daily limit (3,000,000) is exceeded

Transfers 1-5 (same request shape, own Idempotency-Key each):

- `POST /api/transfers` with `Idempotency-Key: s7-c002-1`, sendAmount 500000 → 201
- `POST /api/transfers` with `Idempotency-Key: s7-c002-2`, sendAmount 500000 → 201
- `POST /api/transfers` with `Idempotency-Key: s7-c002-3`, sendAmount 500000 → 201
- `POST /api/transfers` with `Idempotency-Key: s7-c002-4`, sendAmount 500000 → 201
- `POST /api/transfers` with `Idempotency-Key: s7-c002-5`, sendAmount 500000 → 201

Transfer 6 — last success (day total = 3,000,000):

Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s7-c002-6

{"customerId":"C002","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response header: `Idempotent-Replayed: false`
Response:
```json
{
  "transferId" : "fbd051b6-bb23-415c-b65e-58fe5c28b27c",
  "customerId" : "C002",
  "status" : "REQUESTED",
  "sendCurrency" : "KRW",
  "sendAmount" : "500000",
  "fee" : "5000",
  "totalDebit" : "505000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "20600.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "createdAt" : "2026-09-30T09:56:30.010Z",
  "updatedAt" : "2026-09-30T09:56:30.010Z"
}
```

Transfer 7 — rejected:

Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s7-c002-7

{"customerId":"C002","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 422
Response:
```json
{
  "error" : "LIMIT_EXCEEDED",
  "message" : "Daily limit of 3000000 KRW exceeded: already used 3000000, requested 500000"
}
```

### S8 — Same callback (same eventId) twice

Create and send a transfer (partner accepts, status stays SENDING):

Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s8-c003-1

{"customerId":"C003","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response header: `Idempotent-Replayed: false`
Response:
```json
{
  "transferId" : "9f0ee686-5c58-4ad1-a482-4bc4a095cce8",
  "customerId" : "C003",
  "status" : "REQUESTED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "createdAt" : "2026-09-30T09:56:32.007Z",
  "updatedAt" : "2026-09-30T09:56:32.007Z"
}
```

Request:
```http
POST /api/transfers/9f0ee686-5c58-4ad1-a482-4bc4a095cce8/send
```
Status: 200
Response:
```json
{
  "transferId" : "9f0ee686-5c58-4ad1-a482-4bc4a095cce8",
  "customerId" : "C003",
  "status" : "SENDING",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "partnerRef" : "PR0000000009",
  "createdAt" : "2026-09-30T09:56:32.007Z",
  "updatedAt" : "2026-09-30T09:56:33.379Z"
}
```

First callback:

Request:
```http
POST /api/callbacks/partner
Content-Type: application/json

{"partnerRef":"PR0000000009","eventId":"evt_0001","status":"COMPLETED"}
```
Status: 200
Response:
```json
{
  "transferId" : "9f0ee686-5c58-4ad1-a482-4bc4a095cce8",
  "customerId" : "C003",
  "status" : "COMPLETED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "partnerRef" : "PR0000000009",
  "createdAt" : "2026-09-30T09:56:32.007Z",
  "updatedAt" : "2026-09-30T09:56:34.614Z"
}
```

Second callback (same eventId):

Request:
```http
POST /api/callbacks/partner
Content-Type: application/json

{"partnerRef":"PR0000000009","eventId":"evt_0001","status":"COMPLETED"}
```
Status: 200
Response:
```json
{
  "transferId" : "9f0ee686-5c58-4ad1-a482-4bc4a095cce8",
  "customerId" : "C003",
  "status" : "COMPLETED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "partnerRef" : "PR0000000009",
  "createdAt" : "2026-09-30T09:56:32.007Z",
  "updatedAt" : "2026-09-30T09:56:34.614Z"
}
```

Final state:

Request:
```http
GET /api/transfers/9f0ee686-5c58-4ad1-a482-4bc4a095cce8
```
Status: 200
Response:
```json
{
  "transferId" : "9f0ee686-5c58-4ad1-a482-4bc4a095cce8",
  "customerId" : "C003",
  "status" : "COMPLETED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "partnerRef" : "PR0000000009",
  "createdAt" : "2026-09-30T09:56:32.007Z",
  "updatedAt" : "2026-09-30T09:56:34.614Z"
}
```

### S9 — Partner fails on every attempt

Request:
```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s9-c003-1

{"customerId":"C003","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response header: `Idempotent-Replayed: false`
Response:
```json
{
  "transferId" : "2a917432-6c6b-413b-b6c9-cb8a1c28453d",
  "customerId" : "C003",
  "status" : "REQUESTED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "createdAt" : "2026-09-30T09:56:37.568Z",
  "updatedAt" : "2026-09-30T09:56:37.568Z"
}
```

Request:
```http
POST /api/transfers/2a917432-6c6b-413b-b6c9-cb8a1c28453d/send
```
Status: 200
Response:
```json
{
  "transferId" : "2a917432-6c6b-413b-b6c9-cb8a1c28453d",
  "customerId" : "C003",
  "status" : "FAILED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "partnerRef" : "PR0000000010",
  "failureReason" : "Partner rejected the request (3 of 3 attempts failed)",
  "createdAt" : "2026-09-30T09:56:37.568Z",
  "updatedAt" : "2026-09-30T09:56:39.954Z"
}
```

Retry log (application console, partnerRef PR0000000010):
```text
2026-09-30T16:56:38.924+07:00  INFO 8948 --- [remittance] [nio-8080-exec-8] c.f.remittance.partner.PartnerSender     : partner attempt=1/3 partnerRef=PR0000000010 outcome=REJECTED
2026-09-30T16:56:39.344+07:00  INFO 8948 --- [remittance] [nio-8080-exec-8] c.f.remittance.partner.PartnerSender     : partner attempt=2/3 partnerRef=PR0000000010 outcome=REJECTED
2026-09-30T16:56:39.954+07:00  INFO 8948 --- [remittance] [nio-8080-exec-8] c.f.remittance.partner.PartnerSender     : partner attempt=3/3 partnerRef=PR0000000010 outcome=REJECTED
```

Final state:

Request:
```http
GET /api/transfers/2a917432-6c6b-413b-b6c9-cb8a1c28453d
```
Status: 200
Response:
```json
{
  "transferId" : "2a917432-6c6b-413b-b6c9-cb8a1c28453d",
  "customerId" : "C003",
  "status" : "FAILED",
  "sendCurrency" : "KRW",
  "sendAmount" : "100000",
  "fee" : "3000",
  "totalDebit" : "103000",
  "receiveCurrency" : "PHP",
  "receiveAmount" : "4120.00",
  "fxRate" : "0.0412",
  "recipientName" : "Juan Dela Cruz",
  "partnerRef" : "PR0000000010",
  "failureReason" : "Partner rejected the request (3 of 3 attempts failed)",
  "createdAt" : "2026-09-30T09:56:37.568Z",
  "updatedAt" : "2026-09-30T09:56:39.954Z"
}
```

