package app.pixel.browser.feature.browser.web

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.text.format.Formatter
import android.webkit.URLUtil
import android.webkit.WebSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.JsDialogKind
import app.pixel.browser.core.engine.SitePermission
import app.pixel.browser.core.engine.isSecureOrigin
import app.pixel.browser.core.engine.schemeOf
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.R

/**
 * Hiển thị request đang chờ của tab [tabId] (tab đang chọn, spec 007 AC13): mỗi lúc một dialog. Mọi lựa chọn (kể
 * cả Back, chạm ngoài) đều trả lời request qua [onRespond] kèm [tabId] lúc hiện, nên kết quả đến muộn (picker,
 * hộp thoại quyền hệ thống) không rơi sang tab được chọn sau đó. Nội dung được dựng lại khi đổi tab/request.
 * [pageOrigin] là origin trang chính, dùng để báo quyền xin từ iframe khác origin.
 */
@Composable
fun EngineRequestHost(
    tabId: String?,
    request: EngineRequest?,
    pageOrigin: String,
    runtimePermissions: RuntimePermissionGate,
    onRespond: (tabId: String, requestId: Long, response: EngineResponse) -> Unit,
    onPermissionDecision: (tabId: String, requestId: Long, allow: Boolean) -> Unit,
    onFileChooserUnavailable: () -> Unit,
    onDownloadFailed: () -> Unit,
    /** Tab hiện tại là tab ẩn danh: hộp thoại tải xuống có cảnh báo (spec 016 AC14). */
    isPrivate: Boolean = false,
) {
    if (tabId == null || request == null) return
    val context = LocalContext.current
    key(tabId, request.id) {
        val respond = { response: EngineResponse -> onRespond(tabId, request.id, response) }
        when (request) {
            is EngineRequest.JsDialog -> {
                JsDialog(request, respond)
            }

            is EngineRequest.PermissionPrompt -> {
                PermissionDialog(
                    request = request,
                    pageOrigin = pageOrigin,
                    onDecision = { allow -> onPermissionDecision(tabId, request.id, allow) },
                    onDismiss = { respond(EngineResponse.Dismiss()) },
                )
            }

            is EngineRequest.HttpAuth -> {
                HttpAuthDialog(request, respond)
            }

            is EngineRequest.Download -> {
                DownloadDialog(
                    request,
                    onFailed = onDownloadFailed,
                    isPrivate = isPrivate,
                ) { respond(EngineResponse.Dismiss()) }
            }

            is EngineRequest.FileChooser -> {
                FileChooserLauncher(
                    request = request,
                    runtimePermissions = runtimePermissions,
                    onResponse = respond,
                    onUnavailable = onFileChooserUnavailable,
                    captureUri = { launch ->
                        createCaptureUri(context, if (launch == FileChooserLaunch.CaptureVideo) ".mp4" else ".jpg")
                    },
                    onCaptureDiscarded = { deleteCapture(context, it) },
                )
            }

            else -> {
                Unit
            }
        }
    }
}

/** Host của origin để hiển thị (vd. `example.com`); rỗng nếu không xác định. */
private fun displayHost(origin: String): String = UrlDisplay.host(origin)

