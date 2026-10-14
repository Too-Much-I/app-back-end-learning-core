# lc-billing-paid-exam-r2-fixtures-2

Fake offline fixture package for contract R2 draft-2. No live credentials or requests.

- canonical-cases and invalid-event-cases: raw input base64, expected normalized bytes/hash or rejection. String-codec cases test escaping only, not domain identifiers.
- token-cases: ephemeral RSA test signatures and fixed now. Private keys were never persisted. 204 means token accepted for this route; it is not an executed HTTP response.
- http-cases: expected routing/auth outcomes, NOT executed against a server.
- wire/status examples: concrete proposed DTO examples, NOT calls to the old runtime.
- state-scenarios: concrete state inputs and expectations requiring future service/transaction tests; NOT proof of concurrency or business correctness.
- S15/V07 deferred. Existing A fixtures unchanged. LC independent verification has not run.

Run from repository root: python3 scripts/contracts/verify-paid-exam-fixtures-r2.py
Do not regenerate accepted bytes. Generator rejects an existing output directory. New content needs a new revision and hashes.
