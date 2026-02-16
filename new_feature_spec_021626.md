# CF Llama Chat — Full Technical Implementation Specification

## Feature Roadmap: Gap Closure with Open WebUI

**Version:** 2.0  
**Base:** CF Llama Chat v1.22.0 (Spring Boot 3.4 / Spring AI 1.1)  
**Date:** February 2026  
**Target Platform:** Tanzu Platform / Cloud Foundry  

---

## Document Conventions

Each feature section contains everything a coding agent needs:

- **Overview** — What the feature does and why it matters
- **Database Schema** — JPA entities and Flyway SQL migrations
- **API Endpoints** — REST controller signatures with request/response DTOs
- **Service Layer** — Core business logic
- **Frontend** — Thymeleaf / JavaScript / CSS guidance
- **Configuration** — `application.yml` properties
- **Dependencies** — Maven additions (if any)
- **Acceptance Criteria** — Testable pass/fail conditions

**Package root:** `com.example.cfchat`  
**Existing structure:** `auth/`, `config/`, `controller/`, `model/`, `repository/`, `service/`  
**Database:** PostgreSQL 15+ with pgvector extension  
**Frontend stack:** Thymeleaf server-side templates + vanilla JavaScript + CSS3  
**Build:** Maven, Java 21  

---

## Table of Contents

### Phase 1 — HIGH Priority (Foundation)
| # | Feature |
|---|---------||
| 1 | Chat Persistence |
| 2 | Markdown & LaTeX Rendering |
| 3 | Code Syntax Highlighting |
| 4 | Async Chat |
| 5 | Bi-Directional Text (RTL) |
| 6 | Hybrid RAG Search (BM25 + Vector + Re-ranking) |
| 7 | RAG Citations |
| 8 | Web URL RAG |
| 9 | Web Search Provider |
| 10 | Agentic Search |
| 11 | LDAP / Active Directory Auth |
| 12 | SCIM 2.0 Provisioning |
| 13 | Granular Permissions |
| 14 | Model Whitelisting |
| 15 | Rate Limiting |
| 16 | Prompt Presets (/ Command System) |
| 17 | OpenTelemetry Integration |

### Phase 2 — MEDIUM Priority (Enhancement)
| # | Feature |
|---|---------||
| 18 | Chat Export & Import |
| 19 | Chat Archive |
| 20 | Message Editing |
| 21 | Response Regeneration |
| 22 | Chat Sharing |
| 23 | YouTube RAG Pipeline |
| 24 | Advanced RAG Queries |
| 25 | Document Library |
| 26 | Advanced Document Extractors |
| 27 | URL Fetching Tool |
| 28 | Knowledge Attachment to Models |
| 29 | User Groups |
| 30 | Prevent Chat Deletion |
| 31 | Webhook Notifications |
| 32 | Active Users Monitor |
| 33 | PWA Support |
| 34 | Theme Modes |
| 35 | i18n / Multilingual |
| 36 | Interactive Artifacts |
| 37 | Dynamic Prompt Variables |
| 38 | Memory System |
| 39 | Prompt Library |
| 40 | Redis Session Management |
| 41 | Cloud Storage Backends |
| 42 | Horizontal Scaling Coordination |
| 43 | Toxic Message Filtering |
| 44 | Prompt Injection Guard |
| 45 | Usage Monitoring |

### Phase 3 — LOW Priority (Polish)
| # | Feature |
|---|---------||
| 46 | Chat Cloning |
| 47 | Chat Folders |
| 48 | Pinned Chats |
| 49 | Tagging System |
| 50 | Temporary Chats |
| 51 | Favorite Responses |
| 52 | Full Doc vs Snippet Toggle |
| 53 | Large Text Paste Detection |
| 54 | Channels (Collaborative Rooms) |
| 55 | Notes |
| 56 | Custom Backgrounds |
| 57 | Keyboard Shortcuts |
| 58 | Settings Search |
| 59 | Haptic Feedback |
| 60 | Notification Banners |
| 61 | Live Translation |
| 62 | Config Import/Export |
| 63 | Swagger/OpenAPI Documentation |
| 64 | Quick Actions |


# PHASE 1 — HIGH PRIORITY

---

## 1. Chat Persistence

### Overview
Replace session-scoped chat with full database-persisted conversations. Users see a sidebar with all previous conversations, each auto-titled. Conversations survive across sessions, restarts, and devices.

### Database Schema

```java
@Entity
@Table(name = "conversations", indexes = {
    @Index(name = "idx_conv_user_updated", columnList = "user_id, updated_at DESC"),
    @Index(name = "idx_conv_org", columnList = "organization_id")
})
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "model_id", length = 255)
    private String modelId;

    private boolean archived = false;
    private boolean pinned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private ChatFolder folder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    @PrePersist void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_msg_conv_seq", columnList = "conversation_id, sequence_number")
})
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;  // USER, ASSISTANT, SYSTEM

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "model_id") private String modelId;
    @Column(name = "token_count") private Integer tokenCount;
    @Column(name = "generation_time_ms") private Long generationTimeMs;
    @Column(name = "parent_message_id") private String parentMessageId;
    @Column(name = "is_active") private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist void prePersist() { createdAt = Instant.now(); }
}

public enum MessageRole { USER, ASSISTANT, SYSTEM }
```

### Flyway Migration

```sql
-- V2__chat_persistence.sql
CREATE TABLE conversations (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT REFERENCES organizations(id),
    title VARCHAR(500) NOT NULL DEFAULT 'New Chat',
    model_id VARCHAR(255),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    folder_id VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE chat_messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sequence_number INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    model_id VARCHAR(255),
    token_count INT,
    generation_time_ms BIGINT,
    parent_message_id VARCHAR(36),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_conv_user_updated ON conversations(user_id, updated_at DESC);
CREATE INDEX idx_conv_archived ON conversations(user_id, archived);
CREATE INDEX idx_msg_conv_seq ON chat_messages(conversation_id, sequence_number);
```

### Repository

```java
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    @Query("SELECT c FROM Conversation c WHERE c.user.id = :uid AND c.archived = false ORDER BY c.updatedAt DESC")
    Page<Conversation> findActiveByUserId(@Param("uid") Long uid, Pageable p);

    @Query("SELECT c FROM Conversation c WHERE c.user.id = :uid AND c.archived = true ORDER BY c.updatedAt DESC")
    Page<Conversation> findArchivedByUserId(@Param("uid") Long uid, Pageable p);

    List<Conversation> findByUserIdAndPinnedTrueOrderByUpdatedAtDesc(Long userId);

    @Query("SELECT c FROM Conversation c WHERE c.user.id = :uid AND LOWER(c.title) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<Conversation> searchByTitle(@Param("uid") Long uid, @Param("q") String q, Pageable p);

    @Modifying @Query("UPDATE Conversation c SET c.archived = true WHERE c.user.id = :uid AND c.archived = false")
    int archiveAllByUserId(@Param("uid") Long uid);

    List<Conversation> findAllByUserId(Long userId);
}

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findByConversationIdAndActiveTrueOrderBySequenceNumberAsc(String convId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :cid AND m.parentMessageId = :pid ORDER BY m.createdAt")
    List<ChatMessage> findRegenerations(@Param("cid") String cid, @Param("pid") String pid);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :cid AND m.role = 'USER' AND m.active = true AND m.sequenceNumber < :seq ORDER BY m.sequenceNumber DESC LIMIT 1")
    Optional<ChatMessage> findPrecedingUserMessage(@Param("cid") String cid, @Param("seq") int seq);
}
```

### Service

```java
@Service @RequiredArgsConstructor @Transactional
public class ConversationService {
    private final ConversationRepository convRepo;
    private final ChatMessageRepository msgRepo;
    private final TitleGenerationService titleService;

    public Conversation create(User user, String modelId, String systemPrompt) {
        Conversation c = new Conversation();
        c.setUser(user); c.setModelId(modelId); c.setTitle("New Chat");
        c = convRepo.save(c);
        if (systemPrompt != null && !systemPrompt.isBlank())
            addMessage(c, MessageRole.SYSTEM, systemPrompt, null);
        return c;
    }

    public ChatMessage addMessage(Conversation c, MessageRole role, String content, String modelId) {
        ChatMessage m = new ChatMessage();
        m.setConversation(c); m.setSequenceNumber(c.getMessages().size());
        m.setRole(role); m.setContent(content); m.setModelId(modelId);
        m = msgRepo.save(m);
        c.getMessages().add(m);
        convRepo.save(c);
        return m;
    }

    @Async public void autoGenerateTitle(String convId) {
        Conversation c = convRepo.findById(convId).orElseThrow();
        if (!"New Chat".equals(c.getTitle())) return;
        String first = c.getMessages().stream().filter(m -> m.getRole() == MessageRole.USER)
            .findFirst().map(ChatMessage::getContent).orElse("");
        c.setTitle(titleService.generate(first));
        convRepo.save(c);
    }
}

@Service @RequiredArgsConstructor
public class TitleGenerationService {
    private final ChatModel chatModel;
    public String generate(String firstMessage) {
        String prompt = "Generate a concise title (max 6 words) for a conversation starting with: \""
            + firstMessage.substring(0, Math.min(firstMessage.length(), 200))
            + "\". Reply with ONLY the title, no quotes.";
        try {
            return chatModel.call(new Prompt(prompt)).getResult().getOutput().getText().trim();
        } catch (Exception e) {
            return firstMessage.substring(0, Math.min(firstMessage.length(), 50));
        }
    }
}
```

### API Endpoints

```java
@RestController @RequestMapping("/api/conversations") @RequiredArgsConstructor
public class ConversationController {
    @GetMapping
    Page<ConversationSummaryDto> list(@AuthenticationPrincipal UserDetails u,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "false") boolean archived, @RequestParam(required = false) String search);

    @GetMapping("/{id}")    ConversationDetailDto get(@AuthenticationPrincipal UserDetails u, @PathVariable String id);
    @PostMapping            ConversationDetailDto create(@AuthenticationPrincipal UserDetails u, @RequestBody CreateConversationRequest r);
    @PatchMapping("/{id}")  ConversationSummaryDto update(@AuthenticationPrincipal UserDetails u, @PathVariable String id, @RequestBody UpdateConversationRequest r);
    @DeleteMapping("/{id}") void delete(@AuthenticationPrincipal UserDetails u, @PathVariable String id);
    @PostMapping("/{id}/archive")   void archive(@AuthenticationPrincipal UserDetails u, @PathVariable String id);
    @PostMapping("/{id}/unarchive") void unarchive(@AuthenticationPrincipal UserDetails u, @PathVariable String id);
    @PostMapping("/archive-all")    Map<String,Integer> archiveAll(@AuthenticationPrincipal UserDetails u);
    @PostMapping("/{id}/clone")     ConversationDetailDto clone(@AuthenticationPrincipal UserDetails u, @PathVariable String id);
}
```

