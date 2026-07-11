package com.example.chat.internal;

import reactor.core.publisher.Mono;

/**
 * Interface for Ollama client operations.
 * Implementations should provide methods for chat and embedding calls.
 */
public interface AiClientInter {

    /**
     * Send a chat message to the configured chat model and return the raw response as a Mono.
     *
     * @param message user message
     * @return Mono with raw response body (JSON) from the chat endpoint
     */
    Mono<String> chat(String message);

    /**
     * Request embeddings for the provided input using the configured embedding model.
     *
     * @param input text to embed
     * @return Mono with raw response body (JSON) from the embeddings endpoint
     */
    Mono<String> embed(String input);

    /**
     * List models available in the Ollama server.
     * Useful for diagnosing missing models (returns raw JSON).
     */
    Mono<String> listModels();
}

