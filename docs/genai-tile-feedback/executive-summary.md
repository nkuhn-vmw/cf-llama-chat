# Tanzu GenAI Tile — Executive Summary

**For**: Tanzu Platform GenAI Product Manager
**From**: Nick Kuhn (`cf-llama-chat` / `wiki-chat` reference application)
**Date**: 2026-04-09
**Decision requested**: prioritize 8 backlog items into the next 1–2 sprints

---

## TL;DR

The GenAI tile's CF service-binding integration is **best-in-class** and is
the right reason customers choose it. But its **request/response surface
is currently a strict subset of the OpenAI Chat Completions API**, and
that subset is missing fields that have become table-stakes for any LLM
gateway in 2026 — including fields that the **gpt-oss models we ship**
natively support at the runtime layer.

The result: a Spring AI / OpenAI SDK / LangChain developer who writes
code that works against OpenAI directly, against OpenRouter, against
LiteLLM, against Azure OpenAI Service, or against Cloudflare AI Gateway
**will hit HTTP 422 the first time their code touches our tile**. They
will then either work around the gaps, leave the platform, or never
adopt in the first place.

This report identifies **7 specific deficiencies** (`D1`–`D7`) and
proposes **8 backlog items** (`R1`–`R8`) to close them. Six of the eight
are small-to-medium engineering work; none require architectural
changes; all are non-breaking and additive. The companion technical
report includes verbatim repros, acceptance criteria, and a phased
rollout plan.

---

## What's broken today (in plain English)

1. **The model can't be told to "think more" or "think less"**. The
   industry-standard knob (`reasoning_effort`) is rejected by our proxy
   with no error message. Customers have to fall back to writing
   "please think step by step" in the prompt, which is unreliable.

2. **The same model is sometimes available under two names with
   different behavior**. Tool-calling silently breaks depending on
   which name the developer picked. There is no documentation
   explaining which name is "right".

3. **Per-model tuning controls (vLLM `chat_template_kwargs`,
   `extra_body` passthrough) are blocked**. New runtime features ship
   in vLLM every few months; our proxy can't keep up because it
   validates against a fixed allowlist. Customers can't opt in to new
   features without waiting for us to whitelist each one.

4. **Errors come back as bare HTTP 422 with no body**. A developer who
   sends an unsupported field has to bisect the request by hand to find
   out which field broke it. Every other gateway returns a JSON error
   identifying the offending field. **This single fix would save
   developers hours per month.**

