package app.pixel.browser.feature.bookmarks

import android.content.ContentResolver
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface FileRead {
    class Bytes(
        val bytes: ByteArray,
    ) : FileRead

    data object TooLarge : FileRead

    data object Unreadable : FileRead
}

interface BookmarkFileGateway {
    /** Đọc tối đa [maxBytes] byte (đếm khi đọc, không tin `OpenableColumns.SIZE`); nhiều hơn → [FileRead.TooLarge]. */
    suspend fun read(
        uri: String,
        maxBytes: Int,
    ): FileRead

    suspend fun write(
        uri: String,
        bytes: ByteArray,
    ): Boolean
}

/** Đọc/ghi qua SAF (`content://`); không xin quyền lưu trữ, không giữ quyền URI lâu dài. Lỗi không ném. */
class ContentResolverBookmarkFile(
    private val resolver: ContentResolver,
) : BookmarkFileGateway {
    override suspend fun read(
        uri: String,
        maxBytes: Int,
    ): FileRead =
        withContext(Dispatchers.IO) {
            try {
                val stream = resolver.openInputStream(uri.toUri()) ?: return@withContext FileRead.Unreadable
                stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER)
                    var total = 0
                    while (true) {
                        // Không bao giờ đọc quá maxBytes + 1 byte: nguồn vô hạn bị cắt ngay khi vượt giới hạn.
                        val want = minOf(buffer.size, maxBytes - total + 1)
                        val n = input.read(buffer, 0, want)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return@withContext FileRead.TooLarge
                        out.write(buffer, 0, n)
                    }
                    FileRead.Bytes(out.toByteArray())
                }
            } catch (e: Exception) {
                FileRead.Unreadable
            }
        }

    override suspend fun write(
        uri: String,
        bytes: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val stream = resolver.openOutputStream(uri.toUri(), "wt") ?: return@withContext false
                stream.use { it.write(bytes) }
                true
            } catch (e: Exception) {
                false
            }
        }

    private companion object {
        const val BUFFER = 8 * 1024
    }
}