/** `alert/confirm/prompt/beforeunload` (spec 007 AC14). Tiêu đề là origin của frame, không phải tiêu đề trang. */
@Composable
fun JsDialog(
    request: EngineRequest.JsDialog,
    onResponse: (EngineResponse) -> Unit,
) {
    var text by rememberSaveable(request.id) { mutableStateOf(request.defaultValue.orEmpty()) }
    var suppress by rememberSaveable(request.id) { mutableStateOf(false) }
    val host = displayHost(request.origin)
    val isBeforeUnload = request.kind == JsDialogKind.BeforeUnload
    val title =
        when {
            isBeforeUnload -> stringResource(R.string.web_js_leave_title)
            host.isEmpty() -> stringResource(R.string.web_js_title_unknown)
            else -> stringResource(R.string.web_js_title, host)
        }
    AlertDialog(
        onDismissRequest = { onResponse(EngineResponse.Dismiss(suppressFurther = suppress)) },
        modifier = Modifier.testTag(BrowserTestTags.JS_DIALOG),
        title = { Text(title, Modifier.testTag(BrowserTestTags.JS_DIALOG_TITLE)) },
        text = {
            // Message dài (tới 20 000 ký tự) cuộn trong vùng nội dung; nút luôn nằm trong màn hình.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(request.message, Modifier.testTag(BrowserTestTags.JS_DIALOG_MESSAGE))
                if (request.kind == JsDialogKind.Prompt) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(BrowserTestTags.JS_DIALOG_INPUT),
                    )
                }
                if (request.canSuppress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = suppress,
                            onCheckedChange = { suppress = it },
                            modifier = Modifier.testTag(BrowserTestTags.JS_DIALOG_SUPPRESS),
                        )
                        Text(stringResource(R.string.web_js_suppress))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onResponse(
                        if (request.kind == JsDialogKind.Prompt) {
                            EngineResponse.PromptText(text, suppressFurther = suppress)
                        } else {
                            EngineResponse.Confirm(suppressFurther = suppress)
                        },
                    )
                },
                modifier = Modifier.testTag(BrowserTestTags.JS_DIALOG_OK),
            ) { Text(stringResource(if (isBeforeUnload) R.string.web_js_leave else R.string.web_ok)) }
        },
        dismissButton =
            if (request.kind == JsDialogKind.Alert) {
                null
            } else {
                {
                    TextButton(
                        onClick = { onResponse(EngineResponse.Dismiss(suppressFurther = suppress)) },
                        modifier = Modifier.testTag(BrowserTestTags.JS_DIALOG_CANCEL),
                    ) { Text(stringResource(if (isBeforeUnload) R.string.web_js_stay else R.string.web_cancel)) }
                }
            },
    )
}

