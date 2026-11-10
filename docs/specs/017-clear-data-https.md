# Spec 017: Xóa dữ liệu duyệt web, HTTPS-first, cảnh báo chứng chỉ & Safe Browsing, cookie bên thứ ba (B12)
## Story
**Xóa dữ liệu:** Cài đặt > Quyền riêng tư > "Xóa dữ liệu duyệt web": chọn khoảng thời gian (giờ qua / 24 giờ / 7 ngày / tất cả) và loại dữ liệu (lịch sử, cookie & dữ liệu trang, bộ nhớ đệm, cài đặt trang đã lưu), xác nhận rồi xóa. Dấu trang không bao giờ bị xóa ở đây. Giao diện nói thẳng điều WebView không làm được: cookie/bộ nhớ đệm **không xóa theo khoảng thời gian**, nên luôn xóa toàn bộ.
**HTTPS:** ba chế độ "Tắt / HTTPS trước (mặc định) / Chỉ HTTPS". Mọi điều hướng `http://` tới site công khai được nâng cấp lên `https://`; nếu site không hỗ trợ, người dùng thấy **trang cảnh báo của Pixel** (không âm thầm hạ xuống http).
**Chứng chỉ & Safe Browsing:** lỗi chứng chỉ không còn bị hủy mù; hiện trang cảnh báo native với nút chính "Quay lại an toàn" và mục "Nâng cao" để tiếp tục (chỉ cho host đó, chỉ trong phiên của tab). Trang bị Safe Browsing gắn cờ cũng hiện trang cảnh báo native thay cho trang đỏ HTML của WebView.
**Cookie bên thứ ba:** công tắc "Chặn cookie bên thứ ba" (tab ẩn danh luôn chặn).

## Phạm vi & module
- **Module mới `:core:privacy`** (Kotlin thuần, không phụ thuộc Android/engine/history): `HttpsMode`, `PrivacyConfig`, `PrivacyConfigSource`, `HttpsPolicy`, `HttpsDecision`, `DataType`, `ClearRange`, `ClearRequest`, `DataClearTarget`, `BrowsingDataCleaner`, `ClearReport`. Lý do tách: `:core:engine` cần `HttpsPolicy`/`PrivacyConfig`, `:core:data` cần `HttpsMode`, mà hai module đó không phụ thuộc nhau; `HistoryRange` ở `:core:history` nên `ClearRange` được định nghĩa lại và ánh xạ ở `:app`.
- `:core:engine`: `EngineRequest.SslError/SafeBrowsingHit/HttpsFallback`, `SslErrorKind`, `ThreatKind`, `WebDataCleaner` + `AndroidWebDataCleaner`, `EngineSession.setPrivacyConfigSource`, nâng cấp HTTPS và xử lý SSL/Safe Browsing trong `WebViewEngineSession` + `FakeEngineSession`.
- `:core:data`, package `privacy`: mở rộng `PrivacySettings` của spec 016 (`httpsMode`, `safeBrowsing`, `blockThirdPartyCookies`); phụ thuộc `:core:privacy`.
- `:feature:settings`: `ClearDataPage` (thay giữ chỗ `clear_data`), `SafeBrowsingPage` (thay giữ chỗ `safe_browsing`), trang mới `https_mode` và `cookies` (`SettingsPageIds.HTTPS_MODE`, `COOKIES`), `ClearDataState`.
- `:feature:browser`: `SecurityInterstitial` (Compose) cho `SslError`/`SafeBrowsingHit`/`HttpsFallback`, `BrowserViewModel.onInterstitialDecision`.
- `:app`: ánh xạ `DataClearTarget` (lịch sử, cookie, cache, cài đặt trang, [019] tải xuống), `privacyConfig`, `network_security_config.xml`, `meta-data` Safe Browsing, `SettingsPages.kt`.
- Phụ thuộc: sau 016 (`PrivacySettings`, tab ẩn danh), sau 014 (registry). Spec 019 thêm `DataType.DownloadHistory` thật.

