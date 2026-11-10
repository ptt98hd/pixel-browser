package app.pixel.browser.feature.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Thanh tìm trong trang (spec 015 B04): ô nhập (focus + bàn phím khi xuất hiện, IME `Search` = kết quả sau và
 * ẩn bàn phím), số kết quả "n/m" (live region), nút trước/sau và đóng. [onFindNext] nhận `true` = kết quả sau.
 * Vị trí trên bàn phím do `BrowserBottomBar` (đã áp `WindowInsets.ime`) nằm ngay dưới thanh này.
 */
@Composable
fun FindBar(
    state: FindUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onFindNext: (Boolean) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
    LaunchedEffect(state.query) {
        if (field.text != state.query) field = TextFieldValue(state.query, TextRange(state.query.length))
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val hasResults = state.total > 0
    Surface(
        modifier = modifier.fillMaxWidth().testTag(BrowserTestTags.FIND_BAR),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = stringResource(R.string.browser_find_in_page)
            TextField(
                value = field,
                onValueChange = {
                    val changed = it.text != field.text
                    field = it
                    if (changed) onQueryChange(it.text)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag(BrowserTestTags.FIND_FIELD)
                        .semantics { contentDescription = label },
                placeholder = { Text(label, maxLines = 1) },
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                        autoCorrectEnabled = false,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            onFindNext(true)
                            keyboard?.hide()
                        },
                    ),
            )
            if (state.query.isNotBlank()) {
                val description =
                    when {
                        state.total == 0 && state.isSearching -> stringResource(R.string.browser_find_searching)
                        state.total == 0 -> stringResource(R.string.browser_find_not_found)
                        else -> stringResource(R.string.browser_find_position, state.current, state.total)
                    }
                Text(
                    text = "${state.current}/${state.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .testTag(BrowserTestTags.FIND_COUNT)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = description
                            },
                )
            }
            IconButton(
                onClick = { onFindNext(false) },
                enabled = hasResults,
                modifier = Modifier.size(FIND_BUTTON_SIZE).testTag(BrowserTestTags.FIND_PREV),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.browser_find_previous))
            }
            IconButton(
                onClick = { onFindNext(true) },
                enabled = hasResults,
                modifier = Modifier.size(FIND_BUTTON_SIZE).testTag(BrowserTestTags.FIND_NEXT),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.browser_find_next))
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(FIND_BUTTON_SIZE).testTag(BrowserTestTags.FIND_CLOSE),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.browser_find_close))
            }
        }
    }
}

/** Vùng chạm tối thiểu của nút trên thanh tìm (Material: 48dp). */
private val FIND_BUTTON_SIZE = 48.dp
