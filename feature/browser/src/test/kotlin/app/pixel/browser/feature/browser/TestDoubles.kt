package app.pixel.browser.feature.browser

import app.pixel.browser.core.bookmarks.BookmarkRepository
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.history.PageVisit
import app.pixel.browser.core.history.PageVisitRecorder
import app.pixel.browser.core.tabs.EngineSessionFactory
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabSnapshotStore
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.intent.ExternalAppLauncher
import app.pixel.browser.feature.browser.intent.ExternalLaunchResult
import app.pixel.browser.feature.browser.intent.ExternalTarget
import app.pixel.browser.feature.browser.tabs.TabThumbnailRepository
import app.pixel.browser.feature.browser.web.RuntimePermissionGate
import app.pixel.browser.feature.browser.web.SitePermissionMemory

/** Bọc [FakeEngineSession] và đếm lời gọi để kiểm "được gọi đúng N lần" / "không được gọi". */
class SpySession(
    val fake: FakeEngineSession = FakeEngineSession(),
) : EngineSession by fake {
    val loadUrlCalls = mutableListOf<String>()
    var goBackCalls = 0
        private set
    var goForwardCalls = 0
        private set
    var reloadCalls = 0
        private set
    var stopLoadingCalls = 0
        private set

    override fun stopLoading() {
        stopLoadingCalls++
        fake.stopLoading()
    }

    override fun loadUrl(url: String) {
        loadUrlCalls += url
        fake.loadUrl(url)
    }

    override fun goBack() {
        goBackCalls++
        fake.goBack()
    }

    override fun goForward() {
        goForwardCalls++
        fake.goForward()
    }

    override fun reload() {
        reloadCalls++
        fake.reload()
    }

    /** Tải [url] và commit vào lịch sử. */
    fun visit(url: String) {
        loadUrl(url)
        fake.simulatePageFinished(url)
    }

    fun resetCalls() {
        loadUrlCalls.clear()
        goBackCalls = 0
        goForwardCalls = 0
        reloadCalls = 0
        stopLoadingCalls = 0
    }
}

class SpySessionFactory : EngineSessionFactory {
    val created = mutableListOf<SpySession>()

    override fun create(): EngineSession = SpySession().also { created += it }
}

class InMemoryStore : TabSnapshotStore {
    var stored: TabsSnapshot? = null

    override fun read(): TabsSnapshot? = stored

    override fun write(snapshot: TabsSnapshot) {
        stored = snapshot
    }
}

fun TabManager.spy(id: String): SpySession = session(id) as SpySession

fun TabManager.selectedSpy(): SpySession = spy(selectedTabId.value!!)

/** [ExternalAppLauncher] giả: ghi lại URI và trả [result] (spec 006). */
class RecordingLauncher(
    var result: ExternalLaunchResult = ExternalLaunchResult.Launched,
) : ExternalAppLauncher {
    val calls = mutableListOf<String>()

    /** Kết quả [describe]; mặc định dùng mô tả mặc định của interface. */
    var describeResult: ExternalTarget? = DEFAULT_DESCRIBE

    override fun launch(uri: String): ExternalLaunchResult {
        calls += uri
        return result
    }

    override fun describe(uri: String): ExternalTarget? =
        if (describeResult === DEFAULT_DESCRIBE) super.describe(uri) else describeResult

    private companion object {
        val DEFAULT_DESCRIBE = ExternalTarget("", null)
    }
}

/**
 * [RuntimePermissionGate] giả (spec 007): [alreadyGranted] là quyền OS đã có; [grantOnRequest] quyết định tập được
 * cấp khi xin (mặc định cấp hết). Ghi lại mọi lần xin.
 */
class FakePermissionGate(
    val alreadyGranted: MutableSet<String> = mutableSetOf(),
    var grantOnRequest: (Set<String>) -> Set<String> = { it },
) : RuntimePermissionGate {
    val requests = mutableListOf<Set<String>>()

    override suspend fun request(androidPermissions: Set<String>): Set<String> {
        requests += androidPermissions
        return grantOnRequest(androidPermissions).intersect(androidPermissions).also { alreadyGranted += it }
    }

    override fun isGranted(androidPermission: String): Boolean = androidPermission in alreadyGranted
}

/**
 * [TabThumbnailRepository] giả (spec 009): ghi lại mọi lời gọi cùng `selectedTabId` tại thời điểm gọi.
 * [selectedTabId] được [BrowserFixture] nối với `TabManager` ngay sau khi tạo.
 */
