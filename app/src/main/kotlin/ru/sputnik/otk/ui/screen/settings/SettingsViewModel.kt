package ru.sputnik.otk.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.SettingsStore

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val panelRepository: PanelRepository,
    private val errorLogRepository: ErrorLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsStore.webhookUrl,
                settingsStore.webhookPassword,
                settingsStore.masters,
            ) { url, password, masters ->
                SettingsUiState(
                    webhookUrl = url,
                    webhookPassword = password,
                    masters = masters,
                    isLoading = false,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onWebhookUrlChanged(url: String) {
        _uiState.update { it.copy(webhookUrl = url) }
    }

    fun onWebhookPasswordChanged(password: String) {
        _uiState.update { it.copy(webhookPassword = password) }
    }

    fun onNewMasterInputChanged(text: String) {
        _uiState.update { it.copy(newMasterInput = text) }
    }

    fun onAddMaster() {
        val name = _uiState.value.newMasterInput.trim()
        if (name.isEmpty()) return
        val current = _uiState.value.masters
        if (name in current) return
        viewModelScope.launch {
            settingsStore.setMasters(current + name)
            _uiState.update { it.copy(newMasterInput = "") }
        }
    }

    fun onRemoveMaster(master: String) {
        viewModelScope.launch {
            settingsStore.setMasters(_uiState.value.masters - master)
        }
    }

    fun onSaveSettings() {
        viewModelScope.launch {
            settingsStore.setWebhookUrl(_uiState.value.webhookUrl)
            settingsStore.setWebhookPassword(_uiState.value.webhookPassword)
        }
    }

    fun onClearPanels() {
        viewModelScope.launch {
            panelRepository.clear()
        }
    }

    fun onClearLogs() {
        viewModelScope.launch {
            errorLogRepository.clear()
        }
    }
}
