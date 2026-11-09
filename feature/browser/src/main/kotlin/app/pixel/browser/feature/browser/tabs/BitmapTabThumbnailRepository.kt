package app.pixel.browser.feature.browser.tabs

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import app.pixel.browser.core.engine.EngineSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [TabThumbnailRepository] dùng `Bitmap` (spec 009 AC10–AC11): bộ nhớ LRU + file `<dir>/<tabId>.webp`.
 *
 * Chụp chạy trên thread gọi (main); mã hóa/ghi/xóa file chạy tuần tự trên [io]. Tab ẩn danh (`persist = false`)
 * chỉ nằm trong bộ nhớ, không bao giờ ghi đĩa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitmapTabThumbnailRepository(
    private val dir: File,
    io: CoroutineDispatcher,
    maxBytes: Long = DEFAULT_MAX_BYTES,
) : TabThumbnailRepository {
    // Một luồng tuần tự cho mọi thao tác file để ghi/xóa không đảo thứ tự.
    private val io: CoroutineDispatcher = io.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + this.io)
    private val cache = ThumbnailCache<String, Bitmap>(maxBytes) { it.byteCount }
    private val version = mutableIntStateOf(0)

    override fun capture(
        tabId: String,
        session: EngineSession,
        persist: Boolean,
    ) {
        val view = session.view ?: return
        val size = thumbnailSize(view.width, view.height) ?: return
        val bitmap =
            try {
                createBitmap(size.width, size.height, Bitmap.Config.RGB_565)
            } catch (_: OutOfMemoryError) {
                return
            }
        val scale = size.width.toFloat() / view.width
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        view.draw(canvas)
        synchronized(cache) { cache.put(tabId, bitmap) }
        version.intValue++
        if (persist) scope.launch { write(tabId, bitmap) }
    }

    override fun remove(tabId: String) {
        // TODO(B06): khi thoát ẩn danh, xóa bitmap của các tab ẩn danh đã đóng khỏi bộ nhớ (hiện giữ để undo).
        scope.launch { fileOf(tabId)?.delete() }
    }

    override fun retainOnly(tabIds: Set<String>) {
        scope.launch {
            dir.listFiles().orEmpty().forEach { f ->
                if (f.name.endsWith(TMP_EXT)) {
                    f.delete()
                } else if (f.name.endsWith(EXT) && f.name.removeSuffix(EXT) !in tabIds) {
                    f.delete()
                }
            }
        }
    }

    override fun clearDisk() {
        scope.launch {
            dir.listFiles().orEmpty().forEach { f -> if (f.name.endsWith(EXT) || f.name.endsWith(TMP_EXT)) f.delete() }
        }
    }

    /** Bộ nhớ trước, miss thì đọc đĩa trên `io`. */
    @Composable
    fun thumbnail(tabId: String): ImageBitmap? {
        val v = version.intValue
        val inMemory = remember(tabId, v) { synchronized(cache) { cache[tabId] }?.asImageBitmap() }
        if (inMemory != null) return inMemory
        return produceState<ImageBitmap?>(null, tabId, v) {
            value = withContext(io) { load(tabId) }?.asImageBitmap()
        }.value
    }

    /** `TRIM_MEMORY_BACKGROUND` trở lên xóa bộ nhớ; đĩa giữ. */
    fun trim(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) return
        synchronized(cache) { cache.clear() }
        version.intValue++
    }

    /** null nếu [tabId] có thể thoát khỏi [dir] (`/`, `\\`, `..`) hoặc rỗng. */
    private fun fileOf(tabId: String): File? =
        if (tabId.isEmpty() || '/' in tabId || '\\' in tabId || ".." in tabId) null else File(dir, "$tabId$EXT")

    private fun write(
        tabId: String,
        bitmap: Bitmap,
    ) {
        val target = fileOf(tabId) ?: return
        val tmp = File(dir, "${target.name}.tmp")
        try {
            dir.mkdirs()
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, it) }
            if (!tmp.renameTo(target)) tmp.delete()
        } catch (_: IOException) {
            // Cache có thể bị hệ thống xóa/đầy: bỏ qua, thẻ dùng placeholder.
            tmp.delete()
        }
    }

    private fun load(tabId: String): Bitmap? {
        synchronized(cache) { cache[tabId] }?.let { return it }
        val file = fileOf(tabId) ?: return null
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val bitmap = BitmapFactory.decodeFile(file.path, options)
        if (bitmap == null) {
            file.delete()
            return null
        }
        synchronized(cache) { cache.put(tabId, bitmap) }
        return bitmap
    }

    private companion object {
        const val EXT = ".webp"
        const val TMP_EXT = ".webp.tmp"
        const val QUALITY = 70
        const val DEFAULT_MAX_BYTES = 24L shl 20
    }
}
