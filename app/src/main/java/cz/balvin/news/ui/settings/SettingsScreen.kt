package cz.balvin.news.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.balvin.news.R
import cz.balvin.news.data.prefs.SettingsStore
import cz.balvin.news.data.prefs.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))
            ChoiceRow(
                label = stringResource(R.string.settings_theme),
                value = stringResource(themeLabel(settings.theme)),
                options = ThemeMode.entries.map { it to stringResource(themeLabel(it)) },
                onSelect = viewModel::setTheme,
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_edition))
            ChoiceRow(
                label = stringResource(R.string.settings_edition_hour),
                value = hourLabel(settings.editionHour),
                options = SettingsStore.EDITION_HOUR_CHOICES.map { it to hourLabel(it) },
                onSelect = viewModel::setEditionHour,
            )
            ChoiceRow(
                label = stringResource(R.string.settings_edition_size),
                value = sizeLabel(settings.editionSize),
                options = SettingsStore.EDITION_SIZE_CHOICES.map { it to sizeLabel(it) },
                onSelect = viewModel::setEditionSize,
            )
            SwitchRow(
                label = stringResource(R.string.settings_notifications),
                checked = settings.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled,
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_volume))
            ChoiceRow(
                label = stringResource(R.string.settings_per_source),
                value = perSourceLabel(settings.perSourceLimit),
                options = SettingsStore.PER_SOURCE_CHOICES.map { it to perSourceLabel(it) },
                onSelect = viewModel::setPerSourceLimit,
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_storage))
            ChoiceRow(
                label = stringResource(R.string.settings_retention),
                value = stringResource(R.string.settings_days, settings.retentionDays),
                options = SettingsStore.RETENTION_CHOICES.map {
                    it to stringResource(R.string.settings_days, it)
                },
                onSelect = viewModel::setRetentionDays,
            )
            TextButton(
                onClick = viewModel::markAllRead,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.action_mark_all_read))
            }
        }
    }
}

@Composable
private fun perSourceLabel(limit: Int): String =
    if (limit <= 0) {
        stringResource(R.string.per_source_unlimited)
    } else {
        stringResource(R.string.per_source_count, limit)
    }

@Composable
private fun hourLabel(hour: Int): String = stringResource(R.string.settings_at_hour, hour)

@Composable
private fun sizeLabel(size: Int): String =
    if (size <= 0) {
        stringResource(R.string.per_source_unlimited)
    } else {
        stringResource(R.string.settings_articles, size)
    }

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
