package app.pixel.browser.core.engine

class FakeEngineSessionContractTest : EngineSessionContractTest() {
    override fun createSession(): EngineSession = FakeEngineSession()

    override fun EngineSession.emitProgress(progress: Int) = (this as FakeEngineSession).simulateProgress(progress)

    override fun EngineSession.emitPageFinished(url: String) = (this as FakeEngineSession).simulatePageFinished(url)

    override fun EngineSession.emitSslError() = (this as FakeEngineSession).simulateSslError()
}
