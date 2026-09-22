package cz.balvin.news.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cz.balvin.news.data.prefs.ThemeMode

private val LightScheme = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3F2),
    onPrimaryContainer = InkBlueDark,
    secondary = Signal,
    onSecondary = Color.White,
    background = PaperLight,
    onBackground = Color(0xFF1A1C18),
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFEFECE5),
    onSurfaceVariant = Color(0xFF4A4C47),
    outline = OutlineLight,
)

private val DarkScheme = darkColorScheme(
    primary = InkBlueLight,
    onPrimary = Color.White,
    primaryContainer = InkBlueDark,
    onPrimaryContainer = Color(0xFFD5E3F2),
    secondary = SignalLight,
    onSecondary = Color(0xFF3A1206),
    background = PaperDark,
    onBackground = Color(0xFFE3E3DC),
    surface = SurfaceDark,
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF2A2C27),
    onSurfaceVariant = Color(0xFFC6C8C0),
    outline = OutlineDark,
)

@Composable
fun ZpravyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZpravyTypography,
        content = content,
    )
}
