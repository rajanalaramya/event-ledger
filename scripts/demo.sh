#!/usr/bin/env bash
set -euo pipefail

gateway_url="${GATEWAY_URL:-http://localhost:8080}"
account_url="${ACCOUNT_URL:-http://localhost:8081}"
account_id="${ACCOUNT_ID:-demo-account}"
prefix="demo-$(date +%s)"
now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
older="$(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-5M +%Y-%m-%dT%H:%M:%SZ)"

post_event() {
  local event_id="$1" type="$2" amount="$3" timestamp="$4"
  curl --fail-with-body --silent --show-error \
    --header 'Content-Type: application/json' \
    --data "{\"eventId\":\"${event_id}\",\"accountId\":\"${account_id}\",\"type\":\"${type}\",\"amount\":${amount},\"currency\":\"USD\",\"eventTimestamp\":\"${timestamp}\"}" \
    "${gateway_url}/events"
  printf '\n'
}

echo "Submitting credit"
post_event "${prefix}-credit" CREDIT 100.00 "$now"

echo "Submitting debit"
post_event "${prefix}-debit" DEBIT 25.00 "$now"

echo "Submitting duplicate debit (expect Idempotent-Replay: true)"
post_event "${prefix}-debit" DEBIT 25.00 "$now"

echo "Submitting an out-of-order credit"
post_event "${prefix}-out-of-order" CREDIT 10.00 "$older"

echo "Balance:"
curl --fail-with-body --silent --show-error "${account_url}/accounts/${account_id}/balance"
printf '\nEvents:\n'
curl --fail-with-body --silent --show-error "${gateway_url}/events?account=${account_id}&size=100"
printf '\n'
