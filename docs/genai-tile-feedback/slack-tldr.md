# Slack TL;DR — Tanzu GenAI tile feedback

> Paste-ready for #genai-platform or whichever channel reaches the
> Tanzu GenAI tile team. Two variants below: short and long.

---

## Variant A — short (~80 words, fits in one Slack message)

> 👋 Hey Tanzu GenAI team — built a Spring AI app (`wiki-chat`) on CDC
> against `tanzu-all-models` and hit 7 spec gaps in the proxy. Most
> important: `reasoning_effort` returns 422 with no body, even on the
> gpt-oss models we ship that support it natively. Forces every Spring
> AI / OpenAI SDK consumer onto fragile system-prompt workarounds. Full
> writeup with verbatim repros, industry comparison, and 8 backlog
> items here:
> https://github.com/nkuhn-vmw/cf-llama-chat/blob/feature/llm-wiki/docs/genai-tile-feedback/executive-summary.md
> Live test app: https://wiki-chat.apps.tas-cdc.kuhn-labs.com — happy
> to walk through.

---

## Variant B — slightly longer (~180 words, threaded)

**Top message:**

> 👋 Tanzu GenAI team — wanted to share findings from building a
> Spring AI reference app (`wiki-chat`, live on CDC) against the
> `tanzu-all-models` plan. The CF service-binding integration is great
> and is the right reason customers pick this tile. But the proxy's
> request/response surface is a strict subset of OpenAI Chat
> Completions, and it's missing fields that have become table-stakes
> for any 2026 LLM gateway — including fields the gpt-oss models we
> already ship support natively at the runtime layer.
>
> Quick top-3:
> 1. `reasoning_effort` → 422 with no body (every Spring AI
>    `OpenAiChatOptions.reasoningEffort()` call dies on contact)
> 2. Same model under two IDs with divergent tool-calling behavior
>    (`gpt-oss:20b` vs `openai/gpt-oss-20b`)
> 3. Errors come back as bare 422 with no JSON body — debugging is
>    bisection by hand
>
> Two writeups, one for engineering, one for PM:
> 📄 Exec summary: https://github.com/nkuhn-vmw/cf-llama-chat/blob/feature/llm-wiki/docs/genai-tile-feedback/executive-summary.md
> 🔧 Technical (verbatim repros + acceptance criteria): https://github.com/nkuhn-vmw/cf-llama-chat/blob/feature/llm-wiki/docs/genai-tile-feedback/technical-deficiency-report.md
>
> Live repro app: https://wiki-chat.apps.tas-cdc.kuhn-labs.com (login
> in DM if you want it). Happy to walk through in 30 min any time.

**Reply in thread:**

> The technical doc is structured so an engineering agent can pick it
> up directly — each of the 7 deficiencies (D1–D7) has a verbatim
> repro, upstream spec reference, currently-shipped workaround in
> `wiki-chat` with file paths, proposed fix, and paste-into-test-suite
> acceptance criteria. The 8 asks (R1–R8) are ordered by impact and
> include effort/risk estimates plus a 4-stage rollout plan that's
> additive throughout (no breaking changes for existing consumers).
>
> If you'd rather see this as a GitHub issue against the right repo,
> just point me at it and I'll port it over.

---

## Notes for whoever sends this

- The links above point to the **branch URL**. Once `feature/llm-wiki`
  merges to `main`, update the URLs to drop `/feature/llm-wiki/` →
  `/main/`. Or just use the PR URL: https://github.com/nkuhn-vmw/cf-llama-chat/pull/31
- Don't paste the admin password in Slack. Send it via DM to whoever
  asks for app access.
- If the tile team has a public tracker, the technical report is
  formatted to convert directly into 8 issues (one per `R*` ask).
  Each section has a self-contained title, repro, fix, and acceptance
  criteria — copy-paste each `R*` block into a new issue body.
- The author would benefit from a thread response confirming receipt
  even if there's no immediate answer on the asks — silence is hard to
  plan around.
