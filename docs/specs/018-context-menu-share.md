# Spec 018: Menu bấm giữ trên liên kết/ảnh và hành động trang: chia sẻ, sao chép, thêm vào màn hình chính, in/lưu PDF (B13, B09)
## Story
**Bấm giữ trong trang** (liên kết, ảnh, ảnh có liên kết, số điện thoại, email, địa chỉ): hiện một bảng ở đáy với các hành động: mở ở tab mới (nền), mở ở tab ẩn danh, sao chép liên kết, chia sẻ liên kết, tải ảnh, mở ảnh ở tab mới, sao chép địa chỉ ảnh, gọi / gửi email / mở bản đồ. Liên kết chỉ http/https mới có menu; `javascript:`, `data:`, `intent:`… bị bỏ qua (không có menu), nên trang không thể dùng bảng này để dụ người dùng.
**Hành động của trang** (menu ba chấm): chia sẻ (đã có), sao chép liên kết, mở bằng ứng dụng khác, thêm vào màn hình chính (lối tắt mở URL trong Pixel), in / lưu PDF.
Chọn văn bản bằng bấm giữ trên chữ thường thuộc B15; xem trước liên kết (C08) và chia sẻ ảnh không thuộc spec này.

## Phạm vi & module
- `:core:engine`: `HitTarget`, `HitMapper`, `EngineRequest.ContextMenu`, `EngineSession.createPrintAdapter`; `WebViewEngineSession` (`OnLongClickListener`, `dispatchLongPress`), `FakeEngineSession.simulateLongPress`.
- `:feature:browser`, package `...browser.actions`: `ContextAction`, `ContextMenuModel`, `PageActions` + `AndroidPageActions`, `ClipboardGateway`, `PrintLauncher`, `DownloadLauncher`/`DownloadSpec`, `PageShortcut`; UI `ContextMenuSheet`; `BrowserViewModel`, `BrowserMenu`, `BrowserActions`, `BrowserUiState`.
- `:app`: nối `AndroidPageActions`, `DownloadLauncher` tạm (DownloadManager, spec 007) – spec 019 thay bằng trình tải riêng; không quyền mới.
- Phụ thuộc: sau 006 (`ExternalAppLauncher`, `IncomingIntentResolver`), 007 (`EngineRequest`), 016 ("tab ẩn danh"; nếu 016 chưa merge thì ẩn mục này), 015 (menu). Tách PR được: 018A menu bấm giữ (B13), 018B hành động trang (B09).

