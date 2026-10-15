# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `oldcatsthecat/SIT_OJ`. Use `gh -R oldcatsthecat/SIT_OJ` so the Gitee remote cannot change the target.

## Issue operations

- Create: `gh issue create -R oldcatsthecat/SIT_OJ --title "..." --body "..."`
- Read: `gh issue view <number> -R oldcatsthecat/SIT_OJ --json number,title,body,labels,comments`
- List: `gh issue list -R oldcatsthecat/SIT_OJ --state open --json number,title,body,labels,comments`; add `--label` or change `--state` as needed.
- Comment: `gh issue comment <number> -R oldcatsthecat/SIT_OJ --body "..."`
- Label: `gh issue edit <number> -R oldcatsthecat/SIT_OJ --add-label "..."` or `--remove-label "..."`
- Close: `gh issue close <number> -R oldcatsthecat/SIT_OJ --comment "..."`

Use a body file for multiline issue text. When a skill says "publish to the issue tracker", create a GitHub issue. When it says "fetch the relevant ticket", read the GitHub issue.

## Pull requests as a triage surface

**PRs as a request surface: no.**

## Wayfinding operations

The map is one issue labelled `wayfinder:map`. Its child tickets are GitHub sub-issues when available; otherwise, add each child to a task list in the map and put `Part of #<map>` in the child body. Label child tickets `wayfinder:<type>` (`research`, `prototype`, `grilling`, or `task`).

Represent blockers with native GitHub issue dependencies when available. Otherwise, put `Blocked by: #<n>, #<n>` at the top of the child body. The frontier is the first open, unassigned child in map order whose blockers are closed.

Claim a ticket with `gh issue edit <n> -R oldcatsthecat/SIT_OJ --add-assignee @me`. To resolve it, comment with the answer, close it, and add a link and short summary to the map's Decisions-so-far.
