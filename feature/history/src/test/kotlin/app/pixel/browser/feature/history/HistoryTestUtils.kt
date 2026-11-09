package app.pixel.browser.feature.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import app.pixel.browser.core.history.HistoryDay
import app.pixel.browser.core.history.HistoryEntry
import app.pixel.browser.core.history.HistoryStore
import app.pixel.browser.core.history.InMemoryHistoryStore
import java.time.Instant
import java.time.ZoneId

fun SemanticsNodeInteraction.textValue(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.Text) { emptyList() }
        .joinToString(" ") { it.text }

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

/** Nhóm theo ngày độc lập với `groupByDay` của `:core:history` (để test UI không phụ thuộc cài đặt đó). */
fun daysOf(
    entries: List<HistoryEntry>,
    zone: ZoneId,
): List<HistoryDay> =
    entries
        .groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() }
        .map { (date, list) -> HistoryDay(date, list) }
        .sortedByDescending { it.date }

fun stateOf(
    entries: List<HistoryEntry>,
    query: String = "",
    canLoadMore: Boolean = false,
    zone: ZoneId = ZoneId.systemDefault(),
) = HistoryUiState(
    query = query,
    days = daysOf(entries, zone),
    canLoadMore = canLoadMore,
    isEmpty = entries.isEmpty(),
)

/** Ghi lại mọi truy vấn `entries` và số lần `delete` để kiểm tra ViewModel. */
class SpyHistoryStore(
    val delegate: InMemoryHistoryStore = InMemoryHistoryStore(),
) : HistoryStore by delegate {
    val entryQueries = mutableListOf<Pair<String, Int>>()
    var deleteCalls = mutableListOf<Long>()
    var deleteBetweenCalls = 0

    override fun entries(
        query: String,
        limit: Int,
    ) = delegate.entries(query, limit).also { entryQueries += query to limit }

    override suspend fun delete(id: Long) {
        deleteCalls += id
        delegate.delete(id)
    }

    override suspend fun deleteBetween(
        fromInclusive: Long,
        toInclusive: Long,
    ): Int {
        deleteBetweenCalls++
        return delegate.deleteBetween(fromInclusive, toInclusive)
    }
}
