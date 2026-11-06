package app.pixel.browser.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeDefaults
import app.pixel.browser.core.data.home.Shortcut
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 010 – AC19 (trang chủ) và AC20 (thêm/sửa/xóa shortcut). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomePageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private var searchClicks = 0

    private fun show(config: HomeConfig?) {
        val actions = HomeActions(onOpenUrl = { opened += it }, onSearchClick = { searchClicks++ })
        rule.setContent { MaterialTheme { HomePage(config, actions, Modifier.fillMaxSize()) } }
        rule.waitForIdle()
    }

    private fun top(tag: String): Dp = rule.onNodeWithTag(tag).getBoundsInRoot().top

    private fun left(tag: String): Dp = rule.onNodeWithTag(tag).getBoundsInRoot().left

    // --- AC19 ---

    @Test
    fun ac19_defaults_showSearchAllShortcutsInOrder_andAddTile() {
        show(HomeConfig(shortcuts = HomeDefaults.shortcuts))
        rule.onNodeWithTag(HomeTestTags.HOME_PAGE).assertExists()
        rule.onNodeWithTag(HomeTestTags.HOME_SEARCH).assertIsDisplayed()
        val tags = HomeDefaults.shortcuts.map { HomeTestTags.shortcut(it.id) }
        tags.forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        assertEquals(4, rule.shortcutCount())
        val lefts = tags.map(::left)
        assertEquals(lefts.sorted(), lefts)
        assertEquals(4, lefts.distinct().size)
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).assertExists()
    }

    @Test
    fun ac19_compactWidth_hasFourColumns() {
        show(configOf(8))
        val tags = (0 until 8).map { HomeTestTags.shortcut("s$it") }
        val firstRow = top(tags[0])
        assertTrue((0 until 4).all { top(tags[it]) == firstRow })
        assertTrue(top(tags[4]) > firstRow)
        assertEquals(left(tags[0]), left(tags[4]))
    }

    @Test
    @Config(qualifiers = "w600dp-h960dp")
    fun ac19_mediumWidth_hasSixColumns() {
        show(configOf(8))
        val tags = (0 until 8).map { HomeTestTags.shortcut("s$it") }
        val firstRow = top(tags[0])
        assertTrue((0 until 6).all { top(tags[it]) == firstRow })
        assertTrue(top(tags[6]) > firstRow)
        assertEquals(left(tags[0]), left(tags[6]))
    }

    @Test
    fun ac19_loadingConfig_showsPageWithoutShortcutsOrAddTile() {
        show(null)
        rule.onNodeWithTag(HomeTestTags.HOME_PAGE).assertExists()
        rule.onNodeWithTag(HomeTestTags.HOME_SEARCH).assertExists()
        assertEquals(0, rule.shortcutCount())
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).assertDoesNotExist()
    }

    @Test
    fun ac19_twentyShortcuts_hideAddTile() {
        show(configOf(HomeDefaults.MAX_SHORTCUTS))
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).assertDoesNotExist()
    }

    @Test
    fun ac19_nineteenShortcuts_showAddTile() {
        show(configOf(19))
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).assertExists()
    }

    @Test
    fun ac19_tapShortcut_opensItsUrl() {
        show(HomeConfig(shortcuts = HomeDefaults.shortcuts))
        val gh = HomeDefaults.shortcuts[2]
        rule.onNodeWithTag(HomeTestTags.shortcut(gh.id)).performClick()
        assertEquals(listOf(gh.url), opened)
    }

    @Test
    fun ac19_tapSearch_callsOnSearchClick() {
        show(HomeConfig(shortcuts = HomeDefaults.shortcuts))
        rule.onNodeWithTag(HomeTestTags.HOME_SEARCH).performClick()
        assertEquals(1, searchClicks)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun ac19_eachShortcut_hasTitleAsContentDescription() {
        show(HomeConfig(shortcuts = HomeDefaults.shortcuts))
        HomeDefaults.shortcuts.forEach {
            val cds = rule.onNodeWithTag(HomeTestTags.shortcut(it.id)).contentDescriptions()
            assertTrue("${it.title} in $cds", it.title in cds)
        }
    }

    // --- AC20 (HomeViewModel thật + store giả) ---

    private lateinit var store: FakeHomeConfigStore
    private lateinit var vm: HomeViewModel

    private val sa = Shortcut("a", "Alpha", "https://a.example/x")
    private val sb = Shortcut("b", "Beta", "https://b.example/x")

    private fun showRoute(stored: HomeConfig = HomeConfig(shortcuts = listOf(sa, sb))) {
        store = FakeHomeConfigStore(stored)
        vm = HomeViewModel(store, FakeImageImporter(), Dispatchers.Unconfined)
        rule.setContent {
            MaterialTheme { HomeRoute(vm, onOpenUrl = { opened += it }, onSearchClick = { searchClicks++ }) }
        }
        rule.waitForIdle()
    }

    private fun currentIds() =
        vm.config.value!!
            .shortcuts
            .map { it.id }

    @Test
    fun ac20_addShortcut_appendsAtEnd_andClosesDialog() {
        showRoute()
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).performClick()
        rule.onNodeWithTag(HomeTestTags.DIALOG).assertExists()
        rule.onNodeWithTag(HomeTestTags.DIALOG_URL).performTextInput("example.com")
        rule.onNodeWithTag(HomeTestTags.DIALOG_SAVE).performClick()
        rule.waitForIdle()
        val last =
            vm.config.value!!
                .shortcuts
                .last()
        assertEquals(
            3,
            vm.config.value!!
                .shortcuts.size,
        )
        assertEquals("https://example.com", last.url)
        rule.onNodeWithTag(HomeTestTags.shortcut(last.id)).assertExists()
        rule.onNodeWithTag(HomeTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac20_saveDisabled_whileUrlEmpty() {
        showRoute()
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).performClick()
        rule.onNodeWithTag(HomeTestTags.DIALOG_SAVE).assertIsNotEnabled()
        rule.onNodeWithTag(HomeTestTags.DIALOG_TITLE).performTextInput("Only title")
        rule.onNodeWithTag(HomeTestTags.DIALOG_SAVE).assertIsNotEnabled()
        rule.onNodeWithTag(HomeTestTags.DIALOG_URL).performTextInput("e")
        rule.onNodeWithTag(HomeTestTags.DIALOG_SAVE).assertIsEnabled()
    }

    @Test
    fun ac20_invalidAndDuplicateUrl_showDifferentErrors_dialogStaysOpen() {
        showRoute()
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).performClick()
        rule.onNodeWithTag(HomeTestTags.DIALOG_ERROR).assertDoesNotExist()

        rule.onNodeWithTag(HomeTestTags.DIALOG_URL).performTextInput("hello world")
        rule.onNodeWithTag(HomeTestTags.DIALOG_SAVE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.DIALOG).assertExists()
        val invalidText = rule.onNodeWithTag(HomeTestTags.DIALOG_ERROR, useUnmergedTree = true).textValue()

        rule.onNodeWithTag(HomeTestTags.DIALOG_URL).performTextReplacement(sa.url)
        rule.onNodeWithTag(HomeTestTags.DIALOG_SAVE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.DIALOG).assertExists()
        val duplicateText = rule.onNodeWithTag(HomeTestTags.DIALOG_ERROR, useUnmergedTree = true).textValue()

        assertTrue(invalidText.isNotBlank())
        assertTrue(duplicateText.isNotBlank())
        assertNotEquals(invalidText, duplicateText)
        assertEquals(listOf("a", "b"), currentIds())
    }

    @Test
    fun ac20_cancel_closesDialogWithoutChange() {
        showRoute()
        rule.onNodeWithTag(HomeTestTags.SHORTCUT_ADD).performClick()
        rule.onNodeWithTag(HomeTestTags.DIALOG_URL).performTextInput("example.com")
        rule.onNodeWithTag(HomeTestTags.DIALOG_CANCEL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.DIALOG).assertDoesNotExist()
        assertEquals(listOf("a", "b"), currentIds())
    }

    @Test
    fun ac20_longPress_showsMenu_removeDeletesShortcut() {
        showRoute()
        rule.onNodeWithTag(HomeTestTags.shortcut("a")).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.MENU_EDIT).assertExists()
        rule.onNodeWithTag(HomeTestTags.MENU_REMOVE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.shortcut("a")).assertDoesNotExist()
        assertEquals(listOf("b"), currentIds())
        assertTrue(opened.isEmpty())
    }

    @Test
    fun ac20_edit_prefillsDialog_andUpdatesInPlace() {
        showRoute()
        rule.onNodeWithTag(HomeTestTags.shortcut("a")).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.MENU_EDIT).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.DIALOG).assertExists()
        assertEquals("Alpha", rule.onNodeWithTag(HomeTestTags.DIALOG_TITLE).editableText())
        assertEquals("https://a.example/x", rule.onNodeWithTag(HomeTestTags.DIALOG_URL).editableText())

        rule.onNodeWithTag(HomeTestTags.DIALOG_TITLE).performTextReplacement("Alpha 2")
        rule.onNodeWithTag(HomeTestTags.DIALOG_SAVE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.DIALOG).assertDoesNotExist()
        assertEquals(listOf("a", "b"), currentIds())
        assertEquals(Shortcut("a", "Alpha 2", "https://a.example/x"), vm.config.value!!.shortcuts[0])
        assertTrue(
            "Alpha 2" in rule.onNodeWithTag(HomeTestTags.shortcut("a")).contentDescriptions(),
        )
    }

    @Test
    fun ac20_routeTapShortcut_opensUrl() {
        showRoute()
        rule.onNodeWithTag(HomeTestTags.shortcut("b")).performClick()
        assertEquals(listOf(sb.url), opened)
    }
}
