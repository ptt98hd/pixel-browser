package app.pixel.browser.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.home.ShortcutEdit

/**
 * Hộp thoại thêm/sửa shortcut, vẽ như lớp phủ trong cây compose của trang chủ (không dùng cửa sổ `Dialog`:
 * ô nhập trong cửa sổ phụ làm Compose test không bao giờ idle). [onSave] trả [ShortcutEdit]: `Ok` → đóng; lỗi →
 * giữ mở và hiện thông báo. Nội dung nhập giữ qua xoay màn hình bằng `rememberSaveable`.
 */
@Composable
internal fun ShortcutDialog(
    initialTitle: String,
    initialUrl: String,
    isEdit: Boolean,
    onSave: (title: String, url: String) -> ShortcutEdit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val errorText =
        when (error) {
            ERROR_INVALID -> stringResource(R.string.home_error_invalid)
            ERROR_DUPLICATE -> stringResource(R.string.home_error_duplicate)
            ERROR_FULL -> stringResource(R.string.home_error_full)
            else -> null
        }
    val heading = stringResource(if (isEdit) R.string.home_dialog_edit_title else R.string.home_dialog_add_title)
    val save = {
        when (onSave(title, url)) {
            is ShortcutEdit.Ok -> onDismiss()
            ShortcutEdit.Invalid -> error = ERROR_INVALID
            ShortcutEdit.Duplicate -> error = ERROR_DUPLICATE
            ShortcutEdit.Full -> error = ERROR_FULL
        }
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .testTag(HomeTestTags.DIALOG)
                    .semantics {
                        paneTitle = heading
                        isTraversalGroup = true
                    }
                    // Chạm vào hộp thoại không được rơi xuống scrim (đóng hộp thoại).
                    .pointerInput(Unit) { detectTapGestures { } },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(heading, style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.home_dialog_name)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.DIALOG_TITLE),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.home_dialog_url)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) save() }),
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.DIALOG_URL),
                )
                if (errorText != null) {
                    Text(
                        errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(HomeTestTags.DIALOG_ERROR),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag(HomeTestTags.DIALOG_CANCEL)) {
                        Text(stringResource(R.string.home_dialog_cancel))
                    }
                    TextButton(
                        onClick = { save() },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.testTag(HomeTestTags.DIALOG_SAVE),
                    ) {
                        Text(stringResource(R.string.home_dialog_save))
                    }
                }
            }
        }
    }
}

private const val ERROR_INVALID = "invalid"
private const val ERROR_DUPLICATE = "duplicate"
private const val ERROR_FULL = "full"
