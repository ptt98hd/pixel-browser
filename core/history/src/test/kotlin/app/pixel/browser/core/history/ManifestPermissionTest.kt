package app.pixel.browser.core.history

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
        assertTrue("unexpected: ${info.requestedPermissions?.toList()}", info.requestedPermissions.isNullOrEmpty())
    }
}