/** Hộp thoại quyền theo site (spec 007 AC15). Back/chạm ngoài → [onDismiss] (không nhớ lựa chọn). */
@Composable
fun PermissionDialog(
    request: EngineRequest.PermissionPrompt,
    onDecision: (allow: Boolean) -> Unit,
    onDismiss: () -> Unit,
    pageOrigin: String = request.origin,
) {
    val embedded = pageOrigin.isNotEmpty() && pageOrigin != request.origin
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(BrowserTestTags.PERMISSION_DIALOG),
        title = { Text(stringResource(R.string.web_permission_title, displayHost(request.origin))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (embedded) {
                    // Iframe khác origin: cho người dùng thấy trang nào đang nhúng nội dung xin quyền.
                    Text(
                        stringResource(R.string.web_permission_embedded, displayHost(pageOrigin)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                for (permission in SitePermission.entries.filter { it in request.permissions }) {
                    Text(
                        stringResource(permission.label()),
                        Modifier.testTag(BrowserTestTags.permissionItem(permission)),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDecision(true) },
                modifier = Modifier.testTag(BrowserTestTags.PERMISSION_ALLOW),
            ) { Text(stringResource(R.string.web_permission_allow)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onDecision(false) },
                modifier = Modifier.testTag(BrowserTestTags.PERMISSION_BLOCK),
            ) { Text(stringResource(R.string.web_permission_block)) }
        },
    )
}

private fun SitePermission.label(): Int =
    when (this) {
        SitePermission.Camera -> R.string.web_permission_camera
        SitePermission.Microphone -> R.string.web_permission_microphone
        SitePermission.Location -> R.string.web_permission_location
    }

private const val MAX_REALM_LENGTH = 100

/**
 * HTTP auth (spec 007 AC23). Realm do server đặt nên được cắt và đặt trong ngoặc kép; mật khẩu chỉ giữ trong
 * `remember` (không `rememberSaveable`) nên không bao giờ vào saved state.
 */
@Composable
fun HttpAuthDialog(
    request: EngineRequest.HttpAuth,
    onResponse: (EngineResponse) -> Unit,
) {
    var username by rememberSaveable(request.id) { mutableStateOf("") }
    var password by remember(request.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onResponse(EngineResponse.Dismiss()) },
        modifier = Modifier.testTag(BrowserTestTags.AUTH_DIALOG),
        title = { Text(stringResource(R.string.web_auth_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.web_auth_message, request.host, request.realm.take(MAX_REALM_LENGTH)))
                if (!isSecureOrigin(request.origin)) {
                    Text(
                        stringResource(R.string.web_auth_insecure),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(BrowserTestTags.AUTH_INSECURE_WARNING),
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.web_auth_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(BrowserTestTags.AUTH_USERNAME),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.web_auth_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { password() }
                            .testTag(BrowserTestTags.AUTH_PASSWORD),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onResponse(EngineResponse.Credentials(username, password)) },
                modifier = Modifier.testTag(BrowserTestTags.AUTH_OK),
            ) { Text(stringResource(R.string.web_auth_sign_in)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onResponse(EngineResponse.Dismiss()) },
                modifier = Modifier.testTag(BrowserTestTags.AUTH_CANCEL),
            ) { Text(stringResource(R.string.web_cancel)) }
        },
    )
}

/**
 * Link tải file (spec 007 AC24 + Quyết định 2026-11-05): tên file đã làm sạch bởi [URLUtil.guessFileName] và kích
 * thước nếu biết. "Tải xuống" giao cho [DownloadManager] hệ thống (chỉ http/https). Mọi lựa chọn → [onDone].
 */
@Composable
fun DownloadDialog(
    request: EngineRequest.Download,
    onFailed: () -> Unit = {},
    isPrivate: Boolean = false,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val fileName =
        remember(request.id) {
            sanitizeDownloadFileName(URLUtil.guessFileName(request.url, request.contentDisposition, request.mimeType))
        }
    val size =
        remember(request.id) {
            request.contentLength.takeIf { it >= 0 }?.let { Formatter.formatShortFileSize(context, it) }
        }
    AlertDialog(
        onDismissRequest = onDone,
        modifier = Modifier.testTag(BrowserTestTags.DOWNLOAD_DIALOG),
        title = { Text(stringResource(R.string.web_download_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fileName, style = MaterialTheme.typography.bodyLarge)
                if (size != null) Text(size)
                val host = displayHost(request.url)
                if (host.isNotEmpty()) Text(host, style = MaterialTheme.typography.bodySmall)
                if (isPrivate) {
                    Text(
                        stringResource(R.string.browser_private_download_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(BrowserTestTags.DOWNLOAD_PRIVATE_WARNING),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ok = enqueueDownload(context, request, fileName)
                    onDone()
                    if (!ok) onFailed()
                },
                modifier = Modifier.testTag(BrowserTestTags.DOWNLOAD_START),
            ) { Text(stringResource(R.string.web_download_start)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDone,
                modifier = Modifier.testTag(BrowserTestTags.DOWNLOAD_CANCEL),
            ) { Text(stringResource(R.string.web_cancel)) }
        },
    )
}

/**
 * Tên file an toàn cho thư mục Downloads: `/`, `\` và ký tự điều khiển → `_`; rỗng hoặc chỉ gồm dấu chấm →
 * `download`.
 */
fun sanitizeDownloadFileName(name: String): String {
    val cleaned = name.trim().map { c -> if (c == '/' || c == '\\' || c.isISOControl()) '_' else c }.joinToString("")
    return if (cleaned.isBlank() || cleaned.all { it == '.' }) "download" else cleaned
}

/** Giao [request] cho [DownloadManager] (thư mục Downloads công khai, thông báo khi xong). false nếu bị từ chối. */
private fun enqueueDownload(
    context: Context,
    request: EngineRequest.Download,
    fileName: String,
): Boolean {
    if (schemeOf(request.url) !in setOf("http", "https")) return false
    val manager = context.getSystemService(DownloadManager::class.java) ?: return false
    return try {
        val dm =
            DownloadManager
                .Request(request.url.toUri())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        request.mimeType?.takeIf { it.isNotBlank() }?.let(dm::setMimeType)
        // Không gửi Cookie: DownloadManager theo redirect sang origin khác sẽ làm lộ cookie (tải cần đăng nhập → B03).
        WebSettings.getDefaultUserAgent(context)?.takeIf { it.isNotBlank() }?.let {
            dm.addRequestHeader("User-Agent", it)
        }
        manager.enqueue(dm)
        true
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: IllegalStateException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
