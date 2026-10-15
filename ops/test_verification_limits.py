"""Execute the production Redis Lua against fakeredis[lua], without running OJ submissions."""
import re
from pathlib import Path
import unittest
import fakeredis

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "sit_oj_backend/user/src/main/java/org/example/user/service/VerificationCodeService.java").read_text(encoding="utf-8")
scripts = dict(re.findall(r'(ISSUE|VERIFY) = new DefaultRedisScript<>\("""(.*?)""", Long.class\)', source, re.S))

class VerificationLimitTests(unittest.TestCase):
    def setUp(self):
        self.redis = fakeredis.FakeRedis()
        self.keys = ["cooldown:test", "hour:test", "challenge:register:test", "global:hour"]
    def issue(self):
        return self.redis.eval(scripts["ISSUE"], 4, *self.keys, "expected-hash")
    def verify(self, value):
        return self.redis.eval(scripts["VERIFY"], 1, self.keys[2], value)
    def test_success_consumes_code(self):
        self.assertEqual(1, self.issue())
        self.assertEqual(1, self.verify("expected-hash"))
        self.assertEqual(0, self.verify("expected-hash"))
    def test_five_wrong_guesses_invalidate_even_correct_next_guess(self):
        self.issue()
        for _ in range(5):
            self.assertEqual(0, self.verify("wrong"))
        self.assertEqual(0, self.verify("expected-hash"))
    def test_four_wrong_guesses_still_allow_correct_fifth(self):
        self.issue()
        for _ in range(4):
            self.verify("wrong")
        self.assertEqual(1, self.verify("expected-hash"))
    def test_code_expires(self):
        self.issue()
        self.assertGreater(self.redis.ttl(self.keys[2]), 0)
        self.assertLessEqual(self.redis.ttl(self.keys[2]), 300)
        # Put the deadline strictly in the past, including with a frozen fake clock.
        self.redis.expire(self.keys[2], -1)
        self.assertEqual(0, self.redis.exists(self.keys[2]))
        self.assertEqual(0, self.verify("expected-hash"))
    def test_cooldown_and_email_hourly_quota(self):
        self.assertEqual(1, self.issue())
        self.assertEqual(0, self.issue())
        for _ in range(4):
            self.redis.delete(self.keys[0])
            self.assertEqual(1, self.issue())
        self.redis.delete(self.keys[0])
        self.assertEqual(0, self.issue())
    def test_global_budget_is_shared_between_recipients(self):
        self.redis.set(self.keys[3], 200)
        self.assertEqual(0, self.issue())
    def test_update_code_cannot_be_used_for_registration(self):
        self.keys[2]="challenge:update:test"
        self.issue()
        self.assertEqual(0,self.redis.eval(scripts["VERIFY"],1,"challenge:register:test","expected-hash"))

if __name__ == "__main__":
    unittest.main()
