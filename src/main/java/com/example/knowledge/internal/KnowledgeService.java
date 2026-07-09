package com.example.knowledge.internal;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
        Document document = new Document(
                text,
                Map.of("filename", file.getOriginalFilename())
        );
        vectorStore.add(List.of(document));
    }

    // Вариант 3: загрузка .txt из classpath (src/main/resources/)
    public void ingestResource(Resource resource) {
        TextReader reader = new TextReader(resource);
        reader.getCustomMetadata().put("source", resource.getFilename());
        List<Document> documents = reader.get();
        vectorStore.add(documents);
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
