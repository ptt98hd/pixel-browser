package app.pixel.browser.feature.browser

import android.content.Intent
import android.content.MutableContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.omnibox.UrlDisplay
import kotlinx.coroutines.launch

/** Có thể chia sẻ [url] không: không chia sẻ URL rỗng/trang trống (AC26). */
internal fun canShare(url: String): Boolean = UrlDisplay.forEditing(url).isNotBlank()

/**
 * Màn hình trình duyệt stateless (spec 005): top bar, [engineContent], bottom bar, menu, tab switcher và
 * xử lý Back hệ thống (AC31). Menu/tab sheet là trạng thái UI cục bộ.
 */
@Composable
fun BrowserScreen(
    state: BrowserUiState,
    actions: BrowserActions,
    modifier: Modifier = Modifier,
    engineContent: @Composable () -> Unit = {},
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var tabsOpen by rememberSaveable { mutableStateOf(false) }

    // AC31: (a) sheet/menu → đóng; (b) đang sửa → hủy sửa; (c) có lịch sử → goBack; (d) không chặn.
    BackHandler(enabled = menuOpen || tabsOpen || state.isEditingUrl || state.canGoBack) {
        when {
            tabsOpen -> tabsOpen = false
            menuOpen -> menuOpen = false
            state.isEditingUrl -> actions.onCancelEditing()
            else -> actions.onBack()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            scope.launch { snackbarHostState.showSnackbar(messageText) }
            actions.onMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            BrowserTopBar(state = state, actions = actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                engineContent()
                // Snackbar ngay trên bottom bar (đáy vùng nội dung).
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            }
            Box {
                BrowserBottomBar(
                    state = state,
                    actions = actions,
                    onTabsClick = { tabsOpen = true },
                    onMenuClick = { menuOpen = true },
                )
                Box(Modifier.align(Alignment.TopEnd)) {
                    BrowserMenu(
                        expanded = menuOpen,
                        canShare = canShare(state.url),
                        onDismiss = { menuOpen = false },
                        onReload = {
                            menuOpen = false
                            actions.onReload()
                        },
                        onNewTab = {
                            menuOpen = false
                            actions.onNewTab()
                        },
                        onShare = {
                            menuOpen = false
                            actions.onShare()
                        },
                    )
                }
            }
        }
    }

    if (tabsOpen) {
        TabSwitcherSheet(
            tabs = state.tabs,
            selectedTabId = state.selectedTabId,
            onSelect = {
                tabsOpen = false
                actions.onSelectTab(it)
            },
            onClose = actions.onCloseTab,
            onNewTab = {
                tabsOpen = false
                actions.onNewTab()
            },
            onDismiss = { tabsOpen = false },
        )
    }
}

@Composable
private fun messageText(code: String): String =
    when (code) {
        BrowserMessages.BLOCKED_INPUT -> stringResource(R.string.browser_message_blocked)
        else -> code
    }

/** Nối [BrowserViewModel] ↔ [BrowserScreen]; hiển thị `view` của session được chọn, mở chooser khi Share. */
@Composable
fun BrowserRoute(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val session by viewModel.selectedSession.collectAsState()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.browser_share)
    val actions =
        remember(viewModel, context, shareTitle) {
            BrowserActions(
                onBack = viewModel::onBack,
                onForward = viewModel::onForward,
                onHome = viewModel::onHome,
                onReload = viewModel::onReload,
                onNewTab = viewModel::onNewTab,
                onScrollToTop = viewModel::onScrollToTop,
                onScrollToBottom = viewModel::onScrollToBottom,
                onShare = {
                    val current = viewModel.uiState.value
                    if (canShare(
                            current.url,
                        )
                    ) {
                        context.startActivity(shareIntent(current.url, current.title, shareTitle))
                    }
                },
                onStartEditing = viewModel::onStartEditing,
                onCancelEditing = viewModel::onCancelEditing,
                onQueryChange = viewModel::onQueryChange,
                onSubmit = viewModel::onSubmit,
                onSuggestionClick = viewModel::onSuggestionClick,
                onSelectTab = viewModel::onSelectTab,
                onCloseTab = viewModel::onCloseTab,
                onMessageShown = viewModel::onMessageShown,
            )
        }
    BrowserScreen(state = state, actions = actions, modifier = modifier) {
        EngineContainer(session = session)
    }
}

/** Chooser bọc `ACTION_SEND` text/plain với URL (và title làm subject nếu có) – AC26. */
internal fun shareIntent(
    url: String,
    title: String,
    chooserTitle: String,
): Intent {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        }
    return Intent.createChooser(send, chooserTitle)
}

/**
 * Hiển thị [session] (qua [EngineContainer]) và đồng bộ pause/resume của nó với lifecycle của
 * [LocalLifecycleOwner]: resume khi lifecycle ở RESUMED, pause khi rời RESUMED hoặc khi session khác được
 * chọn (đổi tab) / composable bị gỡ.
 */
@Composable
fun EngineContainer(
    session: EngineSession?,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    if (session != null) {
        DisposableEffect(session, lifecycleOwner) {
            val lifecycle = lifecycleOwner.lifecycle
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> session.onResume()
                        Lifecycle.Event.ON_PAUSE -> session.onPause()
                        else -> Unit
                    }
                }
            // addObserver phát lại ON_RESUME nếu lifecycle đang RESUMED; nếu chưa thì đảm bảo đang pause.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.onPause()
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                session.onPause()
            }
        }
    }
    EngineContainer(view = session?.view, modifier = modifier)
}

/**
 * Gắn [view] vào `engine_container` qua `AndroidView` (gỡ khỏi parent cũ trước); null → `engine_placeholder`.
 *
 * Nếu `view.context` là [MutableContextWrapper] (WebView tạo bằng application context để sống qua xoay màn
 * hình), base context được trỏ sang Activity khi gắn và trả về application context khi gỡ để không rò Activity.
 */
@Composable
fun EngineContainer(
    view: View?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().testTag(BrowserTestTags.ENGINE_CONTAINER)) {
        if (view == null) {
            val description = stringResource(R.string.browser_page_placeholder)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .semantics { contentDescription = description }
                    .testTag(BrowserTestTags.ENGINE_PLACEHOLDER),
            )
        } else {
            AndroidView(
                factory = { FrameLayout(it) },
                modifier = Modifier.fillMaxSize(),
                update = { container -> container.show(view) },
                onRelease = { container -> container.detachChildren() },
            )
        }
    }
}

private fun FrameLayout.show(view: View) {
    if (childCount == 1 && getChildAt(0) === view) return
    detachChildren()
    (view.parent as? ViewGroup)?.removeView(view)
    (view.context as? MutableContextWrapper)?.baseContext = context
    addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}

private fun FrameLayout.detachChildren() {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        (child.context as? MutableContextWrapper)?.baseContext = context.applicationContext
    }
    removeAllViews()
}
