import importlib.util
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch


ORIGINAL = '''from multiprocessing import Pool
import psutil

def _run(instance, test_case_file_id):
    return instance._judge_one(test_case_file_id)

class JudgeClient:
    def run(self):
        tmp_result = []
        result = []
        pool = Pool(processes=psutil.cpu_count())
        try:
            for test_case_file_id, _ in self._test_case_info["test_cases"].items():
                tmp_result.append(pool.apply_async(_run, (self, test_case_file_id)))
        except Exception as e:
            raise e
        finally:
            pool.close()
            pool.join()
        for item in tmp_result:
            result.append(item.get())
        return result
'''


def load_client():
    sys.modules["psutil"] = types.ModuleType("psutil")
    sys.modules["psutil"].cpu_count = lambda: 4
    spec = importlib.util.spec_from_file_location("patch_judge_client", Path(__file__).with_name("patch_judge_client.py"))
    patcher = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(patcher)
    source = patcher.patch_source(ORIGINAL)
    module = types.ModuleType("judge_client_fixture")
    exec(source, module.__dict__)
    return module


class ImmediateResult:
    def __init__(self, value):
        self.value = value

    def get(self):
        return self.value


class FakePool:
    latest = None

    def __init__(self, processes):
        self.processes = processes
        self.terminated = False
        self.closed = False
        self.joined = False
        FakePool.latest = self

    def apply_async(self, function, args):
        return ImmediateResult(function(*args))

    def imap(self, function, tasks, chunksize=1):
        for task in tasks:
            if self.terminated:
                break
            yield function(task)

    def close(self):
        self.closed = True

    def terminate(self):
        self.terminated = True

    def join(self):
        self.joined = True


class JudgeClientTests(unittest.TestCase):
    def test_original_scheduler_reproduces_eager_execution(self):
        spec = importlib.util.spec_from_file_location("patch_judge_client", Path(__file__).with_name("patch_judge_client.py"))
        patcher = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(patcher)
        self.assertNotEqual(patcher.patch_source(ORIGINAL), ORIGINAL)
        self.assertIn("pool.apply_async(_run", ORIGINAL)

    def test_unexpected_scheduler_is_rejected(self):
        spec = importlib.util.spec_from_file_location("patch_judge_client", Path(__file__).with_name("patch_judge_client.py"))
        patcher = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(patcher)
        with self.assertRaisesRegex(ValueError, "differs"):
            patcher.patch_source(ORIGINAL.replace("pool.apply_async(_run", "pool.apply_async(other"))

    def setUp(self):
        self.module = load_client()
        self.calls = []
        self.client = self.module.JudgeClient.__new__(self.module.JudgeClient)
        self.client._test_case_info = {"test_cases": {"a": {}, "b": {}, "c": {}}}

    def run_cases(self, results):
        self.client._judge_one = lambda case: self.record(case, results[case])
        with patch.object(self.module, "Pool", FakePool):
            return self.client.run()

    def record(self, case, result):
        self.calls.append(case)
        return {"test_case": case, "result": result}

    def test_tle_stops_remaining_cases(self):
        result = self.run_cases({"a": 1, "b": 0, "c": 0})
        self.assertEqual(self.calls, ["a"])
        self.assertEqual(result, [{"test_case": "a", "result": 1}])
        self.assertTrue(FakePool.latest.terminated)
        self.assertTrue(FakePool.latest.joined)

    def test_all_accepted_cases_still_run(self):
        result = self.run_cases({"a": 0, "b": 0, "c": 0})
        self.assertEqual(self.calls, ["a", "b", "c"])
        self.assertEqual(len(result), 3)
        self.assertEqual(FakePool.latest.processes, 1)
        self.assertTrue(FakePool.latest.closed)
        self.assertTrue(FakePool.latest.joined)


if __name__ == "__main__":
    unittest.main()
