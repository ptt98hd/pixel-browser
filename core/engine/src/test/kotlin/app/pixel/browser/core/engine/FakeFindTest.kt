package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 015 AC1–AC2 trên [FakeEngineSession] + các bản ghi riêng của Fake (`findQueries`, `findNextCalls`, `clearFindCalls`). */
class FakeFindTest : FindContractTest() {
    override fun createSession(): EngineSession = FakeEngineSession()

    private val EngineSession.fake get() = this as FakeEngineSession

    override fun EngineSession.emitFindResult(
        active: Int,
        total: Int,
        done: Boolean,
    ) = fake.simulateFindResult(active, total, done)

    override fun EngineSession.emitPageStarted(url: String) = fake.simulatePageStarted(url)

    override fun EngineSession.emitPageFinished(url: String) {
        // Đang tải đúng [url] (vd. sau goBack) thì chỉ commit; ngược lại tải rồi commit.
        if (!(fake.state.value.url == url && fake.state.value.isLoading)) fake.loadUrl(url)
        fake.simulatePageFinished(url)
    }

    @Test
    fun ac2_fake_recordsFindQueriesNextCallsAndClearCalls() {
        val s = FakeEngineSession()
        s.find("k")
        s.find("ko")
        s.find("  ")
        s.findNext(true)
        s.findNext(false)
        s.clearFind()
        assertEquals(listOf("k", "ko"), s.findQueries)
        assertEquals(listOf(true, false), s.findNextCalls)
        // find("  ") ủy quyền cho clearFind() nên tính một lần.
        assertEquals(2, s.clearFindCalls)
    }

    @Test
    fun ac2_fake_navigationResetsStateButDoesNotCountAsClearFindCall() {
        val s = FakeEngineSession()
        s.find("abc")
        s.simulatePageStarted("https://b.com/")
        assertEquals(FindState(), s.findState.value)
        assertEquals(0, s.clearFindCalls)
    }

    @Test
    fun ac2_fake_findQueries_recordsTruncatedQuery() {
        val s = FakeEngineSession()
        s.find("y".repeat(300))
        assertEquals(listOf("y".repeat(256)), s.findQueries)
    }
}
