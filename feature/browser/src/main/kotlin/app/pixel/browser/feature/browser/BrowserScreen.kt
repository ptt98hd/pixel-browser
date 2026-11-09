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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.pixel.browser.feature.browser.intent.DefaultBrowserController
import app.pixel.browser.feature.browser.intent.RoleManagerDefaultBrowser
import app.pixel.browser.feature.browser.intent.defaultAppsSettingsIntent
import app.pixel.browser.feature.browser.tabs.BitmapTabThumbnailRepository
import app.pixel.browser.feature.browser.tabs.TabSwitcher
import app.pixel.browser.feature.browser.web.EngineRequestHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var tabsOpen by rememberSaveable { mutableStateOf(false) }
    var themeDialogOpen by rememberSaveable { mutableStateOf(false) }

    // AC31: (a) sheet/menu → đóng; (b) đang sửa → hủy sửa; (c) có lịch sử → goBack; (d) không chặn.
    // Spec 006 AC14: tab mở từ intent, hết lịch sử → đóng tab và quay về app gọi.
    BackHandler(enabled = menuOpen || tabsOpen || state.isEditingUrl || state.canGoBack || state.isExternalTab) {
        when {
            tabsOpen -> tabsOpen = false
            menuOpen -> menuOpen = false
            state.isEditingUrl -> actions.onCancelEditing()
            state.canGoBack -> actions.onBack()
            else -> actions.onReturnToCaller()
        }
    }
    // Spec 007 AC20: khi fullscreen, Back thoát fullscreen trước mọi nhánh của AC31 (handler đăng ký sau thắng).
    BackHandler(enabled = state.isFullscreen) { actions.onExitFullscreen() }

    val currentOnSwitcherOpenChange by rememberUpdatedState(onSwitcherOpenChange)
    LaunchedEffect(tabsOpen) { currentOnSwitcherOpenChange(tabsOpen) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messageText = state.message?.let { messageText(it) }
    val settingsLabel = stringResource(R.string.browser_message_permission_settings)
    LaunchedEffect(state.message) {
        if (messageText != null) {
            val withSettings = state.message == BrowserMessages.PERMISSION_DENIED
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(messageText, actionLabel = settingsLabel.takeIf { withSettings })
                if (result == SnackbarResult.ActionPerformed) actions.onOpenAppSettings()
            }
            actions.onMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            // Fullscreen (AC20): chrome bị gỡ, engine vẫn trong cây (không pause WebView đang phát video).
            if (!state.isFullscreen) BrowserTopBar(state = state, actions = actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.isHome) homeContent() else engineContent()
                // Snackbar ngay trên bottom bar (đáy vùng nội dung).
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            }
            if (!state.isFullscreen) {
                ChromeBottom(
                    state = state,
                    actions = actions,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onTabsClick = {
                        actions.onOpenTabSwitcher()
                        tabsOpen = true
                    },
                    onThemeClick = { themeDialogOpen = true },
                )
            }
        }
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
) {
    Box {
        BrowserBottomBar(
            state = state,
            actions = actions,
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
            )
        }
    }
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
) {
    val state by viewModel.uiState.collectAsState()
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
    LaunchedEffect(session, state.isHome, switcherOpen) {
        if (state.isHome || switcherOpen) {
            session?.onPause()
        } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            session?.onResume()
        }
    }
    val actions =
        remember(viewModel, context, shareTitle, setDefault, onThemeChange) {
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
            )
        }
    BrowserScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        themeSettings = themeSettings,
        fullscreenContent = { FullscreenContainer(view = session?.fullscreenView) },
        engineContent = { EngineContainer(session = session, paused = switcherOpen) },
        homeContent = homeContent,
        thumbnail = { id -> thumbnails?.thumbnail(id) },
        favicon = { id -> rememberFavicon(viewModel, id, state.tabs) },
        onSwitcherOpenChange = { switcherOpen = it },
    )
    KeepScreenOnAndImmersive(enabled = state.isFullscreen)
    EngineRequestHost(
        tabId = state.selectedTabId,
        request = state.pendingRequest,
        pageOrigin = originOf(state.url),
        runtimePermissions = viewModel.runtimePermissions,
        onRespond = { tabId, requestId, response -> viewModel.onRespond(tabId, requestId, response) },
        onPermissionDecision = { tabId, requestId, allow -> viewModel.onPermissionDecision(tabId, requestId, allow) },
        onFileChooserUnavailable = viewModel::onFileChooserUnavailable,
        onDownloadFailed = viewModel::onDownloadFailed,
    )
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
