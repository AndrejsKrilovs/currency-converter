# Currency Exchange API

REST-сервис для работы со справочником валют, курсами обмена и конвертацией сумм.
Построен без Spring: на чистых сервлетах поверх Jetty, с JPA (EclipseLink), JTA (Narayana) и миграциями Liquibase.

## Содержание

- [Технологии](#технологии)
- [Архитектура](#архитектура)
- [Эндпоинты](#эндпоинты)
- [Обработка ошибок](#обработка-ошибок)
- [Запуск](#запуск)
- [Конфигурация](#конфигурация)
- [Тесты](#тесты)

## Технологии

| Область | Технология |
|---|---|
| Язык | Java 25 (instance `main` без класса), Lombok |
| Сборка | Gradle (Kotlin DSL), Gradle Wrapper |
| HTTP-сервер | Jetty 12.1 (`jetty-ee11-servlet`), Jakarta Servlet |
| Сериализация | Jackson (JSON, YAML, JSR-310) |
| Валидация | Jakarta Bean Validation (Hibernate Validator через `jersey-bean-validation`) |
| ORM | EclipseLink 5 (JPA, динамические сущности) |
| Транзакции | JTA — Narayana |
| Пул соединений | Agroal (`agroal-pool`, `agroal-narayana`) |
| БД | PostgreSQL 18 |
| Миграции | Liquibase (YAML-чейнджлоги) |
| Логирование | Logback, «wide events» (одно JSON-событие на запрос) |
| Тесты | JUnit 6, Mockito, Testcontainers (PostgreSQL), JaCoCo |

## Архитектура

Приложение разбито по предметным пакетам (`currency`, `rate`, `exchange`) и общим инфраструктурным (`config`, `error`, `util`).

```
src/main/java/krilovs/andrejs/app
├── Main.java                 # точка входа: сборка Jetty, фильтров, сервлетов
├── config/
│   ├── AppConfig(Loader)     # типизированный application.yaml с подстановкой ${ENV:default}
│   ├── jpa/
│   │   ├── PersistenceConfig     # EMF, JTA-транзакции, жизненный цикл EntityManager
│   │   ├── DataSourceFactory     # Agroal + Narayana
│   │   ├── PersistenceUnitFactory
│   │   └── DatabaseMigrator      # запуск Liquibase при старте
│   └── logging/              # WideEvent, WideEventFilter, PersistenceSessionLog
├── currency/                 # Servlet → Service → Mapper, Request/Response DTO
├── rate/                     # то же для курсов
├── exchange/                 # конвертация сумм поверх RateService
├── error/                    # исключения и ErrorHandlingFilter
└── util/                     # AbstractServlet, BeanValidator, JsonWriter, DynamicEntities
```

### Слои

```
HTTP ─▶ WideEventFilter ─▶ ErrorHandlingFilter ─▶ Servlet ─▶ Service ─▶ PersistenceConfig ─▶ PostgreSQL
                                                    │           │
                                              DTO + валидация  Mapper (DTO ⇄ DynamicEntity)
```

- **Servlet** — разбирает путь/параметры/тело, валидирует DTO (`BeanValidator`), выбирает HTTP-статус. Общая логика в `AbstractServlet`.
- **Service** — бизнес-логика и работа с БД через `PersistenceConfig`.
- **Mapper** — преобразование между DTO и динамическими JPA-сущностями.
- **Фильтры** — `WideEventFilter` собирает и пишет событие запроса; `ErrorHandlingFilter` превращает доменные исключения в JSON-ответы с нужным статусом.

### Ключевые решения

- **Динамические сущности EclipseLink.** Сущности `Currency` и `Rate` описываются программно (`DynamicEntities`), без классов-сущностей и `persistence.xml`.
- **JTA-транзакции с семантикой REQUIRED.** `PersistenceConfig.inTransaction(...)` открывает транзакцию и привязывает `EntityManager` к ней; вложенные вызовы присоединяются к внешней транзакции, а при исключении она помечается rollback-only. `withEntityManager(...)` — для read-only запросов без транзакции.
- **Кэш валют.** При старте все валюты загружаются в общий кэш EclipseLink, поэтому поиск курсов не обращается к БД за справочником.
- **Миграции при старте.** `DatabaseMigrator` применяет Liquibase-чейнджлоги до создания persistence unit. Начальные данные (валюты и курсы EUR) лежат в `db/changelog/data`.
- **Поиск курса (`RateService.findByPair`).** Порядок: прямой курс → обратный (1/rate) → кросс-курс через EUR.
- **Wide events.** Каждый запрос порождает одно структурированное JSON-событие (метод, путь, статус, длительность, данные ответа/ошибки), которое пишется в `logs/wide-events.<дата>.log` (хранится 7 дней).

### Модель данных

```
currency(code PK varchar(4), name varchar(30) NOT NULL, symbol varchar(3))
rate(base_currency FK→currency.code, target_currency FK→currency.code, rate numeric(19,6),
     PK (base_currency, target_currency))
```

## Эндпоинты

Базовый путь: `http://localhost:8080/api/v1`. Все ответы — `application/json`.
Коды валют и пары нечувствительны к регистру (приводятся к верхнему).

### Валюты

| Метод | Путь | Описание | Успех |
|---|---|---|---|
| `GET` | `/currencies` | Список всех валют | `200` |
| `GET` | `/currency/{code}` | Валюта по коду, например `/currency/USD` | `200` |
| `POST` | `/currencies` | Добавить валюту | `201` |

Тело `POST /currencies`:

```json
{ "code": "PLN", "name": "Polish Zloty", "symbol": "zł" }
```

Правила: `code` — ровно 3 символа, `name` — обязателен, `symbol` — необязателен (до 3 символов).

Ответ:

```json
{ "code": "PLN", "name": "Polish Zloty", "symbol": "zł" }
```

### Курсы обмена

| Метод | Путь | Описание | Успех |
|---|---|---|---|
| `GET` | `/exchangeRates` | Все курсы | `200` |
| `GET` | `/exchangeRate/{pair}` | Курс пары, например `/exchangeRate/USDEUR` | `200` |
| `POST` | `/exchangeRates` | Добавить курс | `201` |
| `PATCH` | `/exchangeRate/{pair}` | Обновить курс пары | `202` |

`{pair}` — 6 латинских букв: базовая валюта + целевая (`USDEUR` = USD → EUR).

Тело `POST /exchangeRates`:

```json
{ "baseCurrencyCode": "EUR", "targetCurrencyCode": "PLN", "rate": 4.25 }
```

Тело `PATCH /exchangeRate/EURPLN`:

```json
{ "rate": 4.30 }
```

Правила: валюты разные, `rate` > 0, обе валюты должны существовать в справочнике.

Ответ:

```json
{
  "baseCurrency":   { "code": "EUR", "name": "Euro",        "symbol": "€" },
  "targetCurrency": { "code": "PLN", "name": "Polish Zloty", "symbol": "zł" },
  "rate": 4.25
}
```

### Конвертация

| Метод | Путь | Описание | Успех |
|---|---|---|---|
| `GET` | `/exchange?from=USD&to=EUR&amount=100` | Конвертация суммы | `200` |

Параметры: `from`, `to` — коды валют (разные), `amount` — положительное число.

Ответ:

```json
{
  "baseCurrency":   { "code": "USD", "name": "US Dollar", "symbol": "$" },
  "targetCurrency": { "code": "EUR", "name": "Euro",      "symbol": "€" },
  "rate": 0.86,
  "amount": 100.00,
  "convertedAmount": 86.00
}
```

Сумма и результат округляются до 2 знаков (`HALF_UP`). Курс определяется так же, как в `GET /exchangeRate/{pair}` (прямой, обратный или кросс через EUR).

### Примеры

```bash
curl http://localhost:8080/api/v1/currencies
curl http://localhost:8080/api/v1/exchangeRate/USDEUR
curl "http://localhost:8080/api/v1/exchange?from=USD&to=GBP&amount=250"

curl -X POST http://localhost:8080/api/v1/exchangeRates \
  -H 'Content-Type: application/json' \
  -d '{"baseCurrencyCode":"EUR","targetCurrencyCode":"PLN","rate":4.25}'

curl -X PATCH http://localhost:8080/api/v1/exchangeRate/EURPLN \
  -H 'Content-Type: application/json' \
  -d '{"rate":4.30}'
```

## Обработка ошибок

`ErrorHandlingFilter` отображает исключения в JSON-ответы:

| Статус | Когда | Формат тела |
|---|---|---|
| `400 Bad Request` | Пропущен/некорректный параметр или путь; нарушение ограничений валидации | `{"error": "..."}` или список нарушений |
| `404 Not Found` | Валюта/курс не найдены; неизвестный эндпоинт | `{"message": "...", "value": ...}` или `{"error": "Incorrect endpoint"}` |
| `406 Not Acceptable` | Некорректный JSON в теле запроса | `{"error": "Incorrect json provided"}` |
| `409 Conflict` | Ошибка БД (например, дубликат или нарушение внешнего ключа) | описание ошибки БД |

## Запуск

### Требования

- JDK 25 (`JAVA_HOME` должен указывать на него)
- Docker (для PostgreSQL и для интеграционных тестов)

### 1. База данных

```bash
export DB_PASSWORD=payment      # пароль обязателен: в application.yaml у него нет значения по умолчанию
docker compose up -d
```

Поднимется PostgreSQL 18 (контейнер `payment-db`, порт `5432`, БД `payment`, пользователь `payment`).
Параметры `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` можно переопределить переменными окружения.

### 2. Приложение

Класс `Main` — точка входа (`src/main/java/krilovs/andrejs/app/Main.java`).

**Через Gradle:**

```bash
./gradlew clean run
```

**Через jar:**

```bash
./gradlew clean jar
java -jar build/libs/currency-converter-1.0-SNAPSHOT.jar
```

Jar «тонкий»: зависимости копируются в `build/libs/lib/` и подключаются через `Class-Path` в манифесте, поэтому jar нужно запускать вместе с папкой `lib` (и переносить их только вместе).

Можно также запустить `Main` из IDE, указав переменную окружения `DB_PASSWORD`.

При старте приложение:
1. применяет миграции Liquibase (создаёт таблицы и загружает начальные валюты и курсы);
2. инициализирует JTA, пул соединений и persistence unit;
3. прогревает кэш валют;
4. запускает Jetty на порту `8080` (контекст `/api/v1`).

Проверка: `curl http://localhost:8080/api/v1/currencies`

## Конфигурация

`src/main/resources/application.yaml`; значения берутся из переменных окружения (`${VAR:default}`).

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SERVER_PORT` | `8080` | Порт HTTP-сервера |
| `DB_JDBC_URL` | `jdbc:postgresql://localhost:5432/payment` | JDBC URL |
| `DB_USERNAME` | `payment` | Пользователь БД |
| `DB_PASSWORD` | — (обязательна) | Пароль БД |
| `DB_MAX_POOL_SIZE` | `10` | Максимальный размер пула соединений |

## Тесты

```bash
./gradlew test    # unit- и интеграционные тесты (интеграционные поднимают PostgreSQL через Testcontainers — нужен Docker)
./gradlew check   # тесты + проверка покрытия JaCoCo (минимум 80% по инструкциям и ветвям)
```

HTML-отчёт о покрытии: `build/jacocoHtml/index.html`.
