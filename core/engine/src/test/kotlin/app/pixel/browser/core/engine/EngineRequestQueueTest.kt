package app.pixel.browser.core.engine

import app.pixel.browser.core.engine.EngineRequest.ExternalApp
import app.pixel.browser.core.engine.EngineResponse.Dismiss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 006 – AC1, AC2, AC6: hàng đợi [EngineRequestQueue] (JVM). */
class EngineRequestQueueTest {
    private val queue = EngineRequestQueue()
    private val received = mutableListOf<Pair<ExternalApp, EngineResponse>>()

    private fun enqueue(
        uri: String,
        accept: Boolean = true,
    ): ExternalApp =
        queue.enqueue(
            build = { id -> ExternalApp(id, "https://a.com", uri, hasUserGesture = true, isRedirect = false) },
            onResponse = { r, resp ->
                if (accept) received += r to resp
                accept
            },
        )

    // --- AC1 ---

    @Test
    fun ac1_newQueue_isEmpty() {
        assertTrue(queue.requests.value.isEmpty())
    }

    @Test
    fun ac1_enqueue_keepsCreationOrder_withIdsFromOne() {
        val first = enqueue("tel:1")
        val second = enqueue("tel:2")
        assertEquals(1L, first.id)
        assertEquals(2L, second.id)
        assertEquals(listOf(first, second), queue.requests.value)
    }

    @Test
    fun ac1_respond_removesOnlyThatRequest_andInvokesCallbackOnce() {
        val first = enqueue("tel:1")
        val second = enqueue("tel:2")
        assertTrue(queue.respond(1, Dismiss()))
        assertEquals(listOf(second), queue.requests.value)
        assertEquals(listOf(first to Dismiss()), received)
    }

    @Test
    fun ac1_respondTwice_returnsFalse_andCallbackNotRepeated() {
        enqueue("tel:1")
        enqueue("tel:2")
        assertTrue(queue.respond(1, Dismiss()))
        assertFalse(queue.respond(1, Dismiss()))
        assertEquals(1, received.size)
    }

    @Test
    fun ac1_respondUnknownId_returnsFalse() {
        enqueue("tel:1")
        assertFalse(queue.respond(99, Dismiss()))
        assertEquals(1, queue.requests.value.size)
        assertTrue(received.isEmpty())
    }

    @Test
    fun ac1_idsKeepIncreasing_afterRespond() {
        enqueue("tel:1")
        queue.respond(1, Dismiss())
        assertEquals(2L, enqueue("tel:2").id)
    }

    // --- AC2 ---

    @Test
    fun ac2_dismissAll_emptiesQueue_andDismissesEveryPendingRequest() {
        val first = enqueue("tel:1")
        val second = enqueue("tel:2")
        queue.dismissAll()
        assertTrue(queue.requests.value.isEmpty())
        assertEquals(listOf(first to Dismiss(), second to Dismiss()), received)
    }

    @Test
    fun ac2_respondAfterDismissAll_returnsFalse() {
        enqueue("tel:1")
        enqueue("tel:2")
        queue.dismissAll()
        assertFalse(queue.respond(1, Dismiss()))
        assertFalse(queue.respond(2, Dismiss()))
        assertEquals(2, received.size)
    }

    @Test
    fun cancel_removesRequest_withoutCallback() {
        enqueue("tel:1")
        val second = enqueue("tel:2")
        queue.cancel(1)
        assertEquals(listOf(second), queue.requests.value)
        assertTrue(received.isEmpty())
        assertFalse(queue.respond(1, Dismiss()))
    }

    // --- AC6 ---

    @Test
    fun ac6_externalApp_acceptsDismiss_andDismissWithSuppressFurther() {
        enqueue("tel:1")
        enqueue("tel:2")
        assertTrue(queue.respond(1, Dismiss()))
        assertTrue(queue.respond(2, Dismiss(suppressFurther = true)))
        assertTrue(queue.requests.value.isEmpty())
    }

    @Test
    fun ac6_onResponseReturningFalse_rejectsResponse_andKeepsRequest() {
        val request = enqueue("tel:1", accept = false)
        assertFalse(queue.respond(request.id, Dismiss()))
        assertEquals(listOf(request), queue.requests.value)
    }
}
