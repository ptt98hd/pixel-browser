package app.pixel.browser.core.tabs

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64

/**
 * [TabSnapshotStore] lưu JSON (org.json) vào [file] qua [AtomicFile]; [TabSnapshot.engineState] mã hóa Base64.
 *
 * [read] và [write] làm I/O đĩa: phải gọi ngoài main thread (vd. `Dispatchers.IO`). [file] nên nằm trong
 * `Context.noBackupFilesDir` để không bị Auto Backup sao lưu. Lỗi I/O không ném ra: [read] trả null,
 * [write] bỏ lần ghi đó và giữ nguyên file cũ.
 */
class FileTabSnapshotStore(
    file: File,
) : TabSnapshotStore {
    private val atomicFile = AtomicFile(file)

    override fun read(): TabsSnapshot? =
        try {
            decode(JSONObject(atomicFile.readFully().toString(Charsets.UTF_8)))
        } catch (_: JSONException) {
            null
        } catch (_: IOException) {
            null
        }

    override fun write(snapshot: TabsSnapshot) {
        val bytes = encode(snapshot).toString().toByteArray(Charsets.UTF_8)
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

    private fun encode(snapshot: TabsSnapshot): JSONObject {
        val tabs = JSONArray()
        snapshot.tabs.forEach { t ->
            tabs.put(
                JSONObject()
                    .put(KEY_ID, t.id)
                    .put(KEY_URL, t.url)
                    .put(KEY_TITLE, t.title)
                    .put(KEY_ENGINE_STATE, t.engineState?.let { Base64.getEncoder().encodeToString(it) })
                    .put(KEY_BACK_TO_HOME, t.backToHome),
            )
        }
        return JSONObject()
            .put(KEY_SELECTED, snapshot.selectedTabId ?: JSONObject.NULL)
            .put(KEY_TABS, tabs)
    }

    private fun decode(root: JSONObject): TabsSnapshot {
        val array = root.getJSONArray(KEY_TABS)
        val tabs =
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                TabSnapshot(
                    id = o.getString(KEY_ID),
                    url = o.getString(KEY_URL),
                    title = o.optString(KEY_TITLE),
                    engineState = o.optStringOrNull(KEY_ENGINE_STATE)?.let(::decodeBase64),
                    // Khóa tùy chọn (spec 010): file cũ không có → false.
                    backToHome = o.optBoolean(KEY_BACK_TO_HOME, false),
                )
            }
        return TabsSnapshot(tabs, root.optStringOrNull(KEY_SELECTED))
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    /** Engine state hỏng thì bỏ (tab vẫn giữ, sẽ tải lại URL). */
    private fun decodeBase64(s: String): ByteArray? =
        try {
            Base64.getDecoder().decode(s)
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val KEY_TABS = "tabs"
        const val KEY_SELECTED = "selectedTabId"
        const val KEY_ID = "id"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_ENGINE_STATE = "engineState"
        const val KEY_BACK_TO_HOME = "backToHome"
    }
}
