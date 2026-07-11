package com.example.knowledge.internal.utils;

import com.example.knowledge.internal.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeLoader {
    private final KnowledgeService knowledgeService;
    private final static Logger log = LoggerFactory.getLogger(KnowledgeLoader.class);

    public KnowledgeLoader(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = null;
        try {
            resources = resolver.getResources("classpath:knowledge/*.txt");
        } catch (IOException e) {
            log.info("Ошибка при загрузке ресурсов из classpath:knowledge/*.txt", e);
            return;
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();

            if (fileExistsInVectorDb(filename)) continue;

            log.info("⏳ Начало обработки файла: {}", filename);

            int chunkIndex = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                StringBuilder chunkBuilder = new StringBuilder();
                String line;
                List<Document> chunkedDocuments = new ArrayList<>();

                while ((line = reader.readLine()) != null) {
                    // Проверяем, является ли строка разделителем (например, состоит из 3 и более дефисов)
                    if (line.trim().matches("-{3,}")) {
                        String chunkText = chunkBuilder.toString().trim();
                        if (!chunkText.isEmpty()) {

                            chunkedDocuments.add(createDocument(chunkText, filename, chunkIndex++));
                            chunkBuilder.setLength(0); // Сбрасываем буфер для следующего чанка
                        }
                    } else {
                        chunkBuilder.append(line).append("\n");
                    }
                }

                // Не забываем сохранить последний чанк, если файл не заканчивался разделителем
                String lastChunkText = chunkBuilder.toString().trim();
                if (!lastChunkText.isEmpty()) {
                    chunkedDocuments.add(createDocument(lastChunkText, filename, chunkIndex));

                }
                knowledgeService.ingestChunk(chunkedDocuments);
            } catch (IOException e) {
                log.info("Ошибка при чтении файла: {}", filename, e);
                return;
            }
            log.info("✅ Успешно загружен и разделен на {} чанков: {}", chunkIndex + 1, filename);
        }
    }

    private boolean fileExistsInVectorDb(String filename) {
        if (knowledgeService.existsBySource(filename)) {
            log.info("⏭️ Пропущен (уже в базе): {}", filename);
            return true;
        }
        return false;
    }

    private static Document createDocument(String chunkText, String filename, int chunkIndex) {
        return new Document(
                chunkText,
                Map.of(
                        "source", filename,
                        "chunk_index", String.valueOf(chunkIndex)
                )
        );
    }
}

