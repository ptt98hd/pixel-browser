# Spec 007: Tính năng nền tảng web – file chooser, quyền, fullscreen, popup, JS dialog, HTTP auth, download hook (A11)
## Story
Người dùng dùng các trang web hiện đại như trên Chrome: chọn ảnh để tải lên, cho phép trang dùng camera/micro/vị trí sau khi được hỏi rõ ràng, xem video toàn màn hình, mở link `target=_blank` ở tab mới (popup tự bật bị chặn), thấy hộp thoại `alert/confirm/prompt` của trang (có thể chặn trang spam hộp thoại), đăng nhập HTTP Basic, và khi bấm link tải file thì được đề nghị mở bằng app khác cho đến khi có trình tải xuống (B03).

## Phạm vi & module
- Phụ thuộc spec 005 (`:feature:browser`) và spec 006 (hạ tầng `EngineRequest`, `EngineResponse`, `EngineRequestQueue`, `EngineSession.requests/respond`). Implement **sau** 006.
- Nguyên tắc: mọi callback WebView được chuyển thành `EngineRequest` (hoặc trạng thái trong `EngineState`) trong `:core:engine`; quyết định/UI nằm ở `:feature:browser`, test được trên JVM bằng `FakeEngineSession`.
- `:feature:browser`, package `app.pixel.browser.feature.browser.web`: `EngineRequestHost` (composable hiển thị request đang chờ), `JsDialog`, `PermissionDialog`, `HttpAuthDialog`, `DownloadDialog`, `FileChooserMapper`, `SitePermissionMemory`, `RuntimePermissionGate`.
- `:app`: quyền trong manifest, `FileProvider` cho ảnh/video chụp.

