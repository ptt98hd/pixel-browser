package app.pixel.browser.feature.browser.intent

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import app.pixel.browser.core.engine.isValidWebUrl
import app.pixel.browser.core.engine.schemeOf
import java.net.URISyntaxException

/** Kết quả [ExternalIntents.parse]: intent đã làm sạch, URL fallback và/hoặc URL web (spec 006). */
data class ParsedExternal(
    val intent: Intent?,
    val fallbackUrl: String?,
    val webUrl: String?,
)

/** Đích hiển thị trong hộp thoại xác nhận: scheme của data và package sẽ được mở (nếu có). */
data class ExternalTarget(
    val scheme: String,
    val packageName: String?,
)

/**
 * Parse URI do trang yêu cầu mở bằng app khác (spec 006 AC20–AC22). Intent kết quả luôn được dựng lại từ
 * đầu: chỉ `ACTION_VIEW` + data/type + package + `CATEGORY_BROWSABLE`; không component, selector, extra,
 * clipData hay cờ (kể cả `FLAG_GRANT_*`).
 */
object ExternalIntents {
    private const val INTENT_SCHEME = "intent"
    private const val EXTRA_FALLBACK_URL = "browser_fallback_url"
    private val WEB_SCHEMES = setOf("http", "https")

    /** Scheme không bao giờ được giao cho app khác (dữ liệu cục bộ, script, nội dung do engine xử lý). */
    private val FORBIDDEN_SCHEMES = setOf("file", "content", "javascript", "data", "about", "blob", "filesystem")

    /** Parse + làm sạch URI ngoài. null nếu không hợp lệ/bị cấm. */
    fun parse(
        uri: String,
        ownPackage: String,
    ): ParsedExternal? {
        val text = uri.trim()
        val scheme = schemeOf(text) ?: return null
        return when (scheme) {
            // Intent.parseUri chỉ nhận tiền tố "intent:" chữ thường.
            INTENT_SCHEME -> parseIntentUri(INTENT_SCHEME + text.substring(INTENT_SCHEME.length), ownPackage)

            in WEB_SCHEMES -> if (isValidWebUrl(text)) ParsedExternal(null, null, text) else null

            in FORBIDDEN_SCHEMES -> null

            else -> ParsedExternal(browsableView(text.toUri(), type = null, pkg = null), null, null)
        }
    }

    /**
     * Mô tả đúng intent mà [parse] tạo ra (package của chính intent sẽ mở, scheme của data/URL web), để hộp
     * thoại không bao giờ hiển thị khác với thứ được mở. null nếu [parse] từ chối.
     */
    fun describe(
        uri: String,
        ownPackage: String,
    ): ExternalTarget? {
        val parsed = parse(uri, ownPackage) ?: return null
        val intent = parsed.intent
        val scheme = intent?.data?.scheme ?: parsed.webUrl?.let(::schemeOf)
        return ExternalTarget(scheme = scheme?.trim()?.lowercase().orEmpty(), packageName = intent?.`package`)
    }

    private fun parseIntentUri(
        uri: String,
        ownPackage: String,
    ): ParsedExternal? {
        val parsed =
            try {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            } catch (_: URISyntaxException) {
                return null
            } catch (_: RuntimeException) {
                return null
            }
        val action = parsed.action
        if (action != null && action != Intent.ACTION_VIEW) return null
        val pkg = parsed.`package`?.takeIf { it.isNotBlank() }
        if (pkg == ownPackage) return null
        val fallback =
            try {
                parsed.getStringExtra(EXTRA_FALLBACK_URL)?.trim()?.takeIf(::isValidWebUrl)
            } catch (_: RuntimeException) {
                null
            }
        val data = parsed.data
        val dataScheme = data?.scheme?.trim()?.lowercase()
        if (dataScheme != null && dataScheme in FORBIDDEN_SCHEMES) return null
        if (dataScheme in WEB_SCHEMES && pkg == null) {
            val web = data.toString().takeIf(::isValidWebUrl) ?: return null
            return ParsedExternal(null, fallback, web)
        }
        if (data == null && pkg == null) return null
        return ParsedExternal(browsableView(data, parsed.type, pkg), fallback, null)
    }

    private fun browsableView(
        data: Uri?,
        type: String?,
        pkg: String?,
    ): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            if (data != null || type != null) setDataAndType(data, type)
            setPackage(pkg)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
}

sealed interface ExternalLaunchResult {
    data object Launched : ExternalLaunchResult

    data class NotFound(
        val fallbackUrl: String?,
    ) : ExternalLaunchResult

    data class OpenInBrowser(
        val url: String,
    ) : ExternalLaunchResult

    data object Rejected : ExternalLaunchResult
}

fun interface ExternalAppLauncher {
    fun launch(uri: String): ExternalLaunchResult

    /**
     * Mô tả thứ [launch] sẽ mở để hỏi người dùng; null nếu URI bị từ chối (không hỏi, không mở). Bản thật phải
     * dùng cùng parse với [launch]; mặc định (cho bản giả) chỉ nêu scheme.
     */
    fun describe(uri: String): ExternalTarget? = ExternalTarget(scheme = schemeOf(uri).orEmpty(), packageName = null)
}

/**
 * Mở URI ngoài qua [context] (spec 006 AC26). Không dùng `resolveActivity`/`<queries>`: gọi thẳng
 * `startActivity` và coi `ActivityNotFoundException` là không có app.
 *
 * Có thể truyền application context (để `ViewModel` giữ mà không rò Activity); khi đó intent được thêm
 * `FLAG_ACTIVITY_NEW_TASK`.
 */
class AndroidExternalAppLauncher(
    private val context: Context,
) : ExternalAppLauncher {
    override fun describe(uri: String): ExternalTarget? = ExternalIntents.describe(uri, context.packageName)

    override fun launch(uri: String): ExternalLaunchResult {
        val parsed = ExternalIntents.parse(uri, context.packageName) ?: return ExternalLaunchResult.Rejected
        val intent =
            parsed.intent
                ?: return parsed.webUrl?.let(ExternalLaunchResult::OpenInBrowser) ?: ExternalLaunchResult.Rejected
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ExternalLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            ExternalLaunchResult.NotFound(parsed.fallbackUrl)
        } catch (_: SecurityException) {
            ExternalLaunchResult.NotFound(parsed.fallbackUrl)
        }
    }
}
