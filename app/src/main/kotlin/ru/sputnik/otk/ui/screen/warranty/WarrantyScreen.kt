package ru.sputnik.otk.ui.screen.warranty

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.sputnik.otk.AppLogger
import ru.sputnik.otk.LocalAppContainer
import ru.sputnik.otk.data.bitrix.BitrixConfig
import ru.sputnik.otk.ui.theme.SputnikBlue
import ru.sputnik.otk.ui.theme.SputnikBluePale
import ru.sputnik.otk.ui.theme.SuccessGreen
import ru.sputnik.otk.ui.theme.SuccessGreenPale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantyScreen(
    onNavigateBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: WarrantyViewModel = viewModel(factory = container.warrantyViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Снекбары
    LaunchedEffect(Unit) {
        launch {
            viewModel.snackbarEvents.collect { event ->
                snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // Вибрация
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
                    WarrantyViewModel.OtkVibrateType.SUCCESS -> 100L
                    WarrantyViewModel.OtkVibrateType.ERROR -> 400L
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(millis)
                }
            }
        }
    }

    // NFC для Гарантии
    LaunchedEffect(Unit) {
        launch {
            container.nfcScans.collect { panelId ->
                AppLogger.d("WarrantyScreen", "NFC получен: panelId='$panelId'")
                viewModel.onNfcScanned(panelId)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Гарантия", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Поиск
            SearchBlock(
                panelInput = state.panelInput,
                isSearching = state.isSearching,
                onInputChange = viewModel::onPanelInputChanged,
                onSearch = viewModel::onSearchClicked,
            )

            // Результат поиска
            if (state.searchError != null) {
                StatusCard(
                    text = "Ошибка: ${state.searchError}",
                    isError = true,
                )
            } else if (state.deal != null) {
                DealInfoCard(
                    deal = state.deal!!,
                    responsibleName = state.selectedResponsible,
                )

                // Поля для редактирования
                EditableFields(
                    state = state,
                    onIncomingTrackChange = viewModel::onIncomingTrackChanged,
                    onTerminalBlockChange = viewModel::onTerminalBlockChanged,
                    onReceiptDateChange = viewModel::onReceiptDateChanged,
                    onOutgoingTrackChange = viewModel::onOutgoingTrackChanged,
                    onResponsibleSelected = viewModel::onResponsibleSelected,
                    onDefectToggled = viewModel::onDefectToggled,
                )

                Spacer(Modifier.height(8.dp))

                // Кнопки стадий
                StageButtons(
                    isUpdating = state.isUpdating,
                    onReceived = viewModel::onReceivedClicked,
                    onRepair = viewModel::onRepairClicked,
                    onQcOut = viewModel::onQcOutClicked,
                    onReadyToShip = viewModel::onReadyToShipClicked,
                )
            } else if (!state.isSearching && state.panelInput.isNotBlank()) {
                StatusCard(
                    text = "Ничего не найдено",
                    isError = true,
                )
            }
        }
    }
}

@Composable
private fun SearchBlock(
    panelInput: String,
    isSearching: Boolean,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = panelInput,
            onValueChange = onInputChange,
            label = { Text("Номер панели") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSearch,
            enabled = !isSearching && panelInput.isNotBlank(),
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = "Поиск")
            }
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    isError: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else SuccessGreen,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else SuccessGreenPale,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DealInfoCard(
    deal: DealInfo,
    responsibleName: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Сделка #${deal.id}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SputnikBlue,
            )
            Text(
                text = deal.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Стадия:", stageDisplayName(deal.stageId))
            if (deal.incomingTrack.isNotBlank()) InfoRow("Вход. трек:", deal.incomingTrack)
            if (deal.outgoingTrack.isNotBlank()) InfoRow("Исх. трек:", deal.outgoingTrack)
            if (deal.terminalBlock) InfoRow("Клемник:", "Да")
            if (deal.receiptDate.isNotBlank()) InfoRow("Дата приёмки:", deal.receiptDate)
            if (responsibleName.isNotBlank()) InfoRow("Ответственный:", responsibleName)
            if (deal.defects.isNotEmpty()) {
                val names = deal.defects.mapNotNull { BitrixConfig.DefectEnum.ALL[it] }
                InfoRow("Дефекты:", names.joinToString(", "))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun stageDisplayName(stageId: String): String = when (stageId) {
    BitrixConfig.Stages.NEW -> "Новая"
    BitrixConfig.Stages.PREPARATION -> "Подготовка"
    BitrixConfig.Stages.RECEIVED -> "Принята на склад"
    BitrixConfig.Stages.IN_REPAIR -> "В ремонте"
    BitrixConfig.Stages.QC_OUT -> "Выходной контроль"
    BitrixConfig.Stages.READY_TO_SHIP -> "Готово к отправке"
    BitrixConfig.Stages.WON -> "Закрыта"
    BitrixConfig.Stages.LOSE -> "Проиграна"
    else -> stageId
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableFields(
    state: WarrantyUiState,
    onIncomingTrackChange: (String) -> Unit,
    onTerminalBlockChange: (Boolean) -> Unit,
    onReceiptDateChange: (String) -> Unit,
    onOutgoingTrackChange: (String) -> Unit,
    onResponsibleSelected: (String) -> Unit,
    onDefectToggled: (String, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Данные для обновления",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = state.incomingTrack,
                onValueChange = onIncomingTrackChange,
                label = { Text("Входящий трек-номер") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.terminalBlock,
                    onCheckedChange = onTerminalBlockChange,
                )
                Text("Клемная колодка")
            }

            OutlinedTextField(
                value = state.receiptDate,
                onValueChange = onReceiptDateChange,
                label = { Text("Дата приёмки (ДД.ММ.ГГГГ)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.outgoingTrack,
                onValueChange = onOutgoingTrackChange,
                label = { Text("Исходящий трек-номер") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Ответственный для «В ремонте»
            GenericDropdown(
                label = "Ответственный",
                selected = state.selectedResponsible.ifBlank { null },
                options = state.responsibles,
                onSelected = onResponsibleSelected,
            )

            // Дефекты — множественный выбор через чекбоксы
            DefectCheckboxes(
                selectedDefects = state.selectedDefects,
                onToggle = onDefectToggled,
            )
        }
    }
}

@Composable
private fun DefectCheckboxes(
    selectedDefects: List<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Дефекты внешнего вида",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BitrixConfig.DefectEnum.ALL.forEach { (enumId, name) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = enumId in selectedDefects,
                    onCheckedChange = { checked -> onToggle(enumId, checked) },
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericDropdown(
    label: String,
    selected: String?,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected ?: "— Выбери —",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StageButtons(
    isUpdating: Boolean,
    onReceived: () -> Unit,
    onRepair: () -> Unit,
    onQcOut: () -> Unit,
    onReadyToShip: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isUpdating) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            StageButton(
                text = "Принята на склад",
                color = SuccessGreen,
                onClick = onReceived,
            )
            StageButton(
                text = "В ремонте",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onRepair,
            )
            StageButton(
                text = "Выходной контроль",
                color = SputnikBlue,
                onClick = onQcOut,
            )
            StageButton(
                text = "Готово к отправке",
                color = SuccessGreen,
                onClick = onReadyToShip,
            )
        }
    }
}

@Composable
private fun StageButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
