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
