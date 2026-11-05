package app.pixel.browser.feature.browser

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import org.junit.Assert.assertEquals

/** Toàn bộ Text (merged) của node, nối bằng dấu cách. */
fun SemanticsNodeInteraction.textValue(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.Text) { emptyList() }
        .joinToString(" ") { it.text }

fun SemanticsNodeInteraction.contentDescriptions(): List<String> =
    fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }

fun SemanticsNodeInteraction.progressFraction(): Float =
    fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].let {
        (it.current - it.range.start) / (it.range.endInclusive - it.range.start)
    }

fun SemanticsNodeInteraction.customActionLabels(): List<String> =
    fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

/** Thực thi custom accessibility action đầu tiên của node (long-press tương ứng, AC23). */
fun SemanticsNodeInteraction.invokeFirstCustomAction(): Boolean =
    fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .first()
        .action()

fun hasTestTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }

fun SemanticsNodeInteractionsProvider.tabItemCount(): Int =
    onAllNodes(hasTestTagPrefix("tab_item_")).fetchSemanticsNodes().size

fun SemanticsNodeInteraction.assertTextIs(expected: String): SemanticsNodeInteraction {
    assertEquals(expected, textValue())
    return this
}
