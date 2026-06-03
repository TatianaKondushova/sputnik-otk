package ru.sputnik.otk

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.sputnik.otk.data.NfcParser
import ru.sputnik.otk.ui.screen.HomeScreen
import ru.sputnik.otk.ui.screen.errorlog.ErrorLogScreen
import ru.sputnik.otk.ui.screen.otk.OtkScreen
import ru.sputnik.otk.ui.screen.settings.SettingsScreen
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Перехватчик крашей — установить как можно раньше
        CrashLogger.install(applicationContext)

        // Показать последний краш-лог, если есть
        val lastCrash = CrashLogger.readAndClear(this)
        if (lastCrash != null) {
            Toast.makeText(this, "Был краш! Смотри настройки → Логи краша", Toast.LENGTH_LONG).show()
            // Сохраняем для просмотра в экране логов
            appLastCrashLog = lastCrash
        }

        appContainer = AppContainer(applicationContext)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        handleIntent(intent)

        setContent {
            SputnikOtkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CompositionLocalProvider(LocalAppContainer provides appContainer) {
                        val navController = rememberNavController()

                        LaunchedEffect(Unit) {
                            try {
                                appContainer.nfcScans.collect { panelId ->
                                    if (navController.currentDestination?.route != "otk") {
                                        appContainer.pendingNfcPanelId = panelId
                                        navController.navigate("otk")
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }

                        NavHost(navController = navController, startDestination = "home") {
                            composable("home") {
                                HomeScreen(
                                    onNavigateToOtk = { navController.navigate("otk") },
                                    onLongPressTitle = { navController.navigate("settings") },
                                )
                            }
                            composable("otk") {
                                OtkScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToLogs = { navController.navigate("error_log") },
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }
                            composable("error_log") {
                                ErrorLogScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        try {
            val panelId = NfcParser.parse(intent) ?: return
            appContainer.nfcScans.tryEmit(panelId)
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Последний краш-лог, если приложение упало в предыдущем запуске. */
        var appLastCrashLog: String? = null
            private set
    }
}