## Quyết định thiết kế
### Bắt sự kiện
- `PixelWebView`/`WebViewEngineSession` đặt `OnLongClickListener`: lấy `hitTestResult` (`type`, `extra`); nếu ánh xạ ra `HitTarget` hợp lệ ⇒ `dispatchLongPress(...)` phát `EngineRequest.ContextMenu` và trả `true` (chặn hành vi mặc định, tránh bắt đầu chọn chữ); ngược lại trả `false` để WebView xử lý như cũ (chọn chữ, ô nhập).
- `HitTestResult`: `SRC_ANCHOR_TYPE (7)` → `Link(extra)`; `IMAGE_TYPE (5)` → `Image(extra)`; `SRC_IMAGE_ANCHOR_TYPE (8)` thì `extra` chỉ là URL ảnh ⇒ engine gọi `requestFocusNodeHref(Message)` để lấy `url` (liên kết) và `src` (ảnh), rồi phát `ImageLink`; không lấy được liên kết ⇒ hạ xuống `Image`; `PHONE_TYPE (2)`, `EMAIL_TYPE (4)`, `GEO_TYPE (3)` → `Phone/Email/Geo`; `EDIT_TEXT_TYPE (9)`, `UNKNOWN_TYPE (0)` → không menu.
- Validate trong `HitMapper` (thuần, JVM) và **lặp lại ở ViewModel** (không tin UI): liên kết/ảnh chỉ `http`/`https`, ≤ 8 192 ký tự (`MAX_URL_LENGTH` của spec 006), không khoảng trắng/ký tự điều khiển; số điện thoại sau khi bỏ khoảng/`-`/`.`/`()` chỉ còn `0-9 + * #`, 3..32 ký tự; email dạng `local@domain` ≤ 254 ký tự, bỏ phần `?subject=…`; địa chỉ `geo` ≤ 256 ký tự. Ảnh `data:`/`blob:` **không** có hành động tải (019 xử lý `DownloadStream`), chỉ còn hành động liên kết nếu là `ImageLink`.
### Hành động theo ngữ cảnh (`ContextMenuModel.build`, thuần)
| Đối tượng | Tab thường | Tab ẩn danh |
|---|---|---|
| Link | Mở ở tab mới · Mở ở tab ẩn danh* · Sao chép liên kết · Chia sẻ liên kết | Mở ở tab ẩn danh · Sao chép liên kết · Chia sẻ liên kết (**không** có "tab mới" thường để khỏi rò rỉ) |
| Image | Mở ảnh ở tab mới · Tải ảnh xuống · Sao chép địa chỉ ảnh | cùng (mở ở tab ẩn danh) |
| ImageLink | hành động Link, rồi hành động Image | tương ứng |
| Phone | Gọi (`tel:` quay số, không gọi thẳng) · Sao chép số | cùng |
| Email | Gửi email · Sao chép địa chỉ | cùng |
| Geo | Mở bản đồ · Sao chép địa chỉ | cùng |
\*chỉ khi 016 khả dụng (`canOpenPrivate`). Danh sách rỗng ⇒ request tự `Dismiss` (không hiện bảng).
- "Mở ở tab mới" mở **nền** (`TabManager.openTab(select = false)`) kèm thông báo "Đã mở trong tab mới" + nút "Chuyển tới"; tab cạnh tab hiện tại không cần; tab mới thuộc đúng nhóm (thường/ẩn danh).
- Gọi/Email/Bản đồ đi qua `ExternalAppLauncher` của spec 006 (`ExternalIntents.parse` làm sạch, không bao giờ `ACTION_CALL`); người dùng vừa chọn hành động nên coi là có gesture.
- Sao chép: `ClipData` văn bản thuần; tab ẩn danh thêm `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+, bỏ qua dưới đó). Từ Android 13 hệ thống tự hiện xác nhận; dưới đó ứng dụng hiện thông báo "Đã sao chép".
### Hành động trang
- `PageActions` là giao diện (JVM test được bằng Fake); `AndroidPageActions(context)` là bản thật. Chia sẻ trang hiện có (spec 005 AC26) giữ nguyên nhưng chooser loại `MainActivity` của Pixel (`EXTRA_EXCLUDE_COMPONENTS`) vì chia sẻ cho chính mình vô nghĩa.
- **Mở bằng ứng dụng khác:** `ACTION_VIEW` http/https qua `Intent.createChooser` loại `MainActivity`; tab ẩn danh phải xác nhận ("mở ngoài sẽ thoát chế độ ẩn danh").
- **Thêm vào màn hình chính:** `ShortcutManagerCompat.requestPinShortcut` (cần `isRequestPinShortcutSupported`); `ShortcutInfoCompat` id `url-<16 ký tự đầu SHA-256(url)>`, nhãn = tiêu đề (hoặc host) bỏ ký tự điều khiển/bidi, cắt 25 ký tự; intent **tường minh** tới `MainActivity` với `ACTION_VIEW` + URL (đi qua `IncomingIntentResolver`, spec 006 – mở tab mới); icon = favicon (nếu có) hoặc ô chữ cái đầu của host. **Tắt ở tab ẩn danh** (lối tắt bền sẽ ghi URL ẩn danh lên launcher).
- **In / Lưu PDF:** `WebView.createPrintDocumentAdapter(jobName)` + `PrintManager.print` (hộp thoại in của hệ thống có "Lưu thành PDF"); `jobName` = tiêu đề hoặc host, bỏ ký tự điều khiển, ≤ 100 ký tự. Pixel không tự ghi file PDF.
- Tải ảnh dùng `DownloadLauncher` (tạm: DownloadManager như spec 007, **không** gửi cookie; 019 thay bằng trình tải riêng với `Referer` = origin của trang).

## Thay đổi API
### `:core:engine`
```kotlin
sealed interface HitTarget {
    data class Link(val url: String) : HitTarget
    data class Image(val imageUrl: String) : HitTarget
    data class ImageLink(val linkUrl: String, val imageUrl: String) : HitTarget
    data class Phone(val number: String) : HitTarget
    data class Email(val address: String) : HitTarget
    data class Geo(val query: String) : HitTarget
}
object HitMapper { fun map(type: Int, extra: String?, linkUrl: String? = null, imageUrl: String? = null): HitTarget? }   // null = không menu
sealed interface EngineRequest { data class ContextMenu(override val id: Long, override val origin: String, val target: HitTarget) : EngineRequest }   // accepts: Dismiss
interface EngineSession { fun createPrintAdapter(jobName: String): PrintDocumentAdapter? }   // null khi đã đóng/Fake không in được
// WebViewEngineSession.dispatchLongPress(type: Int, extra: String?, linkUrl: String?, imageUrl: String?): Boolean   // true = đã tạo request
// FakeEngineSession.simulateLongPress(target: HitTarget): Boolean ; printAdapterRequests: List<String>
```
- Tối đa **một** `ContextMenu` chờ mỗi session: request mới thay request cũ (cũ bị `cancel`); mọi điều hướng main-frame (`dispatchPageStarted`, `loadUrl`, `goBack`…) hủy request đang chờ.
### `:feature:browser`
```kotlin
sealed interface ContextAction {
    data class OpenInNewTab(val url: String); data class OpenInPrivateTab(val url: String)
    data class CopyLink(val url: String); data class ShareLink(val url: String)
    data class OpenImageInNewTab(val url: String); data class DownloadImage(val url: String); data class CopyImageUrl(val url: String)
    data class Call(val number: String); data class SendEmail(val address: String); data class OpenMap(val query: String); data class CopyText(val text: String)
}
object ContextMenuModel { fun build(target: HitTarget, isPrivateTab: Boolean, canOpenPrivate: Boolean): List<ContextAction> }

