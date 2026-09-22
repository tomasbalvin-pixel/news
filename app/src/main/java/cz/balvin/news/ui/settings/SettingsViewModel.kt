package cz.balvin.news.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.balvin.news.NewsApplication
import cz.balvin.news.data.prefs.Settings
import cz.balvin.news.data.prefs.SettingsStore
import cz.balvin.news.data.prefs.ThemeMode
import cz.balvin.news.data.repository.NewsRepository
import cz.balvin.news.work.RefreshScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val application: Application,
    private val settingsStore: SettingsStore,
    private val repository: NewsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setTheme(mode) }
    }

    /** Changing the hour also reschedules; the stored value alone changes nothing. */
    fun setEditionHour(hour: Int) {
        viewModelScope.launch {
            settingsStore.setEditionHour(hour)
            RefreshScheduler.scheduleDailyEdition(application, hour)
        }
    }

    fun setEditionSize(size: Int) {
        viewModelScope.launch { settingsStore.setEditionSize(size) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setPerSourceLimit(limit: Int) {
        viewModelScope.launch { settingsStore.setPerSourceLimit(limit) }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsStore.setRetentionDays(days)
            repository.prune(days)
        }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as NewsApplication
                SettingsViewModel(
                    application = application,
                    settingsStore = application.container.settingsStore,
                    repository = application.container.repository,
                )
            }
        }
    }
}
