package app.pixel.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/** Spec 006 – AC17: intent-filter và `launchMode` của `MainActivity` trong manifest đã merge. */
@RunWith(RobolectricTestRunner::class)
class ManifestIntentFilterTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val mainActivity = MainActivity::class.java.name

    /** Như `startActivity` implicit: luôn có `CATEGORY_DEFAULT`. */
    private fun query(intent: Intent): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(intent.addCategory(Intent.CATEGORY_DEFAULT), 0)

    private fun mainActivityInfo(intent: Intent): ResolveInfo? =
        query(intent).firstOrNull {
            it.activityInfo.packageName == context.packageName &&
                it.activityInfo.name == mainActivity
        }

    private fun resolvesToMain(intent: Intent): Boolean = mainActivityInfo(intent) != null

    private fun browsableView(url: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

    @Test
    fun ac17_browsableViewHttpAndHttps_resolveToMainActivity() {
        assertTrue(resolvesToMain(browsableView("https://x.com")))
        assertTrue(resolvesToMain(browsableView("http://x.com")))
        assertTrue(resolvesToMain(browsableView("https://x.com/path?q=1#f")))
    }

    @Test
    fun ac17_browsableViewOtherSchemes_doNotResolveToApp() {
        for (url in listOf("file:///a", "content://x/y", "javascript:alert(1)")) {
            assertFalse(url, resolvesToMain(browsableView(url)))
            assertFalse(url, query(browsableView(url)).any { it.activityInfo.packageName == context.packageName })
        }
    }

    @Test
    fun ac17_webSearch_resolvesToMainActivity() {
        assertTrue(resolvesToMain(Intent(Intent.ACTION_WEB_SEARCH)))
    }

    @Test
    fun ac17_search_resolvesToMainActivity() {
        assertTrue(resolvesToMain(Intent(Intent.ACTION_SEARCH)))
    }

    @Test
    fun ac17_processTextPlain_resolvesToMainActivity() {
        assertTrue(resolvesToMain(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")))
    }

    @Test
    fun ac17_sendTextPlain_resolvesToMainActivity() {
        assertTrue(resolvesToMain(Intent(Intent.ACTION_SEND).setType("text/plain")))
    }

    @Test
    fun ac17_sendImage_doesNotResolveToMainActivity() {
        assertFalse(resolvesToMain(Intent(Intent.ACTION_SEND).setType("image/png")))
        assertFalse(resolvesToMain(Intent(Intent.ACTION_SEND).setType("image/*")))
    }

    @Test
    fun ac17_launcherStillResolves() {
        // Launcher truy vấn không kèm CATEGORY_DEFAULT.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        assertTrue(
            context.packageManager.queryIntentActivities(launcher, 0).any { it.activityInfo.name == mainActivity },
        )
    }

    @Test
    fun ac17_mainActivity_isSingleTask() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }

    @Test
    fun ac17_processTextFilter_hasProcessTextLabel() {
        val labelRes = context.resources.getIdentifier("process_text_label", "string", context.packageName)
        assertNotEquals("R.string.process_text_label must exist", 0, labelRes)
        assertTrue(
            "PROCESS_TEXT must resolve to MainActivity",
            resolvesToMain(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")),
        )
        assertEquals("@string/process_text_label", processTextFilterLabel())
    }

    /**
     * `android:label` của intent-filter PROCESS_TEXT trong manifest đã merge. Robolectric không đưa label của
     * filter vào `ResolveInfo`, nên đọc trực tiếp file manifest mà AGP ghi trong `test_config.properties`.
     */
    private fun processTextFilterLabel(): String? {
        val props = Properties()
        javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties").use { props.load(it) }
        val path = props.getProperty("android_merged_manifest")
        assertNotNull("android_merged_manifest", path)
        val doc =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(File(path))
        val activities = doc.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val name = activity.getAttributeNS(ANDROID_NS, "name")
            if (name != mainActivity && name != ".MainActivity") continue
            val filters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until filters.length) {
                val filter = filters.item(j) as Element
                val actions = filter.getElementsByTagName("action")
                val isProcessText =
                    (0 until actions.length).any {
                        (actions.item(it) as Element).getAttributeNS(ANDROID_NS, "name") == Intent.ACTION_PROCESS_TEXT
                    }
                if (isProcessText) return filter.getAttributeNS(ANDROID_NS, "label")
            }
        }
        return null
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
