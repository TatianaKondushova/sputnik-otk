# Sputnik OTK — OtkScreen (этап 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать рабочий OtkScreen по спеке `2026-04-20-otkscreen-stage3-design.md` — обязательный выбор мастера, ручной ввод номеров панелей, список, кнопка «Сохранить» с полным путём до HTTP-запроса. Реальный webhook не интегрируется (placeholder URL), задача проверяет UX. Результат этапа — устанавливаемый debug-APK, который проходит чеклист.

**Architecture:** Первый MVVM-слой в проекте. UI (`ui/screen/otk/`) — Compose, stateless компоненты. Логика — `OtkViewModel`. Data-слой (`data/`) — `WebhookClient` (OkHttp + kotlinx.serialization) и два репозитория как интерфейсы с in-memory реализациями (файловые реализации появятся в этапе 6). Зависимости создаются один раз в `AppContainer` и раздаются через `CompositionLocal`.

**Tech Stack:**
- Kotlin 2.0.21
- Jetpack Compose BOM 2024.12.01, Material 3
- AndroidX Lifecycle ViewModel Compose 2.8.7
- OkHttp 4.12.0, kotlinx.serialization-json 1.7.3
- Coroutines (через Compose/Lifecycle), kotlinx-coroutines-test (НОВАЯ зависимость для unit-тестов)
- JUnit 4 + OkHttp MockWebServer
- JDK 17, AGP 8.7.0, Gradle 8.10.2

---

## Контекст машины и стратегия верификации

На dev-машине пользователя:
- `ulimit -u=150` — Kotlin compiler daemon для тестов падает.
- `./gradlew assembleDebug` работает (Kotlin compiler forced in-process, см. `gradle.properties`).
- `./gradlew testDebugUnitTest` НЕ работает (пытается форкать JVM для JUnit runner).

**Стратегия:** TDD-дисциплину сохраняем (тест пишется рядом с реализацией, в той же задаче), но «Run test to verify it fails/passes» заменяется на:
- `./gradlew compileDebugUnitTestKotlin` — проверяет, что тестовый код компилируется (то есть API совпадает и используются правильные сигнатуры).
- `./gradlew assembleDebug` — проверяет, что основной код компилируется.

Запуск самих тестов — отложен: они побегут в Android Studio на другой машине или после поднятия `ulimit`. Это осознанный компромисс, задокументированный в спеке §11.

**Перед КАЖДОЙ командой `./gradlew`** — экспорт окружения (без этого сборка упадёт):

```bash
export JAVA_HOME=/home/tatyana/jdk-17
export ANDROID_HOME=/home/tatyana/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
```

Можно один раз в `~/.bashrc` — но если нет, каждая bash-сессия начинается с этого.

**Флаги Gradle** (тоже каждый раз, упрощают работу на этой машине):
```
./gradlew --no-daemon --no-parallel -Dorg.gradle.workers.max=1 <task>
```

Далее в плане они опущены для краткости — но добавляй их в каждый вызов.

---

## Рабочая директория и ветка

- CWD: `/home/tatyana/workspace/sputnik-otk/`
- Текущая ветка: `feature/otk-screen`
- Запушена на `origin/feature/otk-screen`
- Последний коммит: `c3559a7` (Add OtkScreen stage 3 design spec)

Все задачи ниже работают в этой же ветке.

---

## File Structure

Создаются на этапе 3:

| Путь | Назначение |
|------|------------|
| `app/src/main/kotlin/ru/sputnik/otk/data/Defaults.kt` | Placeholder-константы webhook URL/password |
| `app/src/main/kotlin/ru/sputnik/otk/data/Panel.kt` | Data class `Panel` |
| `app/src/main/kotlin/ru/sputnik/otk/data/WebhookClient.kt` | OkHttp-клиент отправки в webhook |
| `app/src/main/kotlin/ru/sputnik/otk/data/PanelRepository.kt` | Interface + `InMemoryPanelRepository` |
| `app/src/main/kotlin/ru/sputnik/otk/data/ErrorLogRepository.kt` | Interface + `InMemoryErrorLogRepository` + `ErrorEntry` |
| `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkUiState.kt` | `OtkUiState` + sealed `SnackbarEvent` |
| `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModel.kt` | `OtkViewModel` |
| `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelFactory.kt` | `OtkViewModelFactory` |
| `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/MasterDropdown.kt` | Выпадашка мастера |
| `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelInput.kt` | Поле ввода + ➕ |
| `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelList.kt` | Список панелей |
| `app/src/main/kotlin/ru/sputnik/otk/AppContainer.kt` | Ручной DI-контейнер |
| `app/src/main/kotlin/ru/sputnik/otk/LocalAppContainer.kt` | CompositionLocal для AppContainer |
| `app/src/test/kotlin/ru/sputnik/otk/data/WebhookClientTest.kt` | Unit-тест через MockWebServer |
| `app/src/test/kotlin/ru/sputnik/otk/data/InMemoryPanelRepositoryTest.kt` | Unit-тест репозитория |
| `app/src/test/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelTest.kt` | Unit-тест ViewModel |

Переезжает (из `ui/screen/`) в `ui/screen/otk/`:
- `OtkScreen.kt` — содержимое переписывается полностью, файл перемещается.

Модифицируются:
- `gradle/libs.versions.toml` — добавлена `kotlinx-coroutines-test`.
- `app/build.gradle.kts` — новая тестовая зависимость и подключение `kotlinx.serialization.plugin` (если ещё нет).
- `app/src/main/kotlin/ru/sputnik/otk/MainActivity.kt` — оборачивание дерева в `CompositionLocalProvider(LocalAppContainer provides ...)`, перенос импорта `OtkScreen`.

Не создаются (в этом плане): `pending_panels.json`, `error_log.json`, `SettingsScreen`, `LogsScreen`, NFC-обработчик, `Thread.setDefaultUncaughtExceptionHandler`.

---

## Задачи

### Задача 1: Добавить kotlinx-coroutines-test и Serialization-плагин

**Цель:** подготовить проект к написанию тестов и json-сериализации. Это структурный шаг без UI-эффекта — после него проект просто собирается с новыми зависимостями.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Проверить текущее состояние `libs.versions.toml`**

