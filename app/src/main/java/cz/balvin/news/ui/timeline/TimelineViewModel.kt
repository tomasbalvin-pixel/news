package cz.balvin.news.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.balvin.news.data.ai.Digest
import cz.balvin.news.data.ai.DigestService
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TimelineMessage {
    data object NoApiKey : TimelineMessage
    data class DigestFailed(val reason: String) : TimelineMessage
    data class PartialFailure(val feedCount: Int, val reason: String) : TimelineMessage
    data object MarkedAllRead : TimelineMessage
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TimelineViewModel(
    private val repository: NewsRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _filter = MutableStateFlow(TimelineFilter())
    val filter: StateFlow<TimelineFilter> = _filter.asStateFlow()

    /** The edition is the default view; everything else stays one chip away. */
    private val _editionOnly = MutableStateFlow(true)
    val editionOnly: StateFlow<Boolean> = _editionOnly.asStateFlow()

    private val _isSummarising = MutableStateFlow(false)
    val isSummarising: StateFlow<Boolean> = _isSummarising.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _message = MutableStateFlow<TimelineMessage?>(null)
    val message: StateFlow<TimelineMessage?> = _message.asStateFlow()

    val categories: StateFlow<List<String>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val digest: StateFlow<Digest?> = settingsStore.settings
        .map { it.currentEditionAt }
        .distinctUntilChanged()
        .flatMapLatest(repository::observeDigest)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val articles: StateFlow<List<ArticleListItem>> = combine(
        _filter,
        _editionOnly,
        settingsStore.settings,
    ) { filter, editionOnly, settings ->
        filter.copy(
            perSourceLimit = settings.perSourceLimit,
            since = if (editionOnly) settings.currentEditionAt else 0,
            limit = if (editionOnly) settings.editionSize else 0,
        )
    }
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

    /** Rebuilds the digest for the current edition from what is already stored. */
    fun summarise() {
        if (_isSummarising.value) return
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val editionAt = settings.currentEditionAt
            if (editionAt == 0L) return@launch

            _isSummarising.value = true
            val result = runCatching {
                repository.refreshDigest(
                    editionAt = editionAt,
                    apiKey = settings.apiKey,
                    perSourceLimit = settings.perSourceLimit,
                    editionSize = settings.editionSize,
                )
            }.getOrElse { DigestService.Result.Failure(it.message.orEmpty()) }
            _isSummarising.value = false

            _message.value = when (result) {
                is DigestService.Result.Success -> null
                DigestService.Result.NoApiKey -> TimelineMessage.NoApiKey
                is DigestService.Result.Failure -> TimelineMessage.DigestFailed(result.message)
            }
        }
    }

    fun setEditionOnly(value: Boolean) {
        _editionOnly.value = value
    }

    fun clearFilters() {
        _filter.value = _filter.value.copy(onlyUnread = false, onlyBookmarked = false, category = null)
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            // A pull before the first scheduled run opens edition one; later pulls
            // top up the edition already on screen instead of starting a new one.
            if (settingsStore.settings.first().currentEditionAt == 0L) {
                settingsStore.startEdition(System.currentTimeMillis())
            }
            val outcome = runCatching { repository.refreshAll() }.getOrNull()
            _isRefreshing.value = false
            if (outcome != null && outcome.hasFailures) {
                _message.value = TimelineMessage.PartialFailure(
                    feedCount = outcome.failures.size,
                    reason = outcome.failures.first().message,
                )
            }
            summarise()
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
