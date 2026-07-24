# Failure scenarios

## Account Service outage

1. `docker compose stop account-service`
2. `POST /events` → `503` + gateway event stored as `RETRYABLE_FAILURE`
3. `GET /events/{id}` and `GET /events?account=` continue to work from Gateway H2
4. `docker compose start account-service`
5. Resubmit the same payload → applied safely (Account idempotency)

Script: `scripts/simulate-outage.sh` / `scripts/simulate-outage.ps1`

## Uncertain commit (response lost after Account success)

Covered by WireMock test `uncertainCommit_timeoutThenReplay`:

1. Account applies the transaction
2. Gateway observes timeout/fault
3. Client resubmits
4. Account returns replay; balance changed once

## Concurrent duplicate submissions

Covered by gateway and account concurrency tests plus `scripts/verify-idempotency.*`.

Expected: one gateway row, one account transaction row, balance changed once.

## Circuit breaker open

Repeated distinct failures while Account is down eventually open the `accountService` breaker. Inspect:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep resilience4j_circuitbreaker
```

## Optional evidence folder

Place real screenshots under `docs/images/` only when captured from a live run (CI, Jaeger, Prometheus). Do not add fabricated images.
