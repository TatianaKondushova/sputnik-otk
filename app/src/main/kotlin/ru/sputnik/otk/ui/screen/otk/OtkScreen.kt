package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.sputnik.otk.LocalAppContainer
import ru.sputnik.otk.data.Panel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtkScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogs: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: OtkViewModel = viewModel(factory = container.otkViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // Состояние диалога редактирования
    var editTarget: Panel? by remember { mutableStateOf(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        launch {
            try {
                viewModel.snackbarEvents.collect { event ->
                    snackbarHostState.showSnackbar(event.text)
                }
            } catch (_: Exception) {
            }
        }
        container.pendingNfcPanelId?.let { panelId ->
            try {
                viewModel.onNfcScanned(panelId)
            } catch (_: Exception) {
            }
            container.pendingNfcPanelId = null
        }
        launch {
            try {
                container.nfcScans.collect { panelId ->
                    viewModel.onNfcScanned(panelId)
                }
            } catch (_: Exception) {
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить список?") },
            text = { Text("Все ${state.pendingPanels.size} панелей будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.onClearClicked()
                    },
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    // Диалог редактирования fault
    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Комментарий к ${editTarget!!.id}") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Замечание") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEditFault(editTarget!!.id, editText.trim())
                        editTarget = null
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text("Отмена")
                }
            },
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Дата: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.pendingPanels.isNotEmpty()) {
                    PanelCounter(count = state.pendingPanels.size)
                }
            }

            Spacer(Modifier.height(16.dp))
            PanelInput(
                value = state.panelInput,
                enabled = state.master != null && !state.isSending,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Отсканировано:",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.pendingPanels.isNotEmpty() && !state.isSending) {
                    Row {
                        IconButton(
                            onClick = {
                                val text = viewModel.formatForClipboard()
                                clipboardManager.setText(AnnotatedString(text))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Скопировано ${state.pendingPanels.size} шт.")
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Копировать",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        TextButton(
                            onClick = { showClearDialog = true },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Очистить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PanelList(
                panels = state.pendingPanels,
                onRemove = viewModel::onRemovePanel,
                onEdit = { panelId ->
                    val panel = state.pendingPanels.find { it.id == panelId }
                    editTarget = panel
                    editText = panel?.fault ?: ""
                },
            )

            Spacer(Modifier.height(24.dp))
            OtkBottomBar(
                saveEnabled = state.pendingPanels.isNotEmpty() && !state.isSending,
                isSending = state.isSending,
                sendProgress = state.sendProgress,
                onSave = viewModel::onSaveClicked,
                onLogs = onNavigateToLogs,
                onBack = {
                    if (viewModel.onBackClicked()) onNavigateBack()
                },
            )
        }
    }
}

@Composable
private fun PanelCounter(count: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
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
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSending && sendProgress != null) {
                CircularProgressIndicator(
                    progress = { sendProgress.first.toFloat() / sendProgress.second },
                    modifier = Modifier
                        .height(20.dp)
                        .width(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Отправка ${sendProgress.first} из ${sendProgress.second}")
            } else {
                Text("Сохранить")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onLogs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Логи")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Назад")
        }
    }
}