Открой `gradle/libs.versions.toml` и убедись, что секции выглядят так (они уже должны — это сделано в коммите `36bf1da`):
- `[versions]` содержит `okhttp`, `kotlinxSerialization`, `datastore`, `lifecycleViewmodelCompose`.
- `[libraries]` содержит `okhttp`, `okhttp-mockwebserver`, `kotlinx-serialization-json`, `androidx-lifecycle-viewmodel-compose`, `androidx-datastore-preferences`.
- `[plugins]` содержит `kotlin-serialization`.

Если чего-то нет — это значит коммит `36bf1da` не дошёл. Останови выполнение и восстанови.

- [ ] **Step 2: Добавить `kotlinxCoroutinesTest` в `[versions]` и библиотеку в `[libraries]`**

В `gradle/libs.versions.toml` в секции `[versions]` после строки с `kotlinxSerialization` добавь:

```toml
kotlinxCoroutinesTest = "1.9.0"
```

В секции `[libraries]` после строки с `kotlinx-serialization-json` добавь:

```toml
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
```

- [ ] **Step 3: Проверить и, если нужно, добавить kotlin-serialization плагин в `app/build.gradle.kts`**

Открой `app/build.gradle.kts`, посмотри секцию `plugins { ... }`. Там уже должны быть:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
```

Добавь строку (если отсутствует):
```kotlin
    alias(libs.plugins.kotlin.serialization)
```

Получится:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
```

- [ ] **Step 4: Добавить test-зависимости в `app/build.gradle.kts`**

В `dependencies { ... }` найди блок с `testImplementation`. Должно быть что-то вроде:
```kotlin
    testImplementation(libs.junit)
```

(или уже с `okhttp.mockwebserver` — зависит от коммита `36bf1da`). Добавь две строки, чтобы блок стал:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
```

Если какая-то из этих строк уже есть — не дублируй.

- [ ] **Step 5: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`. Если падает — скорее всего опечатка в `libs.versions.toml` (проверь отсутствие лишних запятых и правильность ссылок `version.ref =`).

- [ ] **Step 6: Закоммитить**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Add kotlinx-coroutines-test and enable serialization plugin"
```

---

### Задача 2: Data-слой — Defaults и Panel

**Цель:** добавить две маленькие модели, с которых начинается data-слой. Это чистые data-классы без логики.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/data/Defaults.kt`
- Create: `app/src/main/kotlin/ru/sputnik/otk/data/Panel.kt`

- [ ] **Step 1: Создать `Defaults.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/data/Defaults.kt`

```kotlin
package ru.sputnik.otk.data

/**
 * Placeholder-значения для webhook на этапе 3 (см. спеку §2).
 * Реальные значения зашьются здесь, когда будет создан Apps Script webhook.
 * На этапе 7 появится DataStore-override через SettingsScreen.
 */
const val DEFAULT_WEBHOOK_URL = "https://example.invalid/webhook"
const val DEFAULT_WEBHOOK_PASSWORD = ""
```

- [ ] **Step 2: Создать `Panel.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/data/Panel.kt`

```kotlin
package ru.sputnik.otk.data

data class Panel(
    val id: String,
    val fault: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 3: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/data/
git commit -m "Add Panel data class and webhook placeholder constants"
```

---

### Задача 3: ErrorLogRepository — интерфейс, реализация, тест

**Цель:** завести логгер ошибок. На этапе 3 — просто in-memory, чтобы ViewModel писал туда и не думал куда. Файловая версия — в этапе 6.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/data/ErrorLogRepository.kt`
- Create: `app/src/test/kotlin/ru/sputnik/otk/data/InMemoryErrorLogRepositoryTest.kt`

- [ ] **Step 1: Написать тест**

Путь: `app/src/test/kotlin/ru/sputnik/otk/data/InMemoryErrorLogRepositoryTest.kt`

```kotlin
package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryErrorLogRepositoryTest {

    @Test
    fun `log adds entry with provided panelId and reason`() = runTest {
        val repo = InMemoryErrorLogRepository()
        repo.log("04:AB:CD", "wrong password")

        val entries = repo.snapshot()
        assertEquals(1, entries.size)
        assertEquals("04:AB:CD", entries.first().panelId)
        assertEquals("wrong password", entries.first().reason)
    }

    @Test
    fun `log appends multiple entries in order`() = runTest {
        val repo = InMemoryErrorLogRepository()
        repo.log("p1", "r1")
        repo.log("p2", "r2")

        val entries = repo.snapshot()
        assertEquals(listOf("p1", "p2"), entries.map { it.panelId })
        assertEquals(listOf("r1", "r2"), entries.map { it.reason })
    }
}
```

Заметка: `snapshot()` — internal helper, нужен только тестам (LogsScreen появится в этапе 6 и будет читать через публичный Flow). Объявляем сейчас, чтобы уже было чем тестировать.

- [ ] **Step 2: Создать `ErrorLogRepository.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/data/ErrorLogRepository.kt`

```kotlin
package ru.sputnik.otk.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ErrorEntry(
    val panelId: String,
    val reason: String,
    val timestamp: Long,
)

interface ErrorLogRepository {
    suspend fun log(panelId: String, reason: String)
}

class InMemoryErrorLogRepository(
    private val now: () -> Long = { System.currentTimeMillis() },
) : ErrorLogRepository {

    private val mutex = Mutex()
    private val entries = mutableListOf<ErrorEntry>()

    override suspend fun log(panelId: String, reason: String) {
        mutex.withLock {
            entries += ErrorEntry(panelId, reason, now())
        }
    }

    // internal helper для тестов; будет заменён на публичный Flow в этапе 6
    internal suspend fun snapshot(): List<ErrorEntry> = mutex.withLock { entries.toList() }
}
```

- [ ] **Step 3: Проверить компиляцию main и test**

```bash
./gradlew assembleDebug
./gradlew compileDebugUnitTestKotlin
```

Ожидание обоих: `BUILD SUCCESSFUL`.

Если тест не компилируется из-за `kotlinx-coroutines-test` — проверь, что Задача 1 завершена и зависимость реально в `build.gradle.kts`.

- [ ] **Step 4: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/data/ErrorLogRepository.kt \
        app/src/test/kotlin/ru/sputnik/otk/data/InMemoryErrorLogRepositoryTest.kt
git commit -m "Add ErrorLogRepository with in-memory implementation"
```

