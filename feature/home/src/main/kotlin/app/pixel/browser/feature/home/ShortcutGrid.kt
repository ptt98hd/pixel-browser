package app.pixel.browser.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeDefaults
import app.pixel.browser.core.data.home.Shortcut
import app.pixel.browser.core.omnibox.UrlDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private sealed interface GridItem {
    data class Tile(
        val shortcut: Shortcut,
        val index: Int,
    ) : GridItem

    data object Add : GridItem
}

/**
 * Lưới shortcut theo [columns] cột (+ ô "thêm" nếu chưa đủ [HomeDefaults.MAX_SHORTCUTS]). Long-press rồi kéo để
 * sắp xếp; long-press không kéo mở menu sửa/xóa.
 */
@Composable
internal fun ShortcutGrid(
    config: HomeConfig,
    columns: Int,
    contentColor: Color,
    labelShadow: Boolean,
    actions: HomeActions,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val bounds = remember { mutableMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val currentConfig by rememberUpdatedState(config)
    val currentMove by rememberUpdatedState(actions.onMoveShortcut)

    val items: List<GridItem> =
        config.shortcuts.mapIndexed { i, s -> GridItem.Tile(s, i) } +
            if (config.shortcuts.size < HomeDefaults.MAX_SHORTCUTS) listOf(GridItem.Add) else emptyList()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        when (item) {
                            GridItem.Add -> {
                                AddTile(contentColor, labelShadow, onAdd)
                            }

                            is GridItem.Tile -> {
                                val id = item.shortcut.id
                                ShortcutTile(
                                    shortcut = item.shortcut,
                                    index = item.index,
                                    count = config.shortcuts.size,
                                    contentColor = contentColor,
                                    labelShadow = labelShadow,
                                    dragging = draggingId == id,
                                    dragOffset = if (draggingId == id) dragOffset else Offset.Zero,
                                    onBounds = { bounds[id] = it },
                                    onTap = { actions.onOpenUrl(item.shortcut.url) },
                                    onDragStart = {
                                        draggingId = id
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { dragOffset = it },
                                    onDragEnd = {
                                        val from = currentConfig.shortcuts.indexOfFirst { it.id == id }
                                        val center = bounds[id]?.center?.plus(dragOffset)
                                        val to =
                                            center?.let { c ->
                                                currentConfig.shortcuts.indexOfFirst {
                                                    bounds[it.id]?.contains(c) ==
                                                        true
                                                }
                                            } ?: -1
                                        draggingId = null
                                        dragOffset = Offset.Zero
                                        if (from >= 0 && to >= 0 && from != to) currentMove(from, to)
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = Offset.Zero
                                    },
                                    onEdit = { onEdit(id) },
                                    onRemove = { actions.onRemoveShortcut(id) },
                                    onMove = { to -> actions.onMoveShortcut(item.index, to) },
                                )
                            }
                        }
                    }
                }
                // Hàng cuối thiếu ô: giữ độ rộng cột đều.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AddTile(
    contentColor: Color,
    labelShadow: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.home_add_shortcut)
    Column(
        Modifier
            .testTag(HomeTestTags.SHORTCUT_ADD)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    onClick()
                    true
                }
            }.pointerInput(Unit) {
                detectTapOrLongPress(onTap = onClick, onLongPress = {}, onDrag = {}, onCancel = {}, onEnd = {})
            }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(TILE_ICON).clip(CircleShape).background(contentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = contentColor)
        }
        TileLabel(label, contentColor, labelShadow)
    }
}

