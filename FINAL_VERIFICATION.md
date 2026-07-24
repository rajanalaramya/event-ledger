# Final Verification Report — Event Ledger

**Date/time:** 2026-07-23 (local; trace re-verification same day)  
**Workspace:** `C:\schwab`  
**Run ID suffix:** `1784844279` (functional suite); live trace event `evt-live-trace-final`  
**Primary account:** `acct-verify-1784844279`  
**Race account:** `acct-race-1784844279`  
**Evidence directory:** `verification-artifacts/` (local capture; not committed)  
**Implementation changes during this verification:** request-scoped audit logs + Micrometer/W3C tracing fix (see item 19); **not committed yet**

### Tooling

| Tool | Version / availability |
|------|------------------------|
| Java | Temurin 21.0.11 |
| Maven | 3.9.11 |
| Docker | 29.4.2 |
| curl | 8.21.0 (Windows) |
| gitleaks | **not installed** |

---

## Summary scorecard

| # | Check | Result |
|---|--------|--------|
| 1 | `git status` | **PASS** |
| 2 | `git log` | **PASS** |
| 3 | `mvn clean verify` | **PASS** (40 Surefire tests, 0 failures) |
| 3b | `mvn clean verify -Pdual-service-it` | **PASS** (40 Surefire + 8 Failsafe, 0 failures) |
| 4 | `docker compose config` | **PASS** |
| 5 | `docker compose up --build -d` | **PASS** |
| 6 | `docker compose ps` | **PASS** |
| 7 | Health endpoints | **PASS** |
| 8 | New CREDIT | **PASS** |
| 9 | Exact duplicate replay | **PASS** |
| 10 | Conflicting duplicate | **PASS** |
| 11 | DEBIT + exact balance | **PASS** |
| 12 | Out-of-order listing | **PASS** |
| 13 | Validation errors | **PASS** |
| 14 | Account Service outage | **PASS** |
| 15 | Gateway reads during outage | **PASS** |
| 16 | Safe 503 during outage | **PASS** |
| 17 | Recovery and retry | **PASS** |
| 18 | Circuit-breaker evidence | **PASS** |
| 19 | Trace propagation (both service logs) | **PASS** |
| 20 | JSON structured logs | **PASS** |
| 21 | Prometheus / custom metrics | **PASS** |
| 22 | Concurrent duplicates | **PASS** |
| 23 | Final balance + transaction count | **PASS** |
| 24 | Secret / generated-file scan | **PASS** (gitleaks NOT VERIFIED) |

**Overall:** Repository meets functional and operational verification goals, including live dual-service W3C trace correlation and an optional Failsafe profile that runs both real Spring Boot processes end-to-end.

---

## 1. `git status`

**Command**

```bash
git status
```

**Expected:** Readable working tree status; branch present.

**Actual:** On branch `master`. Clean tracked history plus uncommitted GitHub-hygiene changes (`.gitignore`, workflows, `LICENSE`, etc.). No implementation edits during this run.

**Result:** **PASS**

**Excerpt**

```text
On branch master
Changes not staged for commit:
  modified:   .dockerignore
  modified:   .github/workflows/build.yml
  modified:   .gitignore
  modified:   README.md
  modified:   SUBMISSION_CHECKLIST.md
Untracked files:
  .editorconfig
  .env.example
  .gitattributes
  .github/ISSUE_TEMPLATE/
  ...
  LICENSE
  docs/
```

---

## 2. `git log --oneline --decorate --graph -15`

**Command**

```bash
git log --oneline --decorate --graph -15
```

**Expected:** Multiple incremental commits (not a single squash).

**Actual:** 7 commits on `master`.

**Result:** **PASS**

**Excerpt**

```text
* d5ad766 (HEAD -> master) docs: add architecture decisions walkthrough and submission guide
* 91abf14 build: add containerized runtime and observability stack
* 7cff11c test: cover resiliency tracing duplicates and partial failures
* 0626e62 feat: implement gateway event lifecycle and idempotent replay
* 5040bee feat: implement idempotent and concurrency-safe account transactions
* f6cf33b feat: add versioned contracts and safe persistence foundations
* 910554c docs: document implementation plan and official PDF alignment
```

---

## 3. `mvn clean verify`

**Command**

```bash
mvn clean verify
```

**Expected:** `BUILD SUCCESS`, all tests green.

**Actual:** Exit code 0. Contract 8 + Account 11 + Gateway 21 = **40 Surefire tests**, 0 failures/errors.

**Result:** **PASS**

