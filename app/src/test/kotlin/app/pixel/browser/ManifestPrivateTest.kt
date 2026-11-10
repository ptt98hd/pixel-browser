package app.pixel.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 016 AC26 – tab ẩn danh không thêm quyền, service hay receiver (thông báo sẽ lộ phiên ẩn danh). */
@RunWith(RobolectricTestRunner::class)
class ManifestPrivateTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Suppress("DEPRECATION")
    private fun packageInfo() =
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS,
        )

    @Test
    fun ac26_requestedPermissions_areExactlyTheSpec007Set() {
        val requested =
            packageInfo()
                .requestedPermissions
                .orEmpty()
                .filterNot { it.startsWith(context.packageName) } // quyền động nội bộ do thư viện thêm
                .toSet()
        assertEquals(
            setOf(
                Manifest.permission.INTERNET,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            requested,
        )
    }

    @Test
    fun ac26_noNotificationOrForegroundServicePermission() {
        val requested = packageInfo().requestedPermissions.orEmpty().toList()
        assertTrue(requested.none { it == "android.permission.POST_NOTIFICATIONS" })
        assertTrue(requested.none { it.startsWith("android.permission.FOREGROUND_SERVICE") })
    }

    @Test
    fun ac26_appDeclaresNoServiceOrReceiver() {
        val info = packageInfo()
        val own = { name: String -> name.startsWith("app.pixel.browser") }
        assertTrue(info.services.orEmpty().none { own(it.name) })
        assertTrue(info.receivers.orEmpty().none { own(it.name) })
    }
}
