# cf-llama-chat LLM Wiki — Design Spec

**Date:** 2026-04-08
**Status:** Approved for implementation planning
**Replaces:** `UserNote` + `UserMemory` features

## 1. Problem

cf-llama-chat has two disconnected "memory" features — `UserNote` (markdown notes) and `UserMemory` (flat fact/preference/instruction entries) — that share a critical flaw: **neither is ever injected into an LLM prompt**. `MemoryService.buildMemoryContext()` exists but has zero callers; `ChatService.buildMessageHistory()` (lines 657–780) never references either table. They function as a glorified sticky-note UI next to a chat app.

Additional weaknesses:

1. Memory "search" is `LOWER(content) LIKE '%q%'` — no semantic retrieval
2. No vector embeddings on memory data (pgvector is used only for documents)
3. Flat schema — no cross-links, tags, provenance, confidence, or decay
4. Manual-only capture — users must type facts to save them
5. No org/workspace scoping (only `user_id`)
6. Spring AI's `ChatMemory` interface is unused; conversation history is hand-rolled JPA
7. No `@Tool` methods exposed, so the agent cannot read or write memory autonomously

The infrastructure to fix this is already present and CF-bound: `PgVectorStore` (via `enterprise-chat-db`), Spring AI 1.1.2 with `@Tool`/`ToolContext` support, `DocumentEmbeddingService` as a working reference pattern, and `enterprise-chat-genai` for chat + embeddings.

## 2. Solution Overview

Replace both features with a unified **LLM Wiki** — an agent-curated knowledge store where the LLM reads and writes pages via tools mid-turn, and every chat request carries a capped index of existing pages in the system prompt. The wiki is synthesized from two references:

- **Karpathy's LLM wiki concept** — the wiki is a compounding, interlinked markdown artifact the LLM maintains; operations are ingest / query / lint / maintain; two special pages (`index`, `log`) bootstrap orientation
- **MemPalace architecture** — agent-visible memory tools, layered retrieval (L0 identity → L1 index → L2/L3 on-demand search), temporal knowledge graph with validity windows, structural metadata filtering

Shaped for Spring AI 1.1 + CF:

- Pages stored as JPA entities in `enterprise-chat-db`
- Embeddings in the existing `PgVectorStore` under a new `wiki_embeddings` collection
- `@Tool`-annotated methods on `WikiTools` bean expose Core 6 operations to the LLM
- `ToolContext` carries `userId`/`conversationId` into every tool call — the model never sees or can forge them
- An always-loaded index block (capped, cached) gives the LLM orientation without forcing a search

Zero new CF service bindings. Zero changes to `manifest.yml`. Customers get the wiki on next `cf push`.

## 3. Scope

### In Scope (this spec)
- New schema: `wiki_page`, `wiki_page_history`, `wiki_link`, `wiki_log_entry`, plus `wiki_embeddings` collection in `PgVectorStore`
- Core 6 `@Tool` methods on `WikiTools`
- `ChatService.buildMessageHistory()` integration: index preamble + tool registration + `ToolContext` propagation
- Chat UI: SSE `wiki_op` event + inline "🧠 saved/linked/invalidated [undo]" chip
- Unified `workspace/wiki.html` replacing `notes.html` + `memory.html`
- One-shot migration from `user_notes` + `user_memories` at `ApplicationReadyEvent`, with per-row embedding and `embedding_status` retry
- Deletion of old notes/memory code (models, repos, services, controllers, templates, JS) in the same PR

### Out of Scope (deferred to Phase C or later)
- `wiki_timeline`, `wiki_log_tail`, `wiki_lint` tools (Phase C — schema pre-provisioned, pure additive code later)
- Workspace/org sharing (`workspace_id` column shape planned but not populated)
- Graph visualization UI
- Pinned L0 identity layer (full-content pages always in prompt)
- Replacing hand-rolled conversation history with Spring AI `ChatMemory`
- Auto-extraction from non-chat sources (uploads, web content) — only in-turn writes

## 4. Architecture

### 4.1 Component Map

