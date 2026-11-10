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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.privacy.PrivacySettings
import app.pixel.browser.core.data.privacy.PrivateAutoClose

/**
 * Trang "Duyệt ẩn danh" (spec 016 AC22): Switch chặn chụp màn hình, radio tự đóng sau khi rời app, bảng bảo đảm
 * (được / không được bảo đảm) và lời giải thích khi [privateAvailable] = false (WebView thiếu `MULTI_PROFILE`).
 */
@Composable
fun PrivateBrowsingPage(
    settings: PrivacySettings,
    privateAvailable: Boolean,
    onChange: (PrivacySettings) -> Unit,
) {
    val options =
        listOf(
            Triple(PrivateAutoClose.Never, "never", R.string.settings_private_autoclose_never),
            Triple(PrivateAutoClose.Minutes5, "5", R.string.settings_private_autoclose_5),
            Triple(PrivateAutoClose.Minutes30, "30", R.string.settings_private_autoclose_30),
        )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!privateAvailable) {
            Text(
                stringResource(R.string.settings_private_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp).testTag(SettingsTestTags.PRIVATE_UNAVAILABLE),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(SettingsTestTags.PRIVATE_SCREENSHOTS)
                .toggleable(
                    value = settings.blockPrivateScreenshots,
                    role = Role.Switch,
                    onValueChange = { onChange(settings.copy(blockPrivateScreenshots = it)) },
                ).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_private_screenshots), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_private_screenshots_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.blockPrivateScreenshots, onCheckedChange = null)
        }
        Text(
            stringResource(R.string.settings_private_autoclose_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            options.forEach { (value, tag, label) ->
                val selected = settings.privateAutoClose == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag(SettingsTestTags.privateAutoClose(tag))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { if (!selected) onChange(settings.copy(privateAutoClose = value)) },
                        ).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Column(
            Modifier
                .padding(16.dp)
                .testTag(SettingsTestTags.PRIVATE_GUARANTEE)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GuaranteeBlock(R.string.settings_private_guaranteed_title, R.string.settings_private_guaranteed)
            GuaranteeBlock(R.string.settings_private_not_guaranteed_title, R.string.settings_private_not_guaranteed)
        }
    }
}

@Composable
private fun GuaranteeBlock(
    title: Int,
    body: Int,
) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