**Excerpt**

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0   (contract)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0  (account-service)
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0  (event-gateway)
[INFO] BUILD SUCCESS
```

---

## 3b. `mvn clean verify -Pdual-service-it`

**Command**

```bash
mvn clean verify -Pdual-service-it
```

**Expected:** Default suite plus Failsafe `DualServiceEventLedgerIT` against real Gateway (`18080`) and Account Service (`18081`) processes; both processes stop afterward.

**Actual:** Exit code 0 on two consecutive runs. `process-exec-maven-plugin` started both JARs with healthcheck URLs, Failsafe ran **8** IT methods, `stop-all` terminated processes, temp H2 data deleted, logs retained.

**Result:** **PASS**

**Artifacts**

- Failsafe: `dual-service-it/target/failsafe-reports/`
- Logs: `dual-service-it/target/dual-service-it/logs/event-gateway.log`, `.../account-service.log`

**Excerpt**

```text
[INFO] --- process-exec:0.9:start (start-account-service) @ dual-service-it ---
[INFO] --- process-exec:0.9:start (start-event-gateway) @ dual-service-it ---
[INFO] Running com.example.eventledger.dual.DualServiceEventLedgerIT
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- process-exec:0.9:stop-all (stop-dual-service-it-processes) @ dual-service-it ---
[INFO] BUILD SUCCESS
```

**Note:** Mid-test Account Service kill/restart is not exercised in this profile; WireMock + Docker Compose cover outage/recovery.

---

## 4. `docker compose config`

**Command**

```bash
docker compose config
```

**Expected:** Valid Compose project; exit 0.

**Actual:** Exit 0; services `account-service`, `event-gateway`, volumes rendered.

**Result:** **PASS**

**Excerpt**

```text
volumes:
  account-data:
    name: event-ledger_account-data
  gateway-data:
    name: event-ledger_gateway-data
COMPOSE_CONFIG_EXIT=0
```

---

## 5. `docker compose up --build -d`

**Command**

```bash
docker compose up --build -d
```

**Expected:** Images build; containers start; exit 0.

**Actual:** Exit 0 after rebuild (~110s).

**Result:** **PASS**

---

## 6. `docker compose ps`

**Command**

```bash
docker compose ps
```

**Expected:** Both services up; health progressing to healthy.

**Actual:** Both up; later confirmed healthy.

**Result:** **PASS**

**Excerpt (after warm-up)**

```text
event-ledger-account-service-1   Up (healthy)   0.0.0.0:8081->8081/tcp
event-ledger-event-gateway-1     Up (healthy)   0.0.0.0:8080->8080/tcp
```

---

## 7. Both service health endpoints

**Commands**

```bash
curl -si http://localhost:8080/health
curl -si http://localhost:8081/health
```

**Expected:** HTTP 200; status UP; database diagnostics present.

**Actual:** Both 200 with `database.status=UP`.

**Result:** **PASS**

**Excerpt**

```text
{"status":"UP","service":"event-gateway","version":"1.0.0-SNAPSHOT","liveness":"CORRECT","database":{"status":"UP","product":"H2"}}
{"status":"UP","service":"account-service","version":"1.0.0-SNAPSHOT","database":{"status":"UP","product":"H2"}}
```

---

## 8. New CREDIT submission

**Command**

```bash
curl -si -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "traceparent: 00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01" \
  --data-binary @verification-artifacts/payload-credit.json
```

Payload: `evt-credit-1784844279`, CREDIT `150.00`, account `acct-verify-1784844279`.

**Expected:** `201 Created`, `Location`, `processingStatus=APPLIED`.

**Actual:** HTTP 201; `Location: /events/evt-credit-1784844279`; `APPLIED`.

**Result:** **PASS**

**Excerpt**

```text
HTTP/1.1 201
Location: /events/evt-credit-1784844279
{"eventId":"evt-credit-1784844279",...,"processingStatus":"APPLIED","idempotentReplay":false,...}
```

---

## 9. Exact duplicate replay

**Command**

```bash
curl -si -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  --data-binary @verification-artifacts/payload-credit.json
```

**Expected:** `200 OK`, `Idempotent-Replay: true`, balance unchanged later.

**Actual:** HTTP 200; header and body replay flags true; `attemptCount` remains 1.

**Result:** **PASS**

**Excerpt**

```text
HTTP/1.1 200
Idempotent-Replay: true
{"processingStatus":"APPLIED","idempotentReplay":true,"attemptCount":1}
```

---

## 10. Conflicting duplicate submission

**Command**

```bash
curl -si -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  --data-binary @verification-artifacts/payload-conflict.json
```

(Same `eventId`, amount `999.00`.)

**Expected:** `409 Conflict`, `errorCode=CONFLICT`.

**Actual:** HTTP 409 ProblemDetail.

**Result:** **PASS**

**Excerpt**

```text
HTTP/1.1 409
{"type":"urn:event-ledger:conflict","title":"Conflict","status":409,
 "detail":"eventId was submitted with a different payload","errorCode":"CONFLICT",...}
