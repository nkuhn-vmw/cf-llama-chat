package com.example.cfchat.config;

import com.example.cfchat.service.FetchUrlTool;
import com.example.cfchat.service.WebSearchTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class ToolConfig {

    @Bean
    @Description("Search the web for current information on any topic")
    @ConditionalOnProperty(name = "search.enabled", havingValue = "true")
    public Function<WebSearchTool.Request, WebSearchTool.Response> webSearch(WebSearchTool tool) {
        return tool;
    }

    @Bean
    @Description("Fetch the full text content of a web page given its URL")
    public Function<FetchUrlTool.Request, FetchUrlTool.Response> fetchUrl(FetchUrlTool tool) {
        return tool;
    }
}
