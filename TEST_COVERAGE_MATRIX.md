# Test Coverage Matrix — Event Ledger

**Baseline before this review:** 33 automated tests (`mvn clean verify`).  
**After this review:** **40** automated Surefire tests in the default build, 0 failures (`mvn clean verify` BUILD SUCCESS).  
**Optional dual-service profile:** +8 Failsafe tests via `mvn clean verify -Pdual-service-it`.  
**Assignment source:** Schwab take-home PDF + production-risk scenarios in `docs/failure-scenarios.md`.  
**Rule:** Status is based on assertions that run, not on implementation code existing.

| Requirement | Risk being prevented | Test class | Test method | Test type | Status | Evidence |
|---|---|---|---|---|---|---|
| **1. Gateway core — submit event → APPLIED** | Silent drop / wrong lifecycle | `GatewayWireMockIntegrationTest` | `happyPath_createsAndAppliesEvent` | integration | COVERED | Asserts `201`, `APPLIED`, GET by id |
| **1. Gateway core — identical replay** | Double downstream apply on retry | `GatewayWireMockIntegrationTest` | `duplicateIdentical_returns200WithoutSecondAccountCall` | integration | COVERED | `200` + `Idempotent-Replay` + WireMock count `1` |
| **1. Gateway core — list by account** | Wrong account scoping | `GatewayWireMockIntegrationTest` | `outOfOrderEvents_listChronologically` | integration | COVERED | Lists by `?account=` |
| **2. Account — credit/debit/balance** | Incorrect ledger state | `AccountServiceIntegrationTest` | `appliesTransactionsReplaysIdenticalEventsAndReportsConflicts` | integration | COVERED | Balance `10→7`, history length `2` |
| **2. Account — allow-negative debit** | Unexpected reject / wrong policy | `BalanceCalculatorTest` | `creditsAndDebitsNormalizeToMoneyScale` | unit | COVERED | Debit to `-2.00` |
| **2. Account — currency mismatch** | Cross-currency corruption | `AccountServiceIntegrationTest` | `rejectsCurrencyMismatchWithoutPartialTransaction` | integration | COVERED | `409`, txn count `1`, balance unchanged |
| **3. Validation — blank eventId (Gateway)** | Garbage accepted into DB | `GatewayWireMockIntegrationTest` | `validationError_returnsProblemDetail` | integration | COVERED | `400` ProblemDetail + `errors.eventId` |
| **3. Validation — negative amount (Gateway)** | Negative money posted | `GatewayWireMockIntegrationTest` | `rejectsNegativeAmount_returnsProblemDetail` | integration | COVERED | `400` + `VALIDATION_ERROR` + events count `0` |
| **3. Validation — negative amount (Account)** | Downstream accepts invalid money | `AccountServiceIntegrationTest` | `rejectsNegativeAmount_returnsProblemDetail` | integration | COVERED | `400` + no rows |
| **3. Validation — currency codes** | Invalid ISO currency | `CurrencyCodesTest` | `rejectsInvalidCurrencyCodes` | unit | COVERED | Rejects lowercase / wrong length |
| **3. Validation — zero/negative MoneyAmounts** | Scale/sign bugs | `MoneyAmountsTest` | `rejectsZeroAndNegativeAmounts` | unit | COVERED | AssertJ illegal-arg |
| **4. Gateway DB idempotency (unique eventId)** | Race creates duplicate gateway rows | `EventRepositoryUniqueConstraintTest` | `rejectsDuplicateEventIds` | repository | COVERED | `DataIntegrityViolationException` on duplicate PK |
| **4. Gateway DB idempotency (app path)** | App bypasses constraint | `GatewayWireMockIntegrationTest$ConcurrentDuplicateGatewayTest` | `parallelIdenticalPosts_createOnlyOneEventRow` | concurrency | COVERED | Final `events.count()==1` |
| **5. Account DB idempotency (unique event_id)** | Duplicate ledger lines | `RepositoryUniqueConstraintTest` | `rejectsDuplicateEventIds` | repository | COVERED | Unique constraint violation |
| **5. Account DB idempotency (app path)** | Balance applied twice | `ConcurrentAccountUpdatesTest` | `appliesConcurrentDuplicateEventOnlyOnce` | concurrency | COVERED | Balance `3.00`, txn count `2` (seed+one) |
| **6. Conflicting eventId reuse (Gateway)** | Payload swap accepted | `GatewayWireMockIntegrationTest` | `conflictingPayload_returns409` | integration | COVERED | `409` + `errorCode=CONFLICT` |
| **6. Conflicting eventId reuse (Account)** | Fingerprint mismatch ignored | `AccountServiceIntegrationTest` | `appliesTransactionsReplaysIdenticalEventsAndReportsConflicts` | integration | COVERED | Conflict path in same method |
| **6. Fingerprint determinism** | Unstable conflict detection | `RequestFingerprintTest` | `producesTheSameFingerprintForEquivalentRequests` / `producesDifferentFingerprintsForDifferentPayloads` | unit | COVERED | Stable SHA-256 |
| **7. Concurrent duplicate submissions (Gateway)** | Two rows / two HTTP applies | `GatewayWireMockIntegrationTest$ConcurrentDuplicateGatewayTest` | `parallelIdenticalPosts_createOnlyOneEventRow` | concurrency | COVERED | Latch start; WireMock `1` call |
| **7. Concurrent duplicate submissions (Account)** | Double balance credit | `ConcurrentAccountUpdatesTest` | `appliesConcurrentDuplicateEventOnlyOnce` | concurrency | COVERED | 24 parallel identical applies |
| **8. Concurrent updates to one account** | Lost updates / wrong balance | `ConcurrentAccountUpdatesTest` | `appliesConcurrentCreditsAndDebitsWithExactBalance` | concurrency | COVERED | 50 credit + 20 debit → `31.00`, 71 rows |
| **9. Out-of-order arrival (Gateway list)** | Clients see wrong chronology | `GatewayWireMockIntegrationTest` | `outOfOrderEvents_listChronologically` | integration | COVERED | Earlier timestamp listed first |
| **9. Out-of-order arrival (Account history)** | History not chronological | `AccountServiceIntegrationTest` | `ordersHistoryByEventTimestampThenEventId` | integration | COVERED | Tie-break by `eventId` |
| **10. Exact financial calc (BigDecimal)** | Float rounding money loss | `MoneyAmountsTest` | `normalizesUsingTwoDigitsAndHalfEvenRounding` | unit | COVERED | Half-even `1.235→1.24`, `1.245→1.24` |
| **10. Exact financial calc (balance apply)** | Credit/debit scale drift | `BalanceCalculatorTest` | `creditsAndDebitsNormalizeToMoneyScale` | unit | COVERED | AssertJ `isEqualByComparingTo` |
| **11. Partial failure after downstream commit** | Double-apply on timeout retry | `GatewayWireMockIntegrationTest` | `uncertainCommit_timeoutThenReplay` | integration | COVERED | Timeout then replay; WireMock `2`; APPLIED |
| **12. Retry safety — no retry on 409** | Amplifying permanent conflicts | `GatewayWireMockIntegrationTest` | `noRetryOnConflict409` | integration | COVERED | WireMock verify count `1` |
| **12. Retry classifier** | Retrying non-transient errors | `RetryClassifierTest` | `retriesOnlyTransientFailures` | unit | COVERED | 502/IO yes; 500/409 no |
| **12. Recovery resubmit after outage** | Unsafe replay after 503 | `GatewayWireMockIntegrationTest` | `recoveryAfterOutage_resubmitsSafely` | integration | COVERED | First 503, second APPLIED |
| **13. Timeout behavior** | Hung client / wrong status | `GatewayWireMockIntegrationTest` | `timeout_marksRetryable` | integration | COVERED | `503` + `RETRYABLE_FAILURE` |
| **14. Circuit breaker** | Cascading overload | `GatewayWireMockIntegrationTest` | `circuitBreaker_opensAfterRepeatedFailures` | integration | COVERED | State `OPEN`; 4th call no WireMock hit |
| **15. Bulkhead saturation** | Unbounded concurrent downstream calls | `GatewayWireMockIntegrationTest` | `bulkhead_rejectsWhenSaturated` | integration | COVERED | 2nd call `503`/`DOWNSTREAM_UNAVAILABLE` while 1st holds permit (`maxConcurrentCalls=1` in test profile) |
| **16. Graceful degradation — write fails, reads work** | Total outage of Gateway reads | `GatewayWireMockIntegrationTest` | `accountUnavailable_storesRetryableAndAllowsRead` | integration | COVERED | POST `503`; GET event still readable |
| **17. Trace propagation — outbound header** | Broken distributed trace | `GatewayWireMockIntegrationTest` | `tracePropagation_sendsTraceparent` | integration | COVERED | WireMock `traceparent` matches inbound trace id |
| **17. Trace — Gateway audit log fields** | Missing correlatable logs | `GatewayWireMockIntegrationTest` | `tracePropagation_gatewayAuditLogContainsSameTraceId` | integration | COVERED | JSON `traceId`/`spanId`/`service`/`operation` |
| **17. Trace — Account extracts inbound** | Account orphaned from client trace | `AccountTracingLogIntegrationTest` | `applyTransaction_logsInboundTraceIdAsStructuredJsonFields` | integration | COVERED | Fixed `traceparent`; JSON fields asserted |
| **17. Trace — live dual-service** | Prod-only correlation gap | — | Live curl + `docker compose logs` | manual | COVERED | `FINAL_VERIFICATION.md` item 19 (same `1111…` trace id both services) |
| **18. Structured JSON logging** | Unparseable ops logs | `AccountTracingLogIntegrationTest` / `GatewayWireMockIntegrationTest` | tracing audit methods | integration | COVERED | Log lines parsed as JSON; structured fields not message-only |
| **19. Health diagnostics (Account)** | False-healthy with dead DB | `AccountServiceIntegrationTest` | `healthEndpointIsAvailable` | integration | COVERED | `service` + `database.status=UP` |
| **19. Health diagnostics (Gateway)** | Same | `GatewayWireMockIntegrationTest` | `healthEndpoint_reportsServiceAndDatabase` | integration | COVERED | `service=event-gateway` + DB UP |
| **20. Custom Prometheus metrics (Account)** | Silent metric regression | `AccountServiceIntegrationTest` | `customPrometheusMetrics_exposeAfterApply` | integration | COVERED | `MeterRegistry` counter increments after apply |
| **20. Custom Prometheus metrics (Gateway)** | Same | `GatewayWireMockIntegrationTest` | `customPrometheusMetrics_exposeAfterApply` | integration | COVERED | `/actuator/prometheus` contains `event_ledger_events_*` |
| **20. Metrics scrape (Compose)** | Endpoint not exposed in container | CI `build.yml` smoke | prometheus grep | manual | COVERED | CI asserts `event_ledger_events_received_total` |
| **21. Full Gateway→Account integration** | WireMock-only false confidence | `DualServiceEventLedgerIT` | ordered health/credit/dup/conflict/debit/OOO/persistence/trace methods | end-to-end | COVERED | Failsafe via `mvn clean verify -Pdual-service-it`; both real Spring Boot JARs on ports 18080/18081; CI job uploads reports/logs |
| **22. Docker Compose smoke** | Broken images / networking | `.github/workflows/build.yml` | compose up + health + smoke | manual | COVERED | CI waits for `/health`, create+replay+balance |
| **Contract — status transitions** | Illegal state machine moves | `ProcessingStatusTransitionsTest` | `allowsDefinedTransitions` / `rejectsUndefinedAndTerminalTransitions` | unit | COVERED | Transition matrix |
| **Contract — Account DTO mapping** | Fingerprint/field loss at boundary | `AccountContractCompatibilityTest` | `accountTransactionRequestCarriesAccountContractFields` | unit | COVERED | Required fields preserved |