### DTOs

```java
public record ConversationSummaryDto(String id, String title, String modelId, boolean archived,
    boolean pinned, String folderId, Instant createdAt, Instant updatedAt, int messageCount) {}
public record ConversationDetailDto(String id, String title, String modelId, boolean archived,
    boolean pinned, Instant createdAt, Instant updatedAt, List<ChatMessageDto> messages) {}
public record ChatMessageDto(String id, int sequenceNumber, MessageRole role, String content,
    String modelId, Integer tokenCount, Long generationTimeMs, String parentMessageId, boolean active, Instant createdAt) {}
public record CreateConversationRequest(@NotBlank String modelId, String title, String systemPrompt) {}
public record UpdateConversationRequest(String title, Boolean pinned, String folderId) {}
```

### ChatService Integration

Modify the existing `ChatService` to persist messages and use conversation context:

```java
public Flux<String> streamChat(User user, String conversationId, String userMessage, String modelId) {
    Conversation conv = conversationId == null
        ? conversationService.create(user, modelId, null)
        : convRepo.findById(conversationId).orElseThrow();
    if (!conv.getUser().getId().equals(user.getId()))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);

    conversationService.addMessage(conv, MessageRole.USER, userMessage, null);

    List<Message> history = conv.getMessages().stream().filter(ChatMessage::isActive)
        .map(m -> switch (m.getRole()) {
            case SYSTEM -> (Message) new SystemMessage(m.getContent());
            case USER -> new UserMessage(m.getContent());
            case ASSISTANT -> new AssistantMessage(m.getContent());
        }).toList();

    StringBuilder acc = new StringBuilder();
    long start = System.currentTimeMillis();
    return chatModel.stream(new Prompt(history))
        .map(r -> { String chunk = r.getResult().getOutput().getText(); acc.append(chunk); return chunk; })
        .doOnComplete(() -> {
            ChatMessage msg = conversationService.addMessage(conv, MessageRole.ASSISTANT, acc.toString(), modelId);
            msg.setGenerationTimeMs(System.currentTimeMillis() - start);
            msgRepo.save(msg);
            conversationService.autoGenerateTitle(conv.getId());
        });
}
```

### Acceptance Criteria

1. New chat creates a conversation; it appears in sidebar after first message
2. Navigating away and returning preserves full message history
3. Title auto-generates after first assistant response
4. User can rename, delete, search conversations
5. Conversations are user-scoped; no cross-user access
6. Sidebar paginates (infinite scroll)

---

## 2. Markdown & LaTeX Rendering

### Overview
Render GFM Markdown (tables, blockquotes, task lists, emphasis, links) and LaTeX math (`$inline$`, `$$block$$`) in chat messages.

### Dependencies
Frontend only. Vendor these into `src/main/resources/static/vendor/`:
- **marked.js** 15.x — Markdown parser
- **DOMPurify** 3.x — XSS sanitization
- **KaTeX** 0.16.x — LaTeX math rendering

### Frontend

```javascript
// static/js/markdown-renderer.js
import { marked } from '/vendor/marked/marked.min.js';
import DOMPurify from '/vendor/dompurify/purify.min.js';
import katex from '/vendor/katex/katex.min.js';

function preprocessLatex(text) {
    const blocks = [];
    text = text.replace(/\$\$([\s\S]+?)\$\$/g, (_, math) => {
        blocks.push({ math: math.trim(), display: true });
        return `%%LATEX_BLOCK_${blocks.length - 1}%%`;
    });
    text = text.replace(/\$([^\s$][^$]*?[^\s$])\$/g, (_, math) => {
        blocks.push({ math: math.trim(), display: false });
        return `%%LATEX_INLINE_${blocks.length - 1}%%`;
    });
    return { text, blocks };
}

function postprocessLatex(html, blocks) {
    blocks.forEach((b, i) => {
        try {
            const rendered = katex.renderToString(b.math, { displayMode: b.display, throwOnError: false });
            html = html.replace(`%%LATEX_${b.display ? 'BLOCK' : 'INLINE'}_${i}%%`, rendered);
        } catch { html = html.replace(`%%LATEX_${b.display ? 'BLOCK' : 'INLINE'}_${i}%%`, `<code>${b.math}</code>`); }
    });
    return html;
}

marked.setOptions({ gfm: true, breaks: true, tables: true });

export function renderMarkdown(rawText) {
    const { text, blocks } = preprocessLatex(rawText);
    const rawHtml = marked.parse(text);
    const withLatex = postprocessLatex(rawHtml, blocks);
    return DOMPurify.sanitize(withLatex, {
        ADD_TAGS: ['span', 'div', 'math', 'semantics', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub', 'mfrac'],
        ADD_ATTR: ['class', 'style', 'aria-hidden']
    });
}
```

### Streaming Integration
Accumulate SSE chunks and debounce-render every 100ms:
```javascript
let renderTimeout;
function onStreamChunk(chunk, container) {
    accumulatedText += chunk;
    clearTimeout(renderTimeout);
    renderTimeout = setTimeout(() => { container.innerHTML = renderMarkdown(accumulatedText); }, 100);
}
```

### Acceptance Criteria
1. Tables, blockquotes, task lists, bold/italic, links render correctly
2. `$E=mc^2$` renders inline; `$$\int_0^1 f(x)dx$$` renders display math
3. All HTML sanitized via DOMPurify — no XSS
4. Streaming renders incrementally without layout thrash

---

## 3. Code Syntax Highlighting

### Overview
Language-aware syntax highlighting in code blocks with language label, line numbers, and copy button.

### Dependencies
Frontend only. Vendor **Prism.js** (one-dark theme) + language packs (java, python, javascript, sql, bash, yaml, json, xml, go, ruby, csharp, typescript).

### Frontend

```javascript
// static/js/code-blocks.js — post-process rendered markdown
export function addCodeBlockControls(containerEl) {
    containerEl.querySelectorAll('pre > code').forEach(block => {
        const wrapper = document.createElement('div');
        wrapper.className = 'code-block-wrapper';
        const lang = (block.className.match(/language-(\w+)/) || [, 'text'])[1];
        const header = document.createElement('div');
        header.className = 'code-block-header';
        header.innerHTML = `<span class="code-lang">${lang}</span>
            <button class="copy-btn" onclick="navigator.clipboard.writeText(this.closest('.code-block-wrapper').querySelector('code').textContent).then(()=>{this.textContent='Copied!';setTimeout(()=>this.textContent='Copy',2000)})">Copy</button>`;
        block.parentElement.before(wrapper);
        wrapper.append(header, block.parentElement);

        if (window.Prism && Prism.languages[lang]) {
            block.innerHTML = Prism.highlight(block.textContent, Prism.languages[lang], lang);
        }
    });
}
```

### Integration
Call `addCodeBlockControls(container)` after each `renderMarkdown()` call.

### Acceptance Criteria
1. Code blocks show syntax-highlighted with correct language colors
2. Language label in header; copy button copies raw code
3. Minimum 12 languages supported
4. Prism.js lazy-loads language packs on demand

---

## 4. Async Chat

### Overview
Decouple LLM response generation from the browser SSE connection. If user navigates away, backend completes the response and persists it.

### Async Executor Config

```java
@Configuration @EnableAsync
public class AsyncConfig {
    @Bean(name = "chatExecutor")
    public TaskExecutor chatExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(10); e.setMaxPoolSize(50); e.setQueueCapacity(200);
        e.setThreadNamePrefix("chat-async-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return e;
    }
}
```

### SSE Emitter Registry

```java
@Component
public class SseEmitterRegistry {
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String convId, long timeout) {
        SseEmitter e = new SseEmitter(timeout);
        emitters.put(convId, e);
        e.onCompletion(() -> emitters.remove(convId));
        e.onTimeout(() -> emitters.remove(convId));
        e.onError(ex -> emitters.remove(convId));
        return e;
    }
    public void trySend(String convId, String data) {
        SseEmitter e = emitters.get(convId);
        if (e != null) try { e.send(SseEmitter.event().data(data)); } catch (Exception ex) { emitters.remove(convId); }
    }
    public void complete(String convId) {
        SseEmitter e = emitters.remove(convId);
        if (e != null) try { e.complete(); } catch (Exception ignored) {}
    }
    public boolean isConnected(String convId) { return emitters.containsKey(convId); }
}
```

### Async Chat Service

```java
@Service @RequiredArgsConstructor
public class AsyncChatService {
    private final ConversationService convService;
    private final ChatModel chatModel;
    private final SseEmitterRegistry emitterRegistry;
    private final ChatMessageRepository msgRepo;

    @Async("chatExecutor")
    public void processChat(String convId, String modelId) {
        Conversation conv = convService.getById(convId);
        List<Message> history = conv.getMessages().stream().filter(ChatMessage::isActive)
            .map(m -> switch (m.getRole()) {
                case SYSTEM -> (Message) new SystemMessage(m.getContent());
                case USER -> new UserMessage(m.getContent());
                case ASSISTANT -> new AssistantMessage(m.getContent());
            }).toList();

        StringBuilder acc = new StringBuilder();
        long start = System.currentTimeMillis();
        chatModel.stream(new Prompt(history))
            .doOnNext(r -> { String ch = r.getResult().getOutput().getText(); if (ch != null) { acc.append(ch); emitterRegistry.trySend(convId, ch); } })
            .doOnComplete(() -> {
                ChatMessage m = convService.addMessage(conv, MessageRole.ASSISTANT, acc.toString(), modelId);
                m.setGenerationTimeMs(System.currentTimeMillis() - start);
                msgRepo.save(m);
                convService.autoGenerateTitle(convId);
                emitterRegistry.complete(convId);
            })
            .doOnError(err -> {
                convService.addMessage(conv, MessageRole.ASSISTANT, "[Error: " + err.getMessage() + "]", modelId);
                emitterRegistry.complete(convId);
            })
            .subscribe();
    }
}
```

