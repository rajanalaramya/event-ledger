# Contributing to Event Ledger

Thanks for improving this take-home project. This guide describes the branch model and review expectations used for a clean public GitHub submission.

## Branch model

| Branch | Purpose |
|--------|---------|
| `main` | Stable, reviewable default branch |
| `feature/*` | New functionality |
| `fix/*` | Bug fixes |
| `docs/*` | Documentation-only changes |
| `test/*` | Test-only improvements |
| `chore/*` | Build, CI, dependency housekeeping |

Recommendations:

* Do not land unreviewed production changes directly on `main`.
* Open a pull request for every change intended for `main`.
* Require CI to pass before merge.
* Squash-merge is acceptable for feature branches in day-to-day work; for this take-home, prefer preserving meaningful incremental commits that show design progression.
* No force pushes to shared branches.
* Signed commits are optional, not mandatory.

## Development workflow

1. Fork or clone the repository.
2. Create a topic branch from `main`.
3. Implement focused changes with tests.
4. Run locally:

```bash
mvn clean verify
docker compose config
```

5. Open a pull request using the template.
6. Address review feedback.

## Coding standards

* Java 21, Spring Boot 3.3.x, constructor injection only.
* Prefer immutable DTOs/records in the `contract` module.
* Keep Gateway and Account Service independently deployable.
* Do not hold a database transaction open across remote HTTP calls.
* Avoid high-cardinality metric labels (`eventId`, `accountId`, trace IDs).
* Never commit secrets, local H2 files, or IDE metadata.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```text
feat(gateway): add duplicate replay handling
fix(account): retry optimistic-lock conflicts
test: cover uncertain downstream commit
docs: clarify resiliency tradeoffs
ci: upload surefire reports on failure
```

## Security

See [SECURITY.md](SECURITY.md) for vulnerability reporting. Automated scanning (CodeQL, dependency review, Dependabot) is supplemental and does not replace human dependency review.
