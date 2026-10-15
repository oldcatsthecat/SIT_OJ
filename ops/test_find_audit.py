import argparse
from contextlib import redirect_stdout, redirect_stderr
import gzip
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("find_audit", Path(__file__).with_name("find_audit.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class AuditSearchTests(unittest.TestCase):
    def args(self, **values):
        defaults = dict(fingerprint=None, request_id=None, submission_id=None, user_id=None, limit=1000)
        defaults.update(values)
        return argparse.Namespace(**defaults)

    def test_bearer_and_raw_auth_have_same_fingerprint(self):
        self.assertEqual(audit.token_fingerprint(" Bearer token-example "), audit.token_fingerprint("token-example"))
        self.assertEqual(64, len(audit.token_fingerprint("token-example")))
        with self.assertRaises(ValueError):
            audit.token_fingerprint(" ")

    def test_claimed_victim_is_not_treated_as_authenticated_actor(self):
        record = dict(actorUserId=77, claimedUserId=88, submittedUserId=77)
        self.assertTrue(audit.matches(record, self.args(user_id=77)))
        self.assertFalse(audit.matches(record, self.args(user_id=88)))

    def test_searches_issued_and_used_tokens_in_current_and_compressed_logs(self):
        fingerprint = audit.token_fingerprint("token-example")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "audit.ndjson").write_text(json.dumps(dict(tokenFingerprint=fingerprint, actorUserId=77))
                                              + '\n{"partial":\n[]\n', encoding="utf-8")
            with gzip.open(root / "audit.2026-10-11.0.ndjson.gz", "wt", encoding="utf-8") as stream:
                stream.write(json.dumps(dict(issuedTokenFingerprint=fingerprint, issuedUserId=77)) + "\n")
            with gzip.open(root / "access.ndjson.2.gz", "wt", encoding="utf-8") as stream:
                stream.write(json.dumps(dict(tokenFingerprint=fingerprint)) + "\n")
            output = io.StringIO()
            with redirect_stdout(output):
                count = audit.search(self.args(log_dir=root, fingerprint=fingerprint))
            self.assertEqual(3, count)
            records = [json.loads(line) for line in output.getvalue().splitlines()]
            self.assertTrue(all("logFile" in record for record in records))
            self.assertNotIn("token-example", output.getvalue())

    def test_submission_search_and_result_limit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "audit.ndjson").write_text('\n'.join(json.dumps(dict(submissionId=i)) for i in (12, 13, 13)), encoding="utf-8")
            output, errors = io.StringIO(), io.StringIO()
            with redirect_stdout(output), redirect_stderr(errors):
                count = audit.search(self.args(log_dir=root, submission_id=13, limit=1))
            self.assertEqual(1, count)
            self.assertEqual(13, json.loads(output.getvalue())["submissionId"])
            self.assertIn("limit", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
