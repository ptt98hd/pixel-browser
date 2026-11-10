package app.pixel.browser.core.data.site

import android.util.AtomicFile
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Dữ liệu `desktop.json` (spec 015 B05): mặc định toàn cục + ngoại lệ theo site (giữ thứ tự chèn). */
data class DesktopSiteData(
    val version: Int = 1,
    val defaultDesktop: Boolean = false,
    val sites: Map<String, Boolean> = emptyMap(),
)

/** Nơi lưu [DesktopSiteData]. `write` làm I/O đĩa (gọi trên IO); `read` đồng bộ lúc khởi tạo. */
interface DesktopSiteStore {
    /** null nếu chưa có, hỏng hoặc version không hỗ trợ. */
    fun read(): DesktopSiteData?

    fun write(data: DesktopSiteData)
}

/**
 * [DesktopSiteStore] dạng JSON (`org.json` + [AtomicFile]) tại [file] (spec 015 AC15). Lỗi không ném ra: [read]
 * trả null cho file thiếu/hỏng/version lạ (mục lẻ bị bỏ, tối đa [DesktopSitePolicy.MAX_SITES]); [write] bỏ lần ghi
 * lỗi và giữ file cũ. File version lạ được đổi tên `<file>.unsupported` trước lần ghi đầu tiên.
 */
class FileDesktopSiteStore(
    file: File,
) : DesktopSiteStore {
    private val atomicFile = AtomicFile(file)
    private val backupFile = File(file.path + ".unsupported")
    private var checkedExisting = false

    override fun read(): DesktopSiteData? =
        if (isOversize()) {
            null
        } else {
            readParsed()
        }

    private fun isOversize(): Boolean = atomicFile.baseFile.length() > MAX_FILE_BYTES

    private fun readParsed(): DesktopSiteData? =
        try {
            decode(JSONObject(atomicFile.readFully().toString(Charsets.UTF_8)))
        } catch (_: JSONException) {
            null
        } catch (_: IOException) {
            null
        }

    override fun write(data: DesktopSiteData) {
        backUpUnknownVersionOnce()
        val bytes = encode(data).toString().toByteArray(Charsets.UTF_8)
        var out: FileOutputStream? = null
        try {
            atomicFile.baseFile.parentFile
                ?.mkdirs()
            out = atomicFile.startWrite()
            out.write(bytes)
            atomicFile.finishWrite(out)
        } catch (_: IOException) {
            out?.let(atomicFile::failWrite)
        }
    }

    private fun backUpUnknownVersionOnce() {
        if (checkedExisting) return
        checkedExisting = true
        if (isOversize()) return
        val version =
            try {
                JSONObject(atomicFile.readFully().toString(Charsets.UTF_8)).optInt(KEY_VERSION, VERSION)
            } catch (_: JSONException) {
                return
            } catch (_: IOException) {
                return
            }
        if (version != VERSION) {
            backupFile.delete()
            atomicFile.baseFile.renameTo(backupFile)
        }
    }

    private fun encode(data: DesktopSiteData): JSONObject {
        val sites = JSONObject()
        data.sites.forEach { (k, v) -> if (isValidHost(k)) sites.put(k, v) }
        return JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_DEFAULT, data.defaultDesktop)
            .put(KEY_SITES, sites)
    }

    private fun decode(root: JSONObject): DesktopSiteData? {
        if (root.optInt(KEY_VERSION, 0) != VERSION) return null
        val raw = root.optJSONObject(KEY_SITES)
        val sites = LinkedHashMap<String, Boolean>()
        if (raw != null) {
            val keys = raw.keys()
            while (keys.hasNext() && sites.size < DesktopSitePolicy.MAX_SITES) {
                val key = keys.next()
                val value = raw.opt(key) as? Boolean ?: continue
                if (isValidHost(key)) sites[key] = value
            }
        }
        return DesktopSiteData(VERSION, root.optBoolean(KEY_DEFAULT, false), sites)
    }

    private fun isValidHost(key: String): Boolean =
        key.isNotBlank() && key.length <= MAX_HOST_LENGTH &&
            key.none { it.isWhitespace() || it.isISOControl() || it in INVALID_HOST_CHARS }

    private companion object {
        const val VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_DEFAULT = "defaultDesktop"
        const val KEY_SITES = "sites"
        const val MAX_HOST_LENGTH = 253
        const val MAX_FILE_BYTES = 256 * 1024L
        const val INVALID_HOST_CHARS = "/\\?#@%"
    }
}
