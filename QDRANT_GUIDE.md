# 🧠 Qdrant + Ollama + Spring AI — Полный гайд

---

## 📌 Что такое Qdrant и зачем он нужен?

**Qdrant** — это высокопроизводительная **векторная база данных**, специально созданная для хранения и поиска **векторных эмбеддингов** (embeddings).

### Что такое эмбеддинг?

Эмбеддинг — это числовое представление текста (или изображения) в виде вектора (массива float-чисел).
Например, фраза _"как настроить Redis"_ превращается в вектор из 768 или 1536 чисел вроде:
```
[0.023, -0.412, 0.887, 0.001, ...]
```

Магия в том, что **похожие по смыслу фразы дают близкие векторы** в многомерном пространстве.

---

## ❓ Зачем это нужно — реальная проблема

### Проблема без векторного поиска:

Представь: у тебя есть база знаний — 500 статей документации по внутреннему продукту.
Пользователь задаёт вопрос через чат: _"как добавить нового пользователя?"_

**Обычный поиск (LIKE, полнотекстовый):**
- Ищет буквально совпадение слов
- Фраза _"создание аккаунта"_ — не найдёт, хотя смысл тот же
- Не понимает синонимы, перефразировки

**Векторный поиск (Qdrant):**
- Преобразует вопрос в вектор
- Находит статьи с близким **смыслом**, а не словами
- Работает как семантический поиск

---

## 🎯 Наш сценарий: "Умная база знаний для разработчика"

### Задача:
Создадим мини **RAG-систему** (Retrieval Augmented Generation):

1. **Загружаем** несколько "документов" (заметок, FAQ) в Qdrant с векторами
2. **Пользователь задаёт вопрос** через HTTP
3. Система **ищет похожие документы** по смыслу через Qdrant
4. **Передаёт найденный контекст + вопрос** в Ollama (llama3.2)
5. **Ollama отвечает**, опираясь на наши документы — не выдумывает

### Почему это круто локально?
- Всё работает **без интернета и без OpenAI**
- Твои данные **никуда не уходят**
- Ollama генерирует ответы, Qdrant ищет контекст — всё на твоей машине

---

## 🗄️ Индексы в Qdrant — когда и зачем

Qdrant использует **HNSW (Hierarchical Navigable Small World)** как основной алгоритм индексации — он встроен по умолчанию и не требует ручной настройки, как в pgvector.

### 1. HNSW (по умолчанию)

Qdrant автоматически строит HNSW-граф при добавлении векторов.

**Как работает:**
- Строит многослойный граф связей между векторами
- При поиске "путешествует" по графу к ближайшим соседям
- Поддерживает инкрементальное добавление данных без переиндексации

| | |
|---|---|
| ✅ Скорость | Очень быстро |
| ✅ Точность | ~99% (approximate nearest neighbor) |
| ✅ Инкрементальность | Добавление данных без переиндексации |
| ✅ Масштаб | Сотни миллионов векторов |
| 📌 Когда использовать | Всегда — это дефолт Qdrant |

---

### 2. Параметры HNSW (тонкая настройка)

```json
// Пример настройки через REST API (для справки — Spring AI делает это за тебя)
{
  "hnsw_config": {
    "m": 16,               // количество связей на слое (больше = точнее, больше памяти)
    "ef_construct": 100,   // точность построения (больше = медленнее индексация)
    "full_scan_threshold": 10000  // ниже этого — точный поиск без индекса
  }
}
```

| Параметр | Описание | Дефолт |
|---|---|---|
| `m` | Связей на уровень графа | 16 |
| `ef_construct` | Качество построения | 100 |
| `full_scan_threshold` | Перебор всех при малом кол-ве | 10000 |

---

### 3. Метрики расстояния в Qdrant

```
Cosine    -- угол между векторами (лучший для текста!)
Dot       -- скалярное произведение (для нормализованных векторов)
Euclid    -- L2 расстояние (геометрическое)
Manhattan -- L1 расстояние
```

**Для текста почти всегда используй `Cosine`** — он не зависит от длины вектора, только от направления (смысла).

---

### 4. Payload фильтрация (бонус Qdrant)

Qdrant позволяет хранить метаданные (payload) рядом с векторами и **фильтровать при поиске**:

```json
// Поиск только по документам категории "java"
{
  "filter": {
    "must": [{ "key": "category", "match": { "value": "java" } }]
  }
}
```

Spring AI передаёт metadata документов как payload — это работает автоматически.

---

## 🛠️ Что нужно реализовать — пошаговый план

### Шаг 1: compose.yaml — Qdrant уже добавлен ✅

```yaml
# http://localhost:6333/dashboard#/tutorial
qdrant:
  image: qdrant/qdrant:latest
  container_name: qdrant-vector-db
  ports:
    - "6333:6333"  # REST API и Web UI
    - "6334:6334"  # gRPC (Spring AI по умолчанию использует его для скорости)
  volumes:
    - ./qdrant_storage:/qdrant/storage
```

