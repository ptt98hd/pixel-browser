package app.pixel.browser.core.engine

import app.pixel.browser.core.engine.NavigationDecision.Block
import app.pixel.browser.core.engine.NavigationDecision.External
import app.pixel.browser.core.engine.NavigationDecision.LoadInEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Spec 006 – AC4: bảng [NavigationPolicy.decide] cho main frame và subframe (JVM, parameterized). */
@RunWith(Parameterized::class)
class NavigationPolicyTest(
    private val url: String,
    private val mainFrame: NavigationDecision,
    private val subFrame: NavigationDecision,
) {
    @Test
    fun ac4_mainFrame() {
        assertEquals("main frame: $url", mainFrame, NavigationPolicy.decide(url, isMainFrame = true))
    }

    @Test
    fun ac4_subFrame_externalBecomesBlock() {
        assertEquals("subframe: $url", subFrame, NavigationPolicy.decide(url, isMainFrame = false))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            listOf(
                // Web
                row("https://a.com", LoadInEngine),
                row("http://a.com", LoadInEngine),
                row("HTTPS://A.COM", LoadInEngine),
                // Engine tự xử lý
                row("about:blank", LoadInEngine),
                row("about:srcdoc", LoadInEngine),
                row("data:text/html,x", LoadInEngine),
                row("blob:https://a.com/uuid", LoadInEngine),
                row("javascript:void(0)", LoadInEngine),
                // Chặn
                row("file:///sdcard/a", Block),
                row("content://x/y", Block),
                // App ngoài
                row("tel:123", External),
                row("mailto:a@b.c", External),
                row("sms:1", External),
                row("geo:0,0", External),
                row("market://details?id=x", External),
                row("intent://scan#Intent;scheme=zxing;end", External),
                row("myapp://open", External),
                // Không parse được scheme
                row("", Block),
                row("not a url", Block),
                row("://x", Block),
                row("1abc:foo", Block),
            )

        /** Subframe: `External` → `Block`, còn lại giữ nguyên. */
        private fun row(
            url: String,
            main: NavigationDecision,
        ): Array<Any> = arrayOf(url, main, if (main == External) Block else main)
    }
}
