#!/usr/bin/env bash
#
# push-to-github.sh — commit and push local soccer-pro changes to GitHub.
#
# Run this from your own Terminal (not through Claude/Cowork) — it needs your
# real SSH key for git@github.com, which the Cowork device bridge doesn't have
# access to.
#
# Usage:
#   ./push-to-github.sh "commit message"     # commits staged+unstaged changes, then pushes
#   ./push-to-github.sh                      # no new commit; just pushes whatever's already committed
#
set -euo pipefail

REPO_DIR="$HOME/Documents/Projects/soccer-pro"
cd "$REPO_DIR"

# Bail out early if a previous `git am` or rebase was left half-applied —
# don't pile a push on top of a broken state.
if [ -d .git/rebase-apply ] || [ -d .git/rebase-merge ]; then
  echo "A rebase/am is still in progress in $REPO_DIR (.git/rebase-apply or .git/rebase-merge exists)."
  echo "Resolve that first — run 'git status' to see what's pending — then re-run this script."
  exit 1
fi

BRANCH="$(git branch --show-current)"
if [ -z "$BRANCH" ]; then
  echo "Not on a branch (detached HEAD?). Aborting — check 'git status' manually."
  exit 1
fi

echo "Repo:   $REPO_DIR"
echo "Branch: $BRANCH"
echo

# Commit local changes, if any and if a message was given.
if [ -n "$(git status --porcelain)" ]; then
  if [ $# -eq 0 ]; then
    echo "You have uncommitted changes but didn't pass a commit message."
    echo "Either pass one (./push-to-github.sh \"message\") or commit manually, then re-run."
    git status --short
    exit 1
  fi
  echo "Staging and committing local changes..."
  git add -A
  git commit -m "$1"
else
  echo "No uncommitted changes to commit."
fi

echo
echo "Fetching from origin to check for upstream changes..."
git fetch origin

AHEAD_BEHIND="$(git rev-list --left-right --count "origin/${BRANCH}...${BRANCH}" 2>/dev/null || echo "0	0")"
BEHIND="$(echo "$AHEAD_BEHIND" | cut -f1)"
AHEAD="$(echo "$AHEAD_BEHIND" | cut -f2)"

if [ "$BEHIND" -gt 0 ]; then
  echo "origin/${BRANCH} has $BEHIND commit(s) you don't have locally."
  echo "Pull/rebase first (e.g. 'git pull --rebase origin $BRANCH') so you don't overwrite remote work, then re-run."
  exit 1
fi

if [ "$AHEAD" -eq 0 ]; then
  echo "Nothing to push — local and origin/${BRANCH} already match."
  exit 0
fi

echo "Pushing $AHEAD commit(s) to origin/${BRANCH}..."
git push origin "$BRANCH"

echo
echo "Done. Pushed to https://github.com/rsolter/soccer-pro/tree/${BRANCH}"