### Acceptance Criteria
1. User sends message, navigates away, returns — response is present
2. If user stays, SSE streaming works identically to before
3. Concurrent users don't block each other (thread pool)
4. Errors during generation are persisted as error messages

---

## 5. Bi-Directional Text (RTL)

### Overview
Auto-detect RTL scripts (Arabic, Hebrew, Farsi, Urdu) and apply `dir="rtl"` per-message.

### Frontend

```javascript
function detectDirection(text) {
    const rtl = /[\u0600-\u06FF\u0750-\u077F\u0590-\u05FF\uFB1D-\uFB4F]/;
    const sample = text.substring(0, 100);
    const count = (sample.match(new RegExp(rtl.source, 'g')) || []).length;
    return count > sample.length * 0.3 ? 'rtl' : 'ltr';
}
// Apply: <div class="message" dir="${detectDirection(content)}">
```

```css
.message[dir="rtl"] { direction: rtl; text-align: right; }
.message[dir="rtl"] code { direction: ltr; text-align: left; }
```

### Acceptance Criteria
1. Arabic/Hebrew messages auto-display right-to-left
2. Code blocks within RTL remain LTR
3. No manual toggle needed

---

## 6. Hybrid RAG Search (BM25 + Vector + Re-ranking)

### Overview
Combine PostgreSQL full-text BM25 scoring with pgvector similarity via Reciprocal Rank Fusion, followed by optional cross-encoder re-ranking.

### Migration

```sql
-- V3__bm25_search.sql
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS content_tsv tsvector;
UPDATE document_chunks SET content_tsv = to_tsvector('english', content);
CREATE INDEX idx_chunks_tsv ON document_chunks USING GIN(content_tsv);

CREATE OR REPLACE FUNCTION update_chunk_tsv() RETURNS TRIGGER AS $$
BEGIN NEW.content_tsv := to_tsvector('english', NEW.content); RETURN NEW; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_chunk_tsv BEFORE INSERT OR UPDATE OF content ON document_chunks
FOR EACH ROW EXECUTE FUNCTION update_chunk_tsv();
```

### Service

```java
@Service @RequiredArgsConstructor
public class HybridSearchService {
    private final JdbcTemplate jdbc;
    private final VectorStore vectorStore;
    @Value("${rag.top-k:5}") private int topK;
    @Value("${rag.hybrid.bm25-weight:0.3}") private double bm25Weight;
    @Value("${rag.hybrid.vector-weight:0.7}") private double vectorWeight;
    @Value("${rag.hybrid.candidate-multiplier:4}") private int candidateMultiplier;

    public List<RankedDocument> search(String query, Long userId, boolean includeShared) {
        int candidates = topK * candidateMultiplier;
        List<ScoredChunk> bm25 = bm25Search(query, userId, includeShared, candidates);
        List<ScoredChunk> vector = vectorSearch(query, userId, includeShared, candidates);
        Map<String, RankedDocument> fused = rrf(bm25, vector);
        return fused.values().stream()
            .sorted(Comparator.comparingDouble(RankedDocument::score).reversed())
            .limit(topK).toList();
    }

    private List<ScoredChunk> bm25Search(String query, Long userId, boolean shared, int limit) {
        String sql = """
            SELECT id, content, document_id, ts_rank_cd(content_tsv, plainto_tsquery('english', ?)) AS score
            FROM document_chunks WHERE (user_id = ? OR (? AND is_shared = TRUE))
            AND content_tsv @@ plainto_tsquery('english', ?) ORDER BY score DESC LIMIT ?""";
        return jdbc.query(sql, new Object[]{query, userId, shared, query, limit},
            (rs, i) -> new ScoredChunk(rs.getString("id"), rs.getString("content"), rs.getString("document_id"), rs.getDouble("score")));
    }

    private List<ScoredChunk> vectorSearch(String query, Long userId, boolean shared, int limit) {
        String filter = shared ? "(user_id == '" + userId + "' || is_shared == 'true')" : "user_id == '" + userId + "'";
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(limit).filterExpression(filter).build())
            .stream().map(d -> new ScoredChunk(d.getId(), d.getText(), d.getMetadata().getOrDefault("document_id","").toString(), d.getScore())).toList();
    }

    private Map<String, RankedDocument> rrf(List<ScoredChunk> bm25, List<ScoredChunk> vector) {
        int k = 60;
        Map<String, RankedDocument> results = new HashMap<>();
        for (int i = 0; i < bm25.size(); i++) { ScoredChunk c = bm25.get(i); double s = bm25Weight / (k + i + 1);
            results.merge(c.id(), new RankedDocument(c.id(), c.content(), c.documentId(), s), (a,b) -> a.addScore(b.score())); }
        for (int i = 0; i < vector.size(); i++) { ScoredChunk c = vector.get(i); double s = vectorWeight / (k + i + 1);
            results.merge(c.id(), new RankedDocument(c.id(), c.content(), c.documentId(), s), (a,b) -> a.addScore(b.score())); }
        return results;
    }
}

public record ScoredChunk(String id, String content, String documentId, double score) {}
public record RankedDocument(String id, String content, String documentId, double score) {
    public RankedDocument addScore(double add) { return new RankedDocument(id, content, documentId, score + add); }
}
```

### Configuration
```yaml
rag:
  top-k: 5
  hybrid:
    enabled: true
    bm25-weight: 0.3
    vector-weight: 0.7
    candidate-multiplier: 4
```

### Acceptance Criteria
1. Results combine keyword + semantic matches
2. Exact keyword matches rank higher than pure semantic
3. Configurable weights; `rag.hybrid.enabled=false` reverts to vector-only
4. Hybrid search < 500ms for typical document sets

---

## 7. RAG Citations

### Overview
Instruct the LLM to cite RAG sources using `[Source N]` notation; parse and render as interactive badges.

### Prompt Builder

```java
@Service
public class RagPromptBuilder {
    public String build(String query, List<RankedDocument> docs) {
        StringBuilder sb = new StringBuilder("Use these documents to answer. Cite with [Source N].\n\n");
        for (int i = 0; i < docs.size(); i++) {
            RankedDocument d = docs.get(i);
            sb.append(String.format("[Source %d] (%.0f%% relevance) From '%s':\n%s\n\n", i+1, d.score()*100, d.documentId(), d.content()));
        }
        sb.append("Question: ").append(query).append("\nAnswer with [Source N] citations inline.");
        return sb.toString();
    }
}
```

### Frontend
```javascript
function renderCitations(html, citations) {
    return html.replace(/\[Source (\d+)\]/g, (_, num) => {
        const c = citations[parseInt(num) - 1];
        if (!c) return `[Source ${num}]`;
        return `<span class="citation-badge" title="${c.docTitle} (${Math.round(c.relevance*100)}%)">[${num}]</span>`;
    });
}
```

### Acceptance Criteria
1. RAG responses include `[Source N]` rendered as styled badges
2. Hover shows document name + relevance %
3. Non-RAG responses show no citation artifacts

---

## 8. Web URL RAG

### Overview
`# https://example.com` in chat input fetches the URL and injects content as RAG context.

### Dependencies
```xml
<dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId><version>1.18.3</version></dependency>
```

### Service

```java
@Service
public class WebContentService {
    @Value("${rag.web.max-content-length:50000}") private int maxLen;
    @Value("${rag.web.timeout-ms:10000}") private int timeout;

    public WebPageContent fetch(String url) {
        Document doc = Jsoup.connect(url).timeout(timeout).userAgent("CF-Llama-Chat/2.0").maxBodySize(maxLen * 2).get();
        doc.select("script,style,nav,footer,header,aside").remove();
        String text = doc.body().text();
        if (text.length() > maxLen) text = text.substring(0, maxLen);
        return new WebPageContent(url, doc.title(), text, Instant.now());
    }
}
public record WebPageContent(String url, String title, String text, Instant fetchedAt) {}
```

### Chat Integration
```java
private static final Pattern URL_RAG = Pattern.compile("#\\s*(https?://\\S+)");
// In ChatService: detect URLs, fetch, prepend as context
```

### Acceptance Criteria
1. `# https://example.com` fetches and injects page content
2. Multiple URLs supported per message
3. Timeout/size limits prevent abuse

---

## 9. Web Search Provider

### Overview
Pluggable web search with at least one zero-config provider (DuckDuckGo).

### Service

```java
@Service @RequiredArgsConstructor
public class WebSearchService {
    @Value("${search.enabled:false}") private boolean enabled;
    @Value("${search.provider:duckduckgo}") private String provider;
    @Value("${search.tavily.api-key:}") private String tavilyKey;
    @Value("${search.brave.api-key:}") private String braveKey;

    public List<SearchResult> search(String query, int max) {
        if (!enabled) return List.of();
        return switch (provider) {
            case "tavily" -> searchTavily(query, max);
            case "brave" -> searchBrave(query, max);
            default -> searchDuckDuckGo(query, max);
        };
    }
    // Provider implementations using RestTemplate / OkHttp
}
public record SearchResult(String title, String url, String snippet) {}
```

### Configuration
```yaml
search:
  enabled: ${SEARCH_ENABLED:false}
  provider: ${SEARCH_PROVIDER:duckduckgo}
  auto-inject: ${SEARCH_AUTO_INJECT:false}
  tavily.api-key: ${TAVILY_API_KEY:}
  brave.api-key: ${BRAVE_API_KEY:}
```

### Acceptance Criteria
1. DuckDuckGo works with zero configuration
2. Tavily/Brave work with API key
3. Results injectable as RAG context for non-tool-calling models

---

## 10. Agentic Search

### Overview
Spring AI function-calling tools so capable models can autonomously search and fetch web pages.

### Tool Functions

```java
@Component
public class WebSearchTool implements Function<WebSearchTool.Req, WebSearchTool.Res> {
    public record Req(@JsonProperty(required = true) String query, int maxResults) {}
    public record Res(List<SearchResult> results) {}
    private final WebSearchService webSearchService;
    @Override public Res apply(Req r) { return new Res(webSearchService.search(r.query(), r.maxResults() > 0 ? r.maxResults() : 5)); }
}

@Component
public class FetchUrlTool implements Function<FetchUrlTool.Req, FetchUrlTool.Res> {
    public record Req(@JsonProperty(required = true) String url) {}
    public record Res(String title, String content, String url) {}
    private final WebContentService webContentService;
    @Override public Res apply(Req r) { WebPageContent p = webContentService.fetch(r.url()); return new Res(p.title(), p.text(), p.url()); }
}
```

