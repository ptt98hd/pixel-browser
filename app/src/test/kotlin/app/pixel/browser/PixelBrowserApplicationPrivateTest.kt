package app.pixel.browser

import app.pixel.browser.core.data.privacy.FakePrivacySettingsStore
import app.pixel.browser.core.data.privacy.PrivacySettings
import app.pixel.browser.core.data.privacy.PrivateAutoClose
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.engine.FakeProfileGateway
import app.pixel.browser.core.engine.PrivateSpace
import app.pixel.browser.core.engine.PrivateUnavailableException
import app.pixel.browser.core.engine.RetryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 016 AC6 (factory của app, fail-closed), AC24 (profile theo factory) và cài đặt riêng tư ở `:app`. */
@RunWith(RobolectricTestRunner::class)
class PixelBrowserApplicationPrivateTest {
    private val app: PixelBrowserApplication get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))

    private val requestedProfiles = mutableListOf<String?>()
    private val noRetry = RetryScheduler { _, _ -> }

    @After
    fun tearDown() = scope.cancel()

    private fun drainIo() = runBlocking { app.ioScope.launch {}.join() }

    private fun bindFactory() {
        requestedProfiles.clear()
        app.bindBaseSessionFactory { profile ->
            requestedProfiles += profile
            FakeEngineSession(isPrivate = profile != null)
        }
    }

    // --- AC6: fail-closed ở factory của app ---

    @Test
    fun ac6_privateSession_whenSpaceUnavailable_throwsPrivateUnavailable_withoutCreatingAnySession() {
        app.bindPrivateSpace(PrivateSpace(FakeProfileGateway(supported = false), scheduler = noRetry))
        bindFactory()
        assertThrows(PrivateUnavailableException::class.java) { app.sessionFactory.create(true) }
        assertTrue("no session (WebView) may be created", requestedProfiles.isEmpty())
    }

    @Test
    fun ac6_normalSession_isStillCreated_whenSpaceUnavailable() {
        app.bindPrivateSpace(PrivateSpace(FakeProfileGateway(supported = false), scheduler = noRetry))
        bindFactory()
        val session: EngineSession = app.sessionFactory.create(false)
        assertEquals(listOf<String?>(null), requestedProfiles)
        assertEquals(false, session.isPrivate)
    }

    // --- AC24: factory gắn đúng profile ---

    @Test
    fun ac24_privateSession_getsPrivateProfile_andSameNameWhileSpaceIsAlive() {
        app.bindPrivateSpace(PrivateSpace(FakeProfileGateway(), scheduler = noRetry))
        bindFactory()
        val first = app.sessionFactory.create(true)
        val second = app.sessionFactory.create(true)
        assertTrue(first.isPrivate && second.isPrivate)
        val names = requestedProfiles.map { checkNotNull(it) }
        assertTrue(names.all { it.startsWith("private_") })
        assertEquals(names[0], names[1])
    }

    @Test
    fun ac24_normalSession_getsNoProfile() {
        app.bindPrivateSpace(PrivateSpace(FakeProfileGateway(), scheduler = noRetry))
        bindFactory()
        app.sessionFactory.create(false)
        assertEquals(listOf<String?>(null), requestedProfiles)
    }

    @Test
    fun ac24_afterWipe_nextPrivateSession_getsNewProfileName() {
        val space = PrivateSpace(FakeProfileGateway(), scheduler = noRetry)
        app.bindPrivateSpace(space)
        bindFactory()
        app.sessionFactory.create(true)
        space.wipe()
        app.sessionFactory.create(true)
        assertNotEquals(requestedProfiles[0], requestedProfiles[1])
    }

    // --- cài đặt riêng tư ---

    @Test
    fun privacy_beforeLoad_isDefaults_afterLoad_isFromStore() {
        app.bindPrivacyStore(FakePrivacySettingsStore(PrivacySettings(false, PrivateAutoClose.Minutes30)), scope)
        assertEquals(PrivacySettings(), app.privacySettings.value)
        scheduler.advanceUntilIdle()
        assertEquals(PrivacySettings(false, PrivateAutoClose.Minutes30), app.privacySettings.value)
    }

    @Test
    fun privacy_update_setsValueImmediately_andWritesStoreOnce() {
        val store = FakePrivacySettingsStore()
        app.bindPrivacyStore(store, scope)
        scheduler.advanceUntilIdle()

        app.updatePrivacy { it.copy(privateAutoClose = PrivateAutoClose.Minutes5) }
        assertEquals(PrivateAutoClose.Minutes5, app.privacySettings.value.privateAutoClose)

        drainIo()
        assertEquals(listOf(PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes5)), store.updates)
    }

    @Test
    fun privacy_update_toSameValue_doesNotWrite() {
        val store = FakePrivacySettingsStore()
        app.bindPrivacyStore(store, scope)
        scheduler.advanceUntilIdle()
        app.updatePrivacy { it }
        drainIo()
        assertTrue(store.updates.isEmpty())
    }
}
