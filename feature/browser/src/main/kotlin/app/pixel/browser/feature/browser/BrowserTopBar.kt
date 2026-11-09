package app.pixel.browser.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.engine.SecurityState
import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.core.omnibox.UrlDisplay

/** Thanh URL (A05): domain/placeholder, ổ khóa, progress, chế độ sửa + gợi ý. Nút QR ẩn tới C06. */
@Composable
fun BrowserTopBar(
    state: BrowserUiState,
    actions: BrowserActions,
    modifier: Modifier = Modifier,
    showQrButton: Boolean = false,
    onQrClick: () -> Unit = {},
) {
    // Văn bản đang sửa: tạo lại (chọn toàn bộ URL hiện tại) mỗi lần vào chế độ sửa (AC9).
    var editValue by remember(state.isEditingUrl) {
        val initial = UrlDisplay.forEditing(state.url)
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isEditingUrl) {
                    UrlField(
                        value = editValue,
                        onValueChange = {
                            val changed = it.text != editValue.text
                            editValue = it
                            if (changed) actions.onQueryChange(it.text)
                        },
                        onSubmit = { actions.onSubmit(editValue.text) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SecurityIcon(state.securityState)
                    UrlDisplayText(state.displayHost, actions.onStartEditing, Modifier.weight(1f))
                }
                if (showQrButton) {
                    IconButton(onClick = onQrClick, modifier = Modifier.testTag(BrowserTestTags.QR_BUTTON)) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.browser_scan_qr))
                    }
                }
            }
            if (state.isLoading) {
                val loading = stringResource(R.string.browser_page_loading)
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0, 100) / 100f },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(BrowserTestTags.PROGRESS)
                            .semantics { contentDescription = loading },
                )
            }
            if (state.isEditingUrl && state.suggestions.isNotEmpty()) {
                SuggestionList(state.suggestions, editValue.text.trim(), actions.onSuggestionClick)
            }
        }
    }
}

@Composable
private fun SecurityIcon(securityState: SecurityState) {
    val (icon: ImageVector, label: Int, tag: String) =
        when (securityState) {
            SecurityState.Secure -> {
                Triple(Icons.Filled.Lock, R.string.browser_security_secure, BrowserTestTags.SECURITY_SECURE)
            }

            SecurityState.Insecure -> {
                Triple(Icons.Filled.Warning, R.string.browser_security_insecure, BrowserTestTags.SECURITY_INSECURE)
            }

            SecurityState.Unknown -> {
                return
            }
        }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(label),
        tint =
            if (securityState == SecurityState.Insecure) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = Modifier.padding(horizontal = 8.dp).size(20.dp).testTag(tag),
    )
}

@Composable
private fun UrlDisplayText(
    displayHost: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.browser_edit_url),
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 8.dp)
                .testTag(BrowserTestTags.URL_DISPLAY),
        contentAlignment = Alignment.CenterStart,
    ) {
        val isPlaceholder = displayHost.isEmpty()
        Text(
            text = if (isPlaceholder) stringResource(R.string.browser_url_placeholder) else displayHost,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (isPlaceholder) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UrlField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val label = stringResource(R.string.browser_url_field)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .focusRequester(focusRequester)
                .testTag(BrowserTestTags.URL_FIELD)
                .semantics { contentDescription = label },
        placeholder = { Text(stringResource(R.string.browser_url_placeholder), maxLines = 1) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
            ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
    )
}

@Composable
private fun SuggestionList(
    suggestions: List<Suggestion>,
    query: String,
    onClick: (Suggestion) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        suggestions.forEachIndexed { index, suggestion ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onClick(suggestion) }
                        .padding(horizontal = 16.dp)
                        .testTag(BrowserTestTags.suggestion(index)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    suggestionIcon(suggestion.kind)?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.padding(end = 12.dp).size(20.dp))
                    }
                    Text(
                        text = suggestionLabel(suggestion, query),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Nhãn hiển thị: GoTo → URL; History/Bookmark → title (rỗng → URL); Search → chuỗi resource với truy vấn đang gõ (title của provider là tiếng Anh). */
@Composable
private fun suggestionLabel(
    suggestion: Suggestion,
    query: String,
): String =
    when (suggestion.kind) {
        SuggestionKind.GoTo -> suggestion.url
        SuggestionKind.Search -> stringResource(R.string.browser_suggestion_search, query)
        SuggestionKind.History, SuggestionKind.Bookmark -> suggestion.title.ifBlank { suggestion.url }
    }

private fun suggestionIcon(kind: SuggestionKind): ImageVector? =
    when (kind) {
        SuggestionKind.History -> HistoryIcon
        SuggestionKind.Bookmark -> Icons.Filled.Star
        SuggestionKind.GoTo, SuggestionKind.Search -> null
    }
