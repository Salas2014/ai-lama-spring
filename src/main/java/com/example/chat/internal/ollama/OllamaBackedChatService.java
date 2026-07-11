package com.example.chat.internal.ollama;

import com.example.chat.internal.AiClientInter;
import com.example.chat.internal.IChatService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatService implementation backed by OllamaClient instead of Spring AI ChatClient.
 * Implements the same interface and logic as original ChatService, but uses AiClientInter (OllamaClient).
 */
@Service
public class OllamaBackedChatService implements IChatService {

    private final AiClientInter ollamaClient;
    private final VectorStore vectorStore;

    public OllamaBackedChatService(AiClientInter ollamaClient, VectorStore vectorStore) {
        this.ollamaClient = ollamaClient;
        this.vectorStore = vectorStore;
    }

    @Override
    public String chat(String message) {
        return ollamaClient.chat(message).block();
    }

    @Override
    public Flux<String> asyncChat(String message) {
        return ollamaClient.chat(message)
                .flatMapMany(Flux::just);
    }

    @Override
    public Flux<String> ragChat(String userQuestion) {
        List<Document> context = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuestion)
                        .topK(3)
                        .similarityThreshold(0.7)
                        .build()
        );

        if (context.isEmpty()) {
            return Flux.error(new RuntimeException(
                    "Контекст не найден в базе знаний. Пожалуйста, уточните вопрос или добавьте контекст."
            ));
        }

        String contextText = context.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String prompt = """
                Используй только следующий контекст для ответа.
                Если ответа нет в контексте — так и скажи.
                
                Контекст:
                %s
                
                Вопрос: %s
                """.formatted(contextText, userQuestion);

        return ollamaClient.chat(prompt)
                .flatMapMany(Flux::just);
    }
}

