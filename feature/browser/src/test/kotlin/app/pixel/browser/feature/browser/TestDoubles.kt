package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.tabs.EngineSessionFactory
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabSnapshotStore
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.intent.ExternalAppLauncher
import app.pixel.browser.feature.browser.intent.ExternalLaunchResult
import app.pixel.browser.feature.browser.intent.ExternalTarget

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

/** Môi trường chung: TabManager thật + SpySession + ViewModel. */
class BrowserFixture(
    store: InMemoryStore = InMemoryStore(),
    val launcher: RecordingLauncher = RecordingLauncher(),
    clock: () -> Long = { 0L },
) {
    val factory = SpySessionFactory()
    val tabManager = TabManager(factory, store)
    val viewModel = BrowserViewModel(tabManager, externalAppLauncher = launcher, clock = clock)

    val homeUrl: String get() = viewModel.homeUrl

    fun selected(): SpySession = tabManager.selectedSpy()

    fun state(): BrowserUiState = viewModel.uiState.value
}
