package cz.balvin.news.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.balvin.news.R
import cz.balvin.news.data.local.ArticleListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenArticle: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = viewModel(factory = TimelineViewModel.Factory),
) {
    val articles by viewModel.articles.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()

    val messageText = when (val current = message) {
        is TimelineMessage.PartialFailure -> stringResource(R.string.refresh_partial, current.feedCount)
        TimelineMessage.MarkedAllRead -> stringResource(R.string.marked_all_read)
        null -> null
    }
    LaunchedEffect(messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.consumeMessage()
    }

    // A new filter should start the list at the top, not mid-scroll.
    LaunchedEffect(filter.category, filter.onlyUnread, filter.onlyBookmarked, filter.query) {
        listState.scrollToItem(0)
    }

    Scaffold(
        modifier = modifier
            .padding(bottom = contentPadding.calculateBottomPadding())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    if (searchVisible) {
                        SearchField(
                            query = filter.query,
                            onQueryChange = viewModel::setQuery,
                        )
                    } else {
                        Column {
                            Text(stringResource(R.string.app_name))
                            if (unreadCount > 0) {
                                Text(
                                    text = "$unreadCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) viewModel.setQuery("")
                        }
                    ) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (searchVisible) R.string.action_close else R.string.action_search
                            ),
                        )
                    }
                    IconButton(onClick = viewModel::markAllRead) {
                        Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = stringResource(R.string.action_mark_all_read),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                FilterRow(
                    categories = categories,
                    selectedCategory = filter.category,
                    onlyUnread = filter.onlyUnread,
                    onlyBookmarked = filter.onlyBookmarked,
                    onCategory = viewModel::setCategory,
                    onUnread = viewModel::setOnlyUnread,
                    onBookmarked = viewModel::setOnlyBookmarked,
                    onClearAll = viewModel::clearFilters,
                )

                if (articles.isEmpty()) {
                    EmptyState(
                        searching = filter.query.isNotBlank(),
                        onRefresh = viewModel::refresh,
                    )
                } else {
                    ArticleList(
                        articles = articles,
                        state = listState,
                        onOpen = { item ->
                            viewModel.setRead(item.article.id, true)
                            onOpenArticle(item.article.id)
                        },
                        onToggleBookmark = viewModel::toggleBookmark,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleList(
    articles: List<ArticleListItem>,
    state: LazyListState,
    onOpen: (ArticleListItem) -> Unit,
    onToggleBookmark: (ArticleListItem) -> Unit,
) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(articles, key = { it.article.id }) { item ->
            ArticleCard(
                item = item,
                onClick = { onOpen(item) },
                onToggleBookmark = { onToggleBookmark(item) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun FilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onlyUnread: Boolean,
    onlyBookmarked: Boolean,
    onCategory: (String?) -> Unit,
    onUnread: (Boolean) -> Unit,
    onBookmarked: (Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !onlyUnread && !onlyBookmarked && selectedCategory == null,
            onClick = onClearAll,
            label = { Text(stringResource(R.string.filter_all)) },
        )
        FilterChip(
            selected = onlyUnread,
            onClick = { onUnread(!onlyUnread) },
            label = { Text(stringResource(R.string.filter_unread)) },
        )
        FilterChip(
            selected = onlyBookmarked,
            onClick = { onBookmarked(!onlyBookmarked) },
            label = { Text(stringResource(R.string.filter_bookmarked)) },
        )
        categories.forEach { category ->
            val selected = selectedCategory == category
            FilterChip(
                selected = selected,
                onClick = { onCategory(if (selected) null else category) },
                label = { Text(category) },
            )
        }
    }
}

/**
 * Pull-to-refresh is driven by nested scroll, so an empty screen still has to be
 * a scrollable — a plain Box swallows the gesture and the first launch, which is
 * exactly when the timeline is empty, offers no way to load anything. The list
 * dispatches nested scroll even with nothing to scroll, and the button covers
 * the case where the gesture is not discovered at all.
 */
@Composable
private fun EmptyState(searching: Boolean, onRefresh: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.fillParentMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(
                        if (searching) R.string.empty_search else R.string.empty_timeline
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!searching) {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(stringResource(R.string.action_refresh))
                    }
                }
            }
        }
    }
}
