# OtkScreen — этап 3 — дизайн

Дополнение к основной спеке `2026-04-16-sputnik-otk-design.md` (§7.2, §8, §9, §10, §12, §13 п.3).
Описывает конкретные границы модулей, типы и поведение для этапа 3.

## 0. Цель этапа 3

Рабочий экран ОТК с обязательным выбором мастера, ручным вводом номеров панелей, списком и кнопкой «Сохранить», которая проходит полный путь до HTTP-запроса. Без NFC, без редактирования комментариев, без экрана логов, без экрана настроек.

В конце этапа: устанавливаемый APK, в котором можно выбрать мастера, добавить пару панелей, нажать «Сохранить» и увидеть красный snackbar про отсутствие связи (реальный webhook в этап 3 не интегрируется — см. §2).

## 1. Что НЕ в scope этапа 3

- NFC-сканирование (этап 4).
- Карандашик, метёлка, копирование в буфер (этап 5).
- LogsScreen и persistent-файл логов (этап 6).
- SettingsScreen и DataStore для URL/пароля (этап 7).
- Вибрация при дубликате (откладывается до этапа 4 вместе с NFC).
- Глобальный `Thread.setDefaultUncaughtExceptionHandler` (этап 8).
- Persistent хранение pendingPanels в JSON-файле (появляется в этапе 6 вместе с логами; см. §4).
- UI/инструментальные тесты (требуют устройства).

## 2. Webhook в этапе 3

Реальный Apps Script webhook **не создаётся на этапе 3**. В коде:

```kotlin
// data/Defaults.kt
const val DEFAULT_WEBHOOK_URL = "https://example.invalid/webhook"
const val DEFAULT_WEBHOOK_PASSWORD = ""
```

Это значит, что при нажатии «Сохранить» `WebhookClient.send` вернёт `NetworkError` из-за невалидного хоста. Цель этапа 3 — проверить UX состояний кнопки и snackbar, а не интеграцию с Google Sheets. Реальная отправка проверяется в этапе 4 или по отдельной задаче, когда webhook будет создан.

## 3. Границы модулей

Этап 3 вводит первый MVVM-слой. Папки:

```
app/src/main/kotlin/ru/sputnik/otk/
  MainActivity.kt
  AppContainer.kt                          — ручной DI-контейнер
  
  ui/screen/otk/
    OtkScreen.kt                           — корневой Composable
    OtkViewModel.kt
    OtkViewModelFactory.kt
    OtkUiState.kt                          — data class + SnackbarEvent
    MasterDropdown.kt
    PanelInput.kt
    PanelList.kt
  
  data/
    Defaults.kt                            — DEFAULT_WEBHOOK_URL и password
    Panel.kt
    WebhookClient.kt
    PanelRepository.kt                     — interface + InMemoryPanelRepository
    ErrorLogRepository.kt                  — interface + InMemoryErrorLogRepository
```

Правила:
- `ui/` не знает про OkHttp, JSON, файлы. Импортирует из `data/` только типы и интерфейсы.
- `data/` не знает про Compose, `Context`, Android-UI.
- `OtkViewModel` не создаёт зависимости сам — получает их через конструктор.

`AppContainer`: один класс, создаётся в `MainActivity.onCreate`, хранит синглтоны `OkHttpClient`, `Json`, `WebhookClient`, `InMemoryPanelRepository`, `InMemoryErrorLogRepository`. Передаётся в `OtkViewModelFactory`.

Почему не Hilt: +KSP, +Gradle-время, +сложность для учебного проекта с тремя синглтонами. Ручной контейнер — 15 строк, читается глазами, в любой момент заменяется на Hilt.

## 4. Модель данных

