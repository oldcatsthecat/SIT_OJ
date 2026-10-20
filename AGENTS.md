## Agent skills

### Issue tracker

Track issues in GitHub Issues for `oldcatsthecat/SIT_OJ`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the five default triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

Use a single-context layout. See `docs/agents/domain.md`.

### Remote deployment

When connecting to the OJ server or pushing deployment files to it, read `docs/agents/server-connection.md` and use the remote host and project directory recorded there. The repo-local skill is `.agents/skills/oj-remote-deploy/SKILL.md`.

### GitHub pushes and pull requests

When publishing a branch or submitting a PR to GitHub, follow `.agents/skills/github-api-push/SKILL.md`. Use its GitHub API publishing flow for subsequent pushes; the Git transport to `github.com` is unavailable in this environment.
