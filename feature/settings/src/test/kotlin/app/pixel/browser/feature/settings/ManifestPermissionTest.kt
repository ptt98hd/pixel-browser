package app.pixel.browser.feature.settings

import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 014 AC20 – module không khai báo quyền nào (đặc biệt INTERNET). Test bảo vệ: xanh ngay từ đầu. */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionTest {
    @Test
    fun mergedManifest_hasNoUsesPermission() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val requested =
            info.requestedPermissions.orEmpty().filterNot {
                it == "android.permission.REORDER_TASKS" || it.endsWith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            }
        assertTrue("unexpected: $requested", requested.isEmpty())
    }
}
