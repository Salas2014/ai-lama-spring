package com.example.knowledge.internal;

import org.springframework.ai.document.Document;
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

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    // POST /api/v1/knowledge/ingest
    // Body: ["Spring Boot это фреймворк", "Redis это кэш", ...]
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody List<String> texts) {
        knowledgeService.ingest(texts);
        return Map.of("status", "ok", "count", texts.size());
    }

    // POST /api/v1/knowledge/ingest/file
    // Multipart: file=@myfile.txt
    @PostMapping(value = "/ingest/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> ingestFile(@RequestParam("file") MultipartFile file) throws IOException {
        knowledgeService.ingestFile(file);
        return Map.of(
                "status", "ok",
                "filename", file.getOriginalFilename(),
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
}

