# Observability Guide — Grafana + Prometheus + Spring AI

Это пошаговая инструкция по настройке мониторинга для нашего `ai-lama-spring` приложения.
Стек: **Spring Boot Actuator → Prometheus → Grafana**.

---

## Шаг 1 — Добавить зависимости в `pom.xml`

Открой `pom.xml` и добавь внутрь блока `<dependencies>`:

```xml
<!-- Spring Boot Actuator — открывает /actuator/health, /actuator/metrics и т.д. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer — собирает метрики в формате Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

## Шаг 2 — Настроить `application.yaml`

Открой `src/main/resources/application.yaml` и добавь конфиг Actuator:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  metrics:
    tags:
      application: ${spring.application.name}
  endpoint:
    health:
      show-details: always
```

После этого эндпоинт с метриками будет доступен по адресу:
`http://localhost:8080/actuator/prometheus`

Проверь в браузере — должна открыться стена текста с метриками.

---

## Шаг 3 — Настроить Prometheus

Открой файл `docker/prometheus/prometheus.yml` (он уже создан, но пустой) и вставь:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'ai-lama-spring'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'ai-lama-spring'
```

> `host.docker.internal` — это специальный DNS-адрес внутри Docker, который указывает на твой localhost.
> Используется потому, что Prometheus крутится в контейнере, а Spring Boot — на хосте.

---

## Шаг 4 — Настроить Grafana Datasource

Открой файл `docker/grafana/provisioning/datasources/datasources.yml` (тоже пустой) и вставь:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

---

## Шаг 5 — Обновить `compose.yaml`

Открой `compose.yaml` в корне проекта и замени содержимое на:

```yaml
services:
  ollama:
    image: 'ollama/ollama:latest'
    ports:
      - '11434:11434'

  prometheus:
    image: prom/prometheus:latest
    ports:
      - '9090:9090'
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    extra_hosts:
      - "host.docker.internal:host-gateway"

  grafana:
    image: grafana/grafana:latest
    ports:
      - '3000:3000'
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./docker/grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - prometheus
```

---

## Шаг 6 — Запустить всё

1. Запусти Spring Boot приложение (как обычно через IDE или `./mvnw spring-boot:run`)
2. В отдельном терминале подними Docker-контейнеры:

```bash
docker compose up -d
```

3. Проверь что всё работает:
   - Spring метрики: http://localhost:8080/actuator/prometheus
   - Prometheus UI: http://localhost:9090
   - Grafana UI: http://localhost:3000 (логин: `admin` / пароль: `admin`)

---

## Шаг 7 — Настроить Dashboard в Grafana

> ⚠️ **Если при импорте по ID видишь ошибку "Bad Gateway"** — это нормально.
> Grafana пытается скачать дашборд с `grafana.com` из Docker-контейнера, но у него нет доступа в интернет.
> Решение: скачать JSON вручную в браузере и импортировать как файл (см. ниже).

---

### Шаг 7.1 — Скачать JSON дашборда вручную

Открой в браузере одну из ссылок и нажми кнопку **"Download JSON"** (или просто сохрани страницу как файл):

| Ссылка для скачивания | Что показывает |
|---|---|
| https://grafana.com/grafana/dashboards/4701 | JVM (Micrometer) — память, CPU, потоки |
| https://grafana.com/grafana/dashboards/12900 | Spring Boot 3 — HTTP запросы, метрики приложения |
| https://grafana.com/grafana/dashboards/11378 | Spring Boot Statistics |

На странице дашборда найди кнопку **"Download JSON"** — она скачает `.json` файл на твой компьютер.

---

### Шаг 7.2 — Импортировать JSON в Grafana

1. Зайди в Grafana: http://localhost:3000 (логин: `admin` / пароль: `admin`)
2. Левое меню → **Dashboards** → кнопка **New** → **Import**
3. Нажми **"Upload dashboard JSON file"**
4. Выбери скачанный `.json` файл
5. В поле **Prometheus** выбери `Prometheus` (наш datasource)
6. Нажми **Import**

Ты сразу увидишь графики CPU, памяти, HTTP-запросов и т.д.

---

### Альтернатива — вставить JSON напрямую

Если не хочешь скачивать файл:
1. Открой ссылку на дашборд в браузере, например: https://grafana.com/grafana/dashboards/4701
2. Нажми **"Download JSON"** → откроется JSON в новой вкладке
3. Скопируй весь текст (`Ctrl+A` → `Ctrl+C`)
4. В Grafana → **Import** → вставь в поле **"Import via panel json"**
5. Нажми **Load** → выбери datasource → **Import**

---

## Что ты увидишь в метриках

После того как поговоришь с моделью через `/api/v1/chat`, в Prometheus/Grafana появятся метрики:

| Метрика | Что показывает |
|---------|---------------|
| `http_server_requests_seconds` | Время ответа и количество HTTP-запросов к нашему API |
| `jvm_memory_used_bytes` | Использование памяти JVM |
| `spring_ai_chat_client_operation_*` | Метрики вызовов AI модели (токены, время ответа) |
| `process_cpu_usage` | Загрузка CPU |
| `hikaricp_connections_*` | Состояние пула соединений (если будет БД) |

---

## Быстрая проверка в Prometheus

Зайди на http://localhost:9090/targets — там должен быть `ai-lama-spring` в статусе **UP**.

Если `DOWN` — убедись что:
1. Spring Boot приложение запущено
2. Доступен http://localhost:8080/actuator/prometheus
3. В `prometheus.yml` указан правильный хост (`host.docker.internal:8080`)

