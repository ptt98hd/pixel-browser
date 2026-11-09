package app.pixel.browser.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings

/** Trang Giao diện (spec 014 AC15): cùng hành vi với hộp thoại theme ở menu (spec 011). */
@Composable
fun AppearancePage(
    settings: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
) {
    val modes =
        listOf(
            Triple(ThemeMode.System, "system", R.string.appearance_mode_system),
            Triple(ThemeMode.Light, "light", R.string.appearance_mode_light),
            Triple(ThemeMode.Dark, "dark", R.string.appearance_mode_dark),
        )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.selectableGroup()) {
            modes.forEach { (mode, tag, label) ->
                val selected = settings.mode == mode
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag(SettingsTestTags.appearanceMode(tag))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { if (!selected) onChange(settings.copy(mode = mode)) },
                        ).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(SettingsTestTags.APPEARANCE_DYNAMIC)
                .toggleable(
                    value = settings.dynamicColor,
                    role = Role.Switch,
                    onValueChange = { onChange(settings.copy(dynamicColor = it)) },
                ).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.appearance_dynamic), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.appearance_dynamic_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.dynamicColor, onCheckedChange = null)
        }
    }
}
