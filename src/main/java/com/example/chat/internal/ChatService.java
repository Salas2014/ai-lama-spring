package com.example.chat.internal;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public Flux<String> asyncChat(String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    // RAG: ищет контекст в Qdrant, затем отвечает с его учётом
    public String ragChat(String userQuestion) {
        List<Document> context = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuestion)
                        .topK(3)
                        .similarityThreshold(0.7) // только если схожесть >= 70%, иначе не возвращать
                        .build()
        );

        if (context.isEmpty()) {
            return chat(userQuestion);
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

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
