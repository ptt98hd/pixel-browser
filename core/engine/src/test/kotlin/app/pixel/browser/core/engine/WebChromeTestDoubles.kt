package app.pixel.browser.core.engine

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import org.robolectric.Shadows.shadowOf

/** [PermissionRequest] ghi lại grant/deny (spec 007 AC4). */
class RecordingPermissionRequest(
    private val origin: Uri,
    private val resources: Array<String>,
) : PermissionRequest() {
    val granted = mutableListOf<List<String>>()
    var denyCalls = 0
        private set

    override fun getOrigin(): Uri = origin

    override fun getResources(): Array<String> = resources

    override fun grant(resources: Array<String>) {
        granted += resources.toList()
    }

    override fun deny() {
        denyCalls++
    }
}

class TestFileChooserParams(
    private val mode: Int,
    private val acceptTypes: Array<String>,
    private val capture: Boolean,
) : WebChromeClient.FileChooserParams() {
    override fun getMode(): Int = mode

    override fun getAcceptTypes(): Array<String> = acceptTypes

    override fun isCaptureEnabled(): Boolean = capture

    override fun getTitle(): CharSequence? = null

    override fun getFilenameHint(): String? = null

    override fun createIntent(): Intent = Intent(Intent.ACTION_GET_CONTENT)
}

/** Ghi lại mọi giá trị [ValueCallback] nhận (spec 007 AC6). */
class RecordingFileCallback : ValueCallback<Array<Uri>?> {
    val values = mutableListOf<List<Uri>?>()

    override fun onReceiveValue(value: Array<Uri>?) {
        values += value?.toList()
    }
}

class RecordingGeolocationCallback : GeolocationPermissions.Callback {
    data class Call(
        val origin: String,
        val allow: Boolean,
        val retain: Boolean,
    )

    val calls = mutableListOf<Call>()

    override fun invoke(
        origin: String,
        allow: Boolean,
        retain: Boolean,
    ) {
        calls += Call(origin, allow, retain)
    }
}

class RecordingCustomViewCallback : WebChromeClient.CustomViewCallback {
    var hiddenCalls = 0
        private set

    override fun onCustomViewHidden() {
        hiddenCalls++
    }
}

val WebViewEngineSession.chromeClient: WebChromeClient
    get() = requireNotNull(shadowOf(webView).webChromeClient) { "WebChromeClient chưa được cài" }

fun WebViewEngineSession.showCustomView(
    view: View,
    callback: WebChromeClient.CustomViewCallback,
) = chromeClient.onShowCustomView(view, callback)

fun WebViewEngineSession.emitDownloadStart(
    url: String,
    contentDisposition: String?,
    mimeType: String?,
    contentLength: Long,
) {
    val listener = requireNotNull(shadowOf(webView).downloadListener) { "DownloadListener chưa được cài (AC10)" }
    listener.onDownloadStart(url, "Mozilla/5.0", contentDisposition, mimeType, contentLength)
}
