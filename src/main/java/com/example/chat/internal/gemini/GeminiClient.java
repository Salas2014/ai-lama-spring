package com.example.chat.internal.gemini;


import com.example.chat.internal.config.GeminiProperties;
import com.example.chat.internal.AiClientInter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.util.List;
import java.util.Map;

@Component
@Qualifier("geminiClient")
public class GeminiClient implements AiClientInter {

    private final WebClient webClient;
    private final String model;
    private final String apiKey;

    public GeminiClient(WebClient geminiWebClient,
                        GeminiProperties properties) {
        this.webClient = geminiWebClient;
        this.model = properties.getModel();
        this.apiKey = properties.getApiKey();
    }

    @Override
    public Mono<String> chat(String message) {
        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", message)))
                )
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/" + model + ":generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RuntimeException("Gemini error " + resp.statusCode() + ": " + body))))
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(5, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(30))
                        .filter(throwable -> throwable instanceof WebClientResponseException.TooManyRequests)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()));
    }

    @Override
    public Mono<String> embed(String input) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "content", Map.of("parts", List.of(Map.of("text", input)))
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/" + model + ":embedContent")
                        .queryParam("key", apiKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(5, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(30))
                        .filter(throwable -> throwable instanceof WebClientResponseException.TooManyRequests)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()));
    }

    @Override
    public Mono<String> listModels() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/models")
                        .queryParam("key", apiKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(5, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(30))
                        .filter(throwable -> throwable instanceof WebClientResponseException.TooManyRequests)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()));
    }
}

