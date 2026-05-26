package ru.sputnik.otk.data

import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsStoreTest {

    private fun createStore(): SettingsStore = SettingsStore(InMemoryDataStore())

    @Test
    fun `selectedMaster is null by default`() = runTest {
        val store = createStore()
        assertNull(store.selectedMaster.first())
    }

    @Test
    fun `setSelectedMaster updates selectedMaster`() = runTest {
        val store = createStore()
        store.setSelectedMaster("Руслан")
        assertEquals("Руслан", store.selectedMaster.first())
    }

    @Test
    fun `setSelectedMaster to null clears value`() = runTest {
        val store = createStore()
        store.setSelectedMaster("Камиль")
        store.setSelectedMaster(null)
        assertNull(store.selectedMaster.first())
    }

    @Test
    fun `webhookUrl defaults to DEFAULT_WEBHOOK_URL`() = runTest {
        val store = createStore()
        assertEquals(DEFAULT_WEBHOOK_URL, store.webhookUrl.first())
    }

    @Test
    fun `setWebhookUrl updates webhookUrl`() = runTest {
        val store = createStore()
        store.setWebhookUrl("https://new.url")
        assertEquals("https://new.url", store.webhookUrl.first())
    }

    @Test
    fun `webhookPassword defaults to DEFAULT_WEBHOOK_PASSWORD`() = runTest {
        val store = createStore()
        assertEquals(DEFAULT_WEBHOOK_PASSWORD, store.webhookPassword.first())
    }

    @Test
    fun `setWebhookPassword updates webhookPassword`() = runTest {
        val store = createStore()
        store.setWebhookPassword("secret123")
        assertEquals("secret123", store.webhookPassword.first())
    }

    @Test
    fun `masters defaults to DEFAULT_MASTERS`() = runTest {
        val store = createStore()
        assertEquals(SettingsStore.DEFAULT_MASTERS, store.masters.first())
    }

    @Test
    fun `setMasters updates masters`() = runTest {
        val store = createStore()
        store.setMasters(listOf("Анна", "Борис"))
        assertEquals(listOf("Анна", "Борис"), store.masters.first())
    }

    @Test
    fun `corrupted masters falls back to DEFAULT_MASTERS`() = runTest {
        val dataStore = InMemoryDataStore()
        val store = SettingsStore(dataStore)
        dataStore.edit { it[stringPreferencesKey("masters")] = "invalid json" }
        assertEquals(SettingsStore.DEFAULT_MASTERS, store.masters.first())
    }
}
