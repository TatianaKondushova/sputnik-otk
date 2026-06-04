package ru.sputnik.otk.ui.screen.otk

import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.flow.first
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.Panel
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.SettingsStore
import ru.sputnik.otk.data.WebhookClient
import java.time.Clock
import java.time.LocalDate

class OtkViewModel(
    private val webhookClient: WebhookClient,
    private val panelRepository: PanelRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    enum class VibrateType { SUCCESS, DUPLICATE, ERROR }

    /** Вибрация. Вызывается из Screen. */
    var onVibrate: ((VibrateType) -> Unit)? = null

    /** Toast для диагностики. */
    var showToast: ((String) -> Unit)? = null

    private val tag = "OtkViewModel"

    private val localState = MutableStateFlow(OtkUiState())

    init {
        // Загружаем ТОЛЬКО список мастеров, НЕ выбранного мастера
        // (по спеке мастер сбрасывается при каждом запуске)
        viewModelScope.launch {
            try {
                settingsStore.masters.collect { masters ->
                    localState.update { current ->
                        current.copy(masters = masters)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка загрузки списка мастеров", e)
            }
        }
    }

    private val events = Channel<SnackbarEvent>(Channel.BUFFERED)

    val uiState: StateFlow<OtkUiState> = combine(
        localState,
        panelRepository.panels,
    ) { local, panels -> local.copy(pendingPanels = panels) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OtkUiState())

    val snackbarEvents: Flow<SnackbarEvent> = events.receiveAsFlow()

    fun onMasterSelected(master: String) {
        localState.update { it.copy(master = master) }
        // НЕ сохраняем в DataStore — мастер только на текущую сессию
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
            try {
                when (panelRepository.add(Panel(id = input))) {
                    PanelRepository.AddResult.Ok -> {
                        localState.update { it.copy(panelInput = "") }
                    }
                    PanelRepository.AddResult.Duplicate -> {
                        onVibrate?.invoke(VibrateType.DUPLICATE)
                        events.send(SnackbarEvent.Error("Эта панель уже в списке"))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка добавления панели вручную", e)
                events.send(SnackbarEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun onNfcScanned(panelId: String) {
        val master = localState.value.master
        showToast?.invoke("NFC: $panelId | мастер=${master ?: "НЕ ВЫБРАН"}")
        if (master == null) {
            viewModelScope.launch {
                onVibrate?.invoke(VibrateType.ERROR)
                events.send(SnackbarEvent.Error("Сначала выбери мастера"))
            }
            return
        }
        val id = panelId.trim()
        if (id.isEmpty()) return

        viewModelScope.launch {
            try {
                when (panelRepository.add(Panel(id = id))) {
                    PanelRepository.AddResult.Ok -> {
                        onVibrate?.invoke(VibrateType.SUCCESS)
                        events.send(SnackbarEvent.Success("Панель $id добавлена"))
                    }
                    PanelRepository.AddResult.Duplicate -> {
                        onVibrate?.invoke(VibrateType.DUPLICATE)
                        events.send(SnackbarEvent.Error("Эта панель уже в списке"))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка добавления панели по NFC", e)
                onVibrate?.invoke(VibrateType.ERROR)
                events.send(SnackbarEvent.Error("Ошибка NFC: ${e.message}"))
            }
        }
    }

    fun onSaveClicked() {
        val snapshot = localState.value
        if (snapshot.isSending) return
        val master = snapshot.master ?: return
        val batch = panelRepository.panels.value
        if (batch.isEmpty()) return

        localState.update { it.copy(isSending = true, sendProgress = 0 to batch.size) }

        viewModelScope.launch {
            try {
                val url = settingsStore.webhookUrl.first()
                val password = settingsStore.webhookPassword.first()
                var ok = 0
                var abortedByWrongPassword = false
                val today = LocalDate.now(clock)

                for ((index, panel) in batch.withIndex()) {
                    val result = webhookClient.send(
                        url = url,
                        password = password,
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
                        SnackbarEvent.Error("Ошибка отправки. Сообщи руководителю.")
                    ok == batch.size -> {
                        onVibrate?.invoke(VibrateType.SUCCESS)
                        SnackbarEvent.Success("Отправлено $ok из ${batch.size} ✓")
                    }
                    else ->
                        SnackbarEvent.Error("Отправлено $ok из ${batch.size}. Остальное — в логах.")
                }
                events.send(finalEvent)
            } catch (e: Exception) {
                Log.e(tag, "Ошибка при отправке", e)
                events.send(SnackbarEvent.Error("Ошибка отправки: ${e.message}"))
            } finally {
                localState.update { it.copy(isSending = false, sendProgress = null) }
            }
        }
    }

    fun onRemovePanel(panelId: String) {
        viewModelScope.launch {
            try {
                panelRepository.remove(panelId)
            } catch (e: Exception) {
                Log.e(tag, "Ошибка удаления панели", e)
            }
        }
    }

    fun onClearClicked() {
        viewModelScope.launch {
            try {
                panelRepository.clear()
                events.send(SnackbarEvent.Info("Список очищен"))
            } catch (e: Exception) {
                Log.e(tag, "Ошибка очистки списка", e)
            }
        }
    }

    fun onEditFault(panelId: String, fault: String) {
        viewModelScope.launch {
            try {
                panelRepository.updateFault(panelId, fault)
            } catch (e: Exception) {
                Log.e(tag, "Ошибка обновления замечания", e)
            }
        }
    }

    fun formatForClipboard(): String {
        return panelRepository.panels.value.joinToString("\n") { it.id }
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
