package app.pixel.browser

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.history.HistoryClearer
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.ui.PixelTheme
import app.pixel.browser.core.ui.resolveDarkTheme
import app.pixel.browser.feature.bookmarks.BookmarkEditRoute
import app.pixel.browser.feature.bookmarks.BookmarksRoute
import app.pixel.browser.feature.bookmarks.BookmarksViewModel
import app.pixel.browser.feature.bookmarks.ContentResolverBookmarkFile
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.browser.PrivateAutoCloseGuard
import app.pixel.browser.feature.browser.SecureWindowPolicy
import app.pixel.browser.feature.browser.intent.AndroidExternalAppLauncher
import app.pixel.browser.feature.browser.intent.IncomingIntent
import app.pixel.browser.feature.browser.intent.IncomingIntentResolver
import app.pixel.browser.feature.browser.intent.from
import app.pixel.browser.feature.browser.web.AndroidRuntimePermissionGate
import app.pixel.browser.feature.history.HistoryRoute
import app.pixel.browser.feature.history.HistoryViewModel
import app.pixel.browser.feature.home.AndroidBackgroundImageImporter
import app.pixel.browser.feature.home.HomeRoute
import app.pixel.browser.feature.home.HomeViewModel
import app.pixel.browser.feature.settings.AppInfo
import app.pixel.browser.feature.settings.SettingsRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app: PixelBrowserApplication get() = application as PixelBrowserApplication

    /**
     * Session (WebView) sống trong ViewModel nên qua được xoay màn hình. WebView tạo bằng
     * [MutableContextWrapper] của application context; `EngineContainer` trỏ base context sang Activity khi gắn.
     *
     * TabManager phải tạo trên main thread (tạo WebView) và constructor đọc `tabs.json` đồng bộ — xem spec 005
     * "Quyết định".
     */
    private val viewModel: BrowserViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = app
                // TabManager báo "hết tab ẩn danh" khi mọi session ẩn danh đã đóng: dọn thumbnail/quyền ẩn danh rồi xóa
                // profile (spec 016 AC24). ViewModel được gán ngay sau đó; callback không chạy trong constructor.
                var browser: BrowserViewModel? = null
                val tabs =
                    TabManager(
                        sessionFactory = appContext.sessionFactory,
                        store = appContext.tabStore,
                        onPrivateTabsEmpty = {
                            browser?.onPrivateTabsEmpty()
                            appContext.privateSpace.wipe()
                        },
                    )
                BrowserViewModel(
                    tabs,
                    // Application context: ViewModel sống lâu hơn Activity (launcher thêm FLAG_ACTIVITY_NEW_TASK).
                    externalAppLauncher = AndroidExternalAppLauncher(appContext),
                    // Gate giữ application context; Activity chỉ gắn launcher xin quyền (xem onCreate).
                    runtimePermissions = AndroidRuntimePermissionGate(appContext),
                    thumbnails = appContext.thumbnails,
                    searchEngines = appContext.searchEngine,
                    suggestionProvider = appContext.suggestionProvider,
                    pageVisits = appContext.historyRecorder,
                    bookmarks = appContext.bookmarkRepository,
                    desktopSites = appContext.desktopSites,
                    privateAvailable = { appContext.privateSpace.isAvailable },
                    privateAutoClose =
                        PrivateAutoCloseGuard(
                            clock = SystemClock::elapsedRealtime,
                            autoClose = { appContext.privacySettings.value.privateAutoClose },
                        ),
                ).also { browser = it }
            }
        }
    }

    /** Launcher xin quyền Android cho trang web (spec 007 AC15); kết quả về gate trong ViewModel. */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            (viewModel.runtimePermissions as? AndroidRuntimePermissionGate)?.onResult(result)
        }

    /** Một `HomeViewModel` cho mọi tab: sửa trang chủ ở tab này thấy ngay ở tab khác (spec 010). */
    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = app
                HomeViewModel(
                    appContext.homeStore,
                    AndroidBackgroundImageImporter(appContext),
                    Dispatchers.IO,
                    // Scope cấp process: ghi cấu hình đang chờ không mất khi Activity bị hủy.
                    writeScope = appContext.ioScope,
                )
            }
        }
    }

    /** Màn hình Lịch sử (lớp phủ); ViewModel cấp Activity nên giữ truy vấn khi xoay (spec 012). */
    private val historyViewModel: HistoryViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = app
                HistoryViewModel(
                    appContext.historyStore,
                    HistoryClearer(appContext.historyStore, System::currentTimeMillis),
                )
            }
        }
    }

    /** Màn hình Dấu trang (lớp phủ); ViewModel cấp Activity nên giữ thư mục đang xem khi xoay (spec 013). */
    private val bookmarksViewModel: BookmarksViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = app
                BookmarksViewModel(
                    appContext.bookmarkRepository,
                    ContentResolverBookmarkFile(appContext.contentResolver),
                    ioDispatcher = Dispatchers.IO,
                    rootTitle = appContext.getString(R.string.bookmarks_root_title),
                    importFolderPrefix = appContext.getString(R.string.bookmarks_import_folder_prefix),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Bottom bar luôn có nền surfaceContainer sau inset nên không cần lớp scrim của hệ thống (spec 011 AC13).
        window.isNavigationBarContrastEnforced = false
        (viewModel.runtimePermissions as? AndroidRuntimePermissionGate)?.attach(permissionLauncher)
        setContent {
            val settings by app.themeSettings.collectAsState()
            val browserState by viewModel.uiState.collectAsState()
            val privacy by app.privacySettings.collectAsState()
            var switcherOpen by remember { mutableStateOf(false) }
            SecureWindowEffect(
                SecureWindowPolicy.shouldSecure(
                    blockScreenshots = privacy.blockPrivateScreenshots,
                    selectedIsPrivate = browserState.selectedIsPrivate,
                    switcherOpen = switcherOpen,
                    switcherShowsPrivate = browserState.switcherShowsPrivate,
                ),
            )
            PixelTheme(settings, private = browserState.selectedIsPrivate) {
                SystemBarsEffect(
                    dark = resolveDarkTheme(settings.mode, isSystemInDarkTheme(), browserState.selectedIsPrivate),
                )
                // Điều hướng cấp app: màn hình phụ là lớp phủ trên BrowserRoute (spec 012), sống qua xoay màn hình.
                var destination by rememberSaveable { mutableStateOf(AppDestination.Browser) }
                // Hộp thoại sửa dấu trang mở từ snackbar/menu của trình duyệt (id < 0: đang đóng).
                var editingBookmark by rememberSaveable { mutableLongStateOf(NO_BOOKMARK) }
                BrowserRoute(
                    viewModel,
                    // Nội dung dưới lớp phủ không được TalkBack đọc/focus.
                    modifier =
                        if (destination != AppDestination.Browser || editingBookmark != NO_BOOKMARK) {
                            Modifier.semantics { hideFromAccessibility() }
                        } else {
                            Modifier
                        },
                    overlayOpen = destination != AppDestination.Browser,
                    onOpenHistory = { destination = AppDestination.History },
                    onOpenSettings = { destination = AppDestination.Settings },
                    onOpenBookmarks = { destination = AppDestination.Bookmarks },
                    onEditBookmark = { editingBookmark = it },
                    onReturnToCaller = { moveTaskToBack(true) },
                    onSwitcherVisibilityChange = { switcherOpen = it },
                    themeSettings = settings,
                    thumbnails = app.thumbnails,
                    onThemeChange = ::onThemeChange,
                    homeContent = {
                        val engine by app.searchEngine.collectAsState()
                        HomeRoute(
                            homeViewModel,
                            onOpenUrl = viewModel::onOpenUrl,
                            onSearchClick = viewModel::onStartEditing,
                            searchEngineName = engine.name,
                        )
                    },
                )
                if (destination == AppDestination.History) {
                    HistoryRoute(
                        historyViewModel,
                        onOpenUrl = {
                            destination = AppDestination.Browser
                            viewModel.onOpenUrl(it)
                        },
                        onOpenInNewTab = {
                            destination = AppDestination.Browser
                            viewModel.onOpenUrlInNewTab(it)
                        },
                        onClose = { destination = AppDestination.Browser },
                    )
                }
                if (destination == AppDestination.Bookmarks) {
                    BookmarksRoute(
                        bookmarksViewModel,
                        onOpenUrl = {
                            destination = AppDestination.Browser
                            viewModel.onOpenUrl(it)
                        },
                        onOpenInNewTab = {
                            destination = AppDestination.Browser
                            viewModel.onOpenUrlInNewTab(it)
                        },
                        onClose = { destination = AppDestination.Browser },
                    )
                }
                if (editingBookmark != NO_BOOKMARK) {
                    BookmarkEditRoute(
                        app.bookmarkRepository,
                        editingBookmark,
                        onDismiss = { editingBookmark = NO_BOOKMARK },
                        onRemoved = viewModel::onBookmarkRemoved,
                        rootTitle = stringResource(R.string.bookmarks_root_title),
                    )
                }
                if (destination == AppDestination.Settings) {
                    val registry = remember { settingsRegistry(app, appInfo()) }
                    SettingsRoute(
                        registry,
                        onClose = { destination = AppDestination.Browser },
                        // Mở liên kết (mã nguồn, giấy phép) trong tab mới và đóng Cài đặt.
                        onOpenUrl = {
                            destination = AppDestination.Browser
                            viewModel.onOpenUrlInNewTab(it)
                        },
                    )
                }
            }
        }
        // Quét profile ẩn danh còn sót khi rảnh sau khung hình đầu, không nằm trên đường khởi động (spec 016 review).
        window.decorView.doOnPreDraw {
            Looper.myQueue().addIdleHandler {
                app.privateSpace.sweepStale()
                false
            }
        }
        // Spec 006 AC16: intent chỉ xử lý ở lần tạo đầu; recreate (xoay, process death) không xử lý lại.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Spec 006 AC15: intent từ app khác → tab mới (xem [IncomingIntentResolver]). */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val request = IncomingIntentResolver.resolve(IncomingIntent.from(intent), viewModel.searchEngine)
        viewModel.onIncomingIntent(request)
    }

    /** Phiên bản và liên kết mã nguồn cho trang Giới thiệu (spec 014 AC16). */
    private fun appInfo(): AppInfo {
        val info = packageManager.getPackageInfo(packageName, 0)
        return AppInfo(info.versionName.orEmpty(), info.longVersionCode, SOURCE_URL)
    }

    /** Đổi Sáng/Tối gây configuration change → Activity tạo lại; WebView nằm trong ViewModel nên giữ trang. */
    private fun onThemeChange(next: ThemeSettings) = app.updateTheme { next }

    /** Icon status/navigation bar tối trên nền sáng và ngược lại, theo theme đang hiển thị (spec 011 AC13). */
    @Composable
    private fun SystemBarsEffect(dark: Boolean) {
        val view = LocalView.current
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    /** `FLAG_SECURE` theo [SecureWindowPolicy]: chặn ảnh chụp, quay màn hình và Recents (spec 016 AC16). */
    @Composable
    private fun SecureWindowEffect(secure: Boolean) {
        DisposableEffect(secure) {
            if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Tự đóng tab ẩn danh nếu đã rời app đủ lâu (spec 016 AC15).
        viewModel.onForegroundReturn()
    }

    override fun onDestroy() {
        (viewModel.runtimePermissions as? AndroidRuntimePermissionGate)?.detach(permissionLauncher)
        super.onDestroy()
    }

    private companion object {
        const val NO_BOOKMARK = -1L
        const val SOURCE_URL = "https://github.com/ptt98hd/pixel-browser"
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackground(isChangingConfigurations)
        // Chụp snapshot (saveState WebView) trên main, ghi đĩa trên IO.
        val snapshot = viewModel.tabsSnapshot()
        val store = app.tabStore
        app.ioScope.launch { store.write(snapshot) }
    }
}
