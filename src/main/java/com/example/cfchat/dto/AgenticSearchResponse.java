package com.example.cfchat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenticSearchResponse {

    /**
     * The final synthesized answer incorporating all search results.
     */
    private String answer;

    /**
     * HTML-rendered version of the answer.
     */
    private String htmlAnswer;

    /**
     * The original query that was submitted.
     */
    private String originalQuery;

    /**
     * Details of each search iteration performed.
     */
    private List<SearchIteration> iterations;

    /**
     * Total number of unique sources found across all iterations.
     */
    private int totalSourcesFound;

    /**
     * Total processing time in milliseconds.
     */
    private long totalTimeMs;

    /**
     * Error message if the search failed.
     */
    private String error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchIteration {

        /**
         * The iteration number (1-based).
         */
        private int iteration;

        /**
         * The sub-queries generated for this iteration.
         */
        private List<String> subQueries;

        /**
         * The sources found in this iteration.
         */
        private List<SearchSource> sources;

        /**
         * Intermediate summary produced after this iteration.
         */
        private String intermediateSummary;

        /**
         * Time taken for this iteration in milliseconds.
         */
        private long iterationTimeMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchSource {

        /**
         * The sub-query that found this source.
         */
        private String query;

        /**
         * Source type: "document" or "web".
         */
        private String sourceType;

        /**
         * Document filename or web page title.
         */
        private String title;

        /**
         * URL for web sources, null for document sources.
         */
        private String url;

        /**
         * The relevant text snippet from this source.
         */
        private String snippet;

        /**
         * Relevance score (0.0 to 1.0) for vector store results.
         */
        private Double relevance;
    }
}
