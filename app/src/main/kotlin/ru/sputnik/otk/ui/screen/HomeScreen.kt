package ru.sputnik.otk.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.sputnik.otk.MainActivity
import ru.sputnik.otk.ui.theme.SputnikBlue
import ru.sputnik.otk.ui.theme.SputnikBluePale
import ru.sputnik.otk.ui.theme.SputnikOtkTheme
import ru.sputnik.otk.ui.theme.SuccessGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToOtk: () -> Unit,
    onNavigateToWarranty: () -> Unit,
    onLongPressTitle: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
) {
    val crashLog = MainActivity.appLastCrashLog
    var showCrashDialog by remember { mutableStateOf(crashLog != null) }
    val clipboardManager = LocalClipboardManager.current

    if (showCrashDialog && crashLog != null) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("⚠ Краш-лог") },
            text = {
                Column {
                    Text(
                        text = crashLog,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(crashLog))
                    showCrashDialog = false
                }) {
                    Text("Копировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashDialog = false }) {
                    Text("Закрыть")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Сервис ОТК",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = SputnikBlue,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongPressTitle,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ООО «Спутник»",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            onClick = onNavigateToOtk,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = SputnikBlue,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "ОТК",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SputnikBluePale,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Сканирование панелей",
                    style = MaterialTheme.typography.bodySmall,
                    color = SputnikBluePale,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            onClick = onNavigateToWarranty,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = SuccessGreen,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Гарантия",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SputnikBluePale,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bitrix24",
                    style = MaterialTheme.typography.bodySmall,
                    color = SputnikBluePale,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка Логи (маленькая, незаметная)
        TextButton(
            onClick = onNavigateToLogs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "📋 Логи приложения",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SputnikOtkTheme {
        HomeScreen(
            onNavigateToOtk = {},
            onNavigateToWarranty = {},
            onLongPressTitle = {},
        )
    }
}