```
┌─────────────────────────────────────────────────────────────┐
│ ChatService.buildMessageHistory()                           │
│   ├─ base prompt + skill                                    │
│   ├─ WikiContextLoader.loadIndexBlock(userId)  ◄── Caffeine │
│   ├─ document RAG (unchanged)                               │
│   ├─ conversation history                                   │
│   └─ current message                                        │
│                                                             │
│ ChatService.chatStream() → ChatClient                       │
│   .tools(wikiTools)                                         │
│   .toolContext({userId, conversationId})                    │
└──────────────┬──────────────────────────────────────────────┘
               │ tool calls mid-turn
               ▼
┌─────────────────────────────────────────────────────────────┐
│ WikiTools  (@Component, 6 @Tool methods)                    │
│   wiki_search / wiki_read / wiki_write                      │
│   wiki_link / wiki_invalidate / wiki_index                  │
└──────────────┬──────────────────────────────────────────────┘
               ▼
┌─────────────────────────────────────────────────────────────┐
│ WikiService (@Transactional)                                │
│   ├─ WikiPageRepository (JPA)                               │
│   ├─ WikiPageHistoryRepository                              │
│   ├─ WikiLinkRepository                                     │
│   ├─ WikiLogRepository                                      │
│   └─ WikiEmbeddingService ──► PgVectorStore (wiki collection)│
└──────────────┬──────────────────────────────────────────────┘
               │ after every mutation
               ▼
┌─────────────────────────────────────────────────────────────┐
│ WikiOpEventPublisher ──► ChatController SSE stream          │
│   emits "wiki_op" event for inline chat chip                │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Data Flow — A Chat Turn With Wiki Activity

1. User sends message → `ChatController.chatStream()`
2. `ChatService.buildMessageHistory()` builds system prompt; `WikiContextLoader.loadIndexBlock(userId)` returns cached capped index (~500 tokens), appended after skill augmentation
3. `ChatClient.prompt()...tools(wikiTools).toolContext({userId, conversationId})...stream()` invoked
4. LLM streams tokens; at some point decides "this question needs prior context" and emits a tool call to `wiki_search`
5. Spring AI tool-calling loop executes `WikiTools.wikiSearch(query, kind, k, toolContext)` — `userId` read from `toolContext`, semantic search runs against `wiki_embeddings`, returns top-k with slugs/titles/snippets
6. LLM may then call `wiki_read` on specific pages, then continues streaming its response
7. Mid-response, if the LLM detects a durable new fact, it calls `wiki_write(slug, title, kind, body_md)` — `WikiService` upserts `wiki_page`, appends `wiki_page_history` row, re-embeds, appends `wiki_log_entry`, invalidates Caffeine index cache, and publishes a `wiki_op` event
8. `ChatController`'s SSE stream relays the `wiki_op` event as an SSE message with event name `wiki_op` — frontend `app.js` renders a chip on the current assistant bubble
9. User can click "undo" → `POST /api/wiki/pages/{id}/undo` → `WikiService.revertToPreviousRevision(id)` → UI chip updates to "↩ undone"

### 4.3 Key Design Decisions & Rationales

| Decision | Why |
|---|---|
| Tools, not prompt-stuffing | Karpathy/MemPalace insight: agent-driven curation beats passive retrieval. `@Tool` + `ToolContext` is Spring AI's idiomatic answer. |
| `ToolContext` for user scoping | LLM cannot forge `userId` because it's injected server-side and never sent to the model. This is the safety boundary for multi-tenant memory. |
| Capped index in system prompt (L1) | Pure tools-only underperforms because the LLM doesn't know what exists. A cheap index orients it without full content dump. |
| Caffeine cache with invalidation on write | Index is read on every chat turn; naïve DB query per turn is wasteful. Cache keyed by `userId`, invalidated in-process on mutations. |
| `VARCHAR(32)` + `@Pattern` for `kind` | Eliminates the `ddl-auto: update` CHECK-constraint gotcha documented in CLAUDE.md (`McpService.migrateTransportTypeConstraint`). Adding a kind is a one-line Java change. |
| `origin` column separate from `kind` | Prevents the classifier "catch-all" trap — the LLM never sees `NOTE`, so it either classifies into a real kind or stays silent. |
| `wiki_page_history` append-only + optimistic `version` lock | Enables undo without touching primary table read performance; gives revision history for free. |
| `wiki_log_entry` written from day one | Powers the UI op feed and makes Phase C's `wiki_log_tail` tool purely additive. |
| `wiki_link.valid_from/valid_until` nullable columns | Phase B ignores; Phase C's `wiki_timeline` uses them. Null = "always valid." |
| `wiki_page.last_read_at` updated by `wiki_read` | Cheap in Phase B, unlocks Phase C staleness heuristics without backfill. |

## 5. Data Model

### 5.1 `wiki_page`

```java
@Entity
@Table(name = "wiki_page",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "slug"}),
       indexes = {
           @Index(columnList = "user_id,kind"),
           @Index(columnList = "user_id,updated_at"),
           @Index(columnList = "embedding_status")
       })
