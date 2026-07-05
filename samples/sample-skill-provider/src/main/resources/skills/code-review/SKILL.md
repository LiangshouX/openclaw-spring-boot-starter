---
name: code-review
description: Perform code reviews with focus on correctness, performance, and style
user-invocable: true
disable-model-invocation: false
command-dispatch: tool
command-tool: run_code_review
---

# Code Review Skill

When the user asks to review code, a pull request, or a file,
use the `run_code_review` tool to analyze the code.

## Review Dimensions

1. **Correctness** — Logic errors, off-by-one, null safety, concurrency issues
2. **Performance** — Unnecessary allocations, O(n²) where O(n log n) is possible, N+1 queries
3. **Security** — SQL injection, XSS, hardcoded secrets, missing auth checks
4. **Style** — Naming conventions, dead code, overly complex methods

## Output Format

Present findings as a prioritized list:
- 🔴 Critical (blocks merge)
- 🟡 Warning (should fix)
- 🟢 Suggestion (nice to have)
