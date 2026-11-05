# Spec 006: Intent từ app khác, trình duyệt mặc định, mở link sang app ngoài (A12)
## Story
Người dùng bấm một link trong app khác (chat, email) và trang mở ngay trong Pixel ở tab mới; chọn "Tìm bằng Pixel" khi bôi đen chữ, chia sẻ một link/đoạn chữ sang Pixel, hoặc đặt Pixel làm trình duyệt mặc định từ menu. Ngược lại, khi trang web có link `tel:`, `mailto:`, `market://`, `intent://`, Pixel mở app tương ứng một cách an toàn: không bao giờ tự mở app khác nếu người dùng không thao tác, không để trang lợi dụng intent để gọi component tùy ý.

## Phạm vi & module
- Phụ thuộc spec 005 (`:feature:browser`, `BrowserViewModel`, `BrowserUiState.message`, menu, Back hệ thống AC31). Spec này implement **sau** 005 và **trước** 007.
- `:core:engine`: hạ tầng `EngineRequest` (dùng chung với spec 007) + chính sách điều hướng `NavigationPolicy` (Kotlin thuần, test JVM).
- `:feature:browser`, package `app.pixel.browser.feature.browser.intent`:
  - Lớp thuần JVM: `data class IncomingIntent`, `object IncomingIntentResolver`, `sealed interface IncomingRequest`, `RejectReason`.
  - Lớp Android (Robolectric): `fun IncomingIntent.Companion.from(intent: Intent): IncomingIntent`, `object ExternalIntents`, `interface ExternalAppLauncher` + `AndroidExternalAppLauncher(activity)`, `interface DefaultBrowserController` + `RoleManagerDefaultBrowser(context)`.
- `:app`: `AndroidManifest.xml` (intent-filter, `launchMode`), `MainActivity` (`onCreate`/`onNewIntent`).
- Test tag mới (thêm vào `BrowserTestTags`): `menu_set_default`, `external_dialog`, `external_open`, `external_cancel`.

## Thay đổi API
### `:core:engine` – hạ tầng EngineRequest (spec 007 mở rộng thêm subtype)
```kotlin
/** Yêu cầu từ trang cần tầng UI xử lý. Mỗi request được trả lời đúng một lần. */
sealed interface EngineRequest {
    val id: Long          // duy nhất trong một session, tăng dần từ 1
    val origin: String    // originOf(url của frame/trang tạo request); "" nếu không xác định

    data class ExternalApp(
        override val id: Long, override val origin: String,
        val uri: String, val hasUserGesture: Boolean, val isRedirect: Boolean,
    ) : EngineRequest
}

sealed interface EngineResponse {
    data class Dismiss(val suppressFurther: Boolean = false) : EngineResponse
    // spec 007 thêm: Confirm, PromptText, Files, Grant, Credentials, AttachWindow
}

interface EngineSession {
    // ... (spec 001–005)
    /** Hàng đợi FIFO các request chưa trả lời. */
    val requests: StateFlow<List<EngineRequest>>
    /** Trả lời request; false nếu id không tồn tại/đã trả lời/response không hợp lệ với loại request (request giữ nguyên). */
    fun respond(requestId: Long, response: EngineResponse): Boolean
}

/** `scheme://host[:port]` chữ thường, bỏ port mặc định; URL không phải http/https → "". */
fun originOf(url: String): String

enum class NavigationDecision { LoadInEngine, Block, External }
object NavigationPolicy { fun decide(url: String, isMainFrame: Boolean): NavigationDecision }

