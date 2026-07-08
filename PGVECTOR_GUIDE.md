# 🧠 pgvector + Ollama + Spring AI — Полный гайд

---

## 📌 Что такое pgvector и зачем он нужен?

**pgvector** — это расширение для PostgreSQL, которое позволяет хранить и искать **векторные эмбеддинги** (embeddings).

### Что такое эмбеддинг?

Эмбеддинг — это числовое представление текста (или изображения) в виде вектора (массива float-чисел).
Например, фраза _"как настроить Redis"_ превращается в вектор из 768 или 1536 чисел вроде:
```
[0.023, -0.412, 0.887, 0.001, ...]
```

Магия в том, что **похожие по смыслу фразы дают близкие векторы** в многомерном пространстве.

---

## ❓ Зачем это нужно — реальная проблема

### Проблема без pgvector:

Представь: у тебя есть база знаний — 500 статей документации по внутреннему продукту.
Пользователь задаёт вопрос через чат: _"как добавить нового пользователя?"_

**Обычный поиск (LIKE, полнотекстовый):**
- Ищет буквально совпадение слов
- Фраза _"создание аккаунта"_ — не найдёт, хотя смысл тот же
- Не понимает синонимы, перефразировки

**Векторный поиск (pgvector):**
- Преобразует вопрос в вектор
- Находит статьи с близким **смыслом**, а не словами
- Работает как семантический поиск

---

## 🎯 Наш сценарий: "Умная база знаний для разработчика"

### Задача:
Создадим мини **RAG-систему** (Retrieval Augmented Generation):

1. **Загружаем** несколько "документов" (заметок, FAQ) в PostgreSQL с векторами
2. **Пользователь задаёт вопрос** через HTTP
3. Система **ищет похожие документы** по смыслу через pgvector
4. **Передаёт найденный контекст + вопрос** в Ollama (llama3.2)
5. **Ollama отвечает**, опираясь на наши документы — не выдумывает

### Почему это круто локально?
- Всё работает **без интернета и без OpenAI**
- Твои данные **никуда не уходят**
- Ollama генерирует ответы, pgvector ищет контекст — всё на твоей машине

---

## 🗄️ Индексы в pgvector — когда и зачем

pgvector поддерживает несколько типов индексов. Выбор зависит от размера данных и требований к точности.

### 1. Без индекса (Sequential Scan)

```sql
-- Поиск без индекса — перебирает ВСЕ строки
SELECT * FROM documents ORDER BY embedding <=> query_vector LIMIT 5;
```

| | |
|---|---|
| ✅ Точность | 100% (exact search) |
| ❌ Скорость | Медленно при > 10k записей |
| 📌 Когда использовать | При разработке, для малых наборов (< 10k строк) |

---

### 2. IVFFlat (Inverted File with Flat compression)

```sql
-- Создание индекса: делим векторы на 100 кластеров
CREATE INDEX ON documents USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

**Как работает:**
- При создании делит все векторы на **N кластеров (lists)**
- При поиске смотрит только в **ближайшие кластеры** (не все)
- Компромисс между скоростью и точностью

```sql
-- Регулируем сколько кластеров проверять при поиске
SET ivfflat.probes = 10;  -- больше = точнее, но медленнее
```

| | |
|---|---|
| ✅ Скорость | Быстро при > 10k записей |
| ⚠️ Точность | ~95-98% (approximate search) |
| ❌ Требование | Нужно иметь данные ДО создания индекса |
| 📌 Когда использовать | Большие наборы (100k+ строк), редко меняющиеся данные |
| 💡 Правило | `lists` ≈ `количество_строк / 1000` (минимум 100) |

---

### 3. HNSW (Hierarchical Navigable Small World)

```sql
-- Создание HNSW индекса
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

**Как работает:**
- Строит многослойный граф связей между векторами
- При поиске "путешествует" по графу к ближайшим соседям

```sql
-- Регулируем точность поиска
SET hnsw.ef_search = 100;  -- больше = точнее
```

| | |
|---|---|
| ✅ Скорость | Очень быстро |
| ✅ Точность | Выше чем IVFFlat (~99%) |
| ✅ Инкрементальность | Можно добавлять данные после создания |
| ❌ Память | Больше памяти чем IVFFlat |
| 📌 Когда использовать | Продакшн, динамические данные, нужна скорость + точность |

