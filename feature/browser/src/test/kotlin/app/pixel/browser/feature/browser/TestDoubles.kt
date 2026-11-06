package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.tabs.EngineSessionFactory
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabSnapshotStore
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.intent.ExternalAppLauncher
import app.pixel.browser.feature.browser.intent.ExternalLaunchResult
import app.pixel.browser.feature.browser.intent.ExternalTarget
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

/** Môi trường chung: TabManager thật + SpySession + ViewModel. */
class BrowserFixture(
    store: InMemoryStore = InMemoryStore(),
    val launcher: RecordingLauncher = RecordingLauncher(),
    clock: () -> Long = { 0L },
    val permissionMemory: SitePermissionMemory = SitePermissionMemory(),
    val gate: FakePermissionGate = FakePermissionGate(),
) {
    val factory = SpySessionFactory()
    val tabManager = TabManager(factory, store)
    val viewModel =
        BrowserViewModel(
            tabManager,
            externalAppLauncher = launcher,
            clock = clock,
            permissionMemory = permissionMemory,
            runtimePermissions = gate,
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
        ): BrowserFixture {
            val store =
                InMemoryStore().apply {
                    stored =
                        TabsSnapshot(listOf(TabSnapshot(PAGE_TAB_ID, url)), PAGE_TAB_ID)
                }
            return BrowserFixture(store, launcher).apply {
                selected().fake.simulatePageFinished(url)
                selected().resetCalls()
            }
        }
    }
}
