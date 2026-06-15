---
name: "refactoring-guide"
description: "Safe code refactoring — Fowler's catalog, legacy code strategies, behavior-preserving transformations"
---

---
name: "refactoring-guide"
description: "Safe code transformation — changing structure without changing behavior. From Fowler's catalog to legacy code strategies. Use when refactor/refactoring/clean up/legacy code/code smell/restructure/technical debt/rewrite/extract"
---

# Refactoring Guide

## Core Principles
1. Small steps with tests — refactor in tiny increments, verify after each
2. Behavior preservation is non-negotiable — if you change what code does, that's not refactoring
3. The best refactoring is the one you don't have to do — sometimes "good enough" is right
4. Legacy code is code without tests — fix that first
5. Incremental always beats big-bang — rewrites almost always fail

## Contrarian Insights
- "Rewrite from scratch" is almost always wrong. Strangler fig pattern always.
- Refactoring during feature work is dangerous. Separate commits, separate branches.
- Code smells are symptoms, not diseases. Refactor only when the smell causes actual pain.
- Characterization tests: when inheriting legacy code without tests, write tests that capture what it DOES do, then refactor safely.

## Techniques
- Extract method, rename, inline, move method
- Fowler's catalog of refactorings
- Legacy code strategies: sprout method, sprout class, wrap method
