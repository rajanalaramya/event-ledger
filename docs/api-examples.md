# API examples

Paths below match the running Springdoc configuration (`springdoc-openapi` 2.6 / Spring Boot 3.3).

## OpenAPI UI

- Gateway: http://localhost:8080/swagger-ui.html → `/swagger-ui/index.html`
- Account: http://localhost:8081/swagger-ui.html → `/swagger-ui/index.html`

## `201 Created`

```bash
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-201","accountId":"acct-demo","type":"CREDIT","amount":25.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'
```

Expect `Location: /events/evt-201` and `"processingStatus":"APPLIED"`.

## Duplicate `200 OK`

Resubmit the same body. Expect:

- status `200`
- header `Idempotent-Replay: true`
- `"idempotentReplay": true`

## `400 Bad Request`

```bash
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"","accountId":"acct-demo","type":"CREDIT","amount":25.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'
```

Expect `application/problem+json` with `errorCode=VALIDATION_ERROR`.

## `409 Conflict`

Reuse `eventId` with a different `amount`. Expect `errorCode=CONFLICT`.

## `503 Service Unavailable`

Stop Account Service, then POST a new event. Expect `DOWNSTREAM_UNAVAILABLE` and `Retry-After: 1`. Gateway `GET /events/{id}` for previously stored events still returns `200`.
