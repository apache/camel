# Project Guidelines

This rule file contains branching, commit, PR, and task-finding conventions for the project. Commands read this file to determine how to name branches, format commits, and search for tasks.

- **Fix branch:** `fix/<ISSUE_ID>`
- **Feature branch:** `feature/<ISSUE_ID>-<short-slug>`
- **Bugfix branch:** `bugfix/<ISSUE_ID>`
- **Quick-fix branch:** `quick-fix/<short-slug>`
- **Commit format (fix):** `<ISSUE_ID>: <brief description of fix>`
- **Commit format (quick-fix):** `chore: <brief description>`
- **CI-issue branch:** `ci-issue/<short-slug>`
- **Commit format (ci-issue):** `ci: <brief description>`
- **PR creation:** always
- **Backport upgrade-guide policy:** the `docs/user-manual/modules/ROOT/pages/camel-4x-upgrade-guide-4_XX.adoc` files for ALL versions live on `main` and act as the canonical history across all releases. When a PR is backported to a maintenance branch (e.g. `camel-4.18.x`, `camel-4.14.x`) and adds an upgrade-guide note for that release line, the corresponding entry must ALSO be added to the matching `camel-4x-upgrade-guide-4_XX.adoc` file on `main` — either as part of the backport workflow or in a follow-up doc-sync PR. Without this, `main`'s version-specific upgrade guides drift out of sync with what's actually shipping on the maintenance branches.
- **Find-task source:** Jira
- **Find-task beginner JQL:** `project = CAMEL AND status = Open AND labels = good-first-issue` (maxResults=10)
- **Find-task intermediate:** Filter 12352792 (easy issues)
- **Find-task experienced JQL:** `project = CAMEL AND status = Open AND labels = help-wanted` (maxResults=10)
- **Scope-too-large redirect:** create a Jira issue directly
