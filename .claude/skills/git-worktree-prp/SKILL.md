---
name: git-worktree-prp
description: >
  Manages Git Worktrees as an isolated development environment whenever the user
  invokes /generate-prp or /execute-prp commands. This skill MUST be activated
  automatically any time the user types /generate-prp or /execute-prp, or mentions
  creating/executing a PRP (Product Requirements Prompt / Pull Request Proposal).
  The skill ensures every feature, fix, or experiment runs in its own worktree —
  completely isolated from the main working directory — following a structured
  lifecycle: create worktree → do the work → validate → merge → cleanup.
  Always use this skill when you see /generate-prp, /execute-prp, or any reference
  to starting implementation work that would benefit from branch isolation.
compatibility:
  - requires: git (>=2.15), bash
  - optional: gh (GitHub CLI for PR automation)
---

# Git Worktree PRP Skill

## Philosophy

Every PRP (Product Requirements Prompt) represents a unit of work that should be
isolated from the current state of the repository. Git Worktrees provide the
mechanism: instead of stashing, switching branches, or risking contamination of
the main working directory, each PRP gets its own directory on disk backed by the
same `.git` database. This means two PRPs can be active simultaneously, each with
its own running processes, without interfering with each other.

The agent must NEVER start implementation work directly on the current branch
when a PRP command is invoked. The worktree is the contract.

---

## Phase 1 — /generate-prp

When the user invokes `/generate-prp [description]`, your job is to:

1. **Understand the scope** by asking the minimum questions needed (or extracting
   from context). You need to know: what is the goal, what files/services are
   likely affected, what is the target base branch (usually `main`).

2. **Determine the branch name** using the convention:
   ```
   <type>/<short-slug>
   # Examples:
   feature/product-catalog-aggregate
   fix/domainEventPublisher-null-message
   refactor/event-sourcing-projection
   chore/update-spring-boot-deps
   ```

3. **Create the worktree** in a sibling directory next to the repo root:
   ```bash
   # From inside the repository root
   BRANCH_NAME="feature/product-catalog-aggregate"
   WORKTREE_PATH="../worktree-${BRANCH_NAME//\//-}"

   git worktree add -b "$BRANCH_NAME" "$WORKTREE_PATH" origin/main
   ```
   Always branch off `origin/main` (or the base branch specified by the user)
   to ensure a clean, up-to-date starting point.

4. **Generate the PRP document** inside the worktree at `PRPs/PRP.md`.
   The document must contain the following sections:

   ```markdown
   # PRP: [Title]

   ## Context
   Brief description of why this work is needed. Reference architectural context
   where relevant (e.g., "This affects the Transactional Outbox in mars-order-service").

   ## Goal
   One clear sentence stating what done looks like.

   ## Scope
   - Files / services likely to change
   - Services / components NOT in scope (explicit exclusions prevent scope creep)

   ## Acceptance Criteria
   Numbered list of verifiable conditions that define success. Each criterion
   must be testable — no vague language like "works correctly".

   ## Implementation Plan
   Step-by-step breakdown of the work, ordered by dependency. Each step should
   be granular enough for a single commit.

   ## Risks & Rollback
   Known risks and how to revert if something goes wrong.

   ## Worktree Info
   - Branch: [branch name]
   - Worktree path: [path]
   - Base branch: [main]
   - Created at: [datetime]
   ```

5. **Commit the PRP document** immediately:
   ```bash
   cd "$WORKTREE_PATH"
   git add PRPs/PRP.md
   git commit -m "docs(prp): add PRP for [title]"
   ```

6. **Report back** to the user with a summary: the branch name, worktree path,
   and a brief review of the acceptance criteria. Ask for confirmation before
   proceeding to `/execute-prp`.

---

## Phase 2 — /execute-prp

When the user invokes `/execute-prp` (with or without specifying a PRP), your job is to:

### Step 1 — Locate the active worktree

First, list all worktrees to understand the landscape:
```bash
git worktree list
```

If there is only one feature worktree, use it automatically. If there are multiple,
ask the user which PRP to execute. Never assume.

```bash
# Navigate to the correct worktree
cd "$WORKTREE_PATH"

# Confirm you're in the right place
git branch --show-current
cat PRPs/PRP.md
```

### Step 2 — Sync with base branch before starting

Always pull the latest changes from the base branch to minimize future merge conflicts:
```bash
git fetch origin
git rebase origin/main
# If there are conflicts, report them to the user before proceeding
```

### Step 3 — Execute the implementation plan

Work through the PRP's Implementation Plan step by step. For each step:

