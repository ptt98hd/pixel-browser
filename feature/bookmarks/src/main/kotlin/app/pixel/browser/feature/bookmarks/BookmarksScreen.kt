package app.pixel.browser.feature.bookmarks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkFolder
import app.pixel.browser.core.bookmarks.BookmarkNode
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.DeletedTree
import app.pixel.browser.core.bookmarks.FolderOption
import app.pixel.browser.core.bookmarks.ROOT_ID
import app.pixel.browser.core.omnibox.UrlDisplay
import kotlinx.coroutines.launch

data class BookmarksActions(
    val onOpenFolder: (Long) -> Unit = {},
    /** Nút "lên" trên thanh tiêu đề và Back hệ thống ở thư mục con. */
    val onNavigateUp: () -> Boolean = { false },
    val onQueryChange: (String) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onOpenInNewTab: (String) -> Unit = {},
    /** Mở hộp thoại sửa dấu trang (`BookmarksRoute` hiển thị `BookmarkEditDialog`). */
    val onEditBookmark: (Long) -> Unit = {},
    val onCreateFolder: (String) -> Unit = {},
    val onRename: (Long, String) -> Unit = { _, _ -> },
    val onMove: (Long, Long) -> Unit = { _, _ -> },
    /** Nguồn danh sách thư mục đích cho "Di chuyển": tham số là gốc cây con bị loại. */
    val folderOptions: suspend (excludeSubtreeOf: Long?) -> List<FolderOption> = { emptyList() },
    val onDelete: (Long) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoExpired: () -> Unit = {},
    /** URI người dùng chọn để nhập; `null` = hủy. */
    val onImportPicked: (String?) -> Unit = {},
    val onExportPicked: (String?) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onClose: () -> Unit = {},
)

private val IMPORT_MIME_TYPES = arrayOf("text/html", "application/xhtml+xml", "*/*")
private const val NO_ID = -1L

private enum class FolderDialogMode { None, New, Rename }

/** Màn hình Dấu trang (stateless; chỉ giữ ô tìm kiếm và hộp thoại bằng `rememberSaveable`). */
@Composable
fun BookmarksScreen(
    state: BookmarksUiState,
    actions: BookmarksActions,
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    var searchText by rememberSaveable { mutableStateOf(state.query) }
    var folderDialog by rememberSaveable { mutableStateOf(FolderDialogMode.None) }
    var folderDialogTarget by rememberSaveable { mutableLongStateOf(NO_ID) }
    var folderDialogName by rememberSaveable { mutableStateOf("") }
    var moveTarget by rememberSaveable { mutableLongStateOf(NO_ID) }
    val currentActions by rememberUpdatedState(actions)
    val currentExportName by rememberUpdatedState(state.exportFileName)

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            currentActions.onImportPicked(uri?.toString())
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
            currentActions.onExportPicked(uri?.toString())
        }
    val closeSearch = {
        searching = false
        searchText = ""
        actions.onQueryChange("")
    }
    BackHandler(enabled = searching, onBack = closeSearch)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            val tag = if (state.message is BookmarksMessage.ImportDone) BookmarksTestTags.IMPORT_RESULT else null
            scope.launch { snackbarHostState.showSnackbar(ScreenVisuals(messageText, null, tag)) }
            actions.onMessageShown()
        }
    }
    val deletedText = stringResource(R.string.bookmarks_deleted)
    val undoLabel = stringResource(R.string.bookmarks_undo)
    LaunchedEffect(state.pendingUndo) {
        if (state.pendingUndo != null) {
            val result = snackbarHostState.showSnackbar(ScreenVisuals(deletedText, undoLabel, null))
            if (result == SnackbarResult.ActionPerformed) currentActions.onUndo() else currentActions.onUndoExpired()
        }
    }

    Surface(modifier.fillMaxSize().testTag(BookmarksTestTags.SCREEN), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                if (searching) {
                    SearchBar(
                        text = searchText,
                        onTextChange = {
                            searchText = it
                            actions.onQueryChange(it)
                        },
                        onClose = closeSearch,
                    )
                } else {
                    TopBar(
                        state = state,
                        onUp = { actions.onNavigateUp() },
                        onSearch = { searching = true },
                        onClose = actions.onClose,
                    )
                    ToolBar(
                        onNewFolder = {
                            folderDialogName = ""
                            folderDialogTarget = NO_ID
                            folderDialog = FolderDialogMode.New
                        },
                        onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                        onExport = { exportLauncher.launch(currentExportName) },
                    )
                }
                BookmarksList(
                    state = state,
                    actions = actions,
                    onRename = { folder ->
                        folderDialogName = folder.title
                        folderDialogTarget = folder.id
                        folderDialog = FolderDialogMode.Rename
                    },
                    onMove = { moveTarget = it },
                )
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter)) { data ->
                val visuals = data.visuals as? ScreenVisuals
                Snackbar(
                    action =
                        data.visuals.actionLabel?.let { label ->
                            {
                                TextButton(
                                    onClick = { data.performAction() },
                                    modifier = Modifier.testTag(BookmarksTestTags.UNDO),
                                ) { Text(label) }
                            }
                        },
                ) {
                    val textModifier = visuals?.tag?.let { Modifier.testTag(it) } ?: Modifier
                    Text(data.visuals.message, textModifier)
                }
            }
        }
    }
    when (folderDialog) {
        FolderDialogMode.None -> {
            Unit
        }

        FolderDialogMode.New, FolderDialogMode.Rename -> {
            val renaming = folderDialog == FolderDialogMode.Rename
            FolderNameDialog(
                heading =
                    stringResource(
                        if (renaming) R.string.bookmarks_folder_rename_title else R.string.bookmarks_folder_new_title,
                    ),
                initialName = folderDialogName,
                onConfirm = { name ->
                    folderDialog = FolderDialogMode.None
                    if (renaming) actions.onRename(folderDialogTarget, name) else actions.onCreateFolder(name)
                },
                onDismiss = { folderDialog = FolderDialogMode.None },
            )
        }
    }
    if (moveTarget != NO_ID) {
        val target = moveTarget
        MoveDialog(
            load = { actions.folderOptions(target) },
            onPick = { parent ->
                moveTarget = NO_ID
                actions.onMove(target, parent)
            },
            onDismiss = { moveTarget = NO_ID },
        )
    }
}

