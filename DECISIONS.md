# Architecture Decision Records

## ADR-001 — Split the public gateway from balance authority
**Decision:** Use `event-gateway` on port 8080 for the public event API and `account-service` on port 8081 for balance mutation, communicating by REST.  
**Why:** It makes API workflow/state ownership distinct from the account side effect and models independently deployable services.  
**Consequence:** The services need explicit resilience and cross-service idempotency; they cannot share a transaction.

## ADR-002 — Give each service its own database
**Decision:** Gateway owns its H2 `events` database and Account Service owns its H2 `accounts` and `applied_transactions` database.  
**Why:** Data ownership is explicit and independent deployment is possible.  
**Consequence:** There is no cross-service query or distributed transaction; H2 is a local exercise constraint, not a production HA store.

## ADR-003 — Use two-level idempotency
**Decision:** Store event id/fingerprint/state at Gateway and event id/fingerprint in Account Service’s applied transaction table.  
**Why:** Gateway provides client-visible replay/conflict/status semantics, while Account protects the balance mutation after a lost response or retry.  
**Consequence:** Both records must use the same event ID and fingerprint.

## ADR-004 — Let database uniqueness be the race authority
**Decision:** Use `events.event_id` as the Gateway primary key and `applied_transactions.event_id` as a unique key.  
**Why:** A read-before-write check is not safe under concurrent requests; the database constraint remains correct across process restarts.  
**Consequence:** Unique-key failures are re-read and classified as replay or conflict.

## ADR-005 — Fingerprint canonical request content
**Decision:** SHA-256 the canonical event ID, account ID, type, normalized amount, currency, Instant, and sorted metadata.  
**Why:** A reused `eventId` must be distinguishable from an equivalent retry, independent of JSON field/key order and amount scale.  
**Consequence:** A different fingerprint produces `409 Conflict`; metadata collection/object canonicalization is intentionally limited.

## ADR-006 — Use a persisted, optimistic state machine
**Decision:** Gateway transitions `RECEIVED → PROCESSING → APPLIED|RETRYABLE_FAILURE|PERMANENT_FAILURE`, with retry from `RETRYABLE_FAILURE`; entities use optimistic versions.  
**Why:** State survives restarts and a conditional claim prevents concurrent callers from processing the same gateway event.  
**Consequence:** A caller that finds `PROCESSING` receives `503`/`Retry-After` rather than blocking.

## ADR-007 — Never hold a local transaction over REST
**Decision:** Persist/claim/finish Gateway state in short local transactions and make the Account REST call between them.  
**Why:** Network latency while holding a database transaction increases lock contention and still cannot yield atomic distributed commit.  
**Consequence:** A committed Account change with lost response is an expected uncertain state resolved through Account idempotency.

## ADR-008 — Use Account’s atomic transaction for at-most-once balance mutation
**Decision:** Update the account balance and insert the applied transaction in one Account Service transaction.  
**Why:** This is the only local atomic boundary containing the side effect and its idempotency record.  
**Consequence:** The system does not promise distributed exactly-once processing, XA, saga, or outbox semantics.

## ADR-009 — Bound downstream pressure with Resilience4j and rate limiting
**Decision:** Apply connect/read timeouts, retry only transient failures, circuit breaker, and a zero-wait bulkhead to the Account client; rate-limit Gateway `POST /events`.  
**Why:** Retries improve transient recovery but can multiply outage traffic.  
**Consequence:** Calls may return `503`, and a client should honor `Retry-After`; retries remain safe but add load.

## ADR-010 — Make observability and deployment behavior explicit
**Decision:** Expose Actuator health/Prometheus, Micrometer OTel tracing, trace/span IDs in log patterns, non-root container images, and optional Compose observability services.  
**Why:** Operators need to distinguish process liveness from dependency readiness and correlate a request across REST.  
**Consequence:** Logs are pattern-enriched text rather than JSON; authentication/TLS/secrets are external requirements, not implemented security controls.
