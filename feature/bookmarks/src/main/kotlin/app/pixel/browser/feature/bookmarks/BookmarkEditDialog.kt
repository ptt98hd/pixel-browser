package app.pixel.browser.feature.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkRepository
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.DeletedTree
import app.pixel.browser.core.bookmarks.FolderOption
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class BookmarkEditError { InvalidUrl, InvalidTitle, NotFound }

/**
 * Hộp thoại sửa dấu trang (lớp phủ trong cây compose, không `AlertDialog`).
 * Giá trị nhập giữ bằng `rememberSaveable`; [onSave] nhận tên, URL và thư mục đã chọn.
 */
@Composable
fun BookmarkEditDialog(
    title: String,
    url: String,
    folderId: Long,
    options: List<FolderOption>,
    error: BookmarkEditError?,
    onSave: (title: String, url: String, folderId: Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var titleText by rememberSaveable { mutableStateOf(title) }
    var urlText by rememberSaveable { mutableStateOf(url) }
    var folder by rememberSaveable { mutableLongStateOf(folderId) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    BookmarksOverlay(
        heading = stringResource(R.string.bookmark_edit_title),
        tag = BookmarksTestTags.EDIT_DIALOG,
        onDismiss = onDismiss,
        modifier = modifier,
        dismissOnScrimTap = false,
    ) {
        Column(
            Modifier.heightIn(max = MAX_FORM_HEIGHT).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text(stringResource(R.string.bookmark_title_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag(BookmarksTestTags.EDIT_TITLE_FIELD),
            )
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text(stringResource(R.string.bookmark_url_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                isError = error == BookmarkEditError.InvalidUrl,
                modifier = Modifier.fillMaxWidth().testTag(BookmarksTestTags.EDIT_URL_FIELD),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag(BookmarksTestTags.EDIT_FOLDER_PICKER)
                    .clickable { pickerOpen = !pickerOpen }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.bookmark_folder_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    options.firstOrNull { it.id == folder }?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (pickerOpen) {
                Column(Modifier.heightIn(max = MAX_PICKER_HEIGHT).verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        FolderOptionRow(option, selected = option.id == folder) {
                            folder = option.id
                            pickerOpen = false
                        }
                    }
                }
            }
            if (error != null) {
                Text(
                    stringResource(error.message()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(BookmarksTestTags.EDIT_ERROR),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRemove, modifier = Modifier.testTag(BookmarksTestTags.EDIT_REMOVE)) {
                Text(stringResource(R.string.bookmark_remove), color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag(BookmarksTestTags.EDIT_CANCEL)) {
                    Text(stringResource(R.string.bookmark_cancel))
                }
                TextButton(
                    onClick = { onSave(titleText, urlText, folder) },
                    enabled = urlText.isNotBlank(),
                    modifier = Modifier.testTag(BookmarksTestTags.EDIT_SAVE),
                ) { Text(stringResource(R.string.bookmark_save)) }
            }
        }
    }
}

private fun BookmarkEditError.message(): Int =
    when (this) {
        BookmarkEditError.InvalidUrl -> R.string.bookmark_error_invalid_url
        BookmarkEditError.InvalidTitle -> R.string.bookmark_error_invalid_title
        BookmarkEditError.NotFound -> R.string.bookmark_error_not_found
    }

/** Stateful: nạp dấu trang theo [bookmarkId], gọi `updateBookmark`/`delete`; [onRemoved] nhận cây đã xóa để hoàn tác. */
@Composable
fun BookmarkEditRoute(
    repo: BookmarkRepository,
    bookmarkId: Long,
    onDismiss: () -> Unit,
    onRemoved: (DeletedTree) -> Unit = {},
    /** Tên hiển thị của gốc theo locale (kho lưu "Dấu trang"); null → giữ tên đã lưu. */
    rootTitle: String? = null,
) {
    var loaded by remember(repo, bookmarkId) { mutableStateOf<Loaded?>(null) }
    var error by remember(repo, bookmarkId) { mutableStateOf<BookmarkEditError?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repo, bookmarkId) {
        val bookmark = attempt { repo.bookmark(bookmarkId) }
        if (bookmark == null) {
            onDismiss()
        } else {
            val options =
                attempt { repo.folderOptions() }.orEmpty().map {
                    if (it.id == ROOT_ID && rootTitle != null) it.copy(title = rootTitle) else it
                }
            loaded = Loaded(bookmark, options)
        }
    }
    val data = loaded ?: return
    BookmarkEditDialog(
        title = data.bookmark.title,
        url = data.bookmark.url,
        folderId = data.bookmark.parentId ?: ROOT_ID,
        options = data.options,
        error = error,
        onSave = { title, url, folderId ->
            scope.launch {
                when (attempt { repo.updateBookmark(bookmarkId, title, url, folderId) }) {
                    is BookmarkResult.Ok -> {
                        attempt { repo.setLastFolderId(folderId) }
                        onDismiss()
                    }

                    BookmarkResult.InvalidUrl -> {
                        error = BookmarkEditError.InvalidUrl
                    }

                    BookmarkResult.InvalidTitle -> {
                        error = BookmarkEditError.InvalidTitle
                    }

                    else -> {
                        error = BookmarkEditError.NotFound
                    }
                }
            }
        },
        onRemove = {
            scope.launch {
                val result = attempt { repo.delete(bookmarkId) }
                if (result is BookmarkResult.Ok) {
                    onRemoved(result.value)
                    onDismiss()
                } else {
                    error = BookmarkEditError.NotFound
                }
            }
        },
        onDismiss = onDismiss,
    )
}

private class Loaded(
    val bookmark: Bookmark,
    val options: List<FolderOption>,
)

/** Lỗi lưu trữ → `null` (UI báo lỗi), không làm sập; hủy coroutine vẫn được truyền đi. */
private suspend fun <T> attempt(block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

private val MAX_FORM_HEIGHT = 440.dp
private val MAX_PICKER_HEIGHT = 240.dp