## Thay đổi API
### `:core:engine`
```kotlin
enum class SitePermission { Camera, Microphone, Location }
enum class JsDialogKind { Alert, Confirm, Prompt, BeforeUnload }

sealed interface EngineRequest {  // spec 006 đã có: id, origin, ExternalApp
    data class JsDialog(override val id: Long, override val origin: String,
        val kind: JsDialogKind, val message: String, val defaultValue: String?, val canSuppress: Boolean) : EngineRequest
    data class PermissionPrompt(override val id: Long, override val origin: String,
        val permissions: Set<SitePermission>) : EngineRequest
    data class FileChooser(override val id: Long, override val origin: String,
        val acceptTypes: List<String>, val allowMultiple: Boolean, val capture: Boolean) : EngineRequest
    data class HttpAuth(override val id: Long, override val origin: String,
        val host: String, val realm: String) : EngineRequest
    data class NewWindow(override val id: Long, override val origin: String) : EngineRequest
    data class PopupBlocked(override val id: Long, override val origin: String) : EngineRequest
    data class CloseWindow(override val id: Long, override val origin: String) : EngineRequest
    data class Download(override val id: Long, override val origin: String,
        val url: String, val mimeType: String?, val contentDisposition: String?, val contentLength: Long) : EngineRequest
}

sealed interface EngineResponse {  // spec 006 đã có: Dismiss(suppressFurther)
    data class Confirm(val suppressFurther: Boolean = false) : EngineResponse
    data class PromptText(val value: String, val suppressFurther: Boolean = false) : EngineResponse
    data class Files(val uris: List<String>) : EngineResponse
    data class Grant(val permissions: Set<SitePermission>) : EngineResponse
    data class Credentials(val username: String, val password: String) : EngineResponse // toString() che password
    data class AttachWindow(val target: EngineSession) : EngineResponse
}

data class EngineState(/* ... */ val isFullscreen: Boolean = false)

interface EngineSession {
    // ...
    /** View video toàn màn hình (WebView: view của onShowCustomView); Fake và khi không fullscreen: null. */
    val fullscreenView: android.view.View?
    fun exitFullscreen()
}

/** https, hoặc http với host localhost/127.0.0.1/[::1]. */
fun isSecureOrigin(origin: String): Boolean
```
- `WebViewEngineSession`: cài đặt thêm `setSupportMultipleWindows(true)`, `javaScriptCanOpenWindowsAutomatically = true` (để tự quyết theo gesture và báo popup bị chặn), `setGeolocationEnabled(true)`, `mediaPlaybackRequiresUserGesture = true`, `setDownloadListener`. Override `onJsAlert/onJsConfirm/onJsPrompt/onJsBeforeUnload`, `onPermissionRequest/onPermissionRequestCanceled`, `onGeolocationPermissionsShowPrompt/HidePrompt`, `onShowFileChooser`, `onShowCustomView/onHideCustomView`, `onCreateWindow/onCloseWindow`, `onReceivedHttpAuthRequest`.
- Điểm vào cho test (public như các `dispatch*` hiện có, vì `JsResult`/`HttpAuthHandler` không khởi tạo được trong test): `dispatchJsDialog(kind, url, message, defaultValue, onResult: (EngineResponse) -> Unit)`, `dispatchHttpAuth(host, realm, onResult: (EngineResponse) -> Unit)`, `dispatchCreateWindow(isUserGesture: Boolean, onAttach: (WebView) -> Unit): Boolean`. Các callback có kiểu khởi tạo được (`PermissionRequest`, `FileChooserParams` là abstract công khai; `ValueCallback`, `GeolocationPermissions.Callback`, `CustomViewCallback` là interface) được test qua `shadowOf(webView).webChromeClient` trực tiếp.
- `FakeEngineSession` thêm: `simulateJsDialog(kind, message, defaultValue = null): EngineRequest.JsDialog?` (null nếu bị chặn – response tự động ghi vào `responses`), `simulatePermissionRequest(permissions: Set<SitePermission>, origin = originOf(state.url))`, `simulatePermissionCanceled(id)`, `simulateFileChooser(acceptTypes, allowMultiple = false, capture = false)`, `simulateHttpAuth(host, realm)`, `simulateCreateWindow(isUserGesture: Boolean): Boolean`, `simulateCloseWindow()`, `simulateDownload(url, mimeType = null, contentDisposition = null, contentLength = -1)`, `simulateEnterFullscreen()`, `simulateExitFullscreen()`; `val exitFullscreenCalls: Int`.

### `:core:tabs`
- `fun TabManager.openTabForWindow(openerId: String): Tab?` – tạo tab ngay sau tab opener, chọn nó, session mới **không** `loadUrl` (engine của popup tự điều hướng); trả `null` nếu opener không tồn tại.

### `:feature:browser`
```kotlin
fun interface RuntimePermissionGate { suspend fun request(androidPermissions: Set<String>): Set<String> } // trả tập được cấp
class SitePermissionMemory { fun get(origin: String, p: SitePermission): Boolean?; fun set(origin: String, p: SitePermission, allowed: Boolean) }
sealed interface FileChooserLaunch {
    data class GetContent(val mimeType: String) : FileChooserLaunch
    data class OpenMultiple(val mimeTypes: List<String>) : FileChooserLaunch
    data object TakePicture : FileChooserLaunch
    data object CaptureVideo : FileChooserLaunch
}
object FileChooserMapper { fun map(request: EngineRequest.FileChooser, extensionToMime: (String) -> String?): FileChooserLaunch }
// BrowserViewModel thêm tham số: permissionMemory: SitePermissionMemory, runtimePermissions: RuntimePermissionGate
// và hàm: onRespond(requestId: Long, response: EngineResponse), onPermissionDecision(requestId, allow: Boolean), onExitFullscreen()
// BrowserUiState thêm: pendingRequest: EngineRequest? (request UI đầu tiên của tab đang chọn), isFullscreen: Boolean
```
- Test tag (`BrowserTestTags`): `js_dialog`, `js_dialog_title`, `js_dialog_message`, `js_dialog_input`, `js_dialog_ok`, `js_dialog_cancel`, `js_dialog_suppress`, `permission_dialog`, `permission_item_<Camera|Microphone|Location>`, `permission_allow`, `permission_block`, `auth_dialog`, `auth_username`, `auth_password`, `auth_ok`, `auth_cancel`, `auth_insecure_warning`, `download_dialog`, `download_open_external`, `download_cancel`, `fullscreen_container`.

