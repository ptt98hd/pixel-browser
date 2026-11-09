package app.pixel.browser.feature.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.AnnotatedString
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.Shortcut

fun SemanticsNodeInteraction.textValue(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.Text) { emptyList() }
        .joinToString(" ") { it.text }

fun SemanticsNodeInteraction.editableText(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.EditableText) { AnnotatedString("") }
        .text

fun SemanticsNodeInteraction.contentDescriptions(): List<String> =
    fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }

fun SemanticsNodeInteraction.customActionLabels(): List<String> =
    fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

fun SemanticsNodeInteraction.invokeCustomAction(label: String): Boolean =
    fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .first { it.label == label }
        .action()

fun hasTestTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }

/** Số shortcut (không tính nút thêm) đang có trong cây semantics. */
fun SemanticsNodeInteractionsProvider.shortcutCount(): Int =
    onAllNodes(hasTestTagPrefix("home_shortcut_"))
        .fetchSemanticsNodes()
        .count { it.config[SemanticsProperties.TestTag] != HomeTestTags.SHORTCUT_ADD }

fun shortcuts(n: Int): List<Shortcut> = List(n) { Shortcut("s$it", "Site $it", "https://s$it.example/") }

fun configOf(n: Int): HomeConfig = HomeConfig(shortcuts = shortcuts(n))
