package app.pixel.browser.core.bookmarks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Spec 013 AC29 + "Parser … Kotlin thuần": không HTTP client, parser không import `android.*`. */
class NoNetworkOrAndroidInParserTest {
    private val gradle = File("build.gradle.kts").readText()
    private val mainSources =
        File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    @Test
    fun noHttpClientDependency() {
        val banned = listOf("okhttp", "ktor", "retrofit", "cronet", "volley", "coil", "glide", "apache.http")
        banned.forEach { assertFalse("dependency $it", gradle.contains(it, ignoreCase = true)) }
    }

    @Test
    fun mainSources_neverOpenConnections() {
        assertTrue(mainSources.isNotEmpty())
        val banned =
            listOf("HttpURLConnection", "openConnection", "openStream", "java.net.URL(", "java.net.http", "WebView")
        mainSources.forEach { f ->
            banned.forEach { assertFalse("${f.name} uses $it", f.readText().contains(it)) }
        }
    }

    @Test
    fun netscapeParser_isPureKotlin_noAndroidImports() {
        val file = mainSources.first { it.name == "NetscapeBookmarks.kt" }
        assertFalse(file.readText().lines().any { it.trim().startsWith("import android.") })
    }
}
