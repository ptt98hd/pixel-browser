package app.pixel.browser.feature.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.omnibox.UrlDisplay

/** Tiêu đề hiển thị của tab: title, nếu rỗng thì host của URL (AC27). */
internal fun TabItem.displayTitle(): String = title.ifBlank { UrlDisplay.host(url) }

/** Tab switcher tối thiểu dạng bottom sheet (AC27–AC30). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(
    tabs: List<TabItem>,
    selectedTabId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(BrowserTestTags.TAB_SHEET),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.browser_tabs_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewTab, modifier = Modifier.testTag(BrowserTestTags.TAB_SHEET_NEW)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.browser_new_tab))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(tabs, key = { it.id }) { tab ->
                TabRow(tab, selected = tab.id == selectedTabId, onSelect = onSelect, onClose = onClose)
            }
        }
    }
}

@Composable
private fun TabRow(
    tab: TabItem,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    val title = tab.displayTitle().ifEmpty { stringResource(R.string.browser_untitled_tab) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.id) })
                .padding(start = 24.dp, end = 12.dp)
                .testTag(BrowserTestTags.tabItem(tab.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.testTag(BrowserTestTags.tabClose(tab.id))) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.browser_close_tab, title))
        }
    }
}
