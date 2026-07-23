# Official Assignment Alignment

Source PDF: `Assignment - Senior Software Engineer AI- Final[77].pdf` (Schwab take-home).

The repository intentionally **meets and exceeds** the official PDF. The earlier workspace brief added production hardening (two-level fingerprint conflicts, Flyway, Prometheus, full Resilience4j stack, ADRs, etc.). Those extras remain; this note maps PDF requirements to the implementation.

## PDF requirements → implementation

| PDF requirement | Status | Where |
|---|---|---|
| Two services, sync REST | Done | `event-gateway` :8080, `account-service` :8081 |
| Separate embedded DBs | Done | Separate H2 files / Docker volumes |
| Idempotency on `eventId` | Done | Gateway unique PK + Account unique `event_id` |
| Out-of-order listings | Done | `ORDER BY event_timestamp, event_id` |
| Balance = credits − debits | Done | `BalanceCalculator` |
| Validation + meaningful HTTP errors | Done | Bean Validation + ProblemDetail |
| Trace ID generate/propagate/log | Done | Micrometer OTel + W3C `traceparent` |
| JSON structured logs | Done | `logback-spring.xml` + LogstashEncoder |
| `GET /health` with diagnostics | Done | Service status + database connectivity |
| ≥1 resiliency pattern | Done | Circuit breaker **and** timeout/retry/bulkhead |
| Graceful degradation | Done | POST→503; Gateway GETs still serve local data |
| Docker Compose preferred | Done | `docker compose up --build` |
| Automated tests (`mvn test`) | Done | 31+ tests including WireMock resiliency + tracing |
| README | Done | Architecture, setup, run, tests, resiliency choice |
| Commit history (not one squash) | Done | Phase-aligned commits |

## Bonus covered

OpenTelemetry Collector + Jaeger profile, Prometheus, retry+jitter, rate limiting, contract module + compatibility test. Async local queue when Account is down is **not** implemented; failed events stay `RETRYABLE_FAILURE` for client or internal retry.
