---
name: "self-improving-agent"
description: "Captures learnings, errors, and corrections to enable continuous improvement. Log errors and user corrections to markdown files, promote important learnings to memory, and detect recurring patterns for preventive rules."
---

---
name: self-improvement
description: "Captures learnings, errors, and corrections to enable continuous improvement. Use when: (1) A command or operation fails unexpectedly, (2) User corrects Claude ('No, that's wrong...', 'Actually...'), (3) User requests a capability that doesn't exist, (4) An external API or tool fails, (5) Claude realizes its knowledge is outdated or incorrect, (6) A better approach is discovered for a recurring task. Also review learnings before major tasks."
---

# Self-Improvement Skill

Log learnings and errors to markdown files for continuous improvement.

## Quick Reference

| Situation | Action |
|-----------|--------|
| Command/operation fails | Log to `.learnings/ERRORS.md` |
| User corrects you | Log to `.learnings/LEARNINGS.md` with category `correction` |
| User wants missing feature | Log to `.learnings/FEATURE_REQUESTS.md` |
| API/external tool fails | Log to `.learnings/ERRORS.md` with integration details |
| Knowledge was outdated | Log to `.learnings/LEARNINGS.md` with category `knowledge_gap` |
| Found better approach | Log to `.learnings/LEARNINGS.md` with category `best_practice` |
| Similar to existing entry | Link with `**See Also**`, consider priority bump |
| Broadly applicable learning | Promote to memory files |
| Workflow improvements | Promote to AGENTS.md |
| Tool gotchas | Promote to TOOLS.md |
| Behavioral patterns | Promote to SOUL.md |

## Working Directory Setup

Create `.learnings/` in the project root:

```bash
mkdir -p .learnings
```

Create the three log files:

### `.learnings/LEARNINGS.md`
```markdown
# Learnings Log

Corrections, knowledge gaps, and best practices discovered during sessions.

## Active Entries (pending/in_progress)

<!-- New entries go here -->

## Resolved Entries

<!-- Resolved entries go here -->
```

### `.learnings/ERRORS.md`
```markdown
# Errors Log

Command failures, exceptions, and unexpected behaviors.

## Active Entries (pending/in_progress)

<!-- New entries go here -->

## Resolved Entries

<!-- Resolved entries go here -->
```

### `.learnings/FEATURE_REQUESTS.md`
```markdown
# Feature Requests Log

Capabilities requested by users that don't currently exist.

## Pending Requests

<!-- New entries go here -->

## Completed Requests

<!-- Completed entries go here -->
```

## Logging Format

### Learning Entry

Append to `.learnings/LEARNINGS.md`:

```markdown
## [LRN-YYYYMMDD-XXX] category

**Logged**: ISO-8601 timestamp
**Priority**: low | medium | high | critical
**Status**: pending
**Area**: frontend | backend | infra | tests | docs | config

### Summary
One-line description of what was learned

### Details
Full context: what happened, what was wrong, what's correct

### Suggested Action
Specific fix or improvement to make

### Metadata
- Source: conversation | error | user_feedback
- Related Files: path/to/file.ext
- Tags: tag1, tag2
- See Also: LRN-20250110-001 (if related to existing entry)

---
```

### Error Entry

Append to `.learnings/ERRORS.md`:

```markdown
## [ERR-YYYYMMDD-XXX] skill_or_command_name

**Logged**: ISO-8601 timestamp
**Priority**: high
**Status**: pending
**Area**: frontend | backend | infra | tests | docs | config

### Summary
Brief description of what failed

### Error
```
Actual error message or output
```

### Context
- Command/operation attempted
- Input or parameters used
- Environment details if relevant

### Suggested Fix
If identifiable, what might resolve this

### Metadata
- Reproducible: yes | no | unknown
- Related Files: path/to/file.ext
- See Also: ERR-20250110-001 (if recurring)

---
```

### Feature Request Entry

Append to `.learnings/FEATURE_REQUESTS.md`:

```markdown
## [FEAT-YYYYMMDD-XXX] capability_name

**Logged**: ISO-8601 timestamp
**Priority**: medium
**Status**: pending
**Area**: frontend | backend | infra | tests | docs | config

### Requested Capability
What the user wanted to do

### User Context
Why they needed it, what problem they're solving

### Complexity Estimate
simple | medium | complex

### Suggested Implementation
How this could be built, what it might extend

### Metadata
- Frequency: first_time | recurring
- Related Features: existing_feature_name

---
```

## ID Generation

Format: `TYPE-YYYYMMDD-XXX`
- TYPE: `LRN` (learning), `ERR` (error), `FEAT` (feature)
- YYYYMMDD: Current date
- XXX: Sequential number or random 3 chars

