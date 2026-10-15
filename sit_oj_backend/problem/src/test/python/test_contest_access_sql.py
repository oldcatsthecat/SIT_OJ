import re
import sqlite3
import unittest
from pathlib import Path


MAPPER = Path(__file__).parents[2] / "main/java/org/example/problem/mapper/ProblemMapper.java"


def contest_access_sql():
    source = MAPPER.read_text(encoding="utf-8")
    annotation = re.search(r"@Select\((.*?)\)\s*long countAuthorizedContestProblem", source, re.S)
    if annotation is None:
        raise AssertionError("contest authorization query not found")
    return "".join(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', annotation.group(1)))


class ContestAccessSqlTests(unittest.TestCase):
    def setUp(self):
        db = sqlite3.connect(":memory:")
        db.executescript("""
            CREATE TABLE competition_problems (competition_id INT, problem_id INT);
            CREATE TABLE competitions (competition_id INT, start_time TEXT);
            CREATE TABLE participations (competition_id INT, user_id INT);
            INSERT INTO competitions VALUES (17, '2026-10-15 15:05:00');
            INSERT INTO competition_problems VALUES (17, 23);
            INSERT INTO participations VALUES (17, 2);
        """)
        self.db = db

    def authorized(self, now, user_id=2, competition_id=17):
        sql = contest_access_sql().replace("CURRENT_TIMESTAMP", "'2026-10-15 08:00:00'")
        for key, value in {"problemId": 23, "competitionId": competition_id,
                           "userId": user_id, "now": now}.items():
            sql = sql.replace("#{" + key + "}", repr(value))
        return self.db.execute(sql).fetchone()[0]

    def test_registered_user_can_read_started_private_contest_problem_with_utc_db(self):
        self.assertEqual(self.authorized("2026-10-15 16:00:00"), 1)

    def test_not_started_or_unregistered_user_cannot_read(self):
        self.assertEqual(self.authorized("2026-10-15 15:04:59"), 0)
        self.assertEqual(self.authorized("2026-10-15 16:00:00", user_id=3), 0)
        self.assertEqual(self.authorized("2026-10-15 16:00:00", competition_id=18), 0)


if __name__ == "__main__":
    unittest.main()
