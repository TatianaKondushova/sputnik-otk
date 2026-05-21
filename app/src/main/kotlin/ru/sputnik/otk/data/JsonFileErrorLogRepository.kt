package ru.sputnik.otk.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

class JsonFileErrorLogRepository(
    private val file: File,
    private val json: Json = Json,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ErrorLogRepository {

    private val mutex = Mutex()
    private val entries = mutableListOf<ErrorEntry>()

    init {
        load()
    }

    override suspend fun log(panelId: String, reason: String) {
        mutex.withLock {
            entries += ErrorEntry(panelId, reason, now())
            save()
        }
    }

    suspend fun snapshot(): List<ErrorEntry> = mutex.withLock {
        entries.toList()
    }

    suspend fun clear() {
        mutex.withLock {
            entries.clear()
            save()
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val text = file.readText()
            if (text.isBlank()) return
            val loaded = json.decodeFromString<List<ErrorEntry>>(text)
            entries.clear()
            entries.addAll(loaded)
        } catch (_: Exception) {
            // ignore corrupted file
        }
    }

    private fun save() {
        try {
            file.writeText(json.encodeToString(entries.toList()))
        } catch (_: Exception) {
            // ignore write errors for now
        }
    }
}
