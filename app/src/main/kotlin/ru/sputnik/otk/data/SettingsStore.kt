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

class SettingsStore(
    private val dataStore: DataStore<Preferences>,
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

    private object Keys {
        val SELECTED_MASTER = stringPreferencesKey("selected_master")
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val WEBHOOK_PASSWORD = stringPreferencesKey("webhook_password")
    }

    companion object {
        fun create(context: Context): SettingsStore {
            val dataStore = PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile("settings")
            }
            return SettingsStore(dataStore)
        }
    }
}
