# Git branch manual

# branch info
- main - main repository for public and release 
- develop - development repository
- name/main your upstream FIT passau repository
- name/develop (optional)

all branch must be pushed into the upstream if ready (except your own /develop)

---

# Git Commands

## Remote

```bash
# Add a remote
git remote add <remote-name> <url>

# Check remotes
git remote -v
```

## Push

```bash
# Push local branch to a different remote branch name
git push <remote-name> <local-branch>:<remote-branch>

# Example: push local sirisuk/main to main on sirisuk remote
git push sirisuk sirisuk/main:main

# Set upstream and push
git push -u <remote-name> <local-branch>:<remote-branch>
```

## Pull

```bash
# Pull remote branch into a different local branch name
git pull <remote-name> <remote-branch>:<local-branch>

# Example: pull main from sirisuk remote into local sirisuk/main
git pull sirisuk main:sirisuk/main
```

## Fetch & Checkout

```bash
# Fetch all branches from remote
git fetch <remote-name>

# Create local branch from remote tracking ref
git checkout -b <local-branch> <remote-name>/<remote-branch>

# Example
git checkout -b sirisuk/main sirisuk/main
```

## Merge

```bash
# Normal merge
git merge <branch>

# Merge two branches with unrelated histories (no common commit)
git merge <branch> --allow-unrelated-histories
```

## Delete Remote Branch

```bash
git push <remote-name> --delete <branch>

# Shorthand
git push <remote-name> :<branch>
```