/** Helper dùng chung cho WebView và Fake: cấp id, giữ danh sách, gọi callback khi trả lời. */
class EngineRequestQueue {
    val requests: StateFlow<List<EngineRequest>>
    fun <T : EngineRequest> enqueue(build: (id: Long) -> T, onResponse: (T, EngineResponse) -> Boolean): T
    fun respond(requestId: Long, response: EngineResponse): Boolean
    fun cancel(requestId: Long)   // engine tự hủy (vd. onPermissionRequestCanceled), không gọi onResponse
    fun dismissAll()              // dùng khi close(): trả Dismiss() cho mọi request còn treo
}
```
- `WebViewEngineSession`: override `shouldOverrideUrlLoading(view, WebResourceRequest)` → `dispatchNavigation(url, isMainFrame, hasGesture, isRedirect): Boolean` (public như các `dispatch*` khác, để test gọi trực tiếp).
- `FakeEngineSession`: `fun simulateNavigation(url: String, isMainFrame: Boolean = true, hasUserGesture: Boolean = true, isRedirect: Boolean = false): Boolean` (true = engine đã chặn điều hướng); `val responses: Map<Long, EngineResponse>` ghi mọi response đã được chấp nhận (kể cả `dismissAll`).

### `:core:tabs`
- Không đổi API. Tab mở từ intent ngoài được `BrowserViewModel` ghi nhận trong bộ nhớ (`Set<String>` id) cho AC14.

### `:feature:browser`
```kotlin
data class IncomingIntent(
    val action: String?, val dataString: String?, val type: String?,
    val extraText: String?,      // Intent.EXTRA_TEXT
    val query: String?,          // SearchManager.QUERY
    val processText: String?,    // Intent.EXTRA_PROCESS_TEXT (CharSequence → String)
    val categories: Set<String>,
    val launchedFromHistory: Boolean, // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
) { companion object }

sealed interface IncomingRequest {
    data class OpenUrl(val url: String) : IncomingRequest
    data class Search(val query: String, val url: String) : IncomingRequest
    data object Resume : IncomingRequest
    data class Rejected(val reason: RejectReason) : IncomingRequest
}
enum class RejectReason { UnsupportedScheme, InvalidUrl, Empty }

object IncomingIntentResolver {
    const val MAX_QUERY_LENGTH = 2048
    fun resolve(intent: IncomingIntent, engine: SearchEngine): IncomingRequest
}

// BrowserViewModel (spec 005) thêm:
fun onIncomingIntent(request: IncomingRequest)
fun onExternalAppDecision(requestId: Long, open: Boolean)
fun onDefaultBrowserChanged(isDefault: Boolean)
// BrowserViewModel nhận thêm tham số constructor: externalAppLauncher: ExternalAppLauncher
// BrowserUiState thêm: showSetDefaultBrowser: Boolean, externalPrompt: EngineRequest.ExternalApp?

object ExternalIntents {
    /** Parse + làm sạch URI ngoài. null nếu không hợp lệ/bị cấm. */
    fun parse(uri: String, ownPackage: String): ParsedExternal?
}
data class ParsedExternal(val intent: Intent?, val fallbackUrl: String?, val webUrl: String?)

sealed interface ExternalLaunchResult {
    data object Launched : ExternalLaunchResult
    data class NotFound(val fallbackUrl: String?) : ExternalLaunchResult
    data class OpenInBrowser(val url: String) : ExternalLaunchResult
    data object Rejected : ExternalLaunchResult
}
fun interface ExternalAppLauncher { fun launch(uri: String): ExternalLaunchResult }