```kotlin
// data/Panel.kt
data class Panel(
    val id: String,
    val fault: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

// ui/screen/otk/OtkUiState.kt
data class OtkUiState(
    val master: String? = null,
    val masters: List<String> = listOf(
        "Руслан", "Камиль", "Виктор", "Тимур", "Мастер",
    ),
    val panelInput: String = "",
    val pendingPanels: List<Panel> = emptyList(),
    val isSending: Boolean = false,
    val sendProgress: Pair<Int, Int>? = null,
)

sealed class SnackbarEvent {
    abstract val text: String
    data class Info(override val text: String) : SnackbarEvent()
    data class Error(override val text: String) : SnackbarEvent()
    data class Success(override val text: String) : SnackbarEvent()
}
```

Разделение state/events:
- `OtkUiState` — то, что рендерится постоянно (через `StateFlow`).
- `SnackbarEvent` — одноразовые события, идут через `Channel<SnackbarEvent>` (`BUFFERED`), потребляются в `LaunchedEffect` в `OtkScreen`. Канал не даёт snackbar'у всплывать повторно при ротации/рекомпозиции.

Список мастеров в `OtkUiState` — хардкод, источник — раздел §4 основной спеки.

## 5. WebhookClient

```kotlin
class WebhookClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun send(
        url: String,
        password: String,
        panel: Panel,
        master: String,
        date: LocalDate,
    ): Result = withContext(Dispatchers.IO) {
        // сформировать JSON по §9 спеки, отправить POST, разобрать ответ
    }

    sealed class Result {
        data object Ok : Result()
        data object WrongPassword : Result()
        data class ServerError(val reason: String) : Result()
        data class NetworkError(val reason: String) : Result()
    }
}
```

Требования:
- **URL и пароль — аргументы, не поля.** В этапе 7 (SettingsScreen) ничего в клиенте менять не нужно.
- **Возвращает `Result`, не кидает исключения.** Сетевые и серверные ошибки — ожидаемые исходы.
- **Переключение на `Dispatchers.IO` — внутри `send`.** ViewModel вызывает в `viewModelScope.launch` без забот о потоках.
- **Таймаут 30 секунд** — настраивается на `OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30))` в `AppContainer`, не в клиенте.

Формат запроса — по §9 основной спеки:
```json
{"password":"...","panel":"<panel.id>","master":"...","date":"YYYY-MM-DD","fault":"<panel.fault>"}
```

Разбор ответа:
- HTTP 200 + `{"ok":true}` → `Ok`
- HTTP 200 + `{"ok":false,"error":"wrong password"}` → `WrongPassword`
- HTTP 200 + `{"ok":false,"error":<other>}` → `ServerError(other)`
- `IOException` (включая таймаут) → `NetworkError(message)`
- Иные HTTP-коды → `ServerError("HTTP <code>")`

## 6. Репозитории

### 6.1. PanelRepository

```kotlin
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
    private val _panels = MutableStateFlow<List<Panel>>(emptyList())
    override val panels = _panels.asStateFlow()

    override suspend fun add(panel: Panel): AddResult =
        if (_panels.value.any { it.id == panel.id }) AddResult.Duplicate
        else { _panels.update { it + panel }; AddResult.Ok }
    // ...
}
```

- **Защита от дубликатов — в репозитории.** Он единственный владелец списка, проверка рядом с данными.
- **`panels: StateFlow`** — ViewModel подписывается, комбинирует со своим state. В этапе 6 файловая реализация пишет на диск в `add`/`remove`/`clear`, интерфейс и потребители не меняются.
- **`clear()` в интерфейсе сразу** — метёлка будет в этапе 5, добавление метода позже = breaking change; одна строка в интерфейсе + 2 в in-memory реализации сейчас дешевле.

### 6.2. ErrorLogRepository

```kotlin
interface ErrorLogRepository {
    suspend fun log(panelId: String, reason: String)
}

class InMemoryErrorLogRepository : ErrorLogRepository {
    private val entries = mutableListOf<ErrorEntry>()
    override suspend fun log(panelId: String, reason: String) {
        entries += ErrorEntry(panelId, reason, System.currentTimeMillis())
    }
}

internal data class ErrorEntry(
    val panelId: String,
    val reason: String,
    val timestamp: Long,
)
```

