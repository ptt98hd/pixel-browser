package app.pixel.browser.core.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hợp đồng spec 003 (back/forward không reload, save/restore lịch sử) cho mọi [EngineSession]. */
abstract class HistoryNavigationContractTest {
    protected abstract fun createSession(): EngineSession

    /** Mô phỏng engine tải xong [url] (đã vào lịch sử). */
    protected abstract fun EngineSession.emitPageFinished(url: String)

    /** Cache mode mà engine sẽ dùng cho điều hướng gần nhất. */
    protected abstract fun EngineSession.observedCacheMode(): CacheMode

    private val a = "https://a.example/"
    private val b = "https://b.example/"
    private val c = "https://c.example/"

    private fun EngineSession.visit(url: String) {
        loadUrl(url)
        emitPageFinished(url)
    }

    @Test
    fun loadUrl_usesDefaultCacheMode() {
        val s = createSession()
        s.loadUrl(a)
        assertEquals(CacheMode.Default, s.observedCacheMode())
    }

    @Test
    fun goBack_usesCacheElseNetwork() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        s.goBack()
        assertEquals(CacheMode.CacheElseNetwork, s.observedCacheMode())
    }

    @Test
    fun goForward_usesCacheElseNetwork() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        s.goBack()
        s.emitPageFinished(a)
        s.goForward()
        assertEquals(CacheMode.CacheElseNetwork, s.observedCacheMode())
    }

    @Test
    fun loadUrl_afterHistoryNavigation_resetsToDefaultCacheMode() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        s.goBack()
        s.emitPageFinished(a)
        s.loadUrl(c)
        assertEquals(CacheMode.Default, s.observedCacheMode())
    }

    @Test
    fun saveState_onFreshSession_returnsNull() {
        assertNull(createSession().saveState())
    }

    @Test
    fun saveState_afterNavigation_returnsData() {
        val s = createSession()
        s.visit(a)
        val bytes = s.saveState()
        assertNotNull(bytes)
        assertTrue(bytes!!.isNotEmpty())
    }

    @Test
    fun saveAndRestore_restoresHistoryAndCurrentEntry() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        s.visit(c)
        s.goBack()
        s.emitPageFinished(b)
        val bytes = s.saveState()!!

        val restored = createSession()
        assertTrue(restored.restoreState(bytes))
        assertEquals(b, restored.state.value.url)
        assertTrue(restored.state.value.canGoBack)
        assertTrue(restored.state.value.canGoForward)

        restored.goForward()
        restored.emitPageFinished(c)
        assertEquals(c, restored.state.value.url)
        assertFalse(restored.state.value.canGoForward)
    }

    @Test
    fun restoreState_usesCacheElseNetwork() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        val bytes = s.saveState()!!

        val restored = createSession()
        restored.restoreState(bytes)
        assertEquals(CacheMode.CacheElseNetwork, restored.observedCacheMode())
    }

    @Test
    fun saveState_isStableAcrossRestore() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        val bytes = s.saveState()!!
        val restored = createSession()
        restored.restoreState(bytes)
        val again = createSession()
        assertTrue(again.restoreState(restored.saveState()!!))
        assertEquals(b, again.state.value.url)
        assertTrue(again.state.value.canGoBack)
    }

    @Test
    fun restoreState_withCorruptData_returnsFalseAndKeepsState() {
        val s = createSession()
        s.visit(a)
        val before = s.state.value
        assertFalse(s.restoreState(byteArrayOf(1, 2, 3, 4, 5)))
        assertEquals(before, s.state.value)
    }

    @Test
    fun restoreState_withEmptyData_returnsFalse() {
        assertFalse(createSession().restoreState(ByteArray(0)))
    }

    @Test
    fun afterClose_saveStateIsNull_andRestoreIsRejected() {
        val s = createSession()
        s.visit(a)
        val bytes = s.saveState()!!
        s.close()
        assertNull(s.saveState())
        val other = createSession()
        other.close()
        assertFalse(other.restoreState(bytes))
    }

    @Test
    fun savedBytes_areIndependentOfLaterNavigation() {
        val s = createSession()
        s.visit(a)
        val bytes = s.saveState()!!
        val copy = bytes.copyOf()
        s.visit(b)
        assertArrayEquals(copy, bytes)
        val restored = createSession()
        restored.restoreState(bytes)
        assertEquals(a, restored.state.value.url)
        assertFalse(restored.state.value.canGoBack)
    }

    @Test
    fun historyNavigation_afterPageFinished_resetsToDefaultCacheMode() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        s.goBack()
        s.emitPageFinished(a)
        assertEquals(CacheMode.Default, s.observedCacheMode())
    }

    @Test
    fun restoreState_afterPageFinished_resetsToDefaultCacheMode() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        val bytes = s.saveState()!!
        val restored = createSession()
        assertTrue(restored.restoreState(bytes))
        restored.emitPageFinished(b)
        assertEquals(CacheMode.Default, restored.observedCacheMode())
    }

    @Test
    fun restoreState_startsLoadingCurrentEntry_untilPageFinished() {
        val s = createSession()
        s.visit(a)
        s.visit(b)
        val bytes = s.saveState()!!
        val restored = createSession()
        assertTrue(restored.restoreState(bytes))
        assertTrue(restored.state.value.isLoading)
        restored.emitPageFinished(b)
        assertFalse(restored.state.value.isLoading)
        assertEquals(b, restored.state.value.url)
        assertTrue(restored.state.value.canGoBack)
    }
}
