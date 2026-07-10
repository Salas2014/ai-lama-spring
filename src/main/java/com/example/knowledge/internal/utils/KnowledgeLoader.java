package com.example.knowledge.internal.utils;

import com.example.knowledge.internal.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KnowledgeLoader {
    private final KnowledgeService knowledgeService;
    private final static Logger log = LoggerFactory.getLogger(KnowledgeLoader.class);

    public KnowledgeLoader(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/*.txt");

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (knowledgeService.existsBySource(filename)) {
                log.info("⏭️ Пропущен (уже в базе): {}", filename);
                continue;
            }
            knowledgeService.ingestResource(resource);
            log.info("✅ Загружен: {}", filename);
        }
    }
}