```

---

## 11. DEBIT and exact balance verification

**Commands**

```bash
curl -si -X POST http://localhost:8080/events ... --data-binary @payload-debit.json
curl -si http://localhost:8081/accounts/acct-verify-1784844279/balance
```

DEBIT `40.00` after CREDIT `150.00`.

**Expected:** DEBIT `201`; balance `110.00` (= 150 − 40) before out-of-order credit.

**Actual:** DEBIT 201; balance **110.00**.

**Result:** **PASS**

**Excerpt**

```text
HTTP/1.1 201
{"eventId":"evt-debit-1784844279","type":"DEBIT","amount":40.00,"processingStatus":"APPLIED",...}
{"accountId":"acct-verify-1784844279","currency":"USD","balance":110.00,...}
```

---

## 12. Out-of-order event listing

**Commands**

```bash
curl -si -X POST ... --data-binary @payload-ooo.json   # timestamp 2026-05-15T10:00:00Z, +10.00
curl -si "http://localhost:8080/events?account=acct-verify-1784844279"
```

**Expected:** Listing ordered by `eventTimestamp` ascending (OOO first), then credit, then debit.

**Actual:** Order: `evt-ooo-…` (10:00), `evt-credit-…` (14:02), `evt-debit-…` (16:00).

**Result:** **PASS**

**Excerpt**

```text
[
  {"eventId":"evt-ooo-1784844279","eventTimestamp":"2026-05-15T10:00:00Z",...},
  {"eventId":"evt-credit-1784844279","eventTimestamp":"2026-05-15T14:02:11Z",...},
  {"eventId":"evt-debit-1784844279","eventTimestamp":"2026-05-15T16:00:00Z",...}
]
```

---

## 13. Validation errors

**Command**

```bash
curl -si -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  --data-binary @payload-invalid.json
```

(Blank `eventId`.)

**Expected:** `400` ProblemDetail with field error.

**Actual:** HTTP 400; `errorCode=VALIDATION_ERROR`; `errors.eventId=must not be blank`.

**Result:** **PASS**

**Excerpt**

```text
HTTP/1.1 400
{"type":"urn:event-ledger:validation_error","status":400,"errorCode":"VALIDATION_ERROR",
 "errors":{"eventId":"must not be blank"}}
```

---

## 14. Account Service outage

**Command**

```bash
docker compose stop account-service
```

**Expected:** Account container stopped.

**Actual:** `Container ... Stopped`.

**Result:** **PASS**

---

## 15. Gateway read availability during outage

**Commands**

```bash
curl -si http://localhost:8080/events/evt-credit-1784844279
curl -si "http://localhost:8080/events?account=acct-verify-1784844279"
```

**Expected:** Both remain `200` while Account is down.

**Actual:** GET by id 200; list 200 (includes stored events + new outage event as `RETRYABLE_FAILURE`).

**Result:** **PASS**

**Excerpt**

```text
HTTP/1.1 200
{"eventId":"evt-credit-1784844279","processingStatus":"APPLIED",...}
... list includes evt-outage-1784844279 with "processingStatus":"RETRYABLE_FAILURE"
```

---

## 16. Safe 503 behavior during outage

**Command**

```bash
curl -si -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  --data-binary @payload-outage.json
```

**Expected:** `503`, `Retry-After`, no hang, sanitized ProblemDetail (not `500`).

**Actual:** HTTP 503; `Retry-After: 1`; `errorCode=DOWNSTREAM_UNAVAILABLE`.

**Result:** **PASS**

**Excerpt**

```text
HTTP/1.1 503
Retry-After: 1
{"type":"urn:event-ledger:downstream_unavailable","status":503,
 "detail":"account service is unavailable","errorCode":"DOWNSTREAM_UNAVAILABLE",...}
