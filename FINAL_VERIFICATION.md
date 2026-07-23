# Final Verification Report

**Date:** 2026-07-23  
**Repository:** `C:\schwab` (Event Ledger)  
**Toolchain:** Temurin JDK 21.0.11, Apache Maven 3.9.11, Docker 29.4.2

## Commands run and results

| Command | Result |
|---------|--------|
| `mvn clean verify` | **PASS** — BUILD SUCCESS |
| `docker compose config` | **PASS** |
| `docker compose up --build -d` | **PASS** (after Dockerfile reactor-module fix) |
| `docker compose ps` | Both services **healthy** |
| `curl http://localhost:8080/health` | **200** UP |
| `curl http://localhost:8081/health` | **200** UP |
| Manual curl demo (credit/debit/dup/conflict/OOO) | **PASS** |
| Account outage + read + recovery | **PASS** |
| Concurrent duplicate race (10 parallel POSTs) | **PASS** |
| Prometheus custom + Resilience4j metrics | **PASS** (observed) |
| Traceparent accepted on Gateway | **PASS** (ProblemDetail/response includes traceId; WireMock IT covers propagation) |

## Test counts (`mvn clean verify`)

| Module | Tests | Failures | Errors | Skipped |
|--------|------:|---------:|-------:|--------:|
| contract | 8 | 0 | 0 | 0 |
| account-service | 8 | 0 | 0 | 0 |
| event-gateway | 15 | 0 | 0 | 0 |
| **Total** | **31** | **0** | **0** | **0** |

Gateway suite includes WireMock scenarios: happy path, duplicate replay, conflict, out-of-order listing, validation ProblemDetail, outage/read, recovery, timeout, no-retry-on-409, uncertain-commit, circuit breaker, traceparent, concurrent duplicate.

Account suite includes credit/debit/replay/conflict/currency mismatch, 70-op concurrency balance, concurrent identical eventId, unique constraint, chronological history, health.

## Docker service status (after verification)

```text
event-ledger-account-service-1   Up (healthy)   0.0.0.0:8081->8081/tcp
event-ledger-event-gateway-1     Up (healthy)   0.0.0.0:8080->8080/tcp
```

## Sample API results

### Happy path + ordering + balance

- CREDIT `evt-demo-001` +150 → **201 Created**, `APPLIED`
- DEBIT `evt-demo-002` −40 → **201 Created**
- Duplicate CREDIT → **200** + `Idempotent-Replay: true`
- Conflicting payload same `eventId` → **409 Conflict**
- Out-of-order CREDIT `evt-demo-000` +10 (earlier timestamp) → **201**
- `GET /events?account=acct-demo` order: `evt-demo-000`, `evt-demo-001`, `evt-demo-002` (by `eventTimestamp`, then `eventId`)
- Balance after credit/debit/OOO: **120.00** (= 150 − 40 + 10)

### Duplicate safety (concurrent)

10 parallel POSTs of identical `evt-race-1`:

- HTTP codes: **one 201**, **nine 200**
- Gateway events for account: **1 row**
- Account transactions: **1 row**
- Balance: **25.00** (changed once)

### Failure recovery

1. Stopped `account-service`
2. New POST → **503** `DOWNSTREAM_UNAVAILABLE` + `Retry-After: 1`; event retained as retryable
3. `GET /events/evt-demo-001` still **200** while Account down
4. Restarted Account; resubmitted outage event → **APPLIED** (`attemptCount: 2`)
5. Final balance **125.00** (= 120 + 5)

### Metrics result

`/actuator/prometheus` on Gateway exposed:

- `event_ledger_events_received_total`
- `event_ledger_events_applied_total`
- `event_ledger_duplicate_replays_total`
- `event_ledger_idempotency_conflicts_total`
- `event_ledger_downstream_failures_total`
- `event_ledger_processing_duration_seconds`
- `resilience4j_circuitbreaker_*` (buffered failed/successful calls observed after outage)

### Trace-propagation result

- Automated: WireMock IT asserts `traceparent` reaches Account stub
- Manual: Gateway accepts inbound `traceparent`; ProblemDetail/`traceId` populated from MDC

## Unresolved warnings / gaps

1. **Flyway H2 version warning** — H2 2.2.224 newer than Flyway’s tested H2; migrations still applied successfully.
2. **JSON logging** — structured fields via logging pattern + MDC (`traceId`/`spanId`); not a full Logstash JSON encoder dependency (documented).
3. **Optional observability profile** (`prometheus`/`grafana`/`jaeger`/`otel-collector`) — compose config validates; full Jaeger UI walkthrough not run in this session (apps run without it).
4. **Gateway `/health` enrichment** (`service`/`version`) patched after image build; running container still returns the earlier slim payload until image rebuild. Actuator probes remain available.
5. **True two-process Testcontainers e2e** — covered by Docker Compose manual verification + WireMock gateway ITs + Account ITs rather than a single Failsafe Testcontainers class.
6. **Hibernate SQL unique-violation ERROR lines** appear in test logs during intentional race tests; expected and asserted.

## Functionality not verified in this environment

- Grafana dashboards / Jaeger UI end-to-end browsing
- OWASP dependency-check scan execution (instructions in README; CI notes scanning)
- Production PostgreSQL compatibility (H2 MODE=PostgreSQL only — explicitly not claimed as proof)

## Suggested Git commits (phase-aligned; not auto-committed)

```text
docs: document implementation plan and initial repository assessment
feat: add versioned contracts and safe persistence model
feat: implement idempotent and concurrency-safe account transactions
feat: implement gateway event lifecycle and idempotent replay
feat: add bounded downstream resilience and graceful degradation
feat: add distributed tracing structured logs and metrics
test: cover duplicate races partial failures and concurrent updates
build: add containerized runtime and observability stack
docs: add architecture decisions walkthrough and submission guide
```

## Verdict

The Event Ledger take-home meets the required architecture and reliability bar: two independent services, defense-in-depth idempotency, explicit Gateway state machine, bounded resilience, observability endpoints, Docker Compose runtime, and automated tests including WireMock failure injection. All executed `mvn clean verify` tests passed; Docker demo confirmed duplicate safety, chronological ordering, outage read-path degradation, and safe recovery.