interface DefaultBrowserController {
    val isRoleAvailable: Boolean
    fun isDefault(): Boolean
    /** Intent của RoleManager nếu khả dụng, ngược lại Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS. */
    fun requestIntent(): Intent
}
```

## Acceptance criteria
### Hạ tầng EngineRequest & điều hướng (`:core:engine`)
1. **AC1 – Hàng đợi.** Given session mới, Then `requests.value` rỗng. When engine tạo 2 request, Then `requests.value` có 2 phần tử theo thứ tự tạo, `id` lần lượt 1, 2. When `respond(1, Dismiss())`, Then trả `true`, request 1 biến mất, request 2 còn; `respond(1, Dismiss())` lần nữa → `false`; `respond(99, Dismiss())` → `false`. (`EngineRequestQueueTest`, JVM)
2. **AC2 – Close.** Given 2 request đang treo, When `close()`, Then `requests.value` rỗng, callback engine của cả hai nhận `Dismiss()`, `respond` sau đó → `false`. (`EngineRequestQueueTest`, `EngineSessionContractTest` cho Fake + WebView)
3. **AC3 – `originOf`.** `https://Example.com/a?b` → `https://example.com`; `https://example.com:443/` → `https://example.com`; `http://example.com:8080/x` → `http://example.com:8080`; `http://[::1]:80/` → `http://[::1]`; `about:blank`, `file:///a`, `""`, `"%%"` → `""`. (`OriginTest`, JVM)
4. **AC4 – `NavigationPolicy`.** Bảng (main frame): `https://a.com`, `http://a.com` → `LoadInEngine`; `about:blank`, `about:srcdoc`, `data:text/html,x`, `blob:https://a.com/uuid`, `javascript:void(0)` → `LoadInEngine` (engine tự xử lý theo cài đặt an toàn của spec 001); `file:///sdcard/a`, `content://x/y` → `Block`; `tel:123`, `mailto:a@b.c`, `sms:1`, `geo:0,0`, `market://details?id=x`, `intent://scan#Intent;scheme=zxing;end`, `myapp://open`, `HTTPS://A.COM` (so scheme không phân biệt hoa thường → `LoadInEngine`) → như trên theo scheme. Subframe: mọi URL có quyết định `External` → `Block`. URL không parse được scheme → `Block`. (`NavigationPolicyTest`, JVM, parameterized)
5. **AC5 – Engine chặn và phát request.** Given session, When `simulateNavigation("tel:123", isMainFrame = true, hasUserGesture = true)`, Then trả `true`, `state.url` không đổi, `requests.value == [ExternalApp(id=1, origin=originOf(state.url), uri="tel:123", hasUserGesture=true, isRedirect=false)]`. When `simulateNavigation("tel:123", isMainFrame = false)`, Then trả `true`, không có request mới. When `simulateNavigation("https://a.com")`, Then trả `false`, không có request. When `simulateNavigation("file:///sdcard/a")`, Then `true`, không có request. Cùng case cho WebView qua `dispatchNavigation`. (`EngineSessionContractTest`, Fake JVM + WebView Robolectric)
6. **AC6 – Response hợp lệ.** `ExternalApp` chấp nhận `Dismiss()` và `Dismiss(suppressFurther = true)` (cờ bị bỏ qua); `onResponse` trả `false` → `respond` trả `false` và request còn trong hàng đợi (test bằng request giả trong `EngineRequestQueueTest` với `onResponse = { _, _ -> false }`). Kiểm response sai loại cho từng subtype thuộc spec 007 AC1. (`EngineRequestQueueTest`)

### Intent vào (`IncomingIntentResolver`, JVM thuần, engine = Google)
7. **AC7 – VIEW http/https.** `action=VIEW, data="https://example.com/a?b=1"` → `OpenUrl("https://example.com/a?b=1")`; `data="HTTP://Example.com"` → `OpenUrl("http://example.com")` (chuẩn hóa qua `OmniboxClassifier`, chỉ chấp nhận kết quả `Navigate` có scheme http/https). (`IncomingIntentResolverTest`)
8. **AC8 – VIEW bị từ chối.** `data` là `javascript:alert(1)`, `file:///data/data/app.pixel.browser/x`, `content://com.android.contacts/1`, `intent://x#Intent;end`, `data:text/html,x`, `about:blank`, `ftp://a.com`, `market://details?id=x` → `Rejected(UnsupportedScheme)`; `https://`, `https://user@evil.com`, `https://exa mple.com` → `Rejected(InvalidUrl)` (không bao giờ biến thành `Search`); `data = null` → `Rejected(Empty)`. Quy tắc: scheme (không phân biệt hoa thường) khác `http`/`https` → `UnsupportedScheme`; là http/https nhưng `OmniboxClassifier` không trả `Navigate` → `InvalidUrl`. (`IncomingIntentResolverTest`)
9. **AC9 – Tìm kiếm (WEB_SEARCH / SEARCH / PROCESS_TEXT).** Với mỗi action `android.intent.action.WEB_SEARCH`, `android.intent.action.SEARCH` (chuỗi lấy từ `query`) và `android.intent.action.PROCESS_TEXT` (chuỗi từ `processText`): `"hello world"` → `Search("hello world", "https://www.google.com/search?q=hello%20world")`; `"example.com"` → `OpenUrl("https://example.com")`; `"javascript:alert(1)"` → `Rejected(UnsupportedScheme)`; `""`/`null`/`"  "` → `Rejected(Empty)`. Chuỗi dài hơn `MAX_QUERY_LENGTH` bị cắt còn 2048 ký tự (không cắt đôi cặp surrogate) trước khi phân loại. (`IncomingIntentResolverTest`)
10. **AC10 – SEND text/plain.** `action=SEND, type="text/plain"`: `extraText="https://example.com/a"` → `OpenUrl("https://example.com/a")`; `"Xem cái này https://example.com/a nhé"` → `OpenUrl("https://example.com/a")` (token đầu tiên khớp `https?://\S+`, bỏ dấu câu cuối `.,;:!?)]}'"`); `"phở bò"` → `Search("phở bò", …)`; `"javascript:alert(1)"` → `Rejected(UnsupportedScheme)`; `extraText = null` → `Rejected(Empty)`; `type = "image/png"` → `Rejected(Empty)`. (`IncomingIntentResolverTest`)
11. **AC11 – Launcher / lịch sử.** `action=MAIN` có category `LAUNCHER`, `action = null`, action không hỗ trợ (`android.intent.action.EDIT`), hoặc bất kỳ intent nào có `launchedFromHistory = true` → `Resume`. (`IncomingIntentResolverTest`)

