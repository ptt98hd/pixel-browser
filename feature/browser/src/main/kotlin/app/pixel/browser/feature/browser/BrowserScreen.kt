package app.pixel.browser.feature.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.originOf
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.feature.browser.gesture.BackGestureIndicator
import app.pixel.browser.feature.browser.gesture.EdgeSwipeOverlay
import app.pixel.browser.feature.browser.gesture.GestureGate
import app.pixel.browser.feature.browser.gesture.PullToRefreshHost
import app.pixel.browser.feature.browser.intent.DefaultBrowserController
import app.pixel.browser.feature.browser.intent.RoleManagerDefaultBrowser
import app.pixel.browser.feature.browser.intent.defaultAppsSettingsIntent
import app.pixel.browser.feature.browser.tabs.BitmapTabThumbnailRepository
import app.pixel.browser.feature.browser.tabs.TabSwitcher
import app.pixel.browser.feature.browser.web.EngineRequestHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Hệ thống dùng điều hướng cử chỉ khi cả hai mép có vùng cử chỉ hệ thống (spec 008 AC9). */
@Composable
private fun rememberSystemGestureNav(): Boolean {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    return insets.getLeft(density, direction) > 0 || insets.getRight(density, direction) > 0
}

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
    themeSettings: ThemeSettings = ThemeSettings(),
    fullscreenContent: @Composable () -> Unit = { FullscreenContainer(view = null) },
    engineContent: @Composable () -> Unit = {},
    homeContent: @Composable () -> Unit = {},
    thumbnail: @Composable (String) -> ImageBitmap? = { null },
    favicon: @Composable (String) -> ImageBitmap? = { null },
    onSwitcherOpenChange: (Boolean) -> Unit = {},
    systemGestureNav: Boolean = rememberSystemGestureNav(),
) {
    PrivateKeyboard(enabled = state.selectedIsPrivate) {
        BrowserScreenContent(
            state,
            actions,
            modifier,
            themeSettings,
            fullscreenContent,
            engineContent,
            homeContent,
            thumbnail,
            favicon,
            onSwitcherOpenChange,
            systemGestureNav,
        )
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun BrowserScreenContent(
    state: BrowserUiState,
    actions: BrowserActions,
    modifier: Modifier = Modifier,
    themeSettings: ThemeSettings = ThemeSettings(),
    fullscreenContent: @Composable () -> Unit = { FullscreenContainer(view = null) },
    engineContent: @Composable () -> Unit = {},
    /** Spec 010: vẽ thay cho [engineContent] khi `state.isHome`. */
    homeContent: @Composable () -> Unit = {},
    /** Spec 009: ảnh thumbnail của tab cho tab switcher. */
    thumbnail: @Composable (String) -> ImageBitmap? = { null },
    /** Spec 009: favicon của tab cho tab switcher. */
    favicon: @Composable (String) -> ImageBitmap? = { null },
    /** Spec 009: báo cho [BrowserRoute] khi tab switcher mở/đóng để pause/resume WebView phía sau. */
    onSwitcherOpenChange: (Boolean) -> Unit = {},
    /** Spec 008: true khi hệ thống dùng điều hướng cử chỉ (mặc định tính từ `WindowInsets.systemGestures`). */
    systemGestureNav: Boolean = rememberSystemGestureNav(),
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var tabsOpen by rememberSaveable { mutableStateOf(false) }
    var themeDialogOpen by rememberSaveable { mutableStateOf(false) }
    // Nhóm đang hiển thị của trình chuyển tab: giữ ở đây (đặt ngay lúc bấm) để FLAG_SECURE theo kịp từ khung đầu.
    var switcherPrivate by rememberSaveable { mutableStateOf(false) }

    // AC31: (a) sheet/menu → đóng; (b) đang sửa → hủy sửa; (c) có lịch sử → goBack; (d) không chặn.
    // Spec 006 AC14: tab mở từ intent, hết lịch sử → đóng tab và quay về app gọi.
    // Spec 008 AC7: nhánh (c) vẽ mũi tên `back_gesture_indicator` theo tiến độ của predictive back.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backFromRight by remember { mutableStateOf(false) }
    var backIndicatorVisible by remember { mutableStateOf(false) }
    val currentState by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)
    PredictiveBackHandler(
        enabled =
            menuOpen || tabsOpen || state.find != null || state.isEditingUrl || state.canGoBack || state.isExternalTab,
    ) { events ->
        // Fullscreen có BackHandler đăng ký sau nên thắng; handler này không nhận sự kiện khi đó.
        val showsIndicator =
            !(menuOpen || tabsOpen || currentState.find != null || currentState.isEditingUrl) &&
                currentState.canGoBack
        try {
            events.collect { event ->
                if (showsIndicator) {
                    backProgress = event.progress
                    backFromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                    backIndicatorVisible = true
                }
            }
        } catch (e: CancellationException) {
            backIndicatorVisible = false
            throw e
        }
        backIndicatorVisible = false
        // Đọc lại state/actions mới nhất sau khi cử chỉ kết thúc (có thể đã đổi trong lúc vuốt).
        when {
            tabsOpen -> tabsOpen = false
            menuOpen -> menuOpen = false
            currentState.find != null -> currentActions.onCloseFind()
            currentState.isEditingUrl -> currentActions.onCancelEditing()
            currentState.canGoBack -> currentActions.onBack()
            else -> currentActions.onReturnToCaller()
        }
    }
    // Spec 007 AC20: khi fullscreen, Back thoát fullscreen trước mọi nhánh của AC31 (handler đăng ký sau thắng).
    BackHandler(enabled = state.isFullscreen) { actions.onExitFullscreen() }

    val currentOnSwitcherOpenChange by rememberUpdatedState(onSwitcherOpenChange)
    LaunchedEffect(tabsOpen) { currentOnSwitcherOpenChange(tabsOpen) }
    LaunchedEffect(menuOpen, tabsOpen) { if (menuOpen || tabsOpen) actions.onResolvePrivateAvailability() }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messageText = state.message?.let { messageText(it) }
    val settingsLabel = stringResource(R.string.browser_message_permission_settings)
    val editLabel = stringResource(R.string.browser_bookmark_edit_action)
    val undoLabel = stringResource(R.string.browser_tab_undo)
    LaunchedEffect(state.message) {
        if (messageText != null) {
            val message = state.message
            val bookmarkId = state.bookmarkId
            val action =
                when (message) {
                    BrowserMessages.PERMISSION_DENIED -> settingsLabel
                    BrowserMessages.BOOKMARK_ADDED -> editLabel
                    BrowserMessages.BOOKMARK_REMOVED -> undoLabel
                    else -> null
                }
            scope.launch {
                val visuals = MessageVisuals(messageText, action, message == BrowserMessages.BOOKMARK_ADDED)
                if (snackbarHostState.showSnackbar(visuals) == SnackbarResult.ActionPerformed) {
                    when (message) {
                        BrowserMessages.BOOKMARK_ADDED -> bookmarkId?.let(actions.onEditBookmark)
                        BrowserMessages.BOOKMARK_REMOVED -> actions.onUndoBookmarkRemoved()
                        else -> actions.onOpenAppSettings()
                    }
                }
            }
            actions.onMessageShown()
        }
    }

    val gestures =
        GestureGate.of(
            state,
            overlayOpen = menuOpen || tabsOpen || themeDialogOpen,
            systemGestureNav = systemGestureNav,
        )
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            // Fullscreen (AC20): chrome bị gỡ, engine vẫn trong cây (không pause WebView đang phát video).
            if (!state.isFullscreen) BrowserTopBar(state = state, actions = actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Spec 008: vuốt cạnh (chỉ khi không có điều hướng cử chỉ và đi được) bọc nội dung.
                EdgeSwipeOverlay(
                    edgeBack = gestures.edgeBack,
                    edgeForward = gestures.edgeForward,
                    onBack = actions.onBack,
                    onForward = actions.onForward,
                ) {
                    when {
                        state.isHome && state.selectedIsPrivate -> {
                            PrivateLanding(
                                onSearchClick = actions.onStartEditing,
                            )
                        }

                        state.isHome -> {
                            homeContent()
                        }

                        else -> {
                            engineContent()
                        }
                    }
                }
                // Snackbar ngay trên bottom bar (đáy vùng nội dung).
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter)) { data ->
                    val tag = if ((data.visuals as? MessageVisuals)?.editAction == true) BOOKMARK_EDIT_TAG else null
                    Snackbar(
                        action =
                            data.visuals.actionLabel?.let { label ->
                                {
                                    TextButton(
                                        onClick = { data.performAction() },
                                        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
                                    ) { Text(label) }
                                }
                            },
                    ) { Text(data.visuals.message) }
                }
            }
            val find = state.find
            if (find != null && !state.isFullscreen) {
                FindBar(
                    state = find,
                    onQueryChange = actions.onFindQueryChange,
                    onFindNext = actions.onFindNext,
                    onClose = actions.onCloseFind,
                )
            }
            if (!state.isFullscreen) {
                ChromeBottom(
                    state = state,
                    actions = actions,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onTabsClick = {
                        actions.onOpenTabSwitcher()
                        switcherPrivate = state.selectedIsPrivate
                        actions.onSwitcherGroupChange(switcherPrivate)
                        onSwitcherOpenChange(true)
                        tabsOpen = true
                    },
                    onThemeClick = { themeDialogOpen = true },
                    tabSwipeEnabled = gestures.tabSwipe,
                )
            }
        }
        if (backIndicatorVisible) BackGestureIndicator(progress = { backProgress }, fromRight = backFromRight)
        if (state.isFullscreen) fullscreenContent()
        // Spec 009: toàn màn hình, phủ cả top/bottom bar; có snackbar "Hoàn tác" riêng nằm trên lưới.
        if (tabsOpen) {
            TabSwitcher(
                tabs = state.tabs,
                selectedTabId = state.selectedTabId,
                thumbnail = thumbnail,
                favicon = favicon,
                onSelect = actions.onSelectTab,
                onClose = actions.onCloseTab,
                onUndoClose = actions.onUndoCloseTab,
                onNewTab = actions.onNewTab,
                onCloseAll = actions.onCloseAllTabs,
                onDismiss = { tabsOpen = false },
                selectedIsPrivate = state.selectedIsPrivate,
                canOpenPrivate = state.canOpenPrivate,
                onNewNormalTab = actions.onNewNormalTab,
                onNewPrivateTab = actions.onNewPrivateTab,
                onClosePrivate = actions.onClosePrivateTabs,
                showPrivate = switcherPrivate,
                onShowPrivateChange = {
                    switcherPrivate = it
                    actions.onSwitcherGroupChange(it)
                },
            )
        }
    }

    val prompt = state.externalPrompt
    val promptTabId = state.externalPromptTabId
    if (prompt != null && promptTabId != null) {
        ExternalAppDialog(request = prompt) { open -> actions.onExternalAppDecision(promptTabId, prompt.id, open) }
    }

    if (themeDialogOpen) {
        ThemeDialog(
            settings = themeSettings,
            onChange = actions.onThemeChange,
            onDismiss = { themeDialogOpen = false },
        )
    }
}

