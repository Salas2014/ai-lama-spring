package com.example.knowledge.internal;

import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeAnalysisService analysisService;

    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeAnalysisService analysisService) {
        this.knowledgeService = knowledgeService;
        this.analysisService = analysisService;
    }

    // POST /api/v1/knowledge/ingest
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody List<String> texts) {
        knowledgeService.ingest(texts);
        return Map.of("status", "ok", "count", texts.size());
    }

    // POST /api/v1/knowledge/ingest/file
    @PostMapping(value = "/ingest/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> ingestFile(@RequestParam("file") MultipartFile file) throws IOException {
        knowledgeService.ingestFile(file);
        return Map.of(
                "status", "ok",
                "filename", String.valueOf(file.getOriginalFilename()),
                "size", file.getSize() + " bytes"
        );
    }

    // GET /api/v1/knowledge/search?query=как настроить Redis&topK=3
    @GetMapping("/search")
    public List<String> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK
    ) {
        return knowledgeService.search(query, topK)
                .stream()
                .map(Document::getText)
                .toList();
    }

    /**
     * GET /api/v1/knowledge/topics?file=db.txt
     * Анализирует файл из resources/knowledge/ и возвращает список тем, деталей и пробелов.
     */
    @GetMapping("/topics")
    public String topics(@RequestParam String file) throws IOException {
        ClassPathResource resource = new ClassPathResource("knowledge/" + file);
        if (!resource.exists()) {
            return "Файл не найден: knowledge/" + file;
        }
        return analysisService.analyzeTopics(resource);
    }

    /**
     * POST /api/v1/knowledge/topics/file
     * Загрузи любой .txt файл и получи анализ тем — без сохранения в векторную БД.
     */
    @PostMapping(value = "/topics/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String topicsFromUpload(@RequestParam("file") MultipartFile file) throws IOException {
        return analysisService.analyzeTopics(file);
    }
}

