# Submission checklist

## Build and tests

- [x] Maven multi-module reactor: `contract`, `account-service`, `event-gateway`.
- [x] Java 21 and Maven 3.9+ are enforced by Maven Enforcer.
- [ ] `mvn clean verify` could not run in the current documentation pass: `mvn` is not installed/on `PATH`, and no Maven wrapper is committed (see `IMPLEMENTATION_PLAN.md`).
- [ ] `docker compose up --build` verified healthy in the current documentation pass.
- [ ] `docker compose --profile observability up --build` verified with Prometheus and Jaeger in the current documentation pass.

## Functional behavior

- [x] Gateway public REST API on port 8080.
- [x] Account REST API on port 8081.
- [x] Separate Gateway and Account H2 data stores, Flyway migrations, and no shared runtime state.
- [x] Valid credit/debit events create/update an account balance.
- [x] Negative balances are permitted by the implemented balance policy.
- [x] `POST /events` returns `201` for a new event and `200` with `Idempotent-Replay: true` for an applied duplicate.
- [x] Reusing an event ID with a distinct fingerprint returns `409`.
- [x] Invalid body/request validation returns RFC 9457-style `400` responses.
- [x] Gateway supports event lookup and ordered account event listing.
- [x] Account supports balance and account/history lookup.
- [x] Out-of-order timestamps are preserved and sorted in queries; they do not block application.

## Correctness and failure behavior

- [x] Gateway event primary key and Account applied-transaction unique key enforce two-level idempotency.
- [x] SHA-256 request fingerprint includes canonical business fields and metadata.
- [x] Gateway state machine persists `RECEIVED`, `PROCESSING`, `APPLIED`, `RETRYABLE_FAILURE`, and `PERMANENT_FAILURE`.
- [x] Gateway uses optimistic locking for event claims.
- [x] Account uses optimistic locking with bounded retry for concurrent balance updates.
- [x] Account balance update and applied-transaction record are in one local transaction.
- [x] No Gateway database transaction is held across the Account REST call.
- [x] Lost Account response after commit is safe to retry due to Account-side idempotency.
- [x] Account outage records retryable Gateway state and returns `503` with `Retry-After`.
- [x] Optional internal retry endpoint is disabled by default and only accepts `RETRYABLE_FAILURE`.

## Resilience, observability, and operations

- [x] Gateway has configured connect/read timeouts, retry, circuit breaker, and bulkhead for Account Service.
- [x] Retry predicate limits retries to I/O/timeouts and 502/503/504.
- [x] Gateway rate limits `POST /events` and returns `429`/`Retry-After`.
- [x] Prometheus endpoint and application metrics are exposed.
- [x] Micrometer/OpenTelemetry tracing and trace/span log pattern fields are configured.
- [x] Liveness and readiness Actuator probes are enabled.
- [x] Gateway readiness includes Account Service health.
- [x] Docker images run as non-root and Compose health checks use `/health`.
- [x] Compose observability profile defines `prometheus`, `grafana`, `otel-collector`, and `jaeger`.
- [x] Demo, outage, and concurrent-idempotency scripts exist in both PowerShell and shell variants.

## Documentation and security

- [x] README documents actual API behavior, status codes, resilience settings, metrics, service names, run commands, and known tradeoffs.
- [x] ADR-001 through ADR-010 are captured in `DECISIONS.md`.
- [x] `WALKTHROUGH.md` provides a 10–15 minute interview path.
- [x] Documentation distinguishes log-pattern tracing enrichment from JSON/logstash logging.
- [x] Documentation identifies external requirements: authentication, authorization, TLS, secrets, scanning, and production database/operations.
- [x] Dependency and container scanning commands are documented; no scanner is falsely represented as configured.
