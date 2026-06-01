package ru.sputnik.otk.ui.screen.errorlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.sputnik.otk.data.ErrorLogRepository

class ErrorLogViewModel(
    private val errorLogRepository: ErrorLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ErrorLogUiState())
    val uiState: StateFlow<ErrorLogUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val entries = errorLogRepository.getAll()
            _uiState.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    fun onClearClicked() {
        viewModelScope.launch {
            errorLogRepository.clear()
            _uiState.update { it.copy(entries = emptyList()) }
        }
    }
}
