package app.pixel.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/** Spec 007 AC26 – quyền, uses-feature và FileProvider trong manifest đã merge. */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val authority get() = "${context.packageName}.fileprovider"

    @Suppress("DEPRECATION")
    private fun packageInfo() =
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA,
        )

    @Test
    fun ac26_requestsWebPlatformPermissions() {
        val requested = packageInfo().requestedPermissions.orEmpty().toSet()
        for (
        p in listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        ) {
            assertTrue(p, p in requested)
        }
    }

    @Test
    fun ac26_hardwareFeatures_areOptional() {
        val features = mergedManifest().getElementsByTagName("uses-feature")
        val required =
            (0 until features.length).associate {
                val e = features.item(it) as Element
                e.getAttributeNS(ANDROID_NS, "name") to e.getAttributeNS(ANDROID_NS, "required")
            }
        for (name in listOf("android.hardware.camera", "android.hardware.microphone", "android.hardware.location")) {
            assertEquals(name, "false", required[name])
        }
    }

    @Test
    fun ac26_fileProvider_isPrivate_andGrantsUriPermissions() {
        val provider = packageInfo().providers.orEmpty().firstOrNull { it.authority == authority }
        assertNotNull("FileProvider $authority", provider)
        assertEquals("androidx.core.content.FileProvider", provider!!.name)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
    }

    @Test
    fun ac26_fileProvider_sharesOnlyCacheCaptures() {
        val provider = mergedManifest().getElementsByTagName("provider")
        val element =
            (0 until provider.length)
                .map { provider.item(it) as Element }
                .first {
                    it.getAttributeNS(ANDROID_NS, "authorities") == authority ||
                        it.getAttributeNS(ANDROID_NS, "authorities") == "\${applicationId}.fileprovider"
                }
        val metaData = element.getElementsByTagName("meta-data")
        val resource =
            (0 until metaData.length)
                .map { metaData.item(it) as Element }
                .first { it.getAttributeNS(ANDROID_NS, "name") == "android.support.FILE_PROVIDER_PATHS" }
                .getAttributeNS(ANDROID_NS, "resource")
        val resName = resource.removePrefix("@xml/")
        val resId = context.resources.getIdentifier(resName, "xml", context.packageName)
        assertNotEquals("xml/$resName", 0, resId)

        val paths = mutableListOf<Pair<String, String?>>()
        val parser = context.resources.getXml(resId)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name != "paths") {
                paths += parser.name to parser.getAttributeValue(null, "path")
            }
        }
        assertEquals(1, paths.size)
        assertEquals("cache-path", paths.single().first)
        assertTrue(paths.single().second, paths.single().second?.trim('/') == "captures")
    }

    private fun mergedManifest(): org.w3c.dom.Document {
        val props = Properties()
        javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties").use { props.load(it) }
        val path = props.getProperty("android_merged_manifest")
        assertNotNull("android_merged_manifest", path)
        return DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(path))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
