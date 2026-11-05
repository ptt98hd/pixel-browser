package app.pixel.browser.core.engine

class FakeHistoryNavigationTest : HistoryNavigationContractTest() {
    override fun createSession(): EngineSession = FakeEngineSession()

    override fun EngineSession.emitPageFinished(url: String) = (this as FakeEngineSession).simulatePageFinished(url)

    override fun EngineSession.observedCacheMode(): CacheMode = (this as FakeEngineSession).cacheMode
}
