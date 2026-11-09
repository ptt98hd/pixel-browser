package app.pixel.browser.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import app.pixel.browser.core.data.home.HomeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Nhập ảnh nền vào `filesDir/home` (spec 010 AC13–AC14). */
interface BackgroundImageImporter {
    /** Copy + thu nhỏ ảnh tại [uri]; trả tên file (`bg-<số>.jpg`) hoặc null nếu lỗi. */
    suspend fun import(uri: String): String?

    fun exists(fileName: String): Boolean

    fun delete(fileName: String)
}

/**
 * [BackgroundImageImporter] dùng `ContentResolver` + `ImageDecoder` (spec 010 AC14). Chạy I/O và decode trên
 * `Dispatchers.IO`. Ảnh được copy tạm (tối đa [MAX_SOURCE_BYTES]) rồi decode thu nhỏ về cạnh dài ≤ [MAX_EDGE]
 * và ghi JPEG; tên file không lấy từ nguồn. Mọi lỗi (kể cả hết bộ nhớ) → null, không để lại file rác.
 */
class AndroidBackgroundImageImporter(
    private val context: Context,
) : BackgroundImageImporter {
    private val homeDir: File get() = HomeFiles.dir(context.filesDir)

    override suspend fun import(uri: String): String? = withContext(Dispatchers.IO) { importBlocking(uri) }

    override fun exists(fileName: String): Boolean = isSafeName(fileName) && File(homeDir, fileName).isFile

    override fun delete(fileName: String) {
        if (isSafeName(fileName)) File(homeDir, fileName).delete()
    }

    private fun importBlocking(uri: String): String? {
        val temp = File.createTempFile("bg-src", ".tmp", context.cacheDir)
        var target: File? = null
        return try {
            if (!copyLimited(uri.toUri(), temp)) return null
            val bitmap = decode(temp)
            try {
                val dir = homeDir.apply { mkdirs() }
                val file = newTargetFile(dir).also { target = it }
                val ok = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (ok) file.name else null.also { file.delete() }
            } finally {
                bitmap.recycle()
            }
        } catch (_: IOException) {
            target?.delete()
            null
        } catch (_: ImageDecoder.DecodeException) {
            target?.delete()
            null
        } catch (_: RuntimeException) {
            // URI không đọc được (SecurityException, IllegalArgumentException, provider lạ...).
            target?.delete()
            null
        } catch (_: OutOfMemoryError) {
            target?.delete()
            null
        } finally {
            temp.delete()
        }
    }

    /** Copy [uri] vào [dest]; false nếu không mở được hoặc vượt [MAX_SOURCE_BYTES]. */
    private fun copyLimited(
        uri: Uri,
        dest: File,
    ): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use { src ->
            dest.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val n = src.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_SOURCE_BYTES) return false
                    out.write(buffer, 0, n)
                }
            }
        }
        return true
    }

    /** Decode có áp EXIF, thu nhỏ về cạnh dài ≤ [MAX_EDGE] (không phóng to). */
    private fun decode(file: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longEdge
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }

    private fun newTargetFile(dir: File): File {
        var stamp = System.currentTimeMillis()
        while (File(dir, "bg-$stamp.jpg").exists()) stamp++
        return File(dir, "bg-$stamp.jpg")
    }

    /** Chỉ chấp nhận tên phẳng (không có đường dẫn) để không thoát khỏi thư mục home. */
    private fun isSafeName(name: String): Boolean = name.isNotEmpty() && '/' !in name && '\\' !in name && name != ".."

    private companion object {
        const val MAX_EDGE = 2048
        const val MAX_SOURCE_BYTES = 50L * 1024 * 1024
        const val JPEG_QUALITY = 90
        const val BUFFER_SIZE = 64 * 1024
    }
}