## Acceptance criteria
### Engine (`EngineSessionContractTest` chạy cho Fake – JVM và WebView – Robolectric, trừ khi ghi khác)
1. **AC1 – Bảng response hợp lệ.** `respond` trả `true` chỉ với: `JsDialog` Alert ← `Confirm`/`Dismiss`; Confirm, BeforeUnload ← `Confirm`/`Dismiss`; Prompt ← `PromptText`/`Confirm` (= giá trị mặc định)/`Dismiss`; `PermissionPrompt` ← `Grant`/`Dismiss`; `FileChooser` ← `Files`/`Dismiss`; `HttpAuth` ← `Credentials`/`Dismiss`; `NewWindow` ← `AttachWindow`/`Dismiss`; `PopupBlocked`, `CloseWindow`, `Download` ← `Dismiss`. Mọi cặp khác → `false`, request vẫn trong `requests`. `Grant` chứa quyền không có trong `permissions` của request → `false`; `Files` có > 1 URI khi `allowMultiple = false` → `false`. (`EngineRequestResponseTableTest`, JVM, parameterized trên Fake)
2. **AC2 – JS dialog ánh xạ kết quả.** WebView qua `dispatchJsDialog(..., onResult)` và Fake qua `simulateJsDialog`: request `JsDialog(kind, origin = originOf(url của frame), message, defaultValue)`; `onJsAlert/Confirm/Prompt/BeforeUnload` trả `true` (tự xử lý). Ánh xạ trong WebView: `Confirm` → `result.confirm()`, `PromptText(v)` → `confirm(v)`, `Dismiss` → `cancel()`; test xác nhận `onResult` nhận đúng response đã gửi. Message được giữ nguyên (không cắt); `defaultValue` chỉ khác null với Prompt.
3. **AC3 – Chặn dialog spam.** Trong cùng một trang: dialog thứ nhất có `canSuppress = false`, từ dialog thứ hai trở đi `canSuppress = true`. Sau khi trả lời với `suppressFurther = true`, các dialog Alert/Confirm/Prompt kế tiếp **không** vào `requests` và được tự trả `Dismiss()` (Fake: `simulateJsDialog` trả `null`, `responses[id] == Dismiss()`); BeforeUnload tự trả `Confirm()` (cho rời trang, không nhốt người dùng). Bộ đếm và cờ chặn reset khi bắt đầu điều hướng main frame mới (`loadUrl`, `reload`, `goBack`, `goForward`; WebView: `dispatchPageStarted`).
4. **AC4 – Quyền media.** `onPermissionRequest` với resources `[VIDEO_CAPTURE, AUDIO_CAPTURE]` → `PermissionPrompt(permissions = {Camera, Microphone}, origin = originOf(request.origin))`. `Grant({Camera})` → `request.grant([VIDEO_CAPTURE])`; `Grant(emptySet())` hoặc `Dismiss` → `request.deny()`. Resource không hỗ trợ (`RESOURCE_MIDI_SYSEX`, `RESOURCE_PROTECTED_MEDIA_ID`) bị bỏ khỏi request; nếu không còn resource nào → `deny()` ngay, không tạo request. `onPermissionRequestCanceled` → request biến mất khỏi `requests` (không gọi grant/deny). Test WebView dùng lớp con `PermissionRequest` ghi lại grant/deny.
5. **AC5 – Vị trí.** `onGeolocationPermissionsShowPrompt("https://a.com", cb)` → `PermissionPrompt(origin = "https://a.com", permissions = {Location})`; `Grant({Location})` → `cb.invoke("https://a.com", true, false)` (không bao giờ `retain = true`); `Dismiss` → `invoke(origin, false, false)`. `onGeolocationPermissionsHidePrompt` → request bị gỡ.
6. **AC6 – File chooser.** `onShowFileChooser` trả `true`, tạo `FileChooser(acceptTypes, allowMultiple = mode == MODE_OPEN_MULTIPLE, capture = isCaptureEnabled)`; `acceptTypes` đã trim, lowercase, bỏ chuỗi rỗng (`["", " Image/* ", ".PDF"]` → `["image/*", ".pdf"]`). `Files(["content://a/1"])` → `ValueCallback` nhận `arrayOf(Uri.parse("content://a/1"))`; `Dismiss` hoặc `Files(emptyList())` → nhận `null` (bắt buộc, nếu không WebView không mở chooser lần sau). Khi đã có một `FileChooser` đang chờ, chooser thứ hai bị trả `null` ngay, không tạo request.
7. **AC7 – HTTP auth.** `dispatchHttpAuth("a.com", "Admin")` → `HttpAuth(host = "a.com", realm = "Admin", origin = originOf(state.url))`; `Credentials("u", "p")` → `onResult(Credentials("u","p"))` (WebView: `handler.proceed`); `Dismiss` → `handler.cancel()`. `Credentials("u", "secret").toString()` không chứa `secret`. Engine không lưu thông tin đăng nhập (`WebViewDatabase` không được ghi).
8. **AC8 – Fullscreen.** `onShowCustomView(view, cb)` → `state.isFullscreen = true`, `fullscreenView === view`. `exitFullscreen()` → `cb.onCustomViewHidden()` đúng 1 lần, sau `onHideCustomView` thì `isFullscreen = false`, `fullscreenView == null`. `onShowCustomView` lần hai khi đang fullscreen → callback mới bị gọi `onCustomViewHidden()` ngay, giữ view cũ. `close()` khi đang fullscreen → thoát fullscreen trước khi destroy. Fake: `simulateEnterFullscreen()` / `exitFullscreen()` (tăng `exitFullscreenCalls`, `isFullscreen = false`).
9. **AC9 – Popup theo gesture.** Cài đặt: `supportMultipleWindows() == true`, `javaScriptCanOpenWindowsAutomatically == true`. `dispatchCreateWindow(isUserGesture = true, …)` / `simulateCreateWindow(true)` → trả `true`, tạo `NewWindow(origin)`. `respond(id, AttachWindow(target))` với WebView: `onAttach(target.webView)` được gọi (transport + `sendToTarget`); `target` không phải `WebViewEngineSession` → `false`. `Dismiss` → không attach. `isUserGesture = false` → trả `false` (WebView hủy popup), không có `NewWindow`, tạo `PopupBlocked(origin)`.
10. **AC10 – Close window & download.** `onCloseWindow` → `CloseWindow(origin)`. `DownloadListener.onDownloadStart(url, ua, cd, mime, len)` → `Download(url, mimeType = mime, contentDisposition = cd, contentLength = len)`; engine không tự tải gì.
11. **AC11 – `isSecureOrigin`.** `https://a.com`, `http://localhost:8080`, `http://127.0.0.1`, `http://[::1]` → `true`; `http://a.com`, `""`, `file://` → `false`. (`SecureOriginTest`, JVM)