## Quyết định thiết kế
### Xóa dữ liệu
- **Loại dữ liệu → nơi xóa.** `History` → `HistoryClearer.clear(range)` (có theo khoảng). `CookiesAndSiteData` → `CookieManager.removeAllCookies` + `flush`, `WebStorage.deleteAllData`, `GeolocationPermissions.clearAll` (Android **không** có API xóa cookie/localStorage theo thời gian ⇒ luôn toàn bộ). `Cache` → `WebView.clearCache(true)` (cache toàn app; cần một `WebView` tạm với application context) + `TabThumbnailRepository.clearDisk()` + `FaviconStore.clear()` (cũng toàn bộ). `SiteSettings` → `DesktopSitePolicy.clearOverrides()` + `SitePermissionMemory.clear()` (+ kho site settings của B07 sau này). `DownloadHistory` (spec 019, chỉ khi có target) → xóa bản ghi, **không** xóa tệp.
- **Hậu quả của việc không theo thời gian.** Khoảng ≠ Tất cả và có tick cookie/cache/cài đặt trang ⇒ giao diện hiện `clear_note_all_time` ngay cạnh các mục đó ("xóa toàn bộ, không theo khoảng thời gian") và hộp xác nhận nhắc lại ("bạn sẽ bị đăng xuất khỏi mọi trang"). Không lặng lẽ xóa nhiều hơn điều đã hiển thị.
- **Mặc định an toàn:** khoảng "Giờ qua"; tick lịch sử + bộ nhớ đệm; **không** tick cookie (đăng xuất mọi site) và cài đặt trang. Dấu trang nằm ngoài danh sách (xóa ở màn Dấu trang); ghi chú `clear_note_bookmarks_kept`.
- **Tab ẩn danh không liên quan:** dữ liệu ẩn danh nằm trong profile riêng (016) và tự xóa; "Cookie" ở đây chỉ là profile mặc định. Tab đang mở không bị tải lại; hiện gợi ý "Tải lại các tab đang mở để áp dụng" khi cookie/site data đã xóa.
- `BrowsingDataCleaner` chạy tuần tự theo thứ tự cố định `History, CookiesAndSiteData, Cache, SiteSettings, DownloadHistory`, **cô lập lỗi từng loại** (một loại ném → ghi `Failed`, loại sau vẫn chạy), trả `ClearReport`; `CancellationException` luôn được ném lại.
### HTTPS-first
- **Mặc định `HttpsFirst`.** Quyết định tại engine (mọi điều hướng, không chỉ omnibox): `loadUrl`, `shouldOverrideUrlLoading` main frame (gồm redirect), và điều hướng lịch sử/khôi phục (qua `onPageStarted`, như spec 015 AC19). Omnibox đã mặc định `https` cho tên miền trần (spec 004); nhập `http://` tường minh cũng được nâng cấp.
- **`HttpsPolicy.decide(url, mode, hostExceptions)`** thuần: `Off` → `Keep`. Còn lại, URL `http://` được `Upgrade(httpsUrl)` (đổi scheme; bỏ cổng `:80`) trừ **host miễn trừ**: mọi IP literal (IPv4/IPv6), `localhost` và `*.localhost`, host một nhãn (`intranet`), hậu tố `.local`, `.lan`, `.internal`, `.home.arpa`, và URL có cổng tường minh khác 80 (máy dev). Trong `HttpsOnly` cổng tường minh vẫn nâng cấp giữ nguyên cổng và `hostExceptions` bị bỏ qua. `https`, `about`, `data`, `blob`, `javascript`… → `Keep`.
- **Chống vòng lặp:** một chuỗi điều hướng chỉ nâng cấp mỗi URL một lần; nếu `https://x` lại chuyển hướng về đúng `http://x` ⇒ coi như site không hỗ trợ HTTPS (đi tới fallback), không tải đi tải lại.
- **Phát hiện "không hỗ trợ HTTPS":** lỗi main frame của điều hướng đã nâng cấp với mã `ERROR_CONNECT`, `ERROR_TIMEOUT`, `ERROR_FAILED_SSL_HANDSHAKE`, `ERROR_IO`. **Không** coi là fallback: `ERROR_HOST_LOOKUP` (DNS hỏng), HTTP ≥ 400, lỗi subframe, và **lỗi chứng chỉ** (đi tới trang chứng chỉ – hạ xuống http khi server có nói https nhưng chứng chỉ sai là hạ cấp nguy hiểm). Không có timeout riêng (WebView không có API connect-timeout) – ghi nhận là giới hạn (E05).
- **Fallback = `EngineRequest.HttpsFallback`** (trang cảnh báo native). `HttpsFirst`: nút "Quay lại an toàn" (chính) và "Tiếp tục tới trang http (không an toàn)" trong "Nâng cao"; tiếp tục ⇒ host vào `httpExceptions` **của session (tab)**, không lưu đĩa, tab khác/phiên mới hỏi lại. `HttpsOnly`: không có nút tiếp tục (`canProceed = false`), `respond(Confirm)` bị từ chối. Quay lại an toàn ⇒ tab lùi một bước hoặc về home.
- **Mixed content & cleartext.** Giữ `MIXED_CONTENT_NEVER_ALLOW` (chặn cả tải thụ động http trên trang https). **Phát hiện lỗi hiện hữu:** manifest chưa có `usesCleartextTraffic`/`networkSecurityConfig` và `targetSdk 37`, nên WebView có khả năng trả `ERR_CLEARTEXT_NOT_PERMITTED` cho mọi trang `http://` (kể cả chế độ Tắt và fallback). Spec thêm `res/xml/network_security_config.xml`: `base-config cleartextTrafficPermitted="true"` với `trust-anchors` chỉ `system` (không `user`), và `android:networkSecurityConfig`. Cleartext chỉ là **quyền ở tầng OS**; chính sách thật nằm ở engine (`HttpsPolicy`). Mã mạng riêng (019) tự từ chối http trừ khi nguồn là trang http người dùng đã chọn. Cần `CleartextInstrumentedTest` xác nhận cả trước/sau khi thêm cấu hình.
### Chứng chỉ SSL
- `onReceivedSslError`: lỗi của **main frame** khi `error.url` trùng URL điều hướng hiện tại (bỏ fragment) ⇒ `EngineRequest.SslError` (host, loại lỗi, cấp cho/bởi, hiệu lực, SHA-256 vân tay của chứng chỉ server) giữ `SslErrorHandler` chờ; trả lời `Dismiss` ⇒ `handler.cancel()`, `Confirm` ⇒ `handler.proceed()`. Lỗi **subresource** (url khác) ⇒ `cancel()` ngay, không request (hành vi cũ), `securityState = Insecure`. Một request chờ mỗi session; lỗi tiếp theo trong lúc chờ ⇒ `cancel()`.
- **Ngoại lệ chỉ trong phiên của tab**, khóa `(host, sha256)` – đổi chứng chỉ thì hỏi lại; lần sau cùng khóa ⇒ tự `proceed()` không hiện trang; không lưu đĩa; thanh trên vẫn hiện `security_insecure`.
### Safe Browsing
- `WebSettings.safeBrowsingEnabled` đã bật ở spec 001; thêm `meta-data android:name="android.webkit.WebView.EnableSafeBrowsing" value=true` (tường minh). Mặc định WebView tự vẽ trang đỏ HTML cục bộ; override `WebViewClient.onSafeBrowsingHit` ⇒ `EngineRequest.SafeBrowsingHit(url, threat, isMainFrame)`; `Dismiss` ⇒ `callback.backToSafety(false)`, `Confirm` ⇒ `callback.proceed(false)`. **Tham số `report` luôn `false`** (không telemetry). Công tắc "Duyệt web an toàn" (mặc định bật) áp lên `WebSettings.safeBrowsingEnabled` ở mỗi lần bắt đầu tải trang.
- **Giới hạn trung thực:** Safe Browsing của WebView dựa vào dịch vụ Google (cần Play Services/WebView hỗ trợ); máy không có GMS thì công tắc không có tác dụng. Trang cài đặt hiển thị cảnh báo này, ứng dụng không tuyên bố "được bảo vệ" khi không chắc. Có gửi băm URL tới Google khi bật.
### Cookie bên thứ ba
- `CookieManager.setAcceptThirdPartyCookies(webView, accept)` theo từng WebView: áp ở mỗi `onPageStarted` từ `PrivacyConfigSource` (đổi cài đặt có hiệu lực ở điều hướng kế). **Tab ẩn danh luôn chặn** (bất kể cài đặt). Mặc định tab thường: cho phép (tránh hỏng đăng nhập nhúng/thanh toán); đổi mặc định ở Q3.
### Giao diện cảnh báo
- Trang cảnh báo là **Compose native** ở `:feature:browser`, **không** HTML từ xa/WebView; hiển thị đè lên vùng nội dung (không `AlertDialog`/cửa sổ phụ, cho test ổn định như spec 010/014). Luôn: tiêu đề theo loại lỗi, host (qua `UrlDisplay.host`, loại ký tự bidi/điều khiển), giải thích ngắn, nút chính "Quay lại an toàn", mục "Nâng cao" (chi tiết + nút tiếp tục). Back hệ thống = quay lại an toàn.

