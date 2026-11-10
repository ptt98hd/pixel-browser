package app.pixel.browser.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 016 AC16 – bảng chân trị của [SecureWindowPolicy] (JVM). */
class SecureWindowPolicyTest {
    private val bools = listOf(false, true)

    @Test
    fun ac16_blockScreenshotsOff_isNeverSecure() {
        for (selectedPrivate in bools) {
            for (switcherOpen in bools) {
                for (switcherPrivate in bools) {
                    assertEquals(
                        "private=$selectedPrivate open=$switcherOpen group=$switcherPrivate",
                        false,
                        SecureWindowPolicy.shouldSecure(false, selectedPrivate, switcherOpen, switcherPrivate),
                    )
                }
            }
        }
    }

    @Test
    fun ac16_blockScreenshotsOn_followsFormula_forAllCombinations() {
        for (selectedPrivate in bools) {
            for (switcherOpen in bools) {
                for (switcherPrivate in bools) {
                    val expected = selectedPrivate || (switcherOpen && switcherPrivate)
                    assertEquals(
                        "private=$selectedPrivate open=$switcherOpen group=$switcherPrivate",
                        expected,
                        SecureWindowPolicy.shouldSecure(true, selectedPrivate, switcherOpen, switcherPrivate),
                    )
                }
            }
        }
    }

    @Test
    fun ac16_normalTab_withClosedSwitcher_isNotSecure() {
        assertEquals(false, SecureWindowPolicy.shouldSecure(true, false, false, false))
    }

    @Test
    fun ac16_normalTab_withSwitcherShowingPrivateGroup_isSecure() {
        assertEquals(true, SecureWindowPolicy.shouldSecure(true, false, true, true))
    }

    @Test
    fun ac16_switcherGroupFlag_isIgnoredWhileSwitcherIsClosed() {
        assertEquals(false, SecureWindowPolicy.shouldSecure(true, false, false, true))
    }
}
