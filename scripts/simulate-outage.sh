#!/usr/bin/env bash
set -euo pipefail

gateway_url="${GATEWAY_URL:-http://localhost:8080}"
account_id="${ACCOUNT_ID:-outage-account}"
event_id="outage-$(date +%s)"
timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "Stopping account-service"
docker compose stop account-service

echo "Submitting event while account-service is stopped (expect HTTP 503)"
status="$(
  curl --silent --show-error --output /tmp/event-ledger-outage-response.json --write-out '%{http_code}' \
    --header 'Content-Type: application/json' \
    --data "{\"eventId\":\"${event_id}\",\"accountId\":\"${account_id}\",\"type\":\"CREDIT\",\"amount\":10.00,\"currency\":\"USD\",\"eventTimestamp\":\"${timestamp}\"}" \
    "${gateway_url}/events" || true
)"
cat /tmp/event-ledger-outage-response.json
printf '\nHTTP status: %s\n' "$status"
if [[ "$status" != "503" ]]; then
  echo "Expected HTTP 503 while account-service is unavailable." >&2
  exit 1
fi

echo "Reading the persisted gateway event"
curl --fail-with-body --silent --show-error "${gateway_url}/events/${event_id}"
printf '\n'

echo "Starting account-service and waiting for it to become healthy"
docker compose start account-service
until [[ "$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q account-service)")" == "healthy" ]]; do
  sleep 2
done

echo "Resubmitting the event"
curl --fail-with-body --silent --show-error \
  --header 'Content-Type: application/json' \
  --data "{\"eventId\":\"${event_id}\",\"accountId\":\"${account_id}\",\"type\":\"CREDIT\",\"amount\":10.00,\"currency\":\"USD\",\"eventTimestamp\":\"${timestamp}\"}" \
  "${gateway_url}/events"
printf '\n'