## Material gaps closed in this pass

| Gap | Fix |
|---|---|
| Gateway unique constraint had no repository test | Added `EventRepositoryUniqueConstraintTest` |
| Bulkhead YAML existed but was **not applied** to `AccountServiceClient` | Wired `Bulkhead.decorateSupplier` + `bulkhead_rejectsWhenSaturated` |
| Gateway `/health` untested | Added `healthEndpoint_reportsServiceAndDatabase` |
| Custom metrics untested in Maven | Added Gateway prometheus scrape + Account `MeterRegistry` assert |
| Negative-amount validation thin | Added Gateway + Account negative-amount ProblemDetail tests |

## Remaining non-Maven checks (intentional)

| Check | Why not in default `mvn verify` |
|---|---|
| Full dual-process Gateway↔Account IT | Optional: `mvn clean verify -Pdual-service-it` (`DualServiceEventLedgerIT` + Failsafe). Also covered by CI Compose smoke. |
| Mid-test Account Service kill/restart in the Maven profile | Process restart mid-suite is brittle across OS; outage/recovery remains in WireMock ITs + Docker Compose |
| Live Jaeger UI / OTLP export | Optional `observability` Compose profile; not required for assignment correctness |
| gitleaks | Tool not installed locally (`FINAL_VERIFICATION.md`) |

## Priority mapping (assignment risk order)

1. Double-balance prevention → Account concurrent duplicate (**COVERED**)
2. Concurrent duplicate safety → Gateway + Account concurrent tests (**COVERED**)
3. Conflicting duplicate safety → Gateway + Account conflict paths (**COVERED**)
4. Partial failure after Account commit → `uncertainCommit_timeoutThenReplay` (**COVERED**)
5. Concurrent account balance correctness → `appliesConcurrentCreditsAndDebitsWithExactBalance` (**COVERED**)
6. Outage and recovery → unavailable + recovery tests (**COVERED**)
7. Trace correlation → WireMock + Account tracing ITs + live manual (**COVERED**)
8. Out-of-order ordering → Gateway + Account list tests (**COVERED**)
9. Validation → blank id + negative amount + currency/money units (**COVERED**)
10. Metrics and health → new Gateway/Account tests (**COVERED**)
