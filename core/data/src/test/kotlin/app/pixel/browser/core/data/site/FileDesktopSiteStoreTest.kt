package app.pixel.browser.core.data.site

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Spec 015 AC15 – [FileDesktopSiteStore] (org.json cần Robolectric). */
@RunWith(RobolectricTestRunner::class)
class FileDesktopSiteStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file: File get() = File(tmp.root, "site/desktop.json")

    private fun store() = FileDesktopSiteStore(file)

    private fun writeRaw(text: String) {
        file.parentFile!!.mkdirs()
        file.writeText(text)
    }

    private fun sitesJson(vararg entries: Pair<String, Any>): String {
        val sites = JSONObject()
        entries.forEach { (k, v) -> sites.put(k, v) }
        return JSONObject()
            .put("version", 1)
            .put("defaultDesktop", false)
            .put("sites", sites)
            .toString()
    }

    @Test
    fun ac15_roundTrip_keepsDefaultAndSiteOrder() {
        val data =
            DesktopSiteData(
                defaultDesktop = true,
                sites =
                    linkedMapOf(
                        "z.com" to true,
                        "a.com" to false,
                        "m.org" to true,
                    ),
            )
        store().write(data)
        val read = store().read()
        assertEquals(data, read)
        assertEquals(listOf("z.com", "a.com", "m.org"), read!!.sites.keys.toList())
    }

    @Test
    fun ac15_write_createsMissingParentDirectory() {
        store().write(DesktopSiteData())
        assertTrue(file.exists())
    }

    @Test
    fun ac15_missingFile_readsNull() {
        assertNull(store().read())
    }

    @Test
    fun ac15_corruptJson_readsNull() {
        writeRaw("{not json")
        assertNull(store().read())
    }

    @Test
    fun ac15_newerVersion_readsNull() {
        writeRaw(JSONObject(sitesJson("a.com" to true)).put("version", 2).toString())
        assertNull(store().read())
    }

    @Test
    fun ac15_unknownVersion_isRenamedUnsupported_beforeFirstWrite() {
        val original = JSONObject(sitesJson("a.com" to true)).put("version", 2).toString()
        writeRaw(original)
        val s = store()
        assertNull(s.read())
        s.write(DesktopSiteData(sites = mapOf("b.com" to true)))
        val backup = File(file.path + ".unsupported")
        assertTrue(backup.exists())
        assertEquals(original, backup.readText())
        assertEquals(DesktopSiteData(sites = mapOf("b.com" to true)), s.read())
    }

    @Test
    fun ac15_invalidEntries_areDropped_restKept() {
        val longHost = "a".repeat(254) + ".com"
        writeRaw(
            sitesJson(
                "good.com" to true,
                "bad/host.com" to true,
                "has space.com" to true,
                longHost to true,
                "notbool.com" to "yes",
                "number.com" to 1,
                "also-good.org" to false,
            ),
        )
        val read = store().read()
        assertNotNull(read)
        assertEquals(listOf("good.com", "also-good.org"), read!!.sites.keys.toList())
        assertEquals(mapOf("good.com" to true, "also-good.org" to false), read.sites)
    }

    @Test
    fun ac15_readsAtMost1000Entries_dropsTheRest() {
        val sites = JSONObject()
        for (i in 1..1005) sites.put("s$i.com", true)
        writeRaw(
            JSONObject()
                .put("version", 1)
                .put("defaultDesktop", false)
                .put("sites", sites)
                .toString(),
        )
        val read = store().read()!!
        assertEquals(1000, read.sites.size)
        assertTrue("s1.com" in read.sites)
        assertFalse("s1005.com" in read.sites)
    }

    @Test
    fun ac15_failedWrite_keepsOldFile() {
        org.junit.Assume.assumeFalse("root bỏ qua quyền ghi", System.getProperty("user.name") == "root")
        store().write(DesktopSiteData(sites = mapOf("a.com" to true)))
        // Chặn ghi: thay file đích bằng thư mục khiến startWrite/finishWrite lỗi; bản cũ phải còn.
        val before = file.readText()
        file.parentFile!!.setWritable(false)
        try {
            store().write(DesktopSiteData(sites = mapOf("b.com" to true)))
        } finally {
            file.parentFile!!.setWritable(true)
        }
        assertEquals(before, file.readText())
    }

    @Test
    fun ac15_policyBuiltFromNullRead_doesNotThrow() {
        val policy = DesktopSitePolicy(initial = store().read() ?: DesktopSiteData(), persist = {})
        assertFalse(policy.resolve("a.com"))
    }

    @Test
    fun review_oversizeFile_readsNull_withoutParsing() {
        val sites = JSONObject()
        for (i in 1..20000) sites.put("site-number-$i.example.com", true)
        writeRaw(
            JSONObject()
                .put("version", 1)
                .put("defaultDesktop", false)
                .put("sites", sites)
                .toString(),
        )
        assertTrue(file.length() > 256 * 1024)
        assertNull(store().read())
    }

    @Test
    fun review_write_dropsInvalidHostKeys() {
        store().write(DesktopSiteData(sites = linkedMapOf("ok.com" to true, "bad/host" to true, "a b" to false)))
        assertEquals(
            listOf("ok.com"),
            store()
                .read()!!
                .sites.keys
                .toList(),
        )
    }

    @Test
    fun review_hostWithPercentOrControlChar_isDropped() {
        writeRaw(sitesJson("ok.com" to true, "a%2Eb.com" to true, "a\u0001b.com" to true))
        assertEquals(
            listOf("ok.com"),
            store()
                .read()!!
                .sites.keys
                .toList(),
        )
    }
}