### TabManager
12. **AC12 – `openTabForWindow`.** Given tab [A, B, C], A được chọn, When `openTabForWindow(A.id)`, Then thứ tự [A, P, B, C], P được chọn, session của P chưa `loadUrl` (`fake.loadedUrls` rỗng, `state.url == ""`); `openTabForWindow("missing") == null`, danh sách không đổi. (`TabManagerTest`, JVM)

### ViewModel & UI (`:feature:browser`)
13. **AC13 – Chỉ hiện request của tab đang chọn.** Given tab A (chọn) và B, When B phát `JsDialog`, Then `uiState.pendingRequest == null`; When chọn B, Then `pendingRequest` là dialog đó; chọn lại A → `null`, request của B vẫn trong `B.session.requests`. Request do ViewModel tự xử lý (`NewWindow`, `PopupBlocked`, `CloseWindow`, quyền đã nhớ) không bao giờ xuất hiện trong `pendingRequest`. (`BrowserViewModelRequestTest`, JVM)
14. **AC14 – JS dialog UI.** Alert: có `js_dialog`, `js_dialog_title` chứa `UrlDisplay.host(origin)` (không dùng title trang), `js_dialog_message` = message, chỉ có `js_dialog_ok`. Confirm/BeforeUnload: có `js_dialog_ok` và `js_dialog_cancel`. Prompt: `js_dialog_input` chứa sẵn `defaultValue`; nhập `abc` + OK → `respond(id, PromptText("abc"))`. OK → `Confirm()`, Cancel → `Dismiss()`, Back hệ thống hoặc chạm ngoài dialog → `Dismiss()`. `canSuppress = true` → có checkbox `js_dialog_suppress`; tick rồi OK → `Confirm(suppressFurther = true)`. Message 20 000 ký tự hiển thị được (cuộn), nút vẫn trong màn hình. (`JsDialogTest`, Compose Robolectric)
15. **AC15 – Hộp thoại quyền.** `PermissionPrompt(origin = "https://a.com", {Camera, Microphone})` → `permission_dialog` hiện `a.com`, `permission_item_Camera`, `permission_item_Microphone`. Tap `permission_allow` → ViewModel gọi `runtimePermissions.request({CAMERA, RECORD_AUDIO})` cho các quyền Android chưa được cấp, rồi `respond(id, Grant(<những quyền OS đã cấp>))`; tap `permission_block` hoặc Back → `Dismiss()`. Ánh xạ: Camera → `CAMERA`; Microphone → `RECORD_AUDIO`; Location → `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (chỉ cần một trong hai được cấp). Quyền OS đã cấp sẵn → không gọi `runtimePermissions`. OS từ chối Camera → `Grant({Microphone})` và `uiState.message != null`. (`PermissionDialogTest` Compose, `BrowserViewModelPermissionTest` JVM với fake gate)
16. **AC16 – Nhớ lựa chọn trong phiên.** Given đã Allow Camera cho `https://a.com`, When tab khác (cùng `SitePermissionMemory`) phát `PermissionPrompt("https://a.com", {Camera})`, Then không hiện dialog, tự `Grant({Camera})` (vẫn qua `runtimePermissions` nếu quyền OS chưa có). Đã Block → tự `Dismiss()`. Origin khác (`https://b.com`, `http://a.com`, `https://a.com:8443`) → hỏi lại. Request {Camera, Microphone} khi chỉ nhớ Camera = Allow → dialog chỉ liệt kê Microphone; kết quả gộp `Grant({Camera, Microphone})` nếu Allow. `SitePermissionMemory` mới (process mới) rỗng – không ghi đĩa. (`BrowserViewModelPermissionTest`, `SitePermissionMemoryTest`)
17. **AC17 – Origin không an toàn.** `PermissionPrompt` từ origin mà `isSecureOrigin == false` (vd `http://a.com`) hoặc origin rỗng → tự `Dismiss()`, không dialog, không gọi `runtimePermissions`. (`BrowserViewModelPermissionTest`)
18. **AC18 – Ánh xạ file chooser.** `FileChooserMapper.map` (JVM, `extensionToMime = mapOf(".pdf" to "application/pdf")::get`): `accept=[]`, single → `GetContent("*/*")`; `["image/*"]` → `GetContent("image/*")`; `[".pdf"]` → `GetContent("application/pdf")`; `["image/*", "video/*"]` single → `GetContent("*/*")`; `allowMultiple` + `["image/*", ".pdf"]` → `OpenMultiple(["image/*", "application/pdf"])`; `allowMultiple` + `[]` → `OpenMultiple(["*/*"])`; extension không biết bị bỏ (`[".xyz"]` → `GetContent("*/*")`); `capture` + `["image/*"]` → `TakePicture`; `capture` + `["video/*"]` → `CaptureVideo`; `capture` + `[]` hoặc `["image/*","video/*"]` → như không capture. (`FileChooserMapperTest`)
19. **AC19 – Khởi chạy file chooser.** Với `TestActivityResultRegistry` cung cấp qua `LocalActivityResultRegistryOwner`: `FileChooser` → launcher tương ứng được gọi với đúng input (mime); registry trả `[uri1, uri2]` → `respond(id, Files(["uri1","uri2"]))`; trả `null`/rỗng → `Dismiss()`. `TakePicture`: URI đích là `content://<applicationId>.fileprovider/captures/…` trong `cacheDir/captures`; nếu chưa có quyền `CAMERA` thì xin qua `runtimePermissions`, bị từ chối → fallback `GetContent("image/*")`; kết quả `true` → `Files([uri đích])`, `false` → `Dismiss()`. Không có activity xử lý (`ActivityNotFoundException`) → `Dismiss()` + `message`. (`FileChooserLauncherTest`, Robolectric)
20. **AC20 – Fullscreen UI.** Given `isFullscreen = true` trên tab chọn, Then có `fullscreen_container` chứa `fullscreenView` (Fake: placeholder), không có `url_display` và `btn_back` (thanh chrome ẩn), `window.attributes.flags` có `FLAG_KEEP_SCREEN_ON`. Back hệ thống → `exitFullscreen()` (ưu tiên cao nhất, trước mọi nhánh của spec 005 AC31). Đổi tab, đóng tab, hoặc intent mới (spec 006) khi đang fullscreen → `exitFullscreen()` được gọi trước. Khi `isFullscreen = false` → chrome hiện lại, cờ `FLAG_KEEP_SCREEN_ON` bị gỡ. (`FullscreenTest`, Robolectric)
21. **AC21 – Popup → tab mới.** Given tab A phát `NewWindow`, Then ViewModel gọi `tabManager.openTabForWindow(A.id)` rồi `A.session.respond(id, AttachWindow(<session tab mới>))`; tab mới được chọn, `tab_count` tăng 1. `PopupBlocked` → `respond(Dismiss())`, `uiState.message != null` ("Đã chặn cửa sổ bật lên"), số tab không đổi. (`BrowserViewModelPopupTest`, JVM)
22. **AC22 – `window.close()`.** Given P mở từ A qua AC21, When P phát `CloseWindow`, Then `closeTab(P.id)` và A được chọn (nếu còn tồn tại; nếu không, theo luật chọn của spec 002). `CloseWindow` từ tab không mở bằng `NewWindow` → chỉ `Dismiss()`, tab giữ nguyên. (`BrowserViewModelPopupTest`)
23. **AC23 – HTTP auth UI.** `auth_dialog` hiện `host` và realm (realm cắt còn 100 ký tự, đặt trong ngoặc kép để không giả mạo câu chữ của app); `auth_password` có semantics `Password` (che ký tự). Nhập `u`/`p` + `auth_ok` → `Credentials("u","p")`; `auth_cancel`/Back → `Dismiss()`. Origin không an toàn (`http://`) → có `auth_insecure_warning`; `https://` → không có. Dialog đóng sau khi trả lời; trường mật khẩu không được lưu vào `SavedStateHandle`/`rememberSaveable`. (`HttpAuthDialogTest`)
24. **AC24 – Download hook.** `Download(url = "https://a.com/f.pdf", mimeType = "application/pdf", contentLength = 2048)` → `download_dialog` hiện tên file (`URLUtil.guessFileName`) và kích thước ("2,0 KB" theo `Formatter.formatShortFileSize`; `contentLength < 0` → không hiện kích thước). Tap `download_open_external` → activity kế tiếp là `ACTION_CHOOSER` bọc `Intent(ACTION_VIEW, url)` + type `mimeType`, có `EXTRA_EXCLUDE_COMPONENTS` chứa `MainActivity` của Pixel; rồi `Dismiss()`. `download_cancel`/Back → `Dismiss()`, không start activity. URL không phải http/https (`blob:`, `data:`) → không dialog, tự `Dismiss()`, `message` "chưa hỗ trợ". (`DownloadDialogTest`, Robolectric)
25. **AC25 – Request treo khi tab đóng.** Given tab có `JsDialog` đang hiện, When `closeTab`, Then dialog biến mất (`pendingRequest` của tab mới chọn), engine của tab cũ nhận `Dismiss()` (spec 006 AC2). (`BrowserViewModelRequestTest`)
26. **AC26 – Manifest.** Manifest đã merge khai báo `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`; `uses-feature` `android.hardware.camera`, `android.hardware.microphone`, `android.hardware.location` đều `required="false"`; `FileProvider` với authority `${applicationId}.fileprovider`, `exported=false`, `grantUriPermissions=true`, chỉ chia sẻ `cache/captures/`. (`ManifestPermissionsTest`, Robolectric qua `PackageManager.getPackageInfo(GET_PERMISSIONS | GET_PROVIDERS)`)