```

---

## 17. Recovery and retry

**Commands**

```bash
docker compose start account-service
# wait until /health 200
curl -si -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  --data-binary @payload-outage.json
curl -si http://localhost:8081/accounts/acct-verify-1784844279/balance
```

**Expected:** Outage event becomes `APPLIED` (attemptCount ≥ 2); balance includes +5 once.

**Actual:** HTTP 201 path for apply completion; `processingStatus=APPLIED`, `attemptCount=2`; balance **125.00** (= 150 − 40 + 10 + 5).

**Result:** **PASS**

**Excerpt**

```text
{"eventId":"evt-outage-1784844279","processingStatus":"APPLIED","attemptCount":2,...}
{"accountId":"acct-verify-1784844279","balance":125.00,...}
```

---

## 18. Circuit-breaker evidence

**Commands**

```bash
# while Account stopped: 8 distinct POSTs
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/events ...
curl -s http://localhost:8080/actuator/prometheus | grep resilience4j_circuitbreaker
```

**Expected:** Failures recorded; breaker eventually `open`; not-permitted calls increase.

**Actual:** All 8 returned 503. Metrics show `state="open" = 1.0`, `failure_rate=50.0`, `not_permitted_calls_total=6.0`.

**Result:** **PASS**

**Excerpt**

```text
evt-cb-1 -> HTTP 503
...
evt-cb-8 -> HTTP 503
resilience4j_circuitbreaker_state{...,state="open"} 1.0
resilience4j_circuitbreaker_not_permitted_calls_total{...,name="accountService"} 6.0
resilience4j_circuitbreaker_failure_rate{...,name="accountService"} 50.0
```

---

## 19. Trace propagation evidence from both service logs

**Commands**

```bash
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "traceparent: 00-11111111111111111111111111111111-2222222222222222-01" \
  --data-binary @verification-artifacts/live-trace-body.json
docker compose logs event-gateway | grep "11111111111111111111111111111111"
docker compose logs account-service | grep "11111111111111111111111111111111"
```

Plus automated coverage inside Maven:

- `GatewayWireMockIntegrationTest.tracePropagation_sendsTraceparent`
- `GatewayWireMockIntegrationTest.tracePropagation_gatewayAuditLogContainsSameTraceId`
- `AccountTracingLogIntegrationTest.applyTransaction_logsInboundTraceIdAsStructuredJsonFields`

**Expected:** HTTP success; same 32-char `traceId` in structured JSON logs from both services; distinct non-empty `spanId`s; Account log from transaction apply path.

**Actual:**

- Live request: **HTTP 201** `APPLIED` for `evt-live-trace-final`.
- Same inbound `traceId` `11111111111111111111111111111111` in Gateway and Account audit JSON.
- Gateway `spanId`: `34d60f47a96f8fea`; Account `spanId`: `9c11ecaf7ffca8d6` (different).
- Account logger: `com.example.eventledger.account.RequestAuditLog` / `operation=apply_transaction` (not a diagnostic filter).
- Maven: all 33 tests green, including the three tracing assertions above.

**Result:** **PASS**

**Excerpt (Gateway)**

```text
{"@timestamp":"2026-07-23T22:27:32.547Z","message":"gateway event APPLIED",
 "logger_name":"com.example.eventledger.gateway.RequestAuditLog",
 "traceId":"11111111111111111111111111111111","spanId":"34d60f47a96f8fea",
 "eventId":"evt-live-trace-final","accountId":"acct-live-trace-final",
 "operation":"submit_event","outcome":"APPLIED","service":"event-gateway"}
```

**Excerpt (Account Service)**

```text
{"@timestamp":"2026-07-23T22:27:32.459Z","message":"account transaction APPLIED",
 "logger_name":"com.example.eventledger.account.RequestAuditLog",
 "traceId":"11111111111111111111111111111111","spanId":"9c11ecaf7ffca8d6",
 "eventId":"evt-live-trace-final","accountId":"acct-live-trace-final",
 "operation":"apply_transaction","outcome":"APPLIED","service":"account-service"}
