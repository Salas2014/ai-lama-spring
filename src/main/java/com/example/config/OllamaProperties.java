package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Ollama (mapped from application.yaml)
 * Example YAML:
 * spring:
 *   ai:
 *     ollama:
 *       base-url: http://localhost:11434
 *       chat:
 *         model: llama3.2:1b
 *       embedding:
 *         model: nomic-embed-text
 */
@ConfigurationProperties(prefix = "spring.ai.ollama")
public class OllamaProperties {

    private String baseUrl;

    private Chat chat = new Chat();

    private Embedding embedding = new Embedding();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public static class Chat {
        private String model;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Embedding {
        private String model;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}

