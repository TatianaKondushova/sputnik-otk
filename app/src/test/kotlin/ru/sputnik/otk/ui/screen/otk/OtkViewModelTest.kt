package ru.sputnik.otk.ui.screen.otk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.sputnik.otk.data.ErrorEntry
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.InMemoryPanelRepository
import ru.sputnik.otk.data.Panel
import ru.sputnik.otk.data.WebhookClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class OtkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-20T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeErrorLog : ErrorLogRepository {
        val entries = mutableListOf<ErrorEntry>()
        override suspend fun log(panelId: String, reason: String) {
            entries += ErrorEntry(panelId, reason, 0L)
        }
    }

    private class FakeWebhookClient(
        private val responses: ArrayDeque<WebhookClient.Result>,
    ) : WebhookClient(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json) {
        val calls = mutableListOf<Panel>()
        override suspend fun send(
            url: String,
            password: String,
            panel: Panel,
            master: String,
            date: java.time.LocalDate,
        ): Result {
            calls += panel
            return responses.removeFirst()
        }
    }

    private fun buildVm(
        webhook: FakeWebhookClient = FakeWebhookClient(ArrayDeque()),
        panels: InMemoryPanelRepository = InMemoryPanelRepository(),
        errors: FakeErrorLog = FakeErrorLog(),
    ): OtkViewModel = OtkViewModel(
        webhookClient = webhook,
        panelRepository = panels,
        errorLogRepository = errors,
        clock = fixedClock,
    )

    @Test
    fun `onAddPanelClicked emits error snackbar when master not selected`() = runTest(dispatcher) {
        val vm = buildVm()
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }

        vm.onPanelInputChanged("p1")
        vm.onAddPanelClicked()
        advanceUntilIdle()

        val err = events.first() as SnackbarEvent.Error
        assertTrue(err.text.contains("мастер"))
        assertTrue(vm.uiState.value.pendingPanels.isEmpty())
        job.cancel()
    }

    @Test
    fun `onAddPanelClicked ignores blank input`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.onMasterSelected("Руслан")
        vm.onPanelInputChanged("   ")
        vm.onAddPanelClicked()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingPanels.isEmpty())
    }

    @Test
    fun `onAddPanelClicked adds panel and clears input on success`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.onMasterSelected("Руслан")
        vm.onPanelInputChanged("04:AB")
        vm.onAddPanelClicked()
        advanceUntilIdle()

        assertEquals(listOf("04:AB"), vm.uiState.value.pendingPanels.map { it.id })
        assertEquals("", vm.uiState.value.panelInput)
    }

    @Test
    fun `onAddPanelClicked emits duplicate snackbar on repeated id`() = runTest(dispatcher) {
        val vm = buildVm()
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }

        vm.onMasterSelected("Руслан")
        vm.onPanelInputChanged("04:AB"); vm.onAddPanelClicked(); advanceUntilIdle()
        vm.onPanelInputChanged("04:AB"); vm.onAddPanelClicked(); advanceUntilIdle()

        val err = events.last() as SnackbarEvent.Error
        assertTrue(err.text.contains("уже в списке"))
        assertEquals(1, vm.uiState.value.pendingPanels.size)
        job.cancel()
    }

    @Test
    fun `onSaveClicked sends all panels and removes them on Ok`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also {
            it.add(Panel("a")); it.add(Panel("b"))
        }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(
            WebhookClient.Result.Ok, WebhookClient.Result.Ok,
        )))
        val vm = buildVm(webhook = webhook, panels = panels)
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), webhook.calls.map { it.id })
        assertTrue(vm.uiState.value.pendingPanels.isEmpty())
        assertEquals(false, vm.uiState.value.isSending)
        assertNull(vm.uiState.value.sendProgress)
    }

    @Test
    fun `onSaveClicked emits success snackbar when all ok`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also { it.add(Panel("a")) }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(WebhookClient.Result.Ok)))
        val vm = buildVm(webhook = webhook, panels = panels)
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }

        vm.onMasterSelected("Руслан")
        vm.onSaveClicked()
        advanceUntilIdle()

        assertTrue(events.any { it is SnackbarEvent.Success })
        job.cancel()
    }

    @Test
    fun `onSaveClicked aborts batch on WrongPassword and logs it`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also {
            it.add(Panel("a")); it.add(Panel("b"))
        }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(
            WebhookClient.Result.WrongPassword, WebhookClient.Result.Ok,
        )))
        val errors = FakeErrorLog()
        val vm = buildVm(webhook = webhook, panels = panels, errors = errors)
        val events = mutableListOf<SnackbarEvent>()
        val job = launch { vm.snackbarEvents.toList(events) }
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(listOf("a"), webhook.calls.map { it.id })
        assertEquals(listOf("a"), errors.entries.map { it.panelId })
        assertEquals("wrong password", errors.entries.single().reason)
        assertTrue(events.any { it is SnackbarEvent.Error && it.text.contains("пароль") })
        assertEquals(2, vm.uiState.value.pendingPanels.size)
        job.cancel()
    }

    @Test
    fun `onSaveClicked keeps panel on NetworkError and logs it`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also { it.add(Panel("a")) }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(
            WebhookClient.Result.NetworkError("timeout"),
        )))
        val errors = FakeErrorLog()
        val vm = buildVm(webhook = webhook, panels = panels, errors = errors)
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.uiState.value.pendingPanels.map { it.id })
        assertEquals("timeout", errors.entries.single().reason)
    }

    @Test
    fun `onBackClicked returns false while sending`() = runTest(dispatcher) {
        val panels = InMemoryPanelRepository().also { it.add(Panel("a")) }
        val webhook = FakeWebhookClient(ArrayDeque(listOf(WebhookClient.Result.Ok)))
        val vm = buildVm(webhook = webhook, panels = panels)
        vm.onMasterSelected("Руслан")

        vm.onSaveClicked()
        assertTrue(vm.uiState.value.isSending)
        assertEquals(false, vm.onBackClicked())

        advanceUntilIdle()
        assertEquals(true, vm.onBackClicked())
    }
}
