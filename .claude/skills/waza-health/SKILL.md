---
name: "waza-health"
description: "Waza: Engineering health audit — check agent config, hooks/MCP, maintainability"
---

---
name: "waza-health"
description: "Waza: Budget-aware agent-assisted engineering health audit — check instruction/config drift, hooks/MCP, verifier surfaces, and AI maintainability. For 检查claude/健康度/配置检查. Not for debugging code or reviewing PRs."
---

# Health: Agent-Assisted Engineering Health

Prefix your first line with 🥷 inline. Audit the current project's agent setup and AI coding maintainability.

## Outcome Contract
- Outcome: a budget-aware health report that separates agent configuration risk from AI maintainability risk.
- Done when: each finding names the misaligned layer, the concrete evidence, and a copy-pasteable action or diagnostic command.

## Two Lanes
- **Agent config health**: Codex/Claude/Pi instruction drift, permissions, hooks, MCP, skills, memory supply chain
- **AI maintainability health**: project context surface, verifier wrapper, generated-artifact checks, hotspot ownership, stale docs

## Tiers
| Tier | Signal | What's Expected |
|---|---|---|
| Simple | <500 files, 1 contributor, no CI | CLAUDE.md only; 0-1 skills; hooks optional |
| Standard | 500-5K files, small team or CI | CLAUDE.md + 1-2 rules; 2-4 skills; basic hooks |
| Complex | >5K files, multi-contributor, active CI | Full six-layer setup required |

## Finding Severities
[!] Critical — fix now (security, leaked tokens, dangerous allowedTools)
[~] Structural — fix soon (agent instructions in wrong layer, missing hooks, verifier gaps)
[-] Incremental — nice to have

## Key Rules
- Never auto-apply fixes without confirmation
- Never apply complex-tier checks to simple projects
- Start with summary mode; deep audits consume significant token quota — warn before proceeding