## Thay đổi API
### `:core:privacy`
```kotlin
enum class HttpsMode { Off, HttpsFirst, HttpsOnly }
data class PrivacyConfig(val httpsMode: HttpsMode = HttpsMode.HttpsFirst, val safeBrowsing: Boolean = true, val blockThirdPartyCookies: Boolean = false)
fun interface PrivacyConfigSource { fun current(): PrivacyConfig }
sealed interface HttpsDecision { data object Keep; data class Upgrade(val httpsUrl: String) }
object HttpsPolicy {
    fun decide(url: String, mode: HttpsMode, hostExceptions: Set<String> = emptySet()): HttpsDecision
    fun isExemptHost(host: String): Boolean
}
enum class DataType { History, CookiesAndSiteData, Cache, SiteSettings, DownloadHistory }
enum class ClearRange { LastHour, LastDay, LastWeek, All; val isAll: Boolean }
data class ClearRequest(val types: Set<DataType>, val range: ClearRange)
interface DataClearTarget { val type: DataType; val supportsRange: Boolean; suspend fun clear(range: ClearRange, now: Long) }
sealed interface TypeResult { data object Cleared; data class Failed(val message: String) }
data class ClearReport(val results: Map<DataType, TypeResult>) { val allCleared: Boolean }
class BrowsingDataCleaner(targets: List<DataClearTarget>, clock: () -> Long) {
    val availableTypes: Set<DataType>
    fun rangeIgnoredFor(request: ClearRequest): Set<DataType>      // loại có tick nhưng !supportsRange khi range != All
    suspend fun clear(request: ClearRequest): ClearReport
}
```
### `:core:engine`
```kotlin
enum class SslErrorKind { NotYetValid, Expired, HostMismatch, Untrusted, DateInvalid, Invalid }   // từ SslError.SSL_* 0..5
enum class ThreatKind { Malware, Phishing, UnwantedSoftware, Billing, Unknown }                   // SAFE_BROWSING_THREAT_*
sealed interface EngineRequest {
    data class SslError(override val id: Long, override val origin: String, val url: String, val host: String, val kind: SslErrorKind,
        val issuedTo: String?, val issuedBy: String?, val validFrom: Long?, val validUntil: Long?, val sha256: String?) : EngineRequest
    data class SafeBrowsingHit(override val id: Long, override val origin: String, val url: String, val threat: ThreatKind, val isMainFrame: Boolean) : EngineRequest
    data class HttpsFallback(override val id: Long, override val origin: String, val httpUrl: String, val httpsUrl: String, val canProceed: Boolean) : EngineRequest
}
// accepts(): SslError, SafeBrowsingHit ← Confirm/Dismiss; HttpsFallback ← Dismiss, và Confirm chỉ khi canProceed
interface EngineSession { fun setPrivacyConfigSource(source: PrivacyConfigSource?) }   // null = mặc định PrivacyConfig()
interface WebDataCleaner { suspend fun clearCookiesAndSiteData(); suspend fun clearCache() }
class AndroidWebDataCleaner(context: Context) : WebDataCleaner      // chạy trên main bên trong
```
- Điểm vào test (không khởi tạo được `SslErrorHandler`/`SafeBrowsingResponse`): `WebViewEngineSession.dispatchSslError(info: SslErrorInfo, onResult)`, `dispatchSafeBrowsingHit(url, threatConst, isMainFrame, onResult)`, `dispatchMainFrameError(errorCode: Int)`. Fake: `simulateSslError(info)` (thay hàm cũ không tham số – cập nhật nơi dùng), `simulateSafeBrowsingHit(url, threat, isMainFrame)`, `simulateMainFrameError(code)`, `val appliedPrivacy: List<PrivacyConfig>`, `val loadedUrls` (đã có) dùng kiểm URL nâng cấp.
### `:core:data` (`...core.data.privacy`)
```kotlin
data class PrivacySettings(
    val blockPrivateScreenshots: Boolean = true, val privateAutoClose: PrivateAutoClose = PrivateAutoClose.Never,     // 016
    val httpsMode: HttpsMode = HttpsMode.HttpsFirst, val safeBrowsing: Boolean = true, val blockThirdPartyCookies: Boolean = false,
) { fun toConfig(): PrivacyConfig }
```
- Khóa DataStore: `privacy_https_mode` (`"off"|"https_first"|"https_only"`, lạ → `HttpsFirst`), `privacy_safe_browsing`, `privacy_block_3p_cookies`.
### `:feature:settings`
```kotlin
fun interface ClearDataController { suspend fun clear(request: ClearRequest): ClearReport }
data class ClearDataState(val range: ClearRange = ClearRange.LastHour, val types: Set<DataType> = setOf(History, Cache)) {
    val canClear: Boolean; fun needsAllTimeNote(supportsRange: (DataType) -> Boolean): Boolean
}
@Composable fun ClearDataPage(available: Set<DataType>, controller: ClearDataController, rangeIgnoredFor: (ClearRequest) -> Set<DataType>)
@Composable fun HttpsModePage(settings: PrivacySettings, onChange: ((PrivacySettings) -> PrivacySettings) -> Unit)
@Composable fun SafeBrowsingPage(...); @Composable fun CookiesPage(...)
```
- Test tag: `clear_range_<hour|day|week|all>`, `clear_type_<history|cookies|cache|site_settings|downloads>`, `clear_note_all_time`, `clear_note_bookmarks_kept`, `clear_button`, `clear_confirm_panel`, `clear_confirm_ok`, `clear_confirm_cancel`, `clear_progress`, `clear_result`, `https_mode_<off|first|only>`, `safe_browsing_switch`, `safe_browsing_no_gms_note`, `block_third_party_switch`, `block_private_always_note`.
### `:feature:browser`
- `BrowserUiState.pendingRequest` có thể là `SslError`/`SafeBrowsingHit`/`HttpsFallback`; `BrowserViewModel.onInterstitialDecision(tabId: String, requestId: Long, proceed: Boolean)`; `SecurityInterstitial(request, onBack, onProceed)`.
- Tag: `interstitial`, `interstitial_title`, `interstitial_host`, `interstitial_back`, `interstitial_details_toggle`, `interstitial_details`, `interstitial_proceed`, `interstitial_cert_issued_to`, `interstitial_cert_issued_by`, `interstitial_cert_validity`, `interstitial_cert_fingerprint`.
- Chuỗi mới ở `values` và `values-vi`.