### Tool Registration
```java
@Configuration
public class ToolConfig {
    @Bean @Description("Search the web for current information")
    Function<WebSearchTool.Req, WebSearchTool.Res> webSearch(WebSearchTool t) { return t; }

    @Bean @Description("Fetch full text content of a web page URL")
    Function<FetchUrlTool.Req, FetchUrlTool.Res> fetchUrl(FetchUrlTool t) { return t; }
}
```

### Enable in Chat
```java
ChatOptions opts = ChatOptions.builder().model(modelId).functions("webSearch", "fetchUrl").build();
```

### Acceptance Criteria
1. Models invoke `webSearch` and `fetchUrl` autonomously
2. Multi-step research (search → fetch → search again) works
3. Works with Tanzu GenAI models supporting function calling

---

## 11. LDAP / Active Directory Auth

### Overview
Enterprise directory authentication with auto-provisioning on first login.

### Dependencies
```xml
<dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-ldap</artifactId></dependency>
<dependency><groupId>org.springframework.ldap</groupId><artifactId>spring-ldap-core</artifactId></dependency>
```

### Configuration
```yaml
auth.ldap:
  enabled: ${LDAP_ENABLED:false}
  url: ${LDAP_URL:ldap://localhost:389}
  base: ${LDAP_BASE:dc=example,dc=com}
  user-dn-pattern: ${LDAP_USER_DN_PATTERN:uid={0},ou=people}
  user-search-filter: ${LDAP_USER_SEARCH_FILTER:(uid={0})}
  group-search-base: ${LDAP_GROUP_SEARCH_BASE:ou=groups}
  manager-dn: ${LDAP_MANAGER_DN:}
  manager-password: ${LDAP_MANAGER_PASSWORD:}
  role-mapping: { cf-llama-admins: ADMIN, cf-llama-users: USER }
  default-role: USER
```

### Auto-Provisioning
On successful LDAP auth, check if user exists locally. If not, create with role mapped from LDAP groups. Set `auth_provider = 'LDAP'`.

### Migration
```sql
-- V5__auth_providers.sql
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN external_id VARCHAR(255);
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
```

### Acceptance Criteria
1. `LDAP_ENABLED=true` authenticates against directory
2. First login auto-creates local user with mapped role
3. LDAP group → local role mapping is configurable
4. Local auth works alongside LDAP for admin fallback
5. Zero LDAP code loaded when disabled

---

## 12. SCIM 2.0 Provisioning

### Overview
RFC 7643/7644 compliant endpoints for automated user lifecycle from Okta, Azure AD, Google Workspace.

### Endpoints
```
GET    /scim/v2/Users           — List/search users
GET    /scim/v2/Users/{id}      — Get user
POST   /scim/v2/Users           — Create user
PUT    /scim/v2/Users/{id}      — Replace user
PATCH  /scim/v2/Users/{id}      — Partial update (activate/deactivate)
DELETE /scim/v2/Users/{id}      — Deactivate user
```

### Security
Bearer token auth on `/scim/**` via `ScimBearerTokenFilter`. Token configured as `SCIM_BEARER_TOKEN` env var. CSRF disabled for SCIM endpoints.

### Key Behaviors
- Create: provision local user with `auth_provider='SCIM'`, empty password (SSO-only)
- Deactivate: soft-delete via `enabled=false`
- Filter support: `userName eq "value"` parsed and mapped to JPA query
- All responses use SCIM JSON schemas

### Configuration
```yaml
auth.scim:
  enabled: ${SCIM_ENABLED:false}
  bearer-token: ${SCIM_BEARER_TOKEN:}
```

### Acceptance Criteria
1. Okta/Azure AD can CRUD users via SCIM endpoints
2. Deactivation in IdP deactivates locally
3. SCIM filter syntax `userName eq "value"` works
4. RFC 7644 response schemas

---

## 13. Granular Permissions

### Overview
Replace Admin/User binary with fine-grained per-action permissions assigned to roles.

### Migration
```sql
-- V6__permissions.sql
CREATE TABLE permissions (id BIGSERIAL PRIMARY KEY, code VARCHAR(100) UNIQUE NOT NULL, description VARCHAR(255), category VARCHAR(50));
CREATE TABLE roles (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL);
CREATE TABLE role_permissions (role_id BIGINT REFERENCES roles(id), permission_id BIGINT REFERENCES permissions(id), PRIMARY KEY(role_id, permission_id));
ALTER TABLE users ADD COLUMN role_id BIGINT REFERENCES roles(id);

INSERT INTO permissions (code, description, category) VALUES
('chat.create','Create conversations','chat'),('chat.delete','Delete conversations','chat'),('chat.export','Export conversations','chat'),
('document.upload','Upload documents','document'),('document.delete','Delete documents','document'),('document.library.access','Access shared library','document'),
('model.select','Select chat models','model'),('model.configure','Configure model params','model'),
('admin.users','Manage users','admin'),('admin.models','Manage models','admin'),('admin.settings','Manage settings','admin');

INSERT INTO roles (name) VALUES ('ADMIN'),('USER'),('VIEWER');
INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ADMIN';
INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'USER' AND p.category != 'admin';
INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'VIEWER' AND p.code IN ('chat.create','model.select');
```

### Annotation + Aspect
```java
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission { String value(); }

@Aspect @Component @RequiredArgsConstructor
public class PermissionAspect {
    private final PermissionService permissionService;
    @Before("@annotation(rp)") public void check(JoinPoint jp, RequiresPermission rp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        permissionService.require(auth.getName(), rp.value());
    }
}
```

### Acceptance Criteria
1. `@RequiresPermission("document.upload")` guards endpoints
2. Admin portal shows role → permission matrix
3. New custom roles creatable with arbitrary permission sets

---

## 14. Model Whitelisting

### Overview
Admins restrict which models non-admin roles can access.

### Migration
```sql
-- V7__model_access.sql
CREATE TABLE model_access_rules (id BIGSERIAL PRIMARY KEY, model_id VARCHAR(255) NOT NULL,
    role_id BIGINT REFERENCES roles(id), allowed BOOLEAN DEFAULT TRUE, created_at TIMESTAMPTZ DEFAULT NOW());
ALTER TABLE system_settings ADD COLUMN model_access_control_enabled BOOLEAN DEFAULT FALSE;
```

### Service
When `model_access_control_enabled = true`, filter the model list to only show models with `allowed = true` rules for the user's role. Admins always see all models.

### Acceptance Criteria
1. Disabled by default — all users see all models
2. When enabled, non-admins only see whitelisted models
3. Admin UI shows model × role checkbox matrix

---

## 15. Rate Limiting

### Overview
Per-user, per-role API rate limiting using Bucket4j.

### Dependencies
```xml
<dependency><groupId>io.github.bucket4j</groupId><artifactId>bucket4j-core</artifactId><version>8.10.1</version></dependency>
```

### Configuration
```yaml
rate-limit:
  enabled: ${RATE_LIMIT_ENABLED:true}
  roles:
    ADMIN: { chat-per-minute: 0 }    # unlimited
    USER:  { chat-per-minute: 20, chat-per-hour: 200, uploads-per-hour: 50 }
    VIEWER: { chat-per-minute: 10, chat-per-hour: 60 }
```

### Annotation
```java
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited { String value(); }
// Usage: @RateLimited("chat") on chat endpoint
```

Exceeding limit → HTTP 429 with `Retry-After` header.

### Acceptance Criteria
1. 429 returned when limits exceeded
2. Per-role configurable
3. Admins unlimited
4. Disabled when `RATE_LIMIT_ENABLED=false`

---

## 16. Prompt Presets (/ Command System)

### Overview
Type `/` in chat to see searchable prompt presets. Supports user-created and shared presets.

### Migration
```sql
-- V8__prompt_presets.sql
CREATE TABLE prompt_presets (
    id VARCHAR(36) PRIMARY KEY, command VARCHAR(100) NOT NULL, title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL, description VARCHAR(500), owner_id BIGINT REFERENCES users(id),
    shared BOOLEAN DEFAULT FALSE, created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_preset_owner ON prompt_presets(owner_id);
```

### API
```java
@RestController @RequestMapping("/api/prompts")
public class PromptPresetController {
    @GetMapping List<PromptPresetDto> list(@AuthenticationPrincipal UserDetails u, @RequestParam(required = false) String search);
    @GetMapping("/search") List<PromptPresetDto> search(@AuthenticationPrincipal UserDetails u, @RequestParam String q);
    @PostMapping PromptPresetDto create(@AuthenticationPrincipal UserDetails u, @RequestBody CreatePromptRequest r);
    @PutMapping("/{id}") PromptPresetDto update(@PathVariable String id, @RequestBody UpdatePromptRequest r);
    @DeleteMapping("/{id}") void delete(@AuthenticationPrincipal UserDetails u, @PathVariable String id);
}
```

### Frontend
```javascript
chatInput.addEventListener('input', async (e) => {
    if (e.target.value.startsWith('/')) {
        const q = e.target.value.substring(1);
        const presets = await (await fetch(`/api/prompts/search?q=${encodeURIComponent(q)}`)).json();
        showDropdown(presets);  // Click inserts preset.content into input
    } else hideDropdown();
});
```

### Acceptance Criteria
1. `/` triggers dropdown; `/sum` filters to "summarize"
2. Selecting preset fills input
3. Users create personal; admins create shared presets

---

## 17. OpenTelemetry Integration

### Overview
OTLP export for traces, metrics, and logs.

### Dependencies
```xml
<dependency><groupId>io.opentelemetry.instrumentation</groupId><artifactId>opentelemetry-spring-boot-starter</artifactId></dependency>
<dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-otlp</artifactId></dependency>
```

### Configuration
```yaml
management:
  otlp:
    metrics.export: { url: "${OTEL_ENDPOINT:http://localhost:4318}/v1/metrics", enabled: "${OTEL_ENABLED:false}" }
    tracing.endpoint: "${OTEL_ENDPOINT:http://localhost:4318}/v1/traces"
  tracing.sampling.probability: ${OTEL_SAMPLING:1.0}
```