- Make the changes in the worktree directory (NOT in the main working directory)
- Run any relevant tests or build commands to verify the step
- Commit with a descriptive message following Conventional Commits:
  ```
  <type>(<scope>): <short description>

  # Examples for Mars Enterprise Kit:
  feat(order): add OrderTotal value object with TDD
  fix(domainEventPublisher): resolve null message in failure handler
  refactor(payment): extract Money to shared domain
  test(inventory): add integration test for reservation
  ```
- Update the PRP document to mark the step as done (use `- [x]` in a checklist)

### Step 4 — Validation checklist

Before considering the work done, run through this checklist inside the worktree:

```bash
# 1. All tests pass
mvn clean verify

# 2. No uncommitted changes remain
git status

# 3. Branch is up to date with base
git fetch origin && git log HEAD..origin/main --oneline

# 4. Review your own diff
git diff origin/main...HEAD --stat
```

Report the output of each check to the user before proceeding.

### Step 5 — Merge strategy

Once the user approves the validation results, offer two paths:

**Option A — GitHub PR (recommended for team workflows):**
```bash
git push origin "$BRANCH_NAME"
gh pr create \
  --title "[PRP] $(head -1 PRPs/PRP.md | sed 's/# PRP: //')" \
  --body "$(cat PRPs/PRP.md)" \
  --base main
```

**Option B — Local merge (for solo or homelab work):**
```bash
git checkout main
git merge --no-ff "$BRANCH_NAME" -m "merge: [PRP] [title]"
```

Always prefer `--no-ff` (no fast-forward) to preserve the merge commit in history,
making it easy to identify PRP boundaries in the log.

### Step 6 — Cleanup

After merge (or after PR creation, if using GitHub):
```bash
# Remove the worktree
git worktree remove "$WORKTREE_PATH"

# Delete the local branch
git branch -d "$BRANCH_NAME"

# Optional: delete remote branch if merged
git push origin --delete "$BRANCH_NAME"

# Confirm cleanup
git worktree list
```

Report to the user that the environment is clean and summarize what was done.

---

## Worktree Naming Convention

Always derive the worktree directory name from the branch name to keep them
predictable and easy to find:

```
Repository root:  ~/Projects/mars-enterprise-kit/
Worktree path:    ~/Projects/worktree-feature-product-catalog-aggregate/
Branch:           feature/product-catalog-aggregate
```

The pattern is: sibling of repo root + `worktree-` + branch name with `/` replaced by `-`.

---

## Concurrent PRPs

If the user is running multiple PRPs in parallel (common when fixing a production
issue while a feature is in progress), be explicit about which worktree you're
operating in at all times. Start every shell command block with a `cd` to the
correct worktree path. Never assume the shell is already in the right place.

---

## Error Handling

**Branch already exists:**
```bash
# Use existing branch instead of creating a new one
git worktree add "$WORKTREE_PATH" "$BRANCH_NAME"
```

**Worktree directory already exists:**
```bash
# Remove stale worktree reference and recreate
git worktree prune
git worktree add -b "$BRANCH_NAME" "$WORKTREE_PATH" origin/main
```

**Cannot checkout branch — already checked out in another worktree:**
This is Git protecting you from a real conflict. Report the existing worktree
location to the user and ask how to proceed. Never force-remove a worktree
without user confirmation.

**Rebase conflicts during sync:**
Stop immediately. Show the user the conflicting files with `git status` and
`git diff`. Do not attempt to auto-resolve merge conflicts — ask the user for
guidance on each conflict.

---

## Quick Reference — Commands Summary

```bash
# === GENERATE PRP ===
git worktree add -b <branch> ../worktree-<slug> origin/main
cd ../worktree-<slug>
# ... write PRP.md, commit ...

# === EXECUTE PRP ===
git worktree list                          # confirm location
git fetch origin && git rebase origin/main # sync first
# ... implement, test, commit per step ...
git diff origin/main...HEAD --stat         # final review

# === CLEANUP ===
git worktree remove ../worktree-<slug>
git branch -d <branch>
git worktree list                          # confirm clean
```

---

## Context for Mars Enterprise Kit

When working in this project context, keep in mind:

- The base branch is typically `main` — confirm with the user on first use.
- Build validation uses Maven: `mvn clean verify` or module-specific `mvn test -pl business`.
- Services are Spring Boot multi-module — check `application.yaml` for environment-specific configs.
- Docker Compose services (PostgreSQL, Redpanda) use environment variables — the worktree inherits the shell environment.
- Kubernetes manifests live in `infra/` — validate with `kubectl apply --dry-run=client -f infra/`.
- Conventional Commit scopes to use: `order`, `inventory`, `payment`, `domainEventPublisher`, `saga`, `infra`, `k8s`, `ci`, `helm`, `docker`.
- Onion Architecture modules per service: `business/`, `data-provider/`, `app/`, `third-party/`.
- Reference implementation lives at: `reference-system/mars-order-service/`.
- PRP documents are saved at `PRPs/` directory inside the worktree.