5. **The model list endpoint doesn't say what each model can do**.
   Customers can't write code that dynamically chooses between models
   based on capabilities (does this model support tools? vision?
   reasoning? what's the context window?). They have to hardcode this
   knowledge client-side and update it themselves.

6. **No request trace IDs**. When a customer reports a problem, the
   support team has no proxy-side identifier to grep for. Debugging is
   slower and more expensive than it has to be.

7. **Reasoning models leak their internal thinking into the visible
   chat**. Customers writing chat UIs have to parse out `<think>` tags
   themselves with model-specific code. The OpenAI streaming standard
   (`delta.reasoning_content`) handles this cleanly; we don't.

---

## What our competitors do

| Capability the tile is missing | LiteLLM | Portkey | OpenRouter | Cloudflare AI Gateway | Azure OpenAI Service |
|---|:-:|:-:|:-:|:-:|:-:|
| Pass `reasoning_effort` through | ✅ | ✅ | ✅ | ✅ | ✅ |
| Structured error envelope | ✅ | ✅ | ✅ | ✅ | ✅ |
| Capability metadata on `/v1/models` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Pass `extra_body` through | ✅ | ✅ | ✅ | ✅ | partial |
| `delta.reasoning_content` in stream | ✅ | ✅ | ✅ | ✅ | ✅ |
| Per-request trace headers | ✅ | ✅ | ✅ | ✅ | ✅ |

We are missing **all six** of these on a tile whose entire pitch is
"OpenAI-compatible endpoint for the open-weight models you run on
Tanzu". The compatibility promise is unfulfilled today.

What we have that they don't:

- ✅ CF service binding (zero-key consumer code)
- ✅ BOSH-managed vLLM workers with GPU scheduling
- ✅ OpsManager-installable, on-prem-first deployment

These are real and worth keeping. But they're being undercut by basic
compatibility issues that don't need to be there.

---

## The ask

Approve the following 8 backlog items, in priority order. The companion
**technical report** (`technical-deficiency-report.md`, in the same
folder) contains verbatim repros, code-level fix sketches, and
acceptance criteria for each one — written so the engineering team or
an AI coding agent can pick them up directly without further
clarification.

| # | Item | Effort | Customer impact | Risk |
|---|---|---|---|---|
| **R1** | Pass `reasoning_effort` through to backends that support it | small | **high** — single biggest unblock for Spring AI / OpenAI SDK consumers | low (additive) |
| **R2** | Return OpenAI-shaped error envelopes on every rejection | small | **high** — developer time-to-debug drops from hours to minutes | low (additive) |
| **R3** | Add capability metadata to `/v1/models` (context window, supports_tools, supports_reasoning, vision, etc.) | medium | **high** — enables dynamic UI behavior, automated model selection, kills hardcoded capability tables | low (additive) |
| **R4** | Pass through unknown JSON keys (`extra_body`, `chat_template_kwargs`) instead of rejecting | small | **high** — unblocks per-model inference controls without proxy-side work per field | low (additive) |
| **R5** | Stream reasoning content as `delta.reasoning_content` per OpenAI spec | medium | medium — clean reasoning UX for chat apps; today requires fragile model-specific parsing | low (additive) |
| **R6** | Consolidate to one canonical model ID per backing model (or guarantee identical behavior across IDs) | small | medium — eliminates a class of silent tool-calling bugs | low |
| **R7** | Add `x-tanzu-genai-trace-id` header on every response | trivial | medium — support team gets a one-step debug flow | none |
| **R8** | `fallbacks` field support (auto-retry against alternate models) | medium | medium — resilient consumer apps without custom fallback code | medium |

**Total estimated effort**: 6–8 sprint-weeks of one engineer's time,
spread across 4 sequential stages.

**Recommended phasing** (also in the technical report):

- **Stage 1** (1 sprint, additive only): R2 + R7. Lowest risk,
  highest immediate developer-experience win.
- **Stage 2** (1 sprint): R4. Foundation for everything that follows.
- **Stage 3** (2 sprints): R1 + R5. The reasoning-surface unblock.
  This is where Spring AI / OpenAI SDK code starts working unmodified.
- **Stage 4** (2 sprints): R3 + R6 + R8. Discovery and routing.
  Differentiates the tile from a basic proxy toward being a real
  gateway.

---

## Why this matters now

Three reasons this is timely:

1. **Spring AI 1.1 is the default Spring Boot AI integration on the
   Tanzu Platform.** Any Spring shop adopting AI will reach for it
   first. They will hit D1 within their first hour. We should not be
   the platform that breaks Spring AI's default reasoning options.

2. **The open-weight model ecosystem has shifted to reasoning models.**
   gpt-oss (which we already serve), Qwen3-Reasoning, DeepSeek-R1, and
   the upcoming next generation all expose reasoning controls. If our
   proxy can't pass those controls through, customers will route around
   us — running their own LiteLLM in a CF app, bound to the same vLLM
   workers, just to get a working `reasoning_effort` field.

3. **Every gap above is a story we have to tell during a sales motion.**
   "Yes, you can run LiteLLM on top of our tile to get error envelopes
   and trace IDs" is not the answer we want to give. Closing these gaps
   makes the tile self-sufficient.

---

## What I'm asking for

A **yes/no/needs-discussion** on R1–R8. If yes, I'm happy to:

- Walk the engineering team through the technical report in a
  30-minute session
- Provide live A/B testing against the `wiki-chat` reference app on CDC
  as each item lands
- Update the cf-llama-chat reference app to remove its workarounds as
  each item ships, providing a clean "before/after" demo of the
  developer experience

If no or partial, I'd value a rationale on which items the team is
not pursuing so I can document the gap for future Spring AI consumers
and plan around it.

---

**Companion document**: `technical-deficiency-report.md` (same folder).
~3500 words of verbatim repros, code, acceptance criteria, and rollout
plan, written for the engineering team or an AI coding agent.
