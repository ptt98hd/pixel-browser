package app.pixel.browser.feature.browser.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.core.ui.PixelMotion
import app.pixel.browser.core.ui.pixelSpring
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.R
import app.pixel.browser.feature.browser.TabItem
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Tiêu đề hiển thị của tab: title, nếu rỗng thì host của URL; rỗng với tab home (UI dùng chuỗi "Tab mới"). */
internal fun TabItem.displayTitle(): String = if (isHome) "" else title.ifBlank { UrlDisplay.host(url) }

private const val DISMISS_FRACTION = 0.5f
private const val FLING_VELOCITY_PX = 2500f
private const val ENTER_SCALE_DELTA = 0.08f
private const val EXIT_SCALE_DELTA = 0.5f
private const val CARD_ASPECT = 0.75f
private const val SWIPE_OUT_MILLIS = 150

/**
 * Tab switcher toàn màn hình dạng lưới (spec 009 AC12–AC18): thẻ có thumbnail, favicon, tiêu đề; vuốt ngang hoặc
 * nút X để đóng (snackbar "Hoàn tác"), FAB tab mới, "Đóng tất cả" có xác nhận. Chọn thẻ/tab mới/đóng tất cả gọi
 * callback ngay rồi chạy animation thoát và gọi [onDismiss].
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun TabSwitcher(
    tabs: List<TabItem>,
    selectedTabId: String?,
    thumbnail: @Composable (String) -> ImageBitmap?,
    favicon: @Composable (String) -> ImageBitmap?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onUndoClose: () -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val spec = pixelSpring(PixelMotion.spatial<Float>())
    // 1 = ẩn hoàn toàn, 0 = hiện đủ. Vào: thu nhỏ nhẹ → 1; ra: phóng to quanh thẻ được chọn rồi mờ dần.
    val progress = remember { Animatable(1f) }
    var leaving by remember { mutableStateOf(false) }
    var pivot by remember { mutableStateOf(TransformOrigin.Center) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) { progress.animateTo(0f, spec) }

    fun leave(action: () -> Unit) {
        if (leaving) return
        leaving = true
        action()
        scope.launch {
            progress.animateTo(1f, spec)
            onDismiss()
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val closedText = stringResource(R.string.browser_tab_closed)
    val undoText = stringResource(R.string.browser_tab_undo)

    fun closeWithUndo(id: String) {
        onClose(id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(closedText, undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndoClose()
        }
    }

    var confirmCloseAll by rememberSaveable { mutableStateOf(false) }
    val onlyUnusedHome = tabs.size == 1 && tabs.single().isHome

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it }
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val scale = if (leaving) 1f + EXIT_SCALE_DELTA * p else 1f - ENTER_SCALE_DELTA * p
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - p
                    transformOrigin = if (leaving) pivot else TransformOrigin.Center
                }.pointerInput(Unit) { detectTapGestures { } }
                .testTag(BrowserTestTags.TAB_SWITCHER),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.browser_tabs_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { confirmCloseAll = true },
                    enabled = !onlyUnusedHome,
                    modifier = Modifier.testTag(BrowserTestTags.TAB_SWITCHER_CLOSE_ALL),
                ) { Text(stringResource(R.string.browser_tabs_close_all)) }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    action = {
                        TextButton(
                            onClick = { data.performAction() },
                            modifier = Modifier.testTag(BrowserTestTags.TAB_UNDO),
                        ) { Text(data.visuals.actionLabel.orEmpty()) }
                    },
                ) { Text(data.visuals.message) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { leave(onNewTab) },
                modifier = Modifier.testTag(BrowserTestTags.TAB_SWITCHER_NEW),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.browser_new_tab)) }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val columns = tabGridColumns(maxWidth.value.toInt())
            val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex / columns * columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
                    TabCard(
                        tab = tab,
                        selected = tab.id == selectedTabId,
                        thumbnail = if (tab.isHome) null else thumbnail(tab.id),
                        favicon = favicon(tab.id),
                        onClick = { center ->
                            if (rootSize.width > 0 && rootSize.height > 0) {
                                pivot = TransformOrigin(center.x / rootSize.width, center.y / rootSize.height)
                            }
                            leave { onSelect(tab.id) }
                        },
                        onClose = { closeWithUndo(tab.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (confirmCloseAll) {
        AlertDialog(
            onDismissRequest = { confirmCloseAll = false },
            modifier = Modifier.testTag(BrowserTestTags.CLOSE_ALL_DIALOG),
            title = { Text(stringResource(R.string.browser_close_all_title)) },
            text = { Text(stringResource(R.string.browser_close_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCloseAll = false
                        leave(onCloseAll)
                    },
                    modifier = Modifier.testTag(BrowserTestTags.CLOSE_ALL_CONFIRM),
                ) { Text(stringResource(R.string.browser_close_all_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmCloseAll = false },
                    modifier = Modifier.testTag(BrowserTestTags.CLOSE_ALL_CANCEL),
                ) { Text(stringResource(R.string.browser_close_all_cancel)) }
            },
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun TabCard(
    tab: TabItem,
    selected: Boolean,
    thumbnail: ImageBitmap?,
    favicon: ImageBitmap?,
    onClick: (center: Offset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = tab.displayTitle().ifEmpty { stringResource(R.string.browser_untitled_tab) }
    val closeDescription = stringResource(R.string.browser_close_tab, title)
    val closeAction = stringResource(R.string.browser_tab_close_action)
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var closing by remember { mutableStateOf(false) }

    fun requestClose() {
        if (closing) return
        closing = true
        onClose()
    }

    fun swipeOut(direction: Float) {
        if (closing) return
        closing = true
        scope.launch {
            animate(
                offsetX,
                direction * widthPx * 1.2f,
                animationSpec = tween(SWIPE_OUT_MILLIS),
            ) { v, _ -> offsetX = v }
            onClose()
            // Nếu thẻ không bị gỡ (đóng thất bại) thì trả về như cũ; thẻ bị gỡ thì scope này đã bị hủy.
            withFrameNanos { }
            withFrameNanos { }
            offsetX = 0f
            closing = false
        }
    }

    val dragState = rememberDraggableState { delta -> if (!closing) offsetX += delta }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier =
            modifier
                .graphicsLayer {
                    translationX = offsetX
                    alpha = if (widthPx > 0f) (1f - abs(offsetX) / (widthPx * 1.5f)).coerceIn(0f, 1f) else 1f
                }.onSizeChanged { widthPx = it.width.toFloat() }
                .onGloballyPositioned { center = it.boundsInRoot().center }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = { velocity ->
                        val past = abs(offsetX) > widthPx * DISMISS_FRACTION
                        val flung = abs(velocity) > FLING_VELOCITY_PX && velocity * offsetX > 0f
                        if (past || flung) {
                            swipeOut(if (offsetX >= 0f) 1f else -1f)
                        } else {
                            animate(offsetX, 0f) { v, _ -> offsetX = v }
                        }
                    },
                ).selectable(selected = selected, role = Role.Tab, onClick = { if (!closing) onClick(center) })
                .semantics {
                    customActions =
                        listOf(CustomAccessibilityAction(closeAction) { requestClose().let { true } })
                }.testTag(BrowserTestTags.tabItem(tab.id)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(tab, favicon)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = ::requestClose,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .testTag(BrowserTestTags.tabClose(tab.id))
                            .semantics { contentDescription = closeDescription },
                ) { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT).clip(RoundedCornerShape(12.dp))) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().testTag(BrowserTestTags.tabThumb(tab.id)),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag(BrowserTestTags.tabThumbPlaceholder(tab.id)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tab.isHome) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Favicon(
    tab: TabItem,
    favicon: ImageBitmap?,
) {
    val size = Modifier.size(20.dp).clip(CircleShape)
    if (favicon != null && !tab.isHome) {
        Image(
            bitmap = favicon,
            contentDescription = null,
            modifier = size.testTag(BrowserTestTags.tabFavicon(tab.id)),
        )
    } else {
        Box(
            modifier =
                size
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .testTag(BrowserTestTags.tabFaviconFallback(tab.id)),
            contentAlignment = Alignment.Center,
        ) {
            if (tab.isHome) {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text(
                    text = UrlDisplay.host(tab.url).firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
