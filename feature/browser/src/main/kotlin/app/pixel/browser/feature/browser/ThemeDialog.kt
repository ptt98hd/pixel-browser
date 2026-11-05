package app.pixel.browser.feature.browser

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings

private val ROW_MIN_HEIGHT = 56.dp

private val modeOptions =
    listOf(
        Triple(ThemeMode.System, R.string.browser_theme_system, BrowserTestTags.THEME_MODE_SYSTEM),
        Triple(ThemeMode.Light, R.string.browser_theme_light, BrowserTestTags.THEME_MODE_LIGHT),
        Triple(ThemeMode.Dark, R.string.browser_theme_dark, BrowserTestTags.THEME_MODE_DARK),
    )

/**
 * Hộp thoại chọn theme (spec 011 AC11): Theo hệ thống/Sáng/Tối (chọn → [onChange] rồi [onDismiss]) và công tắc
 * dynamic color (đổi → [onChange], dialog vẫn mở). Back/chạm ngoài → [onDismiss], không gọi [onChange].
 * Giá trị tiếp theo tính từ lựa chọn mới nhất trong dialog (không chờ [settings] đi vòng qua store), nên hai lần
 * chạm liên tiếp trước khi recompose vẫn cho kết quả đúng.
 */
@Composable
fun ThemeDialog(
    settings: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember(settings) { mutableStateOf(settings) }
    val change: (ThemeSettings) -> Unit = { next ->
        current = next
        onChange(next)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(BrowserTestTags.THEME_DIALOG),
        title = { Text(stringResource(R.string.browser_theme)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    modeOptions.forEach { (mode, label, tag) ->
                        ModeRow(
                            label = label,
                            selected = current.mode == mode,
                            onClick = {
                                if (current.mode != mode) change(current.copy(mode = mode))
                                onDismiss()
                            },
                            modifier = Modifier.testTag(tag),
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DynamicColorRow(
                    checked = current.dynamicColor,
                    onToggle = { change(current.copy(dynamicColor = !current.dynamicColor)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_theme_done)) }
        },
    )
}

@Composable
private fun ModeRow(
    @StringRes label: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DynamicColorRow(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
                .testTag(BrowserTestTags.THEME_DYNAMIC),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.browser_theme_dynamic), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.browser_theme_dynamic_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