public class WikiPage {
    @Id @GeneratedValue UUID id;

    @Column(name = "user_id", nullable = false) UUID userId;

    // Phase B: always null. Phase C: nullable workspace id.
    @Column(name = "workspace_id") UUID workspaceId;

    @Column(nullable = false, length = 255) String slug;
    @Column(nullable = false, length = 255) String title;

    @Column(nullable = false, length = 32)
    @Pattern(regexp = "ENTITY|CONCEPT|FACT|PREFERENCE|DECISION|EVENT|INDEX|LOG|NOTE")
    String kind;

    @Column(nullable = false, length = 32)
    @Pattern(regexp = "AGENT_WRITE|MIGRATED_NOTE|MIGRATED_MEMORY|USER_DIRECT_EDIT")
    String origin;

    @Column(name = "body_md", columnDefinition = "text", nullable = false)
    String bodyMd;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    Map<String, Object> frontmatter;

    @Column(name = "source_conversation_id") UUID sourceConversationId;

    @Version int version;  // optimistic lock

    @Column(name = "last_read_at") Instant lastReadAt;

    @Column(name = "embedding_status", nullable = false, length = 16)
    @Pattern(regexp = "PENDING|READY|FAILED")
    String embeddingStatus;

    @Column(name = "embedding_error") String embeddingError;

    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    @PrePersist void onCreate() {
        createdAt = updatedAt = Instant.now();
        if (embeddingStatus == null) embeddingStatus = "PENDING";
        if (origin == null) origin = "AGENT_WRITE";
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
```

**Java enums** (source of truth, used for tool descriptions and application validation):

```java
public enum WikiKind {
    ENTITY, CONCEPT, FACT, PREFERENCE, DECISION, EVENT,  // agent-visible
    INDEX, LOG, NOTE                                      // system-only
}

public enum WikiOrigin {
    AGENT_WRITE, MIGRATED_NOTE, MIGRATED_MEMORY, USER_DIRECT_EDIT
}

public enum EmbeddingStatus { PENDING, READY, FAILED }
```

Entity stores as `String` to avoid JPA enum-to-CHECK entanglement; typed getters convert.

### 5.2 `wiki_page_history`

Append-only; one row per edit.

```java
@Entity
@Table(name = "wiki_page_history",
       indexes = @Index(columnList = "page_id,version DESC"))
public class WikiPageHistory {
    @Id @GeneratedValue UUID id;
    @Column(name = "page_id", nullable = false) UUID pageId;
    @Column(nullable = false) int version;  // version of wiki_page BEFORE this edit
    @Column(name = "body_md", columnDefinition = "text", nullable = false) String bodyMd;
    @Column(nullable = false, length = 255) String title;
    @Column(length = 32) String kind;
    @Type(JsonType.class) @Column(columnDefinition = "jsonb") Map<String,Object> frontmatter;
    @Column(name = "edited_by", nullable = false, length = 64) String editedBy;
    // values: "agent:<conversationId>" | "user:<userId>" | "migration"
    @Column(name = "edit_reason", length = 255) String editReason;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}
```

### 5.3 `wiki_link`

Knowledge-graph edges. Temporal columns unused in Phase B, populated in Phase C.

```java
@Entity
@Table(name = "wiki_link",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"from_page_id","to_page_id","relation"}),
       indexes = {
           @Index(columnList = "from_page_id"),
           @Index(columnList = "to_page_id")
       })
public class WikiLink {
    @Id @GeneratedValue UUID id;
    @Column(name = "from_page_id", nullable = false) UUID fromPageId;
    @Column(name = "to_page_id", nullable = false) UUID toPageId;

    @Column(nullable = false, length = 32)
    @Pattern(regexp = "mentions|see_also|supersedes|refines|contradicts")
    String relation;

    @Column(name = "valid_from") Instant validFrom;    // Phase C
    @Column(name = "valid_until") Instant validUntil;  // Phase C

    @Column(name = "created_by", nullable = false, length = 64) String createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}
```

### 5.4 `wiki_log_entry`

Append-only audit/event log. Written in the same transaction as every mutation. Feeds the UI "Recent activity" panel in Phase B and the `wiki_log_tail` tool in Phase C.

```java
@Entity
@Table(name = "wiki_log_entry",
       indexes = @Index(columnList = "user_id,ts DESC"))
