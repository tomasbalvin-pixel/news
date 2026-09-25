package cz.balvin.news.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Hour of day, local time, when the next edition is assembled. */
    val editionHour: Int = 7,
    /** Articles an edition may hold; zero lifts the cap. */
    val editionSize: Int = 20,
    /** Articles per source within an edition; zero lifts the cap. */
    val perSourceLimit: Int = 5,
    val notificationsEnabled: Boolean = true,
    val retentionDays: Int = 30,
    /** When the current edition was assembled; articles fetched since belong to it. */
    val currentEditionAt: Long = 0,
    /** Anthropic API key, typed by the user; empty means no digest. */
    val apiKey: String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            theme = prefs[KEY_THEME]?.let { stored ->
                runCatching { ThemeMode.valueOf(stored) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            editionHour = prefs[KEY_EDITION_HOUR] ?: 7,
            editionSize = prefs[KEY_EDITION_SIZE] ?: 20,
            perSourceLimit = prefs[KEY_PER_SOURCE] ?: 5,
            notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
            retentionDays = prefs[KEY_RETENTION] ?: 30,
            currentEditionAt = prefs[KEY_EDITION_AT] ?: 0,
            apiKey = prefs[KEY_API_KEY].orEmpty(),
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setEditionHour(hour: Int) {
        context.dataStore.edit { it[KEY_EDITION_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun setEditionSize(size: Int) {
        context.dataStore.edit { it[KEY_EDITION_SIZE] = size.coerceAtLeast(0) }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key.trim() }
    }

    /** Opens a new edition: everything fetched from now on belongs to it. */
    suspend fun startEdition(at: Long) {
        context.dataStore.edit { it[KEY_EDITION_AT] = at }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setPerSourceLimit(limit: Int) {
        context.dataStore.edit { it[KEY_PER_SOURCE] = limit.coerceAtLeast(0) }
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { it[KEY_RETENTION] = days.coerceAtLeast(1) }
    }

    companion object {
        val EDITION_HOUR_CHOICES = listOf(6, 7, 8, 12, 18, 21)
        val RETENTION_CHOICES = listOf(7, 14, 30, 90)

        /** Zero means no cap. */
        val EDITION_SIZE_CHOICES = listOf(10, 20, 30, 0)
        val PER_SOURCE_CHOICES = listOf(3, 5, 10, 0)

        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_EDITION_HOUR = intPreferencesKey("edition_hour")
        private val KEY_EDITION_SIZE = intPreferencesKey("edition_size")
        private val KEY_EDITION_AT = longPreferencesKey("current_edition_at")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_RETENTION = intPreferencesKey("retention_days")
        private val KEY_PER_SOURCE = intPreferencesKey("per_source_limit")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
    }
}
