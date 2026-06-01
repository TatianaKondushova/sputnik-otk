package ru.sputnik.otk.ui.screen.errorlog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.sputnik.otk.data.ErrorEntry
import ru.sputnik.otk.data.ErrorLogRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorLogViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeErrorLogRepository : ErrorLogRepository {
        val entries = mutableListOf<ErrorEntry>()

        override suspend fun log(panelId: String, reason: String) {
            entries += ErrorEntry(panelId, reason, System.currentTimeMillis())
        }

        override suspend fun getAll(): List<ErrorEntry> = entries.toList()

        override suspend fun clear() {
            entries.clear()
        }
    }

    @Test
    fun `loads entries on init`() = runTest(dispatcher) {
        val repo = FakeErrorLogRepository()
        repo.entries += ErrorEntry("P1", "timeout", 1000L)
        repo.entries += ErrorEntry("P2", "wrong password", 2000L)

        val vm = ErrorLogViewModel(repo)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.entries.size)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `shows empty state when no entries`() = runTest(dispatcher) {
        val vm = ErrorLogViewModel(FakeErrorLogRepository())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.entries.isEmpty())
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `clear removes all entries`() = runTest(dispatcher) {
        val repo = FakeErrorLogRepository()
        repo.entries += ErrorEntry("P1", "timeout", 1000L)

        val vm = ErrorLogViewModel(repo)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.entries.size)

        vm.onClearClicked()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.entries.isEmpty())
    }

    @Test
    fun `reload refreshes entries`() = runTest(dispatcher) {
        val repo = FakeErrorLogRepository()
        val vm = ErrorLogViewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.entries.isEmpty())

        repo.entries += ErrorEntry("P3", "error", 3000L)
        vm.load()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.entries.size)
        assertEquals("P3", vm.uiState.value.entries.single().panelId)
    }
}
