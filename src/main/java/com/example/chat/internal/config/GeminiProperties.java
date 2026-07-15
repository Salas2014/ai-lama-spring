package com.example.chat.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.google.genai")
public class GeminiProperties {

    private String apiKey;
    private String model;
    private final static String DEFAULT_MODEL_NAME = "gemini-embedding-001";
    private Embedding embedding = new Embedding();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public String getEmbeddingModel() {
        return embedding.getModel() != null ? embedding.getModel() : DEFAULT_MODEL_NAME;
    }

    public static class Embedding {
        private String model = DEFAULT_MODEL_NAME;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