---

### Задача 4: PanelRepository — интерфейс, реализация, тест

**Цель:** репозиторий-владелец списка панелей с защитой от дубликатов. Источник правды, к которому подписывается ViewModel через `StateFlow`.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/data/PanelRepository.kt`
- Create: `app/src/test/kotlin/ru/sputnik/otk/data/InMemoryPanelRepositoryTest.kt`

- [ ] **Step 1: Написать тест**

Путь: `app/src/test/kotlin/ru/sputnik/otk/data/InMemoryPanelRepositoryTest.kt`

```kotlin
package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryPanelRepositoryTest {

    @Test
    fun `add returns Ok and exposes panel in state flow`() = runTest {
        val repo = InMemoryPanelRepository()
        val panel = Panel(id = "04:AB", addedAt = 1L)

        val result = repo.add(panel)

        assertEquals(PanelRepository.AddResult.Ok, result)
        assertEquals(listOf(panel), repo.panels.value)
    }

    @Test
    fun `add returns Duplicate when panel with same id already exists`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "04:AB", addedAt = 1L))

        val result = repo.add(Panel(id = "04:AB", addedAt = 2L))

        assertEquals(PanelRepository.AddResult.Duplicate, result)
        assertEquals(1, repo.panels.value.size)
    }

    @Test
    fun `remove deletes panel by id`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "a"))
        repo.add(Panel(id = "b"))

        repo.remove("a")

        assertEquals(listOf("b"), repo.panels.value.map { it.id })
    }

    @Test
    fun `remove is no-op for unknown id`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "a"))

        repo.remove("missing")

        assertEquals(listOf("a"), repo.panels.value.map { it.id })
    }

    @Test
    fun `clear empties the list`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "a"))
        repo.add(Panel(id = "b"))

        repo.clear()

        assertTrue(repo.panels.value.isEmpty())
    }
}
```

- [ ] **Step 2: Создать `PanelRepository.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/data/PanelRepository.kt`

```kotlin
package ru.sputnik.otk.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PanelRepository {
    val panels: StateFlow<List<Panel>>
    suspend fun add(panel: Panel): AddResult
    suspend fun remove(panelId: String)
    suspend fun clear()

    sealed class AddResult {
        data object Ok : AddResult()
        data object Duplicate : AddResult()
    }
}

class InMemoryPanelRepository : PanelRepository {

    private val mutex = Mutex()
    private val _panels = MutableStateFlow<List<Panel>>(emptyList())
    override val panels: StateFlow<List<Panel>> = _panels.asStateFlow()

    override suspend fun add(panel: Panel): PanelRepository.AddResult = mutex.withLock {
        if (_panels.value.any { it.id == panel.id }) {
            PanelRepository.AddResult.Duplicate
        } else {
            _panels.update { it + panel }
            PanelRepository.AddResult.Ok
        }
    }

    override suspend fun remove(panelId: String) = mutex.withLock {
        _panels.update { current -> current.filterNot { it.id == panelId } }
    }

    override suspend fun clear() = mutex.withLock {
        _panels.value = emptyList()
    }
}
```

- [ ] **Step 3: Проверить компиляцию main и test**

```bash
./gradlew assembleDebug
./gradlew compileDebugUnitTestKotlin
```

Ожидание обоих: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/data/PanelRepository.kt \
        app/src/test/kotlin/ru/sputnik/otk/data/InMemoryPanelRepositoryTest.kt
git commit -m "Add PanelRepository with duplicate protection"
```

---

### Задача 5: WebhookClient + тест через MockWebServer

**Цель:** тонкая обёртка над OkHttp, отправляющая POST в webhook и возвращающая `sealed Result`. Все разбираемые исходы покрыты тестами.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/data/WebhookClient.kt`
- Create: `app/src/test/kotlin/ru/sputnik/otk/data/WebhookClientTest.kt`

- [ ] **Step 1: Написать тест**

Путь: `app/src/test/kotlin/ru/sputnik/otk/data/WebhookClientTest.kt`

```kotlin
package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WebhookClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebhookClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = WebhookClient(OkHttpClient(), Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `send returns Ok on 200 with ok=true`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val result = client.send(
            url = server.url("/").toString(),
            password = "secret",
            panel = Panel(id = "04:AB", fault = ""),
            master = "Руслан",
            date = LocalDate.of(2026, 4, 20),
        )

        assertEquals(WebhookClient.Result.Ok, result)
    }

    @Test
    fun `send puts all fields into request body`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        client.send(
            url = server.url("/").toString(),
            password = "secret",
            panel = Panel(id = "04:AB", fault = "скол"),
            master = "Камиль",
            date = LocalDate.of(2026, 4, 20),
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("body must contain password", body.contains("\"password\":\"secret\""))
        assertTrue("body must contain panel", body.contains("\"panel\":\"04:AB\""))
        assertTrue("body must contain master", body.contains("\"master\":\"Камиль\""))
        assertTrue("body must contain date", body.contains("\"date\":\"2026-04-20\""))
        assertTrue("body must contain fault", body.contains("\"fault\":\"скол\""))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
    }

    @Test
    fun `send returns WrongPassword on 200 with ok=false and wrong password`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"wrong password"}"""))

        val result = client.send(
            url = server.url("/").toString(),
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        assertEquals(WebhookClient.Result.WrongPassword, result)
    }

    @Test
    fun `send returns ServerError on 200 with ok=false and other error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"quota exceeded"}"""))

        val result = client.send(
            url = server.url("/").toString(),
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        assertEquals(WebhookClient.Result.ServerError("quota exceeded"), result)
    }

    @Test
    fun `send returns ServerError on non-200 status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client.send(
            url = server.url("/").toString(),
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        val r = result as WebhookClient.Result.ServerError
        assertTrue(r.reason.contains("HTTP 500"))
    }

    @Test
    fun `send returns NetworkError when server is not reachable`() = runTest {
        val unreachableUrl = server.url("/").toString()
        server.shutdown()

        val result = client.send(
            url = unreachableUrl,
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        assertTrue("expected NetworkError, got $result", result is WebhookClient.Result.NetworkError)
    }
}
```