public class WikiLogEntry {
    @Id @GeneratedValue UUID id;
    @Column(name = "user_id", nullable = false) UUID userId;

    @Column(nullable = false, length = 16)
    @Pattern(regexp = "WRITE|LINK|INVALIDATE|UNDO|READ")
    String op;

    @Column(name = "page_id") UUID pageId;
    @Column(name = "conversation_id") UUID conversationId;
    @Column(length = 512) String summary;
    @Column(nullable = false) Instant ts;

    @PrePersist void onCreate() { if (ts == null) ts = Instant.now(); }
}
```

### 5.5 Vector Embeddings

Reuse existing `PgVectorStore` bean from `VectorStoreConfig.java`. New collection `wiki_embeddings` (parallel to existing `document_embeddings`) with per-page chunking:

- Chunk size: match existing `app.documents.chunk-size` (350 tokens)
- Overlap: match existing `app.documents.chunk-overlap` (100 tokens)
- Metadata attached per chunk: `{pageId, userId, kind, slug, title, workspaceId?}`
- On `wiki_write`: delete existing chunks for `pageId`, re-chunk, re-embed, insert new chunks
- On `wiki_invalidate`: delete chunks for `pageId` (semantic search should not return invalidated content)

`WikiEmbeddingService` mirrors `DocumentEmbeddingService` — same chunker, same `EmbeddingModel` injection point, same `PgVectorStore` target. Failures set `wiki_page.embedding_status = FAILED` with the exception message in `embedding_error`; a `@Scheduled(fixedDelay = 300000)` job picks up `PENDING` and `FAILED` rows and retries with exponential backoff capped at 5 attempts.

### 5.6 Schema Creation Strategy

Relies on `ddl-auto: update` for all tables (matches existing repo convention). The enum-like `VARCHAR` columns use `@Pattern` validation rather than CHECK constraints precisely to sidestep the drift gotcha documented in CLAUDE.md — no `@EventListener` migration code is needed for this feature.

## 6. Tool Surface (Core 6)

All tools are methods on `@Component WikiTools`. Each takes `ToolContext toolContext` and extracts `userId` via a helper; no tool accepts `userId` as a model-visible parameter.

### 6.1 `wiki_search`

```java
@Tool(description = """
    Search the user's wiki for pages relevant to a query. Use this BEFORE
    answering questions about the user, their projects, preferences, or
    prior decisions. Returns up to k matching pages with title, slug, and
    a short snippet. Filter by kind when you know the category.
    """)
public List<WikiSearchHit> wikiSearch(
    @ToolParam(description = "natural-language query") String query,
    @ToolParam(required = false, description = "restrict to ENTITY|CONCEPT|FACT|PREFERENCE|DECISION|EVENT") String kind,
    @ToolParam(required = false, description = "max results, default 6, max 20") Integer k,
    ToolContext toolContext);
```

Behavior: semantic search on `wiki_embeddings` with metadata filter `userId = ctx.userId` (+ optional `kind`). Returns hits sorted by similarity; snippet is the highest-scoring chunk truncated to ~200 chars.

### 6.2 `wiki_read`

```java
@Tool(description = """
    Read a wiki page by slug. Use after wiki_search to get full page
    content. Updates the page's last-read timestamp.
    """)
public WikiPageView wikiRead(
    @ToolParam(description = "page slug, e.g. 'personal/work-style'") String slug,
    ToolContext toolContext);
```

Side effect: sets `wiki_page.last_read_at = now()`, writes `wiki_log_entry(op=READ)` (used by Phase C staleness lint).

### 6.3 `wiki_write`

```java
@Tool(description = """
    Create or update a wiki page with durable information the user just
    shared. Choose the most specific kind:
    - ENTITY: a person, project, product, or thing
    - CONCEPT: an idea, definition, or reference topic
    - FACT: something objectively true about the user or their world
    - PREFERENCE: something the user likes, dislikes, or defaults to
    - DECISION: a choice the user committed to (include rationale)
    - EVENT: something that happened at a specific time
    If none fit, do NOT call this tool - the info is not durable enough.
    """)
public WikiPageView wikiWrite(
    @ToolParam(description = "slug, e.g. 'personal/work-style' or 'projects/orion'") String slug,
    @ToolParam(description = "human-readable title") String title,
    @ToolParam(description = "one of ENTITY|CONCEPT|FACT|PREFERENCE|DECISION|EVENT") String kind,
    @ToolParam(description = "full markdown body") String bodyMd,
    ToolContext toolContext);
