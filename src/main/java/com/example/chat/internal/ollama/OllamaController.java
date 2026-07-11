package com.example.chat.internal.ollama;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/api/v1/ollama")
public class OllamaController {

    private final AiClientInter ollamaClient;

    public OllamaController(AiClientInter ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @GetMapping(value = "/chat")
    public String chat(@RequestParam String message) {
        return ollamaClient.chat(message).block();
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<String> chatStream(@RequestParam String message) {
        return ollamaClient.chat(message);
    }

    @GetMapping(value = "/models")
    public Mono<String> models() {
        return ollamaClient.listModels();
    }

    @GetMapping(value = "/embed")
    public String embed(@RequestParam String input) {
        String resp = ollamaClient.embed(input).block();
        return ollamaClient.embed(input).block();
    }

    @GetMapping(value = "/embed/async")
    public Mono<String> embedAsync(@RequestParam String input) {
        return ollamaClient.embed(input);
    }

}