- [ ] **Step 2: Создать `WebhookClient.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/data/WebhookClient.kt`

```kotlin
package ru.sputnik.otk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

open class WebhookClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    sealed class Result {
        data object Ok : Result()
        data object WrongPassword : Result()
        data class ServerError(val reason: String) : Result()
        data class NetworkError(val reason: String) : Result()
    }

    open suspend fun send(
        url: String,
        password: String,
        panel: Panel,
        master: String,
        date: LocalDate,
    ): Result = withContext(Dispatchers.IO) {
        val body = RequestPayload(
            password = password,
            panel = panel.id,
            master = master,
            date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            fault = panel.fault,
        )
        val requestBody = json.encodeToString(RequestPayload.serializer(), body)
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.ServerError("HTTP ${response.code}")
                }
                val responseBody = response.body?.string().orEmpty()
                parseResponse(responseBody)
            }
        } catch (e: IOException) {
            Result.NetworkError(e.message ?: "network error")
        }
    }

    private fun parseResponse(body: String): Result = try {
        val parsed = json.decodeFromString(ResponsePayload.serializer(), body)
        when {
            parsed.ok -> Result.Ok
            parsed.error == "wrong password" -> Result.WrongPassword
            else -> Result.ServerError(parsed.error ?: "unknown error")
        }
    } catch (e: Exception) {
        Result.ServerError("malformed response: ${e.message}")
    }

    @Serializable
    private data class RequestPayload(
        val password: String,
        val panel: String,
        val master: String,
        val date: String,
        val fault: String,
    )

    @Serializable
    private data class ResponsePayload(
        val ok: Boolean,
        val error: String? = null,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
```

- [ ] **Step 3: Проверить компиляцию main и test**

```bash
./gradlew assembleDebug
./gradlew compileDebugUnitTestKotlin
```

Ожидание обоих: `BUILD SUCCESSFUL`.

Частые ошибки:
- «Serializer was not found for class RequestPayload» — значит, плагин `kotlin-serialization` не включён (Задача 1, Step 3).
- «Unresolved reference: mockwebserver» — значит, `testImplementation(libs.okhttp.mockwebserver)` не в `build.gradle.kts`.

- [ ] **Step 4: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/data/WebhookClient.kt \
        app/src/test/kotlin/ru/sputnik/otk/data/WebhookClientTest.kt
git commit -m "Add WebhookClient with MockWebServer tests"
```

---

### Задача 6: OtkUiState + SnackbarEvent

**Цель:** data-классы состояния. Отдельная короткая задача, чтобы ViewModel в следующей задаче имела готовые типы.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkUiState.kt`

- [ ] **Step 1: Создать `OtkUiState.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkUiState.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import ru.sputnik.otk.data.Panel

data class OtkUiState(
    val master: String? = null,
    val masters: List<String> = DEFAULT_MASTERS,
    val panelInput: String = "",
    val pendingPanels: List<Panel> = emptyList(),
    val isSending: Boolean = false,
    val sendProgress: Pair<Int, Int>? = null,
) {
    companion object {
        val DEFAULT_MASTERS: List<String> = listOf(
            "Руслан", "Камиль", "Виктор", "Тимур", "Мастер",
        )
    }
}

sealed class SnackbarEvent {
    abstract val text: String
    data class Info(override val text: String) : SnackbarEvent()
    data class Error(override val text: String) : SnackbarEvent()
    data class Success(override val text: String) : SnackbarEvent()
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkUiState.kt
git commit -m "Add OtkUiState and SnackbarEvent"
```

---

### Задача 7: OtkViewModel + OtkViewModelFactory + тест

**Цель:** бизнес-логика экрана. Самая большая задача в плане. Тест покрывает 4 блока: выбор мастера, добавление панели, отправка пачки, обработка ошибок.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModel.kt`
- Create: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelFactory.kt`
- Create: `app/src/test/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelTest.kt`

- [ ] **Step 1: Написать тест**

