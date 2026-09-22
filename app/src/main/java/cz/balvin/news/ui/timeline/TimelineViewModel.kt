package cz.balvin.news.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.balvin.news.data.local.ArticleListItem
import cz.balvin.news.data.prefs.SettingsStore
import cz.balvin.news.data.repository.NewsRepository
import cz.balvin.news.data.repository.TimelineFilter
import cz.balvin.news.ui.common.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TimelineMessage {
    data class PartialFailure(val feedCount: Int, val reason: String) : TimelineMessage
    data object MarkedAllRead : TimelineMessage
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TimelineViewModel(
    private val repository: NewsRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val _filter = MutableStateFlow(TimelineFilter())
    val filter: StateFlow<TimelineFilter> = _filter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _message = MutableStateFlow<TimelineMessage?>(null)
    val message: StateFlow<TimelineMessage?> = _message.asStateFlow()

    val categories: StateFlow<List<String>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val articles: StateFlow<List<ArticleListItem>> = combine(
        _filter,
        settingsStore.settings.map { it.perSourceLimit }.distinctUntilChanged(),
    ) { filter, perSourceLimit -> filter.copy(perSourceLimit = perSourceLimit) }
        // Typing a query must not re-run the query on every keystroke.
        .debounce { if (it.query.isEmpty()) 0L else 200L }
        .distinctUntilChanged()
        .flatMapLatest(repository::observeTimeline)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun setCategory(category: String?) {
        _filter.value = _filter.value.copy(category = category)
    }

    fun setOnlyUnread(value: Boolean) {
        _filter.value = _filter.value.copy(onlyUnread = value, onlyBookmarked = false)
    }

    fun setOnlyBookmarked(value: Boolean) {
        _filter.value = _filter.value.copy(onlyBookmarked = value, onlyUnread = false)
    }

    fun clearFilters() {
        _filter.value = _filter.value.copy(onlyUnread = false, onlyBookmarked = false, category = null)
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val outcome = runCatching { repository.refreshAll() }.getOrNull()
            _isRefreshing.value = false
            if (outcome != null && outcome.hasFailures) {
                _message.value = TimelineMessage.PartialFailure(
                    feedCount = outcome.failures.size,
                    reason = outcome.failures.first().message,
                )
            }
        }
    }

    fun toggleBookmark(item: ArticleListItem) {
        viewModelScope.launch {
            repository.setBookmarked(item.article.id, !item.article.isBookmarked)
        }
    }

    fun setRead(id: Long, read: Boolean) {
        viewModelScope.launch { repository.setRead(id, read) }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllRead()
            _message.value = TimelineMessage.MarkedAllRead
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TimelineViewModel(appContainer.repository, appContainer.settingsStore)
            }
        }
    }
}
