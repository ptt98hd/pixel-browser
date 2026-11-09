package app.pixel.browser.feature.bookmarks

import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 013 AC29 – module không khai báo quyền nào (INTERNET, READ/WRITE_EXTERNAL_STORAGE). */
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
