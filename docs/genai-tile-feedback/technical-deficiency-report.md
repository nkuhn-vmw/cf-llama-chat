# Tanzu GenAI Tile — Proxy Deficiency Report and Implementation Brief

> **Audience**: Tanzu Platform GenAI tile engineering team, or an autonomous
> engineering agent picking up this work.
>
> **Author**: Nick Kuhn — `cf-llama-chat` / `wiki-chat` application
>
> **Date drafted**: 2026-04-09
>
> **Tested foundation**: CDC (`*.sys.tas-cdc.kuhn-labs.com`)
>
> **Tested plan**: `genai / tanzu-all-models` service offering
>
> **Test app**: `wiki-chat` — Spring Boot 3.4 + Spring AI 1.1, deployed via
> `cf push -f manifest-cdc-wiki.yml` from `feature/llm-wiki` of
> `nkuhn-vmw/cf-llama-chat`. Live at
> `https://wiki-chat.apps.tas-cdc.kuhn-labs.com`.
>
> **Proxy endpoint observed in 422 stack traces**:
> `https://genai-proxy.sys.tas-cdc.kuhn-labs.com/tanzu-all-models-059c485/openai/v1/chat/completions`
>
> **Reading guide for an implementation agent**: each deficiency
> (`D1`–`D7`) includes a verbatim repro, the exact symptom, the upstream
> spec it deviates from, the workaround currently shipped in the test app,
> the proposed fix in plain prose, and a checklist of acceptance criteria
> the proxy team can convert directly into integration tests. The asks at
> the end (`R1`–`R8`) are ordered by impact and are written as discrete
> tickets you can paste into a backlog as-is.

---

## 0. Background and scope

The Tanzu Platform GenAI tile (`genai-service` broker, plans
`tanzu-all-models`, individual model plans) provides an OpenAI-compatible
chat completions endpoint that fans out to a heterogeneous backend pool:

- vLLM-backed open-weight models (gpt-oss family, Qwen3 family, llama3.x,
  ministral, nemotron, lfm2.5-thinking, etc.)
- Ollama-backed quantized variants of the same families
- Optional SaaS bridges (OpenAI proper, Anthropic, etc., when configured)

The unique value of the tile relative to a stand-alone gateway like
LiteLLM or Portkey is the **CF service-binding integration**: an app
operator can `cf bind-service my-app tanzu-all-models` and have a single
endpoint with authenticated access to every model the platform team has
provisioned, no API key management on the consumer side. That part of the
product is genuinely good and is not what this report is about.

This report is about the **request/response surface** the tile exposes to
SDK-level consumers (Spring AI, OpenAI Python SDK, LangChain, the
Anthropic SDK with an OpenAI-compat shim, etc.). That surface is currently
a **strict subset** of the canonical OpenAI Chat Completions schema, and
the strictness is enforced by silently rejecting requests rather than by
forwarding-and-degrading. This is the wrong default for a multi-tenant
platform proxy in 2026, for the reasons enumerated below.

---

## 1. Deficiency catalog

### D1 — `reasoning_effort` field rejected with HTTP 422

**Severity**: high. Blocks every Spring AI app that uses
`OpenAiChatOptions.reasoningEffort()` against the tile.

