package app.pixel.browser.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val sourceUrl: String,
)

/** Giấy phép của dự án (theo `CLAUDE.md`). */
private const val PROJECT_LICENSE = "GPL-3.0"

/** Trang Giới thiệu (spec 014 AC16): phiên bản, giấy phép dự án, mã nguồn và thư viện bên thứ ba. */
@Composable
fun AboutPage(
    info: AppInfo,
    scope: SettingsPageScope,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AboutRow(
            SettingsTestTags.ABOUT_VERSION,
            stringResource(R.string.about_version, info.versionName, info.versionCode),
            onClick = null,
        )
        AboutRow(
            SettingsTestTags.ABOUT_LICENSE,
            stringResource(R.string.about_license, PROJECT_LICENSE),
            onClick = null,
        )
        AboutRow(
            SettingsTestTags.ABOUT_SOURCE,
            stringResource(R.string.about_source),
            summary = info.sourceUrl,
            onClick = { scope.openUrl(info.sourceUrl) },
        )
        Text(
            stringResource(R.string.about_libraries_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
        )
        Column(Modifier.testTag(SettingsTestTags.ABOUT_LICENSES_LIST)) {
            Licenses.all.forEachIndexed { index, entry ->
                AboutRow(
                    SettingsTestTags.aboutLicense(index),
                    entry.name,
                    summary = entry.license,
                    onClick = { scope.openUrl(entry.url) },
                )
            }
        }
    }
}

@Composable
private fun AboutRow(
    tag: String,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {}
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
