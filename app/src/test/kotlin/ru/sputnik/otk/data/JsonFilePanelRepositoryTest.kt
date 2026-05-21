package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonFilePanelRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepo(): JsonFilePanelRepository {
        val file = tmpFolder.newFile("panels.json")
        return JsonFilePanelRepository(file, Json)
    }

    @Test
    fun `add returns Ok and persists panel`() = runTest {
        val repo = createRepo()
        val panel = Panel(id = "04:AB", addedAt = 1L)

        val result = repo.add(panel)

        assertEquals(PanelRepository.AddResult.Ok, result)
        assertEquals(listOf(panel), repo.panels.value)
    }

    @Test
    fun `add returns Duplicate when panel with same id already exists`() = runTest {
        val repo = createRepo()
        repo.add(Panel(id = "04:AB", addedAt = 1L))

        val result = repo.add(Panel(id = "04:AB", addedAt = 2L))

        assertEquals(PanelRepository.AddResult.Duplicate, result)
        assertEquals(1, repo.panels.value.size)
    }

    @Test
    fun `remove deletes panel by id`() = runTest {
        val repo = createRepo()
        repo.add(Panel(id = "a"))
        repo.add(Panel(id = "b"))

        repo.remove("a")

        assertEquals(listOf("b"), repo.panels.value.map { it.id })
    }

    @Test
    fun `clear empties the list`() = runTest {
        val repo = createRepo()
        repo.add(Panel(id = "a"))
        repo.add(Panel(id = "b"))

        repo.clear()

        assertTrue(repo.panels.value.isEmpty())
    }

    @Test
    fun `reload restores panels from file`() = runTest {
        val file = tmpFolder.newFile("panels2.json")
        val repo1 = JsonFilePanelRepository(file, Json)
        repo1.add(Panel(id = "a", fault = "test"))
        repo1.add(Panel(id = "b"))

        val repo2 = JsonFilePanelRepository(file, Json)

        assertEquals(listOf("a", "b"), repo2.panels.value.map { it.id })
        assertEquals("test", repo2.panels.value.first().fault)
    }

    @Test
    fun `reload handles corrupted file gracefully`() = runTest {
        val file = tmpFolder.newFile("bad.json")
        file.writeText("not json")

        val repo = JsonFilePanelRepository(file, Json)

        assertTrue(repo.panels.value.isEmpty())
    }
}
