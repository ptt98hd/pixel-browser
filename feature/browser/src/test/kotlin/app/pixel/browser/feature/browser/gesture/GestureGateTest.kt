package app.pixel.browser.feature.browser.gesture

import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.feature.browser.BrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 008 AC6, AC17 (bảng điều kiện) – [GestureGate]. */
class GestureGateTest {
    private val normal = BrowserUiState(canGoBack = true, canGoForward = true, tabCount = 2)
    private val allOn = GestureAvailability(edgeBack = true, edgeForward = true, tabSwipe = true, pullToRefresh = true)
    private val allOff =
        GestureAvailability(edgeBack = false, edgeForward = false, tabSwipe = false, pullToRefresh = false)

    private fun gate(
        state: BrowserUiState = normal,
        overlayOpen: Boolean = false,
        systemGestureNav: Boolean = false,
    ) = GestureGate.of(state, overlayOpen, systemGestureNav)

    @Test
    fun ac6_threeButtonNav_enablesEverything() = assertEquals(allOn, gate())

    @Test
    fun ac6_gestureNav_disablesEdgeBackAndForward_only() =
        assertEquals(allOn.copy(edgeBack = false, edgeForward = false), gate(systemGestureNav = true))

    @Test
    fun ac6_cannotGoBack_disablesEdgeBack_only() =
        assertEquals(allOn.copy(edgeBack = false), gate(normal.copy(canGoBack = false)))

    @Test
    fun ac6_cannotGoForward_disablesEdgeForward_only() =
        assertEquals(allOn.copy(edgeForward = false), gate(normal.copy(canGoForward = false)))

    @Test
    fun ac6_singleTab_disablesTabSwipe_only() =
        assertEquals(allOn.copy(tabSwipe = false), gate(normal.copy(tabCount = 1)))

    @Test
    fun ac6_editingUrl_disablesEverything() = assertEquals(allOff, gate(normal.copy(isEditingUrl = true)))

    @Test
    fun ac6_fullscreen_disablesEverything() = assertEquals(allOff, gate(normal.copy(isFullscreen = true)))

    @Test
    fun ac6_pendingRequest_disablesEverything() {
        val request = EngineRequest.PopupBlocked(id = 1, origin = "https://a.com")
        assertEquals(allOff, gate(normal.copy(pendingRequest = request)))
    }

    @Test
    fun ac6_overlayOpen_disablesEverything() = assertEquals(allOff, gate(overlayOpen = true))

    @Test
    fun ac6_home_disablesPullToRefresh_only() =
        assertEquals(allOn.copy(pullToRefresh = false), gate(normal.copy(isHome = true)))

    @Test
    fun ac6_overlayOpen_beatsGestureNavDifference() =
        assertEquals(allOff, gate(overlayOpen = true, systemGestureNav = true))
}
