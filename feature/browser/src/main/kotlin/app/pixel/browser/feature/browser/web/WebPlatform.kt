package app.pixel.browser.feature.browser.web

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.SitePermission
import java.io.File
import java.util.UUID

/**
 * Xin quyền Android lúc chạy (spec 007 AC15). [request] trả tập quyền được cấp (rỗng nếu người dùng từ chối
 * hoặc đã chọn "Không hỏi lại"). [isGranted] cho biết quyền đã được cấp sẵn (khi đó không gọi [request]).
 */
fun interface RuntimePermissionGate {
    suspend fun request(androidPermissions: Set<String>): Set<String>

    fun isGranted(androidPermission: String): Boolean = false
}

/** Lựa chọn quyền theo origin, chỉ trong bộ nhớ của process (spec 007 AC16). Dùng trên main thread. */
class SitePermissionMemory {
    private val choices = HashMap<Pair<String, SitePermission>, Boolean>()

    fun get(
        origin: String,
        permission: SitePermission,
    ): Boolean? = choices[origin to permission]

    fun set(
        origin: String,
        permission: SitePermission,
        allowed: Boolean,
    ) {
        choices[origin to permission] = allowed
    }

    /** Xóa mọi lựa chọn (bộ nhớ quyền của tab ẩn danh khi hết tab ẩn danh; spec 016). */
    fun clear() {
        choices.clear()
    }
}

/** Cách mở trình chọn file cho một [EngineRequest.FileChooser] (spec 007 AC18). */
sealed interface FileChooserLaunch {
    data class GetContent(
        val mimeType: String,
    ) : FileChooserLaunch

    data class OpenMultiple(
        val mimeTypes: List<String>,
    ) : FileChooserLaunch

    data object TakePicture : FileChooserLaunch

    data object CaptureVideo : FileChooserLaunch
}

object FileChooserMapper {
    private const val ANY = "*/*"

    /**
     * `accept` → MIME (extension `.x` qua [extensionToMime], không biết thì bỏ). `capture` chỉ có nghĩa với đúng
     * một loại ảnh hoặc video; nhiều loại khi chọn một file → [ANY] (picker hệ thống chỉ nhận một MIME).
     */
    fun map(
        request: EngineRequest.FileChooser,
        extensionToMime: (String) -> String?,
    ): FileChooserLaunch {
        val types =
            request.acceptTypes
                .mapNotNull { if (it.startsWith(".")) extensionToMime(it) else it.takeIf { t -> '/' in t } }
                .distinct()
        val single = types.singleOrNull()
        return when {
            request.capture && single != null && single.startsWith("image/") -> FileChooserLaunch.TakePicture
            request.capture && single != null && single.startsWith("video/") -> FileChooserLaunch.CaptureVideo
            request.allowMultiple -> FileChooserLaunch.OpenMultiple(types.ifEmpty { listOf(ANY) })
            else -> FileChooserLaunch.GetContent(single ?: ANY)
        }
    }
}

/** MIME của extension `.pdf` theo [MimeTypeMap] của hệ thống. */
fun systemExtensionToMime(extension: String): String? =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.removePrefix(".").lowercase())

private const val CAPTURES_DIR = "captures"

/** URI `content://<applicationId>.fileprovider/captures/…` cho ảnh/video chụp, file nằm trong `cacheDir/captures`. */
fun createCaptureUri(
    context: Context,
    extension: String = ".jpg",
): Uri {
    val dir = File(context.cacheDir, CAPTURES_DIR).apply { mkdirs() }
    val file = File(dir, "capture-${UUID.randomUUID()}$extension").apply { createNewFile() }
    // Cùng định dạng với FileProvider.getUriForFile cho root `<cache-path name="captures" path="captures/">`
    // (xml/file_paths của :app). Dựng trực tiếp vì getUriForFile cache root tĩnh theo authority – sai khi cacheDir
    // đổi trong cùng process (Robolectric); FileProvider vẫn tự resolve URI này khi app camera ghi file.
    return Uri
        .Builder()
        .scheme("content")
        .authority("${context.packageName}.fileprovider")
        .appendPath(CAPTURES_DIR)
        .appendPath(file.name)
        .build()
}

