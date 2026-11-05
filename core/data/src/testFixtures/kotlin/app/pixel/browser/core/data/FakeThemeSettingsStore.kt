package app.pixel.browser.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * [ThemeSettingsStore] trong bộ nhớ cho test. Ghi lại mỗi giá trị sau [update] vào [updates].
 * Nếu [readFailure] khác null thì [settings] ném lỗi đó khi được collect (mô phỏng lỗi đọc đĩa).
 */
class FakeThemeSettingsStore(
    initial: ThemeSettings = ThemeSettings(),
    var readFailure: IOException? = null,
) : ThemeSettingsStore {
    private val state = MutableStateFlow(initial)

    /** Giá trị hiện tại (không đi qua [readFailure]). */
    val current: ThemeSettings get() = state.value

    private val _updates = mutableListOf<ThemeSettings>()
    val updates: List<ThemeSettings> get() = synchronized(_updates) { _updates.toList() }

    override val settings: Flow<ThemeSettings>
        get() =
            flow {
                readFailure?.let { throw it }
                emitAll(state)
            }

    override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        val next = transform(state.value)
        state.value = next
        synchronized(_updates) { _updates += next }
    }
}
