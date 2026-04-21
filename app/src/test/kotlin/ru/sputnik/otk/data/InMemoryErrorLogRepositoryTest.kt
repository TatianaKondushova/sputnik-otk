package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryErrorLogRepositoryTest {

    @Test
    fun `log adds entry with provided panelId and reason`() = runTest {
        val repo = InMemoryErrorLogRepository()
        repo.log("04:AB:CD", "wrong password")

        val entries = repo.snapshot()
        assertEquals(1, entries.size)
        assertEquals("04:AB:CD", entries.first().panelId)
        assertEquals("wrong password", entries.first().reason)
    }

    @Test
    fun `log appends multiple entries in order`() = runTest {
        val repo = InMemoryErrorLogRepository()
        repo.log("p1", "r1")
        repo.log("p2", "r2")

        val entries = repo.snapshot()
        assertEquals(listOf("p1", "p2"), entries.map { it.panelId })
        assertEquals(listOf("r1", "r2"), entries.map { it.reason })
    }
}
