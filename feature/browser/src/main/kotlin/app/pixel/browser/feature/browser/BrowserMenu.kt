package app.pixel.browser.feature.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/** Menu tối thiểu: Reload, New tab, Share (Share disabled khi URL rỗng/`about:blank`). */
@Composable
fun BrowserMenu(
    expanded: Boolean,
    canShare: Boolean,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    onNewTab: () -> Unit,
    onShare: () -> Unit,
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
    }
}
