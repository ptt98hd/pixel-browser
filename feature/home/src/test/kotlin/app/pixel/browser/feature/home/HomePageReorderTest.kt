package app.pixel.browser.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.Shortcut
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 010 – AC21: sắp xếp shortcut bằng long-press + kéo, và bằng custom accessibility action. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "vi-w411dp-h891dp")
class HomePageReorderTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val sa = Shortcut("a", "A", "https://a.example/x")
    private val sb = Shortcut("b", "B", "https://b.example/x")
    private val sc = Shortcut("c", "C", "https://c.example/x")
    private val sd = Shortcut("d", "D", "https://d.example/x")

    private lateinit var store: FakeHomeConfigStore
    private lateinit var vm: HomeViewModel
    private val opened = mutableListOf<String>()

    private fun showRoute() {
        store = FakeHomeConfigStore(HomeConfig(shortcuts = listOf(sa, sb, sc, sd)))
        vm = HomeViewModel(store, FakeImageImporter(), Dispatchers.Unconfined)
        rule.setContent { MaterialTheme { HomeRoute(vm, onOpenUrl = { opened += it }, onSearchClick = {}) } }
        rule.waitForIdle()
    }

    private fun ids() =
        vm.config.value!!
            .shortcuts
            .map { it.id }

    private fun centerInRoot(id: String): Offset {
        val b = rule.onNodeWithTag(HomeTestTags.shortcut(id)).fetchSemanticsNode().boundsInRoot
        return b.center
    }

    @Test
    fun ac21_longPressAndDragOntoC_movesAToCPosition_withoutMenu() {
        showRoute()
        val delta = centerInRoot("c") - centerInRoot("a")
        rule.onNodeWithTag(HomeTestTags.shortcut("a")).performTouchInput {
            down(center)
            advanceEventTime(600)
            val steps = 10
            repeat(steps) { moveBy(delta / steps.toFloat(), delayMillis = 16) }
            advanceEventTime(100)
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("b", "c", "a", "d"), ids())
        rule.onNodeWithTag(HomeTestTags.MENU_EDIT).assertDoesNotExist()
        rule.onNodeWithTag(HomeTestTags.MENU_REMOVE).assertDoesNotExist()
        assertTrue(opened.isEmpty())
        assertEquals(
            listOf("b", "c", "a", "d"),
            store.writes
                .last()
                .shortcuts
                .map { it.id },
        )
    }

    @Test
    fun ac21_accessibilityActions_presentExceptAtEdges() {
        showRoute()
        val left = "Di chuyển sang trái"
        val right = "Di chuyển sang phải"
        val first = rule.onNodeWithTag(HomeTestTags.shortcut("a")).customActionLabels()
        val middle = rule.onNodeWithTag(HomeTestTags.shortcut("b")).customActionLabels()
        val last = rule.onNodeWithTag(HomeTestTags.shortcut("d")).customActionLabels()
        assertFalse(left in first)
        assertTrue(right in first)
        assertTrue(left in middle && right in middle)
        assertTrue(left in last)
        assertFalse(right in last)
    }

    @Test
    fun ac21_moveRightAction_swapsWithNext() {
        showRoute()
        assertTrue(rule.onNodeWithTag(HomeTestTags.shortcut("b")).invokeCustomAction("Di chuyển sang phải"))
        rule.waitForIdle()
        assertEquals(listOf("a", "c", "b", "d"), ids())
        assertEquals(1, store.writes.size)
    }

    @Test
    fun ac21_moveLeftAction_swapsWithPrevious() {
        showRoute()
        assertTrue(rule.onNodeWithTag(HomeTestTags.shortcut("c")).invokeCustomAction("Di chuyển sang trái"))
        rule.waitForIdle()
        assertEquals(listOf("a", "c", "b", "d"), ids())
    }
}