## Acceptance criteria
### Logic thuần (JVM, `:core:privacy`)
1. **AC1 – `HttpsPolicy.decide`.** (`HttpsPolicyTest`, bảng) `Off`: mọi URL → `Keep`. `HttpsFirst`: `http://example.com/a?b=1#c` → `Upgrade("https://example.com/a?b=1#c")`; `HTTP://Example.com` → `Upgrade("https://Example.com")`; `http://example.com:80/` → `Upgrade("https://example.com/")`; `http://example.com:8080/` → `Keep`; `https://x.com`, `about:blank`, `data:text/html,x`, `blob:https://a.com/u`, `javascript:1`, `file:///a` → `Keep`. Miễn trừ → `Keep`: `http://localhost`, `http://app.localhost`, `http://127.0.0.1`, `http://192.168.1.5`, `http://8.8.8.8`, `http://[::1]`, `http://intranet`, `http://printer.local`, `http://nas.lan`, `http://x.internal`, `http://x.home.arpa`. `hostExceptions = {"example.com"}` → `Keep` ở `HttpsFirst`. `HttpsOnly`: `http://example.com:8080/` → `Upgrade("https://example.com:8080/")`; `hostExceptions` bị bỏ qua; host miễn trừ vẫn `Keep`. URL rác/không có host → `Keep`, không ném.
2. **AC2 – `BrowsingDataCleaner`.** (`BrowsingDataCleanerTest`, target giả) chỉ gọi target của loại được yêu cầu, theo thứ tự `History, CookiesAndSiteData, Cache, SiteSettings, DownloadHistory`; `types` rỗng → không gọi gì, `ClearReport` rỗng; truyền đúng `range` và `now = clock()`; target ném `IOException` → `Failed(message)` cho loại đó, các loại sau vẫn chạy, `allCleared == false`; `CancellationException` được ném lại (không thành `Failed`); loại không có target → bị bỏ khỏi `availableTypes`, yêu cầu loại đó → `Failed("unavailable")` không ném; `rangeIgnoredFor` trả đúng các loại `!supportsRange` được tick khi `range != All`, rỗng khi `All`.
3. **AC3 – `ClearDataState`.** (`ClearDataStateTest`) mặc định `LastHour` + `{History, Cache}`; `canClear == false` khi `types` rỗng; `needsAllTimeNote` đúng khi range ≠ All và có loại `!supportsRange`; bật/tắt loại bất biến (set mới).
4. **AC4 – Ánh xạ lỗi.** (`SslMappingTest`, `ThreatMappingTest`) `SslError.SSL_NOTYETVALID(0)→NotYetValid`, `1→Expired`, `2→HostMismatch`, `3→Untrusted`, `4→DateInvalid`, `5→Invalid`, giá trị lạ → `Invalid`; threat `1→Malware`, `2→Phishing`, `3→UnwantedSoftware`, `4→Billing`, `0`/lạ → `Unknown`; `fingerprintHex(bytes)` → `"AA:BB:…"` 32 byte hoa, ngăn cách `:`; `null` chứng chỉ → `sha256 == null`.
### Store & wiring (JVM/Robolectric)
5. **AC5 – Cài đặt riêng tư.** (`DataStorePrivacySettingsStoreTest`) mặc định `httpsMode = HttpsFirst`, `safeBrowsing = true`, `blockThirdPartyCookies = false`; round trip ba trường; `privacy_https_mode` lạ → `HttpsFirst`; trường 016 không bị đổi khi ghi trường 017 (xen kẽ); `toConfig()` ánh xạ đúng. Hồi quy toàn bộ test 016.
6. **AC6 – Nối engine.** (`PixelBrowserApplicationPrivacyTest`, `MainActivityPrivacyTest`) `sessionFactory` đặt `setPrivacyConfigSource { app.privacyConfig.value }` cho **mọi** session (mới, khôi phục, popup, ẩn danh); đổi `httpsMode` trong cài đặt → session mở sẵn dùng giá trị mới ở điều hướng kế (không cần tạo lại).
7. **AC7 – Cleanup targets.** (`:app` `ClearTargetsTest`, đối tượng thật/giả) `History` → `HistoryClearer.clear(HistoryRange)` đúng ánh xạ range (`LastHour→LastHour`…); `SiteSettings` → `DesktopSitePolicy.clearOverrides()` (`overrideCount == 0`, file `desktop.json` ghi lại) và `SitePermissionMemory.clear()`; `Cache` → `WebDataCleaner.clearCache()` + `thumbnails.clearDisk()` + `faviconStore.clear()`; `CookiesAndSiteData` → `WebDataCleaner.clearCookiesAndSiteData()`; dấu trang **không** thay đổi sau mọi tổ hợp (kho dấu trang giả không nhận lệnh xóa).
8. **AC8 – `AndroidWebDataCleaner`.** (`AndroidWebDataCleanerTest`, Robolectric) cookie đặt qua `CookieManager` → sau `clearCookiesAndSiteData()` `getCookie` rỗng và `flush` được gọi; `WebStorage.deleteAllData` và `GeolocationPermissions.clearAll` được gọi; `clearCache()` gọi `clearCache(true)` trên một WebView tạm rồi hủy nó; không giữ Activity context; chạy từ coroutine ngoài main vẫn thực thi trên main.
### Engine – HTTPS (JVM Fake; Robolectric WebView qua `dispatch*`)
9. **AC9 – Nâng cấp khi `loadUrl`.** (`EngineSessionContractTest`, Fake + WebView) nguồn `HttpsFirst`: `loadUrl("http://example.com/")` → engine tải `https://example.com/` (`loadedUrls`/`shadowOf(webView).lastLoadedUrl`), `state.url == "https://example.com/"`; `Off` → giữ `http://`; host miễn trừ giữ `http://`; `file:`/`about:` không đổi. Nguồn `null` → mặc định `HttpsFirst`.
10. **AC10 – Nâng cấp khi điều hướng của trang.** `simulateNavigation("http://a.com/x", isMainFrame = true)` (WebView: `dispatchNavigation`) → trả `true` (chặn bản http) và engine tải `https://a.com/x`; `isMainFrame = false` → không hỏi chính sách; redirect (`isRedirect = true`) cũng nâng cấp; `https://` → `false` không đổi. Vòng lặp: sau khi nâng cấp `http://a.com/` rồi `https://a.com/` chuyển hướng lại `http://a.com/` → lần này **không** nâng cấp nữa và phát `HttpsFallback` (không tải lặp).
11. **AC11 – Điều hướng lịch sử/khôi phục.** `simulatePageStarted("http://example.com/")` (không phải kết quả nâng cấp) ở `HttpsFirst` → engine dừng và tải `https://example.com/` đúng 1 lần; ở `Off` hoặc host miễn trừ/ngoại lệ → để nguyên; `restoreState` chứa mục http cũng xử lý như vậy (tối đa một lần/URL).
12. **AC12 – Phát hiện không hỗ trợ HTTPS.** Sau một lần nâng cấp: `simulateMainFrameError(ERROR_CONNECT / ERROR_TIMEOUT / ERROR_FAILED_SSL_HANDSHAKE / ERROR_IO)` → có đúng một `HttpsFallback(httpUrl, httpsUrl, canProceed = (mode == HttpsFirst))` với `origin` đúng; `ERROR_HOST_LOOKUP`, lỗi trên điều hướng **không** nâng cấp, lỗi subframe, lỗi HTTP ≥ 400 → không có request. Lỗi thứ hai khi đã có một `HttpsFallback` chờ → không tạo thêm.
13. **AC13 – Trả lời fallback.** `respond(id, Dismiss())` → không tải gì, request biến mất. `respond(id, Confirm())` (chỉ `HttpsFirst`) → tải `http://…` gốc **không nâng cấp**; điều hướng http tiếp theo tới cùng host trong session này giữ http, host khác vẫn nâng cấp; session mới (tab khác) nâng cấp lại; `HttpsOnly`: `Confirm` → `respond == false`, request còn đó. `EngineRequestResponseTableTest` bao gồm 3 loại request mới.
### Engine – SSL, Safe Browsing, cookie (JVM Fake; Robolectric `dispatch*`)
14. **AC14 – Lỗi chứng chỉ main frame.** `simulateSslError(info)` với `info.url` trùng URL đang tải → `EngineRequest.SslError` với `host`, `kind`, `issuedTo/By`, `validFrom/Until`, `sha256` đúng; `Dismiss` → `handler.cancel()` (WebView: `onResult(Dismiss)`), `Confirm` → `proceed()`; `securityState == Insecure` trong cả hai trường hợp; request thứ hai khi đang chờ → `cancel()` ngay không tạo request. `info.url` khác URL điều hướng (subresource) → `cancel()` ngay, không request, `securityState == Insecure`.
15. **AC15 – Ngoại lệ theo phiên.** Sau `Confirm` cho `(a.com, FP1)`: lỗi kế tiếp cùng khóa → `proceed()` tự động, không request; cùng host fingerprint `FP2` → hiện request; host khác → hiện request; session khác (tab khác) không dùng ngoại lệ; sau `close()` ngoại lệ mất. Ngoại lệ không được ghi đĩa (quét thư mục như spec 016 AC25).
16. **AC16 – Safe Browsing.** `simulateSafeBrowsingHit(url, Phishing, isMainFrame = true)` → `SafeBrowsingHit(threat = Phishing, isMainFrame = true)`; `Dismiss` → `callback.backToSafety(false)`; `Confirm` → `callback.proceed(false)`; mọi lời gọi truyền `report = false` (fake ghi lại, test khẳng định `reported == false`). Subresource hit (`isMainFrame = false`) vẫn tạo request (trang bị chặn nguyên cả). `onSafeBrowsingHit` không bao giờ gọi `showInterstitial` (không dùng trang HTML của WebView). Bật/tắt `safeBrowsing` trong nguồn cấu hình áp lên `settings.safeBrowsingEnabled` ở lần bắt đầu tải kế (Robolectric nếu shadow hỗ trợ, nếu không → instrumented).
17. **AC17 – Cookie bên thứ ba.** Nguồn `blockThirdPartyCookies = true` → ở `onPageStarted` gọi `CookieManager.setAcceptThirdPartyCookies(webView, false)`; `false` → `true`; đổi nguồn giữa chừng có hiệu lực ở `onPageStarted` kế; session **ẩn danh** luôn `false` kể cả khi cài đặt cho phép. Fake: `appliedPrivacy` ghi từng cấu hình đã áp (kể cả cờ ẩn danh).
18. **AC18 – Bất biến mixed content.** (`WebViewEngineSessionTest`) `settings.mixedContentMode == MIXED_CONTENT_NEVER_ALLOW`, `safeBrowsingEnabled == true` ở trạng thái mặc định, `allowFileAccess == false` (hồi quy).
### Manifest & cấu hình
19. **AC19 – Cleartext & Safe Browsing.** (`ManifestSecurityTest`, Robolectric) manifest đã merge có `android:networkSecurityConfig` trỏ `network_security_config`; parse XML: `base-config` có `cleartextTrafficPermitted="true"` và `trust-anchors` chỉ gồm `certificates src="system"` (không `user`); có `meta-data android.webkit.WebView.EnableSafeBrowsing = true`; không thêm quyền nào mới.
### ViewModel & UI
20. **AC20 – Yêu cầu cảnh báo trong ViewModel.** (`BrowserViewModelInterstitialTest`, JVM) `SslError`/`SafeBrowsingHit`/`HttpsFallback` của tab đang chọn xuất hiện trong `pendingRequest`; của tab nền thì không cho tới khi chọn; `onInterstitialDecision(tab, id, proceed = true)` → `respond(Confirm())`; `false` → `respond(Dismiss())` rồi quay về an toàn: `tabManager.navigateBack` nếu được, ngược lại `showHome`; `tabId` cũ/`requestId` cũ → no-op; đóng tab → engine nhận `Dismiss()` (spec 006); `HttpsFallback` với `canProceed = false` + `proceed = true` → bị bỏ qua.
21. **AC21 – Trang cảnh báo.** (`SecurityInterstitialTest`, Robolectric) `SslError(kind = Expired)`: có `interstitial`, `interstitial_title` đúng loại ("Chứng chỉ đã hết hạn"), `interstitial_host` = host đã làm sạch, `interstitial_back`; `interstitial_proceed` **không tồn tại** tới khi tap `interstitial_details_toggle`; sau đó hiện `interstitial_details` với `interstitial_cert_issued_to/by/validity/fingerprint`. Tap back → `onBack`; tap proceed → `onProceed`. 6 loại lỗi SSL, 5 loại mối đe dọa, `HttpsFallback(canProceed = true/false)` có tiêu đề/mô tả riêng; `canProceed = false` không bao giờ có `interstitial_proceed`. Nút `interstitial_proceed` ghi rõ host và "không an toàn". Tiêu đề là heading (semantics), nút ≥ 48dp, Back hệ thống = `onBack`. Host chứa ký tự bidi/điều khiển bị loại; nội dung không có thành phần tải từ mạng.
22. **AC22 – Hòa nhập vào màn hình trình duyệt.** (`BrowserScreenInterstitialTest`) khi có cảnh báo: `SecurityInterstitial` che vùng nội dung (`boundsInRoot` phủ vùng engine), cử chỉ vuốt cạnh/kéo-để-tải-lại tắt (`GestureGate`), thanh trên vẫn hiện URL và `security_insecure` cho SSL; xoay màn hình giữ nguyên trạng thái "Nâng cao" mở/đóng (`rememberSaveable`).
23. **AC23 – Trang "Xóa dữ liệu".** (`ClearDataPageTest`) mặc định: `clear_range_hour` chọn, `clear_type_history` và `clear_type_cache` tick, `clear_type_cookies`/`clear_type_site_settings` không; `clear_type_downloads` chỉ hiện khi `available` có `DownloadHistory`; `clear_note_bookmarks_kept` luôn hiện; chọn `clear_range_day` + tick cookies → `clear_note_all_time` hiện; chọn `clear_range_all` → ghi chú biến mất; bỏ hết tick → `clear_button` disabled. Tap `clear_button` → `clear_confirm_panel` (liệt kê loại + khoảng; thêm cảnh báo "đăng xuất" khi tick cookies) → `clear_confirm_ok` gọi `controller.clear(ClearRequest(types, range))` đúng 1 lần; `clear_confirm_cancel` không gọi. Trong lúc chạy: `clear_progress` hiện, nút disabled. Kết quả: tất cả xong → `clear_result` "Đã xóa"; có loại `Failed` → `clear_result` liệt kê loại lỗi. Lựa chọn giữ qua xoay (`StateRestorationTester`). Cookies/site data đã xóa → gợi ý tải lại tab.
24. **AC24 – Các trang Bảo mật.** (`HttpsModePageTest`, `SafeBrowsingPageTest`, `CookiesPageTest`) `https_mode_*` radio theo `settings.httpsMode`, chọn → `onChange` đúng 1 lần, chọn lại mục đang chọn không gọi; `HttpsOnly` có mô tả "không thể tiếp tục tới trang http"; `safe_browsing_switch` (`assertIsOn()` theo cài đặt) + `safe_browsing_no_gms_note` luôn hiện (nêu cần Play Services/WebView hỗ trợ); `block_third_party_switch` + `block_private_always_note` ("Tab ẩn danh luôn chặn"). Registry: `clear_data`, `safe_browsing` thay giữ chỗ; `https_mode`, `cookies` thêm vào phần `privacy`; cập nhật `SettingsPagesTest` (spec 014 AC8, spec 016 AC22) – mọi `PageIds` duy nhất.
25. **AC25 – Đầu-cuối.** (`MainActivityPrivacyTest`, Fake engine) Cài đặt > Xóa dữ liệu > tick lịch sử + chọn "Giờ qua" > xác nhận → `historyStore` mất các lượt trong giờ qua và giữ lượt cũ hơn; bookmark không đổi. Mở `http://neverssl.test` với Fake `HttpsFirst` → engine tải https; `simulateMainFrameError(ERROR_CONNECT)` → màn hình hiện `interstitial`; tap `interstitial_back` → quay về home/back, không tải http; mở lại, "Nâng cao" → tiếp tục → `loadedUrls.last() == "http://neverssl.test/"`; `simulateSslError` → cảnh báo chứng chỉ → tiếp tục → trang tải; đổi tab ẩn danh → cookie bên thứ ba bị chặn (`appliedPrivacy.last()` theo cờ ẩn danh).
26. **AC26 – Riêng tư & i18n.** Mọi chuỗi mới ở `values` và `values-vi`, `lintDebug` xanh; `:feature:settings` và `:core:privacy` không có quyền `INTERNET`; không có `Log.*` URL/tiêu đề/vân tay chứng chỉ; không có lệnh gọi mạng để kiểm tra tính năng nào trong spec.