/** Bottom bar + menu tràn (spec 005); ẩn khi fullscreen (spec 007 AC20). */
@Composable
private fun ChromeBottom(
    state: BrowserUiState,
    actions: BrowserActions,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onTabsClick: () -> Unit,
    onThemeClick: () -> Unit,
    tabSwipeEnabled: Boolean,
) {
    Box {
        BrowserBottomBar(
            state = state,
            actions = actions,
            tabSwipeEnabled = tabSwipeEnabled,
            onTabsClick = onTabsClick,
            onMenuClick = { onMenuOpenChange(true) },
        )
        Box(Modifier.align(Alignment.TopEnd)) {
            BrowserMenu(
                expanded = menuOpen,
                canShare = canShare(state.url),
                onDismiss = { onMenuOpenChange(false) },
                onReload = {
                    onMenuOpenChange(false)
                    actions.onReload()
                },
                onNewTab = {
                    onMenuOpenChange(false)
                    actions.onNewTab()
                },
                onShare = {
                    onMenuOpenChange(false)
                    actions.onShare()
                },
                showSetDefault = state.showSetDefaultBrowser,
                onSetDefault = {
                    onMenuOpenChange(false)
                    actions.onSetDefaultBrowser()
                },
                onTheme = {
                    onMenuOpenChange(false)
                    onThemeClick()
                },
                canBookmark = state.canBookmark,
                isBookmarked = state.isBookmarked,
                onToggleBookmark = {
                    onMenuOpenChange(false)
                    actions.onToggleBookmark()
                },
                onBookmarks = {
                    onMenuOpenChange(false)
                    actions.onOpenBookmarks()
                },
                onHistory = {
                    onMenuOpenChange(false)
                    actions.onOpenHistory()
                },
                onSettings = {
                    onMenuOpenChange(false)
                    actions.onOpenSettings()
                },
                canFind = state.canFind,
                onFind = {
                    onMenuOpenChange(false)
                    actions.onOpenFind()
                },
                canToggleDesktopSite = state.canToggleDesktopSite,
                isDesktopSite = state.isDesktopSite,
                onToggleDesktopSite = {
                    onMenuOpenChange(false)
                    actions.onToggleDesktopSite()
                },
                canOpenPrivate = state.canOpenPrivate,
                onNewPrivateTab = {
                    onMenuOpenChange(false)
                    actions.onNewPrivateTab()
                },
            )
        }
    }
}

