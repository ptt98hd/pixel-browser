package app.pixel.browser.core.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 011 AC7 – [LocalReducedMotion] theo ANIMATOR_DURATION_SCALE và [pixelSpring]. */
@RunWith(RobolectricTestRunner::class)
class ReducedMotionTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Cùng tham số với `PixelMotion.spatial()` nhưng không phụ thuộc token (AC2 test riêng). */
    private val input = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)

    private var reduced: Boolean? = null
    private var resolved: FiniteAnimationSpec<Float>? = null

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(rule.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun show() {
        rule.setContent {
            PixelTheme {
                reduced = LocalReducedMotion.current
                resolved = pixelSpring(input)
            }
        }
        rule.waitForIdle()
    }

    /** Rời rồi quay lại RESUMED → phát ON_RESUME. */
    private fun pauseAndResume() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    private fun assertReduced() {
        assertEquals(true, reduced)
        assertTrue("pixelSpring phải là SnapSpec khi reduced motion, là $resolved", resolved is SnapSpec<*>)
    }

    private fun assertNotReduced() {
        assertEquals(false, reduced)
        assertSame(input, resolved)
    }

    @Test
    fun ac7_animatorScaleZero_reducedMotion_andSnap() {
        setAnimatorScale(0f)
        show()
        assertReduced()
    }

    @Test
    fun ac7_animatorScaleOne_notReduced_andReturnsSameSpring() {
        setAnimatorScale(1f)
        show()
        assertNotReduced()
    }

    @Test
    fun ac7_settingChangedToZero_thenResume_updatesToReduced() {
        setAnimatorScale(1f)
        show()
        assertNotReduced()

        setAnimatorScale(0f)
        pauseAndResume()
        assertReduced()
    }

    @Test
    fun ac7_settingChangedToOne_thenResume_updatesToNotReduced() {
        setAnimatorScale(0f)
        show()
        assertReduced()

        setAnimatorScale(1f)
        pauseAndResume()
        assertNotReduced()
    }
}
