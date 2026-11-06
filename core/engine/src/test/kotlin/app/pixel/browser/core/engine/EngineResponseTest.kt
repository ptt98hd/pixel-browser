package app.pixel.browser.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 007 AC1 (ràng buộc nội dung response) và AC7 (che mật khẩu) – JVM. */
class EngineResponseTest {
    private fun loaded() =
        FakeEngineSession().apply {
            loadUrl("https://a.com/")
            simulatePageFinished("https://a.com/")
        }

    @Test
    fun ac1_grantWithPermissionNotRequested_isRejected() {
        val s = loaded()
        s.simulatePermissionRequest(setOf(SitePermission.Camera))
        val id =
            s.requests.value
                .single()
                .id
        assertFalse(s.respond(id, EngineResponse.Grant(setOf(SitePermission.Microphone))))
        assertFalse(s.respond(id, EngineResponse.Grant(setOf(SitePermission.Camera, SitePermission.Location))))
        assertTrue(s.requests.value.isNotEmpty())
        assertTrue(s.respond(id, EngineResponse.Grant(emptySet())))
    }

    @Test
    fun ac1_multipleFiles_rejectedForSingleChooser_acceptedForMultiple() {
        val single = loaded()
        single.simulateFileChooser(listOf("image/*"), allowMultiple = false)
        val two = EngineResponse.Files(listOf("content://a/1", "content://a/2"))
        assertFalse(
            single.respond(
                single.requests.value
                    .single()
                    .id,
                two,
            ),
        )
        assertTrue(single.requests.value.isNotEmpty())

        val multi = loaded()
        multi.simulateFileChooser(listOf("image/*"), allowMultiple = true)
        assertTrue(
            multi.respond(
                multi.requests.value
                    .single()
                    .id,
                two,
            ),
        )
    }

    @Test
    fun ac7_credentialsToString_hidesPassword() {
        val text = EngineResponse.Credentials("u", "secret").toString()
        assertFalse(text, text.contains("secret"))
    }
}
