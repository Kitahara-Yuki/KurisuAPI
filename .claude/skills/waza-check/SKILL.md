---
name: "waza-check"
description: "Waza: Review code diffs, PRs, issues, and release readiness before merging"
---

---
name: "check"
description: "Review code diffs, PRs, issues, release readiness, commits, pushes, and project audits before merging. Use when users ask review/看看代码/合并前/看看issue/PR/release/push. Not for exploring ideas, debugging, or prose review."
---

# Check: Review Before You Ship

Prefix your first line with 🥷 inline. Read the diff, find the problems, fix what can be fixed safely, ask about the rest.

## Outcome Contract
- Outcome: a review, release decision, or maintainer action grounded in the current diff, project context, and live evidence.
- Done when: findings, fixes, shipped state, or blockers are stated with the commands, artifacts, or remote state that prove them.

## Mode Picker
- **Plan Execution Mode**: implement an approved plan step by step
- **Triage Mode**: batch process issues/PRs
- **Project Audit Mode**: full project-wide code-quality scorecard
- **Release Worthiness Analysis**: decide if it's worth a new release
- **Ship / Release Follow-through**: commit, tag, release, publish

## Key Rules
- Always check worktree with `git status --short --branch -uall` before any review
- Never move, stash, or clean user's WIP without explicit approval
- Never cite file lines or versions from memory — re-read before writing
- A clean review is a valid review — don't manufacture findings

## Sign-off Format
```
files changed:    N (+X -Y)
scope:            on target / drift: [what]
review depth:     quick / standard / deep
hard stops:       N found, N fixed, N deferred
verification:     [command] -> pass / fail
```