### Đọc Intent an toàn (Robolectric)
12. **AC12 – `IncomingIntent.from`.** Given `Intent(ACTION_VIEW, Uri.parse("https://a.com"))` kèm extra `Browser.EXTRA_HEADERS` (Bundle `Cookie=x`), `com.android.browser.post_data`, `Intent.EXTRA_REFERRER`, `Browser.EXTRA_APPLICATION_ID`, selector và component lạ, Then `IncomingIntent.from(intent) == IncomingIntent(action = VIEW, dataString = "https://a.com", type = null, extraText = null, query = null, processText = null, categories = emptySet(), launchedFromHistory = false)` (các extra đó không được đọc) và `resolve` cho `OpenUrl("https://a.com")`. Given `EXTRA_TEXT` là `Int` (sai kiểu) hoặc `EXTRA_PROCESS_TEXT` là `SpannableString`, Then `from` không ném, lần lượt `extraText = null` và `processText = "<chuỗi thuần>"`. Given extras ném `BadParcelableException`/`RuntimeException` khi đọc (Intent con ghi đè `getStringExtra`), Then mọi trường extra = null, không crash. (`IncomingIntentTest`)

### Tab và vòng đời Activity
13. **AC13 – Mở ở tab mới / tái dùng tab home trống.** Given TabManager có tab A (`https://a.com`) được chọn, When `onIncomingIntent(OpenUrl("https://b.com"))`, Then có thêm 1 tab ở cuối, được chọn, `loadUrl("https://b.com")`; tab A giữ nguyên. Given tab duy nhất là tab home chưa điều hướng (`url == HOME_URL`, `canGoBack = false`), Then URL được tải vào chính tab đó (`tabs.size` vẫn 1). `Search(q, url)` xử lý như `OpenUrl(url)`. `Resume` → không đổi tab, không `loadUrl`. `Rejected` → không đổi tab, `uiState.message != null`. (`BrowserViewModelIntentTest`, JVM)
14. **AC14 – Back trên tab mở từ app ngoài.** Given tab được mở bởi AC13 (kể cả trường hợp tái dùng tab home) và `canGoBack = false`, When Back hệ thống, Then tab đó bị `closeTab`, và activity gọi `moveTaskToBack(true)` (quay về app gọi); Given `canGoBack = true` → `goBack()` như spec 005 AC31(c). Tab không mở từ intent giữ nguyên AC31(d). (`BrowserBackHandlingTest`, Robolectric: `shadowOf(activity)` kiểm `moveTaskToBack`/`isFinishing`)
15. **AC15 – Cold start & onNewIntent.** `Robolectric.buildActivity(MainActivity, Intent(ACTION_VIEW, "https://b.com"))` với store có sẵn tab A → sau `setup()` có A và tab mới `https://b.com` được chọn. Với activity đang chạy, `controller.newIntent(Intent(ACTION_VIEW, "https://c.com"))` → thêm đúng 1 tab, không tạo Activity mới (`activity` cùng instance), `activity.intent` là intent mới. `newIntent(launcher intent)` → số tab không đổi. (`MainActivityIntentTest`)
16. **AC16 – Không xử lý lại intent cũ.** Given activity đã xử lý VIEW `https://b.com`, When `controller.recreate()` (xoay màn hình, `savedInstanceState != null`), Then số tab không đổi (intent chỉ được xử lý khi `savedInstanceState == null` hoặc qua `onNewIntent`). Given intent có `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, Then không mở tab mới (AC11). (`MainActivityIntentTest`)
17. **AC17 – Manifest.** Parse `AndroidManifest.xml` đã merge (Robolectric `PackageManager.queryIntentActivities`): `Intent(VIEW, "https://x.com").addCategory(BROWSABLE)` và với `http://` → resolve về `MainActivity`; `Intent(VIEW, "file:///a")`, `content://…`, `javascript:…` (BROWSABLE) → không resolve về app; `ACTION_WEB_SEARCH`, `ACTION_SEARCH`, `ACTION_PROCESS_TEXT` (type `text/plain`), `ACTION_SEND` (type `text/plain`) → resolve về `MainActivity`; `ACTION_SEND` type `image/*` → không. `MainActivity` có `launchMode = singleTask` (`ActivityInfo.LAUNCH_SINGLE_TASK`). Filter PROCESS_TEXT có label từ resource `R.string.process_text_label` ("Tìm bằng Pixel"). (`ManifestIntentFilterTest`)

