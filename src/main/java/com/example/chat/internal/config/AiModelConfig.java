package com.example.chat.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({OllamaProperties.class, GeminiProperties.class})
public class AiModelConfig {

    @Bean(name = "ollamaWebClient")
    public WebClient ollamaWebClient(OllamaProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean(name = "geminiWebClient")
    public WebClient geminiWebClient(GeminiProperties properties) {
        return WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean(name = "geminiEmbeddingWebClient")
    public WebClient geminiEmbeddingWebClient() {
        return WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
