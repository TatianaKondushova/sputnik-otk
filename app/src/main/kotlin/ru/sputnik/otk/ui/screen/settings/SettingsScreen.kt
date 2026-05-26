package ru.sputnik.otk.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.sputnik.otk.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Webhook")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.webhookUrl,
                onValueChange = viewModel::onWebhookUrlChanged,
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.webhookPassword,
                onValueChange = viewModel::onWebhookPasswordChanged,
                label = { Text("Пароль") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("Мастера")
            Spacer(Modifier.height(8.dp))
            MasterChips(
                masters = state.masters,
                onRemove = viewModel::onRemoveMaster,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.newMasterInput,
                onValueChange = viewModel::onNewMasterInputChanged,
                label = { Text("Новый мастер") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = viewModel::onAddMaster,
                        enabled = state.newMasterInput.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить")
                    }
                },
            )

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("Данные")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::onClearPanels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Очистить список панелей")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::onClearLogs,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Очистить логи ошибок")
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    viewModel.onSaveSettings()
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MasterChips(
    masters: List<String>,
    onRemove: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        masters.forEach { master ->
            AssistChip(
                onClick = { },
                label = { Text(master) },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(master) },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Удалить $master",
                            modifier = Modifier.height(16.dp),
                        )
                    }
                },
            )
        }
    }
}
