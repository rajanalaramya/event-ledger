#!/usr/bin/env bash
set -euo pipefail

gateway_url="${GATEWAY_URL:-http://localhost:8080}"
account_url="${ACCOUNT_URL:-http://localhost:8081}"
account_id="idempotency-$(date +%s)"
event_id="${account_id}-credit"
timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
payload="{\"eventId\":\"${event_id}\",\"accountId\":\"${account_id}\",\"type\":\"CREDIT\",\"amount\":10.00,\"currency\":\"USD\",\"eventTimestamp\":\"${timestamp}\"}"
responses_dir="$(mktemp -d)"
trap 'rm -rf "$responses_dir"' EXIT

echo "Submitting 10 concurrent requests with eventId ${event_id}"
for request in $(seq 1 10); do
  (
    curl --fail-with-body --silent --show-error \
      --header 'Content-Type: application/json' \
      --data "$payload" \
      "${gateway_url}/events" >"${responses_dir}/${request}.json"
  ) &
done
wait

echo "Gateway responses:"
cat "${responses_dir}"/*.json
printf '\nBalance (must be 10.00):\n'
balance_response="$(curl --fail-with-body --silent --show-error "${account_url}/accounts/${account_id}/balance")"
printf '%s\n' "$balance_response"
balance="$(printf '%s' "$balance_response" | sed -nE 's/.*"balance":([0-9.]+).*/\1/p')"
if [[ "$balance" != "10.00" ]]; then
  echo "Expected one applied credit and a 10.00 balance; got ${balance:-no balance}." >&2
  exit 1
fi

echo "Idempotency verified: the concurrent requests produced one applied balance change."
