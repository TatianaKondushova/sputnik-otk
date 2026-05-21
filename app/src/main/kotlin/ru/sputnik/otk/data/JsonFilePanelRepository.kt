package ru.sputnik.otk.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

class JsonFilePanelRepository(
    private val file: File,
    private val json: Json = Json,
) : PanelRepository {

    private val mutex = Mutex()
    private val _panels = MutableStateFlow<List<Panel>>(emptyList())
    override val panels: StateFlow<List<Panel>> = _panels.asStateFlow()

    init {
        load()
    }

    override suspend fun add(panel: Panel): PanelRepository.AddResult = mutex.withLock {
        if (_panels.value.any { it.id == panel.id }) {
            PanelRepository.AddResult.Duplicate
        } else {
            _panels.update { it + panel }
            save()
            PanelRepository.AddResult.Ok
        }
    }

    override suspend fun remove(panelId: String) = mutex.withLock {
        _panels.update { current -> current.filterNot { it.id == panelId } }
        save()
    }

    override suspend fun clear() = mutex.withLock {
        _panels.value = emptyList()
        save()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val text = file.readText()
            if (text.isBlank()) return
            val loaded = json.decodeFromString<List<Panel>>(text)
            _panels.value = loaded
        } catch (_: Exception) {
            // ignore corrupted file
        }
    }

    private fun save() {
        try {
            file.writeText(json.encodeToString(_panels.value))
        } catch (_: Exception) {
            // ignore write errors for now
        }
    }
}
