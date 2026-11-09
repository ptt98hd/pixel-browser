package app.pixel.browser.feature.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.bookmarks.FolderOption

/**
 * Lớp phủ hộp thoại nằm trong cây compose (scrim + `Surface` + `BackHandler`), không dùng cửa sổ `AlertDialog`
 * (ô nhập trong cửa sổ phụ làm Compose test không idle; xem quyết định spec 010).
 */
@Composable
internal fun BookmarksOverlay(
    heading: String,
    tag: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Chạm scrim có đóng không; tắt cho hộp thoại có ô nhập để chạm nhầm không làm mất nội dung đang gõ. */
    dismissOnScrimTap: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .pointerInput(dismissOnScrimTap) { detectTapGestures(onTap = { if (dismissOnScrimTap) onDismiss() }) }
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .testTag(tag)
                    .semantics {
                        paneTitle = heading
                        isTraversalGroup = true
                    }
                    // Chạm vào hộp thoại không rơi xuống scrim (đóng hộp thoại).
                    .pointerInput(Unit) { detectTapGestures { } },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(heading, style = MaterialTheme.typography.headlineSmall)
                content()
            }
        }
    }
}

/** Hộp thoại tên thư mục dùng chung cho "Thư mục mới" và "Đổi tên"; tên rỗng → lỗi, hộp thoại vẫn mở. */
@Composable
internal fun FolderNameDialog(
    heading: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val confirm = {
        if (name.isBlank()) showError = true else onConfirm(name)
    }
    BookmarksOverlay(heading, BookmarksTestTags.FOLDER_DIALOG, onDismiss, dismissOnScrimTap = false) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                showError = false
            },
            label = { Text(stringResource(R.string.bookmarks_folder_name_label)) },
            singleLine = true,
            isError = showError,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            modifier = Modifier.fillMaxWidth().testTag(BookmarksTestTags.FOLDER_NAME_FIELD),
        )
        if (showError) {
            Text(
                stringResource(R.string.bookmarks_folder_name_empty),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(BookmarksTestTags.FOLDER_DIALOG_ERROR),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.bookmarks_dialog_cancel)) }
            TextButton(onClick = confirm, modifier = Modifier.testTag(BookmarksTestTags.FOLDER_DIALOG_CONFIRM)) {
                Text(stringResource(R.string.bookmarks_dialog_ok))
            }
        }
    }
}

/** Bộ chọn thư mục đích; [load] trả danh sách (đã loại chính nó và con cháu). */
@Composable
internal fun MoveDialog(
    load: suspend () -> List<FolderOption>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var options by androidx.compose.runtime.remember { mutableStateOf<List<FolderOption>>(emptyList()) }
    LaunchedEffect(Unit) { options = load() }
    BookmarksOverlay(stringResource(R.string.bookmarks_move_title), BookmarksTestTags.MOVE_DIALOG, onDismiss) {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(options, key = { it.id }) { option ->
                FolderOptionRow(option, selected = false, onClick = { onPick(option.id) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.bookmarks_dialog_cancel)) }
        }
    }
}

/** Một dòng thư mục thụt lề theo độ sâu; thụt lề đặt trước `testTag` để vị trí của nút phản ánh độ sâu. */
@Composable
internal fun FolderOptionRow(
    option: FolderOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = option.title,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier =
            Modifier
                .padding(start = INDENT * option.depth)
                .testTag(BookmarksTestTags.folderOption(option.id))
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

private const val SCRIM_ALPHA = 0.32f
private val INDENT = 16.dp