/** Xóa file tạm của một lần chụp bị hủy (URI do [createCaptureUri] tạo); URI khác bị bỏ qua. */
fun deleteCapture(
    context: Context,
    uri: Uri,
) {
    val segments = uri.pathSegments
    if (uri.authority != "${context.packageName}.fileprovider" || segments.size != 2) return
    if (segments[0] != CAPTURES_DIR) return
    File(File(context.cacheDir, CAPTURES_DIR), segments[1]).takeIf { it.name == segments[1] }?.delete()
}

/**
 * Mở trình chọn file cho [request] bằng `ActivityResultRegistry` hiện tại (spec 007 AC19) và trả kết quả qua
 * [onResponse] (`Files`/`Dismiss`). Không có activity xử lý → [onResponse] `Dismiss()` rồi [onUnavailable].
 * Chụp ảnh/quay video cần quyền `CAMERA` (app khai báo quyền này); bị từ chối → chọn file ảnh/video thay thế.
 */
@Composable
fun FileChooserLauncher(
    request: EngineRequest.FileChooser,
    runtimePermissions: RuntimePermissionGate,
    onResponse: (EngineResponse) -> Unit,
    onUnavailable: () -> Unit,
    captureUri: (FileChooserLaunch) -> Uri,
    onCaptureDiscarded: (Uri) -> Unit = {},
) {
    val ownProvider = "${LocalContext.current.packageName}.fileprovider"
    val respond by rememberUpdatedState(onResponse)
    val discard by rememberUpdatedState(onCaptureDiscarded)
    val unavailable by rememberUpdatedState(onUnavailable)
    val makeCaptureUri by rememberUpdatedState(captureUri)
    // Sống qua xoay màn hình (request nằm trong session): không mở picker lần hai, vẫn biết URI đích của ảnh chụp.
    var launched by rememberSaveable(request.id) { mutableStateOf(false) }
    var captureTarget by rememberSaveable(request.id) { mutableStateOf<String?>(null) }

    fun files(uris: List<Uri>?) {
        // Chỉ content:// của app khác, hoặc đúng URI chụp do chính launcher tạo: không để trang đọc file riêng
        // của app (file://, đường dẫn trần, FileProvider của Pixel) qua kết quả picker.
        val allowed =
            uris.orEmpty().filter { uri ->
                uri.toString() == captureTarget ||
                    (uri.scheme.equals("content", ignoreCase = true) && uri.authority != ownProvider)
            }
        respond(if (allowed.isEmpty()) EngineResponse.Dismiss() else EngineResponse.Files(allowed.map(Uri::toString)))
    }

    fun captured(ok: Boolean) {
        val target = captureTarget?.toUri()
        if (!ok && target != null) discard(target)
        files(if (ok && target != null) listOf(target) else null)
    }
    val getContent =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { files(listOfNotNull(it)) }
    val openMultiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files(it) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured(it) }
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { captured(it) }

    LaunchedEffect(request.id) {
        if (launched) return@LaunchedEffect
        launched = true
        val launch = FileChooserMapper.map(request, ::systemExtensionToMime)
        try {
            when (launch) {
                is FileChooserLaunch.GetContent -> {
                    getContent.launch(launch.mimeType)
                }

                is FileChooserLaunch.OpenMultiple -> {
                    openMultiple.launch(launch.mimeTypes.toTypedArray())
                }

                FileChooserLaunch.TakePicture, FileChooserLaunch.CaptureVideo -> {
                    val camera = Manifest.permission.CAMERA
                    val granted =
                        runtimePermissions.isGranted(camera) || camera in runtimePermissions.request(setOf(camera))
                    val isPicture = launch == FileChooserLaunch.TakePicture
                    if (!granted) {
                        getContent.launch(if (isPicture) "image/*" else "video/*")
                    } else {
                        val uri = makeCaptureUri(launch)
                        captureTarget = uri.toString()
                        if (isPicture) takePicture.launch(uri) else captureVideo.launch(uri)
                    }
                }
            }
        } catch (_: ActivityNotFoundException) {
            respond(EngineResponse.Dismiss())
            unavailable()
        } catch (_: SecurityException) {
            respond(EngineResponse.Dismiss())
            unavailable()
        }
    }
}
