package app.pixel.browser.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixel.browser.core.history.HistoryClearer
import app.pixel.browser.core.history.HistoryDay
import app.pixel.browser.core.history.HistoryRange
import app.pixel.browser.core.history.HistoryStore
import app.pixel.browser.core.history.groupByDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class HistoryUiState(
    val query: String = "",
    val days: List<HistoryDay> = emptyList(),
    val canLoadMore: Boolean = false,
    val isEmpty: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val store: HistoryStore,
    private val clearer: HistoryClearer,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private data class Params(
        val query: String,
        val limit: Int,
    )

    private val rawQuery = MutableStateFlow("")
    private val params = MutableStateFlow(Params("", PAGE_SIZE))

    init {
        // Đổi truy vấn → về trang đầu; debounce chỉ cho chữ không rỗng (xóa chữ phải hiện lại danh sách ngay).
        viewModelScope.launch {
            rawQuery
                .map { it.trim().take(MAX_QUERY) }
                .distinctUntilChanged()
                .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MS }
                .collect { q -> params.update { if (it.query == q) it else Params(q, PAGE_SIZE) } }
        }
    }

    val state: StateFlow<HistoryUiState> =
        params
            .flatMapLatest { p ->
                store
                    .entries(p.query, p.limit)
                    .map { entries ->
                        HistoryUiState(
                            query = p.query,
                            days = groupByDay(entries, zone),
                            canLoadMore = entries.size >= p.limit,
                            isEmpty = entries.isEmpty(),
                        )
                    }.catch { emit(HistoryUiState(query = p.query, isEmpty = true)) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    fun onQueryChange(q: String) {
        rawQuery.value = q
    }

    fun onLoadMore() {
        params.update { it.copy(limit = it.limit + PAGE_SIZE) }
    }

    fun onDelete(id: Long) {
        launchSafely { store.delete(id) }
    }

    fun onClear(range: HistoryRange) {
        launchSafely { clearer.clear(range) }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Lỗi lưu trữ không được làm sập app; danh sách giữ nguyên.
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val DEBOUNCE_MS = 300L
        const val MAX_QUERY = 256
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