```

Behavior (inside a single `@Transactional` boundary):
1. Load existing `wiki_page` by `(userId, slug)` or create new
2. If existing: snapshot current state into `wiki_page_history` with `editedBy="agent:<conversationId>"`
3. Update fields, bump `version`, set `updatedAt`, set `embeddingStatus=PENDING`, set `origin=AGENT_WRITE` (only on create)
4. Append `wiki_log_entry(op=WRITE, pageId, conversationId, summary)`
5. Trigger re-embedding (async via `@Async` — do not block the chat turn on embedding latency)
6. Publish `WikiOpEvent` for SSE relay
7. Invalidate Caffeine index cache for `userId`

Returns a compact `WikiPageView` (id, slug, title, kind, version) — not the full body, to keep the tool response token cost low.

### 6.4 `wiki_link`

```java
@Tool(description = """
    Link two wiki pages with a semantic relation. Use when pages reference
    each other: mentions, see_also, supersedes, refines, contradicts.
    """)
public void wikiLink(
    @ToolParam String fromSlug,
    @ToolParam String toSlug,
    @ToolParam(description = "one of mentions|see_also|supersedes|refines|contradicts") String relation,
    ToolContext toolContext);
```

Behavior: validates both slugs exist and belong to `ctx.userId`; upserts `wiki_link` row (unique on from/to/relation); writes `wiki_log_entry(op=LINK)`; publishes `WikiOpEvent`.

### 6.5 `wiki_invalidate`

```java
@Tool(description = """
    Mark a wiki page as superseded or incorrect. Does NOT delete history -
    the page becomes hidden from search but remains in the revision log
    and can be restored via undo.
    """)
public void wikiInvalidate(
    @ToolParam String slug,
    @ToolParam(description = "short reason for invalidation") String reason,
    ToolContext toolContext);
```

Behavior:
1. Snapshot current state to `wiki_page_history` with `editReason=reason`, `editedBy="agent:<conversationId>"`
2. Set `frontmatter.invalidated_at = now()`, `frontmatter.invalidated_reason = reason` (not a column — stays in JSONB so migration to a dedicated column later is additive)
3. Delete embedding chunks for this page (so semantic search no longer returns it)
4. Append `wiki_log_entry(op=INVALIDATE)`
5. Publish `WikiOpEvent`
6. Invalidate Caffeine index cache

Undo reverses step 2 and re-embeds.

### 6.6 `wiki_index`

```java
@Tool(description = """
    Return a compact catalog of all wiki pages (slug, title, kind, one-line
    summary). Cheap orientation - prefer this over wiki_search when you
    just need to see what exists.
    """)
public List<WikiIndexEntry> wikiIndex(
    @ToolParam(required = false, description = "restrict to a single kind") String kind,
    ToolContext toolContext);
```

Behavior: serves the same data as the system-prompt index block (up to `app.wiki.index.max-entries`), filtered by kind if provided. Does **not** use the Caffeine cache — this is an explicit agent-driven call and should reflect most recent state.

## 7. System Prompt Integration

### 7.1 `WikiContextLoader`

```java
@Component
public class WikiContextLoader {
    private final WikiPageRepository repo;
    private final Cache<UUID, String> indexCache;  // Caffeine, 5min TTL

    @Value("${app.wiki.index.max-entries:40}")
    private int maxEntries;

    public String loadIndexBlock(UUID userId) {
        return indexCache.get(userId, this::buildBlock);
    }

    public void invalidate(UUID userId) { indexCache.invalidate(userId); }

    private String buildBlock(UUID userId) {
        var pages = repo.findTopForIndex(userId, maxEntries);
        if (pages.isEmpty()) return WIKI_PREAMBLE_NO_DATA;
        var body = pages.stream()
            .map(p -> "- [%s] %s - %s".formatted(p.kind(), p.slug(), p.title()))
            .collect(joining("\n"));
        return WIKI_PREAMBLE + "\n\n## Your wiki index\n" + body;
    }
}
```

`findTopForIndex` ordering: `ORDER BY last_read_at DESC NULLS LAST, updated_at DESC LIMIT :max`.

### 7.2 Preamble Text

```
You have a persistent wiki for this user. Call wiki_search or wiki_read
before answering questions about them, their projects, preferences, or
prior decisions. Call wiki_write to persist durable new facts they share.
The wiki is scoped to this user - you cannot access other users' data.
```

### 7.3 `ChatService.buildMessageHistory()` Changes

Insert the wiki block after skill augmentation and before document context:

```java
// existing:
String systemPromptText = config.getSystemPrompt();
if (skill != null) systemPromptText += "\n\n" + skill.getSystemPromptAugmentation();

