package com.example.knowledge.internal;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Вариант 1: загрузка списка строк через JSON body
    public void ingest(List<String> texts) {
        List<Document> documents = texts.stream()
                .map(Document::new)
                .toList();
        vectorStore.add(documents);
    }

    // Вариант 2: загрузка .txt файла через multipart upload
    public void ingestFile(MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        String filename = Objects.requireNonNull(file.getOriginalFilename());
        List<Document> chunks = splitAndCreateDocuments(text, filename);
        vectorStore.add(chunks);
    }

    // Вариант 3: загрузка .txt из classpath (src/main/resources/)
    public void ingestResource(Resource resource) throws IOException {
        String filename = Objects.requireNonNull(resource.getFilename());
        String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Разбиваем текст на чанки
        List<Document> chunkedDocuments = splitAndCreateDocuments(text, filename);
        vectorStore.add(chunkedDocuments);
    }

    // Загрузка отдельного чанка (для KnowledgeLoader)
    public void ingestChunk(List<Document> documents) {
        vectorStore.add(documents);
    }

    // Вспомогательный метод для разбиения текста на чанки
    private List<Document> splitAndCreateDocuments(String text, String source) {
        // Разбиваем текст на чанки по ~2000 символов с перекрытием
        int chunkSize = 2000;
        int overlap = 200;
        List<String> chunks = new java.util.ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start = end - overlap;
            if (start <= 0) break;
        }

        return chunks.stream()
                .map(chunk -> new Document(
                        chunk,
                        Map.of("source", source)
                ))
                .toList();
    }

    // Проверка: есть ли уже документы с данным именем файла
    public boolean existsBySource(String filename) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(filename)
                        .topK(1)
                        .filterExpression("source == \"" + filename + "\"")
                        .build()
        );
        return !results.isEmpty();
    }

    // Поиск похожих документов
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
    }
}