### Trình duyệt mặc định
18. **AC18 – Hiện mục menu.** Given `ShadowRoleManager` có role `ROLE_BROWSER` khả dụng và app chưa giữ, When mở menu, Then có `menu_set_default`; Given app đã giữ role, hoặc role không khả dụng, Then không có `menu_set_default`. `showSetDefaultBrowser` được tính lại ở mỗi `ON_RESUME` (đổi role trong Settings rồi quay lại → mục biến mất mà không cần khởi động lại). (`DefaultBrowserTest`, Robolectric)
19. **AC19 – Yêu cầu role.** Tap `menu_set_default` → activity được start-for-result với intent bằng `roleManager.createRequestRoleIntent(ROLE_BROWSER)`, menu đóng. Kết quả `RESULT_OK` và role đã giữ → `showSetDefaultBrowser = false`; `RESULT_CANCELED` → mục vẫn hiện, không message lỗi. Khi role không khả dụng, hoặc sau 2 lần `RESULT_CANCELED` liên tiếp trong cùng process (RoleManager có thể tự hủy khi người dùng đã chọn "Không hỏi lại"), `requestIntent()` trả intent `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`. (`DefaultBrowserTest`)

### Link sang app ngoài
20. **AC20 – `ExternalIntents.parse` cho `intent:`.** `intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fapp;end` → `intent.data = "zxing://scan/"`, `intent.package = "com.google.zxing.client.android"`, có category `BROWSABLE`, `fallbackUrl = "https://example.com/app"`. Với `...;component=com.evil/.Exported;SEL;action=x;end` (component + selector) → `intent.component == null`, `intent.selector == null`. `launchFlags=0x43` (gồm `FLAG_GRANT_READ/WRITE_URI_PERMISSION`) → `intent.flags == 0`. `clipData == null`. (`ExternalIntentsTest`, Robolectric)
21. **AC21 – Từ chối.** `parse` trả `null` cho: `intent:` không parse được (`URISyntaxException`); `package` = `ownPackage`; data scheme `file`/`content`/`javascript`; `action` khác `ACTION_VIEW` (`android.intent.action.CALL`, `SEND`, …) – chỉ cho phép `VIEW` (mặc định khi thiếu action); `fallbackUrl` không phải http/https (`javascript:…`) → `fallbackUrl = null` (intent vẫn hợp lệ). (`ExternalIntentsTest`)
22. **AC22 – Scheme thường và `intent:` trỏ về web.** `tel:123`, `mailto:a@b.c`, `market://details?id=x` → `Intent(ACTION_VIEW, uri)` + `BROWSABLE` (không bao giờ `ACTION_CALL`). `intent://example.com/a#Intent;scheme=https;end` (không package) → `intent = null`, `webUrl = "https://example.com/a"`. (`ExternalIntentsTest`)
23. **AC23 – Có user gesture → mở luôn.** Given request `ExternalApp(uri="tel:123", hasUserGesture=true)` trên tab đang chọn, Then ViewModel gọi `externalAppLauncher.launch("tel:123")` đúng 1 lần, `respond(id, Dismiss())`, không hiện `external_dialog`. Kết quả `NotFound(fallbackUrl = "https://example.com/app")` → `loadUrl("https://example.com/app")` trên tab đó; `NotFound(null)` hoặc `Rejected` → `uiState.message != null`, không `loadUrl`; `OpenInBrowser(url)` → `loadUrl(url)`. (`BrowserViewModelExternalAppTest`, JVM với fake launcher)
24. **AC24 – Không có gesture → hỏi.** Given `hasUserGesture=false`, Then `launch` **không** được gọi; `uiState.externalPrompt` = request và `external_dialog` hiển thị (nêu scheme, với `intent:` nêu package nếu có). Tap `external_open` → `launch` 1 lần rồi xử lý kết quả như AC23; tap `external_cancel` hoặc Back → không `launch`; cả hai trường hợp `respond(id, Dismiss())` và dialog đóng. Nếu request không gesture có `browser_fallback_url` hợp lệ thì vẫn hỏi (không tự tải fallback). (`BrowserViewModelExternalAppTest`, `ExternalAppDialogTest` Compose)
25. **AC25 – Chống spam.** Given `external_dialog` đang hiện, When trang phát thêm 5 request `ExternalApp` không gesture, Then các request đó bị `respond(Dismiss())` ngay, dialog vẫn chỉ hiện request đầu, `launch` không được gọi. Given request phát ra từ tab nền (không được chọn), Then không hiện dialog cho tới khi tab đó được chọn; request đã có sẵn trong hàng đợi lúc tab được chọn luôn đi theo AC24 (hỏi), kể cả `hasUserGesture = true` – chỉ request xuất hiện khi tab đang được chọn mới được mở ngay theo AC23. (`BrowserViewModelExternalAppTest`)
26. **AC26 – Launcher Android.** `AndroidExternalAppLauncher.launch("tel:123")` → `shadowOf(activity).nextStartedActivity` có `action = VIEW`, `data = tel:123`; activity ngoài không tồn tại (`ActivityNotFoundException`) → `NotFound(fallbackUrl)` của `parse`; `parse == null` → `Rejected` và không start activity nào. (`AndroidExternalAppLauncherTest`, Robolectric)