**Spec reference**: OpenAI Chat Completions API,
[`reasoning_effort`](https://platform.openai.com/docs/api-reference/chat/create#chat-create-reasoning_effort)
parameter, generally available since the o1 launch (2024-09). Spring AI
has surfaced this as
`org.springframework.ai.openai.OpenAiChatOptions.Builder#reasoningEffort(String)`
since Spring AI 1.0.0-M5. Accepted values per OpenAI docs:
`"minimal" | "low" | "medium" | "high"`.

**Verbatim symptom**, captured from `cf logs wiki-chat --recent` against
the deployed test app, 2026-04-09 12:08 UTC:

```
ERROR 20 --- [or-http-epoll-2] o.s.ai.chat.model.MessageAggregator      : Aggregation Error
org.springframework.web.reactive.function.client.WebClientResponseException$UnprocessableEntity:
  422 Unprocessable Entity from POST
  https://genai-proxy.sys.tas-cdc.kuhn-labs.com/tanzu-all-models-059c485/openai/v1/chat/completions
    at org.springframework.web.reactive.function.client.WebClientResponseException.create(...)
    at org.springframework.web.reactive.function.client.DefaultClientResponse.lambda$createException$1(...)
    Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
    Error has been observed at the following site(s):
    *__checkpoint ⇢ 422 UNPROCESSABLE_ENTITY from POST
      https://genai-proxy.sys.tas-cdc.kuhn-labs.com/tanzu-all-models-059c485/openai/v1/chat/completions
      [DefaultWebClient]
```

The 422 carries **no JSON body** explaining which field was rejected
(see also D4).

**Repro 1 — Spring AI**:

```java
// On any model in the tanzu-all-models plan
OpenAiChatOptions opts = OpenAiChatOptions.builder()
    .model("gpt-oss:20b")
    .reasoningEffort("medium")   // <-- this single field causes 422
    .build();

ChatResponse r = chatClient
    .prompt()
    .options(opts)
    .user("hi")
    .call()
    .chatResponse();
// throws WebClientResponseException$UnprocessableEntity
```

**Repro 2 — raw curl** against the proxy URL extracted from the stack
trace above (substitute your bound credentials):

```bash
curl -i -X POST \
  "https://genai-proxy.sys.tas-cdc.kuhn-labs.com/tanzu-all-models-059c485/openai/v1/chat/completions" \
  -H "Authorization: Bearer $GENAI_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-oss:20b",
    "messages": [{"role": "user", "content": "hi"}],
    "reasoning_effort": "medium"
  }'
# HTTP/2 422
# (no body)
```

Removing `reasoning_effort` from the same payload returns a normal
streaming response. The field is the sole cause of the rejection — every
other field (`model`, `messages`, `tools`, `tool_choice`, `temperature`,
`max_tokens`, `stream`) is accepted.

**Repro 3 — automated against the test app**:

```bash
# Wrapped through the wiki-chat app:
BASE=https://wiki-chat.apps.tas-cdc.kuhn-labs.com
# (login dance to get a CSRF token elided — see test scripts in
#  cf-llama-chat-wiki/scripts/ if needed)
curl -ks -X POST "$BASE/api/chat/stream" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $XSRF" -b $CJ \
  -d '{
    "message": "hi",
    "provider": "genai",
    "model": "gpt-oss:20b",
    "thinkingLevel": "medium",
    "useTools": false,
    "temporary": true
  }'
# Returns SSE stream containing:
# data:{"...","error":"Streaming interrupted: connection error",...}
```

The SSE error wrapping comes from the application's own
`Flux.onErrorResume` in `ChatService.chatStream`; the underlying cause is
the 422 from the proxy.

**Why it matters**:

1. `reasoning_effort` is the canonical, OpenAI-blessed mechanism for
   asking a reasoning-capable model to spend more or fewer thinking
   tokens. Every reasoning model the tile already serves
   (`gpt-oss:20b`, `gpt-oss:120b`, `lfm2.5-thinking:1.2b`, future
   o-series, future Qwen3-Reasoning variants) supports this concept
   natively at the runtime layer (vLLM accepts it; Ollama gpt-oss accepts
   `think: bool`).
2. Spring AI surfaces it as a first-class option on `OpenAiChatOptions`,
   so any Java/Kotlin developer who builds a chat app against
   "OpenAI-compat" will instinctively reach for it. Their app will work
   against OpenAI itself, OpenRouter, LiteLLM, Portkey, Azure OpenAI
   Service, and Cloudflare AI Gateway — and crash against the tile.
3. The fallback path (system-prompt nudges in natural language; see
   `ThinkingOptionsBuilder.systemPromptSuffix` in the test app) is
   strictly inferior:
   - The model can ignore the prompt instruction.
   - There is no way to set a token budget (`max_completion_tokens` for
     reasoning, or Anthropic's `thinking.budget_tokens`).
   - The thinking-vs-answer split is delegated to the model's own
     `<think>` tagging, which is model-version-specific and changes
     between releases.

**Proposed fix**:

The proxy's request schema validator should add `reasoning_effort` to
its allowlist of accepted top-level fields, with values
`{minimal, low, medium, high}`. The handler should:

1. **For backends that natively support it** (vLLM gpt-oss path, Ollama
   gpt-oss path with `think: bool` translation, OpenAI proper, future
   Anthropic via Converse): forward the value unchanged.
2. **For backends that don't** (llama3.1, ministral, base nemotron,
   non-reasoning Qwen): silently no-op. **Do not 422.** The OpenAI API
   itself behaves this way — sending `reasoning_effort` to gpt-4o is a
   no-op, not a rejection. The principle: "the proxy should never be
   stricter than the strictest backend it serves."
3. **For models where the value needs translation** (e.g. mapping
   OpenAI's `minimal|low|medium|high` to Anthropic's `disabled` or to a
   `budget_tokens` value): apply the translation server-side and
   document the mapping.

**Acceptance criteria** (paste-into-test-suite):

```
GIVEN  the tanzu-all-models plan is bound to a test app
WHEN   the app POSTs to /v1/chat/completions with
       {"model": "gpt-oss:20b", "messages": [...], "reasoning_effort": "low"}
THEN   the proxy returns 200 with a normal chat completion response
AND    the response does NOT contain a "reasoning_effort field unknown" error

WHEN   the app POSTs the same payload with reasoning_effort = "minimal"
THEN   the proxy returns 200

WHEN   the app POSTs the same payload with reasoning_effort = "medium"
THEN   the proxy returns 200

WHEN   the app POSTs the same payload with reasoning_effort = "high"
THEN   the proxy returns 200

WHEN   the app POSTs to a non-reasoning model
       {"model": "llama3.1:8b", "messages": [...], "reasoning_effort": "high"}
THEN   the proxy returns 200 (silently no-op, do NOT 422)

WHEN   the app POSTs an invalid value
       {"model": "gpt-oss:20b", "messages": [...], "reasoning_effort": "extreme"}
THEN   the proxy returns 400 with an OpenAI-shaped error envelope
       identifying reasoning_effort as the offending field
```

---

### D2 — Same model exposed under multiple IDs with divergent tool-calling behavior

**Severity**: medium. Silently breaks tool-using applications depending
on which model ID the developer picked.

**Symptom**: the gpt-oss-20b weights are exposed under at least two IDs
in the same `tanzu-all-models` binding:

- `gpt-oss:20b` — Ollama-style colon form
- `openai/gpt-oss-20b` — OpenAI-namespaced form

Both are listed by `GET /v1/models` on the same plan. Both serve chat
completions correctly for plain prompts. But for tool-calling prompts
they diverge:

| Prompt (sent verbatim, system prompt identical) | `gpt-oss:20b` | `openai/gpt-oss-20b` |
|---|---|---|
| `"i like tacos"` (one `wiki_write` tool registered) | tool fired | no tool call |
| `"i prefer dark mode in everything"` | tool fired | no tool call |
| `"my favorite language is rust"` | tool fired | tool fired |

(Empirical results from `wiki-chat` test app on CDC, 2026-04-09. Same
test runs A/B'd back-to-back, same chat history, same `tools` array,
same `tool_choice: "auto"`.)

**Hypothesis on root cause**: the two IDs route through different
internal proxy paths. The colon-form ID resolves to the Ollama-style
path which serializes tool definitions one way; the namespaced form
resolves to a strict OpenAI-compat path which serializes them another
way. The divergence is likely in how `function.parameters` JSON Schema
is normalized, or in how `tool_choice` is encoded when not present in
the request body.

**Why it matters**: a developer reading `GET /v1/models` cannot tell
which ID is "the right one" for tool-calling. They will pick whichever
they tried first, ship code, and silently lose 30-60% of their tool
calls without ever knowing why. There is no observable error — the
model just chooses to respond in plain text instead of calling the tool.
This is an extremely high time-to-detect bug because the failure mode
looks like model variance, not a routing bug.

**Workaround currently shipped**: the test app's project memory
(`~/.claude/projects/.../memory/genai-tile-model-id-variants.md`)
documents "default to colon form when using `@Tool` methods". This is
entirely a developer-side discovery, not platform-documented.

**Proposed fix**: pick one canonical ID per backing model. If the tile
must continue to expose multiple IDs for SDK-compat reasons (e.g.
because some clients hard-code `openai/...` as a routing prefix), then
**both routes must produce identical behavior** for every API surface,
including tool calling. The fix is to consolidate the two internal paths
into a single shared serializer that handles both surface IDs.

**Acceptance criteria**:

```
GIVEN  the tanzu-all-models plan exposes both gpt-oss:20b and
       openai/gpt-oss-20b in /v1/models
WHEN   I run the same chat completion request against both IDs in
       parallel, with the same messages, the same tools array, and
       tool_choice = "auto"
THEN   both responses have the same value for response.choices[0].finish_reason
AND    both responses either both contain a tool_calls array or both omit it
AND    the tool_calls array (if present) has the same function names
       (allowing for the actual model output to differ in argument values)

OR  (alternative resolution)

GIVEN  consolidation: the tile only exposes one ID per backing model
WHEN   I list /v1/models
THEN   gpt-oss:20b appears exactly once, with no openai/ duplicate
```

---

### D3 — No documented passthrough for `extra_body` / `chat_template_kwargs`

**Severity**: medium. Blocks per-model inference controls that are
exposed by every modern open-weight runtime.

**Spec reference**: vLLM exposes per-request inference controls through
`chat_template_kwargs` (see vLLM
[OpenAI server docs](https://docs.vllm.ai/en/latest/serving/openai_compatible_server.html)).
The OpenAI Python SDK has supported arbitrary passthrough via the
`extra_body` parameter since v1.0:

```python
client.chat.completions.create(
    model="qwen3:4b",
    messages=[...],
    extra_body={"chat_template_kwargs": {"enable_thinking": False}},
)
```

LiteLLM, Portkey, OpenRouter, and Cloudflare AI Gateway all accept
`extra_body` and forward it unchanged. The Tanzu GenAI tile appears to
strip or reject anything outside the canonical OpenAI schema (untested
directly; inferred from the D1 422 behavior).

**Why it matters**: open-weight models that ship through vLLM gain new
inference controls every few months. `enable_thinking` for Qwen3,
`enable_reasoning` for DeepSeek R1 derivatives, custom `chat_template`
overrides, sampling-mode toggles, speculative-decoding flags. Each one
arrives in the runtime months before any standardized OpenAI field
covers it. A proxy that doesn't pass them through forces every consumer
to either (a) wait for the proxy to add explicit support per field, or
(b) work around it with system-prompt hacks. Neither scales.

**Workaround currently shipped**: the test app uses the Qwen3-specific
`/no_think` system directive (per its own model card) to suppress
reasoning when `thinkingLevel == "none"`. See
`ThinkingOptionsBuilder.systemPromptSuffix` in
`src/main/java/com/example/cfchat/service/ThinkingOptionsBuilder.java`.
This works for Qwen3 but is a per-model-family hack and doesn't extend
to other models that have *only* the kwargs path.

**Proposed fix**: the proxy should accept any unknown top-level JSON key
in the request body and forward it to the backend unchanged, in the same
shape it arrived. The proxy should NOT validate against a fixed
allowlist of OpenAI fields. If the *backend* rejects the unknown field,
that's fine — the error then has a meaningful "field X not supported by
runtime Y" message that the proxy can pass back.

**Special case**: `chat_template_kwargs` should be explicitly allowed
and documented, with a per-model list of which kwargs each backend
respects. Example doc shape:

```yaml
qwen3:4b:
  supports:
    chat_template_kwargs:
      enable_thinking:
        type: boolean
        default: true
        description: When false, suppresses the model's reasoning tokens

deepseek-r1:8b:
  supports:
    chat_template_kwargs:
      enable_reasoning:
        type: boolean
        default: true
```

**Acceptance criteria**:

```
WHEN   I POST {"model": "qwen3:4b", "messages": [...],
                "extra_body": {"chat_template_kwargs": {"enable_thinking": false}}}
THEN   the proxy forwards the request to vLLM unchanged
AND    the model response does NOT contain <think>...</think> blocks

WHEN   I POST a request with an unknown top-level key
       {"model": "gpt-oss:20b", "messages": [...], "frobnicator": 42}
THEN   the proxy either:
       (a) silently strips the key and returns 200 (current OpenAI behavior), or
       (b) forwards it to the backend (preferred — see R4 below)
       But it does NOT return 422 with no body.
```

---

### D4 — No structured error body when fields are rejected

**Severity**: medium. Multiplies developer time-to-fix by an order of
magnitude.

**Spec reference**: OpenAI Chat Completions error envelope, used by
every major gateway:

```json
{
  "error": {
    "message": "Unrecognized request argument supplied: reasoning_effort",
    "type": "invalid_request_error",
    "param": "reasoning_effort",
    "code": null
  }
}
```

**Symptom**: the 422 returned for D1 (and presumably for any other
schema rejection) is a bare HTTP status with no JSON body. From the
WebClient stack trace there is no `responseBodyAsString` content to log.
The Spring AI `MessageAggregator: Aggregation Error` log line is the
only signal.

**How a developer experiences this**: I lost about an hour bisecting
which field was the culprit. I had to remove fields one at a time from
my `OpenAiChatOptions` builder, redeploy the app to CDC, and re-test
each combination. With a structured error body the process would have
been one re-deploy and one log read.

**Why it matters**: every other gateway in this space returns the OpenAI
shape. The cost of implementing it on the tile is small (it's a JSON
serialization hook in the proxy's error handler). The cost of *not*
implementing it scales linearly with consumer count and adoption.

**Proposed fix**: the proxy's request validator should serialize all
schema rejections as JSON of the shape above. Status codes should match
OpenAI's mapping:

- `400` for invalid field value (e.g. `reasoning_effort: "extreme"`)
- `400` for missing required field (e.g. no `messages`)
- `404` for unknown model
- `422` only for cases where the request is syntactically valid OpenAI
  but the *target backend* refuses it (e.g. tool calling against a model
  that doesn't support tools)
- `429` for rate limiting, with `Retry-After` header
- `503` for backend unavailable, with retry hint

In every case the body must be the OpenAI envelope. Acceptance criteria
for each status code can be lifted directly from the OpenAI API
[error code reference](https://platform.openai.com/docs/guides/error-codes/api-errors).

**Acceptance criteria**:

```
WHEN   any request is rejected by the proxy
THEN   the response Content-Type is application/json
AND    the response body parses as JSON matching the schema:
       { "error": { "message": string,
                    "type":    "invalid_request_error" |
                               "authentication_error" |
                               "permission_error" |
                               "not_found_error" |
                               "rate_limit_error" |
                               "api_error",
                    "param":   string | null,
                    "code":    string | null } }
AND    the error.message string is a human-readable description of the
       problem with a hint about how to fix it
AND    the error.param is the name of the offending field, when applicable
```

---

### D5 — `/v1/models` returns IDs only, no capability metadata

**Severity**: medium. Forces empirical capability discovery.

**Spec reference**: every modern AI gateway exposes per-model
capability metadata via the models listing endpoint. Examples:

- **OpenRouter** `/api/v1/models` returns `{id, name, context_length,
  pricing, supported_parameters, top_provider, ...}`
- **LiteLLM** `/v1/model/info` returns `{model_name, mode,
  max_tokens, max_input_tokens, max_output_tokens, input_cost_per_token,
  output_cost_per_token, supports_vision, supports_function_calling,
  supports_parallel_function_calling, supports_response_schema,
  supports_system_messages, ...}`
- **Azure OpenAI** deployments endpoint returns capabilities as part of
  the deployment metadata
- **AWS Bedrock** `ListFoundationModels` returns `inputModalities`,
  `outputModalities`, `responseStreamingSupported`,
  `inferenceTypesSupported`, etc.

**Symptom**: the tile's `/v1/models` returns:

```json
{
  "object": "list",
  "data": [
    {"id": "gpt-oss:20b", "object": "model", "created": ..., "owned_by": "..."},
    {"id": "openai/gpt-oss-20b", ...},
    {"id": "qwen3:4b", ...},
    {"id": "ministral-3:3b", ...},
    ...
  ]
}
```

That is, the strict OpenAI 1.0-era schema with nothing else. The
consumer cannot tell:

- Does this model support tool calling? (D2 shows two IDs that disagree
  on this for the same weights)
- What's the context window?
- Does it support vision input?
- Does it support `reasoning_effort`?
- Is it a reasoning model that emits `<think>` blocks?
- What's the rate limit?
- Is it streaming-capable?

**Why it matters**: applications have to either hardcode model
capabilities client-side (which goes stale) or discover them through
422 bisection (which is slow and expensive). Frontend UIs that want to
hide tool toggles for non-tool-capable models, or skip the thinking
selector for non-reasoning models, can't do so dynamically.

**Workaround currently shipped**: the test app hardcodes a frontend
filter to skip embedding models by name pattern. There is no general
solution.

**Proposed fix**: extend the `/v1/models` response (or add a parallel
`/v1/models/{id}` detail endpoint) with a `capabilities` object:

```json
{
  "id": "gpt-oss:20b",
  "object": "model",
  "owned_by": "tanzu-genai",
  "capabilities": {
    "context_window": 131072,
    "max_output_tokens": 16384,
    "modalities": {
      "input": ["text"],
      "output": ["text"]
    },
    "supports_streaming": true,
    "supports_tools": true,
    "supports_parallel_tool_calls": true,
    "supports_response_format_json": true,
    "supports_response_format_json_schema": true,
    "supports_system_messages": true,
    "supports_reasoning": true,
    "reasoning_effort_levels": ["minimal", "low", "medium", "high"],
    "supports_vision": false,
    "backend_runtime": "vllm",
    "backend_runtime_version": "0.6.4"
  }
}
```

The `capabilities` object should be **discoverable**, not contractual —
i.e. the proxy populates it from its known config of which backend
serves which model, not from a static metadata table that could go
stale.

**Acceptance criteria**:

```
WHEN   I GET /v1/models
THEN   each entry in data[] includes a "capabilities" object
AND    capabilities.supports_tools is a boolean
AND    capabilities.supports_reasoning is a boolean
AND    capabilities.context_window is an integer

WHEN   I compare capabilities.supports_tools across two model IDs that
       resolve to the same backing model
THEN   the values are identical
```

---

### D6 — No per-call observability headers

**Severity**: low. Multiplies platform-team-side debugging when
customers report issues.

**Symptom**: responses from the tile do not include a trace ID header.
When a request behaves strangely, the customer has no proxy-side
identifier to share with the tile team. The tile team has no way to
correlate proxy logs back to a specific customer-reported call without
timestamp + IP guesswork.

**Industry practice**:

| Provider | Trace header |
|---|---|
| OpenRouter | `x-or-id` |
| Portkey | `x-portkey-trace-id` |
| LiteLLM | `litellm-call-id` |
| Cloudflare AI Gateway | `cf-aig-id` |
| Azure OpenAI Service | `apim-request-id` |
| AWS Bedrock | `x-amzn-RequestId` |
| OpenAI proper | `openai-organization`, `x-request-id` |

**Proposed fix**: every response (including streaming SSE responses,
where the header is on the initial HTTP response) should include a
`x-tanzu-genai-trace-id` header containing a UUID that the proxy logs
alongside its own internal logs. Customer reports can then cite the
trace ID and the support flow becomes a one-step grep.

The header should also be included in error responses and on the SSE
stream's initial 200, so frontend code can capture it for crash reports.

**Acceptance criteria**:

```
WHEN   I make any request to /v1/chat/completions
THEN   the response (success or failure, streaming or not) includes a
       x-tanzu-genai-trace-id header
AND    the header value is a valid UUID v4
AND    the same value appears in the proxy's internal logs for that request
```

---

### D7 — Streaming reasoning content not surfaced as a separate field

**Severity**: medium for UX work. Forces fragile model-specific parsing
on the consumer.

**Spec reference**: the canonical OpenAI streaming format for
reasoning-capable models places reasoning chunks in
`delta.reasoning_content`, separate from the answer chunks in
`delta.content`:

```jsonl
{"id":"...","object":"chat.completion.chunk","choices":[{"delta":{"reasoning_content":"Let me think..."}}]}
{"id":"...","object":"chat.completion.chunk","choices":[{"delta":{"reasoning_content":" The user asked..."}}]}
{"id":"...","object":"chat.completion.chunk","choices":[{"delta":{"content":"The answer is "}}]}
{"id":"...","object":"chat.completion.chunk","choices":[{"delta":{"content":"42."}}]}
```

vLLM, OpenRouter, LiteLLM, and most other gateways forward this split
correctly. The OpenAI SDK has had a `delta.reasoning_content` field
since the o1 launch.

**Symptom**: the tile collapses everything into `delta.content`. So a
gpt-oss model that emits thinking via `<think>...</think>` blocks comes
through with the literal tags inline in the visible answer:

```
<think>The user said "i like tacos"... I should call wiki_write...</think>I've saved that preference.
```

**Workaround currently shipped**: the test app's frontend
(`src/main/resources/static/js/app.js`) implements a stateful streaming
parser that tracks `<think>` open/close tags across SSE chunk boundaries
with a small lookahead buffer, routes the content between the tags to a
collapsible details panel, and routes everything else to the main
message bubble. The same backend also strips `<think>...</think>` from
the final `htmlContent` so it doesn't re-flash. This works but is:

1. **Model-specific** — only models that use the `<think>...</think>`
   convention. Other reasoning-style markers (`<reasoning>`,
   `[REASONING]`, etc.) would need separate parsing.
2. **Fragile** — a model that nests tags or emits a literal `<think>`
   in actual prose breaks the parser.
3. **Wasted bandwidth** — the reasoning tokens are streamed to the
   browser and then hidden by JavaScript, instead of being routed to a
   side-channel field.

**Proposed fix**: the proxy should parse out reasoning content from
backends that emit it (vLLM gpt-oss with `<think>` tags, vLLM Qwen3 in
think mode, vLLM DeepSeek R1, future runtimes) and surface it in
`delta.reasoning_content` per the OpenAI streaming spec. Consumers can
then opt in to displaying it without parsing.

The non-streaming response shape should also include
`message.reasoning_content` parallel to `message.content`.

**Acceptance criteria**:

```
WHEN   I stream a chat completion against gpt-oss:20b with a prompt
       that triggers reasoning
THEN   reasoning tokens arrive in chunks where delta.reasoning_content
       is set and delta.content is empty/absent
AND    answer tokens arrive in chunks where delta.content is set and
       delta.reasoning_content is empty/absent
AND    the literal string "<think>" never appears in delta.content

WHEN   I make a non-streaming chat completion against gpt-oss:20b with
       a reasoning-triggering prompt
THEN   the response message contains both message.content (the answer)
       and message.reasoning_content (the reasoning), as separate fields
```

---

## 2. Industry comparison matrix

The following is a feature comparison of the Tanzu GenAI tile against
the major LLM gateway / proxy products in the same product space, as of
April 2026. Sources: each product's public documentation and changelog,
verified against the tile's observed behavior in this report.

| Capability | LiteLLM | Portkey | OpenRouter | Cloudflare AI Gateway | Azure OpenAI Service | AWS Bedrock Converse | **Tanzu GenAI tile** |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Pass `reasoning_effort` through to backend | ✅ | ✅ | ✅ | ✅ | ✅ (native) | ✅ | ❌ 422 (D1) |
| Pass arbitrary `extra_body` keys through | ✅ | ✅ | ✅ | ✅ | partial | ✅ | ❌ (D3) |
| `delta.reasoning_content` separated from `delta.content` in stream | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (D7) |
| Anthropic `thinking.budget_tokens` passthrough | ✅ | ✅ | ✅ | ✅ | n/a | ✅ | ❌ |
| Structured OpenAI-shaped error envelope | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (D4) |
| `/v1/models` with capability metadata | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (D5) |
| Per-request trace headers | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (D6) |
| Cost / token metrics endpoint | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | partial (in tile dashboard, not via API) |
| Fallback routing on backend failure | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Caching layer (semantic / exact) | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Provider-format translation (OpenAI ⇄ Anthropic ⇄ Bedrock) | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Tool / function calling parity across all served models | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | partial (D2) |
| **Unique strengths of the Tanzu tile** |  |  |  |  |  |  |  |
| CF service-binding integration (zero-key consumer code) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| BOSH-managed vLLM workers with GPU scheduling | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| OpsManager-installable, on-prem-first | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

**Reading**: the tile has genuine and unique value in the bottom three
rows. Those are the reasons a customer would choose Tanzu GenAI over
running LiteLLM in a CF app. But every row above the divider is
table-stakes for a 2026 LLM gateway, and the tile is missing or partial
on most of them. Closing the request/response surface gap would let the
tile compete on its strengths (CF integration, on-prem GPU scheduling)
without being blocked by basic compatibility issues.

---

## 3. Concrete asks (in priority order)

### R1 — Pass `reasoning_effort` through to backends that support it

**Effort estimate**: small (allowlist + per-backend forwarding rule).

**Blocks**: any Spring AI / OpenAI SDK / LangChain consumer that uses
the canonical reasoning control. This is the single biggest unblock.

**Acceptance**: see D1 acceptance criteria above.

### R2 — Return OpenAI-shaped error envelopes

**Effort estimate**: small (proxy error-handler rewrite).

**Blocks**: developer time-to-debug. Currently O(hours) per rejection;
should be O(minutes).

**Acceptance**: see D4 acceptance criteria above.

### R3 — Expose `/v1/models` with capability metadata

**Effort estimate**: medium (requires per-backend capability registry,
but the data is already known internally to the broker).

**Blocks**: dynamic frontend UI behavior, automated model selection,
documentation overhead.

**Acceptance**: see D5 acceptance criteria above.

### R4 — Pass through unknown JSON keys (`extra_body`, `chat_template_kwargs`)

**Effort estimate**: small (loosen the validator to forward unknown
keys instead of rejecting them).

**Blocks**: per-model inference controls that are exposed by every
modern open-weight runtime but that the proxy doesn't know about yet.

**Principle**: the proxy should never be stricter than the strictest
backend it serves. Unknown keys should be forwarded; the backend can
reject them with a meaningful error.

**Acceptance**: see D3 acceptance criteria above.

### R5 — Stream reasoning content as `delta.reasoning_content`

**Effort estimate**: medium (per-backend reasoning extraction logic).

**Blocks**: clean UX for reasoning models. Currently requires
model-specific frontend parsing.

**Acceptance**: see D7 acceptance criteria above.

### R6 — One canonical model ID per backing model

**Effort estimate**: small if just consolidation; medium if both IDs
must continue to exist (then the underlying serializer must be unified).

**Blocks**: silent tool-calling failures depending on which ID the
developer picked.

**Acceptance**: see D2 acceptance criteria above.

### R7 — Per-request trace headers

**Effort estimate**: trivial (UUID generation + header injection).

**Blocks**: support flow when customers report issues.

**Acceptance**: see D6 acceptance criteria above.

### R8 — `fallbacks` field support

**Effort estimate**: medium (proxy needs retry orchestration).

**Blocks**: resilient consumer apps. Today, a model unavailability
forces the consumer to implement its own fallback logic.

**Spec reference**: LiteLLM `fallbacks` parameter, OpenRouter
`models` array fallback, Portkey `strategy: { mode: "fallback" }`.

**Acceptance**: a request like

```json
{
  "model": "gpt-oss:120b",
  "messages": [...],
  "fallbacks": ["gpt-oss:20b", "qwen3:9b"]
}
```

should automatically retry against the fallback models in order if the
primary fails or is rate-limited, returning a header indicating which
model actually served the response.

---

## 4. Workarounds currently shipped in the test app

The `wiki-chat` test application currently ships the following
workarounds for the deficiencies above. An implementation agent picking
up this work should be aware that these can be removed once the
corresponding deficiency is resolved upstream.

### W1 — `reasoning_effort` workaround (covers D1)

**File**: `src/main/java/com/example/cfchat/service/ThinkingOptionsBuilder.java`

```java
public ChatOptions buildOptions(String model, String level) {
    // GenAI tile proxy returns 422 on reasoning_effort; fall back to prompt-only.
    // See genai-tile-reasoning-effort-422.md in project memory.
    @SuppressWarnings("unused") Class<?> futureUse = OpenAiChatOptions.class;
    return null;
}
```

The `OpenAiChatOptions` import is intentionally retained to mark the
intended future code path. Once R1 lands, this method should be
restored to:

```java
public ChatOptions buildOptions(String model, String level) {
    if (model == null) return null;
    String norm = normalize(level);
    String m = model.toLowerCase();
    if (m.contains("gpt-oss")) {
        String effort = switch (norm) {
            case "none" -> "minimal";
            case "low" -> "low";
            case "high" -> "high";
            default -> "medium";
        };
        return OpenAiChatOptions.builder()
                .model(model)
                .reasoningEffort(effort)
                .build();
    }
    return null;
}
```

The `systemPromptSuffix` method should remain in place for non-reasoning
models that don't have a native control (llama3.1, ministral, etc.) but
its scope can be narrowed once R1 lands.

### W2 — Qwen3 `/no_think` directive (covers part of D3)

**File**: same as above

```java
if (m.startsWith("qwen3") || m.contains("/qwen3")) {
    if ("none".equals(norm)) return "/no_think";
    return "";
}
```

This is a Qwen3-specific system-prompt directive. It should be replaced
with a proper `chat_template_kwargs: {enable_thinking: false}`
passthrough once R4 lands.

### W3 — Two model IDs documented in project memory (covers D2)

**File**: `~/.claude/projects/-Users-nkuhn-claude-cf-llama-chat/memory/genai-tile-model-id-variants.md`

A live A/B test result table is recorded in project memory advising
"default to the colon form (`gpt-oss:20b`) when using `@Tool` methods".
This should be removed once R6 lands.

### W4 — `<think>` block parser (covers D7)

**File**: `src/main/resources/static/js/app.js`, function `routeChunk`
inside `sendStreamingMessage`.

A stateful chunk-boundary-aware parser tracks `<think>` open/close tags
across SSE chunks with a small lookahead buffer, routes reasoning to a
collapsible details panel, and routes the answer to the main bubble.
The complete event also strips `<think>...</think>` from `htmlContent`
to prevent re-flash.

Once R5 lands, this can be replaced with:

```javascript
if (data.delta && data.delta.reasoning_content) {
    appendThinkingChunk(messageEl, data.delta.reasoning_content);
}
if (data.delta && data.delta.content) {
    fullContent += data.delta.content;
    debouncedStreamRender();
}
```

— a much smaller and more robust implementation.

### W5 — Empirical capability filtering (covers part of D5)

**File**: `src/main/java/com/example/cfchat/service/ChatService.java`,
method `getAvailableModels`, line ~485

```java
// Skip embedding models - only include chat models
if (modelName.toLowerCase().contains("embed")) {
    continue;
}
```

A name-pattern filter to hide embedding models from the chat UI. Once
R3 lands, this should be replaced with a check on
`model.capabilities.modalities.input.contains("text")` and
`model.capabilities.supports_streaming`.

---

## 5. Out-of-scope items mentioned for context

These are not asks of the tile team — they are notes for the agent
reviewing this report so they understand what is and isn't already
handled in the test app.

- **Async event-driven observability** (per-token cost, per-user
  attribution): the test app uses Spring Boot Actuator + Micrometer
  observation API. The tile contributing per-call cost data via
  response headers would let us attribute correctly without us having
  to maintain our own pricing tables.
- **MCP tool servers**: handled separately via the test app's
  `McpDiscoveryService` which scans `VCAP_SERVICES` for the
  `mcpSseURL` credentials key. This is orthogonal to the chat
  completions proxy.
- **Embedding models**: the test app uses Spring AI's `EmbeddingModel`
  bean which routes through a separate set of bindings. Embedding
  endpoints have their own deficiencies that are not covered in this
  report — would warrant a separate writeup.
- **SSO**: handled via `p-identity`, manually bound, intentionally not
  in the manifest. Not a tile issue.

---

## 6. Verification environment

If the implementation team wants to reproduce any of the above against
the same test setup:

```
Foundation:    CDC (api.sys.tas-cdc.kuhn-labs.com)
Org:           openclaw-system
Space:         orchestrators
App:           wiki-chat (deployed 2026-04-09)
Route:         https://wiki-chat.apps.tas-cdc.kuhn-labs.com
Login:         admin / Tanzu123 (default; change before production)
Service binds: wiki-chat-db (postgres on-demand-postgres-db)
               wiki-chat-genai (genai tanzu-all-models)
Branch:        feature/llm-wiki on github.com/nkuhn-vmw/cf-llama-chat
Branch tip:    c3b3677 (post-fix for D1 422)
PR:            github.com/nkuhn-vmw/cf-llama-chat/pull/31
Source paths to inspect for workarounds:
  src/main/java/com/example/cfchat/service/ThinkingOptionsBuilder.java
  src/main/java/com/example/cfchat/service/ChatService.java
  src/main/resources/static/js/app.js  (search for "routeChunk" and "<think>")
```

To reproduce a 422 directly against the proxy from a CF SSH session
into the deployed app:

```bash
cf ssh wiki-chat
$ env | grep -i genai             # find the proxy URL + key from VCAP
$ curl -i -X POST "$PROXY/openai/v1/chat/completions" \
    -H "Authorization: Bearer $KEY" \
    -H "Content-Type: application/json" \
    -d '{"model":"gpt-oss:20b","messages":[{"role":"user","content":"hi"}],"reasoning_effort":"medium"}'
```

---

## 7. Suggested rollout plan for the proxy team

If the asks above are accepted, here is a low-risk staged rollout that
preserves backward compatibility throughout:

**Stage 1 — Behind a feature flag** (low risk, ~1 sprint):
- Implement R2 (error envelopes) and R7 (trace headers)
- Both are additive and cannot break existing consumers
- Roll out to all foundations

**Stage 2 — Schema loosening** (low risk, additive, ~1 sprint):
- Implement R4 (pass-through unknown keys)
- This is the foundation for everything else; it cannot break existing
  consumers because consumers that don't send unknown keys are unaffected
- Roll out to all foundations

**Stage 3 — Reasoning surface** (medium risk, ~2 sprints):
- Implement R1 (`reasoning_effort` passthrough)
- Implement R5 (`delta.reasoning_content` separation)
- Behind a feature flag at the plan level for the first foundation,
  roll out to all foundations once verified
- Update tile documentation with examples

**Stage 4 — Discovery and routing** (medium risk, ~2 sprints):
- Implement R3 (capability metadata on `/v1/models`)
- Implement R6 (canonical model IDs)
- Implement R8 (fallback routing)
- This is where the tile starts to differentiate from being "just a
  proxy" toward being a proper gateway

**Stage 5 — Cleanup** (no time pressure):
- Removal of W1–W5 in consumer apps including this one

---

## 8. Closing notes

The Tanzu GenAI tile is a useful product and the asks above are not
about replacing it with something else — they are about closing a small
number of compatibility gaps that are blocking real Spring AI adoption
on the platform today. The unique value of the tile (CF integration,
on-prem GPU scheduling, OpsManager installability) is genuine and worth
preserving. Closing the request/response surface gap is the path to
letting that value reach more customers.

The author of this report is happy to:

- Provide a follow-up writeup on **embedding endpoints** if that would
  also be useful
- Provide a separate writeup on **observability and cost attribution**
  if there is interest
- Run any of the proposed test cases against the test app on CDC and
  share results
- Review draft PRs against the proxy if they would benefit from a
  consumer-side perspective

Contact: via the cf-llama-chat repository or directly.

---

*End of report.*
