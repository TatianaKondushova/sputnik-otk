package ru.sputnik.otk.ui.screen.otk

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.sputnik.otk.LocalAppContainer
import ru.sputnik.otk.data.Panel
import ru.sputnik.otk.ui.theme.SuccessGreen
import ru.sputnik.otk.ui.theme.SuccessGreenPale
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
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Диалог редактирования
    var editTarget: Panel? by remember { mutableStateOf(null) }
    var editText by remember { mutableStateOf("") }

    // NFC: приём сканов и снекбаров
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

    // Вибрация + Toast
    LaunchedEffect(viewModel) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        viewModel.onVibrate = { type ->
            vibrator?.let {
                val millis = when (type) {
                    OtkViewModel.VibrateType.SUCCESS -> 80L
                    OtkViewModel.VibrateType.DUPLICATE -> 300L
                    OtkViewModel.VibrateType.ERROR -> 500L
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(millis)
                }
            }
        }
        viewModel.showToast = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Автопрокрутка при добавлении панели
    val panelCount = state.pendingPanels.size
    LaunchedEffect(panelCount) {
        if (panelCount > 0) {
            listState.animateScrollToItem(panelCount - 1)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить список?") },
            text = { Text("Все ${state.pendingPanels.size} панелей будут удалены.") },
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

    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Неисправность: ${editTarget!!.id}") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Описание неисправности") },
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "ОТК",
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.onBackClicked()) onNavigateBack()
                    }) {
                        Text("←", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Логи",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Только кнопка «Сохранить» — крупная
            if (state.pendingPanels.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = viewModel::onSaveClicked,
                        enabled = !state.isSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (state.isSending) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                            )
                        },
                    ) {
                        val progress = state.sendProgress
                        if (state.isSending && progress != null) {
                            CircularProgressIndicator(
                                progress = { progress.first.toFloat() / progress.second },
                                modifier = Modifier
                                    .size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Отправка ${progress.first} из ${progress.second}")
                        } else {
                            Text(
                                "Сохранить (${state.pendingPanels.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Блок: мастер + дата + ввод
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                MasterDropdown(
                    selected = state.master,
                    options = state.masters,
                    onSelected = viewModel::onMasterSelected,
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.pendingPanels.isNotEmpty()) {
                        PanelCounter(count = state.pendingPanels.size)
                    }
                }

                Spacer(Modifier.height(12.dp))
                PanelInput(
                    value = state.panelInput,
                    enabled = state.master != null && !state.isSending,
                    onValueChange = viewModel::onPanelInputChanged,
                    onAdd = viewModel::onAddPanelClicked,
                )

                if (state.master == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "⚠ Сначала выбери мастера",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (state.pendingPanels.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "📱 Поднеси NFC-метку или введи номер вручную",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Заголовок списка
            if (state.pendingPanels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Панели:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
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
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Очистить",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // Список панелей
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.pendingPanels, key = { it.id }) { panel ->
                        PanelCard(
                            panel = panel,
                            onRemove = { viewModel.onRemovePanel(panel.id) },
                            onEdit = {
                                editTarget = panel
                                editText = panel.fault
                            },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            } else {
                // Пустое состояние
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📋",
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Список пуст",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Поднеси NFC-метку\nили введи номер вручную",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    panel: Panel,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = panel.id,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (panel.fault.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = panel.fault,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Редактировать",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelCounter(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SuccessGreen)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$count шт.",
            style = MaterialTheme.typography.labelLarge,
            color = SuccessGreenPale,
            fontWeight = FontWeight.Bold,
        )
    }
}
