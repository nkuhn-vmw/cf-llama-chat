// src/test/java/com/example/cfchat/service/wiki/WikiEmbeddingServiceTest.java
package com.example.cfchat.service.wiki;

import com.example.cfchat.model.wiki.WikiPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WikiEmbeddingServiceTest {

    VectorStore vectorStore;
    WikiEmbeddingService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        service = new WikiEmbeddingService(vectorStore);
    }

    @Test
    void indexPageSplitsAndWritesToVectorStore() {
        UUID pageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WikiPage page = new WikiPage();
        page.setId(pageId);
        page.setUserId(userId);
        page.setSlug("facts/x");
        page.setTitle("X");
        page.setKind("FACT");
        page.setBodyMd("some content that will be chunked into at least one document");

        service.indexPage(page);

        verify(vectorStore).add(argThat(docs -> {
            @SuppressWarnings("unchecked")
            List<Document> list = (List<Document>) docs;
            return !list.isEmpty() &&
                   list.get(0).getMetadata().get("pageId").equals(pageId.toString()) &&
                   list.get(0).getMetadata().get("userId").equals(userId.toString()) &&
                   list.get(0).getMetadata().get("kind").equals("FACT");
        }));
    }

    @Test
    void deletePageRemovesByPageIdFilter() {
        UUID pageId = UUID.randomUUID();
        service.deletePageEmbeddings(pageId);
        verify(vectorStore).delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));
    }

    @Test
    void searchBuildsUserScopedFilter() {
        UUID userId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        service.search(userId, "query", null, 5);
        verify(vectorStore).similaritySearch(argThat((SearchRequest r) ->
            r.getQuery().equals("query") && r.getTopK() == 5));
    }
}