Метод чтения логов появится в этапе 6 вместе с LogsScreen. На этапе 3 лог существует, в него пишется, но никто не читает.

## 7. OtkViewModel

```kotlin
class OtkViewModel(
    private val webhookClient: WebhookClient,
    private val panelRepository: PanelRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _localState = MutableStateFlow(OtkUiState())
    private val events = Channel<SnackbarEvent>(Channel.BUFFERED)

    val uiState: StateFlow<OtkUiState> = combine(
        _localState, panelRepository.panels,
    ) { local, panels -> local.copy(pendingPanels = panels) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OtkUiState())

    val snackbarEvents: Flow<SnackbarEvent> = events.receiveAsFlow()

    fun onMasterSelected(master: String)
    fun onPanelInputChanged(text: String)
    fun onAddPanelClicked()
    fun onSaveClicked()
    fun onBackClicked(): Boolean                  // true = можно уходить
}
```

Источники правды:
- `pendingPanels` — источник правды `PanelRepository.panels`, ViewModel только проксирует через `combine`.
- `master`, `panelInput`, `isSending`, `sendProgress` — источник правды `_localState`, изменяются через `_localState.update {}`.
- `masters` (список имён) — константа, живёт в `OtkUiState` по умолчанию, никогда не меняется.

Поведение:

- **`onMasterSelected`** — обновляет `master`. Не пишется в DataStore (закреплено в §15 основной спеки и в памяти: сброс при перезапуске — защита от подписи чужим именем).
- **`onPanelInputChanged`** — обновляет `panelInput` в state.
- **`onAddPanelClicked`**:
  1. Если `master == null` → `SnackbarEvent.Error("Сначала выбери мастера")`, no-op.
  2. Если `panelInput.isBlank()` → no-op без snackbar (кнопка ➕ и так disabled; это защита от Enter на пустом поле).
  3. `panelRepository.add(Panel(id = panelInput.trim()))`:
     - `Ok` → обнулить `panelInput`.
     - `Duplicate` → `SnackbarEvent.Error("Эта панель уже в списке")`.
- **`onSaveClicked`**:
  1. Если `isSending` — early return.
  2. Снять snapshot: `val batch = panelRepository.panels.value`. Если пуст — early return (кнопка в UI disabled).
  3. Установить `isSending=true`, `sendProgress = 0 to batch.size`.
  4. Для каждой панели `batch[i]`:
     - `webhookClient.send(DEFAULT_WEBHOOK_URL, DEFAULT_WEBHOOK_PASSWORD, panel, master, today)`.
     - `Ok` → `panelRepository.remove(panel.id)`, счётчик `ok++`.
     - `WrongPassword` → `errorLogRepository.log(panel.id, "wrong password")`, `break`.
     - `ServerError(r)` → `errorLogRepository.log(panel.id, r)`, продолжаем.
     - `NetworkError(r)` → `errorLogRepository.log(panel.id, r)`, продолжаем.
     - После каждой итерации — `sendProgress = (i+1) to batch.size`.
  5. Финальный snackbar — ровно по этим правилам (приоритет сверху вниз):
     - Пачку прервал `WrongPassword` → `SnackbarEvent.Error("Неверный пароль. Открой настройки.")`.
     - Все `Ok` (`ok == batch.size`) → `SnackbarEvent.Success("Отправлено N из N ✓")`.
     - Иначе → `SnackbarEvent.Error("Отправлено X из Y. Остальное — в логах.")`, где X=ok, Y=batch.size.
  6. `isSending=false`, `sendProgress=null`.
- **`onBackClicked`** — `return !isSending`. В случае `false` — emit `SnackbarEvent.Info("Идёт отправка, подожди")`, экран остаётся. В случае `true` — OtkScreen вызывает `onNavigateBack`.

