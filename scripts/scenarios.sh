#!/usr/bin/env bash
# Runs scenarios S1-S9 in order and prints the requests and responses as Markdown (the format of RESULT.md).
#
# Needs a freshly started app (in-memory DB), because S8/S9 depend on the internal transfer ids:
#   ./mvnw spring-boot:run > app.log      (terminal 1)
#   APP_LOG=app.log ./scripts/scenarios.sh (terminal 2)
# APP_LOG is optional; when set, S9 also prints the partner retry log lines from it.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
APP_LOG="${APP_LOG:-}"
RESPONSE_BODY=""

# call METHOD PATH [IDEMPOTENCY_KEY] [JSON_BODY]
call() {
  local method=$1 path=$2 key=${3:-} body=${4:-}
  local args=(-sS -i -X "$method" "$BASE_URL$path")

  echo 'Request:'
  echo '```http'
  echo "$method $path"
  if [ -n "$body" ]; then
    echo 'Content-Type: application/json'
    args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  if [ -n "$key" ]; then
    echo "Idempotency-Key: $key"
    args+=(-H "Idempotency-Key: $key")
  fi
  if [ -n "$body" ]; then
    echo
    echo "$body"
  fi
  echo '```'

  local raw status replayed
  raw=$(curl "${args[@]}" | tr -d '\r')
  status=$(printf '%s\n' "$raw" | head -1 | awk '{print $2}')
  replayed=$(printf '%s\n' "$raw" | sed '/^$/q' | grep -i '^Idempotent-Replayed:' || true)
  RESPONSE_BODY=$(printf '%s\n' "$raw" | sed '1,/^$/d')

  echo "Status: $status"
  if [ -n "$replayed" ]; then
    echo "Response header: \`$replayed\`"
  fi
  echo 'Response:'
  echo '```json'
  printf '%s\n' "$RESPONSE_BODY"
  echo '```'
  echo
}

# field NAME -> value of a string field in the last response body
field() {
  printf '%s\n' "$RESPONSE_BODY" | sed -n "s/.*\"$1\" : \"\([^\"]*\)\".*/\1/p" | head -1
}

create_body() { # CUSTOMER AMOUNT
  printf '{"customerId":"%s","sendCurrency":"KRW","sendAmount":"%s","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}' "$1" "$2"
}

echo '## Part 1'
echo

echo '### S1 — Create a transfer for C001, amount 500000'
call POST /api/transfers 7c9e6679-7425-40de-944b-e07fc1f90ae7 "$(create_body C001 500000)"

echo '### S2 — Retry S1 (same Idempotency-Key, same body)'
call POST /api/transfers 7c9e6679-7425-40de-944b-e07fc1f90ae7 "$(create_body C001 500000)"

echo '### S3 — Same Idempotency-Key as S1, amount 600000'
call POST /api/transfers 7c9e6679-7425-40de-944b-e07fc1f90ae7 "$(create_body C001 600000)"

echo '### S4 — Create another transfer, then cancel it'
call POST /api/transfers 1b4e28ba-2fa1-41d2-883f-0016d3cca427 "$(create_body C001 100000)"
S4_ID=$(field transferId)
call POST "/api/transfers/$S4_ID/cancel"

echo '### S5 — Cancel the same transfer again'
call POST "/api/transfers/$S4_ID/cancel"

echo '### S6 — Create with sendAmount = 5000'
call POST /api/transfers 6fa459ea-ee8a-4ca4-894e-db77e160355e "$(create_body C001 5000)"

echo '## Part 2'
echo

echo '### S7 — C002 submits 500000 until the daily limit (3,000,000) is exceeded'
echo
echo 'Transfers 1-5 (same request shape, own Idempotency-Key each):'
echo
for i in 1 2 3 4 5; do
  status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/transfers" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: s7-c002-$i" --data "$(create_body C002 500000)")
  echo "- \`POST /api/transfers\` with \`Idempotency-Key: s7-c002-$i\`, sendAmount 500000 → $status"
done
echo
echo 'Transfer 6 — last success (day total = 3,000,000):'
echo
call POST /api/transfers s7-c002-6 "$(create_body C002 500000)"
echo 'Transfer 7 — rejected:'
echo
call POST /api/transfers s7-c002-7 "$(create_body C002 500000)"

echo '### S8 — Same callback (same eventId) twice'
echo
echo 'Create and send a transfer (partner accepts, status stays SENDING):'
echo
call POST /api/transfers s8-c003-1 "$(create_body C003 100000)"
S8_ID=$(field transferId)
call POST "/api/transfers/$S8_ID/send"
S8_REF=$(field partnerRef)
echo 'First callback:'
echo
call POST /api/callbacks/partner "" "{\"partnerRef\":\"$S8_REF\",\"eventId\":\"evt_0001\",\"status\":\"COMPLETED\"}"
echo 'Second callback (same eventId):'
echo
call POST /api/callbacks/partner "" "{\"partnerRef\":\"$S8_REF\",\"eventId\":\"evt_0001\",\"status\":\"COMPLETED\"}"
echo 'Final state:'
echo
call GET "/api/transfers/$S8_ID"

echo '### S9 — Partner fails on every attempt'
echo
call POST /api/transfers s9-c003-1 "$(create_body C003 100000)"
S9_ID=$(field transferId)
call POST "/api/transfers/$S9_ID/send"
S9_REF=$(field partnerRef)
if [ -n "$APP_LOG" ] && [ -f "$APP_LOG" ]; then
  echo "Retry log (application console, partnerRef $S9_REF):"
  echo '```text'
  grep "partnerRef=$S9_REF " "$APP_LOG" || true
  echo '```'
  echo
fi
echo 'Final state:'
echo
call GET "/api/transfers/$S9_ID"
