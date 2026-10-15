#!/usr/bin/env python3
"""Search structured, persistent OJ logs; full auth tokens are only read through a hidden prompt."""
import argparse
import getpass
import gzip
import hashlib
import json
from pathlib import Path
import sys

def token_fingerprint(value):
    token = value.strip()
    if token.startswith("Bearer "):
        token = token[7:].strip()
    if not token:
        raise ValueError("Auth token is empty")
    return hashlib.sha256(token.encode("utf-8")).hexdigest()

def matches(record, args):
    if args.fingerprint:
        return args.fingerprint in (record.get("tokenFingerprint"), record.get("issuedTokenFingerprint"))
    if args.request_id:
        return record.get("requestId") == args.request_id
    if args.submission_id is not None:
        return record.get("submissionId") == args.submission_id
    return args.user_id in (record.get("actorUserId"), record.get("issuedUserId"), record.get("submittedUserId"))

def search(args):
    root = args.log_dir.resolve()
    if not root.is_dir():
        raise ValueError("Log directory does not exist: " + str(root))
    count = 0
    # Logback: audit.DATE.N.ndjson.gz; logrotate: access.ndjson.1[.gz].
    files = sorted(root.rglob("*.ndjson*"))
    for path in files:
        if path.is_symlink() or not path.is_file():
            continue
        opener = gzip.open if path.name.endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8", errors="replace") as stream:
            for line in stream:
                try:
                    record = json.loads(line)
                except json.JSONDecodeError:
                    continue  # A concurrently written final line may still be incomplete.
                if not isinstance(record, dict) or not matches(record, args):
                    continue
                record["logFile"] = str(path.relative_to(root))
                print(json.dumps(record, ensure_ascii=False))
                count += 1
                if count >= args.limit:
                    print("Result limit reached; increase --limit to see more.", file=sys.stderr)
                    return count
    return count

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log-dir", type=Path, default=Path(__file__).resolve().parents[1] / "data/logs")
    parser.add_argument("--limit", type=int, default=1000)
    selector = parser.add_mutually_exclusive_group(required=True)
    selector.add_argument("--auth", action="store_true", help="Read a full Authorization value with a hidden prompt")
    selector.add_argument("--fingerprint")
    selector.add_argument("--request-id")
    selector.add_argument("--submission-id", type=int)
    selector.add_argument("--user-id", type=int)
    args = parser.parse_args()
    if args.limit <= 0:
        parser.error("--limit must be positive")
    try:
        if args.auth:
            args.fingerprint = token_fingerprint(getpass.getpass("Authorization (hidden): "))
        count = search(args)
        print(f"Matched {count} records.", file=sys.stderr)
    except (OSError, ValueError, EOFError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
