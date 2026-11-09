package app.pixel.browser.feature.history

import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 012 AC21 – module không khai báo quyền nào (đặc biệt INTERNET). */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionTest {
    @Test
    fun mergedManifest_hasNoUsesPermission() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        // Manifest của compose ui-test (debug) thêm quyền chỉ dành cho test; bỏ qua chúng.
        val requested =
            info.requestedPermissions.orEmpty().filterNot {
                it == "android.permission.REORDER_TASKS" || it.endsWith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            }
        assertTrue("unexpected: $requested", requested.isEmpty())
    }
}