/** Nối [BookmarksViewModel] với [BookmarksScreen]; Back: thoát tìm kiếm (trong màn hình) → lên một cấp → đóng. */
@Composable
fun BookmarksRoute(
    viewModel: BookmarksViewModel,
    onOpenUrl: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableLongStateOf(NO_ID) }
    var editError by remember { mutableStateOf<BookmarkEditError?>(null) }
    var editOptions by remember { mutableStateOf<List<FolderOption>>(emptyList()) }
    val scope = rememberCoroutineScope()
    BackHandler { if (!viewModel.navigateUp()) onClose() }
    // Rời màn hình khi snackbar hoàn tác còn hiện: bỏ cây chờ hoàn tác để lần mở sau không hiện lại.
    DisposableEffect(viewModel) { onDispose { viewModel.onUndoExpired() } }
    val actions =
        remember(viewModel, onOpenUrl, onOpenInNewTab, onClose) {
            BookmarksActions(
                onOpenFolder = viewModel::openFolder,
                onNavigateUp = viewModel::navigateUp,
                onQueryChange = viewModel::onQueryChange,
                onOpenUrl = onOpenUrl,
                onOpenInNewTab = onOpenInNewTab,
                onEditBookmark = {
                    editError = null
                    editingId = it
                },
                onCreateFolder = viewModel::createFolder,
                onRename = viewModel::rename,
                onMove = viewModel::move,
                folderOptions = viewModel::folderOptions,
                onDelete = viewModel::delete,
                onUndo = viewModel::undoDelete,
                onUndoExpired = viewModel::onUndoExpired,
                onImportPicked = viewModel::import,
                onExportPicked = viewModel::export,
                onMessageShown = viewModel::onMessageShown,
                onClose = onClose,
            )
        }
    BookmarksScreen(state = state, actions = actions)
    val editing = editingId
    if (editing != NO_ID) {
        val bookmark =
            (state.children.firstOrNull { it.id == editing } as? Bookmark)
                ?: state.results?.firstOrNull { it.bookmark.id == editing }?.bookmark
        LaunchedEffect(editing) { editOptions = viewModel.folderOptions(null) }
        if (bookmark == null) {
            editingId = NO_ID
        } else {
            BookmarkEditDialog(
                title = bookmark.title,
                url = bookmark.url,
                folderId = bookmark.parentId ?: ROOT_ID,
                options = editOptions,
                error = editError,
                onSave = { title, url, folderId ->
                    scope.launch {
                        when (viewModel.edit(editing, title, url, folderId)) {
                            is BookmarkResult.Ok -> editingId = NO_ID
                            BookmarkResult.InvalidUrl -> editError = BookmarkEditError.InvalidUrl
                            BookmarkResult.InvalidTitle -> editError = BookmarkEditError.InvalidTitle
                            else -> editError = BookmarkEditError.NotFound
                        }
                    }
                },
                onRemove = {
                    editingId = NO_ID
                    viewModel.delete(editing)
                },
                onDismiss = { editingId = NO_ID },
            )
        }
    }
}

