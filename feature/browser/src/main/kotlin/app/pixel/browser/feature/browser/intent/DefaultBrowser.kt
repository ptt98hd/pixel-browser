package app.pixel.browser.feature.browser.intent

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

interface DefaultBrowserController {
    val isRoleAvailable: Boolean

    fun isDefault(): Boolean

    /** Intent của RoleManager nếu khả dụng, ngược lại `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`. */
    fun requestIntent(): Intent
}

/** Intent mở màn hình ứng dụng mặc định của hệ thống (fallback khi RoleManager không dùng được). */
fun defaultAppsSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

/** [DefaultBrowserController] dựa trên `RoleManager.ROLE_BROWSER` (spec 006 AC18–AC19). */
class RoleManagerDefaultBrowser(
    context: Context,
) : DefaultBrowserController {
    private val roleManager: RoleManager? = context.getSystemService(RoleManager::class.java)

    override val isRoleAvailable: Boolean
        get() = roleManager?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true

    override fun isDefault(): Boolean = roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true

    override fun requestIntent(): Intent {
        val rm = roleManager
        return if (rm != null && isRoleAvailable) {
            rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
        } else {
            defaultAppsSettingsIntent()
        }
    }
}