private const val BOOKMARK_EDIT_TAG = BrowserTestTags.BOOKMARK_EDIT_ACTION

/** Nội dung snackbar; [editAction] đánh dấu nút "Sửa" của thông báo đã thêm dấu trang (để test/a11y tìm). */
private class MessageVisuals(
    override val message: String,
    override val actionLabel: String?,
    val editAction: Boolean,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
}

@Composable
private fun messageText(code: String): String =
    when (code) {
        BrowserMessages.BLOCKED_INPUT -> stringResource(R.string.browser_message_blocked)
        BrowserMessages.INTENT_REJECTED -> stringResource(R.string.browser_message_intent_rejected)
        BrowserMessages.EXTERNAL_APP_NOT_FOUND -> stringResource(R.string.browser_message_external_not_found)
        BrowserMessages.EXTERNAL_APP_BLOCKED -> stringResource(R.string.browser_message_external_blocked)
        BrowserMessages.SETTINGS_UNAVAILABLE -> stringResource(R.string.browser_message_settings_unavailable)
        BrowserMessages.POPUP_BLOCKED -> stringResource(R.string.browser_message_popup_blocked)
        BrowserMessages.PERMISSION_DENIED -> stringResource(R.string.browser_message_permission_denied)
        BrowserMessages.DOWNLOAD_UNSUPPORTED -> stringResource(R.string.browser_message_download_unsupported)
        BrowserMessages.DOWNLOAD_FAILED -> stringResource(R.string.browser_message_download_failed)
        BrowserMessages.FILE_CHOOSER_UNAVAILABLE -> stringResource(R.string.browser_message_file_chooser_unavailable)
        BrowserMessages.BOOKMARK_ADDED -> stringResource(R.string.browser_message_bookmark_added)
        BrowserMessages.BOOKMARK_FAILED -> stringResource(R.string.browser_message_bookmark_failed)
        BrowserMessages.BOOKMARK_REMOVED -> stringResource(R.string.browser_message_bookmark_removed)
        BrowserMessages.PRIVATE_UNAVAILABLE -> stringResource(R.string.browser_message_private_unavailable)
        else -> code
    }

