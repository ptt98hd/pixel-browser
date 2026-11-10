package app.pixel.browser.feature.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

/**
 * Menu tối thiểu: Reload, New tab, Share, Lịch sử (luôn bật, spec 012) (Share disabled khi URL rỗng/`about:blank`),
 * Theme (mở `ThemeDialog`, spec 011), "Đặt làm trình duyệt mặc định" khi [showSetDefault] (spec 006 AC18) và
 * Cài đặt (luôn bật, cuối menu, spec 014).
 */
@Composable
fun BrowserMenu(
    expanded: Boolean,
    canShare: Boolean,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    onNewTab: () -> Unit,
    onShare: () -> Unit,
    showSetDefault: Boolean = false,
    onSetDefault: () -> Unit = {},
    onTheme: () -> Unit = {},
    onHistory: () -> Unit = {},
    onSettings: () -> Unit = {},
    canBookmark: Boolean = false,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    canFind: Boolean = false,
    onFind: () -> Unit = {},
    canToggleDesktopSite: Boolean = false,
    isDesktopSite: Boolean = false,
    onToggleDesktopSite: () -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_reload)) },
            onClick = onReload,
            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_RELOAD),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_new_tab)) },
            onClick = onNewTab,
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_NEW_TAB),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_share)) },
            onClick = onShare,
            enabled = canShare,
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_SHARE),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_find_in_page)) },
            onClick = onFind,
            enabled = canFind,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_FIND),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_desktop_site)) },
            onClick = onToggleDesktopSite,
            enabled = canToggleDesktopSite,
            trailingIcon = {
                Checkbox(
                    checked = isDesktopSite,
                    onCheckedChange = null,
                    enabled = canToggleDesktopSite,
                )
            },
            modifier =
                Modifier
                    .testTag(BrowserTestTags.MENU_DESKTOP_SITE)
                    .semantics {
                        role = Role.Checkbox
                        toggleableState = if (isDesktopSite) ToggleableState.On else ToggleableState.Off
                    },
        )
        if (showSetDefault) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_set_default)) },
                onClick = onSetDefault,
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                modifier = Modifier.testTag(BrowserTestTags.MENU_SET_DEFAULT),
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(if (isBookmarked) R.string.browser_bookmark_edit else R.string.browser_bookmark_add),
                )
            },
            onClick = onToggleBookmark,
            enabled = canBookmark,
            leadingIcon = { Icon(if (isBookmarked) Icons.Filled.Star else StarOutlineIcon, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_BOOKMARK),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_bookmarks)) },
            onClick = onBookmarks,
            leadingIcon = { Icon(BookmarksIcon, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_BOOKMARKS),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_history)) },
            onClick = onHistory,
            leadingIcon = { Icon(HistoryIcon, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_HISTORY),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_theme)) },
            onClick = onTheme,
            leadingIcon = { Icon(ThemeIcon, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_THEME),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_settings)) },
            onClick = onSettings,
            leadingIcon = { Icon(SettingsIcon, contentDescription = null) },
            modifier = Modifier.testTag(BrowserTestTags.MENU_SETTINGS),
        )
    }
}

/** Icon "contrast" (Material Symbols, Apache-2.0) – nửa hình tròn tô đậm. Không có trong material-icons-core. */
private val ThemeIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Contrast",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M12,22c5.52,0 10,-4.48 10,-10S17.52,2 12,2S2,6.48 2,12S6.48,22 12,22z" +
                        "M13,4.07c3.94,0.49 7,3.85 7,7.93s-3.05,7.44 -7,7.93V4.07z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}

/** Sao rỗng (Material Symbols "star", Apache-2.0); `material-icons-core` chỉ có sao đặc. */
private val StarOutlineIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "StarOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M22,9.24l-7.19,-0.62L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21L12,17.27L18.18,21" +
                        "l-1.63,-7.03L22,9.24z" +
                        "M12,15.4l-3.76,2.27l1,-4.28l-3.32,-2.88l4.38,-0.38L12,6.1l1.71,4.04l4.38,0.38" +
                        "l-3.32,2.88l1,4.28L12,15.4z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}

/** Icon "bookmarks" (Material Symbols, Apache-2.0). */
private val BookmarksIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Bookmarks",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes("M17,3H7C5.9,3 5,3.9 5,5v16l7,-3l7,3V5C19,3.9 18.1,3 17,3z"),
            fill = SolidColor(Color.Black),
        ).build()
}