## Edge cases
- Intent VIEW tới chính Pixel từ trang trong Pixel (link `intent:` có `package=app.pixel.browser`) → bị từ chối (AC21), tránh vòng lặp và tránh trang tự gửi extra vào activity của mình.
- Intent explicit (component trỏ thẳng `MainActivity`) vượt qua intent-filter: mọi kiểm tra scheme nằm trong `IncomingIntentResolver`, không dựa vào manifest.
- `Browser.EXTRA_HEADERS`, post data, referrer, `EXTRA_APPLICATION_ID`, `EXTRA_CREATE_NEW_TAB`, `EXTRA_STREAM`, `EXTRA_SUBJECT` bị bỏ qua hoàn toàn (không đọc). `FLAG_GRANT_*` trên intent vào không được dùng để đọc URI.
- URL trong intent có fragment/Unicode: giữ nguyên path/query, host IDN → Punycode (spec 004 AC9).
- Intent đến khi đang sửa URL hoặc menu/sheet đang mở: thoát chế độ sửa, đóng menu/sheet rồi mới mở tab.
- Intent đến khi tab đang fullscreen (spec 007): thoát fullscreen trước.
- Process death với intent VIEW đang chờ: Android tạo lại activity với `savedInstanceState != null` → không xử lý lại (AC16); tab đã được lưu theo spec 002.
- `PROCESS_TEXT` có `EXTRA_PROCESS_TEXT_READONLY = false`: không trả kết quả (`setResult` không được gọi), chỉ tìm kiếm.
- RoleManager trả `RESULT_CANCELED` ngay khi người dùng đã từ chối nhiều lần: mục menu vẫn hiện, chuyển sang mở Settings (AC19). Bộ đếm chỉ nằm trong bộ nhớ.
- `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` không có activity (ROM lạ) → fallback `Settings.ACTION_SETTINGS`; vẫn không có → message.
- `market://` khi không có store → `NotFound(null)` → message. Không tự chuyển sang `play.google.com`.
- Redirect server-side tới scheme ngoài (`isRedirect = true`) kế thừa gesture từ engine; nếu WebView báo `hasGesture = false` thì đi theo AC24 (hỏi).
- `about:`/`data:`/`blob:` được để engine xử lý: top-level `data:` đã bị Chromium chặn; `javascript:` không đi qua `shouldOverrideUrlLoading`.
- App links (URL http/https có app native tương ứng, vd. YouTube) luôn mở trong Pixel – không chuyển sang app.

