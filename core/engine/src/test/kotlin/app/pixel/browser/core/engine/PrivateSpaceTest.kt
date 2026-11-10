package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 016 AC1–AC3 – vòng đời profile ẩn danh (JVM, `FakeProfileGateway` + scheduler giả). */
class PrivateSpaceTest {
    /** Ghi lại độ trễ mỗi lần lên lịch; test tự chạy khối lệnh theo thứ tự. */
    private class FakeScheduler : RetryScheduler {
        val delays = mutableListOf<Long>()
        private val pending = ArrayDeque<() -> Unit>()

        override fun schedule(
            delayMs: Long,
            block: () -> Unit,
        ) {
            delays += delayMs
            pending.addLast(block)
        }

        val hasPending: Boolean get() = pending.isNotEmpty()

        fun runNext() = pending.removeFirst().invoke()
    }

    private val scheduler = FakeScheduler()
    private var counter = 0
    private val newName: () -> String = { "private_new${++counter}" }

    private fun space(gateway: FakeProfileGateway) = PrivateSpace(gateway, newName, scheduler)

    /** Acquire rồi mô phỏng WebView đã `apply` profile (profile bắt đầu tồn tại trong gateway). */
    private fun PrivateSpace.acquireApplied(gateway: FakeProfileGateway): String =
        acquireProfile()!!.also { gateway.names += it }

    // --- AC1 ---

    @Test
    fun ac1_firstAcquire_returnsNameStartingWithPrivatePrefix() {
        val space = PrivateSpace(FakeProfileGateway(), scheduler = scheduler)
        val name = space.acquireProfile()
        assertNotNull(name)
        assertTrue(name!!, name.startsWith("private_"))
        assertTrue("default names must be random, not just the prefix", name.length > "private_".length)
    }

    @Test
    fun ac1_secondAcquire_withoutWipe_returnsSameName() {
        val space = space(FakeProfileGateway())
        val first = space.acquireProfile()
        assertEquals(first, space.acquireProfile())
    }

    @Test
    fun ac1_unsupportedGateway_isUnavailable_andNeverTouched() {
        val gateway = FakeProfileGateway(supported = false)
        val space = space(gateway)
        assertFalse(space.isAvailable)
        assertNull(space.acquireProfile())
        space.wipe()
        assertEquals(emptyList<String>(), gateway.calls)
    }

    @Test
    fun ac1_supportedGateway_isAvailable() {
        assertTrue(space(FakeProfileGateway()).isAvailable)
    }

    // --- AC2 ---

    @Test
    fun ac2_firstAcquire_sweepsOldPrivateProfiles_onlyThose() {
        val gateway = FakeProfileGateway(names = listOf("Default", "private_old1", "private_old2", "work"))
        val space = space(gateway)
        space.acquireProfile()
        assertEquals(listOf("private_old1", "private_old2"), gateway.deleted)
        assertTrue("Default" in gateway.names)
        assertTrue("work" in gateway.names)
    }

