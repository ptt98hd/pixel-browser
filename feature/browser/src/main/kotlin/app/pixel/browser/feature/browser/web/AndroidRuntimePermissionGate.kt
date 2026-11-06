package app.pixel.browser.feature.browser.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * [RuntimePermissionGate] thật (spec 007 AC15). Giữ application context, không giữ Activity: Activity đang hiển
 * thị gắn launcher `RequestMultiplePermissions` qua [attach] và chuyển kết quả về [onResult]. Kết quả hộp thoại
 * hệ thống vẫn tới được sau khi Activity bị tạo lại (Activity Result API phát lại cho launcher mới).
 * Không có Activity gắn → không hỏi, chỉ trả các quyền đã có. Dùng trên main thread.
 */
class AndroidRuntimePermissionGate(
    context: Context,
) : RuntimePermissionGate {
    private val appContext = context.applicationContext
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Map<String, Boolean>>? = null

    fun attach(launcher: ActivityResultLauncher<Array<String>>) {
        this.launcher = launcher
    }

    fun detach(launcher: ActivityResultLauncher<Array<String>>) {
        if (this.launcher === launcher) this.launcher = null
    }

    fun onResult(result: Map<String, Boolean>) {
        pending?.complete(result)
        pending = null
    }

    override fun isGranted(androidPermission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, androidPermission) == PackageManager.PERMISSION_GRANTED

    override suspend fun request(androidPermissions: Set<String>): Set<String> {
        val current = launcher
        if (current != null && androidPermissions.isNotEmpty()) {
            // Mỗi lúc một hộp thoại hệ thống: yêu cầu cũ còn treo coi như bị từ chối.
            pending?.complete(emptyMap())
            val deferred = CompletableDeferred<Map<String, Boolean>>()
            pending = deferred
            val launched =
                try {
                    current.launch(androidPermissions.toTypedArray())
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            if (launched) {
                deferred.await()
            } else if (pending === deferred) {
                pending = null
            }
        }
        return androidPermissions.filterTo(mutableSetOf(), ::isGranted)
    }
}
