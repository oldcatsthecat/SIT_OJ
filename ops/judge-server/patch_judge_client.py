"""Patch the pinned JudgeServer image to stop remaining cases after a TLE."""

import ast
import sys
from pathlib import Path


PATCH_MARKER = "SIT_OJ_EARLY_TLE_PATCH"
RUN_METHOD = """    def run(self):
        # SIT_OJ_EARLY_TLE_PATCH: consume results before all cases finish.
        result = []
        pool = Pool(processes=1)
        try:
            tasks = ((self, case_id) for case_id in self._test_case_info["test_cases"])
            for item in pool.imap(_sit_oj_run_task, tasks, chunksize=1):
                result.append(item)
                if item["result"] in (1, 2):
                    pool.terminate()
                    break
            else:
                pool.close()
        except Exception:
            pool.terminate()
            raise
        finally:
            pool.join()
        return result
"""


def patch_source(source):
    if PATCH_MARKER in source:
        return source
    tree = ast.parse(source)
    judge_class = next((node for node in tree.body
                        if isinstance(node, ast.ClassDef) and node.name == "JudgeClient"), None)
    run_method = next((node for node in judge_class.body
                       if isinstance(node, ast.FunctionDef) and node.name == "run"), None) if judge_class else None
    if run_method is None:
        raise ValueError("JudgeClient.run not found in base image")
    lines = source.splitlines(keepends=True)
    old_method = "".join(lines[run_method.lineno - 1:run_method.end_lineno])
    expected = (
        "tmp_result.append(pool.apply_async(_run, (self, test_case_file_id)))",
        "pool.close()",
        "pool.join()",
        "result.append(item.get())",
    )
    if any(part not in old_method for part in expected):
        raise ValueError("JudgeClient.run differs from the expected 1.6.1 implementation")
    lines[run_method.lineno - 1:run_method.end_lineno] = [RUN_METHOD]
    patched = "".join(lines).rstrip() + "\n\n"
    patched += "def _sit_oj_run_task(task):\n    return _run(*task)\n"
    ast.parse(patched)
    return patched


if __name__ == "__main__":
    target = Path(sys.argv[1] if len(sys.argv) > 1 else "/app/judge_client.py")
    original = target.read_text(encoding="utf-8")
    target.write_text(patch_source(original), encoding="utf-8")