@Composable
private fun TileLabel(
    text: String,
    color: Color,
    shadow: Boolean,
) {
    // Trên ảnh nền: bóng đổ giúp nhãn đọc được cả khi ảnh sáng cục bộ.
    val style =
        if (shadow) {
            MaterialTheme.typography.labelMedium.copy(shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f))
        } else {
            MaterialTheme.typography.labelMedium
        }
    Text(
        text,
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ShortcutTile(
    shortcut: Shortcut,
    index: Int,
    count: Int,
    contentColor: Color,
    labelShadow: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    onBounds: (Rect) -> Unit,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val currentTap by rememberUpdatedState(onTap)
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentCancel by rememberUpdatedState(onDragCancel)
    val moveLeft = stringResource(R.string.home_move_left)
    val moveRight = stringResource(R.string.home_move_right)
    val optionsLabel = stringResource(R.string.home_shortcut_options)
    val accessibilityActions =
        buildList {
            if (index > 0) add(CustomAccessibilityAction(moveLeft) { onMove(index - 1).let { true } })
            if (index < count - 1) add(CustomAccessibilityAction(moveRight) { onMove(index + 1).let { true } })
        }

    Column(
        Modifier
            .testTag(HomeTestTags.shortcut(shortcut.id))
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = shortcut.title
                customActions = accessibilityActions
                onClick {
                    onTap()
                    true
                }
                onLongClick(optionsLabel) {
                    menuOpen = true
                    true
                }
            }.pointerInput(shortcut.id) {
                detectTapOrLongPress(
                    onTap = { currentTap() },
                    onLongPress = { currentStart() },
                    onDrag = { currentDrag(it) },
                    onCancel = { currentCancel() },
                    onEnd = { moved ->
                        if (moved) {
                            currentEnd()
                        } else {
                            currentEnd()
                            menuOpen = true
                        }
                    },
                )
            }
            // graphicsLayer sau pointerInput: tile di chuyển theo ngón tay mà tọa độ con trỏ không bị lệch.
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                val scale = if (dragging) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(TILE_ICON).clip(CircleShape).background(Color(tileColor(shortcut.url))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letterOf(shortcut.title, shortcut.url),
                color = Color(homeContentColor(tileColor(shortcut.url))),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        TileLabel(shortcut.title, contentColor, labelShadow)
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_edit)) },
                onClick = {
                    menuOpen = false
                    onEdit()
                },
                modifier = Modifier.testTag(HomeTestTags.MENU_EDIT),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_remove)) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
                modifier = Modifier.testTag(HomeTestTags.MENU_REMOVE),
            )
        }
    }
}

private val TILE_ICON = 56.dp

/** Chữ cái đầu của tiêu đề (hoặc host) làm icon. */
internal fun letterOf(
    title: String,
    url: String,
): String =
    title
        .ifBlank { UrlDisplay.host(url) }
        .firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
        ?.toString() ?: "?"

/** Màu ô icon suy từ hash host, lấy trong bảng [HomeDefaults.colors]. */
internal fun tileColor(url: String): Int {
    val colors = HomeDefaults.colors
    return colors[UrlDisplay.host(url).hashCode().mod(colors.size)]
}

/**
 * Nhận diện một chạm: tap (nhả trước khi hết thời gian long-press) → [onTap]; giữ quá thời gian long-press →
 * [onLongPress], rồi theo dõi di chuyển ([onDrag] nhận độ dời tích lũy) tới khi nhả → [onEnd] (`moved` = có kéo
 * quá touch slop). Nếu cha (cuộn) chiếm cử chỉ trước long-press thì không có gì xảy ra.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapOrLongPress(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onCancel: () -> Unit,
    onEnd: (moved: Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var timedOut = true
        val up =
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation().also { timedOut = false }
            }
        if (!timedOut) {
            if (up != null) {
                up.consume()
                onTap()
            }
            return@awaitEachGesture
        }
        onLongPress()
        var total = Offset.Zero
        var moved = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp() || !change.pressed) {
                    change.consume()
                    break
                }
                total += change.positionChange()
                change.consume()
                if (!moved &&
                    (abs(total.x) > viewConfiguration.touchSlop || abs(total.y) > viewConfiguration.touchSlop)
                ) {
                    moved = true
                }
                if (moved) onDrag(total)
            }
        } catch (e: CancellationException) {
            // Cử chỉ bị hủy (gỡ composable, đổi key): không được thả/ghi vị trí mới.
            onCancel()
            throw e
        }
        onEnd(moved)
    }
}