    @Test
    fun ac2_secondAcquire_doesNotSweepAgain() {
        val gateway = FakeProfileGateway(names = listOf("Default"))
        val space = space(gateway)
        space.acquireProfile()
        gateway.names += "private_late"
        space.acquireProfile()
        assertEquals(emptyList<String>(), gateway.deleted)
        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun ac2_acquireAfterWipe_doesNotSweepAgain() {
        val gateway = FakeProfileGateway(names = listOf("Default"))
        val space = space(gateway)
        space.acquireProfile()
        space.wipe()
        gateway.names += "private_late"
        gateway.deleted.clear()
        space.acquireProfile()
        assertEquals(emptyList<String>(), gateway.deleted)
    }

    @Test
    fun ac2_sweepDeleteReturnsFalse_doesNotThrow_andIsPending() {
        val gateway = FakeProfileGateway(names = listOf("Default", "private_old1", "private_old2"))
        gateway.failDeleteTimes = 1
        val space = space(gateway)
        val name = space.acquireProfile()
        assertNotNull(name)
        assertEquals(1, space.pendingWipeCount)
        assertEquals(listOf("private_old1", "private_old2"), gateway.deleted)
    }

    @Test
    fun ac2_sweepDeleteThrows_doesNotThrow_andBothArePending() {
        val gateway = FakeProfileGateway(names = listOf("Default", "private_old1", "private_old2"))
        gateway.deleteThrows = IllegalStateException("in use")
        val space = space(gateway)
        assertNotNull(space.acquireProfile())
        assertEquals(2, space.pendingWipeCount)
    }

    @Test
    fun ac2_pendingSweepNames_areRetriedOnNextAcquire() {
        val gateway = FakeProfileGateway(names = listOf("Default", "private_old1"))
        gateway.failDeleteTimes = 1
        val space = space(gateway)
        space.acquireProfile()
        assertEquals(1, space.pendingWipeCount)
        space.acquireProfile()
        assertEquals(0, space.pendingWipeCount)
        assertFalse("private_old1" in gateway.names)
    }

    // --- AC3 ---

    @Test
    fun ac3_wipe_deletesCurrentName_andNextAcquireReturnsDifferentName() {
        val gateway = FakeProfileGateway()
        val space = space(gateway)
        val first = space.acquireProfile()!!
        space.wipe()
        assertEquals(listOf(first), gateway.deleted)
        val second = space.acquireProfile()!!
        assertNotEquals(first, second)
        assertTrue(second.startsWith("private_"))
        assertEquals(0, space.pendingWipeCount)
    }

    @Test
    fun ac3_wipeFails_retriesExactlyOnBackoff_thenGoesPending() {
        val gateway = FakeProfileGateway()
        gateway.failDeleteTimes = Int.MAX_VALUE
        val space = space(gateway)
        val name = space.acquireProfile()!!

        space.wipe()
        assertEquals(listOf(250L), scheduler.delays)
        scheduler.runNext()
        assertEquals(listOf(250L, 1000L), scheduler.delays)
        scheduler.runNext()
        assertEquals(listOf(250L, 1000L, 5000L), scheduler.delays)
        scheduler.runNext()

        assertFalse("no more retries after the backoff list is exhausted", scheduler.hasPending)
        assertEquals(listOf(250L, 1000L, 5000L), scheduler.delays)
        assertEquals(1, space.pendingWipeCount)
        assertEquals("1 immediate attempt + 3 retries", List(4) { name }, gateway.deleted)
    }

    @Test
    fun ac3_pendingName_isDeletedOnNextAcquire_withNewName() {
        val gateway = FakeProfileGateway()
        gateway.failDeleteTimes = Int.MAX_VALUE
        val space = space(gateway)
        val old = space.acquireApplied(gateway)
        space.wipe()
        repeat(3) { scheduler.runNext() }
        assertEquals(1, space.pendingWipeCount)

        gateway.failDeleteTimes = 0
        val fresh = space.acquireProfile()!!

        assertNotEquals(old, fresh)
        assertEquals(0, space.pendingWipeCount)
        assertFalse(old in gateway.names)
    }

    @Test
    fun ac3_wipeSucceedsMidway_stopsRetrying() {
        val gateway = FakeProfileGateway()
        gateway.failDeleteTimes = 2
        val space = space(gateway)
        val name = space.acquireProfile()!!

        space.wipe()
        scheduler.runNext()
        scheduler.runNext()

        assertEquals(listOf(250L, 1000L), scheduler.delays)
        assertFalse(scheduler.hasPending)
        assertEquals(0, space.pendingWipeCount)
        assertEquals(List(3) { name }, gateway.deleted)
    }

    @Test
    fun ac3_wipeBeforeAnyAcquire_isNoOp() {
        val gateway = FakeProfileGateway()
        val space = space(gateway)
        space.wipe()
        assertEquals(emptyList<String>(), gateway.calls)
        assertTrue(scheduler.delays.isEmpty())
        assertEquals(0, space.pendingWipeCount)
    }

    @Test
    fun ac3_wipeTwiceInARow_secondIsNoOp() {
        val gateway = FakeProfileGateway()
        val space = space(gateway)
        space.acquireProfile()
        space.wipe()
        space.wipe()
        assertEquals(1, gateway.deleted.size)
    }

    @Test
    fun ac3_namesAreNeverReused_acrossManyCycles() {
        val gateway = FakeProfileGateway()
        val space = space(gateway)
        val names = (1..5).map { space.acquireProfile()!!.also { space.wipe() } }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun ac3_retryRunsAfterNewAcquire_doesNotDeleteTheNewProfile() {
        val gateway = FakeProfileGateway()
        gateway.failDeleteTimes = 1
        val space = space(gateway)
        val old = space.acquireApplied(gateway)
        space.wipe()
        val fresh = space.acquireApplied(gateway)
        scheduler.runNext()
        assertNotEquals(old, fresh)
        assertTrue("the live profile must never be deleted by a stale retry", fresh in gateway.names)
        assertFalse(old in gateway.names)
    }

    // --- tên profile & quét độc lập (review 016) ---

    @Test
    fun name_default_isPrivateUnderscoreFollowedBy32Hex() {
        val name = PrivateSpace(FakeProfileGateway(), scheduler = scheduler).acquireProfile()!!
        assertTrue(name, Regex("private_[0-9a-f]{32}").matches(name))
    }

    @Test
    fun sweepStale_deletesOnlyOldPrivateProfiles_withoutOpeningAnyTab() {
        val gateway = FakeProfileGateway(names = listOf("Default", "private_old1", "private-dash", "work"))
        val space = space(gateway)
        space.sweepStale()
        assertEquals(listOf("private_old1"), gateway.deleted)
        assertTrue(gateway.appliedProfiles.isEmpty())
    }

    @Test
    fun sweepStale_runsOnlyOnce_andAcquireDoesNotSweepAgain() {
        val gateway = FakeProfileGateway(names = listOf("Default", "private_old1"))
        val space = space(gateway)
        space.sweepStale()
        space.sweepStale()
        space.acquireProfile()
        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun sweepStale_whenUnsupported_neverTouchesGateway() {
        val gateway = FakeProfileGateway(supported = false)
        space(gateway).sweepStale()
        assertEquals(emptyList<String>(), gateway.calls)
    }

    @Test
    fun sweepStale_listFailure_isRetriedLater_byNextSweepAndByAcquire() {
        val real = FakeProfileGateway(names = listOf("Default", "private_old1"))
        var failList = true
        val gateway =
            object : ProfileGateway by real {
                override fun listProfileNames(): List<String> {
                    if (failList) throw IllegalStateException("provider not ready")
                    return real.listProfileNames()
                }
            }
        val space = PrivateSpace(gateway, newName, scheduler)
        space.sweepStale()
        assertTrue(real.deleted.isEmpty())

        failList = false
        space.acquireProfile()
        assertEquals(listOf("private_old1"), real.deleted)
    }

    @Test
    fun sweepStale_neverDeletesTheCurrentProfile() {
        val gateway = FakeProfileGateway()
        val space = space(gateway)
        val name = space.acquireApplied(gateway)
        space.sweepStale()
        assertTrue(name in gateway.names)
    }
}
