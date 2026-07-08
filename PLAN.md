# План разработки — ai-lama-spring

---

## ✅ Шаг 1 — Подключение к Ollama (ГОТОВО)
- [x] `spring-ai-starter-model-ollama` в `pom.xml`
- [x] `application.yaml` — base-url + model
- [x] `ChatController` — GET `/chat?message=...`

---

## 🔲 Шаг 2 — Запустить и проверить
1. В терминале: `docker compose up -d`
2. Скачать модель: `docker exec <container_id> ollama pull llama3.2`
3. Запустить Spring Boot: `./mvnw spring-boot:run`
4. Открыть браузер: `http://localhost:8080/chat?message=Hello`
5. Убедиться что приходит ответ от модели

---

## 🔲 Шаг 3 — Вынести логику в сервис
1. Создать класс `ChatService` в пакете `com.example`
2. Пометить `@Service`
3. Внедрить `ChatClient` через конструктор
4. Создать метод `String chat(String message)`
5. Логику из `ChatController` перенести в `ChatService`
6. В `ChatController` внедрить `ChatService` вместо `ChatClient`

---

## 🔲 Шаг 4 — Добавить системный промпт
1. В `ChatService` в методе `chat(...)` добавить `.system("You are a helpful assistant")`
2. Можно вынести системный промпт в `application.yaml`:
   ```yaml
   spring:
     ai:
       ollama:
         chat:
           options:
             system: "You are a helpful assistant"
   ```

---

## 🔲 Шаг 5 — POST эндпоинт вместо GET
1. Создать record `ChatRequest(String message)` в пакете `com.example`
2. Создать record `ChatResponse(String answer)` в пакете `com.example`
3. В `ChatController` добавить метод:
   - `@PostMapping("/chat")`
   - Принимает `@RequestBody ChatRequest`
   - Возвращает `ChatResponse`

---

## 🔲 Шаг 6 — Стриминг ответа (SSE)
1. В `ChatService` добавить метод `Flux<String> stream(String message)`
2. Использовать `.stream().content()` вместо `.call().content()`
3. В `ChatController` добавить:
   - `@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)`
   - Возвращает `Flux<String>`
4. Добавить зависимость `spring-boot-starter-webflux` в `pom.xml`

---

## 🔲 Шаг 7 — История сообщений (Memory)
1. Создать бин `InMemoryChatMemory` в `@Configuration` классе
2. При создании `ChatClient` добавить `.defaultAdvisors(new MessageChatMemoryAdvisor(memory))`
3. Передавать `conversationId` в каждый запрос через advisor options
4. Протестировать: задать вопрос, потом спросить "что я спросил раньше?"

---

## 🔲 Шаг 8 — Structured Output
1. Создать record, например: `MovieRecommendation(String title, String genre, String reason)`
2. В `ChatService` добавить метод:
   - `MovieRecommendation recommend(String topic)`
   - Использовать `.call().entity(MovieRecommendation.class)`
3. Добавить GET `/recommend?topic=...` в контроллер

---

## 🔲 Шаг 9 — RAG (чат по документу)
1. Добавить зависимость `spring-ai-starter-vector-store-simple` в `pom.xml`
2. Добавить зависимость `spring-ai-pdf-document-reader` в `pom.xml`
3. Создать `DocumentLoaderService`:
   - Загрузить PDF/TXT из `resources/docs/`
   - Разбить на чанки: `TokenTextSplitter`
   - Сохранить в `VectorStore`
4. В `ChatService` подключить `QuestionAnswerAdvisor(vectorStore)`
5. Добавить эндпоинт `/chat/rag?message=...`

---

## 🔲 Шаг 10 — Тесты
1. Unit тест для `ChatService` — замокать `ChatClient`
2. Integration тест `ChatControllerTest` — использовать `@SpringBootTest` + `MockMvc`
3. Проверить `/chat`, `/chat` (POST), `/recommend`