/**
 * Nối [BrowserViewModel] ↔ [BrowserScreen]; hiển thị `view` của session được chọn, mở chooser khi Share.
 *
 * Spec 006: [defaultBrowserController] (mặc định [RoleManagerDefaultBrowser]) được hỏi lại ở mỗi `ON_RESUME`;
 * [onReturnToCaller] được gọi sau khi tab mở từ intent bị đóng bằng Back (mặc định `moveTaskToBack(true)` trên
 * Activity chứa composable).
 *
 * Theme do app sở hữu: [themeSettings] hiển thị trong `ThemeDialog`, lựa chọn mới gửi qua [onThemeChange] (spec 011).
 */
@Composable
fun BrowserRoute(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
    defaultBrowserController: DefaultBrowserController? = null,
    onReturnToCaller: (() -> Unit)? = null,
    themeSettings: ThemeSettings = ThemeSettings(),
    onThemeChange: (ThemeSettings) -> Unit = {},
    /** Spec 010: trang chủ (`:app` nối `HomeRoute`), hiển thị khi tab đang chọn ở home. */
    homeContent: @Composable () -> Unit = {},
    /** Spec 009: kho thumbnail (`:app` tạo, cũng nhận `onTrimMemory`); null → mọi thẻ dùng placeholder. */
    thumbnails: BitmapTabThumbnailRepository? = null,
    /** Spec 012: màn hình khác (Lịch sử, ...) đang phủ lên trình duyệt → pause trang. */
    overlayOpen: Boolean = false,
    /** Spec 012: `:app` mở lớp phủ Lịch sử. */
    onOpenHistory: () -> Unit = {},
    /** Spec 014: `:app` mở lớp phủ Cài đặt. */
    onOpenSettings: () -> Unit = {},
    /** Spec 013: `:app` mở lớp phủ Dấu trang. */
    onOpenBookmarks: () -> Unit = {},
    /** Spec 013: `:app` mở hộp thoại sửa dấu trang [id] (menu "Sửa dấu trang" hoặc snackbar). */
    onEditBookmark: (Long) -> Unit = {},
    /** Spec 016: `:app` biết trình chuyển tab đang mở để đặt `FLAG_SECURE` cho nhóm ẩn danh. */
    onSwitcherVisibilityChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val currentSwitcherVisibility by rememberUpdatedState(onSwitcherVisibilityChange)
    val currentEditBookmark by rememberUpdatedState(onEditBookmark)
    LaunchedEffect(viewModel) { viewModel.bookmarkEditRequests.collect { currentEditBookmark(it) } }
    val session by viewModel.selectedSession.collectAsState()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.browser_share)
    val controller =
        defaultBrowserController ?: remember(context) { RoleManagerDefaultBrowser(context.applicationContext) }
    val returnToCaller by rememberUpdatedState(
        onReturnToCaller ?: { context.findActivity()?.moveTaskToBack(true) },
    )
    val setDefault = rememberDefaultBrowserRequest(viewModel, controller)
    TrackAppVisibility(viewModel)
    // Ở home EngineContainer không được compose nên không tự pause session bên dưới (spec 010 AC18).
    // Tab switcher phủ toàn màn hình (spec 009): pause trang phía sau khi nó mở, resume khi đóng.
    var switcherOpen by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val covered = switcherOpen || overlayOpen
    LaunchedEffect(session, state.isHome, covered) {
        if (state.isHome || covered) {
            session?.onPause()
        } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            session?.onResume()
        }
    }
    // Spec 008 AC17: kéo-để-tải-lại tắt khi sửa URL, fullscreen, có dialog, switcher mở hoặc ở home.
    val pullEnabled =
        GestureGate.of(state, overlayOpen = covered, systemGestureNav = false).pullToRefresh
    val actions =
        remember(
            viewModel,
            context,
            shareTitle,
            setDefault,
            onThemeChange,
            onOpenHistory,
            onOpenSettings,
            onOpenBookmarks,
        ) {
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
                onSetDefaultBrowser = setDefault,
                onExternalAppDecision = { tabId, requestId, open ->
                    viewModel.onExternalAppDecision(tabId, requestId, open)
                },
                onReturnToCaller = {
                    viewModel.onReturnToCaller()
                    returnToCaller()
                },
                onThemeChange = onThemeChange,
                onExitFullscreen = viewModel::onExitFullscreen,
                onOpenAppSettings = { openAppDetailsSettings(context) },
                onOpenUrl = viewModel::onOpenUrl,
                onOpenTabSwitcher = viewModel::onOpenTabSwitcher,
                onUndoCloseTab = viewModel::onUndoCloseTab,
                onCloseAllTabs = viewModel::onCloseAllTabs,
                onSelectAdjacentTab = viewModel::onSelectAdjacentTab,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
                onOpenBookmarks = onOpenBookmarks,
                onToggleBookmark = viewModel::onToggleBookmark,
                onEditBookmark = { currentEditBookmark(it) },
                onUndoBookmarkRemoved = viewModel::onUndoBookmarkRemoved,
                onOpenFind = viewModel::onOpenFind,
                onFindQueryChange = viewModel::onFindQueryChange,
                onFindNext = viewModel::onFindNext,
                onCloseFind = viewModel::onCloseFind,
                onToggleDesktopSite = viewModel::onToggleDesktopSite,
                onNewNormalTab = viewModel::onNewNormalTab,
                onNewPrivateTab = viewModel::onNewPrivateTab,
                onClosePrivateTabs = viewModel::onClosePrivateTabs,
                onSwitcherGroupChange = viewModel::onSwitcherGroupChange,
                onResolvePrivateAvailability = viewModel::onResolvePrivateAvailability,
            )
        }
    BrowserScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        themeSettings = themeSettings,
        fullscreenContent = { FullscreenContainer(view = session?.fullscreenView) },
        engineContent = {
            PullToRefreshHost(
                session = session,
                enabled = pullEnabled,
                onReload = viewModel::onReload,
            ) { EngineContainer(session = session, paused = covered) }
        },
        homeContent = homeContent,
        thumbnail = { id -> thumbnails?.thumbnail(id) },
        favicon = { id -> rememberFavicon(viewModel, id, state.tabs) },
        onSwitcherOpenChange = {
            switcherOpen = it
            currentSwitcherVisibility(it)
        },
    )
    KeepScreenOnAndImmersive(enabled = state.isFullscreen)
    val onPermissionDecision: (String, Long, Boolean) -> Unit = viewModel::onPermissionDecision
    PrivateKeyboard(enabled = state.selectedIsPrivate) {
        EngineRequestHost(
            tabId = state.selectedTabId,
            request = state.pendingRequest,
            pageOrigin = originOf(state.url),
            runtimePermissions = viewModel.runtimePermissions,
            onRespond = { tabId, requestId, response -> viewModel.onRespond(tabId, requestId, response) },
            onPermissionDecision = onPermissionDecision,
            onFileChooserUnavailable = viewModel::onFileChooserUnavailable,
            onDownloadFailed = viewModel::onDownloadFailed,
            isPrivate = state.selectedIsPrivate,
        )
    }
}

