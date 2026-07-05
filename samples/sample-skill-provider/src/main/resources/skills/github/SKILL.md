---
name: github
description: Interact with GitHub repositories, issues, and pull requests
user-invocable: true
metadata: {"openclaw": {"requires": {"env": ["GITHUB_TOKEN"]}, "primaryEnv": "GITHUB_TOKEN"}}
---

# GitHub Skill

When the user asks about GitHub repositories, issues, PRs, or code reviews,
use the appropriate GitHub API tools.

## Available Tools

- `github_list_repos` — List repositories for a user or organization
- `github_get_issues` — Fetch open issues for a repository
- `github_get_prs` — List pull requests with their review status
- `github_create_issue` — Create a new issue with labels

## Guidelines

- Always confirm the repository name before querying
- Present issues in a table format with number, title, labels, and assignee
- For PRs, highlight the review status (approved, changes requested, pending)