private class ScreenVisuals(
    override val message: String,
    override val actionLabel: String?,
    val tag: String?,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
}

@Composable
private fun messageText(message: BookmarksMessage): String =
    when (message) {
        is BookmarksMessage.ImportDone -> {
            pluralStringResource(R.plurals.bookmarks_import_result, message.imported, message.imported, message.skipped)
        }

        BookmarksMessage.ImportTooLarge -> {
            stringResource(R.string.bookmarks_import_too_large)
        }

        BookmarksMessage.ImportUnreadable -> {
            stringResource(R.string.bookmarks_import_unreadable)
        }

        BookmarksMessage.ImportNotBookmarkFile -> {
            stringResource(R.string.bookmarks_import_not_bookmarks)
        }

        BookmarksMessage.ImportFull -> {
            stringResource(R.string.bookmarks_import_full)
        }

        is BookmarksMessage.ExportDone -> {
            pluralStringResource(R.plurals.bookmarks_export_done, message.count, message.count)
        }

        BookmarksMessage.ExportFailed -> {
            stringResource(R.string.bookmarks_export_failed)
        }

        BookmarksMessage.OperationFailed -> {
            stringResource(R.string.bookmarks_operation_failed)
        }
    }

@Composable
private fun TopBar(
    state: BookmarksUiState,
    onUp: () -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.folderId != ROOT_ID) {
            IconButton(onClick = onUp, modifier = Modifier.size(48.dp).testTag(BookmarksTestTags.UP)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.bookmarks_up))
            }
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .semantics { heading() }
                    .testTag(BookmarksTestTags.TITLE),
        )
        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp).testTag(BookmarksTestTags.SEARCH)) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.bookmarks_search))
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag(BookmarksTestTags.CLOSE)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.bookmarks_close))
        }
    }
}

@Composable
private fun ToolBar(
    onNewFolder: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onNewFolder, modifier = Modifier.size(48.dp).testTag(BookmarksTestTags.NEW_FOLDER)) {
            Icon(NewFolderIcon, contentDescription = stringResource(R.string.bookmarks_new_folder))
        }
        IconButton(onClick = onImport, modifier = Modifier.size(48.dp).testTag(BookmarksTestTags.IMPORT)) {
            Icon(ImportIcon, contentDescription = stringResource(R.string.bookmarks_import))
        }
        IconButton(onClick = onExport, modifier = Modifier.size(48.dp).testTag(BookmarksTestTags.EXPORT)) {
            Icon(ExportIcon, contentDescription = stringResource(R.string.bookmarks_export))
        }
    }
}

@Composable
private fun SearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val label = stringResource(R.string.bookmarks_search_hint)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.bookmarks_search_close),
            )
        }
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .focusRequester(focus)
                    .semantics { contentDescription = label }
                    .testTag(BookmarksTestTags.SEARCH_FIELD),
            placeholder = { Text(label, maxLines = 1) },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSearch = {}),
        )
    }
}