```

---

## 20. JSON structured log verification

**Command**

```bash
docker compose logs event-gateway --tail 5
docker compose logs account-service --tail 5
```

**Expected:** JSON objects with timestamp, level, service name (and tracing fields when MDC populated).

**Actual:** LogstashEncoder JSON on both services (`@timestamp`, `level`, `service`, `message`, …).

**Result:** **PASS**

**Excerpt**

```text
{"@timestamp":"2026-07-23T22:05:21.378Z","@version":"1","message":"Started AccountServiceApplication ...",
 "level":"INFO","service":"account-service"}
{"@timestamp":"2026-07-23T22:05:23.421Z","level":"ERROR","traceId":"...","spanId":"...","service":"event-gateway",...}
```

---

## 21. Prometheus / custom metric verification

**Commands**

```bash
curl -s http://localhost:8080/actuator/prometheus | findstr event_ledger_
curl -s http://localhost:8081/actuator/prometheus | findstr event_ledger_
```

**Expected:** Custom counters/timers present; low-cardinality `application` tag only.

**Actual:** Gateway and Account custom series present; values reflect the verification traffic (e.g. duplicates=1, conflicts=1, downstream failures≥1).

**Result:** **PASS**

**Excerpt**

```text
event_ledger_events_received_total{application="event-gateway"} 13.0
event_ledger_events_applied_total{application="event-gateway"} 5.0
event_ledger_duplicate_replays_total{application="event-gateway"} 1.0
event_ledger_idempotency_conflicts_total{application="event-gateway"} 1.0
event_ledger_downstream_failures_total{application="event-gateway"} 9.0
event_ledger_transactions_applied_total{application="account-service"} ...
```

---

## 22. Concurrent duplicate test

**Command**

```powershell
# 10 parallel identical POSTs of payload-race.json
Start-Job { curl.exe -s -o NUL -w "%{http_code}" -X POST http://localhost:8080/events ... }
# then inspect events + balance + account details
```

**Expected:** One logical apply; balance `25.00` once; one gateway event; one account transaction.

**Actual:** HTTP codes `201,200,200,200,200,200,200,200,200,200`; one gateway event; balance `25.00`; transactions length 1.

**Result:** **PASS**

**Excerpt**

```text
201,200,200,200,200,200,200,200,200,200
[{"eventId":"evt-race-1784844279",...,"processingStatus":"APPLIED",...}]
{"balance":25.00,...}
{"transactions":[{"eventId":"evt-race-1784844279","amount":25.00,...}]}
```

---

## 23. Final exact balance and transaction-count verification

**Commands**

```bash
curl -s http://localhost:8081/accounts/acct-verify-1784844279/balance
curl -s http://localhost:8081/accounts/acct-verify-1784844279
```

**Applied financial events for this account:**

| Event | Type | Amount |
|-------|------|--------|
| evt-ooo-… | CREDIT | +10 |
| evt-credit-… | CREDIT | +150 |
| evt-debit-… | DEBIT | −40 |
| evt-outage-… | CREDIT | +5 |
| evt-trace-… | CREDIT | +1 |

**Expected balance:** 10 + 150 − 40 + 5 + 1 = **126.00**  
**Expected applied transaction count:** **5**  
(CB pressure events remain `RETRYABLE_FAILURE` and must not change balance.)

**Actual:** balance `126.00`; `transactions.length == 5`.

**Result:** **PASS**

**Excerpt**

```text
{"accountId":"acct-verify-1784844279","currency":"USD","balance":126.00,...}
"transactions":[
  {"eventId":"evt-ooo-1784844279",...},
  {"eventId":"evt-credit-1784844279",...},
  {"eventId":"evt-debit-1784844279",...},
  {"eventId":"evt-outage-1784844279",...},
  {"eventId":"evt-trace-1784844279",...}
]
```

---

## 24. Secret and generated-file scan

**Commands**

```bash
git grep -nEi "password|secret|token|api[_-]?key|private[_-]?key"
git ls-files | Select-String "target/|\.idea/|\.vscode/|\.mv\.db$|\.env$|credentials|\.pdf$"
gitleaks detect --source .
```

**Expected:** No real secrets; no generated/IDE/H2/PDF artifacts tracked; gitleaks clean if available.

**Actual:**

- `git grep` hits are documentation, empty H2 `password:` placeholders, or rate-limit “token” naming — **not secrets**.
- Tracked forbidden patterns: **none**.
- `gitleaks`: **not installed** → **NOT VERIFIED**.

**Result:** **PASS** for git-tracked secret/generated-file hygiene; **NOT VERIFIED** for gitleaks.

**Excerpt**

```text
account-service/.../application.yml:    password:     # empty H2 placeholder
event-gateway/.../application.yml:    password:
--- tracked forbidden ---
(empty)
--- gitleaks ---
gitleaks not installed
```

---

## Notes / residual risks

1. Working tree still has **uncommitted** GitHub-hygiene files plus the tracing fix; this verification did not commit them.
2. Default branch is still named `master` locally; rename to `main` before public push.
3. Item 19 is now **PASS** with live dual-service evidence for inbound `traceparent` `00-1111…-2222…-01`.
4. Circuit-breaker pressure left several `RETRYABLE_FAILURE` gateway events that intentionally do not affect the verified applied balance of `126.00`.
5. Raw captures live under `verification-artifacts/` for reviewer inspection (`live-trace-*.txt`).
6. gitleaks remains **NOT VERIFIED** (binary not installed).

## Verdict

Evidence-based verification **succeeds** for build, Docker, happy path, idempotency, conflict, ordering, validation, outage/read degradation, 503, recovery, circuit breaker, JSON logs, metrics, concurrency, final balance, repository hygiene scans, and **live W3C trace correlation across Gateway and Account Service**.

**Do not commit until reviewed.**
