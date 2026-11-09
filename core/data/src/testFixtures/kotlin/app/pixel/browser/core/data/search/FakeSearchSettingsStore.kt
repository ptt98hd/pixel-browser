package app.pixel.browser.core.data.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * [SearchSettingsStore] trong bộ nhớ cho test. Ghi lại mỗi giá trị sau [update] vào [updates].
 * [readFailure] khác null: [settings] ném lỗi đó khi collect. [writeFailure] khác null: [update] ném lỗi đó.
 */
class FakeSearchSettingsStore(
    initial: SearchSettings = SearchSettings(),
    var readFailure: IOException? = null,
    var writeFailure: IOException? = null,
) : SearchSettingsStore {
    private val state = MutableStateFlow(initial)

    val current: SearchSettings get() = state.value

    private val _updates = mutableListOf<SearchSettings>()
    val updates: List<SearchSettings> get() = synchronized(_updates) { _updates.toList() }

    override val settings: Flow<SearchSettings>
        get() =
            flow {
                readFailure?.let { throw it }
                emitAll(state)
            }

    override suspend fun update(transform: (SearchSettings) -> SearchSettings) {
        writeFailure?.let { throw it }
        val next = transform(state.value)
        state.value = next
        synchronized(_updates) { _updates += next }
    }
}
