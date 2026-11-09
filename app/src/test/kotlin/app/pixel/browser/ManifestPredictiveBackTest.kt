package app.pixel.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/** Spec 008 (Quyết định, back hệ thống) – manifest bật predictive back cho app. */
@RunWith(RobolectricTestRunner::class)
class ManifestPredictiveBackTest {
    @Test
    fun application_enablesOnBackInvokedCallback() {
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
        val application = doc.getElementsByTagName("application").item(0) as Element
        assertEquals("true", application.getAttributeNS(ANDROID_NS, "enableOnBackInvokedCallback"))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
