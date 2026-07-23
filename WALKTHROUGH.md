# Event Ledger interview walkthrough (10–15 minutes)

## 1. Open with the problem and boundaries (1 minute)

“Event Ledger receives credit and debit events. Its core promise is at-most-once balance mutation despite duplicate client calls, concurrent requests, and a downstream response that disappears after commit. It has a Gateway public API on 8080 and an Account Service on 8081. Each owns a separate H2 database and they communicate only over REST.”

Emphasize the boundary: this is not a claim of distributed exactly-once processing. It is a practical idempotent REST workflow with an atomic local side-effect boundary.

## 2. Show the architecture and ownership (1 minute)

Point to the README diagram, then describe:

* `contract` contains versioned DTOs, validation helpers, state transitions, and fingerprinting only.
* Gateway owns external event persistence, processing state, idempotency response semantics, resilience, and event lookup/listing.
* Account Service owns balances and the applied transaction record that proves a balance change happened.
* Gateway’s H2 file and Account’s H2 file are separate. Docker Compose preserves them in `gateway-data` and `account-data`.

## 3. Walk through the happy path (1–2 minutes)

Submit:

```bash
curl -i -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  --data '{"eventId":"walkthrough-credit","accountId":"walkthrough","type":"CREDIT","amount":100.00,"currency":"USD","eventTimestamp":"2026-07-23T12:00:00Z"}'
```

Explain the three Gateway transaction boundaries:

1. Insert the event with status `RECEIVED` and its fingerprint.
2. Claim it as `PROCESSING`.
3. After Account responds, mark it `APPLIED`.

Between steps 2 and 3, Gateway invokes Account Service over REST—never while a Gateway transaction is held. Account performs the account balance update and `applied_transactions` insert in one transaction and returns `201`.

## 4. Demonstrate duplicate and conflicting IDs (1 minute)

Repeat the exact request. Gateway returns `200 OK` with `Idempotent-Replay: true` once the stored event is `APPLIED`; the balance does not change.

Change an included fingerprint field, such as `amount`, while retaining `eventId`. Gateway returns `409 Conflict`. Explain that the fingerprint includes canonical event ID, account ID, type, normalized amount, currency, Instant, and sorted metadata. This makes JSON key order and `10.0` vs `10.00` equivalent, while payload changes are conflicts.

Call out two layers:

* Gateway’s `events` primary key controls the public event workflow.
* Account’s unique `applied_transactions.event_id` controls the actual balance side effect.

The database constraints, not application pre-checks, are authoritative under races.

## 5. Discuss out-of-order events (1 minute)

Run `scripts/demo.ps1` or `scripts/demo.sh`, which posts an event timestamped five minutes earlier after newer events. The history/listing is sorted by `eventTimestamp`, then `eventId`, but application is in arrival order. Credits/debits change the balance even when out of order because those arithmetic operations are order-independent here. For domain events that depend on temporal order, this design would need a sequencing/reconciliation policy.

## 6. Explain outage and uncertain commit (2 minutes)

Run `scripts/simulate-outage.ps1` (or `.sh`). It stops `account-service`, submits an event, verifies the Gateway returns `503`, reads the stored event, restarts the service, and resubmits.

Then describe the harder case: Account has committed, but its HTTP response is lost. Gateway cannot know whether the downstream mutation happened, so it records `RETRYABLE_FAILURE` and returns `503`. On resubmission, it retries Account with the exact same event ID/fingerprint. Account finds the already-applied transaction and replays it without mutating the balance again. Gateway can safely record `APPLIED`.

This is why the implementation says “at-most-once balance mutation,” not “exactly once.”

## 7. Cover concurrency control (1 minute)

For Gateway, event state has an optimistic `@Version` and the primary key prevents two initial inserts. A caller that loses the claim race sees `PROCESSING`/`503` rather than duplicating the downstream call.

For Account, the account has optimistic versioning. Balance conflicts retry local transactions up to 25 times. The unique applied-transaction key handles a duplicate event; a matching fingerprint replays, a different one conflicts. The balance update and applied record commit atomically in the same local transaction.

`scripts/verify-idempotency.ps1` / `.sh` sends ten concurrent copies and expects one `10.00` balance change.

## 8. Cover resilience configuration (1–2 minutes)

Gateway’s Account client uses a 500 ms connect timeout and 2 s read timeout. It retries up to three attempts with 200 ms exponential backoff (multiplier 2) and 0.5 randomization. Only I/O, timeout, and HTTP 502/503/504 failures are retryable—409 and generic 500 are not.

The circuit breaker observes a sliding window of 10, needs 5 calls, opens at 50% failure rate, remains open for 5 seconds, and allows 3 half-open probes. A bulkhead limits 20 concurrent downstream calls without waiting. Gateway also applies an in-memory global bucket of 100 `POST /events` tokens, refilled at 100/s.

Mention retry amplification explicitly: a caller retry plus three internal attempts can pressure an outage. The breaker, bulkhead, jitter, rate limit, and `Retry-After: 1` mitigate but do not eliminate it.

## 9. Show observability and health (1 minute)

Start optional tooling:

```bash
docker compose --profile observability up --build
```

Show:

* Metrics: `curl http://localhost:8080/actuator/prometheus`; point out `event_ledger_events_*`, duplicate/conflict/failure counters, and processing timer.
* Tracing: Jaeger at http://localhost:16686. Micrometer OTel sampling is 1.0; incoming `traceparent` is forwarded by Gateway’s Account client.
* Logs: trace/span IDs are in the logging pattern. Clarify this is text pattern enrichment, not JSON logging.
* Health: `/health` is a process convenience endpoint. Actuator liveness is process-oriented; Gateway readiness includes Account Service health, so a downstream outage can make Gateway unready without making its JVM unhealthy.

## 10. Close with tradeoffs and production changes (1–2 minutes)

State what is intentionally absent: authentication/authorization/TLS, distributed rate limiting, background retry worker, outbox/broker, dead-letter operation, HA database, and full JSON logging.

Production evolution priorities are: PostgreSQL and backup/retention; OAuth2/JWT or mTLS plus secrets/TLS; a durable retry/outbox worker with reconciliation for stuck `PROCESSING`; distributed per-principal rate limits; SLO/alerting; immutable/compliance-grade ledger modeling; and dependency/image scanning in CI.

Finish with the governing rationale: database constraints are the race authority; no remote call is made in a local transaction; uncertainty is resolved by an idempotent local side-effect record rather than pretending REST gives exactly-once delivery.
