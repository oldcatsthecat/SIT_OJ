---
name: github-api-push
description: Publish a local Git branch and create or update a GitHub PR through the GitHub API when github.com Git transport is unavailable. Use for GitHub pushes and PR submissions in SIT_OJ.
---

# GitHub API push

This repository uses `oldcatsthecat/SIT_OJ` on GitHub. The Git transport to `github.com:443` can fail while `gh api` still works. For requested GitHub pushes, use the bundled [publish script](scripts/publish.py) to upload the committed tree through GitHub's Git Data API. Use `gh -R oldcatsthecat/SIT_OJ` for PR operations so the Gitee remote cannot redirect them.

## Publish

1. Check the intended changes, run relevant verification, and commit them on a feature branch. The script requires a clean working tree. Do not publish from `main` or `dev`.
2. Check `gh auth status` and run `python .agents/skills/github-api-push/scripts/publish.py --check` to inspect the branch, repository, and remote baseline. Resolve any reported mismatch before publishing.
3. Run `python .agents/skills/github-api-push/scripts/publish.py`. It uploads changed blobs, constructs a tree, verifies that its SHA matches local `HEAD`, creates one GitHub commit with the current remote branch as parent, and creates or advances only that feature branch. A remote tree that does not match a local ancestor stops the update; do not force it. If the branch is new, the remote base commit must be a local ancestor.
4. Create the PR with `gh pr create -R oldcatsthecat/SIT_OJ --base main --head <branch> --title <title> --body-file <file>`, or inspect an existing PR with `gh pr view`. Verify the PR's base, head, changed files, and open state. The repo's `pr` skill can guide the body.

The GitHub API may normalize commit metadata, so the remote commit SHA can differ from local `HEAD` even when the tree SHA is identical. The script verifies content equality. Do not assume local and remote histories are interchangeable or use a force push to reconcile them. On subsequent updates, the script checks that the remote tree matches a local ancestor and creates a fast-forward commit on the remote branch. Stop and investigate if this check fails.

The script publishes only when the user has authorized a GitHub push or PR. Creating or updating a PR remains a separate, explicit step. It does not merge PRs.
