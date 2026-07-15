package com.example.chat.internal.gemini;

import com.example.chat.internal.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI EmbeddingModel backed by Google Gemini embedding API.
 * Model: text-embedding-004 → 768 dimensions (compatible with nomic-embed-text).
 */
@Component
public class GeminiEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingModel.class);

    // gemini-embedding-001 возвращает 3072 измерения
    private static final int DIMENSIONS = 3072;

    private final WebClient webClient;
    private final String apiKey;
    private final String embeddingModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiEmbeddingModel(@Qualifier("geminiEmbeddingWebClient") WebClient geminiWebClient,
                                GeminiProperties properties) {
        this.webClient = geminiWebClient;
        this.apiKey = properties.getApiKey();
        this.embeddingModel = properties.getEmbeddingModel();
    }

    /**
     * Возвращает размерность вектора без вызова API при старте приложения.
     * text-embedding-004 всегда возвращает 768 измерений.
     */
    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            float[] vector = embedSingle(texts.get(i));
            embeddings.add(new Embedding(vector, i));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedSingle(document.getText());
    }

    private float[] embedSingle(String text) {
        // "model" убран из тела — он уже есть в URL
        Map<String, Object> payload = Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        try {
            String json = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + embeddingModel + ":embedContent")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).map(body -> {
                                log.error("Gemini embedding error {}: {}", resp.statusCode(), body);
                                return new RuntimeException("Gemini embedding API error " + resp.statusCode() + ": " + body);
                            }))
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(json);
            JsonNode values = root.path("embedding").path("values");

            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = (float) values.get(i).asDouble();
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get embedding from Gemini API", e);
        }
    }
}