## Edge cases
- Dialog JS từ iframe khác origin: tiêu đề dùng origin của frame (`url` của callback), không phải trang chính – người dùng thấy ai thực sự hỏi.
- Request quyền/dialog đến khi tab ở nền: giữ trong hàng đợi, hiện khi tab được chọn; WebView tự hủy quyền khi trang điều hướng (`onPermissionRequestCanceled`).
- Nhiều request cùng lúc trên một tab (vd quyền + alert): hiện lần lượt theo FIFO, mỗi lúc một dialog.
- Xoay màn hình khi dialog đang mở: request nằm trong session (sống qua configuration change) nên dialog hiện lại; text đang nhập trong prompt có thể mất (chấp nhận), mật khẩu không bao giờ được lưu (AC23).
- Người dùng chọn "Không hỏi lại" ở hộp thoại quyền Android: `runtimePermissions` trả rỗng ngay → `Grant(emptySet())` + message hướng dẫn vào Cài đặt app (nút mở `ACTION_APPLICATION_DETAILS_SETTINGS`).
- Quyền Location chỉ cấp "Xấp xỉ" (coarse) → vẫn coi là cấp Location.
- File chooser: process bị kill khi đang ở app chọn file → khi quay lại, `ValueCallback` cũ không còn; session mới không có request → kết quả bị bỏ qua, không crash.
- `TakePicture`: file tạm nằm trong `cacheDir/captures` (hệ thống có thể dọn); chính sách tự dọn chủ động ghi nhận cho E05, không thuộc spec này.
- Popup không có gesture từ `setTimeout` sau click (gesture hết hạn) → bị chặn; chấp nhận khác biệt nhỏ với Chrome.
- `window.open` liên tục trong một gesture → mỗi `onCreateWindow` có gesture mở một tab (WebView/Blink chỉ cấp 1 popup mỗi gesture).
- Popup mở từ tab rồi opener bị đóng: popup giữ nguyên; `CloseWindow` sau đó chọn theo luật spec 002.
- Fullscreen khi xoay: không ép orientation (D02 làm sau); thoát fullscreen trả lại system bars và orientation cũ.
- HTTP auth sai mật khẩu: WebView gọi lại `onReceivedHttpAuthRequest` → hiện dialog lần nữa (mỗi lần cần người dùng thao tác, không lặp vô hạn tự động).
- HTTP auth cho subresource khác host: dialog hiện `host` của request, không phải host trang.
- `beforeunload` khi người dùng bấm Back: nếu trả `Dismiss` (Ở lại) thì không điều hướng và không thoát app.
- `Download` có `Content-Disposition` chứa path (`../../x`): tên hiển thị chỉ là tên file đã làm sạch bởi `URLUtil.guessFileName`.

