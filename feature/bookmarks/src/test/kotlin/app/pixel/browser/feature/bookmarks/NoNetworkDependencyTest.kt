package app.pixel.browser.feature.bookmarks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Spec 013 AC29 – không có HTTP client/kết nối trong module; không tải gì từ nội dung file nhập. */
class NoNetworkDependencyTest {
    @Test
    fun gradle_hasNoHttpClientOrImageLoader() {
        val gradle = File("build.gradle.kts").readText()
        listOf("okhttp", "ktor", "retrofit", "cronet", "volley", "coil", "glide", "apache.http").forEach {
            assertFalse("dependency $it", gradle.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun mainSources_neverOpenConnections() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        val banned =
            listOf("HttpURLConnection", "openConnection", "openStream", "java.net.URL(", "java.net.http", "WebView")
        sources.forEach { f -> banned.forEach { assertFalse("${f.name} uses $it", f.readText().contains(it)) } }
    }
}
