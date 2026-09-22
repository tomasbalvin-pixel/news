package cz.balvin.news.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.balvin.news.NewsApplication
import cz.balvin.news.data.prefs.Settings
import cz.balvin.news.ui.navigation.NewsNavHost
import cz.balvin.news.ui.theme.ZpravyTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NewsApplication).container

        setContent {
            val settings by container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = Settings())

            // Asked once the user has notifications switched on, not at cold start.
            LaunchedEffect(settings.notificationsEnabled) {
                if (settings.notificationsEnabled) requestNotificationPermission()
            }

            ZpravyTheme(themeMode = settings.theme) {
                NewsNavHost()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