Examples: `LRN-20250115-001`, `ERR-20250115-A3F`, `FEAT-20250115-002`

## Resolving Entries

When an issue is fixed, update the entry:

1. Change `**Status**: pending` → `**Status**: resolved`
2. Add resolution block after Metadata:

```markdown
### Resolution
- **Resolved**: 2025-01-16T09:00:00Z
- **Notes**: Brief description of what was done
```

Other status values:
- `in_progress` - Actively being worked on
- `wont_fix` - Decided not to address (add reason in Resolution notes)
- `promoted` - Elevated to permanent memory

## Promoting to Memory

When a learning is broadly applicable (not a one-off fix), promote it to permanent memory.

### When to Promote

- Learning applies across multiple areas
- Knowledge any future session should know
- Prevents recurring mistakes
- Documents important conventions

### Promotion Targets

| Memory Type | What Belongs There |
|------------|-------------------|
| `user` memory | User preferences, role, goals, responsibilities |
| `feedback` memory | Behavioral guidance, confirmed approaches |
| `project` memory | Project context, decisions, timelines |
| `reference` memory | Where to find info in external systems |

### How to Promote

1. **Distill** the learning into a concise rule or fact
2. **Save** as a memory file with appropriate type
3. **Update** original entry:
   - Change `**Status**: pending` → `**Status**: promoted`
   - Add `**Promoted**: memory type`

### Promotion Examples

**Learning** (verbose):
> User prefers Chinese for all conversations. Attempted English but was corrected.

**In user memory** (concise):
```markdown
---
name: chinese-language-preference
description: User prefers Chinese for all communication
type: user
---
Always communicate in Chinese unless explicitly asked otherwise.
```

## Recurring Pattern Detection

If logging something similar to an existing entry:

1. **Search first**: `grep -r "keyword" .learnings/`
2. **Link entries**: Add `**See Also**: ERR-20250110-001` in Metadata
3. **Bump priority** if issue keeps recurring
4. **Consider systemic fix**: Recurring issues often indicate:
   - Missing documentation (→ promote to memory)
   - Missing automation (→ suggest creating a scheduled task)
   - Architectural problem (→ flag to user)

## Periodic Review

Review `.learnings/` at natural breakpoints:

### When to Review
- Before starting a new major task
- After completing a feature
- When working in an area with past learnings

### Quick Status Check
```bash
# Count pending items
grep -h "Status\*\*: pending" .learnings/*.md | wc -l

# List pending high-priority items
grep -B5 "Priority\*\*: high" .learnings/*.md | grep "^## \["

# Find learnings for a specific area
grep -l "Area\*\*: backend" .learnings/*.md
```

### Review Actions
- Resolve fixed items
- Promote applicable learnings
- Link related entries
- Escalate recurring issues

## Detection Triggers

Automatically log when you notice:

**Corrections** (→ learning with `correction` category):
- "No, that's not right..."
- "Actually, it should be..."
- "You're wrong about..."
- "That's outdated..."

**Feature Requests** (→ feature request):
- "Can you also..."
- "I wish you could..."
- "Is there a way to..."
- "Why can't you..."

**Knowledge Gaps** (→ learning with `knowledge_gap` category):
- User provides information you didn't know
- Documentation you referenced is outdated
- API behavior differs from your understanding

**Errors** (→ error entry):
- Command returns non-zero exit code
- Exception or stack trace
- Unexpected output or behavior
- Timeout or connection failure

## Priority Guidelines

| Priority | When to Use |
|----------|-------------|
| `critical` | Blocks core functionality, data loss risk, security issue |
| `high` | Significant impact, affects common workflows, recurring issue |
| `medium` | Moderate impact, workaround exists |
| `low` | Minor inconvenience, edge case, nice-to-have |

## Area Tags

| Area | Scope |
|------|-------|
| `frontend` | UI, components, client-side code |
| `backend` | API, services, server-side code |
| `infra` | CI/CD, deployment, Docker, cloud |
| `tests` | Test files, testing utilities, coverage |
| `docs` | Documentation, comments, READMEs |
| `config` | Configuration files, environment, settings |

## Best Practices

1. **Log immediately** - context is freshest right after the issue
2. **Be specific** - future agents need to understand quickly
3. **Include reproduction steps** - especially for errors
4. **Link related files** - makes fixes easier
5. **Suggest concrete fixes** - not just "investigate"
6. **Use consistent categories** - enables filtering
7. **Promote aggressively** - if in doubt, save to memory
8. **Review regularly** - stale learnings lose value

