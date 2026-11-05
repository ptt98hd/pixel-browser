package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.robolectric.shadows.ShadowDialog

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** Node khớp [matcher] nằm trong (hoặc chính là) node có tag [dialogTag]. */
fun ComposeRule.inDialog(
    dialogTag: String,
    matcher: SemanticsMatcher,
) = onNode(matcher and (hasTestTag(dialogTag) or hasAnyAncestor(hasTestTag(dialogTag))))

/** Back hệ thống tới cửa sổ trên cùng: dialog nếu đang hiện, nếu không thì activity. */
@Suppress("DEPRECATION")
fun ComposeRule.pressSystemBack() {
    runOnIdle {
        val dialog = ShadowDialog.getLatestDialog()
        when {
            dialog is ComponentDialog && dialog.isShowing -> dialog.onBackPressedDispatcher.onBackPressed()
            dialog != null && dialog.isShowing -> dialog.onBackPressed()
            else -> activity.onBackPressedDispatcher.onBackPressed()
        }
    }
    waitForIdle()
}
