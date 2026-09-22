package cz.balvin.news

import android.content.Context
import cz.balvin.news.data.local.AppDatabase
import cz.balvin.news.data.prefs.SettingsStore
import cz.balvin.news.data.remote.HttpFeedService
import cz.balvin.news.data.repository.NewsRepository

/**
 * Hand-rolled dependency graph. The app has one repository and one settings
 * store; a DI framework would be more machinery than the graph is worth.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase by lazy { AppDatabase.build(context) }

    val settingsStore: SettingsStore by lazy { SettingsStore(context.applicationContext) }

    val repository: NewsRepository by lazy {
        NewsRepository(
            feedDao = database.feedDao(),
            articleDao = database.articleDao(),
            feedService = HttpFeedService(),
        )
    }
}
