package cz.balvin.news.ui.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.balvin.news.data.local.ArticleListItem
import cz.balvin.news.data.repository.NewsRepository
import cz.balvin.news.ui.common.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArticleViewModel(
    private val repository: NewsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>(ARG_ARTICLE_ID) ?: 0L

    val article: StateFlow<ArticleListItem?> = repository.observeArticle(articleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Opening an article is what marks it read; the list only optimistically
        // does the same so the row updates before navigation settles.
        if (articleId != 0L) viewModelScope.launch { repository.setRead(articleId, true) }
    }

    fun toggleBookmark() {
        val current = article.value ?: return
        viewModelScope.launch {
            repository.setBookmarked(current.article.id, !current.article.isBookmarked)
        }
    }

    companion object {
        const val ARG_ARTICLE_ID = "articleId"

        val Factory = viewModelFactory {
            initializer { ArticleViewModel(appContainer.repository, createSavedStateHandle()) }
        }
    }
}