### Custom Metrics
```java
@Configuration @RequiredArgsConstructor
public class MetricsConfig {
    private final MeterRegistry registry;
    @Bean Counter chatCounter() { return Counter.builder("cfllama.chat.requests").register(registry); }
    @Bean Timer chatTimer() { return Timer.builder("cfllama.chat.duration").register(registry); }
    @Bean Counter ragCounter() { return Counter.builder("cfllama.rag.searches").register(registry); }
    @Bean Gauge activeUsersGauge(ActiveUserTracker t) { return Gauge.builder("cfllama.users.active", t, ActiveUserTracker::getActiveCount).register(registry); }
}
```

### Acceptance Criteria
1. `OTEL_ENABLED=true` exports traces and metrics
2. Chat duration, RAG searches, active users tracked
3. Each request produces distributed trace spans
4. Zero overhead when disabled

# PHASE 2 — MEDIUM PRIORITY

---

## 18. Chat Export & Import

### Overview
Export conversations as JSON/PDF/TXT. Import previously exported JSON to restore conversations.

### Service

```java
@Service @RequiredArgsConstructor
public class ChatExportService {
    private final ConversationRepository convRepo;
    private final ObjectMapper mapper;

    public byte[] exportJson(String convId, User user) {
        Conversation c = getOwned(convId, user);
        ExportBundle b = new ExportBundle("1.0", "cf-llama-chat", Instant.now(), List.of(toExportDto(c)));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(b);
    }

    public byte[] exportTxt(String convId, User user) {
        Conversation c = getOwned(convId, user);
        StringBuilder sb = new StringBuilder("# " + c.getTitle() + "\n\n");
        c.getMessages().stream().filter(ChatMessage::isActive).forEach(m ->
            sb.append(m.getRole()).append(":\n").append(m.getContent()).append("\n\n"));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportAllJson(User user) {
        List<Conversation> all = convRepo.findAllByUserId(user.getId());
        ExportBundle b = new ExportBundle("1.0", "cf-llama-chat", Instant.now(), all.stream().map(this::toExportDto).toList());
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(b);
    }

    @Transactional
    public List<String> importJson(byte[] data, User user) {
        ExportBundle bundle = mapper.readValue(data, ExportBundle.class);
        return bundle.conversations().stream().map(dto -> {
            Conversation c = new Conversation();
            c.setUser(user); c.setTitle(dto.title() + " (imported)"); c.setModelId(dto.modelId());
            c = convRepo.save(c);
            int seq = 0;
            for (var m : dto.messages()) { addMessage(c, MessageRole.valueOf(m.role()), m.content(), m.modelId(), seq++); }
            return c.getId();
        }).toList();
    }
}

public record ExportBundle(String version, String source, Instant exportedAt, List<ConversationExportDto> conversations) {}
public record ConversationExportDto(String id, String title, String modelId, Instant createdAt, List<MessageExportDto> messages) {}
public record MessageExportDto(String role, String content, String modelId, Integer tokenCount, Instant createdAt) {}
```

### API
```java
@GetMapping("/api/conversations/{id}/export")
ResponseEntity<byte[]> export(@AuthenticationPrincipal UserDetails u, @PathVariable String id, @RequestParam(defaultValue = "json") String format);

@GetMapping("/api/conversations/export-all")
ResponseEntity<byte[]> exportAll(@AuthenticationPrincipal UserDetails u);

@PostMapping("/api/conversations/import") @RequiresPermission("chat.create")
List<String> importChats(@AuthenticationPrincipal UserDetails u, @RequestParam("file") MultipartFile file);
```

### Acceptance Criteria
1. JSON/TXT export downloads correctly formatted files
2. Export-all bundles every conversation
3. Import creates conversations marked "(imported)"

---

## 19. Chat Archive

### Overview
Archive completed conversations. Already scaffolded in feature #1 via `archived` boolean on `Conversation`.

### Endpoints (already defined in #1)
- `POST /api/conversations/{id}/archive`
- `POST /api/conversations/{id}/unarchive`
- `POST /api/conversations/archive-all`
- `GET /api/conversations?archived=true`

### Acceptance Criteria
1. Archived chats hidden from main sidebar
2. Viewable under "Archive" section
3. Restorable individually

---

## 20. Message Editing

### Overview
Edit sent user messages with option to save-only or save-and-regenerate.

### API
```java
@PutMapping("/api/conversations/{convId}/messages/{msgId}")
ChatMessageDto edit(@AuthenticationPrincipal UserDetails u, @PathVariable String convId, @PathVariable String msgId, @RequestBody EditMessageRequest r);

public record EditMessageRequest(@NotBlank String content, boolean regenerate) {}
```

### Logic
- Only `USER` role messages editable
- `regenerate=false`: save content, no new response
- `regenerate=true`: deactivate all messages after edited message, trigger new async chat

### Acceptance Criteria
1. Users edit own messages
2. Save-only preserves existing responses
3. Save-and-regenerate creates new response branch

---

## 21. Response Regeneration

### Overview
Regenerate the last assistant response. Previous generations preserved as inactive branches.

### API
```java
@PostMapping("/api/conversations/{convId}/regenerate")
SseEmitter regenerate(@AuthenticationPrincipal UserDetails u, @PathVariable String convId, @RequestParam(required = false) String modelId);

@GetMapping("/api/conversations/{convId}/messages/{msgId}/alternatives")
List<ChatMessageDto> alternatives(@AuthenticationPrincipal UserDetails u, @PathVariable String convId, @PathVariable String msgId);
```

### Logic
1. Mark last active assistant message as `active=false`
2. Trigger async chat from the last active user message
3. `/alternatives` returns all messages at that sequence position (active + inactive)

### Acceptance Criteria
1. Regenerate creates new response, hides old
2. User can view all alternatives at that position
3. User can switch active response between alternatives

---

## 22. Chat Sharing

### Overview
Generate shareable links for conversations between users within the same organization.

### Migration
```sql
-- V9__chat_sharing.sql
CREATE TABLE shared_chats (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    share_token VARCHAR(64) UNIQUE NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    organization_id BIGINT REFERENCES organizations(id),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### API
```java
@PostMapping("/api/conversations/{id}/share")
SharedChatDto share(@AuthenticationPrincipal UserDetails u, @PathVariable String id);

@GetMapping("/api/shared/{token}")
ConversationDetailDto viewShared(@PathVariable String token); // Read-only, org-scoped
```

### Acceptance Criteria
1. Sharing generates a unique token URL
2. Shared view is read-only
3. Optional expiration support

---

## 23. YouTube RAG Pipeline

### Overview
Extract video transcripts from YouTube URLs for RAG context injection.

### Service
```java
@Service
public class YouTubeTranscriptService {
    private static final Pattern YT = Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]{11})");

    public boolean isYouTubeUrl(String url) { return YT.matcher(url).find(); }

    public String getTranscript(String url) {
        Matcher m = YT.matcher(url); m.find();
        String videoId = m.group(1);
        // Fetch page, extract captionTracks from ytInitialPlayerResponse
        // Parse caption XML, join text nodes
        Document page = Jsoup.connect("https://www.youtube.com/watch?v=" + videoId).userAgent("Mozilla/5.0").get();
        String captionUrl = extractCaptionUrl(page.html());
        if (captionUrl != null) {
            Document caps = Jsoup.connect(captionUrl).get();
            return caps.select("text").stream().map(Element::text).collect(Collectors.joining(" "));
        }
        return "";
    }
}
```

### Integration
Detect YouTube URLs in `# URL` pattern, fetch transcript, inject as context.

### Acceptance Criteria
1. `# https://youtube.com/watch?v=xxx` extracts transcript
2. Works with both youtube.com and youtu.be URLs
3. Graceful fallback when no transcript available

---

## 24. Advanced RAG Queries

### Overview
Pre-process conversation history to formulate optimal standalone search queries before RAG retrieval.

### Service
```java
@Service @RequiredArgsConstructor
public class QueryRewriteService {
    private final ChatModel chatModel;
    @Value("${rag.query-rewrite.enabled:true}") private boolean enabled;

    public String rewrite(String userMsg, List<ChatMessage> history) {
        if (!enabled || history.size() < 2) return userMsg;
        String recent = history.stream().skip(Math.max(0, history.size() - 6))
            .map(m -> m.getRole() + ": " + m.getContent().substring(0, Math.min(m.getContent().length(), 200)))
            .collect(Collectors.joining("\n"));
        String prompt = "Given this conversation and latest message, generate a standalone search query:\n" + recent + "\nLatest: " + userMsg + "\nReply with ONLY the query.";
        try { return chatModel.call(new Prompt(prompt)).getResult().getOutput().getText().trim(); }
        catch (Exception e) { return userMsg; }
    }
}
```

### Acceptance Criteria
1. "What about it?" resolves to actual referent from context
2. Falls back to raw query on error
3. Configurable via `rag.query-rewrite.enabled`

---

## 25. Document Library

### Overview
Shared document workspace accessible to all users with permission. Extend existing documents with `is_shared` flag.

### Migration
```sql
-- V10__document_library.sql
ALTER TABLE documents ADD COLUMN is_shared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE document_chunks ADD COLUMN is_shared BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_docs_shared ON documents(is_shared) WHERE is_shared = TRUE;
```

### API
```java
@PostMapping("/api/documents/upload")
DocumentDto upload(@RequestParam("file") MultipartFile f, @RequestParam(defaultValue = "false") boolean shared);

@GetMapping("/api/documents/library") @RequiresPermission("document.library.access")
Page<DocumentDto> listShared(Pageable p);
```

### Acceptance Criteria
1. Shared documents visible to all permitted users
2. RAG searches include shared docs when using `#`
3. Only uploader or admin can delete shared docs

---

## 26. Advanced Document Extractors

### Overview
Support Docling or Azure Document Intelligence for higher-fidelity document extraction beyond basic Apache Tika.

### Configuration
```yaml
rag:
  extractor: ${DOCUMENT_EXTRACTOR:tika}  # tika, docling, azure-di
  docling: { url: ${DOCLING_URL:http://localhost:5000} }
  azure-di: { endpoint: ${AZURE_DI_ENDPOINT:}, key: ${AZURE_DI_KEY:} }
```