## Edge cases
- Xóa cookie khi tab đang mở: trang trong bộ nhớ vẫn "đăng nhập" tới khi tải lại; WebView có thể ghi lại cookie từ trang đang chạy. Gợi ý tải lại; không tự động tải lại.
- `Cache` xóa thumbnail của tab đang mở: thumbnail tạo lại ở lần mở trình chuyển tab sau.
- Xóa lịch sử khi `HistoryRecorder` đang ghi: `deleteBetween` bao cả mốc tương lai do lệch đồng hồ (spec 012).
- Máy đổi giờ lùi: `LastHour` tính theo `now` hiện tại; dữ liệu ghi "tương lai" cũng bị xóa (giữ hành vi `HistoryClearer`).
- Nâng cấp HTTPS cho URL có userinfo (`http://u:p@host`): giữ userinfo trong URL nâng cấp (hiếm); không hiện userinfo trong trang cảnh báo.
- `http://` tới cổng khác 80 (dev server) ở `HttpsFirst` không nâng cấp, không cảnh báo; ở `HttpsOnly` thử `https://host:port` rồi fallback không có nút tiếp tục.
- Một site vừa không hỗ trợ HTTPS vừa nằm trong dấu trang `http://`: mở dấu trang đi qua cùng chính sách.
- Link `http://` trong tab ẩn danh: cùng chính sách; ngoại lệ chỉ trong session của tab.
- Server có HSTS/lỗi chứng chỉ không cho ghi đè: nếu WebView không gọi `onReceivedSslError` (huỷ sẵn) thì người dùng thấy trang lỗi mặc định của Chromium; ghi nhận, kiểm thủ công với `badssl.com`.
- Máy không có GMS: không có cảnh báo Safe Browsing; công tắc vẫn hiện kèm ghi chú (AC24).
- Captive portal (`http://` bị chặn bởi mạng): `HttpsFirst` → fallback; người dùng chọn "Tiếp tục" cho host đó để đăng nhập Wi-Fi (kịch bản chính của nút tiếp tục).
- Trang chuyển hướng `https://a.com` → `http://b.com`: `b.com` được nâng cấp riêng; nếu `b.com` không có https ⇒ fallback cho `b.com`.
- Nhiều lần bấm xóa liên tiếp: nút disabled trong lúc chạy; xoay màn hình khi đang chạy không hủy tác vụ (chạy trong scope của ViewModel/Application).
- Người dùng gỡ Play Services sau khi bật Safe Browsing: không lỗi, chỉ mất bảo vệ.