private val NO_FAVICON: StateFlow<Bitmap?> = MutableStateFlow(null)

/** Favicon hiện tại của session tab [id] (spec 009); null nếu tab không có session sống hoặc chưa có favicon. */
@Composable
private fun rememberFavicon(
    viewModel: BrowserViewModel,
    id: String,
    tabs: List<TabItem>,
): ImageBitmap? {
    val flow = remember(id, tabs) { viewModel.faviconOf(id) ?: NO_FAVICON }
    val bitmap by flow.collectAsState()
    return remember(bitmap) { bitmap?.asImageBitmap() }
}

/**
 * Fullscreen (spec 007 AC20): giữ màn hình sáng và ẩn system bar (vuốt để hiện tạm) khi [enabled]; gỡ khi tắt
 * hoặc composable bị gỡ.
 */
@Composable
private fun KeepScreenOnAndImmersive(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (enabled) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                insets.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/** Màn hình chi tiết app (cấp lại quyền đã bị chặn "Không hỏi lại"). */
private fun openAppDetailsSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}

/**
 * Vùng video toàn màn hình (spec 007 AC20): hiển thị [view] của `onShowCustomView`; null (Fake) → placeholder.
 * Không đổi context của view (view thuộc WebView đang gắn ở `engine_container`).
 */
