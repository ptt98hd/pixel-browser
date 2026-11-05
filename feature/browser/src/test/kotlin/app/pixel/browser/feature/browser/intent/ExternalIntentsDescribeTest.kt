package app.pixel.browser.feature.browser.intent

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Spec 006 review: mô tả hiển thị phải lấy từ cùng parse mà launcher dùng để mở. */
@RunWith(RobolectricTestRunner::class)
class ExternalIntentsDescribeTest {
    private val own = "app.pixel.browser"

    private fun describe(uri: String) = ExternalIntents.describe(uri, own)

    @Test
    fun packageAfterSelector_belongsToSelector_andIsNotShown() {
        val uri = "intent://x#Intent;scheme=evil;SEL;package=com.mybank;end"
        val parsed = ExternalIntents.parse(uri, own)!!
        assertNull(parsed.intent!!.`package`)
        assertEquals(ExternalTarget(scheme = "evil", packageName = null), describe(uri))
    }

    @Test
    fun duplicatePackage_showsThePackageThatLaunches() {
        val uri = "intent://x#Intent;scheme=zxing;package=com.mybank;package=com.evil;end"
        val launched = ExternalIntents.parse(uri, own)!!.intent!!.`package`
        assertEquals(ExternalTarget(scheme = "zxing", packageName = launched), describe(uri))
    }

    @Test
    fun plainScheme_isDataScheme_andIntentUriShowsDataSchemeNotIntent() {
        assertEquals(ExternalTarget("tel", null), describe("tel:123"))
        assertEquals(
            ExternalTarget("zxing", "com.google.zxing.client.android"),
            describe("intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"),
        )
    }

    @Test
    fun rejectedUri_hasNoDescription() {
        assertNull(describe("intent://x#Intent;scheme=myapp;package=$own;end"))
        assertNull(describe("javascript:alert(1)"))
    }

    @Test
    fun uppercaseIntentScheme_isParsedAsIntentUri() {
        val parsed = ExternalIntents.parse("INTENT://scan/#Intent;scheme=zxing;package=com.x;end", own)
        assertEquals("zxing://scan/", parsed?.intent?.dataString)
        assertEquals("com.x", parsed?.intent?.`package`)
    }

    @Test
    fun forbiddenSchemeWithSurroundingSpaces_isRejected() {
        assertNull(ExternalIntents.parse("intent://x/y#Intent;scheme=%20file%20;end", own))
    }

    @Test
    fun androidLauncher_describe_usesSameParse() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val launcher = AndroidExternalAppLauncher(activity)
        val uri = "intent://x#Intent;scheme=evil;SEL;package=com.mybank;end"
        assertEquals(ExternalIntents.describe(uri, activity.packageName), launcher.describe(uri))
        assertNull(launcher.describe("intent://x#Intent;scheme=a;package=${activity.packageName};end"))
    }
}
