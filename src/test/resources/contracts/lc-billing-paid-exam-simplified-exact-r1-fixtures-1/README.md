# lc-billing-paid-exam-simplified-exact-r1-fixtures-1

Fake offline fixtures only. No keys, tokens, user data, HTTP, databases or runtime execution.

contract-snapshot.md is the exact draft-3 input. Error/resume vectors are pure decision models; phone vectors project selected immutable fields, not full wire DTOs. Time vectors are UTC milliseconds. State scenarios (28, including E02 parent and eight children) are NOT executed.

Run from repository root: python3 scripts/contracts/verify-paid-exam-simplified-fixtures-r1.py
Pass a fixture folder as optional argument to verify an unpacked package. Verify SHA256SUMS separately. LC must implement its own independent oracle; rerunning this verifier is not LC independent validation.

Excluded: new deletion route/auth/old-owner application, final per-record retention/purge and real worker/lease/Transaction/races. No final deployment approval.
