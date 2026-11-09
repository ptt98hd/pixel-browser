package app.pixel.browser.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 014 AC11 – danh sách mục của màn hình Cài đặt. */
@RunWith(RobolectricTestRunner::class)
// Màn hình mặc định của Robolectric (320x470dp) không chứa đủ 7 hàng ≥ 48dp + 5 tiêu đề mục.
@Config(qualifiers = "w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private var closed = 0

    private fun show(registry: SettingsRegistry = defaultLikeRegistry()) {
        rule.setContent {
            MaterialTheme { SettingsScreen(registry, onOpenPage = { opened += it }, onClose = { closed++ }) }
        }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().top

    @Test
    fun ac11_hasScreenTitleAndBack() {
        show()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
        rule.onNodeWithTag(SettingsTestTags.TITLE).assertExists()
        rule.onNodeWithTag(SettingsTestTags.BACK).assertExists()
    }

    @Test
    fun ac11_sectionsAppearInOrder() {
        show()
        val ids = listOf("search", "appearance", "privacy", "site", "about")
        val tops = ids.map { top(SettingsTestTags.section(it)) }
        assertEquals(tops.sorted(), tops)
        assertEquals(5, tops.distinct().size)
    }

    @Test
    fun ac11_realItemsExist_searchItemShowsCurrentEngineAsSummary() {
        show(defaultLikeRegistry(searchSummary = "DuckDuckGo"))
        rule.onNodeWithTag(SettingsTestTags.item("search_engine")).assertExists()
        rule.onNodeWithTag(SettingsTestTags.item("theme")).assertExists()
        rule.onNodeWithTag(SettingsTestTags.item("about")).assertExists()
        assertTrue("DuckDuckGo" in rule.onNodeWithTag(SettingsTestTags.item("search_engine")).textValue())
    }

    @Test
    fun ac11_itemReadsTitleAndSummaryAsOneNode_forTalkBack() {
        show()
        val node = rule.onNodeWithTag(SettingsTestTags.item("search_engine")).fetchSemanticsNode()
        assertTrue(node.config.isMergingSemanticsOfDescendants)
        val text = rule.onNodeWithTag(SettingsTestTags.item("search_engine")).textValue()
        assertTrue("Title search_engine" in text && "DuckDuckGo" in text)
    }

    @Test
    fun ac11_item_isButtonRole_andAtLeast48dpHigh() {
        show()
        val tag = SettingsTestTags.item("theme")
        assertEquals(Role.Button, rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Role])
        rule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun ac11_placeholders_existAsDisabledRows_withoutClickAction() {
        show()
        for (id in listOf("clear_data", "safe_browsing", "site_settings", "desktop_site")) {
            val tag = SettingsTestTags.placeholder(id)
            rule.onNodeWithTag(tag).assertExists().assertIsNotEnabled()
            assertFalse(
                "placeholder $id must not be clickable",
                SemanticsActions.OnClick in rule.onNodeWithTag(tag).fetchSemanticsNode().config,
            )
            rule.onNodeWithTag(SettingsTestTags.item(id)).assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = "vi")
    fun ac11_placeholder_showsSapCo_inVietnamese() {
        show()
        assertTrue("Sắp có" in rule.onNodeWithTag(SettingsTestTags.placeholder("clear_data")).textValue())
    }

    @Test
    fun ac11_tapItem_callsOnOpenPageWithId() {
        show()
        rule.onNodeWithTag(SettingsTestTags.item("search_engine")).performClick()
        rule.onNodeWithTag(SettingsTestTags.item("about")).performClick()
        assertEquals(listOf("search_engine", "about"), opened)
    }

    @Test
    fun ac11_tapPlaceholder_doesNotOpenAnything() {
        show()
        rule.onNodeWithTag(SettingsTestTags.placeholder("clear_data")).performClick()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun ac11_tapBack_callsOnClose() {
        show()
        rule.onNodeWithTag(SettingsTestTags.BACK).performClick()
        assertEquals(1, closed)
    }
}
