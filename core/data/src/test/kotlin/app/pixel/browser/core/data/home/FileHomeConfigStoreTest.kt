package app.pixel.browser.core.data.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Spec 010 – AC10: [FileHomeConfigStore] (org.json cần Robolectric). */
@RunWith(RobolectricTestRunner::class)
class FileHomeConfigStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file: File get() = File(tmp.root, "config.json")

    private fun store() = FileHomeConfigStore(file)

    private val shortcuts =
        listOf(
            Shortcut("s1", "Một \"quoted\"", "https://a.example/?q=ü&x=1"),
            Shortcut("s2", "B", "https://b.example/"),
            Shortcut("s3", "C", "http://c.example:8080/p"),
        )

    private fun roundTrip(config: HomeConfig): HomeConfig? {
        store().write(config)
        return store().read()
    }

    /** Sửa JSON đã ghi: tìm object shortcut có url [url] trong mảng bất kỳ của root rồi áp [edit]. */
    private fun editShortcutJson(
        url: String,
        edit: (JSONObject, String) -> Unit,
    ) {
        val root = JSONObject(file.readText())
        var found = false
        root.keys().forEach { key ->
            val arr = root.opt(key) as? JSONArray ?: return@forEach
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                o.keys().asSequence().toList().firstOrNull { o.opt(it) == url }?.let {
                    edit(o, it)
                    found = true
                }
            }
        }
        check(found) { "shortcut $url not found in ${file.readText()}" }
        file.writeText(root.toString())
    }

    @Test
    fun ac10_roundTrip_keepsShortcutOrder_andDynamicBackground() {
        val config = HomeConfig(shortcuts = shortcuts, background = HomeBackground.Dynamic)
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun ac10_roundTrip_solidWithAlpha() {
        val config = HomeConfig(shortcuts = shortcuts, background = HomeBackground.Solid(0x80123456.toInt()))
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun ac10_roundTrip_image() {
        val config = HomeConfig(shortcuts = shortcuts, background = HomeBackground.Image("bg-1700000000000.jpg", 0.45f))
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun ac10_roundTrip_emptyShortcuts() {
        val config = HomeConfig(shortcuts = emptyList())
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun ac10_missingFile_readsNull() {
        assertNull(store().read())
    }

    @Test
    fun ac10_corruptJson_readsNull() {
        file.writeText("{not json")
        assertNull(store().read())
    }

    @Test
    fun ac10_unknownNewerVersion_readsNull() {
        store().write(HomeConfig(shortcuts = shortcuts))
        val root = JSONObject(file.readText())
        root.put("version", 2)
        file.writeText(root.toString())
        assertNull(store().read())
    }

    @Test
    fun ac10_shortcutMissingUrl_isDropped_restKept() {
        store().write(HomeConfig(shortcuts = shortcuts))
        editShortcutJson(shortcuts[1].url) { o, key -> o.remove(key) }
        val read = store().read()
        assertNotNull(read)
        assertEquals(listOf(shortcuts[0], shortcuts[2]), read!!.shortcuts)
    }

    @Test
    fun ac10_shortcutWithInvalidUrl_isDropped_restKept() {
        store().write(HomeConfig(shortcuts = shortcuts))
        editShortcutJson(shortcuts[0].url) { o, key -> o.put(key, "javascript:alert(1)") }
        val read = store().read()
        assertNotNull(read)
        assertEquals(listOf(shortcuts[1], shortcuts[2]), read!!.shortcuts)
    }

    @Test
    fun ac10_writeIoFailure_keepsPreviousFile_andDoesNotThrow() {
        val old = HomeConfig(shortcuts = shortcuts)
        store().write(old)
        // Chặn file tạm của AtomicFile bằng một thư mục không rỗng → mở để ghi thất bại.
        File(tmp.root, "config.json.new").apply {
            mkdir()
            File(this, "x").writeText("x")
        }
        store().write(HomeConfig(shortcuts = emptyList(), background = HomeBackground.Solid(0xFF000000.toInt())))
        assertEquals(old, store().read())
    }

    @Test
    fun ac10_writeIntoMissingParentDir_createsIt() {
        val nested = FileHomeConfigStore(File(tmp.root, "home/config.json"))
        val config = HomeConfig(shortcuts = shortcuts)
        nested.write(config)
        assertEquals(config, nested.read())
    }
}