## Không test được bằng Robolectric (instrumented / thủ công)
- Nâng cấp HTTPS và fallback thật (`neverssl.com`, một site http-only, captive portal), `ERROR_*` thật, vòng lặp chuyển hướng http↔https. → `HttpsUpgradeInstrumentedTest` (máy chủ cục bộ) + thủ công.
- Lỗi chứng chỉ thật: `badssl.com` (expired, wrong.host, self-signed, untrusted-root, revoked), HSTS không cho override, xem vân tay khớp `openssl s_client`. → thủ công.
- Safe Browsing thật: `testsafebrowsing.appspot.com` (malware/phishing/unwanted), máy không có GMS; `backToSafety` đưa tab về trang nào. → thủ công.
- `CookieManager`/`WebStorage`/`clearCache` thật: đăng nhập site rồi xóa → bị đăng xuất; cookie bên thứ ba bị chặn trên trang thử (iframe set cookie). → `ClearDataInstrumentedTest` + thủ công.
- `ERR_CLEARTEXT_NOT_PERMITTED` trước/sau khi thêm network security config. → `CleartextInstrumentedTest`.
- TalkBack đọc trang cảnh báo, cỡ chữ 200%. → thủ công.

## Tests liên quan
- `:core:privacy` JVM: `HttpsPolicyTest` (AC1), `BrowsingDataCleanerTest` (AC2).
- `:core:engine`: JVM `SslMappingTest`, `ThreatMappingTest` (AC4), `FakeEngineSessionPrivacyTest`, `EngineRequestResponseTableTest`; `EngineSessionContractTest` bổ sung AC9–AC17 (Fake + WebView), `WebViewEngineSessionTest` (AC18), `AndroidWebDataCleanerTest` (AC8).
- `:core:data`: `DataStorePrivacySettingsStoreTest` (AC5).
- `:feature:settings`: JVM `ClearDataStateTest` (AC3); Robolectric `ClearDataPageTest` (AC23), `HttpsModePageTest`/`SafeBrowsingPageTest`/`CookiesPageTest` (AC24), `SettingsRegistryTest`.
- `:feature:browser`: JVM `BrowserViewModelInterstitialTest` (AC20); Robolectric `SecurityInterstitialTest` (AC21), `BrowserScreenInterstitialTest` (AC22).
- `:app`: `PixelBrowserApplicationPrivacyTest` (AC6), `ClearTargetsTest` (AC7), `ManifestSecurityTest` (AC19), `MainActivityPrivacyTest` (AC25), `SettingsPagesTest`.
- Instrumented: `HttpsUpgradeInstrumentedTest`, `CleartextInstrumentedTest`, `ClearDataInstrumentedTest`, `SecurityInterstitialInstrumentedTest`.