Путь: `app/src/test/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelTest.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.sputnik.otk.data.ErrorEntry
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.InMemoryPanelRepository
import ru.sputnik.otk.data.Panel
import ru.sputnik.otk.data.WebhookClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class OtkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-20T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeErrorLog : ErrorLogRepository {
        val entries = mutableListOf<ErrorEntry>()
        override suspend fun log(panelId: String, reason: String) {
            entries += ErrorEntry(panelId, reason, 0L)
        }
    }

    private class FakeWebhookClient(
        private val responses: ArrayDeque<WebhookClient.Result>,
    ) : WebhookClient(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json) {
        val calls = mutableListOf<Panel>()
        override suspend fun send(
            url: String,
            password: String,
            panel: Panel,
            master: String,
            date: java.time.LocalDate,
        ): Result {
            calls += panel
            return responses.removeFirst()
        }
    }

    private fun buildVm(
        webhook: FakeWebhookClient = FakeWebhookClient(ArrayDeque()),
        panels: InMemoryPanelRepository = InMemoryPanelRepository(),
        errors: FakeErrorLog = FakeErrorLog(),
    ): OtkViewModel = OtkViewModel(
        webhookClient = webhook,
        panelRepository = panels,
        errorLogRepository = errors,
        clock = fixedClock,
    )

    @Test
    fun `onAddPanelClicked emits error snackbar when master not selected`() = runTest(dispatcher) {
        val vm = buildVm()
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }

        vm.onPanelInputChanged("p1")
        vm.onAddPanelClicked()
        advanceUntilIdle()

        val err = events.first() as SnackbarEvent.Error
        assertTrue(err.text.contains("мастер"))
        assertTrue(vm.uiState.value.pendingPanels.isEmpty())
        job.cancel()
    }

    @Test
    fun `onAddPanelClicked ignores blank input`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.onMasterSelected("Руслан")
        vm.onPanelInputChanged("   ")
        vm.onAddPanelClicked()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingPanels.isEmpty())
    }

    @Test
    fun `onAddPanelClicked adds panel and clears input on success`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.onMasterSelected("Руслан")
        vm.onPanelInputChanged("04:AB")
        vm.onAddPanelClicked()
        advanceUntilIdle()

        assertEquals(listOf("04:AB"), vm.uiState.value.pendingPanels.map { it.id })
        assertEquals("", vm.uiState.value.panelInput)
    }

    @Test
    fun `onAddPanelClicked emits duplicate snackbar on repeated id`() = runTest(dispatcher) {
        val vm = buildVm()
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }

        vm.onMasterSelected("Руслан")
        vm.onPanelInputChanged("04:AB"); vm.onAddPanelClicked(); advanceUntilIdle()
        vm.onPanelInputChanged("04:AB"); vm.onAddPanelClicked(); advanceUntilIdle()

        val err = events.last() as SnackbarEvent.Error
        assertTrue(err.text.contains("уже в списке"))
        assertEquals(1, vm.uiState.value.pendingPanels.size)
        job.cancel()
    }

    @Test
    fun `onSaveClicked sends all panels and removes them on Ok`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also {
            it.add(Panel("a")); it.add(Panel("b"))
        }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(
            WebhookClient.Result.Ok, WebhookClient.Result.Ok,
        )))
        val vm = buildVm(webhook = webhook, panels = panels)
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), webhook.calls.map { it.id })
        assertTrue(vm.uiState.value.pendingPanels.isEmpty())
        assertEquals(false, vm.uiState.value.isSending)
        assertNull(vm.uiState.value.sendProgress)
    }

    @Test
    fun `onSaveClicked emits success snackbar when all ok`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also { it.add(Panel("a")) }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(WebhookClient.Result.Ok)))
        val vm = buildVm(webhook = webhook, panels = panels)
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }

        vm.onMasterSelected("Руслан")
        vm.onSaveClicked()
        advanceUntilIdle()

        assertTrue(events.any { it is SnackbarEvent.Success })
        job.cancel()
    }

    @Test
    fun `onSaveClicked aborts batch on WrongPassword and logs it`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also {
            it.add(Panel("a")); it.add(Panel("b"))
        }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(
            WebhookClient.Result.WrongPassword, WebhookClient.Result.Ok,
        )))
        val errors = FakeErrorLog()
        val vm = buildVm(webhook = webhook, panels = panels, errors = errors)
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(listOf("a"), webhook.calls.map { it.id })            // батч прерван
        assertEquals(listOf("a"), errors.entries.map { it.panelId })
        assertEquals("wrong password", errors.entries.single().reason)
        assertTrue(events.any { it is SnackbarEvent.Error && it.text.contains("пароль") })
        assertEquals(2, vm.uiState.value.pendingPanels.size)               // обе на месте
        job.cancel()
    }

    @Test
    fun `onSaveClicked keeps panel on NetworkError and logs it`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also { it.add(Panel("a")) }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(
            WebhookClient.Result.NetworkError("timeout"),
        )))
        val errors = FakeErrorLog()
        val vm = buildVm(webhook = webhook, panels = panels, errors = errors)
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.uiState.value.pendingPanels.map { it.id })
        assertEquals("timeout", errors.entries.single().reason)
    }

    @Test
    fun `onBackClicked returns false while sending`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also { it.add(Panel("a")) }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(WebhookClient.Result.Ok)))
        val vm = buildVm(webhook = webhook, panels = panels)
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        // isSending переключается до suspend в webhookClient.send, поэтому
        // проверяем прямо сейчас, не дожидаясь advanceUntilIdle
        assertTrue(vm.uiState.value.isSending)
        assertEquals(false, vm.onBackClicked())

        advanceUntilIdle()
        assertEquals(true, vm.onBackClicked())
    }
}
```

Заметка про `FakeWebhookClient`: наследуется от `WebhookClient`, потому что `OtkViewModel` принимает именно конкретный класс. Если захочется извлечь интерфейс `WebhookClient` — это отдельный рефакторинг, не в scope этапа 3.

- [ ] **Step 2: Создать `OtkViewModelFactory.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelFactory.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.WebhookClient

class OtkViewModelFactory(
    private val webhookClient: WebhookClient,
    private val panelRepository: PanelRepository,
    private val errorLogRepository: ErrorLogRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == OtkViewModel::class.java) {
            "OtkViewModelFactory can create only OtkViewModel"
        }
        return OtkViewModel(webhookClient, panelRepository, errorLogRepository) as T
    }
}
```