## Không test được bằng Robolectric (instrumented / thủ công)
- Gọi JS thật (`alert()`, `window.open`, `getUserMedia`, `navigator.geolocation`, `<input type=file capture>`, `requestFullscreen()` trên `<video>`) và luồng callback thật của WebView – Robolectric không chạy JS/Chromium. → `WebPlatformInstrumentedTest` với trang HTML trong `androidTest/assets` (khi có KVM) + thủ công: webrtc.github.io/samples, browserleaks geolocation, httpbin.org/basic-auth, YouTube fullscreen.
- Ẩn system bars thật (`WindowInsetsControllerCompat.hide(systemBars())`, behavior swipe) và render video fullscreen. → instrumented/thủ công.
- Hộp thoại quyền Android thật, picker tài liệu và camera thật, `FileProvider` URI đọc được bởi app camera. → thủ công.
- Giá trị `isUserGesture` thật cho `target=_blank` vs `window.open` trong `setTimeout`. → thủ công.

## Tests liên quan
- `:core:engine`: `EngineSessionContractTest` bổ sung AC2–AC10 (Fake JVM qua `FakeEngineSessionContractTest`, WebView Robolectric qua `WebViewEngineSessionTest`); `EngineRequestResponseTableTest` (AC1, JVM); `SecureOriginTest` (AC11, JVM).
- `:core:tabs`: `TabManagerTest` bổ sung AC12 (JVM).
- `:feature:browser`:
  - JVM thuần: `BrowserViewModelRequestTest` (AC13, AC25), `BrowserViewModelPermissionTest` (AC15–AC17), `SitePermissionMemoryTest` (AC16), `FileChooserMapperTest` (AC18), `BrowserViewModelPopupTest` (AC21, AC22).
  - Robolectric + Compose: `JsDialogTest` (AC14), `PermissionDialogTest` (AC15), `FileChooserLauncherTest` (AC19), `FullscreenTest` (AC20), `HttpAuthDialogTest` (AC23), `DownloadDialogTest` (AC24); `BrowserBackHandlingTest` bổ sung AC20 (Back thoát fullscreen).
