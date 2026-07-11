package com.example.chat.internal.ollama;

import com.example.config.OllamaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class OllamaClient implements AiClientInter {

    private final WebClient webClient;
    private final String chatModelName;
    private final String embeddingModelName;

    public OllamaClient(WebClient ollamaWebClient,
                        OllamaProperties ollamaProperties) {
        this.webClient = ollamaWebClient;
        this.chatModelName = ollamaProperties.getChat().getModel();
        this.embeddingModelName = ollamaProperties.getEmbedding().getModel();
    }

    public Mono<String> chat(String message) {
        Map<String, Object> payload = Map.of(
                "model", chatModelName,
                "messages", new Object[]{Map.of("role", "user", "content", message)}
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "chat").build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> embed(String input) {
        Map<String, Object> payload = Map.of(
                "model", embeddingModelName,
                "input", input
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "embed").build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> listModels() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "models").build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);
    }
}

