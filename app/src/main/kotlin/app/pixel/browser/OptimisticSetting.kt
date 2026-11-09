package app.pixel.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Giá trị cài đặt trong bộ nhớ với cập nhật lạc quan (cùng mẫu `themeSettings`, spec 011/014): [update] đặt giá
 * trị ngay, ghi bền vững trên scope IO; giá trị store phát ra trong lúc còn ghi chờ bị bỏ qua (cũ hơn giá trị
 * lạc quan); `IOException` khi đọc/ghi không làm hỏng giá trị trong bộ nhớ.
 */
internal class OptimisticSetting<T : Any>(
    initial: T,
    private val onValueChanged: (T) -> Unit = {},
) {
    private val lock = Any()
    private var binding: Any? = null
    private var job: Job? = null
    private var pendingWrites = 0

    /** Đã có giá trị đầu từ store (hoặc đọc lỗi → dùng giá trị hiện tại); trước đó các [update] được xếp hàng. */
    private var loaded = false
    private val queued = ArrayList<() -> Unit>()
    private val _state = MutableStateFlow(initial)

    val state: StateFlow<T> = _state.asStateFlow()

    /** Collect [source] vào [state] trên [scope]; hủy lần gắn trước. `IOException` khi đọc → giữ giá trị hiện tại. */
    fun bind(
        scope: CoroutineScope,
        source: Flow<T>,
    ) {
        synchronized(lock) {
            job?.cancel()
            loaded = false
            val mine = Any()
            binding = mine
            job =
                scope.launch {
                    source
                        .catch { e ->
                            if (e !is IOException) throw e
                            synchronized(lock) { if (binding === mine) markLoaded() }
                        }.collect { value ->
                            synchronized(lock) {
                                if (binding === mine) {
                                    if (pendingWrites == 0) set(value)
                                    markLoaded()
                                }
                            }
                        }
                }
        }
    }

    /**
     * Áp [transform] ngay (nếu chưa nạp xong giá trị đã lưu thì xếp hàng, trả `false`); nếu giá trị đổi thì ghi bằng [write] trên [ioScope] (tuần tự) và trả `true`.
     * `IOException` khi ghi chỉ được báo qua [onWriteFailure].
     */
    fun update(
        ioScope: CoroutineScope,
        transform: (T) -> T,
        write: suspend (T) -> Unit,
        onWriteFailure: (IOException) -> Unit,
    ): Boolean {
        val next: T
        synchronized(lock) {
            if (!loaded) {
                // Chưa có giá trị đã lưu: tính từ mặc định có thể đè mất dữ liệu → xếp hàng tới khi nạp xong.
                queued += { update(ioScope, transform, write, onWriteFailure) }
                return false
            }
            next = transform(_state.value)
            if (next == _state.value) return false
            pendingWrites++
            set(next)
        }
        ioScope.launch {
            try {
                write(next)
            } catch (e: IOException) {
                onWriteFailure(e)
            } finally {
                synchronized(lock) { pendingWrites-- }
            }
        }
        return true
    }

    private fun markLoaded() {
        if (loaded) return
        loaded = true
        val pending = queued.toList()
        queued.clear()
        pending.forEach { it() }
    }

    private fun set(value: T) {
        _state.value = value
        onValueChanged(value)
    }
}
