package app.pixel.browser.feature.browser

import app.pixel.browser.core.data.privacy.PrivateAutoClose
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 016 AC15 – [PrivateAutoCloseGuard] với đồng hồ giả (JVM). */
class PrivateAutoCloseGuardTest {
    private var now = 1_000_000L
    private var setting = PrivateAutoClose.Minutes5
    private val guard = PrivateAutoCloseGuard({ now }, { setting })

    private val second = 1_000L
    private val minute = 60 * second

    @Test
    fun ac15_minutes5_returnsFalseJustBeforeFiveMinutes() {
        guard.onBackground(hasPrivateTabs = true, isChangingConfigurations = false)
        now += 4 * minute + 59 * second
        assertFalse(guard.onForeground())
    }

    @Test
    fun ac15_minutes5_returnsTrueAtFiveMinutes_onlyOnce() {
        guard.onBackground(hasPrivateTabs = true, isChangingConfigurations = false)
        now += 5 * minute
        assertTrue(guard.onForeground())
        assertFalse("second call must not close again", guard.onForeground())
    }

    @Test
    fun ac15_minutes5_returnsTrueAfterLongAbsence() {
        guard.onBackground(true, false)
        now += 3 * 60 * minute
        assertTrue(guard.onForeground())
    }

    @Test
    fun ac15_minutes30_boundaries() {
        setting = PrivateAutoClose.Minutes30
        guard.onBackground(true, false)
        now += 29 * minute + 59 * second
        assertFalse(guard.onForeground())
        guard.onBackground(true, false)
        now += 30 * minute
        assertTrue(guard.onForeground())
    }

    @Test
    fun ac15_never_isAlwaysFalse() {
        setting = PrivateAutoClose.Never
        guard.onBackground(true, false)
        now += 24 * 60 * minute
        assertFalse(guard.onForeground())
    }

    @Test
    fun ac15_noPrivateTabs_doesNotRecordMark() {
        guard.onBackground(hasPrivateTabs = false, isChangingConfigurations = false)
        now += 60 * minute
        assertFalse(guard.onForeground())
    }

    @Test
    fun ac15_configurationChange_doesNotRecordMark() {
        guard.onBackground(hasPrivateTabs = true, isChangingConfigurations = true)
        now += 60 * minute
        assertFalse(guard.onForeground())
    }

    @Test
    fun ac15_foregroundWithoutBackground_isFalse() {
        now += 60 * minute
        assertFalse(guard.onForeground())
    }

    @Test
    fun ac15_newBackgroundAfterReturn_startsFreshWindow() {
        guard.onBackground(true, false)
        now += minute
        assertFalse(guard.onForeground())
        guard.onBackground(true, false)
        now += 4 * minute
        assertFalse("window restarts from the second background", guard.onForeground())
    }
}