## Out of scope
- Do-Not-Track / Global Privacy Control, chặn tracker, strip tracking params, fingerprint hardening (C02, M3).
- Xóa theo thời gian cho cookie/cache (WebView không hỗ trợ); "xóa khi thoát ứng dụng"; xóa dấu trang/mật khẩu (B10) từ trang này; lịch trình xóa tự động.
- Ngoại lệ HTTPS/chứng chỉ lưu bền theo site (B07 Site settings); danh sách ngoại lệ có thể chỉnh.
- Nâng cấp subresource http (C01-ish), HSTS preload list riêng, certificate transparency, ghim chứng chỉ, DNS-over-HTTPS.
- Báo cáo Safe Browsing / "Safe Browsing nâng cao" (cần gửi dữ liệu); Safe Browsing offline.
- Tùy biến chủ đề trang cảnh báo ngoài theme hiện có; đưa trang cảnh báo vào WebView.

## Quyết định
- (spec-writer, 2026-11-10) Module mới `:core:privacy` thuần Kotlin để engine/data cùng dùng `HttpsMode`/`HttpsPolicy` mà không tạo vòng phụ thuộc; adapter (history, engine, thumbnails) nằm ở `:app`.
- (spec-writer) Mặc định `HttpsFirst`; fallback luôn qua trang cảnh báo native, ngoại lệ chỉ trong session của tab; `HttpsOnly` không có đường hạ xuống http.
- (spec-writer) Lỗi chứng chỉ trên điều hướng đã nâng cấp đi tới trang chứng chỉ, **không** hạ xuống http.
- (spec-writer) Cookie/cache không xóa theo thời gian: hiển thị minh bạch và xác nhận thay vì cố mô phỏng.
- (spec-writer) Thêm `network_security_config` cho phép cleartext ở tầng OS (kèm `trust-anchors` chỉ `system`), chính sách http nằm ở engine; cần xác nhận lỗi `ERR_CLEARTEXT_NOT_PERMITTED` hiện hữu bằng instrumented test trước khi sửa (ghi kết quả vào mục này).
- **Q3 (mở):** mặc định chặn cookie bên thứ ba ở tab thường? Đề xuất: giữ "cho phép" tới C02 để không hỏng đăng nhập nhúng/thanh toán.
- (spec-writer) Cookie bên thứ ba: mặc định cho phép ở tab thường, luôn chặn ở tab ẩn danh; C02 xem xét đổi mặc định.

## Quyết định (orchestrator, 2026-11-10)
- Chấp nhận các đề xuất của spec-writer: cách ly ẩn danh bằng WebView `Profile` (androidx.webkit `MULTI_PROFILE`), tên profile không tái dùng, fail-closed khi thiếu API; cookie bên thứ ba ở tab thường giữ "cho phép" tới C02 (tab ẩn danh luôn chặn); tải xuống ghi `.part` rồi xuất bản (2× dung lượng tạm thời, tối ưu sau); FGS `dataSync` trước; cài APK thử `ACTION_VIEW` với dự phòng mở ứng dụng Tệp.
- Các mục chỉ kiểm được trên máy thật (Profile isolation, cleartext/network security config, SAF/MediaStore, notification, blob:/data:) được ghi vào E05 và chạy thủ công/instrumented khi có thiết bị; không chặn merge nếu phần JVM/Robolectric xanh.
- Thứ tự triển khai: 016 → 017 → 018 → 019; 019 chia 4 PR (019A lõi, 019B service/thông báo, 019C màn hình/cài đặt/APK, 019D blob:/data:). Việc sửa `network_security_config` (cleartext) trong 017 làm sớm vì ảnh hưởng mọi trang http.
