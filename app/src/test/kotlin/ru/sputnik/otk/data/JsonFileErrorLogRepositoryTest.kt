package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonFileErrorLogRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepo(now: () -> Long = { 0L }): JsonFileErrorLogRepository {
        val file = tmpFolder.newFile("errors.json")
        return JsonFileErrorLogRepository(file, Json, now)
    }

    @Test
    fun `log adds entry with provided panelId and reason`() = runTest {
        val repo = createRepo()
        repo.log("04:AB:CD", "wrong password")

        val entries = repo.getAll()
        assertEquals(1, entries.size)
        assertEquals("04:AB:CD", entries.first().panelId)
        assertEquals("wrong password", entries.first().reason)
    }

    @Test
    fun `log appends multiple entries in order`() = runTest {
        val repo = createRepo()
        repo.log("p1", "r1")
        repo.log("p2", "r2")

        val entries = repo.getAll()
        assertEquals(listOf("p1", "p2"), entries.map { it.panelId })
        assertEquals(listOf("r1", "r2"), entries.map { it.reason })
    }

    @Test
    fun `reload restores entries from file`() = runTest {
        val file = tmpFolder.newFile("errors2.json")
        val repo1 = JsonFileErrorLogRepository(file, Json) { 1000L }
        repo1.log("a", "err1")

        val repo2 = JsonFileErrorLogRepository(file, Json) { 2000L }

        val entries = repo2.getAll()
        assertEquals(1, entries.size)
        assertEquals("a", entries.single().panelId)
        assertEquals("err1", entries.single().reason)
    }

    @Test
    fun `clear removes all entries`() = runTest {
        val repo = createRepo()
        repo.log("p1", "r1")

        repo.clear()

        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `reload handles corrupted file gracefully`() = runTest {
        val file = tmpFolder.newFile("bad.json")
        file.writeText("not json")

        val repo = createRepo()

        assertTrue(repo.getAll().isEmpty())
    }
}