interface ClipboardGateway { val systemShowsConfirmation: Boolean; fun copy(label: String, text: String, sensitive: Boolean): Boolean }
fun interface PrintLauncher { fun print(adapter: PrintDocumentAdapter, jobName: String): Boolean }
data class DownloadSpec(val url: String, val referrer: String, val userAgent: String?, val isPrivate: Boolean, val suggestedName: String? = null)
fun interface DownloadLauncher { fun start(spec: DownloadSpec): Boolean }
sealed interface PageActionResult { data object Done; data object Unsupported; data class Failed(val reason: String) }
interface PageActions {
    fun shareLink(url: String, title: String?): PageActionResult
    fun openInOtherApp(url: String): PageActionResult
    fun addToHomeScreen(url: String, title: String, favicon: Bitmap?): PageActionResult
    val canPinShortcut: Boolean
}
// BrowserViewModel(..., pageActions: PageActions, clipboard: ClipboardGateway, printLauncher: PrintLauncher, downloads: DownloadLauncher)
fun onContextAction(tabId: String, requestId: Long, action: ContextAction)
fun onCopyPageLink(); fun onOpenInOtherApp(confirmedPrivate: Boolean = false); fun onAddToHomeScreen(); fun onPrint(); fun onSwitchToBackgroundTab()
// BrowserUiState thêm: canCopyLink, canOpenInApp, canPinShortcut, canPrint, backgroundTabId: String?
// BrowserActions thêm: onCopyLink, onOpenInOtherApp, onAddToHomeScreen, onPrint, onContextAction
```
- `BrowserMenu` thêm `menu_copy_link`, `menu_open_in_app`, `menu_add_shortcut`, `menu_print` (sau "Chia sẻ", trước "Tìm trong trang"). Test tag mới: `context_menu_sheet`, `context_menu_title`, `context_action_<open_new_tab|open_private_tab|copy_link|share_link|open_image|download_image|copy_image_url|call|email|map|copy_text>`, `open_in_app_private_warning`.
- Chuỗi mới ở `values` và `values-vi`.

## Acceptance criteria
### Ánh xạ & mô hình (JVM)
1. **AC1 – `HitMapper`.** (`HitMapperTest`, bảng) `7` + `https://a.com/x` → `Link`; `7` + `javascript:alert(1)`, `data:text/html,x`, `intent://x#Intent;end`, `file:///a`, `content://x`, `blob:https://a.com/u`, `ftp://a.com` → `null`; `5` + `https://a.com/i.png` → `Image`; `5` + `data:image/png;base64,AA==` → `Image` (nhưng không có tải, xem AC2); `8` + link `https://a.com/` + ảnh `https://a.com/i.png` → `ImageLink`; `8` chỉ có ảnh → `Image`; `8` link không hợp lệ + ảnh hợp lệ → `Image`; `2` + `"  +84 912-345.678 "` → `Phone("+84912345678")` và `tel:+84912345678`/`"0123"` hợp lệ; `"12"`, `"abc"`, 33 ký tự → `null`; `4` + `a@b.com` → `Email`, `mailto:a@b.com?subject=x` → `Email("a@b.com")`, `"a b@c"` → `null`; `3` + `"Hà Nội"` → `Geo`, rỗng/ >256 → `null`; `9`, `0` → `null`; URL > 8192 ký tự, có khoảng trắng hoặc `\u0000` → `null`; `extra == null` → `null`.
2. **AC2 – `ContextMenuModel.build`.** (`ContextMenuModelTest`) đúng bảng ở trên, đúng thứ tự: tab thường `Link` → `[OpenInNewTab, OpenInPrivateTab, CopyLink, ShareLink]`; `canOpenPrivate = false` → bỏ `OpenInPrivateTab`; tab ẩn danh `Link` → `[OpenInPrivateTab, CopyLink, ShareLink]` (không `OpenInNewTab`); `Image(https)` → `[OpenImageInNewTab, DownloadImage, CopyImageUrl]`; `Image(data:)` → `[]`; `ImageLink` → hành động liên kết rồi ảnh; `Phone` → `[Call, CopyText]`; `Email` → `[SendEmail, CopyText]`; `Geo` → `[OpenMap, CopyText]`.
### Engine (Fake JVM; WebView Robolectric qua `dispatchLongPress`)
3. **AC3 – Phát request.** (`EngineSessionContractTest`, Fake + WebView) `simulateLongPress(Link("https://a.com/x"))` / `dispatchLongPress(7, "https://a.com/x", null, null)` → trả `true`, `requests.value` có `ContextMenu(origin = originOf(state.url), target = Link(...))`; `dispatchLongPress(9, …)`/`(0, …)`/ URL `javascript:` → trả `false`, không request (WebView để mặc định xử lý). `respond(id, Dismiss())` → `true` và biến mất; response khác loại (`Confirm`, `Files`…) → `false` (cập nhật `EngineRequestResponseTableTest`). Sau `close()` → `false`, không request.
4. **AC4 – Một request, hủy khi điều hướng.** Hai lần bấm giữ liên tiếp → chỉ còn request thứ hai (id lớn hơn), request đầu không còn trong `requests`; `simulatePageStarted`/`loadUrl`/`goBack`/`goForward`/`reload` → request đang chờ bị hủy.
5. **AC5 – Ảnh có liên kết.** (`WebViewEngineSessionTest`) `dispatchLongPress(8, imageUrl, linkUrl, imageUrl)` → `ImageLink`; khi listener thật gọi `requestFocusNodeHref` mà `Message` không có `url` → hạ xuống `Image`. Việc `requestFocusNodeHref` trả đúng `url/src` là kiểm trên máy thật (xem phần instrumented).
### ViewModel (JVM, `FakeEngineSession`, fake `PageActions/ClipboardGateway/DownloadLauncher/ExternalAppLauncher`)
6. **AC6 – Chỉ tab đang chọn.** (`BrowserViewModelContextMenuTest`) `ContextMenu` của tab nền không vào `pendingRequest` tới khi chọn tab đó; của tab đang chọn thì có; request không có hành động nào (`build` rỗng) tự `Dismiss()` và `pendingRequest` không chứa nó; đóng tab → engine nhận `Dismiss()` (spec 006).
7. **AC7 – Mở tab.** `OpenInNewTab(url)` → `tabManager.openTab(url, select = false)`, tab đang chọn **không đổi**, `uiState.backgroundTabId` = tab mới, `message` = "đã mở trong tab mới"; `onSwitchToBackgroundTab()` chọn nó. `OpenInPrivateTab` → tab ẩn danh nền (016); `canOpenPrivate == false` → không tạo tab, `message = "private_unavailable"`. Tab ẩn danh đang chọn + `OpenInNewTab` (tiêm trái ý) → bị từ chối, không tạo tab thường. `OpenImageInNewTab(url)` mở nền trong nhóm của tab đang chọn (tab ẩn danh → tab ẩn danh, không bao giờ tab thường). Mọi URL được kiểm lại bằng `isValidWebUrl`; URL không hợp lệ → không tạo tab, `message` lỗi.
8. **AC8 – Sao chép & chia sẻ.** `CopyLink(url)`/`CopyImageUrl`/`CopyText` → `clipboard.copy(label, text, sensitive = <tab ẩn danh>)` đúng 1 lần; `systemShowsConfirmation == false` → `message = "link_copied"`, `true` → không message. `ShareLink(url)` → `pageActions.shareLink(url, null)`.
9. **AC9 – Tải ảnh.** `DownloadImage(url)` → `downloads.start(DownloadSpec(url, referrer = <origin của trang + "/">, userAgent = <UA mặc định WebView>, isPrivate = <tab ẩn danh>))` đúng 1 lần, `message = "download_started"`; `start` trả `false` → `message = "download_failed"`. URL không phải http/https → không gọi. `referrer` không chứa path/query của trang.
10. **AC10 – Gọi / email / bản đồ.** `Call("+84912345678")` → `externalAppLauncher.launch("tel:+84912345678")`; `SendEmail("a@b.com")` → `launch("mailto:a@b.com")`; `OpenMap("Hà Nội")` → `launch("geo:0,0?q=H%C3%A0%20N%E1%BB%99i")`; kết quả `NotFound`/`Rejected` → `message` (spec 006 AC23). Không bao giờ dựng `ACTION_CALL`. Giá trị không qua kiểm lại (vd. `Call("abc")`) → không `launch`.
11. **AC11 – Response & chống cũ.** Mỗi `onContextAction(tab, id, action)` hợp lệ trả lời request bằng `Dismiss()`; `onContextAction` với `id` cũ/tab khác → no-op; hành động không thuộc `build` của request đó (vd. `ShareLink` khác URL) → bị từ chối, request vẫn chờ.
### Hành động trang (JVM + Robolectric)
12. **AC12 – Sao chép liên kết trang.** `onCopyPageLink()` với tab web → `clipboard.copy("URL", url, sensitive = isPrivate)`; `canCopyLink == false` ở home/`about:blank`/URL không phải web. (`AndroidClipboardGatewayTest`, Robolectric) `ClipData` chứa đúng văn bản; `EXTRA_IS_SENSITIVE` chỉ có khi `sensitive` và `SDK_INT >= 33` (`@Config(sdk = [32, 33])`); `systemShowsConfirmation == (SDK_INT >= 33)`.
13. **AC13 – Chia sẻ trang.** (`ShareLinkTest` cập nhật) chooser vẫn bọc `ACTION_SEND` `text/plain` với `EXTRA_TEXT = url`, `EXTRA_SUBJECT = title` (spec 005 AC26); thêm `EXTRA_EXCLUDE_COMPONENTS` chứa `ComponentName(<pkg>, MainActivity)`. `AndroidPageActions.shareLink` (liên kết không có tiêu đề) không có `EXTRA_SUBJECT`.
14. **AC14 – Mở bằng ứng dụng khác.** `onOpenInOtherApp()` ở tab thường → `pageActions.openInOtherApp(url)`; (`AndroidPageActionsTest`) activity kế tiếp là `ACTION_CHOOSER` bọc `ACTION_VIEW` `Uri.parse(url)` có `BROWSABLE`, `EXTRA_EXCLUDE_COMPONENTS` chứa `MainActivity`, không có flag `GRANT_*`; URL không phải http/https → `Unsupported` không start activity; `ActivityNotFoundException` → `Failed` → `message`. Tab ẩn danh: lần đầu hiện `open_in_app_private_warning`; chỉ xác nhận mới gọi `openInOtherApp`.
15. **AC15 – Thêm vào màn hình chính.** (`AndroidPageActionsTest`, Robolectric `ShadowShortcutManager`) `addToHomeScreen("https://example.com/a?b=1", "Ví dụ", favicon)` → `requestPinShortcut` được gọi 1 lần với `ShortcutInfoCompat`: `id == "url-" + sha256("https://example.com/a?b=1").take(16)`, `shortLabel == "Ví dụ"`, `intent.action == ACTION_VIEW`, `intent.data` đúng URL, `intent.component == ComponentName(pkg, MainActivity)`, icon khác null; tiêu đề rỗng → nhãn = host; tiêu đề 40 ký tự có ký tự bidi/điều khiển → làm sạch và cắt 25; không có favicon → icon ô chữ cái đầu của host. `isRequestPinShortcutSupported == false` → `Unsupported`, `message = "shortcut_unsupported"`. `canPinShortcut == false` ở tab ẩn danh và ở home/URL không phải web; `onAddToHomeScreen()` khi đó không gọi gì.
16. **AC16 – Mở lối tắt.** (`MainActivityShortcutTest`) intent của lối tắt (`ACTION_VIEW`, URL https, component tường minh) → `IncomingIntentResolver` → mở **tab mới** tải URL (spec 006 AC13/AC15); lối tắt với URL `javascript:`/`file:` (do app khác tạo) → bị từ chối (`Rejected`), không tab mới.
17. **AC17 – In / Lưu PDF.** `onPrint()` ở tab web → `session.createPrintAdapter(jobName)` rồi `printLauncher.print(adapter, jobName)` đúng 1 lần; `jobName` = tiêu đề, hoặc host nếu rỗng, hoặc "Pixel Browser"; bỏ ký tự điều khiển, cắt 100 ký tự. `canPrint == false` ở home/URL không phải web/`isFullscreen`/adapter `null` (đã đóng); adapter `null` → `message = "print_unavailable"` không crash. `printLauncher.print` trả `false` → `message`. (WebView) `createPrintAdapter` gọi `createPrintDocumentAdapter(jobName)` trên session chưa đóng, `null` sau `close()`.
### UI (Robolectric + Compose)
18. **AC18 – Bảng ngữ cảnh.** (`ContextMenuSheetTest`) `context_menu_sheet` nằm sát đáy, vẽ trong cây compose (không cửa sổ phụ); `context_menu_title` = host của URL (liên kết/ảnh), số điện thoại, địa chỉ email hoặc địa chỉ, **không** hiện văn bản liên kết do trang cung cấp; các `context_action_*` đúng thứ tự `ContextMenuModel`, mỗi hàng ≥ 48dp, có chữ và `Role.Button`; tap một hành động → `onContextAction` đúng 1 lần; chạm ngoài / Back → `Dismiss`. Tiêu đề cắt giữa nếu dài, có `contentDescription` đầy đủ; tiêu đề là heading (TalkBack).
19. **AC19 – Menu trang.** (`BrowserMenuTest`) `menu_copy_link`, `menu_open_in_app` `assertIsNotEnabled()` khi URL không phải web; `menu_add_shortcut` disabled khi `!canPinShortcut` (kể cả ẩn danh); `menu_print` disabled khi `!canPrint`; tap → callback tương ứng và menu đóng; thứ tự: Tải lại, Tab mới, Chia sẻ, Sao chép liên kết, Mở bằng ứng dụng khác, Thêm vào màn hình chính, In/Lưu PDF, Tìm trong trang, …
20. **AC20 – Snackbar chuyển tab.** `message = "tab_opened_background"` hiện snackbar có nút "Chuyển tới" → `onSwitchToBackgroundTab()`; bỏ qua nếu tab đó đã bị đóng; xoay màn hình giữ nút tới khi snackbar hết hạn.
21. **AC21 – Đầu-cuối.** (`MainActivityContextMenuTest`, Fake engine) tab đang ở `https://a.com`: `simulateLongPress(Link("https://b.com/p"))` → `context_menu_sheet`; tap `context_action_open_new_tab` → tab thứ hai được tạo nền, tab hiện tại vẫn là `a.com`; bảng biến mất; `simulateLongPress(Image("https://a.com/i.png"))` → tap `context_action_download_image` → `DownloadLauncher` fake nhận `DownloadSpec` đúng; ở tab ẩn danh bảng không có `context_action_open_new_tab`; đổi tab khi bảng đang mở → bảng của tab cũ biến mất khỏi màn hình, request vẫn chờ ở tab cũ.
22. **AC22 – Quyền, riêng tư, i18n.** Không thêm quyền (`ManifestPermissionTest`; `ShortcutManagerCompat` không cần quyền); chuỗi mới có `values` và `values-vi`; `lintDebug` xanh; không `Log.*` URL/số điện thoại/email; `ClipboardGateway` không bao giờ đọc clipboard (chỉ ghi).

