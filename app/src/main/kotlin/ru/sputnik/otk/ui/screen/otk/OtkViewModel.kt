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
