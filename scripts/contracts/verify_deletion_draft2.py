"""Offline, test-only raw vector check; not a runtime decoder or HTTP oracle."""
import argparse
import hashlib
import json
from decimal import Decimal
from pathlib import Path

FIELDS = ("eventId", "schemaVersion", "userId", "deletionOperationId",
          "attemptGroupId", "sessionId", "deletedAt")


def pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError("duplicate key")
        result[key] = value
    return result


def scalar(value):
    if isinstance(value, str):
        if any(0xD800 <= ord(c) <= 0xDFFF for c in value):
            raise ValueError("non-scalar Unicode")
    elif isinstance(value, dict):
        for key, item in value.items():
            scalar(key)
            scalar(item)
    elif isinstance(value, list):
        for item in value:
            scalar(item)


def decode(raw):
    value = json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=pairs,
                       parse_float=Decimal,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError("constant")))
    scalar(value)
    if not isinstance(value, dict) or set(value) != set(FIELDS):
        raise ValueError("fields")
    if type(value["schemaVersion"]) is not int or value["schemaVersion"] != 1:
        raise ValueError("integer schema required")
    if any(not isinstance(value[k], str) for k in FIELDS if k != "schemaVersion"):
        raise ValueError("string required")
    return value


def quote(value):
    # Contract scalar escaping, independent of producer serializer.
    pieces = ['"']
    for c in value:
        if c in ('"', '\\'):
            pieces.append('\\' + c)
        elif ord(c) < 32:
            pieces.append('\\u%04x' % ord(c))
        else:
            pieces.append(c)
    return ''.join(pieces) + '"'


def canonical(value):
    return ('{' + ','.join(quote(k) + ':' +
            (str(value[k]) if k == "schemaVersion" else quote(value[k]))
            for k in FIELDS) + '}').encode("utf-8")


def verify(root):
    seen = set()
    for line in (root / "SHA256SUMS").read_text().splitlines():
        expected, name = line.split()
        if name not in ("bytes.json", "boundaries.json"):
            raise ValueError("unexpected manifest path")
        assert name not in seen
        seen.add(name)
        assert hashlib.sha256((root / name).read_bytes()).hexdigest() == expected
    assert seen == {"bytes.json", "boundaries.json"}
    data = json.loads((root / "bytes.json").read_bytes())
    bounds = json.loads((root / "boundaries.json").read_bytes())
    assert data["revision"] == bounds["revision"] == "lc-billing-deletion-exact-r2-draft-2"
    assert len(data["cases"]) == 7 and len(bounds["cases"]) == 31
    ids = [v["id"] for v in data["cases"] + bounds["cases"]]
    assert len(set(ids)) == 38
    for case in data["cases"]:
        raw = bytes.fromhex(case["rawHex"]) if "rawHex" in case else case["rawUtf8"].encode("utf-8")
        try:
            value = decode(raw)
        except (ValueError, UnicodeError):
            assert case["id"] in ("B04", "B05", "B06", "B07")
            print(case["id"], "independent strict rejection OK (not HTTP)")
            continue
        assert case["scope"] == "canonical-only"
        actual = canonical(value)
        assert actual == case["expectedCanonicalUtf8"].encode("utf-8")
        assert hashlib.sha256(actual).hexdigest() == case["expectedSha256"]
        print(case["id"], "independent canonical bytes/hash OK")
    # Negative controls ensure parsing is not silently coercing or discarding input.
    baseline = data["cases"][0]["rawUtf8"]
    for malformed in (baseline + '{}', baseline.replace(':1,', ':true,'),
                      baseline.replace(':1,', ':1.0,')):
        try:
            decode(malformed.encode())
        except ValueError:
            continue
        raise AssertionError("negative control accepted")
    print("2 manifest hashes; 7 raw vectors; 3 negative controls OK")
    print("31 boundary scenarios: NOT EXECUTED; full scalar/JWT/HTTP/DB/worker NOT VERIFIED")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture_directory", type=Path)
    verify(parser.parse_args().fixture_directory)