## Edge cases
- Bấm giữ ảnh trong `<a>` mà trang chặn `contextmenu` bằng JS: `OnLongClickListener` của WebView vẫn nhận; Chromium không chặn kết quả hit-test (khác Chrome desktop) – ghi nhận, kiểm thủ công.
- Liên kết `target=_blank`/`rel=noopener`: mở tab mới không có quan hệ opener (khác `NewWindow` của spec 007).
- Bấm giữ ngay lúc đang tải/điều hướng: request bị hủy khi trang mới bắt đầu (AC4); bảng đang mở sẽ tự đóng.
- Hai vòng bấm giữ nhanh: chỉ bảng thứ hai (AC4).
- Bấm giữ trên `<video>`/`<canvas>`/SVG: `UNKNOWN_TYPE` → không menu (video có điều khiển riêng – D01).
- Ảnh có URL rất dài (query chữ ký): hiển thị host, sao chép toàn bộ; tải với đúng URL.
- Ảnh lớn/ảnh chặn hotlink (cần `Referer`): DownloadManager tạm không gửi cookie và Referer chỉ do 019; có thể thất bại – hiển thị lỗi chung.
- Tiêu đề trang chứa ký tự RTL/emoji trong nhãn lối tắt: làm sạch bidi, giữ emoji, cắt theo ký tự hiển thị (grapheme), không cắt giữa cặp thay thế.
- Launcher không hỗ trợ ghim lối tắt (một số ROM): `Unsupported` + thông báo; không crash.
- Tab ẩn danh: không lối tắt, copy đánh dấu nhạy cảm, mở ngoài có cảnh báo; in vẫn được (do người dùng chủ động).
- In trang đang tải dở: WebView in nội dung hiện có; chấp nhận.
- Chia sẻ qua chooser khi trang có tiêu đề rất dài: `EXTRA_SUBJECT` cắt 200 ký tự.

