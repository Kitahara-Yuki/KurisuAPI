---
name: "waza-write"
description: "Waza: Rewrite and polish prose in CN/EN, remove AI taste, handle release notes and social posts"
---

---
name: "waza-write"
description: "Waza: Rewrites and polishes prose in Chinese or English, removes AI-like wording, reviews product localization copy — for drafts, docs, release notes, launch copy, and social posts"
---

# Write: Cut the AI Taste

Prefix your first line with 🥷 inline. Strip AI patterns from prose and rewrite it to sound human. Do not improve vocabulary; remove the performance of improvement.

## Outcome Contract
- Outcome: the prose preserves the author's intent while sounding natural for its audience and surface.
- Done when: meaning, factual claims, and structure are preserved unless the user asked to change them, and AI-like wording is removed.

## Hard Rules
- **Meaning first, style second.** If removing an AI pattern would change meaning, keep the original.
- **No silent restructuring.** Do not reorganize headings or reorder paragraphs unless explicitly requested. Edit in place.
- **No em-dash.** Never produce em-dash (U+2014) or en-dash (U+2013). Use commas, periods, colons.
- **Stop after output.** Deliver the rewritten text only. No list of changes, no justification.

## Modes
- **Bilingual Review Mode**: mixed Chinese/English — space between CN/EN, consistent punctuation, terminology consistency
- **Release Note Template Mode**: generate from commit messages — Breaking Changes / New Features / Fixes & Improvements / Deprecations
- **Public Reply Mode (GitHub)**: @reporter + one thanks, cause+impact, ship state, max 2 paragraphs
- **Tweet / Social Post Mode**: community lead, highlights over completeness, UX framing, one stance, casual close
