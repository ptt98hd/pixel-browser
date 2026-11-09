package app.pixel.browser.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 014 AC12 – điều hướng trang bằng ngăn xếp id. */
@RunWith(RobolectricTestRunner::class)
class SettingsRouteTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var closed = 0
    private val urls = mutableListOf<String>()

    private val registry =
        defaultLikeRegistry { id ->
            { scope ->
                Text("content-$id")
                Text("go-other", Modifier.testTag("go_other_$id").clickable { scope.open("about") })
                Text("go-back", Modifier.testTag("go_back_$id").clickable { scope.navigateBack() })
                Text("go-url", Modifier.testTag("go_url_$id").clickable { scope.openUrl("https://example.org/x") })
            }
        }

    private fun show(startPageId: String? = null) {
        rule.setContent {
            MaterialTheme { SettingsRoute(registry, startPageId, onClose = { closed++ }, onOpenUrl = { urls += it }) }
        }
        rule.waitForIdle()
    }

    private fun systemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun openSearchPage() {
        rule.onNodeWithTag(SettingsTestTags.item("search_engine")).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac12_opensOnList() {
        show()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
    }

    @Test
    fun ac12_tapItem_showsPage_hidesList_andTitleIsPageTitle() {
        show()
        openSearchPage()
        rule.onNodeWithTag(SettingsTestTags.page("search_engine")).assertExists()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertDoesNotExist()
        assertEquals("Title search_engine", rule.onNodeWithTag(SettingsTestTags.TITLE).textValue())
    }

    @Test
    fun ac12_backButton_returnsToList() {
        show()
        openSearchPage()
        rule.onNodeWithTag(SettingsTestTags.BACK).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
        rule.onNodeWithTag(SettingsTestTags.page("search_engine")).assertDoesNotExist()
        assertEquals(0, closed)
    }

    @Test
    fun ac12_systemBack_onPage_returnsToList_notClose() {
        show()
        openSearchPage()
        systemBack()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
        assertEquals(0, closed)
    }

    @Test
    fun ac12_systemBack_onList_callsOnCloseOnce() {
        show()
        systemBack()
        assertEquals(1, closed)
    }

    @Test
    fun ac12_backButton_onList_callsOnClose() {
        show()
        rule.onNodeWithTag(SettingsTestTags.BACK).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun ac12_startPageId_opensThatPage_thenBackGoesToList() {
        show(startPageId = "about")
        rule.onNodeWithTag(SettingsTestTags.page("about")).assertExists()
        systemBack()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
        assertEquals(0, closed)
    }

    @Test
    fun ac12_unknownStartPageId_opensList() {
        show(startPageId = "nope")
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
    }

    @Test
    fun ac12_placeholderStartPageId_opensList() {
        show(startPageId = "clear_data")
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
        rule.onNodeWithTag(SettingsTestTags.page("clear_data")).assertDoesNotExist()
    }

    @Test
    fun ac12_pageOpensAnotherPage_stacks_andNavigateBackPopsOneLevel() {
        show()
        openSearchPage()
        rule.onNodeWithTag("go_other_search_engine").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTestTags.page("about")).assertExists()

        rule.onNodeWithTag("go_back_about").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTestTags.page("search_engine")).assertExists()

        rule.onNodeWithTag("go_back_search_engine").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
    }

    @Test
    fun ac12_openUrlFromPage_callsOnOpenUrl() {
        show()
        openSearchPage()
        rule.onNodeWithTag("go_url_search_engine").performClick()
        assertEquals(listOf("https://example.org/x"), urls)
    }

    @Test
    fun ac12_stack_survivesStateRestoration() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            MaterialTheme { SettingsRoute(registry, null, onClose = { closed++ }, onOpenUrl = { urls += it }) }
        }
        openSearchPage()
        rule.onNodeWithTag("go_other_search_engine").performClick()
        rule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag(SettingsTestTags.page("about")).assertExists()
        rule.onNodeWithTag("go_back_about").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTestTags.page("search_engine")).assertExists()
    }
}