- `:app`: `ManifestPermissionsTest` (AC26, Robolectric).
- Instrumented: `WebPlatformInstrumentedTest`.

## Out of scope
- Lưu quyền/popup theo site lâu dài, "luôn cho phép popup", UI Site settings (B07). Quyền chỉ nhớ trong process.
- Trình tải xuống thật (đa luồng, resume, notification, quản lý file) – B03; ở đây chỉ mở bằng app khác.
- Lưu mật khẩu HTTP auth/Autofill (B10). Trang lỗi/cảnh báo SSL (B12).
- Media overlay, xoay video, tốc độ phát (D01/D02); Picture-in-Picture; WebXR, MIDI, Protected media (DRM – tự từ chối), Notifications/Push API (WebView không hỗ trợ), clipboard permission.
- Incognito tách bộ nhớ quyền (B06).

## Quyết định (orchestrator, 2026-11-05)
- Download tạm thời giao cho `DownloadManager` hệ thống (thông báo + thư mục Downloads) thay vì chooser mở app khác; B03 sẽ thay thế bằng download manager riêng.
- Quyền theo site: nhớ trong bộ nhớ cho cả process, dùng chung giữa các tab, nhớ cả Allow và Block; lưu bền ở B07.
- `javaScriptCanOpenWindowsAutomatically = true`, tự quyết bằng `isUserGesture` để hiện thông báo "đã chặn popup".
