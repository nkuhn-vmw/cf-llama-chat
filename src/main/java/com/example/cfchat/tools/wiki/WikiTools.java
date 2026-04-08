// src/main/java/com/example/cfchat/tools/wiki/WikiTools.java
package com.example.cfchat.tools.wiki;

import com.example.cfchat.dto.wiki.WikiIndexEntry;
import com.example.cfchat.dto.wiki.WikiPageView;
import com.example.cfchat.dto.wiki.WikiSearchHit;
import com.example.cfchat.model.wiki.WikiKind;
import com.example.cfchat.service.wiki.WikiScope;
import com.example.cfchat.service.wiki.WikiService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WikiTools {

    private final WikiService service;

    public WikiTools(WikiService service) {
        this.service = service;
    }

    @Tool(description = """
        Search the user's wiki for pages relevant to a query. Use this BEFORE
        answering questions about the user, their projects, preferences, or
        prior decisions. Returns up to k matching pages with title, slug, and
        a short snippet. Filter by kind when you know the category:
        ENTITY | CONCEPT | FACT | PREFERENCE | DECISION | EVENT.
        """)
    public List<WikiSearchHit> wikiSearch(
            @ToolParam(description = "natural-language query") String query,
            @ToolParam(required = false, description = "restrict to ENTITY|CONCEPT|FACT|PREFERENCE|DECISION|EVENT") String kind,
            @ToolParam(required = false, description = "max results, default 6, cap 20") Integer k,
            ToolContext toolContext) {
        int effectiveK = Math.min(20, k == null ? 6 : Math.max(1, k));
        return service.search(WikiScope.from(toolContext), query, kind, effectiveK);
    }

    @Tool(description = """
        Read a wiki page by slug. Use after wiki_search to get full page content.
        Updates the page's last-read timestamp.
        """)
    public WikiPageView wikiRead(
            @ToolParam(description = "page slug, e.g. 'personal/work-style'") String slug,
            ToolContext toolContext) {
        return service.read(WikiScope.from(toolContext), slug);
    }

    @Tool(description = """
        Create or update a wiki page with durable information the user just shared.
        Choose the most specific kind:
        - ENTITY: a person, project, product, or thing
        - CONCEPT: an idea, definition, or reference topic
        - FACT: something objectively true about the user or their world
        - PREFERENCE: something the user likes, dislikes, or defaults to
        - DECISION: a choice the user committed to (include rationale)
        - EVENT: something that happened at a specific time
        If none fit, do NOT call this tool - the info is not durable enough.
        """)
    public WikiPageView wikiWrite(
            @ToolParam(description = "slug, e.g. 'personal/work-style'") String slug,
            @ToolParam(description = "human-readable title") String title,
            @ToolParam(description = "one of ENTITY|CONCEPT|FACT|PREFERENCE|DECISION|EVENT") String kind,
            @ToolParam(description = "full markdown body") String bodyMd,
            ToolContext toolContext) {
        WikiKind parsed = WikiKind.parse(kind);
        if (!WikiKind.agentVisible().contains(parsed)) {
            throw new IllegalArgumentException("Kind " + kind + " is not agent-writable");
        }
        return service.upsert(WikiScope.from(toolContext), slug, title, parsed.name(),
                              bodyMd, "AGENT_WRITE");
    }

    @Tool(description = """
        Link two wiki pages with a semantic relation: mentions | see_also |
        supersedes | refines | contradicts.
        """)
    public void wikiLink(
            @ToolParam(description = "source page slug") String fromSlug,
            @ToolParam(description = "target page slug") String toSlug,
            @ToolParam(description = "mentions|see_also|supersedes|refines|contradicts") String relation,
            ToolContext toolContext) {
        service.link(WikiScope.from(toolContext), fromSlug, toSlug, relation);
    }

    @Tool(description = """
        Mark a wiki page as superseded or incorrect. Does not delete history -
        the page is hidden from search but remains in the revision log and
        can be restored via undo.
        """)
    public void wikiInvalidate(
            @ToolParam(description = "page slug to invalidate") String slug,
            @ToolParam(description = "short reason") String reason,
            ToolContext toolContext) {
        service.invalidate(WikiScope.from(toolContext), slug, reason);
    }

    @Tool(description = """
        Return a compact catalog of wiki pages (slug, title, kind). Cheap
        orientation - prefer over wiki_search when you just need to see
        what exists. Optionally filter by kind.
        """)
    public List<WikiIndexEntry> wikiIndex(
            @ToolParam(required = false, description = "restrict to a single kind") String kind,
            ToolContext toolContext) {
        return service.indexFor(WikiScope.from(toolContext), kind);
    }
}
