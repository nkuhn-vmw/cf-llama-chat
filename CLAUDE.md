# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A **second local clone of `cf-llama-chat`**, not a GitHub wiki mirror. `git remote -v` points at `https://github.com/nkuhn-vmw/cf-llama-chat.git` — the same origin as `/Users/nkuhn/claude/cf-llama-chat`. The `-wiki` suffix is misleading.

## What to do here

- Do not edit code here expecting it to be separate from `cf-llama-chat` — it is the same repo. Changes pushed from here land on the same GitHub repo.
- For guidance on the actual app (build, manifests, MCP discovery contract, blue-green SSO gotcha), see `/Users/nkuhn/claude/cf-llama-chat/CLAUDE.md`.
- If you need a true wiki clone, it would be `cf-llama-chat.wiki.git` — this isn't it.
- Safe uses for this directory: a scratch worktree, a second branch checkout, or comparing states. Confirm branch (`git branch --show-current`) before making changes.
