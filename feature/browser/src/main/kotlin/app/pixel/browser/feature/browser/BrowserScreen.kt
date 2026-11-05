package app.pixel.browser.feature.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.MutableContextWrapper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.feature.browser.intent.DefaultBrowserController
import app.pixel.browser.feature.browser.intent.RoleManagerDefaultBrowser
import app.pixel.browser.feature.browser.intent.defaultAppsSettingsIntent
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
    engineContent: @Composable () -> Unit = {},
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
                        showSetDefault = state.showSetDefaultBrowser,
                        onSetDefault = {
                            menuOpen = false
                            actions.onSetDefaultBrowser()
                        },
                        onTheme = {
                            menuOpen = false
                            themeDialogOpen = true
                        },
                    )
                }
            }
        }
    }

    val prompt = state.externalPrompt
    val promptTabId = state.externalPromptTabId
    if (prompt != null && promptTabId != null) {
        ExternalAppDialog(request = prompt) { open -> actions.onExternalAppDecision(promptTabId, prompt.id, open) }
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

    if (themeDialogOpen) {
        ThemeDialog(
            settings = themeSettings,
            onChange = actions.onThemeChange,
            onDismiss = { themeDialogOpen = false },
        )
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
            )
        }
    BrowserScreen(state = state, actions = actions, modifier = modifier, themeSettings = themeSettings) {
        EngineContainer(session = session)
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