// NEW:
systemPromptText += "\n\n" + wikiContextLoader.loadIndexBlock(userId);

// existing:
if (useDocumentContext) { ... document RAG ... }
```

And in `chatStream()` wire `WikiTools` + `ToolContext`:

```java
ChatClient.create(chatModel)
    .prompt()
    .system(systemPromptText)
    .messages(history)
    .user(processedMessage)
    .tools(wikiTools)
    .toolContext(Map.of(
        "userId", userId,
        "conversationId", conversation.getId()))
    .stream()
    .content();
```

`ToolContext` note: Spring AI 1.1's `ToolContext` accepts arbitrary `Map<String,Object>` — values never leave the JVM, the model never sees them. `WikiTools.scopeOf(toolContext)` helper extracts and type-checks.

## 8. Chat UI Integration

### 8.1 SSE Event: `wiki_op`

New SSE event name emitted from `ChatController.chatStream()`. Payload:

```json
{
  "op": "WRITE|LINK|INVALIDATE|UNDO",
  "pageId": "uuid",
  "slug": "personal/work-style",
  "title": "Work Style",
  "kind": "PREFERENCE",
  "summary": "Saved preference for PostgreSQL over MySQL"
}
```

Publication mechanism: `WikiService` publishes a Spring `ApplicationEvent` (`WikiOpEvent`) inside the transaction; `ChatController` subscribes via a per-request `ApplicationListener` scoped to the streaming response and forwards events onto the SSE sink. This mirrors the existing "complete" event pattern and respects the `streamComplete` flag gotcha documented in CLAUDE.md (don't let the fallback `marked.parse()` overwrite the inline chip).

### 8.2 Frontend Chip

`static/js/app.js` adds an `EventSource` listener for `wiki_op`. On receipt, it appends a chip to the currently streaming assistant message's DOM node:

```
[🧠 Saved preference: Work Style] [view] [undo]
```

Styling lives in `static/css/style.css`. CSP note: no inline handlers — `data-action="undo"` + event delegation, matching the pattern established in `static/js/admin-*.js`.

"view" → navigates to `/workspace/wiki#slug=personal/work-style`.
"undo" → `POST /api/wiki/pages/{id}/undo` → server calls `WikiService.revertToPreviousRevision(pageId)` → chip becomes "↩ Undone".

### 8.3 Undo Semantics

Undo reverts `wiki_page` to the state in the most recent `wiki_page_history` row for that page. If the most recent history row corresponds to a create (no prior state), undo deletes the page and its embedding chunks. A new `wiki_log_entry(op=UNDO)` is appended. Undo is itself reversible by calling undo again (which walks further back in history), bounded by history depth.

## 9. Workspace UI (`workspace/wiki.html`)

Replaces both `workspace/notes.html` and `workspace/memory.html`. Single Thymeleaf template with:

- **Left rail** (280px): collapsible tree grouped by `kind`, counts per group. Clicking a page loads it into the center panel.
- **Top bar**: search input. On submit, hits `GET /api/wiki/search?q=...` and replaces the tree with ranked results until cleared.
- **Center panel**: rendered markdown via `marked` (positional-args API per CLAUDE.md gotcha). Page metadata header (kind, origin, version, last updated).
- **Right rail**: "Recent activity" (last 20 `wiki_log_entry` rows for this user, with timestamps and op icons).
- **Bottom accordion** on each page: revision history from `wiki_page_history`, click to restore.

Direct edit: users can click the center panel to edit the markdown; save posts to `PUT /api/wiki/pages/{id}` with `origin=USER_DIRECT_EDIT` set server-side, writes a history row, re-embeds.