@Composable
private fun BookmarksList(
    state: BookmarksUiState,
    actions: BookmarksActions,
    onRename: (BookmarkFolder) -> Unit,
    onMove: (Long) -> Unit,
) {
    val results = state.results
    val empty = if (results != null) results.isEmpty() else state.children.isEmpty()
    if (empty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text =
                    stringResource(
                        if (results != null) R.string.bookmarks_empty_search else R.string.bookmarks_empty_folder,
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp).testTag(BookmarksTestTags.EMPTY),
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (results != null) {
            items(results, key = { it.bookmark.id }) { hit ->
                BookmarkRow(hit.bookmark, hit.folderTitle, actions, onMove)
            }
        } else {
            items(state.children, key = { it.id }) { node ->
                when (node) {
                    is BookmarkFolder -> FolderRow(node, state.childCounts[node.id] ?: 0, actions, onRename, onMove)
                    is Bookmark -> BookmarkRow(node, null, actions, onMove)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: BookmarkFolder,
    count: Int,
    actions: BookmarksActions,
    onRename: (BookmarkFolder) -> Unit,
    onMove: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val renameLabel = stringResource(R.string.bookmarks_menu_rename)
    val deleteLabel = stringResource(R.string.bookmarks_menu_delete)
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { actions.onOpenFolder(folder.id) },
                        onLongClickLabel = stringResource(R.string.bookmarks_item_more_actions),
                        onLongClick = { menuOpen = true },
                    ).padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(renameLabel) {
                                    onRename(folder)
                                    true
                                },
                                CustomAccessibilityAction(deleteLabel) {
                                    actions.onDelete(folder.id)
                                    true
                                },
                            )
                    }.testTag(BookmarksTestTags.folder(folder.id)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(FolderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = folder.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.bookmarks_folder_items, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(renameLabel, BookmarksTestTags.MENU_RENAME) {
                menuOpen = false
                onRename(folder)
            }
            MenuItem(stringResource(R.string.bookmarks_menu_move), BookmarksTestTags.MENU_MOVE) {
                menuOpen = false
                onMove(folder.id)
            }
            MenuItem(deleteLabel, BookmarksTestTags.MENU_DELETE) {
                menuOpen = false
                actions.onDelete(folder.id)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    folderTitle: String?,
    actions: BookmarksActions,
    onMove: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val host = UrlDisplay.host(bookmark.url)
    val title = bookmark.title.ifBlank { host.ifEmpty { bookmark.url } }
    val editLabel = stringResource(R.string.bookmarks_menu_edit)
    val deleteLabel = stringResource(R.string.bookmarks_menu_delete)
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { actions.onOpenUrl(bookmark.url) },
                        onLongClickLabel = stringResource(R.string.bookmarks_item_more_actions),
                        onLongClick = { menuOpen = true },
                    ).padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(deleteLabel) {
                                    actions.onDelete(bookmark.id)
                                    true
                                },
                                CustomAccessibilityAction(editLabel) {
                                    actions.onEditBookmark(bookmark.id)
                                    true
                                },
                            )
                    }.testTag(BookmarksTestTags.item(bookmark.id)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LetterTile(host.ifEmpty { title }, host)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (folderTitle.isNullOrEmpty()) host else "$host · $folderTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(editLabel, BookmarksTestTags.MENU_EDIT) {
                menuOpen = false
                actions.onEditBookmark(bookmark.id)
            }
            MenuItem(stringResource(R.string.bookmarks_menu_open_new_tab), BookmarksTestTags.MENU_OPEN_NEW_TAB) {
                menuOpen = false
                actions.onOpenInNewTab(bookmark.url)
            }
            MenuItem(stringResource(R.string.bookmarks_menu_copy), BookmarksTestTags.MENU_COPY) {
                menuOpen = false
                copyToClipboard(context, bookmark.url)
            }
            MenuItem(stringResource(R.string.bookmarks_menu_move), BookmarksTestTags.MENU_MOVE) {
                menuOpen = false
                onMove(bookmark.id)
            }
            MenuItem(deleteLabel, BookmarksTestTags.MENU_DELETE) {
                menuOpen = false
                actions.onDelete(bookmark.id)
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    tag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

/** Ô chữ cái đầu của host, màu suy từ hash host (thay favicon khi chưa có). */
@Composable
private fun LetterTile(
    label: String,
    host: String,
) {
    val scheme = MaterialTheme.colorScheme
    val palette =
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.errorContainer to scheme.onErrorContainer,
        )
    val (background, content) = palette[host.hashCode().mod(palette.size)]
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

/** Sao chép URL; từ Android 13 hệ thống tự báo, trước đó (API 31–32) hiện Toast. */
private fun copyToClipboard(
    context: Context,
    url: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.bookmarks_copied, Toast.LENGTH_SHORT).show()
    }
}