Дата для запроса: `LocalDate.now(clock)`. `Clock` инжектится, чтобы тесты могли зафризить.

Race conditions: вся логика — в `viewModelScope.launch` на main, плюс флаг `isSending` с early-return. Отдельный mutex не нужен.

## 8. Composable-структура

### 8.1. OtkScreen

Корень: `Scaffold` + `SnackbarHost` + `LaunchedEffect`, который подписывается на `snackbarEvents`.

```kotlin
@Composable
fun OtkScreen(
    onNavigateBack: () -> Unit,
    viewModel: OtkViewModel = viewModel(factory = LocalAppContainer.current.otkViewModelFactory()),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { event ->
            snackbarHostState.showSnackbar(event.text)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { OtkTopBar() },                // «ОТК» + иконки-заглушки для метёлки/копирования
        bottomBar = {
            OtkBottomBar(
                saveEnabled = state.pendingPanels.isNotEmpty() && !state.isSending,
                onSave = viewModel::onSaveClicked,
                onLogs = { /* snackbar «Доступно на следующем этапе» */ },
                onBack = { if (viewModel.onBackClicked()) onNavigateBack() },
            )
        },
    ) { padding ->
        OtkContent(
            state = state,
            padding = padding,
            onMasterSelected = viewModel::onMasterSelected,
            onPanelInputChanged = viewModel::onPanelInputChanged,
            onAddPanel = viewModel::onAddPanelClicked,
        )
    }
}
```

`OtkContent` — внутренний stateless composable, внутри вызывает `MasterDropdown`, `Text("Дата: ...")`, `PanelInput`, подсказку «💡 Сначала выбери мастера» (видна только при `master == null`), `PanelList`.

### 8.2. MasterDropdown

`ExposedDropdownMenuBox` из Material3. Selected = `state.master ?: "— Выбери мастера —"`. Клик по пункту → `onSelected(master)`.

### 8.3. PanelInput

`OutlinedTextField` + `IconButton(Icons.Default.Add)`. Оба `enabled = master != null`. `keyboardActions = KeyboardActions(onDone = { onAdd() })` — Enter добавляет.

### 8.4. PanelList

`LazyColumn` с элементами `state.pendingPanels`. Каждая строка — один `Text(panel.id)`. Иконки ✏ и 💬 не показываем (появятся в этапе 5).

### 8.5. Previews

Каждый stateless composable получает 1–2 `@Preview` (с/без данных, enabled/disabled). `OtkScreen` — preview с фейковым state-объектом, без реального ViewModel.

## 9. AppContainer и DI

```kotlin
// AppContainer.kt
class AppContainer {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build()
    private val json: Json = Json { ignoreUnknownKeys = true }

    val webhookClient = WebhookClient(httpClient, json)
    val panelRepository: PanelRepository = InMemoryPanelRepository()
    val errorLogRepository: ErrorLogRepository = InMemoryErrorLogRepository()

    fun otkViewModelFactory(): ViewModelProvider.Factory =
        OtkViewModelFactory(webhookClient, panelRepository, errorLogRepository)
}

// LocalAppContainer.kt
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided")
}
```

В `MainActivity.onCreate` контейнер создаётся один раз и прокидывается во всё дерево Compose:
```kotlin
val appContainer = remember { AppContainer() }
CompositionLocalProvider(LocalAppContainer provides appContainer) {
    // NavHost и экраны
}
```

`OtkScreen` достаёт фабрику через `LocalAppContainer.current.otkViewModelFactory()`.

## 10. Обработка ошибок (сводка этапа 3)

