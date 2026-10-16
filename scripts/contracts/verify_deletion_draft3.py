"""Offline clarification arithmetic/format checks, not sender/HTTP validation."""
import argparse
import hashlib
import json
import re
from datetime import datetime
from pathlib import Path
from verify_deletion_draft2 import pairs


def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")) if value else None


def valid_id(value):
    return isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9._-]{1,128}", value) is not None


def verify(root):
    expected, name = (root / "SHA256SUMS").read_text().split()
    assert name == "cases.json"
    raw = (root / name).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == expected
    data = json.loads(raw, object_pairs_hook=pairs)
    assert data["revision"] == "lc-billing-deletion-exact-r2-draft-3"
    assert len(data["retryCases"]) == 6 and len(data["correlationCases"]) == 8
    ids = [c["id"] for c in data["retryCases"] + data["correlationCases"]]
    assert len(set(ids)) == 14
    for c in data["retryCases"]:
        floor = max(filter(None, (instant(c["oldFloor"]), instant(c["candidate"]))), default=None)
        due = max(filter(None, (instant(c["backoffDueAt"]), floor)))
        assert floor == instant(c["expectedFloor"])
        assert due == instant(c["expectedDue"])
        assert (due >= instant(c["deadline"])) == c["review"]
    for c in data["correlationCases"]:
        assert valid_id(c["value"]) == c["valid"]
    for length, expected_valid in ((1, True), (128, True), (129, False)):
        assert valid_id("a" * length) == expected_valid
    response = json.loads(data["positive503"]["rawUtf8"], object_pairs_hook=pairs)
    assert set(response) == {"code", "message", "retryable", "correlationId"}
    assert response["code"] == "BILLING_TEMPORARILY_UNAVAILABLE"
    assert response["message"] == "Billing is temporarily unavailable."
    assert response["retryable"] is True and valid_id(response["correlationId"])
    print("hash1 / schedule arithmetic6 / correlation8+length3 / raw503 structure1 OK")
    print("Not HTTP header parsing, jitter, runtime sender, DB, worker, or 31 scenario execution")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture_directory", type=Path)
    verify(parser.parse_args().fixture_directory)
