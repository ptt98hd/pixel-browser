package app.pixel.browser.feature.browser.intent

import android.app.SearchManager
import android.content.Intent

/**
 * Đọc [IncomingIntent] từ [intent] mà không đọc extra ngoài danh sách cho phép; không ném (spec 006 AC12).
 *
 * Chỉ đọc `EXTRA_TEXT`, `SearchManager.QUERY`, `EXTRA_PROCESS_TEXT` (CharSequence → String thuần). Header,
 * post data, referrer, `EXTRA_APPLICATION_ID`, selector, component và cờ grant không bao giờ được dùng.
 */
fun IncomingIntent.Companion.from(intent: Intent): IncomingIntent =
    IncomingIntent(
        action = safe { intent.action },
        dataString = safe { intent.dataString },
        type = safe { intent.type },
        extraText = intent.textExtra(Intent.EXTRA_TEXT),
        query = intent.textExtra(SearchManager.QUERY),
        processText = intent.textExtra(Intent.EXTRA_PROCESS_TEXT),
        categories = safe { intent.categories?.toSet() }.orEmpty(),
        launchedFromHistory =
            safe { intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0 } ?: false,
    )

/** Extra dạng chuỗi; sai kiểu hoặc Parcel hỏng (`BadParcelableException`) → null. */
private fun Intent.textExtra(name: String): String? = safe { getCharSequenceExtra(name)?.toString() }

private inline fun <T> safe(read: () -> T?): T? =
    try {
        read()
    } catch (_: RuntimeException) {
        null
    }
