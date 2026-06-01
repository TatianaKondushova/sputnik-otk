package ru.sputnik.otk.ui.screen.errorlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.sputnik.otk.data.ErrorLogRepository

class ErrorLogViewModelFactory(
    private val errorLogRepository: ErrorLogRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ErrorLogViewModel::class.java)) {
            return ErrorLogViewModel(errorLogRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
