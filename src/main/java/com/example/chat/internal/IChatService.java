package com.example.chat.internal;

import reactor.core.publisher.Flux;

public interface IChatService {
    String chat(String message);

    Flux<String> asyncChat(String message);

    Flux<String> ragChat(String userQuestion);
}
