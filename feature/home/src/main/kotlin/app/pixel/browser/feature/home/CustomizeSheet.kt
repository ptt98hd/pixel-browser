package app.pixel.browser.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.home.HomeBackground
import app.pixel.browser.core.data.home.HomeDefaults

/** Sheet chọn nền: theme, 8 màu, ảnh từ Photo Picker và độ tối của ảnh (spec 010 AC23). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
internal fun CustomizeSheet(
    background: HomeBackground,
    liveDim: Float?,
    onDimChange: (Float) -> Unit,
    onDimChangeFinished: () -> Unit,
    actions: HomeActions,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(HomeTestTags.CUSTOMIZE_SHEET),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.home_sheet_title), style = MaterialTheme.typography.titleLarge)
            FlowRow(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BackgroundOption(
                    selected = background == HomeBackground.Dynamic,
                    tag = HomeTestTags.BG_DYNAMIC,
                    label = stringResource(R.string.home_bg_dynamic),
                    fill = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = { actions.onSetBackground(HomeBackground.Dynamic) },
                )
                HomeDefaults.colors.forEachIndexed { index, argb ->
                    BackgroundOption(
                        selected = background == HomeBackground.Solid(argb),
                        tag = HomeTestTags.bgColor(index),
                        label = stringResource(R.string.home_bg_color, index + 1),
                        fill = Color(argb),
                        onClick = { actions.onSetBackground(HomeBackground.Solid(argb)) },
                    )
                }
                BackgroundOption(
                    selected = background is HomeBackground.Image,
                    tag = HomeTestTags.BG_IMAGE,
                    label = stringResource(R.string.home_bg_image),
                    fill = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onPickImage,
                )
            }
            if (background is HomeBackground.Image) {
                Text(
                    stringResource(R.string.home_bg_dim),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                val dimDescription = stringResource(R.string.home_bg_dim_slider)
                Slider(
                    value = liveDim ?: background.dim,
                    onValueChange = onDimChange,
                    onValueChangeFinished = onDimChangeFinished,
                    valueRange = 0f..HomeDefaults.MAX_DIM,
                    modifier = Modifier.testTag(HomeTestTags.BG_DIM).semantics { contentDescription = dimDescription },
                )
            }
        }
    }
}

@Composable
private fun BackgroundOption(
    selected: Boolean,
    tag: String,
    label: String,
    fill: Color,
    onClick: () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(56.dp)
            .testTag(tag)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .background(fill)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {}
}