| Ситуация | Где ловится | UX | В лог |
|----------|-------------|-----|-------|
| Ввод без выбора мастера | `onAddPanelClicked` | `Error("Сначала выбери мастера")` | — |
| Пустой ввод | `onAddPanelClicked` | no-op | — |
| Дубликат | `PanelRepository.add` | `Error("Эта панель уже в списке")` | — |
| `NetworkError` | `WebhookClient.send` | панель остаётся в списке; финальный snackbar по правилам §7 (успех/прерывание/сводный) | да |
| `WrongPassword` | `WebhookClient.send` | `Error("Неверный пароль. Открой настройки.")`, пачка прервана | да |
| `ServerError` | `WebhookClient.send` | панель остаётся в списке; финальный сводный snackbar по правилам §7 | да |
| Save при пустом списке | Compose — disabled | — | — |
| Save во время отправки | `onSaveClicked` — early return | — | — |
| Back во время отправки | `onBackClicked` → false | `Info("Идёт отправка, подожди")` | — |

## 11. Тесты

Напоминание: на dev-машине unit-тесты сейчас не запускаются (`ulimit -u=150`, Kotlin compiler daemon падает). Тесты пишем — они запустятся либо после поднятия лимита, либо в Android Studio на другой машине.

### 11.1. Юнит-тесты

| Юнит | Сценарии |
|------|----------|
| `WebhookClient.send` | `Ok` на 200+`{"ok":true}`; `WrongPassword`; `ServerError`; `NetworkError` (MockWebServer shutdown); корректный JSON в теле; корректный `Content-Type` |
| `InMemoryPanelRepository` | `add` возвращает `Ok` на новой панели; `Duplicate` на совпадении `id`; `remove` удаляет; `clear` опустошает; `panels` StateFlow эмитит новое значение |
| `OtkViewModel.onAddPanelClicked` | Error-snackbar при `master==null`; no-op при пустом вводе; успех очищает `panelInput`; дубликат — нужный snackbar |
| `OtkViewModel.onSaveClicked` | Успешная пачка → `pendingPanels` пуст, Success-snackbar; `WrongPassword` прерывает и логирует; `NetworkError` оставляет панель в списке, логирует; `isSending=true` во время отправки |

### 11.2. Инструменты

- `junit` — уже есть.
- `okhttp-mockwebserver` — уже в `libs.versions.toml`.
- **Новое:** `kotlinx-coroutines-test` — для `runTest` и управления виртуальным временем. Одна строка в `libs.versions.toml`, одна в `app/build.gradle.kts` (`testImplementation`).
- Turbine — **не добавляем** сейчас: `runTest` + ручная подписка на `Flow` покрывает наши кейсы без новой зависимости.

### 11.3. Не в scope

- Compose UI-тесты (`createComposeRule`) — требуют инструментального окружения, откладываем.
- Интеграционный тест с реальным webhook — откладывается до создания webhook.

## 12. Порядок реализации (для будущего плана)

Черновой порядок, детальный план — в отдельном документе (будет создан skill'ом writing-plans):

1. Model: `Panel.kt`, `OtkUiState.kt`, `SnackbarEvent.kt`.
2. Data-слой: `PanelRepository` (интерфейс + in-memory), `ErrorLogRepository` (то же), `Defaults.kt`.
3. `WebhookClient` (с тестами через MockWebServer).
4. `OtkViewModel` + `OtkViewModelFactory` (с тестами через fake-репозитории).
5. `AppContainer`, `LocalAppContainer`, подключение в `MainActivity`.
6. Composable: `MasterDropdown`, `PanelInput`, `PanelList`, `OtkScreen` (переписать нынешнюю заглушку).
7. Ручная проверка чеклиста на APK.

## 13. Ссылки на основную спеку

- §4 Пользователи — список мастеров и правило подмены.
- §7.2 OtkScreen — макет и требования UX.
- §8 Данные и состояние — модель `Panel`, `OtkUiState`, persistence (этап 3 использует только in-memory).
- §9 Сеть и отправка — формат запроса, разбор ответа, последовательная пачка.
- §10 Обработка ошибок — таблица ситуаций.
- §12 Тестирование — общие требования; этап 3 покрывает юнит-тесты только на эти 4 компонента.
- §15 Принятые решения — мастер не сохраняется, список мастеров — хардкод.