## Không test được bằng Robolectric (instrumented / thủ công)
- Hộp thoại hệ thống của `RoleManager` và việc hệ thống thực sự đổi trình duyệt mặc định; Pixel xuất hiện trong Settings → Default apps → Browser app. → thủ công trên emulator API 34+.
- Mục "Tìm bằng Pixel" trong menu chọn văn bản của app khác, Pixel trong share sheet, mở link từ app khác thật (`adb shell am start -a android.intent.action.VIEW -d https://example.com`, `am start -n app.pixel.browser/.MainActivity -d file:///sdcard/x` phải bị từ chối). → `ExternalIntentInstrumentedTest` (khi có KVM) + thủ công.
- Giá trị thật của `WebResourceRequest.hasGesture()/isRedirect()` với link do người dùng bấm, redirect 302 sang `market://`, `window.location = "intent:…"` không gesture (Robolectric không chạy JS). → thủ công với trang test cục bộ.
- Khởi chạy dialer/mail/store thật và package visibility trên Android 11+ (`startActivity` thay vì `resolveActivity`, không cần `<queries>`). → thủ công.
- `moveTaskToBack` thực sự quay về app gọi (task stack). → thủ công.

## Tests liên quan
- `:core:engine` (JVM): `EngineRequestQueueTest` (AC1, AC2, AC6), `OriginTest` (AC3), `NavigationPolicyTest` (AC4); `EngineSessionContractTest` bổ sung AC2, AC5 (chạy cho `FakeEngineSessionContractTest` JVM và `WebViewEngineSessionTest` Robolectric).
- `:feature:browser`:
  - JVM thuần: `IncomingIntentResolverTest` (AC7–AC11), `BrowserViewModelIntentTest` (AC13), `BrowserViewModelExternalAppTest` (AC23–AC25).
  - Robolectric: `IncomingIntentTest` (AC12), `ExternalIntentsTest` (AC20–AC22), `AndroidExternalAppLauncherTest` (AC26), `DefaultBrowserTest` (AC18, AC19, dùng `ShadowRoleManager`), `ExternalAppDialogTest` (AC24, Compose), `BrowserBackHandlingTest` bổ sung AC14.
- `:app` (Robolectric): `MainActivityIntentTest` (AC15, AC16), `ManifestIntentFilterTest` (AC17).
- Instrumented: `ExternalIntentInstrumentedTest`.

## Out of scope
- Custom Tabs (`CustomTabsService`, `androidx.browser`), Trusted Web Activity.
- Mở App Links http/https sang app native; "luôn mở bằng app X" theo site (B07).
- Màn hình Cài đặt có mục trình duyệt mặc định (B14) – spec này chỉ có mục menu.
- Tab ẩn danh từ intent (B06), `EXTRA_HEADERS` (bỏ qua có chủ đích), POST data từ intent.
- Tải file (`DownloadListener`) – spec 007/B03. Mọi `EngineRequest` khác ngoài `ExternalApp` – spec 007.
- `ACTION_ASSIST`, `android.speech.action.WEB_SEARCH`, NFC, shortcut launcher.

## Quyết định (orchestrator, 2026-11-05)
- `EngineRequest`/`EngineRequestQueue` được tạo trong spec này; 007 mở rộng.
- Intent đến luôn mở tab mới, trừ khi tái dùng tab home ban đầu chưa dùng; không dựa vào `EXTRA_APPLICATION_ID`.
- Link sang app khác: có user gesture → mở thẳng; không có gesture hoặc tab nền → hỏi.
- `launchMode=singleTask` + `onNewIntent`.

## Quyết định khi implement (implementer, 2026-11-05)
- "Tab home chưa dùng" (AC13) = tab home mà `BrowserViewModel` tự tải `homeUrl` vào (lúc khởi tạo hoặc sau khi đóng tab cuối), đang được chọn, `canGoBack == false` và `state.url` là `""` hoặc `homeUrl` (dù trang home đã tải xong hay chưa).
- `AndroidExternalAppLauncher` nhận `Context` thay vì `Activity`: `MainActivity` truyền application context để `ViewModel` không giữ Activity; khi context không phải Activity, intent được thêm `FLAG_ACTIVITY_NEW_TASK`. Không dùng `<queries>`: gọi `startActivity` và bắt `ActivityNotFoundException` (và `SecurityException`) → `NotFound(fallbackUrl)`.
- `ExternalIntents.parse` dựng intent mới từ đầu (chỉ `ACTION_VIEW`, data/type, package, `CATEGORY_BROWSABLE`): bỏ component, selector, extras, clipData, mọi cờ. Ngoài `file`/`content`/`javascript`, scheme `data`/`about`/`blob`/`filesystem` cũng bị từ chối. `intent:` thiếu cả data lẫn package → `null`.
- `NavigationPolicy`: `filesystem:` cũng `Block`. Helper chung `schemeOf(url)` trong `:core:engine`.
- AC25 chống spam áp dụng cho mọi session: request `ExternalApp` mới khi session đó đã có request đang chờ → `Dismiss()` ngay (kể cả tab nền, kể cả có gesture). Tab nền chỉ giữ request đầu tiên.

