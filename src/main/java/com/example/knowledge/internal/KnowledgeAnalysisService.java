package com.example.knowledge.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Анализирует файлы целиком через Gemini API (не через RAG/VectorStore).
 * Отдельный сервис чтобы не нарушать границы Spring Modulith модулей.
 */
@Service
public class KnowledgeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAnalysisService.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeAnalysisService(
            @Value("${spring.ai.google.genai.api-key}") String apiKey,
            @Value("${spring.ai.google.genai.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Анализирует classpath-ресурс (из resources/knowledge/).
     */
    public String analyzeTopics(Resource resource) throws IOException {
        String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return callGemini(buildTopicsPrompt(text));
    }

    /**
     * Анализирует загруженный multipart файл.
     */
    public String analyzeTopics(MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return callGemini(buildTopicsPrompt(text));
    }

    private String buildTopicsPrompt(String text) {
        return """
                Проанализируй следующий учебный текст и составь структурированный отчёт:
                
                1. **Темы которые разобраны** — перечисли все основные темы
                2. **Детали по каждой теме** — что конкретно объясняется внутри каждой темы
                3. **Чего не хватает** — важные связанные концепции которые НЕ упомянуты в тексте
                
                Отвечай на том же языке что и текст.
                
                Текст для анализа:
                ---
                %s
                ---
                """.formatted(text);
    }

    private String callGemini(String prompt) {
        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        try {
            String json = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + model + ":generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(json);
            return root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText(json);
        } catch (Exception e) {
            log.error("Gemini analysis error", e);
            throw new RuntimeException("Ошибка при анализе через Gemini: " + e.getMessage(), e);
        }
    }
}

