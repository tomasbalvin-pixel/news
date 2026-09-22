package cz.balvin.news

import android.app.Application
import cz.balvin.news.work.Notifier
import cz.balvin.news.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NewsApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannel(this)

        applicationScope.launch {
            container.repository.seedDefaultFeedsIfEmpty()
            val settings = container.settingsStore.settings.first()
            RefreshScheduler.scheduleDailyEdition(this@NewsApplication, settings.editionHour)
        }
    }
}
