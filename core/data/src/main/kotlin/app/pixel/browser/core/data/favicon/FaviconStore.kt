package app.pixel.browser.core.data.favicon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Kho favicon theo host (spec 012 AC22). */
interface FaviconStore {
    /** Lưu [pngBytes] (decode được; thu nhỏ còn <= 64x64) cho [host]; ảnh hỏng bị bỏ qua. */
    fun put(
        host: String,
        pngBytes: ByteArray,
    )

    fun get(host: String): ByteArray?

    /** Xóa mọi favicon của host không nằm trong [hosts]. */
    fun retainOnly(hosts: Set<String>)

    fun clear()
}

/** `<dir>/<sha1(host)>.png`, tối đa [maxFiles] file / [maxBytes] byte, LRU theo `lastModified`. */
class FileFaviconStore(
    private val dir: File,
    private val maxFiles: Int = 500,
    private val maxBytes: Long = 4L * 1024 * 1024,
) : FaviconStore {
    private val lock = Any()

    override fun put(
        host: String,
        pngBytes: ByteArray,
    ) {
        val encoded = normalize(pngBytes) ?: return
        synchronized(lock) {
            try {
                dir.mkdirs()
                val target = fileFor(host)
                val tmp = File(dir, "${target.name}.tmp")
                tmp.writeBytes(encoded)
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return
                }
                evict()
            } catch (_: java.io.IOException) {
                // Favicon chỉ là phần trang trí: lỗi ghi đĩa bị bỏ qua.
            }
        }
    }

    override fun get(host: String): ByteArray? =
        synchronized(lock) {
            val file = fileFor(host)
            try {
                file.readBytes().also { file.setLastModified(System.currentTimeMillis()) }
            } catch (_: java.io.IOException) {
                null
            }
        }

    override fun retainOnly(hosts: Set<String>) {
        synchronized(lock) {
            val keep = hosts.mapTo(HashSet()) { fileFor(it).name }
            dir
                .listFiles()
                .orEmpty()
                .filter { it.name !in keep }
                .forEach { it.delete() }
        }
    }

    override fun clear() {
        synchronized(lock) { dir.listFiles().orEmpty().forEach { it.delete() } }
    }

    private fun fileFor(host: String) = File(dir, "${sha1(host.lowercase())}.png")

    private fun evict() {
        // File tạm mồ côi (process chết giữa lúc ghi) không được tồn tại mãi.
        dir.listFiles { f -> f.name.endsWith(".tmp") }.orEmpty().forEach { it.delete() }
        val files =
            dir
                .listFiles { f ->
                    f.name.endsWith(".png")
                }.orEmpty()
                .sortedBy { it.lastModified() }
                .toMutableList()
        var total = files.sumOf { it.length() }
        while (files.isNotEmpty() && (files.size > maxFiles || total > maxBytes)) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }
    }

    /** Decode (có chặn ảnh quá lớn) rồi thu nhỏ còn <= [MAX_EDGE] và mã hóa lại PNG; null nếu hỏng. */
    private fun normalize(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w <= 0 || h <= 0 || w > MAX_SOURCE_EDGE || h > MAX_SOURCE_EDGE) return null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        val bitmap =
            if (longest <= MAX_EDGE) {
                decoded
            } else {
                val scale = MAX_EDGE.toFloat() / longest
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_EDGE = 64
        const val MAX_SOURCE_EDGE = 2048
    }
}
