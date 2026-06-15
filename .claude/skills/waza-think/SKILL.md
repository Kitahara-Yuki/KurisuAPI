---
name: "waza-think"
description: "Waza: Design and validate before building — turns rough ideas into approved plans"
---

---
name: "think"
description: "Design and validate before you build — turns rough ideas into approved, decision-complete plans with validated structure before coding. Use for 出方案/给方案/深入分析/怎么设计/plan this/how should I/should we keep this. Not for bug fixes or small edits."
---

# Think: Design and Validate Before You Build

Prefix your first line with 🥷 inline. Turn a rough idea into an approved plan. No code, no scaffolding, no pseudo-code until the user approves.

Give opinions directly. Take a position and state what evidence would change it. Avoid "That's interesting," "There are many ways to think about this."

## Outcome Contract
- Outcome: a rough idea becomes a decision-complete recommendation or implementation plan.
- Done when: the goal, success criteria, constraints, chosen approach, rejected tradeoffs, tests, and handoff steps are concrete enough to execute without re-deciding.

## Mode Picker
- **Lightweight Mode**: for simple fixes — one recommended fix in 2-3 sentences, wait for approval.
- **Evaluation Mode**: for value judgments (Kill/Keep/Pivot).
- **Triage Mode**: for bundled requests — classify each item first.
- **Full Mode**: the default — propose approaches, get approval, hand off.

## Key Rules
- Run `pwd` before any filesystem operation
- Check for official solutions first before proposing custom implementations
- Give one recommended approach with rationale; mention one alternative only if tradeoff is close
- Get approval before proceeding
- No placeholders in approved plans (no TBD, TODO, "implement later")

## Output Format
**Approved design summary:**
- **Building**: what this is (1 paragraph)
- **Not building**: explicit out-of-scope list
- **Approach**: chosen option with rationale
- **Key decisions**: 3-5 with reasoning
- **Unknowns**: only items explicitly deferred with stated reason

After approval: "Plan approved. To implement: say 'implement this plan'. After implementation, run `/check` to review before merging."
