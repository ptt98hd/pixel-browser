package app.pixel.browser.feature.browser.intent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Spec 006 – AC26: [AndroidExternalAppLauncher] (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class AndroidExternalAppLauncherTest {
    private lateinit var activity: Activity
    private lateinit var launcher: AndroidExternalAppLauncher

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        launcher = AndroidExternalAppLauncher(activity)
    }

    @Test
    fun ac26_tel_startsViewActivity_andReportsLaunched() {
        val result = launcher.launch("tel:123")
        assertEquals(ExternalLaunchResult.Launched, result)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse("tel:123"), started.data)
        assertTrue(started.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(started.component)
    }

    @Test
    fun ac26_noActivityFound_returnsNotFoundWithParsedFallback() {
        // Bật kiểm tra để startActivity ném ActivityNotFoundException khi không có app xử lý.
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        val uri =
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;" +
                "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fapp;end"
        assertEquals(ExternalLaunchResult.NotFound("https://example.com/app"), launcher.launch(uri))
    }

    @Test
    fun ac26_noActivityFound_withoutFallback_returnsNotFoundNull() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        assertEquals(ExternalLaunchResult.NotFound(null), launcher.launch("market://details?id=x"))
    }

    @Test
    fun ac26_rejectedUri_returnsRejected_andStartsNothing() {
        val own = activity.packageName
        for (uri in listOf(
            "intent://x#Intent;scheme=myapp;package=$own;end",
            "file:///sdcard/a",
            "javascript:alert(1)",
        )) {
            assertEquals(uri, ExternalLaunchResult.Rejected, launcher.launch(uri))
        }
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun ac26_intentUriPointingToWeb_returnsOpenInBrowser_andStartsNothing() {
        assertEquals(
            ExternalLaunchResult.OpenInBrowser("https://example.com/a"),
            launcher.launch("intent://example.com/a#Intent;scheme=https;end"),
        )
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
