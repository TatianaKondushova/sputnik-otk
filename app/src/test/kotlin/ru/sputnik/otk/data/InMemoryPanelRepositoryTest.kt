package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryPanelRepositoryTest {

    @Test
    fun `add returns Ok and exposes panel in state flow`() = runTest {
        val repo = InMemoryPanelRepository()
        val panel = Panel(id = "04:AB", addedAt = 1L)

        val result = repo.add(panel)

        assertEquals(PanelRepository.AddResult.Ok, result)
        assertEquals(listOf(panel), repo.panels.value)
    }

    @Test
    fun `add returns Duplicate when panel with same id already exists`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "04:AB", addedAt = 1L))

        val result = repo.add(Panel(id = "04:AB", addedAt = 2L))

        assertEquals(PanelRepository.AddResult.Duplicate, result)
        assertEquals(1, repo.panels.value.size)
    }

    @Test
    fun `remove deletes panel by id`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "a"))
        repo.add(Panel(id = "b"))

        repo.remove("a")

        assertEquals(listOf("b"), repo.panels.value.map { it.id })
    }

    @Test
    fun `remove is no-op for unknown id`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "a"))

        repo.remove("missing")

        assertEquals(listOf("a"), repo.panels.value.map { it.id })
    }

    @Test
    fun `clear empties the list`() = runTest {
        val repo = InMemoryPanelRepository()
        repo.add(Panel(id = "a"))
        repo.add(Panel(id = "b"))

        repo.clear()

        assertTrue(repo.panels.value.isEmpty())
    }
}
