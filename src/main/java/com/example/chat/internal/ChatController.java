package com.example.chat.internal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public String getMessage(@RequestParam(defaultValue = "Hello, World!") String message) {
        return chatService.chat(message);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getAsyncMessage(@RequestParam(defaultValue = "Hello, World!") String message) {
        return chatService.asyncChat(message);
    }

    // GET /api/v1/chat/rag?message=как настроить кэш?
    @GetMapping(value = "/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getRagMessage(@RequestParam String message) {
        return chatService.ragChat(message);
    }
}