> Qdrant Web UI доступен по адресу: **http://localhost:6333/dashboard**

---

### Шаг 2: Добавить зависимости в pom.xml

```xml
<!-- Spring AI Vector Store для Qdrant -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
</dependency>
```

> Никакого JPA, никакого PostgreSQL драйвера — Qdrant работае�� через gRPC/REST, всё уже включено в стартер.

---

### Шаг 3: Обновить application.yaml

```yaml
spring:
  application:
    name: ai-lama-spring

  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3.2
      embedding:
        model: nomic-embed-text   # модель для эмбеддингов (нужно скачать)

    vectorstore:
      qdrant:
        host: localhost
        port: 6334              # gRPC порт (быстрее чем REST)
        collection-name: knowledge  # имя коллекции в Qdrant
        initialize-schema: true     # автоматически создать коллекцию
```

---

### Шаг 4: Новый модуль `knowledge` — загрузка документов

**Структура файлов которые нужно создать:**
```
src/main/java/com/example/
    knowledge/
        internal/
            KnowledgeController.java   -- HTTP endpoints: загрузить / поиск
            KnowledgeService.java      -- бизнес-логика с VectorStore
    chat/
        internal/
            ChatController.java        -- существующий (расширим)
            ChatService.java           -- существующий (расшири�� для RAG)
```

**KnowledgeService.java:**
```java
@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Загружаем документы в Qdrant
    public void ingest(List<String> texts) {
        List<Document> documents = texts.stream()
            .map(Document::new)
            .toList();
        vectorStore.add(documents);  // Spring AI сам вызовет Ollama для эмбеддингов
    }

    // Ищем похожие документы
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(topK)
        );
    }
}
```

**ChatService.java — RAG версия:**
```java
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public String ragChat(String userQuestion) {
        // 1. Ищем релевантные документы
        List<Document> context = vectorStore.similaritySearch(
            SearchRequest.query(userQuestion).withTopK(3)
        );

        // 2. Формируем контекст из найденных документов
        String contextText = context.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n---\n"));

        // 3. Отправляем в Ollama с контекстом
        String prompt = """
            Используй только следующий контекст для ответа.
            Если ответа нет в контексте — скажи об этом.
            
            Контекст:
            %s
            
            Вопрос: %s
            """.formatted(contextText, userQuestion);

        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }
}
```

> **Важно:** Java-код работает с `VectorStore` — Spring AI абстракция.
> Ты не пишешь ни одной строки Qdrant-специфичного кода! Замена pgvector → Qdrant — это только конфиг.

---

### Шаг 5: Нужно скачать модель эмбеддингов

```bash
# Через Docker (если Ollama в контейнере)
docker exec -it <ollama-container-id> ollama pull nomic-embed-text

# Или если Ollama локально
ollama pull nomic-embed-text
```

---

## 🔄 Полный сценарий работы

```
[1] POST /api/v1/knowledge/ingest
    Body: ["Spring Boot это фреймворк для Java", "Redis — это кэш в памяти", ...]
    
         ↓ KnowledgeService.ingest()
         ↓ Spring AI вызывает Ollama (nomic-embed-text)
         ↓ Ollama возвращает векторы [0.12, -0.45, ...]
         ↓ Qdrant сохраняет в коллекцию "knowledge" через gRPC
    
[2] GET /api/v1/chat/rag?message=как настроить кэширование?

         ↓ vectorStore.similaritySearch("как настроить кэширование?")
         ↓ Ollama конвертирует вопрос в вектор
         ↓ Qdrant ищет ближайшие векторы (HNSW индекс)
         ↓ Возвращает: "Redis — это кэш в памяти" (похоже по смыслу!)
         
         ↓ ChatService строит prompt с контекстом
         ↓ Ollama (llama3.2) генерирует ответ на основе контекста
         ↓ Ответ возвращается пользователю
```

---

## 📊 Сравнение: Qdrant vs pgvector

| Критерий | pgvector | **Qdrant** |
|---|---|---|
| Тип | Расширение PostgreSQL | Специализированная vector DB |
| Индекс | IVFFlat / HNSW (ручная настройка) | HNSW из коробки |
| Скорость | Хорошая | Отличная (gRPC + Rust) |
| Фильтрация по метаданным | SQL WHERE | Payload фильтры (встроено) |
| Web UI | ❌ | ✅ http://localhost:6333/dashboard |
| Нужен PostgreSQL | ✅ да | ❌ нет (отдельный сервис) |
| Простота конфига | Средняя (JPA + datasource) | Простая (только host + port) |
| Наш выбор | — | ✅ используем |

---

## 🚀 Порядок реализации (что делаем дальше)