class FakeTabThumbnailRepository : TabThumbnailRepository {
    sealed interface Call {
        data class Capture(
            val tabId: String,
            val session: EngineSession,
            val persist: Boolean,
            val selectedAtCall: String?,
        ) : Call

        data class Remove(
            val tabId: String,
        ) : Call

        data class RetainOnly(
            val tabIds: Set<String>,
        ) : Call

        data object ClearDisk : Call
    }

    var selectedTabId: () -> String? = { null }
    val calls = mutableListOf<Call>()

    val captures: List<Call.Capture> get() = calls.filterIsInstance<Call.Capture>()
    val removed: List<String> get() = calls.filterIsInstance<Call.Remove>().map { it.tabId }
    val retains: List<Call.RetainOnly> get() = calls.filterIsInstance<Call.RetainOnly>()

    override fun capture(
        tabId: String,
        session: EngineSession,
        persist: Boolean,
    ) {
        calls += Call.Capture(tabId, session, persist, selectedTabId())
    }

    override fun remove(tabId: String) {
        calls += Call.Remove(tabId)
    }

    override fun retainOnly(tabIds: Set<String>) {
        calls += Call.RetainOnly(tabIds)
    }

    override fun clearDisk() {
        calls += Call.ClearDisk
    }
}

/** [PageVisitRecorder] giả (spec 012): ghi lại mọi lời gọi theo thứ tự. */
class FakePageVisitRecorder : PageVisitRecorder {
    val visits = mutableListOf<PageVisit>()
    val forgotten = mutableListOf<String>()

    override fun record(visit: PageVisit) {
        visits += visit
    }

    override fun forgetTab(tabId: String) {
        forgotten += tabId
    }
}

/** Môi trường chung: TabManager thật + SpySession + ViewModel. */
class BrowserFixture(
    store: InMemoryStore = InMemoryStore(),
    val launcher: RecordingLauncher = RecordingLauncher(),
    clock: () -> Long = { 0L },
    val permissionMemory: SitePermissionMemory = SitePermissionMemory(),
    val gate: FakePermissionGate = FakePermissionGate(),
    val thumbnails: TabThumbnailRepository = TabThumbnailRepository.None,
    val pageVisits: PageVisitRecorder = PageVisitRecorder.None,
    maxLiveSessions: Int = TabManager.DEFAULT_MAX_LIVE_SESSIONS,
    val bookmarks: BookmarkRepository? = null,
) {
    val factory = SpySessionFactory()
    val tabManager = TabManager(factory, store, maxLiveSessions)

    // Nối FakeTabThumbnailRepository với tab đang chọn trước khi ViewModel được tạo.
    @Suppress("unused")
    private val boundThumbnails =
        (thumbnails as? FakeTabThumbnailRepository)?.also { it.selectedTabId = { tabManager.selectedTabId.value } }
    val viewModel =
        BrowserViewModel(
            tabManager,
            externalAppLauncher = launcher,
            clock = clock,
            permissionMemory = permissionMemory,
            runtimePermissions = gate,
            thumbnails = thumbnails,
            pageVisits = pageVisits,
            bookmarks = bookmarks,
        )

    fun selected(): SpySession = tabManager.selectedSpy()

    fun state(): BrowserUiState = viewModel.uiState.value

    companion object {
        /** Id của tab trang web do [onPage] tạo. */
        const val PAGE_TAB_ID = "page"

        /**
         * Spec 010: tab home không còn là trang web. Môi trường với một tab thường (không phải home,
         * `backToHome = false`) đang chọn, đã tải xong [url], lịch sử engine chỉ có [url]; lời gọi đã được reset.
         */
        fun onPage(
            url: String,
            launcher: RecordingLauncher = RecordingLauncher(),
            thumbnails: TabThumbnailRepository = TabThumbnailRepository.None,
            pageVisits: PageVisitRecorder = PageVisitRecorder.None,
            maxLiveSessions: Int = TabManager.DEFAULT_MAX_LIVE_SESSIONS,
            bookmarks: BookmarkRepository? = null,
        ): BrowserFixture {
            val store =
                InMemoryStore().apply {
                    stored =
                        TabsSnapshot(listOf(TabSnapshot(PAGE_TAB_ID, url)), PAGE_TAB_ID)
                }
            return BrowserFixture(
                store,
                launcher,
                thumbnails = thumbnails,
                pageVisits = pageVisits,
                maxLiveSessions = maxLiveSessions,
                bookmarks = bookmarks,
            ).apply {
                selected().fake.simulatePageFinished(url)
                selected().resetCalls()
            }
        }
    }
}
