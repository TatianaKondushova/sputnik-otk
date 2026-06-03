package ru.sputnik.otk.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    val selectedMaster: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.SELECTED_MASTER]
    }

    val webhookUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.WEBHOOK_URL] ?: DEFAULT_WEBHOOK_URL
    }

    val webhookPassword: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.WEBHOOK_PASSWORD] ?: DEFAULT_WEBHOOK_PASSWORD
    }

    val masters: Flow<List<String>> = dataStore.data.map { preferences ->
        val raw = preferences[Keys.MASTERS]
        if (raw != null) {
            try {
                json.decodeFromString<List<String>>(raw)
            } catch (_: Exception) {
                DEFAULT_MASTERS
            }
        } else {
            DEFAULT_MASTERS
        }
    }

    suspend fun setSelectedMaster(master: String?) {
        dataStore.edit { preferences ->
            if (master != null) {
                preferences[Keys.SELECTED_MASTER] = master
            } else {
                preferences.remove(Keys.SELECTED_MASTER)
            }
        }
    }

    suspend fun setWebhookUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[Keys.WEBHOOK_URL] = url
        }
    }

    suspend fun setWebhookPassword(password: String) {
        dataStore.edit { preferences ->
            preferences[Keys.WEBHOOK_PASSWORD] = password
        }
    }

    suspend fun setMasters(masters: List<String>) {
        dataStore.edit { preferences ->
            preferences[Keys.MASTERS] = json.encodeToString(masters)
        }
    }

    private object Keys {
        val SELECTED_MASTER = stringPreferencesKey("selected_master")
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val WEBHOOK_PASSWORD = stringPreferencesKey("webhook_password")
        val MASTERS = stringPreferencesKey("masters")
    }

    companion object {
        val DEFAULT_MASTERS: List<String> = listOf(
            "Руслан", "Камиль", "Виктор", "Тимур", "Мастер",
        )

        @Volatile
        private var instance: SettingsStore? = null

        /**
         * Потокобезопасный синглтон.
         * DataStore можно создавать только один раз для каждого файла,
         * иначе IllegalStateException. Поэтому кэшируем экземпляр.
         */
        fun singleton(context: Context, json: Json = Json { ignoreUnknownKeys = true }): SettingsStore {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val dataStore = PreferenceDataStoreFactory.create {
                        context.preferencesDataStoreFile("settings")
                    }
                    SettingsStore(dataStore, json).also { instance = it }
                }
            }
        }

        /** Только для тестов. */
        fun create(context: Context, json: Json = Json { ignoreUnknownKeys = true }): SettingsStore {
            val dataStore = PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile("settings")
            }
            return SettingsStore(dataStore, json)
        }
    }
}
