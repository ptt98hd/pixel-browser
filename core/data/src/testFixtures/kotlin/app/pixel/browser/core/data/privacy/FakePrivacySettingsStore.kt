package app.pixel.browser.core.data.privacy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * [PrivacySettingsStore] trong bộ nhớ cho test. Ghi lại mỗi giá trị sau [update] vào [updates].
 * [readFailure] khác null: [settings] ném lỗi đó khi collect. [writeFailure] khác null: [update] ném lỗi đó.
 */
class FakePrivacySettingsStore(
    initial: PrivacySettings = PrivacySettings(),
    var readFailure: IOException? = null,
    var writeFailure: IOException? = null,
) : PrivacySettingsStore {
    private val state = MutableStateFlow(initial)

    val current: PrivacySettings get() = state.value

    private val _updates = mutableListOf<PrivacySettings>()
    val updates: List<PrivacySettings> get() = synchronized(_updates) { _updates.toList() }

    override val settings: Flow<PrivacySettings>
        get() =
            flow {
                readFailure?.let { throw it }
                emitAll(state)
            }

    override suspend fun update(transform: (PrivacySettings) -> PrivacySettings) {
        writeFailure?.let { throw it }
        val next = transform(state.value)
        state.value = next
        synchronized(_updates) { _updates += next }
    }
}