## Không test được bằng Robolectric (instrumented / thủ công)
- Cử chỉ bấm giữ thật và `hitTestResult` thật cho từng loại phần tử; `requestFocusNodeHref` trả `url/src` cho ảnh-trong-liên kết; không bắt đầu chọn chữ khi menu hiện. → `ContextMenuInstrumentedTest` (trang `androidTest/assets` có `<a>`, `<img>`, `<a><img></a>`, `tel:`, `mailto:`, `geo:`) + thủ công.
- Hộp thoại in và "Lưu thành PDF" thật, nội dung PDF đúng trang; in bố cục desktop/di động. → thủ công.
- Ghim lối tắt thật trên launcher Pixel/Samsung/Xiaomi; icon thích ứng hiển thị đúng; mở lối tắt khi app đã chạy (`singleTask` + `onNewIntent`). → thủ công.
- `ACTION_DIAL`/`mailto:`/`geo:` mở đúng ứng dụng; chooser "Mở bằng ứng dụng khác" không liệt kê Pixel. → thủ công.
- Android 13+ tự hiện xác nhận sao chép và `EXTRA_IS_SENSITIVE` che nội dung trong bubble clipboard. → thủ công API 33+.
- TalkBack đọc bảng ngữ cảnh. → thủ công.

## Tests liên quan
- `:core:engine` JVM: `HitMapperTest` (AC1); `EngineSessionContractTest` bổ sung AC3–AC4 (Fake + WebView), `EngineRequestResponseTableTest` (AC3), `WebViewEngineSessionTest` (AC5, AC17 phần engine).
- `:feature:browser`: JVM `ContextMenuModelTest` (AC2), `BrowserViewModelContextMenuTest` (AC6–AC11, AC17 phần VM); Robolectric `AndroidClipboardGatewayTest` (AC12), `ShareLinkTest` (AC13), `AndroidPageActionsTest` (AC14, AC15), `ContextMenuSheetTest` (AC18), `BrowserMenuTest` (AC19), `BrowserScreenTest` (AC20).
- `:app`: `MainActivityShortcutTest` (AC16), `MainActivityContextMenuTest` (AC21), `ManifestPermissionTest` (AC22).
- Instrumented: `ContextMenuInstrumentedTest`.

