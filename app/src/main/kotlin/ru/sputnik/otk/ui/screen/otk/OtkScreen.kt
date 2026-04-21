package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.sputnik.otk.LocalAppContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtkScreen(
    onNavigateBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: OtkViewModel = viewModel(factory = container.otkViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { event ->
            snackbarHostState.showSnackbar(event.text)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("ОТК") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            MasterDropdown(
                selected = state.master,
                options = state.masters,
                onSelected = viewModel::onMasterSelected,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Дата: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            PanelInput(
                value = state.panelInput,
                enabled = state.master != null,
                onValueChange = viewModel::onPanelInputChanged,
                onAdd = viewModel::onAddPanelClicked,
            )

            if (state.master == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "💡 Сначала выбери мастера",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Отсканировано (${state.pendingPanels.size}):",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            PanelList(panels = state.pendingPanels)

            Spacer(Modifier.height(24.dp))
            OtkBottomBar(
                saveEnabled = state.pendingPanels.isNotEmpty() && !state.isSending,
                isSending = state.isSending,
                sendProgress = state.sendProgress,
                onSave = viewModel::onSaveClicked,
                onLogs = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Доступно на следующем этапе")
                    }
                },
                onBack = {
                    if (viewModel.onBackClicked()) onNavigateBack()
                },
            )
        }
    }
}

@Composable
private fun OtkBottomBar(
    saveEnabled: Boolean,
    isSending: Boolean,
    sendProgress: Pair<Int, Int>?,
    onSave: () -> Unit,
    onLogs: () -> Unit,
    onBack: () -> Unit,
) {
    Column {
        Button(onClick = onSave, enabled = saveEnabled) {
            Text(if (isSending && sendProgress != null)
                "Отправка ${sendProgress.first} из ${sendProgress.second}"
            else "Сохранить")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLogs) { Text("Логи") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Назад") }
    }
}
