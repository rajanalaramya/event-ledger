# Event Ledger — Implementation Plan and delivery status

## Delivered architecture

The repository is a Java 21 / Maven multi-module reactor:

```text
event-ledger/
  contract/            Versioned DTOs, validation, fingerprinting, processing states
  account-service/     Account/balance authority (8081), H2 + Flyway
  event-gateway/       Public event workflow API (8080), H2 + Flyway
  observability/       Prometheus, OTel Collector, Grafana provisioning
  scripts/             Demo, outage, and idempotency scripts (.ps1 and .sh)
```

## Phase checklist

| Phase | Status | Delivered / known test status |
|---|---|---|
| 1. Assessment | Complete | Repository is implemented; prior “empty repository” assessment is superseded. |
| 2. Contracts, Flyway, validation | Complete | Versioned contract DTOs, SHA-256 canonical fingerprints, validation, state transitions, and per-service Flyway schemas are present. Contract unit tests are in the reactor. |
| 3. Account correctness | Complete | Atomic balance + applied-transaction persistence, unique event ID, replay/conflict behavior, optimistic account locking, and bounded local retry are implemented. Account integration tests are present. |
| 4. Gateway workflow | Complete | Public event API, persisted state machine, duplicate/conflict behavior, optimistic claims, and ordered listing are implemented. Gateway integration tests cover happy, duplicate, conflict, and ordering behavior. |
| 5. Resilience | Complete | Timeouts, retry classification/backoff/jitter, circuit breaker, bulkhead, rate limiting, and WireMock integration coverage are present. |
| 6. Observability and health | Complete | Actuator health/probes/Prometheus, application metrics, Micrometer OTel, trace/span log pattern fields, and Gateway Account readiness contributor are configured. Logging is pattern-enriched text, not full JSON logging. |
| 7. Failure and concurrency | Complete | Retryable outage state, uncertain-commit recovery through Account idempotency, optimistic locking, and concurrent idempotency scripts/tests are implemented. |
| 8. Docker and operations | Complete | Non-root Dockerfiles, Compose service health checks, named volumes, and optional `observability` profile are defined. Runtime Compose health has not yet been recorded in this file. |
| 9. Documentation and verification | Complete | README, ADRs, interview walkthrough, and submission checklist document actual behavior. Compose configuration was validated; Maven could not run because `mvn` is absent from this environment and no wrapper is committed. |

## Explicit non-goals / deferred production work

* No XA/distributed transaction, saga, outbox, broker, or automatic retry worker.
* No built-in authentication, authorization, TLS, secrets manager, or distributed rate limit.
* H2 in PostgreSQL mode is used for local execution, not production HA/storage validation.
* Full structured JSON/logstash logging is not implemented; trace and span IDs are included in the text logging pattern.

## Verification record

| Check | Status |
|---|---|
| `mvn clean verify` | Not run: `mvn` is not installed/on `PATH` in the current environment, and the repository has no Maven wrapper. |
| `docker compose config --quiet` | Passed (2026-07-23) |
| `docker compose up --build` | Not run in this documentation pass |
| `docker compose --profile observability up --build` | Not run in this documentation pass |

Run these commands from the repository root:

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
docker compose config
docker compose up --build
```