- [ ] **Step 3: Создать `OtkViewModel.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModel.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.sputnik.otk.data.DEFAULT_WEBHOOK_PASSWORD
import ru.sputnik.otk.data.DEFAULT_WEBHOOK_URL
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.Panel
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.WebhookClient
import java.time.Clock
import java.time.LocalDate

class OtkViewModel(
    private val webhookClient: WebhookClient,
    private val panelRepository: PanelRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val localState = MutableStateFlow(OtkUiState())
    private val events = Channel<SnackbarEvent>(Channel.BUFFERED)

    val uiState: StateFlow<OtkUiState> = combine(
        localState,
        panelRepository.panels,
    ) { local, panels -> local.copy(pendingPanels = panels) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OtkUiState())

    val snackbarEvents: Flow<SnackbarEvent> = events.receiveAsFlow()

    fun onMasterSelected(master: String) {
        localState.update { it.copy(master = master) }
    }

    fun onPanelInputChanged(text: String) {
        localState.update { it.copy(panelInput = text) }
    }

    fun onAddPanelClicked() {
        val state = localState.value
        val master = state.master
        if (master == null) {
            viewModelScope.launch {
                events.send(SnackbarEvent.Error("Сначала выбери мастера"))
            }
            return
        }
        val input = state.panelInput.trim()
        if (input.isEmpty()) return

        viewModelScope.launch {
            when (panelRepository.add(Panel(id = input))) {
                PanelRepository.AddResult.Ok -> {
                    localState.update { it.copy(panelInput = "") }
                }
                PanelRepository.AddResult.Duplicate -> {
                    events.send(SnackbarEvent.Error("Эта панель уже в списке"))
                }
            }
        }
    }

    fun onSaveClicked() {
        val snapshot = uiState.value
        if (snapshot.isSending) return
        val master = snapshot.master ?: return
        val batch = panelRepository.panels.value
        if (batch.isEmpty()) return

        viewModelScope.launch {
            localState.update { it.copy(isSending = true, sendProgress = 0 to batch.size) }

            var ok = 0
            var abortedByWrongPassword = false
            val today = LocalDate.now(clock)

            for ((index, panel) in batch.withIndex()) {
                val result = webhookClient.send(
                    url = DEFAULT_WEBHOOK_URL,
                    password = DEFAULT_WEBHOOK_PASSWORD,
                    panel = panel,
                    master = master,
                    date = today,
                )
                when (result) {
                    WebhookClient.Result.Ok -> {
                        panelRepository.remove(panel.id)
                        ok++
                    }
                    WebhookClient.Result.WrongPassword -> {
                        errorLogRepository.log(panel.id, "wrong password")
                        abortedByWrongPassword = true
                    }
                    is WebhookClient.Result.ServerError -> {
                        errorLogRepository.log(panel.id, result.reason)
                    }
                    is WebhookClient.Result.NetworkError -> {
                        errorLogRepository.log(panel.id, result.reason)
                    }
                }
                localState.update { it.copy(sendProgress = (index + 1) to batch.size) }
                if (abortedByWrongPassword) break
            }

            val finalEvent: SnackbarEvent = when {
                abortedByWrongPassword ->
                    SnackbarEvent.Error("Неверный пароль. Открой настройки.")
                ok == batch.size ->
                    SnackbarEvent.Success("Отправлено $ok из ${batch.size} ✓")
                else ->
                    SnackbarEvent.Error("Отправлено $ok из ${batch.size}. Остальное — в логах.")
            }
            events.send(finalEvent)

            localState.update { it.copy(isSending = false, sendProgress = null) }
        }
    }

    fun onBackClicked(): Boolean {
        if (uiState.value.isSending) {
            viewModelScope.launch {
                events.send(SnackbarEvent.Info("Идёт отправка, подожди"))
            }
            return false
        }
        return true
    }
}
```

Заметка про `isSending` в тесте `onBackClicked returns false while sending`: установка `isSending=true` происходит СИНХРОННО перед первым suspend-вызовом `webhookClient.send`. В тесте с `StandardTestDispatcher` корутина из `viewModelScope.launch` не стартует сразу — нужно проверить. Если тест упадёт — изменить на `vm.onSaveClicked(); advanceUntilIdle()` и проверять `isSending` в момент, когда в `FakeWebhookClient.send` блокируется через `suspendCancellableCoroutine`. Но это усложнение; сначала попробовать как написано.

- [ ] **Step 4: Проверить компиляцию main и test**

```bash
./gradlew assembleDebug
./gradlew compileDebugUnitTestKotlin
```

Ожидание обоих: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModel.kt \
        app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelFactory.kt \
        app/src/test/kotlin/ru/sputnik/otk/ui/screen/otk/OtkViewModelTest.kt
git commit -m "Add OtkViewModel with factory and unit tests"
```

---

### Задача 8: AppContainer + LocalAppContainer

**Цель:** собрать все синглтоны в одном месте и дать Compose-дереву доступ к контейнеру через `CompositionLocal`. Интеграцию в `MainActivity` делаем в следующей задаче.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/AppContainer.kt`
- Create: `app/src/main/kotlin/ru/sputnik/otk/LocalAppContainer.kt`

- [ ] **Step 1: Создать `AppContainer.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/AppContainer.kt`

```kotlin
package ru.sputnik.otk

import androidx.lifecycle.ViewModelProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.InMemoryErrorLogRepository
import ru.sputnik.otk.data.InMemoryPanelRepository
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.WebhookClient
import ru.sputnik.otk.ui.screen.otk.OtkViewModelFactory
import java.time.Duration

class AppContainer {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    val webhookClient: WebhookClient = WebhookClient(httpClient, json)
    val panelRepository: PanelRepository = InMemoryPanelRepository()
    val errorLogRepository: ErrorLogRepository = InMemoryErrorLogRepository()

    fun otkViewModelFactory(): ViewModelProvider.Factory =
        OtkViewModelFactory(webhookClient, panelRepository, errorLogRepository)
}
```

- [ ] **Step 2: Создать `LocalAppContainer.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/LocalAppContainer.kt`

```kotlin
package ru.sputnik.otk

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided. Wrap your content in CompositionLocalProvider(LocalAppContainer provides ...).")
}
```

- [ ] **Step 3: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/AppContainer.kt \
        app/src/main/kotlin/ru/sputnik/otk/LocalAppContainer.kt
git commit -m "Add AppContainer for manual DI"
```

---

### Задача 9: MasterDropdown composable

**Цель:** визуальная выпадашка на Material3 `ExposedDropdownMenuBox`. Полностью stateless.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/MasterDropdown.kt`

- [ ] **Step 1: Создать `MasterDropdown.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/MasterDropdown.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDropdown(
    selected: String?,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected ?: "— Выбери мастера —",
            onValueChange = {},
            readOnly = true,
            label = { Text("Мастер") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun MasterDropdownEmptyPreview() {
    SputnikOtkTheme {
        MasterDropdown(selected = null, options = OtkUiState.DEFAULT_MASTERS, onSelected = {})
    }
}

@Preview
@Composable
private fun MasterDropdownSelectedPreview() {
    SputnikOtkTheme {
        MasterDropdown(selected = "Руслан", options = OtkUiState.DEFAULT_MASTERS, onSelected = {})
    }
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/MasterDropdown.kt
git commit -m "Add MasterDropdown composable with previews"
```

---

### Задача 10: PanelInput composable

**Цель:** поле ввода номера + кнопка ➕. Блокируется, если мастер не выбран.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelInput.kt`

- [ ] **Step 1: Создать `PanelInput.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelInput.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

@Composable
fun PanelInput(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text("Номер панели") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onAdd,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить панель")
        }
    }
}

@Preview
@Composable
private fun PanelInputEnabledPreview() {
    SputnikOtkTheme {
        PanelInput(value = "04:AB", enabled = true, onValueChange = {}, onAdd = {})
    }
}

