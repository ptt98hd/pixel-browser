package app.pixel.browser.feature.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pixel.browser.core.history.HistoryEntry
import app.pixel.browser.core.history.HistoryRange
import app.pixel.browser.core.omnibox.UrlDisplay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class HistoryActions(
    val onQueryChange: (String) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onOpenInNewTab: (String) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onClear: (HistoryRange) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Màn hình Lịch sử (stateless; chỉ giữ chữ tìm kiếm và dialog bằng `rememberSaveable`/`remember`). */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
) {
    // Bắt đầu từ truy vấn đang áp dụng (ViewModel cấp Activity giữ nó qua lần mở lại) để ô nhập khớp danh sách.
    var query by rememberSaveable { mutableStateOf(state.query) }
    var clearDialogOpen by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxSize().testTag(HistoryTestTags.SCREEN), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HistoryTopBar(
                clearEnabled = !(state.isEmpty && state.query.isEmpty()),
                onClose = actions.onClose,
                onClear = { clearDialogOpen = true },
            )
            SearchField(
                text = query,
                onTextChange = {
                    query = it
                    actions.onQueryChange(it)
                },
            )
            if (state.isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text =
                            stringResource(
                                if (state.query.isEmpty()) {
                                    R.string.history_empty_no_history
                                } else {
                                    R.string.history_empty_no_match
                                },
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).testTag(HistoryTestTags.EMPTY),
                    )
                }
            } else {
                HistoryList(state, actions)
            }
        }
    }
    if (clearDialogOpen) {
        ClearDialog(
            onConfirm = {
                clearDialogOpen = false
                actions.onClear(it)
            },
            onDismiss = { clearDialogOpen = false },
        )
    }
}

/** Nối [HistoryViewModel] với [HistoryScreen]; Back hệ thống đóng màn hình. */
@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onOpenUrl: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    val actions =
        remember(viewModel, onOpenUrl, onOpenInNewTab, onClose) {
            HistoryActions(
                onQueryChange = viewModel::onQueryChange,
                onOpenUrl = onOpenUrl,
                onOpenInNewTab = onOpenInNewTab,
                onDelete = viewModel::onDelete,
                onClear = viewModel::onClear,
                onLoadMore = viewModel::onLoadMore,
                onClose = onClose,
            )
        }
    HistoryScreen(state = state, actions = actions)
}

@Composable
private fun HistoryTopBar(
    clearEnabled: Boolean,
    onClose: () -> Unit,
    onClear: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag(HistoryTestTags.CLOSE)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_close))
        }
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
        )
        IconButton(
            onClick = onClear,
            enabled = clearEnabled,
            modifier = Modifier.size(48.dp).testTag(HistoryTestTags.CLEAR_BUTTON),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_clear_button))
        }
    }
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.history_search_hint)
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = label }
                .testTag(HistoryTestTags.SEARCH),
        placeholder = { Text(stringResource(R.string.history_search_hint), maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(
                    onClick = { onTextChange("") },
                    modifier = Modifier.size(48.dp).testTag(HistoryTestTags.SEARCH_CLEAR),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    actions: HistoryActions,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    // Làm mới "Hôm nay/Hôm qua" mỗi lần màn hình quay lại foreground (qua nửa đêm).
    var today by remember(zone) { mutableStateOf(LocalDate.now(zone)) }
    LifecycleResumeEffect(zone) {
        today = LocalDate.now(zone)
        onPauseOrDispose {}
    }
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val todayLabel = stringResource(R.string.history_today)
    val yesterdayLabel = stringResource(R.string.history_yesterday)
    LazyColumn(Modifier.fillMaxSize()) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                Text(
                    text =
                        when (day.date) {
                            today -> todayLabel
                            today.minusDays(1) -> yesterdayLabel
                            else -> day.date.format(formatter)
                        },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics { heading() }
                            .testTag(HistoryTestTags.day(day.date)),
                )
            }
            items(day.entries, key = { it.id }) { entry ->
                HistoryItem(entry, actions) { copyToClipboard(context, entry.url) }
            }
        }
        if (state.canLoadMore) {
            item(key = "load-more") {
                TextButton(
                    onClick = actions.onLoadMore,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(HistoryTestTags.LOAD_MORE),
                ) { Text(stringResource(R.string.history_load_more)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    entry: HistoryEntry,
    actions: HistoryActions,
    onCopy: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val host = UrlDisplay.host(entry.url)
    val title = entry.title.ifBlank { host.ifEmpty { entry.url } }
    val deleteLabel = stringResource(R.string.history_menu_delete)
    val newTabLabel = stringResource(R.string.history_menu_open_new_tab)
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { actions.onOpenUrl(entry.url) },
                        onLongClickLabel = stringResource(R.string.history_item_more_actions),
                        onLongClick = { menuOpen = true },
                    ).padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(deleteLabel) {
                                    actions.onDelete(entry.id)
                                    true
                                },
                                CustomAccessibilityAction(newTabLabel) {
                                    actions.onOpenInNewTab(entry.url)
                                    true
                                },
                            )
                    }.testTag(HistoryTestTags.item(entry.id)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LetterTile(host.ifEmpty { title })
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(newTabLabel) },
                onClick = {
                    menuOpen = false
                    actions.onOpenInNewTab(entry.url)
                },
                modifier = Modifier.testTag(HistoryTestTags.MENU_OPEN_NEW_TAB),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_menu_copy)) },
                onClick = {
                    menuOpen = false
                    onCopy()
                },
                modifier = Modifier.testTag(HistoryTestTags.MENU_COPY),
            )
            DropdownMenuItem(
                text = { Text(deleteLabel) },
                onClick = {
                    menuOpen = false
                    actions.onDelete(entry.id)
                },
                modifier = Modifier.testTag(HistoryTestTags.MENU_DELETE),
            )
        }
    }
}

/** Ô chữ cái đầu của host (thay favicon khi chưa có). */
@Composable
private fun LetterTile(label: String) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ClearDialog(
    onConfirm: (HistoryRange) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(HistoryRange.LastHour) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(HistoryTestTags.CLEAR_DIALOG),
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = {
            Column {
                HistoryRange.entries.forEach { range ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = range == selected, role = Role.RadioButton) { selected = range }
                            .testTag(HistoryTestTags.clearRange(range)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = range == selected, onClick = null)
                        Text(stringResource(range.label()), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, modifier = Modifier.testTag(HistoryTestTags.CLEAR_CONFIRM)) {
                Text(stringResource(R.string.history_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(HistoryTestTags.CLEAR_CANCEL)) {
                Text(stringResource(R.string.history_clear_cancel))
            }
        },
    )
}

private fun HistoryRange.label(): Int =
    when (this) {
        HistoryRange.LastHour -> R.string.history_clear_last_hour
        HistoryRange.LastDay -> R.string.history_clear_last_day
        HistoryRange.LastWeek -> R.string.history_clear_last_week
        HistoryRange.All -> R.string.history_clear_all
    }

/** Sao chép URL; từ Android 13 hệ thống tự báo, trước đó (API 31–32) hiện Toast. */
private fun copyToClipboard(
    context: Context,
    url: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.history_copied, Toast.LENGTH_SHORT).show()
    }
}
