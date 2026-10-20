"""Publish the current Git tree to a GitHub feature branch via gh api."""

import argparse
import base64
import json
import subprocess
import sys


REPO = "oldcatsthecat/SIT_OJ"
BASE = "main"


def command(*args, input_bytes=None, allowed=(0,)):
    result = subprocess.run(args, input=input_bytes, capture_output=True, check=False)
    if result.returncode not in allowed:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"{' '.join(args)} failed: {detail}")
    return result


def git(*args):
    return command("git", *args).stdout.decode("utf-8").strip()


def api(method, endpoint, body=None, missing_ok=False):
    args = ["gh", "api", "-X", method, f"repos/{REPO}/{endpoint}"]
    data = None
    if body is not None:
        args.extend(("--input", "-"))
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    result = command(*args, input_bytes=data, allowed=(0, 1) if missing_ok else (0,))
    if result.returncode:
        if "HTTP 404" in result.stderr.decode("utf-8", errors="replace"):
            return None
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace").strip())
    return json.loads(result.stdout)


def local_tree(revision):
    entries = {}
    for item in command("git", "ls-tree", "-r", "-z", revision).stdout.split(b"\0"):
        if not item:
            continue
        metadata, path = item.split(b"\t", 1)
        mode, kind, sha = metadata.decode("ascii").split()
        entries[path.decode("utf-8")] = (mode, kind, sha)
    return entries


def remote_tree(sha):
    result = api("GET", f"git/trees/{sha}?recursive=1")
    if result.get("truncated"):
        raise RuntimeError("GitHub truncated the remote tree; no update was made")
    return {item["path"]: (item["mode"], item["type"], item["sha"])
            for item in result["tree"] if item["type"] != "tree"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate without changing GitHub")
    args = parser.parse_args()

    branch = git("branch", "--show-current")
    if not branch or branch in {BASE, "dev"}:
        raise RuntimeError("Switch to a feature branch before publishing")
    if git("status", "--porcelain"):
        raise RuntimeError("Commit or remove working-tree changes before publishing")

    head = git("rev-parse", "HEAD")
    desired_tree_sha = git("rev-parse", "HEAD^{tree}")
    remote_ref = api("GET", f"git/ref/heads/{branch}", missing_ok=True)
    if remote_ref is None:
        parent = api("GET", f"git/ref/heads/{BASE}")["object"]["sha"]
        if command("git", "merge-base", "--is-ancestor", parent, head, allowed=(0, 1)).returncode:
            raise RuntimeError(f"Remote {BASE} ({parent}) is not an ancestor of local HEAD")
        creating = True
    else:
        parent = remote_ref["object"]["sha"]
        creating = False

    parent_tree = api("GET", f"git/commits/{parent}")["tree"]["sha"]
    if not creating and parent_tree not in git("log", "--format=%T", "HEAD").splitlines():
        raise RuntimeError("Remote branch tree does not match a local ancestor; investigate before updating")
    if parent_tree == desired_tree_sha:
        print(f"{branch}: remote tree already matches local HEAD ({desired_tree_sha})")
        return

    old_files = remote_tree(parent_tree)
    new_files = local_tree("HEAD")
    changes = []
    for path in sorted(old_files.keys() | new_files.keys()):
        old, new = old_files.get(path), new_files.get(path)
        if old == new:
            continue
        if new is None:
            changes.append({"path": path, "mode": old[0], "type": old[1], "sha": None})
        else:
            mode, kind, sha = new
            changes.append({"path": path, "mode": mode, "type": kind, "sha": sha})
    print(f"{branch}: {len(changes)} changed paths; remote parent {parent}")
    if args.check:
        return

    for item in changes:
        if item["sha"] is None or item["type"] != "blob":
            continue
        content = command("git", "cat-file", "blob", item["sha"]).stdout
        uploaded = api("POST", "git/blobs", {
            "content": base64.b64encode(content).decode("ascii"), "encoding": "base64"
        })["sha"]
        if uploaded != item["sha"]:
            raise RuntimeError(f"Uploaded blob SHA differs for {item['path']}")

    tree = api("POST", "git/trees", {"base_tree": parent_tree, "tree": changes})["sha"]
    if tree != desired_tree_sha:
        raise RuntimeError(f"Remote tree {tree} differs from local tree {desired_tree_sha}; branch unchanged")

    message = command("git", "log", "-1", "--format=%B").stdout.decode("utf-8").rstrip("\n")
    def identity(prefix):
        values = git("log", "-1", f"--format=%{prefix}n%n%{prefix}e%n%{prefix}I").splitlines()
        return dict(zip(("name", "email", "date"), values))

    commit = api("POST", "git/commits", {
        "message": message, "tree": tree, "parents": [parent],
        "author": identity("a"), "committer": identity("c"),
    })["sha"]
    if creating:
        api("POST", "git/refs", {"ref": f"refs/heads/{branch}", "sha": commit})
    else:
        api("PATCH", f"git/refs/heads/{branch}", {"sha": commit, "force": False})
    print(f"Published {branch}: {commit}; tree {tree}; local HEAD {head}")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
