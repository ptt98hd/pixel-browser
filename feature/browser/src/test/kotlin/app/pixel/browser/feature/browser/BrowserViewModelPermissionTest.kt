package app.pixel.browser.feature.browser

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CAMERA
import android.Manifest.permission.RECORD_AUDIO
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.engine.SitePermission
import app.pixel.browser.core.engine.SitePermission.Camera
import app.pixel.browser.core.engine.SitePermission.Location
import app.pixel.browser.core.engine.SitePermission.Microphone
import app.pixel.browser.feature.browser.web.SitePermissionMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 007 – AC15–AC17: quyết định quyền theo site trong ViewModel (JVM, gate giả). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelPermissionTest {
    private lateinit var f: BrowserFixture
    private val gate get() = f.gate

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = BrowserFixture()
        f.selected().visit("https://a.com/")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fake: FakeEngineSession get() = f.selected().fake

    /** Trang hiện tại xin [permissions]; trả request mà UI đang hiện (nếu có). */
    private fun ask(
        vararg permissions: SitePermission,
        origin: String? = null,
    ): EngineRequest.PermissionPrompt? {
        if (origin ==
            null
        ) {
            fake.simulatePermissionRequest(permissions.toSet())
        } else {
            fake.simulatePermissionRequest(permissions.toSet(), origin)
        }
        return f.state().pendingRequest as? EngineRequest.PermissionPrompt
    }

    private fun lastResponse(): EngineResponse? = fake.responses.maxByOrNull { it.key }?.value

    // --- AC15 ---

    @Test
    fun ac15_prompt_isShownForSecureOrigin() {
        val p = ask(Camera, Microphone)
        assertNotNull(p)
        assertEquals("https://a.com", p!!.origin)
        assertEquals(setOf(Camera, Microphone), p.permissions)
    }

    @Test
    fun ac15_allow_requestsMissingOsPermissions_thenGrantsAll() {
        val p = ask(Camera, Microphone)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(listOf(setOf(CAMERA, RECORD_AUDIO)), gate.requests)
        assertEquals(EngineResponse.Grant(setOf(Camera, Microphone)), fake.responses[p.id])
        assertNull(f.state().pendingRequest)
    }

    @Test
    fun ac15_allow_onlyRequestsOsPermissionsNotYetGranted() {
        gate.alreadyGranted += CAMERA
        val p = ask(Camera, Microphone)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(listOf(setOf(RECORD_AUDIO)), gate.requests)
        assertEquals(EngineResponse.Grant(setOf(Camera, Microphone)), fake.responses[p.id])
    }

    @Test
    fun ac15_allow_withAllOsPermissionsGranted_doesNotCallGate() {
        gate.alreadyGranted += setOf(CAMERA, RECORD_AUDIO)
        val p = ask(Camera, Microphone)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertTrue(gate.requests.isEmpty())
        assertEquals(EngineResponse.Grant(setOf(Camera, Microphone)), fake.responses[p.id])
    }

    @Test
    fun ac15_osDeniesCamera_grantsMicrophoneOnly_andShowsMessage() {
        gate.grantOnRequest = { it - CAMERA }
        val p = ask(Camera, Microphone)!!
        assertNull(f.state().message)
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(EngineResponse.Grant(setOf(Microphone)), fake.responses[p.id])
        assertNotNull(f.state().message)
    }

    @Test
    fun edge_osDeniesEverything_grantsEmptySet_andShowsMessage() {
        gate.grantOnRequest = { emptySet() }
        val p = ask(Camera)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(EngineResponse.Grant(emptySet()), fake.responses[p.id])
        assertNotNull(f.state().message)
    }

    @Test
    fun ac15_block_dismisses_withoutCallingGate() {
        val p = ask(Camera)!!
        f.viewModel.onPermissionDecision(p.id, allow = false)
        assertEquals(EngineResponse.Dismiss(), fake.responses[p.id])
        assertTrue(gate.requests.isEmpty())
    }

    @Test
    fun ac15_location_requestsFineAndCoarse() {
        val p = ask(Location)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(listOf(setOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)), gate.requests)
        assertEquals(EngineResponse.Grant(setOf(Location)), fake.responses[p.id])
    }

    @Test
    fun ac15_location_coarseOnly_stillGrantsLocation() {
        gate.grantOnRequest = { setOf(ACCESS_COARSE_LOCATION) }
        val p = ask(Location)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(EngineResponse.Grant(setOf(Location)), fake.responses[p.id])
    }

    @Test
    fun ac15_location_coarseAlreadyGranted_doesNotCallGate() {
        gate.alreadyGranted += ACCESS_COARSE_LOCATION
        val p = ask(Location)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertTrue(gate.requests.isEmpty())
        assertEquals(EngineResponse.Grant(setOf(Location)), fake.responses[p.id])
    }

    // --- AC16 ---

    @Test
    fun ac16_decision_isRemembered() {
        val p = ask(Camera)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(true, f.permissionMemory.get("https://a.com", Camera))
        val q = ask(Microphone)!!
        f.viewModel.onPermissionDecision(q.id, allow = false)
        assertEquals(false, f.permissionMemory.get("https://a.com", Microphone))
    }

    @Test
    fun ac16_allowInOneTab_autoGrantsInAnotherTab_withoutDialog() {
        val p = ask(Camera)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        f.viewModel.onNewTab()
        f.selected().visit("https://a.com/other")
        gate.requests.clear()

        fake.simulatePermissionRequest(setOf(Camera))
        assertNull(f.state().pendingRequest)
        assertEquals(EngineResponse.Grant(setOf(Camera)), lastResponse())
        assertTrue(gate.requests.isEmpty())
    }

    @Test
    fun ac16_rememberedAllow_stillAsksOsWhenPermissionMissing() {
        f.permissionMemory.set("https://a.com", Camera, true)
        fake.simulatePermissionRequest(setOf(Camera))
        assertNull(f.state().pendingRequest)
        assertEquals(listOf(setOf(CAMERA)), gate.requests)
        assertEquals(EngineResponse.Grant(setOf(Camera)), lastResponse())
    }

    @Test
    fun ac16_rememberedBlock_autoDismisses_withoutDialog() {
        f.permissionMemory.set("https://a.com", Camera, false)
        fake.simulatePermissionRequest(setOf(Camera))
        assertNull(f.state().pendingRequest)
        assertEquals(EngineResponse.Dismiss(), lastResponse())
        assertTrue(gate.requests.isEmpty())
    }

    @Test
    fun ac16_otherSecureOrigins_areAskedAgain() {
        f.permissionMemory.set("https://a.com", Camera, true)
        for (origin in listOf("https://b.com", "https://a.com:8443")) {
            val p = ask(Camera, origin = origin)
            assertNotNull(origin, p)
            assertEquals(origin, p!!.origin)
            f.viewModel.onPermissionDecision(p.id, allow = false)
        }
    }

    @Test
    fun ac16_httpVariantOfAllowedOrigin_isNotGranted() {
        f.permissionMemory.set("https://a.com", Camera, true)
        fake.simulatePermissionRequest(setOf(Camera), "http://a.com")
        assertEquals(EngineResponse.Dismiss(), lastResponse())
    }

    @Test
    fun ac16_partialMemory_dialogListsOnlyUnknown_andGrantMergesRemembered() {
        f.permissionMemory.set("https://a.com", Camera, true)
        gate.alreadyGranted += setOf(CAMERA, RECORD_AUDIO)
        val p = ask(Camera, Microphone)
        assertNotNull(p)
        assertEquals(setOf(Microphone), p!!.permissions)
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(EngineResponse.Grant(setOf(Camera, Microphone)), fake.responses[p.id])
    }

    @Test
    fun ac16_partialMemory_blockUnknown_grantsOnlyRemembered() {
        f.permissionMemory.set("https://a.com", Camera, true)
        gate.alreadyGranted += setOf(CAMERA, RECORD_AUDIO)
        val p = ask(Camera, Microphone)!!
        f.viewModel.onPermissionDecision(p.id, allow = false)
        assertEquals(EngineResponse.Grant(setOf(Camera)), fake.responses[p.id])
    }

    @Test
    fun ac16_newMemory_inNewViewModel_asksAgain() {
        val p = ask(Camera)!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        val fresh = BrowserFixture(permissionMemory = SitePermissionMemory())
        fresh.selected().visit("https://a.com/")
        fresh.selected().fake.simulatePermissionRequest(setOf(Camera))
        assertTrue(fresh.state().pendingRequest is EngineRequest.PermissionPrompt)
    }

    // --- AC17 ---

    @Test
    fun ac17_insecureOrigin_isDismissed_withoutDialogOrGate() {
        fake.simulatePermissionRequest(setOf(Camera), "http://a.com")
        assertNull(f.state().pendingRequest)
        assertEquals(EngineResponse.Dismiss(), lastResponse())
        assertTrue(gate.requests.isEmpty())
    }

    @Test
    fun ac17_emptyOrigin_isDismissed_withoutDialogOrGate() {
        fake.simulatePermissionRequest(setOf(Location), "")
        assertNull(f.state().pendingRequest)
        assertEquals(EngineResponse.Dismiss(), lastResponse())
        assertTrue(gate.requests.isEmpty())
    }

    @Test
    fun ac17_localhost_isTreatedAsSecure() {
        assertNotNull(ask(Microphone, origin = "http://localhost:8080"))
    }
}