## Out of scope
- Xem trước liên kết trong dialog (C08); chọn văn bản, tra từ, dịch vùng chọn (B15); chia sẻ ảnh (cần tải về), lưu ảnh vào thư viện ảnh ngoài thư mục Tải xuống; "Tải liên kết"; mã QR của liên kết (C07).
- Danh sách lối tắt động / lối tắt khi nhấn giữ biểu tượng app; widget (spec 010 đã loại).
- Tự ghi file PDF không qua hộp thoại in; chọn trang/khổ giấy tùy biến trong Pixel.
- Tab ẩn danh cho lối tắt; mở liên kết ngoài vào tab ẩn danh.
- Trình tải xuống riêng (019): ở đây chỉ có `DownloadLauncher` tạm.

## Quyết định
- (spec-writer, 2026-11-10) "Mở ở tab mới" mở nền kèm nút "Chuyển tới" (như Chrome); tab ẩn danh không có "tab mới thường" để tránh rò rỉ ngữ cảnh.
- (spec-writer) Chỉ liên kết/ảnh http/https có menu; mọi scheme khác bị bỏ qua thay vì hiện menu "sao chép" (tránh dụ người dùng sao chép `javascript:`).
- (spec-writer) `DownloadLauncher` là seam: 018 dùng DownloadManager tạm (không cookie), 019 thay bằng trình tải riêng mà không đổi `ContextAction`/AC của spec này.
- (spec-writer) Chia sẻ chooser loại chính Pixel; lối tắt bị tắt ở tab ẩn danh.
- (spec-writer) Spec tách được 2 PR (018A B13, 018B B09); `menu_print` cần kiểm thủ công nhiều ROM trước khi bật mặc định.

## Quyết định (orchestrator, 2026-11-10)
- Chấp nhận các đề xuất của spec-writer: cách ly ẩn danh bằng WebView `Profile` (androidx.webkit `MULTI_PROFILE`), tên profile không tái dùng, fail-closed khi thiếu API; cookie bên thứ ba ở tab thường giữ "cho phép" tới C02 (tab ẩn danh luôn chặn); tải xuống ghi `.part` rồi xuất bản (2× dung lượng tạm thời, tối ưu sau); FGS `dataSync` trước; cài APK thử `ACTION_VIEW` với dự phòng mở ứng dụng Tệp.
- Các mục chỉ kiểm được trên máy thật (Profile isolation, cleartext/network security config, SAF/MediaStore, notification, blob:/data:) được ghi vào E05 và chạy thủ công/instrumented khi có thiết bị; không chặn merge nếu phần JVM/Robolectric xanh.
- Thứ tự triển khai: 016 → 017 → 018 → 019; 019 chia 4 PR (019A lõi, 019B service/thông báo, 019C màn hình/cài đặt/APK, 019D blob:/data:). Việc sửa `network_security_config` (cleartext) trong 017 làm sớm vì ảnh hưởng mọi trang http.
