package app.pixel.browser.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixel.browser.core.data.home.HomeBackground
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeConfigEditor
import app.pixel.browser.core.data.home.HomeConfigStore
import app.pixel.browser.core.data.home.HomeDefaults
import app.pixel.browser.core.data.home.ShortcutEdit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Thông báo một lần của trang chủ (spec 010). */
sealed interface HomeMessage {
    /** Không nhập được ảnh nền (AC13). */
    data object ImageFailed : HomeMessage
}

/**
 * State holder của trang chủ (spec 010 AC11–AC13). Dùng chung cho mọi tab (scope Activity).
 *
 * Mọi thao tác chạy trên main thread: [config] cập nhật đồng bộ, rồi lần ghi [store] (và xóa ảnh cũ) được xếp
 * hàng FIFO và chạy tuần tự trên [io] — lần ghi sau cùng thắng. Thao tác khi [config] còn null bị bỏ qua.
 *
 * Hàng đợi ghi chạy trong [writeScope] (mặc định `viewModelScope`; app truyền scope cấp process): khi ViewModel
 * bị hủy, hàng đợi được đóng và các thao tác đã xếp vẫn chạy hết, nên chỉnh sửa gần nhất không bị mất.
 */
class HomeViewModel(
    private val store: HomeConfigStore,
    private val importer: BackgroundImageImporter,
    private val io: CoroutineDispatcher,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    writeScope: CoroutineScope? = null,
) : ViewModel() {
    private val _config = MutableStateFlow<HomeConfig?>(null)

    /** null = đang đọc. */
    val config: StateFlow<HomeConfig?> = _config.asStateFlow()

    private val _message = MutableStateFlow<HomeMessage?>(null)
    val message: StateFlow<HomeMessage?> = _message.asStateFlow()

    /** Hàng đợi I/O (ghi config, xóa ảnh) chạy tuần tự trên [io]. */
    private val ioQueue = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        (writeScope ?: viewModelScope).launch(io) {
            // Một thao tác lỗi (I/O) không được làm chết cả hàng đợi.
            for (op in ioQueue) runCatching(op)
        }
        viewModelScope.launch {
            _config.value = withContext(io) { load() }
        }
    }

    fun addShortcut(
        title: String,
        url: String,
    ): ShortcutEdit {
        val c = _config.value ?: return ShortcutEdit.Invalid
        return HomeConfigEditor.add(c, title, url, newId).also { if (it is ShortcutEdit.Ok) commit(it.config) }
    }

    fun updateShortcut(
        id: String,
        title: String,
        url: String,
    ): ShortcutEdit {
        val c = _config.value ?: return ShortcutEdit.Invalid
        return HomeConfigEditor.update(c, id, title, url).also { if (it is ShortcutEdit.Ok) commit(it.config) }
    }

    fun removeShortcut(id: String) {
        val c = _config.value ?: return
        commit(HomeConfigEditor.remove(c, id))
    }

    fun moveShortcut(
        from: Int,
        to: Int,
    ) {
        val c = _config.value ?: return
        commit(HomeConfigEditor.move(c, from, to))
    }

    /** Đổi nền; file ảnh nền cũ (nếu không còn dùng) bị xóa sau khi config mới được ghi. */
    fun setBackground(bg: HomeBackground) {
        val c = _config.value ?: return
        commit(HomeConfigEditor.setBackground(c, bg))
    }

    /** Đổi độ tối của ảnh nền (clamp 0..0.8); không làm gì nếu nền không phải ảnh. */
    fun setDim(dim: Float) {
        val image = _config.value?.background as? HomeBackground.Image ?: return
        setBackground(image.copy(dim = dim))
    }

    /** null = người dùng hủy Photo Picker. */
    fun onImagePicked(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val fileName = withContext(io) { importer.import(uri) }
            val c = _config.value
            when {
                fileName == null -> {
                    _message.value = HomeMessage.ImageFailed
                }

                c == null -> {
                    enqueue { importer.delete(fileName) }
                }

                else -> {
                    val dim = (c.background as? HomeBackground.Image)?.dim ?: HomeDefaults.DEFAULT_DIM
                    setBackground(HomeBackground.Image(fileName, dim))
                }
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    override fun onCleared() {
        ioQueue.close()
    }

    /** Đọc store; chưa có → mặc định (chưa ghi). Ảnh nền mất file → [HomeBackground.Dynamic]. */
    private fun load(): HomeConfig {
        val stored = store.read() ?: return HomeConfig(shortcuts = HomeDefaults.shortcuts)
        val bg = stored.background
        return if (bg is HomeBackground.Image && !importer.exists(bg.fileName)) {
            stored.copy(background = HomeBackground.Dynamic)
        } else {
            stored
        }
    }

    private fun commit(next: HomeConfig) {
        val previous = _config.value ?: return
        if (next == previous) return
        _config.value = next
        enqueue { store.write(next) }
        val oldImage = previous.background as? HomeBackground.Image
        val newImage = next.background as? HomeBackground.Image
        if (oldImage != null && oldImage.fileName != newImage?.fileName) {
            enqueue { importer.delete(oldImage.fileName) }
        }
    }

    private fun enqueue(op: () -> Unit) {
        ioQueue.trySend(op)
    }
}