### Service
```java
public interface DocumentExtractor {
    String extract(byte[] fileContent, String filename);
}

@Component @ConditionalOnProperty(name = "rag.extractor", havingValue = "tika", matchIfMissing = true)
public class TikaExtractor implements DocumentExtractor { /* existing impl */ }

@Component @ConditionalOnProperty(name = "rag.extractor", havingValue = "docling")
public class DoclingExtractor implements DocumentExtractor {
    // POST to Docling REST API with file, receive structured text
}

@Component @ConditionalOnProperty(name = "rag.extractor", havingValue = "azure-di")
public class AzureDocIntelExtractor implements DocumentExtractor {
    // Azure Document Intelligence REST API call
}
```

### Acceptance Criteria
1. Tika remains default
2. Docling improves table/layout extraction from PDFs
3. Extractor switchable via env var

---

## 27. URL Fetching Tool

Already fully specified in [Feature #10 (Agentic Search)](#10-agentic-search) as `FetchUrlTool`. The same tool serves both agentic and manual URL fetching use cases.

---

## 28. Knowledge Attachment to Models

### Overview
Attach specific documents/knowledge bases to custom models. When using that model, attached documents are automatically included in RAG scope.

### Migration
```sql
CREATE TABLE model_knowledge (
    id BIGSERIAL PRIMARY KEY,
    model_id VARCHAR(255) NOT NULL,  -- References model identifier string
    document_id VARCHAR(36) NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(model_id, document_id)
);
```

Note: `model_id` stores the model identifier string (e.g., from Tanzu GenAI Locator or external bindings) rather than referencing a custom models table.

### Behavior
When chatting with a custom model that has knowledge attached, automatically include those documents in the RAG search scope regardless of `#` usage.

### Acceptance Criteria
1. Admin/owner can attach documents to custom models
2. Attached docs are automatically searched during chat
3. Removing attachment stops automatic inclusion

---

## 29. User Groups

### Overview
Group-based access control for models, knowledge, and tools.

### Migration
```sql
-- V14__user_groups.sql
CREATE TABLE user_groups (id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL,
    description VARCHAR(500), organization_id BIGINT REFERENCES organizations(id), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE user_group_members (user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    group_id BIGINT REFERENCES user_groups(id) ON DELETE CASCADE, PRIMARY KEY(user_id, group_id));
```

### API
```java
@RestController @RequestMapping("/api/admin/groups") @RequiresPermission("admin.users")
public class UserGroupController {
    @GetMapping List<GroupDto> list();
    @PostMapping GroupDto create(@RequestBody CreateGroupRequest r);
    @PutMapping("/{id}") GroupDto update(@PathVariable Long id, @RequestBody UpdateGroupRequest r);
    @DeleteMapping("/{id}") void delete(@PathVariable Long id);
    @PostMapping("/{id}/members/{userId}") void addMember(@PathVariable Long id, @PathVariable Long userId);
    @DeleteMapping("/{id}/members/{userId}") void removeMember(@PathVariable Long id, @PathVariable Long userId);
}
```

### Acceptance Criteria
1. Admins CRUD groups and manage membership
2. Model access rules (feature #14) can target groups
3. Users can belong to multiple groups

---

## 30. Prevent Chat Deletion

### Overview
Admin toggle preventing non-admin users from deleting conversations (audit compliance).

### Migration
```sql
ALTER TABLE system_settings ADD COLUMN prevent_chat_deletion BOOLEAN DEFAULT FALSE;
```

### Implementation
In `ConversationController.delete()`: check setting; if enabled and user is non-admin, return 403.

### Acceptance Criteria
1. When enabled, non-admins cannot delete chats
2. Delete button hidden in UI
3. Admins always can delete

---

## 31. Webhook Notifications

### Overview
Fire webhooks on events (user signup, chat complete) to Slack/Discord/Teams.

### Migration
```sql
-- V16__webhooks.sql
CREATE TABLE webhooks (
    id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, url VARCHAR(2000) NOT NULL,
    event_type VARCHAR(50) NOT NULL, platform VARCHAR(20), enabled BOOLEAN DEFAULT TRUE,
    secret VARCHAR(255), created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### Service
```java
@Service @RequiredArgsConstructor
public class WebhookService {
    private final WebhookRepository repo;
    private final RestTemplate restTemplate;

    @Async public void fire(String eventType, Map<String, Object> payload) {
        repo.findByEventTypeAndEnabledTrue(eventType).forEach(hook -> {
            try {
                String body = formatForPlatform(hook.getPlatform(), payload);
                HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
                if (hook.getSecret() != null) h.set("X-Webhook-Signature", hmacSha256(hook.getSecret(), body));
                restTemplate.postForEntity(hook.getUrl(), new HttpEntity<>(body, h), String.class);
            } catch (Exception e) { log.warn("Webhook failed: {}", e.getMessage()); }
        });
    }
}
```

### Acceptance Criteria
1. Webhooks fire async on configured events
2. Platform-specific formatting (Slack/Discord/Teams JSON)
3. HMAC signature when secret configured
4. Failed deliveries logged, don't block operations

---

## 32. Active Users Monitor

### Service
```java
@Component
public class ActiveUserTracker {
    private final ConcurrentHashMap<Long, ActiveSession> sessions = new ConcurrentHashMap<>();
    private static final Duration WINDOW = Duration.ofMinutes(15);

    public void record(Long userId, String modelId) { sessions.put(userId, new ActiveSession(Instant.now(), modelId)); }
    public int getActiveCount() { prune(); return sessions.size(); }
    public List<ActiveSession> getActiveSessions() { prune(); return List.copyOf(sessions.values()); }
    private void prune() { sessions.entrySet().removeIf(e -> e.getValue().lastSeen().isBefore(Instant.now().minus(WINDOW))); }
}
public record ActiveSession(Instant lastSeen, String currentModel) {}
```

Use `HandlerInterceptor` to call `record()` on each authenticated request.

### API
```java
@GetMapping("/api/admin/active-users") @RequiresPermission("admin.users")
Map<String, Object> activeUsers();  // { count: N, users: [...] }
```

### Acceptance Criteria
1. Admin dashboard shows active user count and details
2. "Active" = request within last 15 minutes
3. Shows which model each user is using

---

## 33. PWA Support

### Overview
Progressive Web App for installability and mobile experience.

### Implementation

Create `src/main/resources/static/manifest.json`:
```json
{
  "name": "CF Llama Chat", "short_name": "CF Chat",
  "start_url": "/", "display": "standalone",
  "background_color": "#1a1a2e", "theme_color": "#4a90d9",
  "icons": [{ "src": "/icons/icon-192.png", "sizes": "192x192" }, { "src": "/icons/icon-512.png", "sizes": "512x512" }]
}
```

Create `src/main/resources/static/sw.js` — service worker for caching static assets.

Add to `<head>`: `<link rel="manifest" href="/manifest.json">`

### Acceptance Criteria
1. "Install" prompt appears on mobile browsers
2. App launches in standalone mode
3. Static assets cached for faster loads

---

## 34. Theme Modes

### Overview
User-selectable Light/Dark/OLED Dark themes alongside existing per-org admin theming.

### Implementation
Store theme preference in `users` table (`theme VARCHAR(20) DEFAULT 'dark'`). Use CSS custom properties:

```css
:root[data-theme="light"] { --bg: #fff; --text: #1a1a1a; --surface: #f5f5f5; }
:root[data-theme="dark"] { --bg: #1a1a2e; --text: #e0e0e0; --surface: #16213e; }
:root[data-theme="oled"] { --bg: #000; --text: #e0e0e0; --surface: #111; }
```

### API
```java
@PutMapping("/api/user/preferences/theme")
void setTheme(@AuthenticationPrincipal UserDetails u, @RequestBody Map<String, String> body);
```

### Acceptance Criteria
1. User selects theme in settings
2. Theme persists across sessions
3. Org-level theming overrides still work for branding

---

## 35. i18n / Multilingual

### Overview
Full internationalization using Spring's `MessageSource` with locale bundles.

### Implementation
Create `src/main/resources/messages.properties` (English default) plus `messages_es.properties`, `messages_fr.properties`, `messages_de.properties`, `messages_ja.properties`, `messages_zh.properties`, `messages_ar.properties`.

```java
@Configuration
public class LocaleConfig implements WebMvcConfigurer {
    @Bean LocaleResolver localeResolver() {
        SessionLocaleResolver r = new SessionLocaleResolver();
        r.setDefaultLocale(Locale.ENGLISH);
        return r;
    }
    @Bean LocaleChangeInterceptor localeInterceptor() {
        LocaleChangeInterceptor i = new LocaleChangeInterceptor();
        i.setParamName("lang");
        return i;
    }
    @Override public void addInterceptors(InterceptorRegistry r) { r.addInterceptor(localeInterceptor()); }
}
```

Use `th:text="#{chat.send.button}"` in Thymeleaf templates. Store user preference in DB.

### Acceptance Criteria
1. Language selector in settings
2. All UI strings externalized to message bundles
3. RTL support (feature #5) complements Arabic/Hebrew locales

---

## 36. Interactive Artifacts

### Overview
Render HTML/SVG/web content inline when LLM outputs code blocks with `html` or `svg` language tags.

### Frontend
```javascript
function renderArtifact(codeBlock, lang) {
    if (['html', 'svg'].includes(lang)) {
        const iframe = document.createElement('iframe');
        iframe.sandbox = 'allow-scripts';
        iframe.srcdoc = DOMPurify.sanitize(codeBlock, { WHOLE_DOCUMENT: true, ADD_TAGS: ['style'] });
        iframe.style.cssText = 'width:100%;border:1px solid var(--border);border-radius:8px;min-height:200px;';
        return iframe;
    }
    return null;
}
```

### Acceptance Criteria
1. HTML code blocks render as interactive iframes
2. SVG renders inline
3. Sandboxed — no access to parent page

---

## 37. Dynamic Prompt Variables

### Overview
Template variables in prompt presets resolved at send time.

### Service
```java
@Service
public class PromptVariableResolver {
    public String resolve(String template, User user) {
        return template
            .replace("{{CURRENT_DATE}}", LocalDate.now().toString())
            .replace("{{CURRENT_TIME}}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            .replace("{{CURRENT_DATETIME}}", LocalDateTime.now().toString())
            .replace("{{CURRENT_WEEKDAY}}", LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
            .replace("{{USER_NAME}}", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
    }
}
```

### Acceptance Criteria
1. `{{CURRENT_DATE}}` in presets resolves to today's date
2. `{{USER_NAME}}` resolves to display name
3. Unknown variables passed through unchanged

---

## 38. Memory System

### Overview
Persistent per-user memory. Models can add/search memories via function calling; memories auto-inject as context.

### Migration
```sql
-- V18__memory.sql
CREATE TABLE user_memories (
    id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL, category VARCHAR(50),
    embedding vector(512), created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_memories_user ON user_memories(user_id);
```

### Spring AI Tools
```java
@Component
public class AddMemoryTool implements Function<AddMemoryTool.Req, AddMemoryTool.Res> {
    public record Req(String content, String category) {}
    public record Res(String id, String status) {}
    // Save to user_memories, embed content
}

@Component
public class SearchMemoriesTool implements Function<SearchMemoriesTool.Req, SearchMemoriesTool.Res> {
    public record Req(String query, int maxResults) {}
    public record Res(List<MemoryEntry> memories) {}
    // pgvector similarity search on user_memories
}
```

### Auto-Injection
Before each chat, search memories relevant to the user message and prepend to system prompt.

### User Management API
```java
@RestController @RequestMapping("/api/memory")
public class MemoryController {
    @GetMapping List<MemoryDto> list();
    @PostMapping MemoryDto add(@RequestBody CreateMemoryRequest r);
    @PutMapping("/{id}") MemoryDto update(@PathVariable String id, @RequestBody UpdateMemoryRequest r);
    @DeleteMapping("/{id}") void delete(@PathVariable String id);
}
```

### Acceptance Criteria
1. Users manage memories in Settings > Personalization
2. Function-calling models can add/search memories
3. Relevant memories auto-injected into context
4. Memories persist across conversations

---

## 39. Prompt Library

### Overview
Shared prompt library extending the Prompt Presets (feature #16). Presets with `shared=true` form the org-level library. Admin-managed.

Already implemented via `PromptPreset.shared` field. Add admin UI for library management and community import (JSON format).

### Acceptance Criteria
1. Admin creates org-wide shared presets
2. Users browse library alongside personal presets
3. Import/export preset collections as JSON

---

## 40. Redis Session Management

### Dependencies
```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
<dependency><groupId>org.springframework.session</groupId><artifactId>spring-session-data-redis</artifactId></dependency>
```

### Configuration
```yaml
spring.session.store-type: ${SESSION_STORE:none}  # "redis" or "none"
spring.data.redis: { host: "${REDIS_HOST:localhost}", port: "${REDIS_PORT:6379}" }
```

```java
@Configuration @ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class RedisSessionConfig {}
```

### Acceptance Criteria
1. Redis sessions enable multi-instance session sharing
2. Falls back to in-memory when Redis unavailable
3. Zero-config for single instance

---

## 41. Cloud Storage Backends

### Overview
Store uploaded documents and generated files in S3/GCS/Azure Blob instead of local filesystem.

### Configuration
```yaml
storage:
  provider: ${STORAGE_PROVIDER:local}  # local, s3, gcs, azure
  local.path: ${STORAGE_PATH:/tmp/cf-llama-uploads}
  s3: { bucket: ${S3_BUCKET:}, region: ${AWS_REGION:us-east-1} }
  gcs: { bucket: ${GCS_BUCKET:}, project: ${GCP_PROJECT:} }
  azure: { container: ${AZURE_CONTAINER:}, connection-string: ${AZURE_STORAGE_CONNECTION:} }
```

### Service Interface
```java
public interface StorageService {
    String store(String key, byte[] data, String contentType);
    byte[] retrieve(String key);
    void delete(String key);
}
// Implementations: LocalStorageService, S3StorageService, GcsStorageService, AzureBlobStorageService
```

### Acceptance Criteria
1. Local storage default
2. S3/GCS/Azure work with appropriate credentials
3. Enables stateless CF instances (no local filesystem dependency)

---

## 42. Horizontal Scaling Coordination

### Overview
Multi-node coordination using Redis pub/sub for cache invalidation and event broadcasting.

### Implementation
When Redis is available, use Spring's `RedisMessageListenerContainer` for:
- Model list cache invalidation
- System settings changes
- Active user tracking across instances
- SSE emitter handoff (redirect to correct instance or use Redis-backed SSE)

```java
@Service @ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class ClusterEventService {
    private final RedisTemplate<String, String> redis;
    public void broadcast(String channel, String message) { redis.convertAndSend(channel, message); }
    // Listen for invalidation events and clear local caches
}
```

### Acceptance Criteria
1. Settings changes propagate across instances
2. Model list updates reflected cluster-wide
3. Graceful degradation to single-node when Redis unavailable

---

## 43. Toxic Message Filtering

### Overview
Filter toxic/harmful content using a pluggable content moderation service.

### Configuration
```yaml
moderation:
  enabled: ${MODERATION_ENABLED:false}
  provider: ${MODERATION_PROVIDER:openai}  # openai, perspective, local
  action: ${MODERATION_ACTION:warn}  # warn, block
  openai.api-key: ${OPENAI_API_KEY:}
```

### Service
```java
@Service
public class ContentModerationService {
    public ModerationResult check(String text) {
        // Call OpenAI Moderation API or Google Perspective API
        // Return: flagged (bool), categories, scores
    }
}
```

### Integration
Check user messages before sending to LLM. If flagged: `warn` shows a warning but allows send; `block` prevents submission.

### Acceptance Criteria
1. Flagged messages show warning or are blocked
2. Provider configurable
3. Disabled by default

---

## 44. Prompt Injection Guard

### Overview
Detect and block prompt injection attempts in user messages.

### Service
```java
@Service
public class PromptInjectionDetector {
    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("ignore (all )?(previous|above|prior) instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("new instruction:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("system:\\s*", Pattern.CASE_INSENSITIVE)
    );

    public boolean isInjectionAttempt(String message) {
        return PATTERNS.stream().anyMatch(p -> p.matcher(message).find());
    }
}
```

Optionally use LLM-based detection by sending message to a classification prompt.

### Acceptance Criteria
1. Known injection patterns detected
2. Configurable action (warn/block)
3. Low false-positive rate

---

## 45. Usage Monitoring

### Overview
Detailed per-user, per-model analytics for token usage, request counts, and costs.

### Migration
```sql
-- V19__usage_tracking.sql
CREATE TABLE usage_records (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
    conversation_id VARCHAR(36), model_id VARCHAR(255) NOT NULL,
    prompt_tokens INT, completion_tokens INT, total_tokens INT,
    estimated_cost DECIMAL(10,6), created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_usage_user_date ON usage_records(user_id, created_at);
CREATE INDEX idx_usage_model_date ON usage_records(model_id, created_at);
```

### Service
Record token usage after each chat completion. Aggregate for dashboard.

### API
```java
@GetMapping("/api/admin/usage") @RequiresPermission("admin.settings")
UsageDashboardDto getUsage(@RequestParam(required = false) String modelId,
    @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to);

@GetMapping("/api/user/usage")
UserUsageDto getMyUsage(@AuthenticationPrincipal UserDetails u);
```

### Acceptance Criteria
1. Every chat completion records token usage
2. Admin dashboard shows usage by model, user, time period
3. Users can view their own usage

# PHASE 3 — LOW PRIORITY (Polish)

Each feature below is summarized concisely. Agents can expand as needed.

---

## 46. Chat Cloning

Clone a conversation as a snapshot. `POST /api/conversations/{id}/clone` creates a new conversation with copies of all messages. Title suffixed with "(copy)".

---

## 47. Chat Folders

### Migration
```sql
-- V20__chat_folders.sql
CREATE TABLE chat_folders (
    id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL, parent_folder_id VARCHAR(36) REFERENCES chat_folders(id),
    sort_order INT DEFAULT 0, created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### API
```java
@RestController @RequestMapping("/api/folders")
public class ChatFolderController {
    @GetMapping List<FolderDto> list();
    @PostMapping FolderDto create(@RequestBody CreateFolderRequest r);
    @PutMapping("/{id}") FolderDto update(@PathVariable String id, @RequestBody UpdateFolderRequest r);
    @DeleteMapping("/{id}") void delete(@PathVariable String id);
    @PutMapping("/{id}/conversations") void moveConversations(@PathVariable String id, @RequestBody List<String> convIds);
}
```

Move conversations to folders via `PATCH /api/conversations/{id}` with `folderId` field.

---

## 48. Pinned Chats

Already scaffolded via `Conversation.pinned` boolean (feature #1). `PATCH /api/conversations/{id}` with `pinned: true`. Pinned chats display in a "Pinned" section above the sidebar list.

---

## 49. Tagging System

### Migration
```sql
-- V21__tags.sql
CREATE TABLE conversation_tags (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(50) NOT NULL, color VARCHAR(7), UNIQUE(user_id, name));
CREATE TABLE conversation_tag_links (conversation_id VARCHAR(36) REFERENCES conversations(id) ON DELETE CASCADE,
    tag_id BIGINT REFERENCES conversation_tags(id) ON DELETE CASCADE, PRIMARY KEY(conversation_id, tag_id));
```

### Auto-Tagging
After first 2 messages, async LLM call: "Suggest 1-3 single-word tags for this conversation." Parse response, create/link tags.

### API
```java
@RestController @RequestMapping("/api/tags")
public class TagController {
    @GetMapping List<TagDto> list();
    @PostMapping TagDto create(@RequestBody CreateTagRequest r);
    @DeleteMapping("/{id}") void delete(@PathVariable Long id);
    @PostMapping("/{tagId}/conversations/{convId}") void tag(...);
    @DeleteMapping("/{tagId}/conversations/{convId}") void untag(...);
}
```

---

## 50. Temporary Chats

Ephemeral mode: conversations are not persisted to database. Set via a toggle in the UI. Backend simply skips all persistence calls in `ChatService` when `temporary=true`.

---

## 51. Favorite Responses

### Migration
```sql
ALTER TABLE chat_messages ADD COLUMN favorited BOOLEAN DEFAULT FALSE;
```

Toggle via `PATCH /api/conversations/{convId}/messages/{msgId}` with `favorited: true`. User can filter favorites via `GET /api/messages/favorites`.

---

## 52. Full Doc vs Snippet Toggle

### Configuration
```yaml
rag.retrieval-mode: ${RAG_MODE:snippet}  # "snippet" (chunk-based) or "full" (whole document)
```

When `full`, instead of returning matched chunks, return the entire source document content. Per-chat toggle in UI.

---

## 53. Large Text Paste Detection

### Frontend Only
```javascript
chatInput.addEventListener('paste', e => {
    const text = e.clipboardData.getData('text');
    if (text.length > 5000) {
        e.preventDefault();
        if (confirm(`Large text (${text.length} chars). Upload as document for RAG?`)) {
            const file = new File([text], 'pasted-content.txt', { type: 'text/plain' });
            uploadAsDocument(file);
        } else chatInput.value += text;
    }
});
```

---

## 54. Channels (Collaborative Rooms)

### Migration
```sql
CREATE TABLE channels (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL,
    organization_id BIGINT REFERENCES organizations(id), model_id VARCHAR(255),
    created_by BIGINT REFERENCES users(id), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE channel_messages (id VARCHAR(36) PRIMARY KEY, channel_id VARCHAR(36) REFERENCES channels(id),
    user_id BIGINT REFERENCES users(id), content TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT NOW());
```

Discord-style rooms where multiple users interact with the same AI. Messages broadcast via SSE to all connected users.

---

## 55. Notes

### Migration
```sql
CREATE TABLE user_notes (id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255), content TEXT NOT NULL, conversation_id VARCHAR(36) REFERENCES conversations(id),
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
```

In-app notepad. Users can save text from conversations or write freeform notes. Optional link to conversation.

---

## 56. Custom Backgrounds

Store background preference in `users.preferences` JSON. Apply via CSS:
```css
.chat-container { background-image: var(--user-bg); background-size: cover; }
```

Provide 6–8 preset backgrounds + custom URL option.

---

## 57. Keyboard Shortcuts

### Frontend
```javascript
document.addEventListener('keydown', e => {
    if (e.ctrlKey || e.metaKey) {
        switch (e.key) {
            case 'n': e.preventDefault(); createNewChat(); break;        // New chat
            case 'k': e.preventDefault(); openSearch(); break;           // Search
            case '/': e.preventDefault(); focusChatInput(); break;       // Focus input
            case 'Shift': e.preventDefault(); toggleSidebar(); break;    // Toggle sidebar
        }
    }
    if (e.key === 'Escape') closePanels();
});
```

Show shortcuts modal via `Ctrl+?` or `?` button.

---

## 58. Settings Search

### Frontend
Filter settings fields by text search. Each settings section has `data-search-keywords` attributes. Search input at top filters visible sections:
```javascript
settingsSearch.addEventListener('input', e => {
    document.querySelectorAll('.settings-section').forEach(s => {
        s.style.display = s.dataset.searchKeywords.toLowerCase().includes(e.target.value.toLowerCase()) ? '' : 'none';
    });
});
```

---

## 59. Haptic Feedback

### Frontend
```javascript
function haptic(style = 'light') {
    if (navigator.vibrate) navigator.vibrate(style === 'heavy' ? 50 : 10);
}
// Call on button presses, message send, feedback submission
```

---

## 60. Notification Banners

### Migration
```sql
CREATE TABLE notification_banners (id BIGSERIAL PRIMARY KEY, content TEXT NOT NULL,
    banner_type VARCHAR(20) NOT NULL, dismissible BOOLEAN DEFAULT TRUE,
    active BOOLEAN DEFAULT TRUE, created_at TIMESTAMPTZ DEFAULT NOW(), expires_at TIMESTAMPTZ);
```

### API
```java
@GetMapping("/api/banners") List<BannerDto> active();
@PostMapping("/api/admin/banners") @RequiresPermission("admin.settings") BannerDto create(@RequestBody CreateBannerRequest r);
@DeleteMapping("/api/admin/banners/{id}") @RequiresPermission("admin.settings") void delete(@PathVariable Long id);
```

Banner types: `info`, `warning`, `error`, `success`. Markdown content rendered.

---

## 61. Live Translation

### Overview
Real-time translation of messages using LibreTranslate (self-hosted) or external APIs.

### Configuration
```yaml
translation:
  enabled: ${TRANSLATION_ENABLED:false}
  provider: ${TRANSLATION_PROVIDER:libretranslate}
  libretranslate.url: ${LIBRETRANSLATE_URL:http://localhost:5000}
```

### Service
```java
@Service
public class TranslationService {
    public String translate(String text, String sourceLang, String targetLang) {
        // POST to LibreTranslate API: { q: text, source: src, target: tgt }
    }
}
```

### API
```java
@PostMapping("/api/translate")
Map<String, String> translate(@RequestBody TranslateRequest r);
// { text, source, target } → { translatedText }
```

UI: Translate button on messages opens language selector, translates in-place.

---

## 62. Config Import/Export

### API
```java
@RestController @RequestMapping("/api/admin/config") @RequiresPermission("admin.settings")
public class ConfigController {
    @GetMapping("/export") ResponseEntity<byte[]> export();
    // Export: system settings, roles, permissions, webhooks, model access rules, banners, presets as JSON

    @PostMapping("/import") Map<String, Object> importConfig(@RequestParam("file") MultipartFile f);
    // Parse JSON, apply settings, report what was imported
}
```

---

## 63. Swagger/OpenAPI Documentation

### Dependencies
```xml
<dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.7.0</version></dependency>
```

### Configuration
```yaml
springdoc:
  api-docs.path: /api-docs
  swagger-ui: { path: /swagger-ui, tryItOutEnabled: true }
```

Annotate controllers with `@Tag`, `@Operation`, `@ApiResponse`. Accessible at `/swagger-ui`.

---

## 64. Quick Actions

### Frontend
On text selection in chat messages, show floating toolbar:
```javascript
document.addEventListener('selectionchange', () => {
    const sel = window.getSelection();
    if (sel.rangeCount && sel.toString().trim().length > 0) {
        const range = sel.getRangeAt(0);
        const rect = range.getBoundingClientRect();
        showQuickActions(rect, sel.toString());
    } else hideQuickActions();
});

function showQuickActions(rect, text) {
    toolbar.style.top = (rect.top - 40) + 'px';
    toolbar.style.left = rect.left + 'px';
    toolbar.innerHTML = `
        <button onclick="askAbout('${encodeText(text)}')">Ask</button>
        <button onclick="explainText('${encodeText(text)}')">Explain</button>
        <button onclick="navigator.clipboard.writeText('${encodeText(text)}')">Copy</button>`;
    toolbar.style.display = 'flex';
}
```

---

# CROSS-CUTTING CONCERNS

---

## Flyway Migration Order

| Migration | Feature |
|-----------|---------|
| V2 | Chat persistence (conversations + messages) |
| V3 | BM25 full-text search |
| V4 | Auth providers (LDAP/SCIM columns) |
| V5 | Permissions + roles |
| V6 | Model access rules |
| V7 | Prompt presets |
| V8 | Chat sharing |
| V9 | Document library |
| V10 | User groups |
| V11 | Webhooks |
| V12 | Memory system |
| V13 | Usage tracking |
| V14 | Chat folders |
| V15 | Tags |

---

## Security Requirements

All new endpoints must:
1. Require authentication via Spring Security
2. Use `@RequiresPermission` for authorization
3. Validate resource ownership (users access only their own data)
4. Sanitize user input (DOMPurify on frontend, validate on backend)
5. Rate limit where applicable (feature #15)

---

## Testing Strategy

| Layer | Framework | Coverage Target |
|-------|-----------|----------------|
| Unit tests | JUnit 5 + Mockito | Service layer logic |
| Integration | `@DataJpaTest` + Testcontainers PostgreSQL | Repository queries, Flyway migrations |
| Controller | `@WebMvcTest` + MockMvc | API contracts, auth, validation |
| End-to-end | Testcontainers full stack | Critical user flows |

---

## Performance Targets

| Operation | Target |
|-----------|--------|
| Conversation list (sidebar) | < 100ms |
| Hybrid RAG search | < 500ms |
| Message persistence | < 50ms |
| Prompt preset search | < 100ms |
| Chat export (JSON, single) | < 200ms |
| Memory search | < 200ms |
| Web page fetch | < 10s (timeout) |

---

## Dependency Summary

### Required (add to pom.xml)
```xml
<!-- Already present: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-ai-* -->

<!-- New required -->
<dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId><version>1.18.3</version></dependency>
<dependency><groupId>io.github.bucket4j</groupId><artifactId>bucket4j-core</artifactId><version>8.10.1</version></dependency>
<dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.7.0</version></dependency>
```

### Conditional (only when feature enabled)
```xml
<dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-ldap</artifactId></dependency>
<dependency><groupId>org.springframework.session</groupId><artifactId>spring-session-data-redis</artifactId></dependency>
<dependency><groupId>io.opentelemetry.instrumentation</groupId><artifactId>opentelemetry-spring-boot-starter</artifactId></dependency>
<dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-otlp</artifactId></dependency>
<dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-qdrant-store</artifactId><optional>true</optional></dependency>
```

### Frontend (vendor into static/vendor/)
| Library | Version | Purpose |
|---------|---------|---------|
| marked.js | 15.x | Markdown rendering |
| DOMPurify | 3.x | XSS sanitization |
| KaTeX | 0.16.x | LaTeX math |
| Prism.js | 1.29.x | Code highlighting |


---

## Features Where CF Llama Chat Leads

Preserve these differentiators throughout implementation:

| Feature | Description |
|---------|-------------|
| **Tanzu GenAI Locator** | Automatic model discovery via VCAP_SERVICES with capability-based routing |
| **cf push Deploy** | Single-command Cloud Foundry deployment with zero-config service binding |
| **Multi-Tenancy** | Slug-based routing, per-org theming/branding/logos/CSS |
| **Spring Boot / Java** | Enterprise-familiar stack with Spring AI framework |
| **Skills System** | Tool + Prompt combinations as reusable packages |
| **External Binding Admin** | Hot-reload UI for managing external API connections |
| **Invitation Codes** | Simple but effective controlled sign-up |
| **Built-in Metrics** | Chat and embedding usage dashboard out of the box |
