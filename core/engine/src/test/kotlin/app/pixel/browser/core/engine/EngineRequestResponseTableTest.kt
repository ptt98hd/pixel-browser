package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Spec 007 AC1 – bảng response hợp lệ cho từng loại request, chạy trên [FakeEngineSession] (JVM).
 * Mỗi tổ hợp (request × response): `respond` trả true đúng khi cặp hợp lệ; nếu false, request vẫn trong `requests`.
 */
@RunWith(Parameterized::class)
class EngineRequestResponseTableTest(
    private val requestName: String,
    private val responseName: String,
    private val expected: Boolean,
) {
    @Test
    fun respond_acceptsOnlyValidResponseForRequestKind() {
        val s =
            FakeEngineSession().apply {
                loadUrl("https://a.com/")
                simulatePageFinished("https://a.com/")
            }
        REQUESTS.getValue(requestName)(s)
        val request = s.requests.value.single()
        val accepted = s.respond(request.id, RESPONSES.getValue(responseName)())
        assertEquals("$requestName ← $responseName", expected, accepted)
        assertEquals("$requestName ← $responseName: request còn chờ", !expected, s.requests.value.contains(request))
    }

    companion object {
        private val REQUESTS: Map<String, (FakeEngineSession) -> Unit> =
            linkedMapOf(
                "Alert" to { s -> s.simulateJsDialog(JsDialogKind.Alert, "m") },
                "Confirm" to { s -> s.simulateJsDialog(JsDialogKind.Confirm, "m") },
                "BeforeUnload" to { s -> s.simulateJsDialog(JsDialogKind.BeforeUnload, "m") },
                "Prompt" to { s -> s.simulateJsDialog(JsDialogKind.Prompt, "m", "d") },
                "PermissionPrompt" to { s -> s.simulatePermissionRequest(setOf(SitePermission.Camera)) },
                "FileChooser" to { s -> s.simulateFileChooser(listOf("image/*")) },
                "HttpAuth" to { s -> s.simulateHttpAuth("a.com", "Admin") },
                "NewWindow" to { s -> s.simulateCreateWindow(isUserGesture = true) },
                "PopupBlocked" to { s -> s.simulateCreateWindow(isUserGesture = false) },
                "CloseWindow" to { s -> s.simulateCloseWindow() },
                "Download" to { s -> s.simulateDownload("https://a.com/f.pdf") },
            )

        private val RESPONSES: Map<String, () -> EngineResponse> =
            linkedMapOf(
                "Dismiss" to { EngineResponse.Dismiss() },
                "Confirm" to { EngineResponse.Confirm() },
                "PromptText" to { EngineResponse.PromptText("abc") },
                "Files" to { EngineResponse.Files(listOf("content://a/1")) },
                "Grant" to { EngineResponse.Grant(setOf(SitePermission.Camera)) },
                "Credentials" to { EngineResponse.Credentials("u", "p") },
                "AttachWindow" to { EngineResponse.AttachWindow(FakeEngineSession()) },
            )

        private val VALID: Map<String, Set<String>> =
            mapOf(
                "Alert" to setOf("Confirm", "Dismiss"),
                "Confirm" to setOf("Confirm", "Dismiss"),
                "BeforeUnload" to setOf("Confirm", "Dismiss"),
                "Prompt" to setOf("PromptText", "Confirm", "Dismiss"),
                "PermissionPrompt" to setOf("Grant", "Dismiss"),
                "FileChooser" to setOf("Files", "Dismiss"),
                "HttpAuth" to setOf("Credentials", "Dismiss"),
                "NewWindow" to setOf("AttachWindow", "Dismiss"),
                "PopupBlocked" to setOf("Dismiss"),
                "CloseWindow" to setOf("Dismiss"),
                "Download" to setOf("Dismiss"),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0} <- {1} = {2}")
        fun data(): List<Array<Any>> =
            REQUESTS.keys.flatMap { request ->
                RESPONSES.keys.map { response -> arrayOf(request, response, response in VALID.getValue(request)) }
            }
    }
}