1. ✅ `compose.yaml` — Qdrant контейнер уже добавлен
2. ✅ Добавить зависимость `spring-ai-starter-vector-store-qdrant` в `pom.xml`
3. ✅ Обновить `application.yaml` — настроить Qdrant host/port/collection
4. ✅ Создать `KnowledgeController` + `KnowledgeService`
5. ✅ Расширить `ChatService` — добавить `ragChat()` метод
6. ✅ Скачать `nomic-embed-text` модель в Ollama
7. ✅ Протестировать через HTTP: загрузить документы → задать вопрос
8. 🔬 Посмотреть коллекцию в Qdrant Web UI → http://localhost:6333/dashboard

---

## 💡 Почему именно этот стек для обучения?

| Компонент | Роль |
|---|---|
| **Ollama (llama3.2)** | LLM — генерирует ответы |
| **Ollama (nomic-embed-text)** | Embedding model — конвертирует текст в векторы |
| **Qdrant** | Vector store — хранит и ищет векторы (специализированная БД) |
| **Spring AI** | Клей — связывает всё вместе, прячет сложность |
| **Spring Boot** | HTTP API — точка входа |

Всё **100% локально** — никаких API ключей, никаких облаков, никаких расходов! 🎉

---

# 🔧 Полные примеры кода

## 📁 Структура модуля `knowledge`

```
src/main/java/com/example/
    knowledge/
        internal/
            KnowledgeController.java
            KnowledgeService.java
    chat/
        internal/
            ChatController.java   ← добавляем /rag эндпоинт
            ChatService.java      ← добавляем ragChat()
```

---

## 1️⃣ KnowledgeService.java

```java
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
                Map.of("filename", file.getOriginalFilename())  // метаданные → payload в Qdrant
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
```

---

## 2️⃣ KnowledgeController.java

```java
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
```

---

## 3️⃣ ChatService.java — добавляем ragChat()

```java
package com.example.chat.internal;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public Flux<String> asyncChat(String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    // RAG: ищет контекст в Qdrant, затем отвечает с его учётом
    public String ragChat(String userQuestion) {
        List<Document> context = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuestion)
                        .topK(3)
                        .build()
        );

        if (context.isEmpty()) {
            return chat(userQuestion);  // если контекста нет — обычный чат
        }

        String contextText = context.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String prompt = """
                Используй только следующий контекст для ответа.
                Если ответа нет в контексте — так и скажи.
                
                Контекст:
                %s
                
                Вопрос: %s
                """.formatted(contextText, userQuestion);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
```

---

## 4️⃣ ChatController.java — добавляем /rag

```java
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
    @GetMapping("/rag")
    public String getRagMessage(@RequestParam String message) {
        return chatService.ragChat(message);
    }
}
```

---

## 5️⃣ Автозагрузка .txt при старте (опционально)

Положи файлы в `src/main/resources/knowledge/*.txt` и добавь компонент:

```java
package com.example.knowledge.internal;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KnowledgeLoader {

    private final KnowledgeService knowledgeService;

    public KnowledgeLoader(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/*.txt");

        for (Resource resource : resources) {
            knowledgeService.ingestResource(resource);
            System.out.println("✅ Загружен: " + resource.getFilename());
        }
    }
}
```

---

## 📄 Какие форматы файлов поддерживаются?

| Формат | Класс Spring AI | Зависимость |
|---|---|---|
| `.txt` | `TextReader` | ✅ встроено |
| `.md` | `TextReader` | ✅ встроено (читает как plain text) |
| `.json` | `JsonReader` | ✅ встроено |
| `.pdf` | `PagePdfDocumentReader` | `spring-ai-pdf-document-reader` |
| `.pdf`, `.docx`, `.html` | `TikaDocumentReader` | `spring-ai-tika-document-reader` |

> Для `.txt` никаких дополнительных зависимостей не нужно — `TextReader` уже в Spring AI!

---

## 🧪 HTTP тесты (добавить в chat.http)

```http
### Загрузить строки через JSON
POST http://localhost:8888/api/v1/knowledge/ingest
Content-Type: application/json

["Spring Boot это фреймворк для Java приложений",
 "Redis это in-memory база данных, используется как кэш",
 "Qdrant это векторная база данных написанная на Rust",
 "Docker это инструмент для контейнеризации приложений"]

###

### Поиск по базе знаний
GET http://localhost:8888/api/v1/knowledge/search?query=что такое кэш&topK=2

###

### RAG чат — ответ на основе загруженных документов
GET http://localhost:8888/api/v1/chat/rag?message=что такое Redis?

###

### Обычный чат (без контекста из Qdrant)
GET http://localhost:8888/api/v1/chat?message=Привет!
```

---

## 🔍 Проверить данные в Qdrant Web UI

После загрузки открой **http://localhost:6333/dashboard** → Collections → `knowledge` → Points.

Каждый документ виден как точка (point) с:
- `vector` — числовой вектор от nomic-embed-text
- `payload` — метаданные (filename, source, etc.)
- `text` — оригинальный текст документа

