package app.pixel.browser.core.data.home

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Nơi lưu [HomeConfig] (spec 010). I/O đĩa: gọi trên dispatcher IO. */
interface HomeConfigStore {
    /** null nếu chưa có, hỏng hoặc version không hỗ trợ. */
    fun read(): HomeConfig?

    fun write(config: HomeConfig)
}

/**
 * [HomeConfigStore] dạng JSON (`org.json` + [AtomicFile]) trong [file] (spec 010 AC10).
 *
 * [read]/[write] làm I/O đĩa: gọi ngoài main thread. Lỗi không ném ra: [read] trả null cho file thiếu/hỏng/version
 * lạ (shortcut lỗi lẻ bị bỏ, phần còn lại giữ; tối đa [HomeDefaults.MAX_SHORTCUTS], id trùng bỏ, title bị cắt);
 * [write] bỏ lần ghi lỗi và giữ nguyên file cũ. File có `version` mới hơn (app bản cũ chạy sau khi nâng cấp rồi
 * hạ cấp) được đổi tên thành `<file>.unsupported` (không dùng `.bak`: trùng tên file dự phòng nội bộ của [AtomicFile]) trước lần ghi đầu tiên để không mất dữ liệu.
 */
class FileHomeConfigStore(
    file: File,
) : HomeConfigStore {
    private val atomicFile = AtomicFile(file)
    private val backupFile = File(file.path + ".unsupported")

    /** Đã kiểm tra file hiện có (version lạ) trước lần ghi đầu tiên chưa. */
    private var checkedExisting = false

    override fun read(): HomeConfig? =
        try {
            decode(JSONObject(atomicFile.readFully().toString(Charsets.UTF_8)))
        } catch (_: JSONException) {
            null
        } catch (_: IOException) {
            null
        }

    override fun write(config: HomeConfig) {
        backUpUnknownVersionOnce()
        val bytes = encode(config).toString().toByteArray(Charsets.UTF_8)
        var out: FileOutputStream? = null
        try {
            out = atomicFile.startWrite()
            out.write(bytes)
            atomicFile.finishWrite(out)
        } catch (_: IOException) {
            // failWrite xóa file tạm và giữ bản cũ; startWrite lỗi thì chưa có gì để dọn.
            out?.let(atomicFile::failWrite)
        }
    }

    private fun backUpUnknownVersionOnce() {
        if (checkedExisting) return
        checkedExisting = true
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

    private fun encode(config: HomeConfig): JSONObject {
        val shortcuts = JSONArray()
        config.shortcuts.forEach {
            shortcuts.put(JSONObject().put(KEY_ID, it.id).put(KEY_TITLE, it.title).put(KEY_URL, it.url))
        }
        return JSONObject()
            .put(KEY_VERSION, config.version)
            .put(KEY_SHORTCUTS, shortcuts)
            .put(KEY_BACKGROUND, encode(config.background))
    }

    private fun encode(bg: HomeBackground): JSONObject =
        when (bg) {
            HomeBackground.Dynamic -> {
                JSONObject().put(KEY_TYPE, TYPE_DYNAMIC)
            }

            is HomeBackground.Solid -> {
                JSONObject().put(KEY_TYPE, TYPE_SOLID).put(KEY_ARGB, bg.argb)
            }

            is HomeBackground.Image -> {
                JSONObject()
                    .put(KEY_TYPE, TYPE_IMAGE)
                    .put(KEY_FILE_NAME, bg.fileName)
                    .put(KEY_DIM, bg.dim.toDouble())
            }
        }

    private fun decode(root: JSONObject): HomeConfig? {
        if (root.optInt(KEY_VERSION, 0) != VERSION) return null
        val array = root.getJSONArray(KEY_SHORTCUTS)
        val shortcuts =
            (0 until array.length())
                .mapNotNull { i -> array.optJSONObject(i)?.let(::decodeShortcut) }
                .distinctBy { it.id }
                .take(HomeDefaults.MAX_SHORTCUTS)
        return HomeConfig(VERSION, shortcuts, decodeBackground(root.optJSONObject(KEY_BACKGROUND)))
    }

    /** Shortcut thiếu id/url hoặc URL không phải http/https → bỏ. */
    private fun decodeShortcut(o: JSONObject): Shortcut? {
        val id = o.optString(KEY_ID).takeIf { it.isNotBlank() } ?: return null
        val url = o.optString(KEY_URL).takeIf(::isWebUrl) ?: return null
        return Shortcut(id, o.optString(KEY_TITLE).take(HomeDefaults.MAX_TITLE_LENGTH), url)
    }

    /** Nền thiếu/hỏng/loại lạ → [HomeBackground.Dynamic]. */
    private fun decodeBackground(o: JSONObject?): HomeBackground {
        if (o == null) return HomeBackground.Dynamic
        return when (o.optString(KEY_TYPE)) {
            TYPE_SOLID -> {
                if (o.has(KEY_ARGB)) HomeBackground.Solid(o.optInt(KEY_ARGB)) else HomeBackground.Dynamic
            }

            TYPE_IMAGE -> {
                val name = o.optString(KEY_FILE_NAME)
                if (name.isBlank()) {
                    HomeBackground.Dynamic
                } else {
                    val dim = o.optDouble(KEY_DIM, HomeDefaults.DEFAULT_DIM.toDouble()).toFloat()
                    HomeBackground.Image(name, dim.coerceIn(0f, HomeDefaults.MAX_DIM))
                }
            }

            else -> {
                HomeBackground.Dynamic
            }
        }
    }

    private companion object {
        const val VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_SHORTCUTS = "shortcuts"
        const val KEY_BACKGROUND = "background"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_URL = "url"
        const val KEY_TYPE = "type"
        const val KEY_ARGB = "argb"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_DIM = "dim"
        const val TYPE_DYNAMIC = "dynamic"
        const val TYPE_SOLID = "solid"
        const val TYPE_IMAGE = "image"
    }
}