@Preview
@Composable
private fun PanelInputDisabledPreview() {
    SputnikOtkTheme {
        PanelInput(value = "", enabled = false, onValueChange = {}, onAdd = {})
    }
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelInput.kt
git commit -m "Add PanelInput composable with enter-to-add"
```

---

### Задача 11: PanelList composable

**Цель:** `LazyColumn` со списком номеров. Минимальный — иконки редактирования/комментария появятся в этапе 5.

**Files:**
- Create: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelList.kt`

- [ ] **Step 1: Создать `PanelList.kt`**

Путь: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelList.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.sputnik.otk.data.Panel
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

@Composable
fun PanelList(
    panels: List<Panel>,
    modifier: Modifier = Modifier,
) {
    if (panels.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Пока пусто. Добавь первую панель.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(panels, key = { it.id }) { panel ->
            Text(
                text = panel.id,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun PanelListEmptyPreview() {
    SputnikOtkTheme { PanelList(panels = emptyList()) }
}

@Preview
@Composable
private fun PanelListFilledPreview() {
    SputnikOtkTheme {
        PanelList(panels = listOf(
            Panel("04:AB:CD"),
            Panel("04:EF:12"),
            Panel("04:34:56"),
        ))
    }
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Закоммитить**

```bash
git add app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/PanelList.kt
git commit -m "Add PanelList composable"
```

---

### Задача 12: Переписать OtkScreen + подключить в MainActivity

**Цель:** собрать экран из компонентов предыдущих задач, подключить ViewModel, пробросить `AppContainer` через `CompositionLocal`.

**Files:**
- Delete: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/OtkScreen.kt` (старая заглушка)
- Create: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkScreen.kt`
- Modify: `app/src/main/kotlin/ru/sputnik/otk/MainActivity.kt`

- [ ] **Step 1: Удалить старую заглушку**

```bash
git rm app/src/main/kotlin/ru/sputnik/otk/ui/screen/OtkScreen.kt
```

- [ ] **Step 2: Создать новый `OtkScreen.kt` в `otk/` подпапке**

Путь: `app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkScreen.kt`

```kotlin
package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.sputnik.otk.LocalAppContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtkScreen(
    onNavigateBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: OtkViewModel = viewModel(factory = container.otkViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { event ->
            snackbarHostState.showSnackbar(event.text)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("ОТК") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            MasterDropdown(
                selected = state.master,
                options = state.masters,
                onSelected = viewModel::onMasterSelected,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Дата: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            PanelInput(
                value = state.panelInput,
                enabled = state.master != null,
                onValueChange = viewModel::onPanelInputChanged,
                onAdd = viewModel::onAddPanelClicked,
            )

            if (state.master == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "💡 Сначала выбери мастера",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Отсканировано (${state.pendingPanels.size}):",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            PanelList(panels = state.pendingPanels)

            Spacer(Modifier.height(24.dp))
            OtkBottomBar(
                saveEnabled = state.pendingPanels.isNotEmpty() && !state.isSending,
                isSending = state.isSending,
                sendProgress = state.sendProgress,
                onSave = viewModel::onSaveClicked,
                onLogs = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Доступно на следующем этапе")
                    }
                },
                onBack = {
                    if (viewModel.onBackClicked()) onNavigateBack()
                },
            )
        }
    }
}

@Composable
private fun OtkBottomBar(
    saveEnabled: Boolean,
    isSending: Boolean,
    sendProgress: Pair<Int, Int>?,
    onSave: () -> Unit,
    onLogs: () -> Unit,
    onBack: () -> Unit,
) {
    Column {
        Button(onClick = onSave, enabled = saveEnabled) {
            Text(if (isSending && sendProgress != null)
                "Отправка ${sendProgress.first} из ${sendProgress.second}"
            else "Сохранить")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLogs) { Text("Логи") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

// Превью всего OtkScreen не делаем — оно упало бы на LocalAppContainer.current.
// Реальные previews живут в MasterDropdown/PanelInput/PanelList, а полный
// экран проверяем на устройстве.
```

Заметка: честный preview всего OtkScreen требует пробрасывать `LocalAppContainer` с фейком — это громоздко. Previews для компонентов у нас уже есть в Задачах 9–11, этого достаточно.

- [ ] **Step 3: Обновить `MainActivity.kt`**

Открой `app/src/main/kotlin/ru/sputnik/otk/MainActivity.kt` и замени весь файл на:

```kotlin
package ru.sputnik.otk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.sputnik.otk.ui.screen.HomeScreen
import ru.sputnik.otk.ui.screen.otk.OtkScreen
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SputnikOtkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val appContainer = remember { AppContainer() }
                    CompositionLocalProvider(LocalAppContainer provides appContainer) {
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = "home") {
                            composable("home") {
                                HomeScreen(
                                    onNavigateToOtk = { navController.navigate("otk") },
                                    onLongPressTitle = { /* TODO: SettingsScreen */ },
                                )
                            }
                            composable("otk") {
                                OtkScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Ключевые изменения vs текущий файл:
- Импорт `OtkScreen` идёт теперь из `ru.sputnik.otk.ui.screen.otk` (не `ui.screen`).
- Добавлен `CompositionLocalProvider(LocalAppContainer provides appContainer)`, оборачивающий `NavHost`.
- `AppContainer` создаётся один раз через `remember`, чтобы переживал рекомпозицию, но умирал вместе с Activity.

- [ ] **Step 4: Проверить сборку**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`. Если падает на «Unresolved reference: OtkScreen» — проверь импорты в `MainActivity.kt` и что старый `ui/screen/OtkScreen.kt` действительно удалён через `git rm`.

- [ ] **Step 5: Проверить, что тесты тоже компилируются**

```bash
./gradlew compileDebugUnitTestKotlin
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Закоммитить**

Удаление старого файла уже staged в Step 1, новые — добавляем сейчас.

```bash
git add app/src/main/kotlin/ru/sputnik/otk/ui/screen/otk/OtkScreen.kt \
        app/src/main/kotlin/ru/sputnik/otk/MainActivity.kt
git commit -m "Wire up OtkScreen with MasterDropdown, PanelInput and PanelList"
```

---

### Задача 13: Собрать APK и проверить на устройстве

**Цель:** собрать debug-APK, установить на Huawei P30 Lite, пройти чеклист этапа 3.

**Files:** ничего не создаётся.

- [ ] **Step 1: Собрать debug-APK**

```bash
./gradlew assembleDebug
```

Ожидание: `BUILD SUCCESSFUL`. APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Передать APK на телефон**

Любым способом, которым пользуешься (обычно telegram saved messages или просто adb через USB). APK не подписан release-ключом — это debug-подпись, устанавливается без проблем поверх предыдущей debug-версии.

- [ ] **Step 3: Установить APK и запустить приложение**

На телефоне: открыть APK, разрешить установку из неизвестных источников если спросит, установить, запустить.

- [ ] **Step 4: Пройти чеклист этапа 3**

Отмечай галочкой то, что работает:

- [ ] Приложение запускается, открывается HomeScreen с плиткой «ОТК».
- [ ] Клик по плитке → открывается OtkScreen.
- [ ] Выпадашка показывает `— Выбери мастера —`, список из 5 имён (Руслан, Камиль, Виктор, Тимур, Мастер).
- [ ] Поле «Номер панели» и кнопка ➕ — disabled при незаполненном мастере.
- [ ] Попытка вводить в поле без мастера — поле не активируется (ввод невозможен).
- [ ] Выбор мастера из списка → поле ввода и ➕ становятся активны, сообщение «💡 Сначала выбери мастера» исчезает.
- [ ] Ввод номера + нажатие ➕ → панель появляется в списке, поле ввода очищается.
- [ ] Ввод номера + Enter на клавиатуре → то же самое (Enter добавляет).
- [ ] Ввод того же номера ещё раз → snackbar «Эта панель уже в списке», список не меняется.
- [ ] Кнопка «Сохранить» disabled при пустом списке.
- [ ] Добавить 2–3 панели → «Сохранить» активна → нажать → красный snackbar про ошибку отправки (ожидаемо: webhook placeholder-URL).
- [ ] Панели остались в списке после неуспешной отправки.
- [ ] Кнопка «Логи» → snackbar «Доступно на следующем этапе».
- [ ] Кнопка «Назад» → возврат на HomeScreen.
- [ ] Закрыть приложение (swipe из недавних) → открыть снова → выпадашка снова `— Выбери мастера —`, список панелей пуст (in-memory репозиторий не сохраняет).

- [ ] **Step 5: Коммит с тегом версии (если всё прошло)**

Обновить версию в `app/build.gradle.kts` — найти строку `versionName = "0.2.0"` и заменить на `"0.3.0"`, `versionCode = 2` → `versionCode = 3`.

```bash
git add app/build.gradle.kts
git commit -m "Bump version to 0.3.0"
git tag v0.3.0
```

- [ ] **Step 6: Push ветки и тега**

```bash
git push origin feature/otk-screen
git push origin v0.3.0
```

- [ ] **Step 7: Создать GitHub Release (опционально)**

Если используем `gh`:
```bash
gh release create v0.3.0 app/build/outputs/apk/debug/app-debug.apk \
    --title "v0.3.0 — OtkScreen (этап 3)" \
    --notes "Выпадашка мастера, ручной ввод панелей, кнопка «Сохранить» (webhook пока placeholder)."
```

- [ ] **Step 8: Мердж в `main`**

```bash
git checkout main
git merge --no-ff feature/otk-screen
git push origin main
```

---

## Self-review — покрытие спеки

Сопоставление требований `2026-04-20-otkscreen-stage3-design.md` с задачами:

| Требование спеки | Задача |
|------------------|--------|
| §2 Webhook placeholder URL | Задача 2 (`Defaults.kt`) |
| §3 Границы модулей `ui/`, `data/` | Задачи 2–12 (структура папок) |
| §4 `Panel`, `OtkUiState`, `SnackbarEvent` | Задачи 2, 6 |
| §5 `WebhookClient` с sealed `Result` | Задача 5 |
| §6.1 `PanelRepository` + duplicate protection | Задача 4 |
| §6.2 `ErrorLogRepository` | Задача 3 |
| §7 `OtkViewModel` (все 5 методов) | Задача 7 |
| §7 combine(local, repo.panels) | Задача 7 (реализовано в теле ViewModel) |
| §7 финальный snackbar по правилам (приоритет WrongPassword → Success → сводный) | Задача 7 (тесты + реализация) |
| §8 Composable-структура | Задачи 9–12 |
| §8.1 SnackbarHost + LaunchedEffect | Задача 12 |
| §9 AppContainer + LocalAppContainer | Задачи 8, 12 |
| §10 Сводная таблица ошибок | Задачи 5, 7 (покрыто тестами) |
| §11 Юнит-тесты на 4 компонента | Задачи 3, 4, 5, 7 |
| §11 `kotlinx-coroutines-test` | Задача 1 |
| §12 Порядок реализации | Задачи идут в согласованном порядке |

Всё покрыто.

## Self-review — типы и сигнатуры

- `PanelRepository.AddResult.Ok` / `Duplicate` — одинаково используется в тесте (Задача 4) и во ViewModel (Задача 7).
- `WebhookClient.Result.Ok` / `WrongPassword` / `ServerError` / `NetworkError` — сигнатуры совпадают в клиенте (Задача 5), тесте (Задача 5) и ViewModel (Задача 7).
- `SnackbarEvent.Info` / `Error` / `Success` — используется единообразно в ViewModel, OtkScreen, тестах.
- `OtkUiState.DEFAULT_MASTERS` — определён в Задаче 6, используется в `MasterDropdown` previews (Задача 9).
- `AppContainer.otkViewModelFactory()` — определён в Задаче 8, вызывается в Задаче 12.
- `OtkViewModel.onBackClicked()` возвращает `Boolean` — в Задаче 7 и Задаче 12 используется одинаково.

Коллизий имён не найдено.

---

## После выполнения плана

Обновить заметку в памяти (`project_sputnik_otk_bootstrap.md`):
- пометить этап 3 как «готов, вмержен в main YYYY-MM-DD»
- следующий этап — 4 (NFC-сканирование), начать в ветке `feature/nfc`.

Следующая спека (`2026-XX-XX-nfc-design.md`) и следующий план будут отдельными документами.