### Sau code review (2026-11-06)
- Hộp thoại không tự parse URI thô: nội dung lấy từ `ExternalIntents.describe(uri, ownPackage): ExternalTarget?` – dựng trên chính `parse` mà `AndroidExternalAppLauncher` dùng – hiển thị `intent.package` nếu có, ngược lại scheme của data (không bao giờ là `intent`). Package sau `SEL;` thuộc selector (bị bỏ) nên không hiển thị; `package=` lặp → hiển thị đúng package sẽ mở.
- `ExternalAppLauncher.describe(uri)` (mặc định chỉ nêu scheme, dành cho bản giả; bản Android dùng `ExternalIntents.describe`). ViewModel gọi `describe` cho mọi request mới: `null` → `Dismiss()` + message `external_app_blocked`, không hỏi, không mở.
- Bẫy hộp thoại lặp: sau khi người dùng hủy, mọi request không gesture tiếp theo của tab đó nhận `Dismiss(suppressFurther = true)` cho tới khi `state.url` của tab đổi (trang mới) hoặc có request có gesture.
- Một chạm mở nhiều app: mỗi đợt xử lý chỉ mở thẳng tối đa một request (các request còn lại trong đợt bị bỏ), và mỗi tab mở thẳng tối đa 1 lần/1 giây (`DIRECT_LAUNCH_INTERVAL_MS`); request có gesture trong khoảng đó đi qua hộp thoại. Đồng hồ inject qua tham số `clock` của `BrowserViewModel`.
- Chỉ mở thẳng khi app hiển thị (lifecycle ≥ STARTED, `BrowserViewModel.onAppVisibilityChanged`, do `BrowserRoute` báo); khi ẩn, request có gesture đi qua hộp thoại.
- Quyết định gắn với tab: `onExternalAppDecision(tabId, requestId, open)`; chỉ tác động nếu tab đó còn request này đang chờ. `BrowserUiState.externalPromptTabId`. Overload `(requestId, open)` cũ dùng tab đang chọn.
- Giới hạn độ dài: `IncomingIntentResolver.MAX_URL_LENGTH = 8192`. URL VIEW dài hơn → `Rejected(InvalidUrl)`. Văn bản SEND được cắt còn `MAX_URL_LENGTH + 1` ký tự trước khi chạy regex; URL tìm thấy dài hơn giới hạn hoặc chạm điểm cắt → `Rejected(InvalidUrl)` (không mở URL đã bị cắt).
- `intent:` không phân biệt hoa thường (chuẩn hóa tiền tố trước `Intent.parseUri`); scheme data đã decode được `trim()` trước khi so với danh sách cấm. `openDefaultAppsSettings` bắt cả `SecurityException`.
- AC14: `BrowserUiState.isExternalTab`, `BrowserViewModel.onReturnToCaller()` (đóng tab nếu mở từ intent và hết lịch sử). `BrowserRoute(onReturnToCaller = …)`; mặc định gọi `moveTaskToBack(true)` trên Activity chứa composable, `MainActivity` truyền tường minh.
- AC18–AC19: `BrowserRoute(defaultBrowserController = …)`, mặc định `RoleManagerDefaultBrowser(applicationContext)`; mục menu chỉ hiện khi role khả dụng và chưa giữ (`onDefaultBrowserChanged(isDefault = !available || held)`). Bộ đếm hủy nằm trong `rememberSaveable`, reset khi app đã giữ role. Mở Settings lỗi → `Settings.ACTION_SETTINGS` → message `settings_unavailable` (`BrowserViewModel.onDefaultBrowserSettingsUnavailable()`).
- Chưa làm edge case "intent đến khi menu/tab sheet đang mở thì đóng chúng": trạng thái menu/sheet là state cục bộ của `BrowserScreen`; hiện chỉ thoát chế độ sửa URL.
