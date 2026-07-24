# Architecture notes

Event Ledger is two independently deployable Spring Boot services communicating over synchronous REST with separate H2 databases.

## Boundaries

- `contract` shares DTOs and pure helpers only.
- Gateway owns client-facing event durability and processing state.
- Account owns balances and applied transaction history.

## Key invariants

1. Each logical `eventId` mutates balance at most once.
2. Conflicting reuse of an `eventId` never overwrites prior financial intent.
3. Gateway reads remain available when Account is down.
4. No database transaction spans a remote HTTP call.

See also `DECISIONS.md` and the Mermaid diagrams in `README.md`.
