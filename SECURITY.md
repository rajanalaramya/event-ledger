# Security Policy

## Supported versions

This repository is a take-home submission. Security fixes are applied to the latest `main` branch only.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security-sensitive findings.

Email the repository maintainer privately with:

* a description of the issue
* steps to reproduce
* potential impact
* any suggested remediation

You should receive an acknowledgment within a reasonable time. Please allow time for investigation before any public disclosure.

## Scope notes

* Authentication is intentionally out of scope for the assignment and documented as a production concern.
* H2 is used because of assignment constraints; it is not a production database recommendation.
* Actuator exposure is limited to health/prometheus/info/metrics; the shutdown endpoint is disabled.
* Automated scanners (CodeQL, dependency review, Dependabot) are **supplemental** and do not replace careful dependency and container review.

## Secrets

Never commit:

* passwords, tokens, API keys
* private keys or certificates
* production connection strings
* private corporate assignment materials unless explicitly permitted

If a secret is accidentally committed, rotate it immediately and purge it from Git history before making the repository public.
