package cz.balvin.news.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.balvin.news.data.local.Feed
import cz.balvin.news.data.repository.NewsRepository
import cz.balvin.news.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FeedsMessage {
    data object Duplicate : FeedsMessage
    data object Invalid : FeedsMessage
    data class Added(val title: String) : FeedsMessage
}

class FeedsViewModel(private val repository: NewsRepository) : ViewModel() {

    val feeds: StateFlow<List<Feed>> = repository.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isAdding = MutableStateFlow(false)
    val isAdding: StateFlow<Boolean> = _isAdding.asStateFlow()

    private val _message = MutableStateFlow<FeedsMessage?>(null)
    val message: StateFlow<FeedsMessage?> = _message.asStateFlow()

    /** Invokes [onDone] only once the feed validated, so the dialog can stay open on failure. */
    fun addFeed(url: String, title: String, category: String, onDone: () -> Unit) {
        if (_isAdding.value) return
        viewModelScope.launch {
            _isAdding.value = true
            val result = runCatching { repository.addFeed(url, title, category) }
                .getOrElse { NewsRepository.AddFeedResult.Invalid(it.message.orEmpty()) }
            _isAdding.value = false

            when (result) {
                is NewsRepository.AddFeedResult.Added -> {
                    _message.value = FeedsMessage.Added(result.feed.title)
                    onDone()
                }

                NewsRepository.AddFeedResult.Duplicate -> _message.value = FeedsMessage.Duplicate
                is NewsRepository.AddFeedResult.Invalid -> _message.value = FeedsMessage.Invalid
            }
        }
    }

    fun setEnabled(feed: Feed, enabled: Boolean) {
        viewModelScope.launch { repository.setFeedEnabled(feed.id, enabled) }
    }

    fun delete(feed: Feed) {
        viewModelScope.launch { repository.deleteFeed(feed) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { FeedsViewModel(appContainer.repository) }
        }
    }
}
