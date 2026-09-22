package cz.balvin.news.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val refreshIntervalMinutes: Int = 60,
    val notificationsEnabled: Boolean = true,
    val retentionDays: Int = 30,
    /** Articles shown per source in the timeline; zero lifts the cap. */
    val perSourceLimit: Int = 5,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            theme = prefs[KEY_THEME]?.let { stored ->
                runCatching { ThemeMode.valueOf(stored) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            refreshIntervalMinutes = prefs[KEY_INTERVAL] ?: 60,
            notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
            retentionDays = prefs[KEY_RETENTION] ?: 30,
            perSourceLimit = prefs[KEY_PER_SOURCE] ?: 5,
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setRefreshInterval(minutes: Int) {
        context.dataStore.edit { it[KEY_INTERVAL] = minutes.coerceAtLeast(MIN_INTERVAL_MINUTES) }
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
        /** WorkManager refuses anything shorter for periodic work. */
        const val MIN_INTERVAL_MINUTES = 15

        val INTERVAL_CHOICES = listOf(15, 30, 60, 180, 360, 720)
        val RETENTION_CHOICES = listOf(7, 14, 30, 90)

        /** Zero means no cap. */
        val PER_SOURCE_CHOICES = listOf(3, 5, 10, 0)

        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_INTERVAL = intPreferencesKey("refresh_interval_minutes")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_RETENTION = intPreferencesKey("retention_days")
        private val KEY_PER_SOURCE = intPreferencesKey("per_source_limit")
    }
}