`workspace.html` hub: the two old cards collapse into a single "Wiki" card, count = `SELECT COUNT(*) FROM wiki_page WHERE user_id = ? AND origin = 'AGENT_WRITE'` (so migrated rows don't inflate the feel of "what the agent has learned").

### 9.1 REST Endpoints

```
GET    /api/wiki/pages                          # list (paginated, kind filter)
GET    /api/wiki/pages/{id}                     # read full
PUT    /api/wiki/pages/{id}                     # user direct edit
POST   /api/wiki/pages/{id}/undo                # revert to prior revision
GET    /api/wiki/pages/{id}/history             # list revisions
POST   /api/wiki/pages/{id}/history/{version}/restore
GET    /api/wiki/search?q=...&kind=...&k=...    # semantic search (user-facing, same path tools use)
GET    /api/wiki/log?limit=20                   # recent activity feed
```

All endpoints require authenticated user; all filter by `userId` in the service layer (never from a request parameter).

## 10. Migration from `user_notes` + `user_memories`

### 10.1 Runner

```java
@Component
public class WikiMigrationRunner {
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        if (migrationAlreadyRan()) return;
        migrateNotes();
        migrateMemories();
        dropOldTables();
        markMigrationComplete();
    }
}
```

`migrationAlreadyRan()` checks a simple `app_migration` table row keyed `"wiki_v1"`. On first boot after deploy, the flag is absent → migration runs → flag inserted. On subsequent boots, no-op.

### 10.2 Mapping Rules

| Source | Target |
|---|---|
| `user_notes` row | `wiki_page(kind='CONCEPT', origin='MIGRATED_NOTE', slug='notes/'+slugify(title or id), title=title or 'Untitled', bodyMd=content, sourceConversationId=conversationId)` |
| `user_memories(category='fact')` | `wiki_page(kind='FACT', origin='MIGRATED_MEMORY', slug='facts/'+hash8(content), title=truncate(content,60), bodyMd=content)` |
| `user_memories(category='preference')` | `kind='PREFERENCE', slug='preferences/'+hash8(content)` |
| `user_memories(category='instruction')` | `kind='CONCEPT', slug='instructions/'+hash8(content)` (instructions are reference material, not user state) |
| `user_memories(category='general' or null)` | `kind='CONCEPT', slug='general/'+hash8(content)` |

Slug uniqueness: if a collision occurs, append `-2`, `-3`, etc.

### 10.3 Embedding During Migration

Each migrated page is inserted with `embedding_status='PENDING'` and queued for async embedding via the same `@Async` path as normal writes. The scheduled retry job handles any failures. Migration does NOT block on embedding completion — the wiki is usable immediately; semantic search results fill in as embeddings land (on a fast network, within seconds).

### 10.4 Old Code Deletion (same PR)

Delete:
- `model/UserNote.java`, `model/UserMemory.java`
- `repository/UserNoteRepository.java`, `repository/UserMemoryRepository.java`
- `service/MemoryService.java`
- `controller/NoteController.java`, `controller/MemoryController.java`
- `templates/workspace/notes.html`, `templates/workspace/memory.html`
- `static/js/workspace-notes.js`, `static/js/workspace-memory.js` (and any other note/memory JS)
- Any DTOs (`NoteDto`, `MemoryDto`) used exclusively by those controllers
- References in `workspace.html` hub cards
- `app.memory.max-per-user` config key (remove from `application.yml`)

The tables themselves (`user_notes`, `user_memories`) are dropped by the migration runner via `JdbcTemplate.execute("DROP TABLE IF EXISTS ...")` **after** successful copy.

### 10.5 Rollback

A deployment-level rollback (`cf push` of the prior release JAR) will leave the new `wiki_*` tables in the database (Hibernate will see they exist and not touch them) and recreate the old `user_notes` / `user_memories` tables empty. The old-release code will read/write the empty old tables normally. **This means rolling back loses post-migration wiki activity.** The runbook for deploying this feature must include "take a Postgres backup of `enterprise-chat-db` immediately before `cf push`."

## 11. Configuration

Additions to `application.yml`:

```yaml
app:
  wiki:
    index:
      max-entries: 40            # capped index block size
      cache-ttl-seconds: 300     # Caffeine TTL
    embedding:
      retry:
        interval-seconds: 300    # @Scheduled retry job
        max-attempts: 5
    search:
      default-k: 6
      max-k: 20
```

Removals:

```yaml
app:
  memory:
    max-per-user: 100   # REMOVE
```

## 12. Testing Strategy

### Unit Tests
- `WikiService` CRUD + history + embedding status transitions (mock `WikiEmbeddingService`)
- `WikiContextLoader` cache behavior + invalidation
- `WikiTools` scope extraction from `ToolContext` — verify `userId` cannot be overridden by tool args
- Migration mapping rules for each source category
- Slug collision handling

### Integration Tests
- Full chat turn with `wiki_write` tool call, asserting `wiki_page` + `wiki_page_history` + `wiki_log_entry` + embedding rows all written in one transaction
- SSE `wiki_op` event emitted and received
- Undo cycle: write → undo → verify state → undo again (reversal)
- Semantic search correctness via a seeded wiki against `PgVectorStore` (use Testcontainers Postgres+pgvector, matching existing test pattern if present)
- Migration runner idempotency: running twice is a no-op
- Multi-user isolation: user A's `wiki_search` never returns user B's pages

### Manual / Smoke
- Fresh CF deploy with empty DB → migration no-op → wiki UI works
- Existing user with notes + memories → post-deploy, all data visible in new UI, embeddings populate within 1 minute
- Autonomous write during chat → chip appears, undo works, page appears in workspace UI on refresh

## 13. Phase C Readiness Checklist

The following will be purely additive code with zero schema migration:

- [ ] Add `wiki_timeline(entitySlug)` tool → queries `wiki_link` by from/to page for entity, orders by `valid_from NULLS LAST`
- [ ] Add `wiki_log_tail(n)` tool → wraps `WikiLogRepository.findTopNByUserIdOrderByTsDesc`
- [ ] Add `wiki_lint()` tool → uses existing `last_read_at`, `relation='contradicts'` links, and `wiki_page_history` to find orphans / stale / contradicted pages
- [ ] Add `app.wiki.pinned` concept (L0 full-content identity layer) — new boolean in `frontmatter` JSONB, no schema change
- [ ] Add `workspace_id` population path (column already exists nullable) + shared wing ACLs

## 14. Open Questions (None Blocking Implementation)

Three items deliberately deferred to post-implementation feedback, not blockers:

1. **Extraction cost control** — autonomous writes happen inside the main chat turn using the same `ChatModel`, so no extra LLM call is incurred for classification. Phase C may add a cheaper extraction model if observed token cost justifies it.
2. **Cross-workspace sharing UX** — Phase C work; `workspace_id` column shape is locked in, but product-level sharing flows are not designed yet.
3. **`ChatMemory` interface adoption** — replacing hand-rolled `Conversation`/`Message` JPA with Spring AI's `MessageWindowChatMemory` is orthogonal and deferred.

---

## Appendix A — File Impact Map

### New files

```
src/main/java/com/example/cfchat/
├── model/
│   ├── WikiPage.java
│   ├── WikiPageHistory.java
│   ├── WikiLink.java
│   └── WikiLogEntry.java
├── repository/
│   ├── WikiPageRepository.java
│   ├── WikiPageHistoryRepository.java
│   ├── WikiLinkRepository.java
│   └── WikiLogRepository.java
├── service/wiki/
│   ├── WikiService.java
│   ├── WikiEmbeddingService.java
│   ├── WikiSearchService.java
│   ├── WikiContextLoader.java
│   ├── WikiMigrationRunner.java
│   └── WikiEmbeddingRetryJob.java
├── controller/
│   └── WikiController.java
├── tools/
│   ├── WikiTools.java
│   └── WikiScope.java
├── event/
│   └── WikiOpEvent.java
└── dto/wiki/
    ├── WikiPageView.java
    ├── WikiSearchHit.java
    ├── WikiIndexEntry.java
    └── WikiOpPayload.java

src/main/resources/
├── templates/workspace/wiki.html
└── static/js/workspace-wiki.js
└── static/css/wiki.css (or additions to style.css)
```

### Modified files

```
src/main/java/com/example/cfchat/
├── service/ChatService.java        # buildMessageHistory + chatStream wiring
├── controller/ChatController.java  # SSE wiki_op relay
└── CfLlamaChatApplication.java     # (no change expected; @EnableAsync already present)

src/main/resources/
├── application.yml                 # add app.wiki.*, remove app.memory.max-per-user
└── templates/workspace.html        # replace notes+memory cards with wiki card

src/main/resources/static/js/
└── app.js                          # EventSource listener for wiki_op
```

### Deleted files

```
src/main/java/com/example/cfchat/
├── model/UserNote.java
├── model/UserMemory.java
├── repository/UserNoteRepository.java
├── repository/UserMemoryRepository.java
├── service/MemoryService.java
├── controller/NoteController.java
└── controller/MemoryController.java

src/main/resources/
├── templates/workspace/notes.html
├── templates/workspace/memory.html
└── static/js/workspace-notes.js, workspace-memory.js (and any sibling helpers)
```
