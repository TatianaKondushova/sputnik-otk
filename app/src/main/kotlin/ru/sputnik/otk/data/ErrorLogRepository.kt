package ru.sputnik.otk.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.serialization.Serializable

@Serializable
data class ErrorEntry(
    val panelId: String,
    val reason: String,
    val timestamp: Long,
)

interface ErrorLogRepository {
    suspend fun log(panelId: String, reason: String)
    suspend fun clear()
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

    override suspend fun clear() {
        mutex.withLock {
            entries.clear()
        }
    }

    internal suspend fun snapshot(): List<ErrorEntry> = mutex.withLock { entries.toList() }
}
