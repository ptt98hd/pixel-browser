package app.pixel.browser.core.data.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Review spec 010: version lạ không bị ghi đè; đọc file bị chỉnh tay/hỏng được làm sạch. */
@RunWith(RobolectricTestRunner::class)
class FileHomeConfigStoreHardeningTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file: File get() = File(tmp.root, "config.json")
    private val backup: File get() = File(tmp.root, "config.json.unsupported")

    private fun store() = FileHomeConfigStore(file)

    private fun shortcut(
        id: String,
        title: String = "T",
        url: String = "https://$id.example/",
    ) = JSONObject().put("id", id).put("title", title).put("url", url)

    private fun writeRaw(vararg shortcuts: JSONObject) {
        val arr = org.json.JSONArray().also { a -> shortcuts.forEach(a::put) }
        file.writeText(JSONObject().put("version", 1).put("shortcuts", arr).toString())
    }

    @Test
    fun unknownNewerVersion_isBackedUp_beforeFirstWrite() {
        val original = """{"version":2,"future":true}"""
        file.writeText(original)
        val s = store()
        assertNull(s.read())
        val config = HomeConfig(shortcuts = listOf(Shortcut("a", "A", "https://a.example/")))
        s.write(config)
        assertEquals(original, backup.readText())
        assertEquals(config, store().read())
    }

    @Test
    fun knownVersion_isNotBackedUp() {
        val s = store()
        s.write(HomeConfig(shortcuts = emptyList()))
        s.write(HomeConfig(shortcuts = listOf(Shortcut("a", "A", "https://a.example/"))))
        assertFalse(backup.exists())
    }

    @Test
    fun missingFile_writeCreatesNoBackup() {
        store().write(HomeConfig(shortcuts = emptyList()))
        assertFalse(backup.exists())
        assertTrue(file.exists())
    }

    @Test
    fun read_keepsAtMostMaxShortcuts() {
        writeRaw(*Array(HomeDefaults.MAX_SHORTCUTS + 5) { shortcut("s$it") })
        val read = store().read()!!
        assertEquals(HomeDefaults.MAX_SHORTCUTS, read.shortcuts.size)
        assertEquals("s0", read.shortcuts.first().id)
    }

    @Test
    fun read_dropsDuplicateIds_keepingFirst() {
        writeRaw(shortcut("a", "First"), shortcut("a", "Second", "https://other.example/"), shortcut("b"))
        val read = store().read()!!
        assertEquals(listOf("a", "b"), read.shortcuts.map { it.id })
        assertEquals("First", read.shortcuts[0].title)
    }

    @Test
    fun read_truncatesLongTitles() {
        writeRaw(shortcut("a", "x".repeat(200)))
        assertEquals(
            "x".repeat(HomeDefaults.MAX_TITLE_LENGTH),
            store()
                .read()!!
                .shortcuts
                .single()
                .title,
        )
    }
}
