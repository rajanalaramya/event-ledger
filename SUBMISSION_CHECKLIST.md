# Submission checklist

## Build and tests

- [x] Maven multi-module reactor: `contract`, `account-service`, `event-gateway`
- [x] Java 21 and Maven 3.9+ enforced by Maven Enforcer
- [x] `mvn clean verify` passes locally
- [x] `docker compose up --build` brings both services to healthy
- [ ] `docker compose --profile observability up --build` (optional Jaeger/Prometheus UI walkthrough)

## Functional behavior

- [x] Gateway public REST API on port 8080
- [x] Account REST API on port 8081
- [x] Separate H2 stores, Flyway migrations, no shared runtime state
- [x] Credit/debit apply correctly; negative balances allowed by policy
- [x] `201` new / `200` + `Idempotent-Replay` duplicate / `409` conflict / `400` validation / `503` outage
- [x] Ordered event listing and chronological history
- [x] JSON structured logs with `traceId` / `spanId`
- [x] `/health` includes database connectivity

## Correctness and failure behavior

- [x] Two-level DB-backed idempotency
- [x] Deterministic request fingerprints
- [x] Processing state machine with optimistic claim
- [x] No DB transaction held across remote HTTP
- [x] Uncertain downstream outcome covered by WireMock test
- [x] Concurrent duplicate races change balance once

## Resilience and observability

- [x] Timeout, retry+jitter, circuit breaker, bulkhead
- [x] Prometheus custom metrics
- [x] OpenTelemetry / W3C trace propagation
- [x] Non-root Docker images and Compose health checks

## Documentation and security

- [x] README, ADRs, walkthrough, alignment, GitHub readiness docs
- [x] LICENSE, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT
- [x] No secrets or assignment PDF committed
- [x] `.env.example` contains placeholders only

## GitHub release checklist

- [ ] Repository is public or accessible to reviewers
- [ ] Default branch is `main`
- [ ] README renders correctly (including Mermaid)
- [ ] All relative links work
- [ ] No secrets in tree or history
- [ ] CI (`Build`) passes on `main`
- [ ] Docker Compose works from a clean clone
- [ ] Tests pass from a clean clone
- [ ] Meaningful incremental commit history
- [ ] No internal company assignment PDF committed
- [ ] LICENSE included
- [ ] Final tag created: `v1.0.0`

### Suggested release

**Tag:** `v1.0.0`  
**Title:** Event Ledger Take-Home Submission

```markdown
## Event Ledger Take-Home Submission

This release contains the final implementation of the Event Ledger assignment.

### Highlights

- Independent Event Gateway and Account Service
- Database-backed two-level idempotency
- Conflict detection using deterministic request fingerprints
- Concurrency-safe account updates
- Safe handling of retries and uncertain downstream outcomes
- Timeout, retry, circuit breaker, and bulkhead protection
- OpenTelemetry trace propagation
- JSON structured logs and Prometheus metrics
- Automated unit, integration, concurrency, and failure-injection tests
- Docker Compose local environment
```