@Composable
fun FullscreenContainer(
    view: View?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black).testTag(BrowserTestTags.FULLSCREEN_CONTAINER)) {
        if (view != null) {
            AndroidView(
                factory = { FrameLayout(it) },
                modifier = Modifier.fillMaxSize(),
                update = { container ->
                    if (container.childCount != 1 || container.getChildAt(0) !== view) {
                        container.removeAllViews()
                        (view.parent as? ViewGroup)?.removeView(view)
                        container.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
                onRelease = { container -> container.removeAllViews() },
            )
        }
    }
}

/**
 * Trình duyệt mặc định (spec 006 AC18–AC19): tính lại ở mỗi `ON_RESUME`, trả về hành động của mục menu. Mục
 * menu chỉ hiện khi role khả dụng và chưa giữ. Sau [MAX_ROLE_CANCELS] lần hủy liên tiếp (RoleManager có thể tự
 * hủy khi người dùng chọn "Không hỏi lại"), mở Settings thay vì hỏi role. Bộ đếm chỉ nằm trong state UI.
 */
@Composable
private fun rememberDefaultBrowserRequest(
    viewModel: BrowserViewModel,
    controller: DefaultBrowserController,
): () -> Unit {
    val context = LocalContext.current
    var roleCancels by rememberSaveable { mutableIntStateOf(0) }
    val refresh = {
        viewModel.onDefaultBrowserChanged(isDefault = !controller.isRoleAvailable || controller.isDefault())
    }
    val roleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when {
                controller.isDefault() -> roleCancels = 0
                result.resultCode == Activity.RESULT_CANCELED -> roleCancels++
            }
            refresh()
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(context, controller, viewModel, roleLauncher) {
        {
            val useRole = controller.isRoleAvailable && roleCancels < MAX_ROLE_CANCELS
            val launched =
                useRole &&
                    try {
                        roleLauncher.launch(controller.requestIntent())
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    }
            if (!launched && !openDefaultAppsSettings(context)) viewModel.onDefaultBrowserSettingsUnavailable()
        }
    }
}

private const val MAX_ROLE_CANCELS = 2

/** Báo cho ViewModel app có hiển thị (≥ STARTED) không: chỉ mở thẳng app ngoài khi đang hiển thị (spec 006). */
@Composable
private fun TrackAppVisibility(viewModel: BrowserViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val lifecycle = lifecycleOwner.lifecycle
        viewModel.onAppVisibilityChanged(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onAppVisibilityChanged(true)
                    Lifecycle.Event.ON_STOP -> viewModel.onAppVisibilityChanged(false)
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** Mở Settings → Default apps; ROM thiếu màn hình đó → Settings chung. false nếu không mở được gì. */
private fun openDefaultAppsSettings(context: Context): Boolean =
    listOf(defaultAppsSettingsIntent(), Intent(Settings.ACTION_SETTINGS)).any { intent ->
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
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
    /** Spec 009: true khi trang bị che (tab switcher) → không `onResume` theo lifecycle. */
    paused: Boolean = false,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPaused by rememberUpdatedState(paused)
    if (session != null) {
        DisposableEffect(session, lifecycleOwner) {
            val lifecycle = lifecycleOwner.lifecycle
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> if (!currentPaused) session.onResume()
                        Lifecycle.Event.ON_PAUSE -> session.onPause()
                        else -> Unit
                    }
                }
            // addObserver phát lại ON_RESUME nếu lifecycle đang RESUMED; nếu chưa thì đảm bảo đang pause.
            if (currentPaused || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.onPause()
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
