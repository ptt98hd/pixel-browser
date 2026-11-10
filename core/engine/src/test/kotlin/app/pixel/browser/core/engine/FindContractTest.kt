package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hợp đồng tìm trong trang (spec 015 B04, AC1–AC3) cho mọi [EngineSession]; Fake và WebView đều phải thỏa.
 * Tách khỏi `EngineSessionContractTest` để PR 4A (Find) độc lập với 4B (Desktop).
 */
abstract class FindContractTest {
    protected abstract fun createSession(): EngineSession

    /** Mô phỏng `FindListener.onFindResultReceived` (WebView: `dispatchFindResult`, Fake: `simulateFindResult`). */
    protected abstract fun EngineSession.emitFindResult(
        active: Int,
        total: Int,
        done: Boolean = true,
    )

    protected abstract fun EngineSession.emitPageStarted(url: String)

    /** Mô phỏng trang [url] tải xong và vào lịch sử (để goBack/goForward có đích). */
    protected abstract fun EngineSession.emitPageFinished(url: String)

    private fun searching(query: String = "abc"): EngineSession = createSession().also { it.find(query) }

    // --- AC1 ---

    @Test
    fun ac1_newSession_hasDefaultFindState() {
        assertEquals(FindState(), createSession().findState.value)
    }

    @Test
    fun ac1_find_publishesQueryAsSearching() {
        assertEquals(FindState("abc", 0, 0, isDone = false), searching().findState.value)
    }

    @Test
    fun ac1_findResult_updatesActiveIndexAndTotal() {
        val s = searching()
        s.emitFindResult(active = 1, total = 5, done = true)
        assertEquals(FindState("abc", 1, 5, true), s.findState.value)
    }

    @Test
    fun ac1_findResult_whileCounting_keepsNotDone() {
        val s = searching()
        s.emitFindResult(active = 0, total = 3, done = false)
        assertEquals(FindState("abc", 0, 3, false), s.findState.value)
        s.emitFindResult(active = 0, total = 7, done = true)
        assertEquals(FindState("abc", 0, 7, true), s.findState.value)
    }

    @Test
    fun ac1_find_emptyQuery_actsLikeClear() {
        val s = searching()
        s.emitFindResult(1, 5)
        s.find("")
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac1_find_blankQuery_actsLikeClear() {
        val s = searching()
        s.find("  ")
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac1_find_longQuery_isTruncatedTo256() {
        val s = createSession()
        s.find("x".repeat(300))
        assertEquals(MAX_FIND_QUERY, s.findState.value.query.length)
        assertEquals("x".repeat(256), s.findState.value.query)
    }

    @Test
    fun ac1_find_keepsInnerAndOuterWhitespaceOfNonBlankQuery() {
        val s = createSession()
        s.find(" a b ")
        assertEquals(" a b ", s.findState.value.query)
    }

    @Test
    fun ac1_find_newQueryReplacesOld_andResetsCounts() {
        val s = searching("abc")
        s.emitFindResult(active = 2, total = 9)
        s.find("xyz")
        assertEquals(FindState("xyz", 0, 0, isDone = false), s.findState.value)
    }

    @Test
    fun ac1_findResult_afterClearFind_isIgnored() {
        val s = searching()
        s.clearFind()
        s.emitFindResult(1, 5)
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac1_findResult_afterClose_isIgnored() {
        val s = searching()
        s.close()
        s.emitFindResult(1, 5)
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac1_findNext_doesNotChangeQuery() {
        val s = searching()
        s.emitFindResult(0, 4)
        s.findNext(true)
        s.findNext(false)
        assertEquals("abc", s.findState.value.query)
    }

    @Test
    fun ac1_findNext_withoutResults_isNoOpAndDoesNotThrow() {
        val s = createSession()
        s.findNext(true)
        s.findNext(false)
        s.find("abc")
        s.emitFindResult(0, 0)
        s.findNext(true)
        assertEquals(FindState("abc", 0, 0, true), s.findState.value)
    }

    @Test
    fun ac1_clearFind_resetsToDefault() {
        val s = searching()
        s.emitFindResult(1, 5)
        s.clearFind()
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac1_afterClose_everyCallIsNoOp_andStateIsDefault() {
        val s = searching()
        s.emitFindResult(1, 5)
        s.close()
        assertEquals(FindState(), s.findState.value)
        s.find("again")
        s.findNext(true)
        s.clearFind()
        assertEquals(FindState(), s.findState.value)
    }

    // --- AC2 ---

    private fun withResults(): EngineSession =
        createSession().also {
            it.emitPageFinished("https://a.com/")
            it.find("abc")
            it.emitFindResult(1, 5)
        }

    @Test
    fun ac2_pageStarted_resetsFindState() {
        val s = withResults()
        s.emitPageStarted("https://a.com/next")
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac2_loadUrl_resetsFindState() {
        val s = withResults()
        s.loadUrl("https://b.com/")
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac2_reload_resetsFindState() {
        val s = withResults()
        s.reload()
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac2_goBack_resetsFindState() {
        val s = createSession()
        s.emitPageFinished("https://a.com/")
        s.emitPageFinished("https://b.com/")
        s.find("abc")
        s.emitFindResult(1, 5)
        s.goBack()
        assertEquals(FindState(), s.findState.value)
    }

    @Test
    fun ac2_goForward_resetsFindState() {
        val s = createSession()
        s.emitPageFinished("https://a.com/")
        s.emitPageFinished("https://b.com/")
        s.goBack()
        s.emitPageFinished("https://a.com/")
        s.find("abc")
        s.emitFindResult(1, 5)
        s.goForward()
        assertEquals(FindState(), s.findState.value)
    }
}