---

### 4. Операторы расстояния

```sql
<->   -- L2 (Euclidean distance) — геометрическое расстояние
<#>   -- Inner Product — максимизирует схожесть (для normalized vectors)
<=>   -- Cosine distance — угол между векторами (лучший для текста!)
```

**Для текста почти всегда используй `<=>` (cosine)** — он не зависит от длины вектора, только от направления (смысла).

---

## 🛠️ Что нужно реализовать — пошаговый план

### Шаг 1: Обновить compose.yaml — добавить pgvector

```yaml
services:
  ollama:
    image: 'ollama/ollama:latest'
    ports:
      - '11434:11434'

  postgres:
    image: 'pgvector/pgvector:pg17'   # PostgreSQL 17 + pgvector
    environment:
      POSTGRES_DB: ailamadb
      POSTGRES_USER: aiuser
      POSTGRES_PASSWORD: aipassword
    ports:
      - '5432:5432'
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

---

### Шаг 2: Добавить зависимости в pom.xml

```xml
<!-- Spring AI Vector Store для PostgreSQL -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>

<!-- Spring Data JPA + PostgreSQL драйвер -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

### Шаг 3: Обновить application.yaml

```yaml
spring:
  application:
    name: ai-lama-spring
  
  datasource:
    url: jdbc:postgresql://localhost:5432/ailamadb
    username: aiuser
    password: aipassword
  
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3.2
      embedding:
        model: nomic-embed-text   # модель для эмбеддингов (нужно скачать)
    
    vectorstore:
      pgvector:
        initialize-schema: true     # автоматически создать таблицу vector_store
        index-type: HNSW            # тип индекса: NONE, IVFFlat, HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 768             # размерность для nomic-embed-text
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
            ChatService.java           -- существующий (расширим для RAG)
```

**KnowledgeService.java:**
```java
@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Загружаем документы в pgvector
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
         ↓ pgvector сохраняет в таблицу vector_store
    
[2] GET /api/v1/chat/rag?message=как настроить кэширование?

         ↓ vectorStore.similaritySearch("как настроить кэширование?")
         ↓ Ollama конвертирует вопрос в вектор
         ↓ pgvector ищет ближайшие векторы (HNSW индекс)
         ↓ Возвращает: "Redis — это кэш в памяти" (похоже по смыслу!)
         
         ↓ ChatService строит prompt с контекстом
         ↓ Ollama (llama3.2) генерирует ответ на основе контекста
         ↓ Ответ возвращается пользователю
```

---

## 📊 Сравнительная таблица индексов

| Критерий | Без индекса | IVFFlat | HNSW |
|---|---|---|---|
| Точность | 100% | ~95-98% | ~99% |
| Скорость поиска | Медленно | Быстро | Очень быстро |
| Память | Мало | Средне | Больше |
| Добавление данных после создания | ✅ | ❌ (переиндекс) | ✅ |
| Подходит для | < 10k строк | 100k+ строк | Любой размер |
| Наш сценарий | ✅ dev/learn | - | ✅ хорошо |

---

## 🚀 Порядок реализации (что делаем дальше)

1. ✅ Обновить `compose.yaml` — добавить pgvector контейнер
2. ✅ Добавить зависимости в `pom.xml`
3. ✅ Обновить `application.yaml` — datasource + vector store config
4. ✅ Создать `KnowledgeController` + `KnowledgeService`
5. ✅ Расширить `ChatService` — добавить `ragChat()` метод
6. ✅ Скачать `nomic-embed-text` модель в Ollama
7. ✅ Протестировать через HTTP: загрузить документы → задать вопрос
8. 🔬 Поэкспериментировать с индексами и посмотреть разницу в скорости

---

## 💡 Почему именно этот стек для обучения?

| Компонент | Роль |
|---|---|
| **Ollama (llama3.2)** | LLM — генерирует ответы |
| **Ollama (nomic-embed-text)** | Embedding model — конвертирует текст в векторы |
| **PostgreSQL + pgvector** | Vector store — хранит и ищет векторы |
| **Spring AI** | Клей — связывает всё вместе, прячет сложность |
| **Spring Boot** | HTTP API — точка входа |

Всё **100% локально** — никаких API ключей, никаких облаков, никаких расходов! 🎉

