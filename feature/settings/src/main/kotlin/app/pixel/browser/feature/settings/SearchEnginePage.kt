package app.pixel.browser.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.search.CustomEngineResult
import app.pixel.browser.core.data.search.CustomEngineValidator
import app.pixel.browser.core.data.search.SearchEngineIds
import app.pixel.browser.core.data.search.SearchSettings
import app.pixel.browser.core.omnibox.SearchEngine
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.searchUrl

private const val PLACEHOLDER = "%s"
private const val PREVIEW_QUERY = "pixel browser"

/**
 * Trang Công cụ tìm kiếm (spec 014 AC13/AC14): chọn engine dựng sẵn hoặc nhập engine tùy chỉnh bằng mẫu URL.
 * Mọi kiểm tra/xem trước tính cục bộ, không gọi mạng. Chỉ lưu (onChange) khi người dùng bấm Lưu với mẫu hợp lệ.
 */
@Composable
fun SearchEnginePage(
    settings: SearchSettings,
    onChange: ((SearchSettings) -> SearchSettings) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var template by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val effectiveId = settings.engine.id

    if (editing) {
        // Trình soạn thay thế danh sách (không phải hộp thoại: Compose test không idle với ô nhập ở cửa sổ phụ).
        BackHandler { editing = false }
        CustomEditor(
            name = name,
            template = template,
            attempted = attempted,
            onNameChange = {
                name = it
                attempted = false
            },
            onTemplateChange = {
                template = it
                attempted = false
            },
            onCancel = { editing = false },
            onSave = { result ->
                attempted = true
                if (result is CustomEngineResult.Ok) {
                    onChange { it.copy(selectedId = SearchEngineIds.CUSTOM, custom = result.engine) }
                    editing = false
                    attempted = false
                }
            },
        )
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.selectableGroup()) {
            SearchEngines.all.forEach { engine ->
                EngineOption(
                    id = engine.id,
                    title = engine.name,
                    host = hostOf(engine.template),
                    selected = effectiveId == engine.id,
                    onClick = { if (effectiveId != engine.id) onChange { it.copy(selectedId = engine.id) } },
                )
            }
            val custom = settings.custom
            EngineOption(
                id = SearchEngineIds.CUSTOM,
                title = custom?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.search_engine_custom),
                host = custom?.let { hostOf(it.template) },
                hint = if (custom == null) stringResource(R.string.search_engine_custom_hint) else null,
                selected = effectiveId == SearchEngineIds.CUSTOM,
                onClick = {
                    name = custom?.name.orEmpty()
                    template = custom?.template.orEmpty()
                    attempted = false
                    editing = true
                },
            )
        }
    }
}

@Composable
private fun EngineOption(
    id: String,
    title: String,
    host: String?,
    selected: Boolean,
    onClick: () -> Unit,
    hint: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(SettingsTestTags.searchEngineOption(id))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val secondary = host ?: hint
            if (secondary != null) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CustomEditor(
    name: String,
    template: String,
    attempted: Boolean,
    onNameChange: (String) -> Unit,
    onTemplateChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: (CustomEngineResult) -> Unit,
) {
    val result = CustomEngineValidator.validate(name, template)
    val canSave = name.isNotBlank() && template.isNotBlank()
    val save = { if (canSave) onSave(result) }
    val previewUrl =
        (CustomEngineValidator.validate(name.ifBlank { "x" }, template) as? CustomEngineResult.Ok)
            ?.let { SearchEngine(SearchEngineIds.CUSTOM, it.engine.name, it.engine.template).searchUrl(PREVIEW_QUERY) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.CUSTOM_NAME),
            label = { Text(stringResource(R.string.search_custom_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = template,
            onValueChange = onTemplateChange,
            modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.CUSTOM_TEMPLATE),
            label = { Text(stringResource(R.string.search_custom_template_label)) },
            supportingText = { Text(stringResource(R.string.search_custom_template_help, PLACEHOLDER)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.search_custom_cancel)) }
            Button(onClick = save, enabled = canSave, modifier = Modifier.testTag(SettingsTestTags.CUSTOM_SAVE)) {
                Text(stringResource(R.string.search_custom_save))
            }
        }
        if (previewUrl != null) {
            Text(
                stringResource(R.string.search_custom_preview, previewUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(SettingsTestTags.CUSTOM_PREVIEW),
            )
        }
        if (CustomEngineValidator.isInsecure(template)) {
            Text(
                stringResource(R.string.search_custom_http_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(SettingsTestTags.CUSTOM_HTTP_WARNING),
            )
        }
        if (attempted && result !is CustomEngineResult.Ok) {
            Text(
                errorMessage(result),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier.testTag(SettingsTestTags.CUSTOM_ERROR).semantics {
                        liveRegion =
                            LiveRegionMode.Polite
                    },
            )
        }
    }
}

@Composable
private fun errorMessage(result: CustomEngineResult): String =
    when (result) {
        is CustomEngineResult.Ok -> {
            ""
        }

        CustomEngineResult.NameEmpty -> {
            stringResource(R.string.search_error_name_empty)
        }

        CustomEngineResult.NameTooLong -> {
            stringResource(R.string.search_error_name_too_long, CustomEngineValidator.MAX_NAME)
        }

        CustomEngineResult.TemplateEmpty -> {
            stringResource(R.string.search_error_template_empty)
        }

        CustomEngineResult.NoPlaceholder -> {
            stringResource(R.string.search_error_no_placeholder, PLACEHOLDER)
        }

        CustomEngineResult.MultiplePlaceholders -> {
            stringResource(R.string.search_error_multiple_placeholders, PLACEHOLDER)
        }

        CustomEngineResult.InvalidUrl -> {
            stringResource(R.string.search_error_invalid_url, PLACEHOLDER)
        }

        CustomEngineResult.TemplateTooLong -> {
            stringResource(R.string.search_error_template_too_long, CustomEngineValidator.MAX_TEMPLATE)
        }
    }

/** Host của mẫu URL để hiển thị (không hiện mẫu đầy đủ); `null` nếu không rút ra được một host an toàn. */
private fun hostOf(template: String): String? {
    val rest = template.substringAfter("://", "")
    val authority = rest.takeWhile { it !in "/?#\\" }
    return authority.takeIf { it.isNotEmpty() && '%' !in it && '@' !in it }
}
