package app.pixel.browser.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.site.DesktopSitePolicy

/**
 * Trang "Trang web máy tính" (spec 015 AC22): mặc định toàn cục (Switch), số ngoại lệ theo site và nút xóa
 * ngoại lệ (tắt khi không có). Đổi mặc định không tải lại tab đang mở.
 */
@Composable
fun DesktopSiteSettingsPage(policy: DesktopSitePolicy) {
    val defaultDesktop by policy.defaultDesktop.collectAsState()
    val overrides by policy.overrideCount.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(SettingsTestTags.DESKTOP_DEFAULT)
                .toggleable(
                    value = defaultDesktop,
                    role = Role.Switch,
                    onValueChange = policy::setDefault,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_desktop_default_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.settings_desktop_default_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = defaultDesktop, onCheckedChange = null)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(SettingsTestTags.DESKTOP_OVERRIDES)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                stringResource(R.string.settings_desktop_overrides_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                pluralStringResource(R.plurals.settings_desktop_overrides_count, overrides, overrides),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = policy::clearOverrides,
            enabled = overrides > 0,
            modifier =
                Modifier
                    .padding(
                        horizontal = 8.dp,
                    ).heightIn(min = 48.dp)
                    .testTag(SettingsTestTags.DESKTOP_CLEAR),
        ) {
            Text(stringResource(R.string.settings_desktop_clear))
        }
    }
}
