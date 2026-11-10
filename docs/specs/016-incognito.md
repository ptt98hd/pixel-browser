# Spec 016: Tab ẩn danh (Incognito) với dữ liệu tách biệt (B06)
## Story
Từ menu chọn "Tab ẩn danh" (hoặc bật nhóm "Ẩn danh" trong trình chuyển tab) để mở tab có giao diện tối riêng, biểu tượng ẩn danh trên thanh trên. Cookie, localStorage/IndexedDB, cache và service worker của tab ẩn danh **tách khỏi** tab thường và bị xóa khi đóng tab ẩn danh cuối cùng (hoặc ở lần mở app kế tiếp nếu app bị kill). Tab ẩn danh không ghi lịch sử, không lưu thumbnail/favicon/snapshot xuống đĩa, không gợi ý lưu mật khẩu/autofill, tùy chọn chặn chụp màn hình (mặc định bật) và tùy chọn tự đóng sau khi rời app. "Đóng tất cả tab ẩn danh" không cần thông báo hệ thống (thông báo sẽ lộ sự tồn tại của phiên ẩn danh trên màn hình khóa). Trang cho biết rõ **những gì ẩn danh không che được** (nhà mạng, website, tệp đã tải).

## Phạm vi & module
- `:core:engine`: `EngineSession.isPrivate`, `ProfileGateway` (+ `AndroidxProfileGateway`), `PrivateSpace`, `PrivateUnavailableException`; `WebViewEngineSession(context, privateProfile: String? = null)`; `PixelWebView` (cờ IME); testFixtures: `FakeProfileGateway`, `FakeEngineSession(isPrivate)`.
- `:core:tabs`: `EngineSessionFactory.create(isPrivate)`, `TabManager` (`privateTabCount`, `onPrivateTabsEmpty`, `closePrivateTabs`, `closeTab` trả `Boolean`, dọn ngăn xếp undo).
- `:core:data`, package `privacy`: `PrivacySettings`, `PrivateAutoClose`, `PrivacySettingsStore`, `DataStorePrivacySettingsStore` (+ `FakePrivacySettingsStore`). **Spec 017 mở rộng đúng `PrivacySettings` này** (thêm trường có mặc định).
- `:core:ui`: `PixelTheme(settings, private = false)` + bảng màu ẩn danh.
- `:feature:browser`: `BrowserViewModel`, `BrowserMenu`, `BrowserTopBar` (chỉ báo), `PrivateLanding`, `TabSwitcher` (nhóm), `SecureWindowPolicy`, `PrivateAutoCloseGuard`, `DownloadDialog` (cảnh báo), `TabThumbnailRepository.purge`, `SitePermissionMemory.clear`.
- `:feature:settings`: trang `private_browsing` (`SettingsPageIds.PRIVATE_BROWSING`, phần `privacy`).
- `:app`: `libs.versions.toml` thêm `androidx.webkit:webkit` (bản ổn định mới nhất có `MULTI_PROFILE`, ≥ 1.12.0 – implementer xác nhận trên Maven, ghi vào Quyết định); `PixelBrowserApplication` (`privateSpace`, `privacySettings`, `bindPrivateSpace`, factory), `MainActivity` (`FLAG_SECURE`, auto-close).
- Phụ thuộc: sau 015. Spec 017 (cookie bên thứ ba, clear data) và 018 ("Mở trong tab ẩn danh") và 019 (tải xuống ẩn danh) dùng hạ tầng ở đây.

## Quyết định thiết kế
### Cách ly dữ liệu: WebView `Profile` (androidx.webkit), **không** dùng "xóa tất cả"
- Android System WebView chỉ có **một** kho dữ liệu cho cả process; `CookieManager.getInstance()`, `WebStorage`, `WebView.clearCache` đều toàn cục. Cách duy nhất để hai loại tab sống song song mà không lẫn dữ liệu là **multi-profile**: `ProfileStore.getInstance().getOrCreateProfile(name)`, `WebViewCompat.setProfile(webView, name)`, `ProfileStore.deleteProfile(name)`, kiểm bằng `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)`. Profile có `CookieManager`/`WebStorage`/`GeolocationPermissions`/`ServiceWorkerController` riêng; cache HTTP nằm trong thư mục dữ liệu của profile (**giả định phải kiểm chứng bằng instrumented test**, AC27). Hỗ trợ phụ thuộc **bản WebView provider** trên máy (không phải API level), nên `minSdk 31` không bảo đảm.
- **Ràng buộc API** (implementer xác nhận bằng spike `ProfileSupportInstrumentedTest`): `setProfile` phải gọi ngay sau khi tạo `WebView`, trước mọi thao tác khác (settings, client, `loadUrl`); `deleteProfile` ném `IllegalStateException` khi còn WebView dùng profile đó và không xóa được profile mặc định.
- **Không có nhánh "fallback xóa sạch cookie/cache toàn cục"** như đề xuất ban đầu: khi không có multi-profile, xóa toàn cục lúc đóng tab ẩn danh sẽ **xóa luôn đăng nhập của tab thường**, và trong lúc đang mở thì tab ẩn danh **thấy và ghi vào** cookie của tab thường – đó là lời hứa riêng tư giả. Quyết định: **fail-closed** – nếu `MULTI_PROFILE` không được hỗ trợ thì **không cung cấp** tab ẩn danh (mục menu bị vô hiệu kèm lời giải thích "Cập nhật Android System WebView để dùng tab ẩn danh"). Ghi vào "Câu hỏi mở" (Q1).
### Mức bảo đảm (hiển thị trong trang giới thiệu ẩn danh và trang cài đặt)
| Bảo đảm (khi `MULTI_PROFILE` có) | Không bảo đảm |
|---|---|
| Cookie, web storage, cache, service worker, quyền vị trí của tab ẩn danh tách khỏi tab thường; xóa khi hết tab ẩn danh hoặc ở lần mở app sau | Nhà mạng, cơ quan, website vẫn thấy hoạt động; bộ nhớ đệm DNS/TLS của network stack có thể dùng chung process |
| Không lịch sử, thumbnail, favicon, snapshot tab, ngoại lệ desktop/quyền theo site ghi xuống đĩa | Tệp đã tải xuống **vẫn ở trên thiết bị**; dấu trang người dùng tự thêm vẫn được lưu |
| Chặn chụp màn hình/Recents (tùy chọn, mặc định bật); bàn phím không học từ ô nhập; không autofill | Safe Browsing (nếu bật) vẫn gửi băm URL tới dịch vụ Google; bộ nhớ RAM/swap/crash dump của hệ điều hành |
- Tab ẩn danh **không sống qua process death** (không ghi đĩa), giống Chrome; không có cơ chế khôi phục.
### Vòng đời "không gian ẩn danh" (`PrivateSpace`)
- Không gian ẩn danh = tập các tab `isPrivate` trong `TabManager`. Profile được tạo **lười** ở lần `acquireProfile()` đầu, tên ngẫu nhiên `private-<token>`; **không bao giờ tái dùng tên**. Khi `privateTabCount` về 0 (sau khi mọi session ẩn danh đã `close()`/destroy) `TabManager` gọi `onPrivateTabsEmpty`; app chạy chuỗi dọn: purge thumbnail bộ nhớ → `SitePermissionMemory` ẩn danh → (019) hủy tải xuống ẩn danh → `PrivateSpace.wipe()` (xóa profile). Tab ẩn danh bị discard (quá `maxLiveSessions`) vẫn tính là tab: profile được giữ tới khi hết tab ẩn danh.
- `wipe()` thất bại vì profile còn "đang dùng" (Chromium hủy WebView bất đồng bộ) → thử lại theo `backoffMs = [250, 1000, 5000]` rồi ghi vào danh sách chờ; lần `acquireProfile()` đầu tiên trong mỗi process còn **quét** mọi profile có tiền tố `private-` (lúc đó chắc chắn không có tab ẩn danh nào) và xóa hết – xử lý trường hợp app bị kill khi đang có tab ẩn danh.
### Không ghi đĩa (bất biến, kiểm bằng AC25)
`TabManager.snapshot()` (đã loại), `FileTabSnapshotStore`, `BitmapTabThumbnailRepository` (`persist=false`), `HistoryRecorder` (spec 012), `FaviconStore` (nếu nối UI sau này phải bỏ qua tab ẩn danh), `desktop.json` (spec 015: ngoại lệ tạm), `SitePermissionMemory` (kho **riêng** trong bộ nhớ, tách khỏi kho của tab thường – hoàn thành mục "Incognito tách bộ nhớ quyền" của spec 007), file `captures/` không liên quan. Không `Log.*` URL/tiêu đề của tab ẩn danh.
### Hành vi ngữ cảnh
- Gợi ý omnibox từ lịch sử/dấu trang **vẫn đọc được** trong tab ẩn danh (giữ spec 012 AC13, chỉ không ghi). Dấu trang do người dùng chủ động tạo vẫn cho phép.
- Tab mới (`onNewTab`, bấm giữ nút tabs) từ tab ẩn danh → tab **ẩn danh**; link mở từ app ngoài (spec 006) luôn vào tab **thường** (không đoán ý định).
- "Thêm vào màn hình chính" (spec 018) bị tắt ở tab ẩn danh; mở "ứng dụng khác" có cảnh báo; tải xuống có cảnh báo (AC14).
- Giao diện: tab ẩn danh ép bảng màu **tối cố định** (không dynamic color) để nhận ra ngay; thanh trên có biểu tượng `private_indicator`; trang chủ native của tab ẩn danh thay bằng `PrivateLanding` (giải thích + ô tìm kiếm, **không** hiện shortcut của người dùng).
- Tự đóng khi rời app: `PrivateAutoClose { Never, Minutes5, Minutes30 }` (mặc định `Never`). Kiểm lúc quay lại app (không có timer nền) → hạn chế đã biết: dữ liệu tồn tại trong lúc app ở nền tới khi quay lại hoặc app bị kill.
- Không có thông báo, không foreground service, không quyền mới.

## Thay đổi API
### `:core:engine`
```kotlin
interface EngineSession { /* ... */ val isPrivate: Boolean }
interface ProfileGateway {
    val isSupported: Boolean                                   // WebViewFeature.MULTI_PROFILE
    fun listProfileNames(): List<String>
    fun apply(webView: WebView, profileName: String)           // WebViewCompat.setProfile; ném nếu không áp được
    fun delete(profileName: String): Boolean                   // true: đã xóa/không tồn tại; false: còn đang dùng
}
class PrivateUnavailableException(message: String) : IllegalStateException(message)
class PrivateSpace(gateway: ProfileGateway, newName: () -> String = { "private-" + UUID.randomUUID() },
                   scheduler: RetryScheduler = MainHandlerScheduler, backoffMs: List<Long> = listOf(250, 1000, 5000)) {
    val isAvailable: Boolean
    fun acquireProfile(): String?      // null nếu không hỗ trợ; lần đầu trong process quét `private-*` cũ
    fun wipe()                         // xóa profile hiện tại (thử lại theo backoff); lần acquire sau cho tên mới
    val pendingWipeCount: Int
}
fun interface RetryScheduler { fun schedule(delayMs: Long, block: () -> Unit) }
class WebViewEngineSession(context: Context, privateProfile: String? = null) : EngineSession
```
- Với `privateProfile != null`: gọi `gateway.apply` ngay sau khi tạo `PixelWebView`; lỗi → hủy WebView và ném `PrivateUnavailableException` (**không** rơi về profile mặc định). `importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`; `PixelWebView.onCreateInputConnection` thêm `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` (hàm thuần `privateImeOptions(Int): Int`).
- `FakeEngineSession(isPrivate: Boolean = false)`; `FakeProfileGateway(supported = true)` ghi `appliedProfiles`, `deleted`, `names`, `failDeleteTimes`.
### `:core:tabs`
```kotlin
fun interface EngineSessionFactory { fun create(isPrivate: Boolean): EngineSession }   // thay create(); đổi mọi lambda/factory
class TabManager(sessionFactory, store, maxLiveSessions = 6, onPrivateTabsEmpty: () -> Unit = {}) {
    val privateTabCount: StateFlow<Int>
    fun closeTab(id: String): Boolean        // true nếu có thể hoàn tác (undoClose)
    fun closePrivateTabs(): Int              // số tab đã đóng
}
```
### `:core:data` (`...core.data.privacy`)
```kotlin
enum class PrivateAutoClose(val minutes: Int) { Never(0), Minutes5(5), Minutes30(30) }
data class PrivacySettings(val blockPrivateScreenshots: Boolean = true, val privateAutoClose: PrivateAutoClose = PrivateAutoClose.Never)
interface PrivacySettingsStore { val settings: Flow<PrivacySettings>; suspend fun update(transform: (PrivacySettings) -> PrivacySettings) }
```
- DataStore chung `settings`: khóa `privacy_private_secure` (Boolean), `privacy_private_auto_close` (Int phút; giá trị lạ → `Never`).
### `:feature:browser`
```kotlin
object SecureWindowPolicy { fun shouldSecure(blockScreenshots: Boolean, selectedIsPrivate: Boolean, switcherOpen: Boolean, switcherShowsPrivate: Boolean): Boolean }
class PrivateAutoCloseGuard(clock: () -> Long, autoClose: () -> PrivateAutoClose) {
    fun onBackground(hasPrivateTabs: Boolean, isChangingConfigurations: Boolean)
    fun onForeground(): Boolean              // true = phải đóng tab ẩn danh
}
// BrowserViewModel(..., privateAvailable: () -> Boolean = { false }, pageVisits, thumbnails ...)
fun onNewPrivateTab(); fun onClosePrivateTabs(); fun onSwitcherGroupChange(showPrivate: Boolean); fun onForegroundReturn()
// BrowserUiState thêm: selectedIsPrivate, canOpenPrivate, switcherShowsPrivate, lastCloseUndoable
// TabThumbnailRepository.purge(tabIds: Set<String>)     // xóa cả bộ nhớ lẫn đĩa
// SitePermissionMemory.clear()
```
- Test tag: `menu_new_private_tab`, `private_indicator`, `private_landing`, `private_landing_search`, `tab_switcher_group_normal`, `tab_switcher_group_private`, `tab_switcher_close_private`, `tab_switcher_private_empty`, `download_private_warning`, `private_browsing_screenshots`, `private_browsing_autoclose_<never|5|30>`, `private_browsing_guarantee`, `private_browsing_unavailable`.
- Chuỗi mới ở `values` và `values-vi` (menu, nhóm, landing, bảng bảo đảm, cảnh báo tải xuống, trang cài đặt, `private_unavailable`).

## Acceptance criteria
### Profile & engine (JVM với `FakeProfileGateway`; WebView qua Robolectric)
1. **AC1 – Lấy profile.** (`PrivateSpaceTest`) `acquireProfile()` lần đầu → tên bắt đầu `private-`; lần hai (chưa wipe) → cùng tên; gateway không hỗ trợ → `isAvailable == false`, `acquireProfile() == null`, gateway không bị gọi `listProfileNames/delete/apply`.
2. **AC2 – Quét profile cũ.** Gateway có `["Default", "private-old1", "private-old2", "work"]`: `acquireProfile()` đầu tiên xóa đúng `private-old1`, `private-old2` (không đụng `Default`, `work`); lần acquire sau không quét lại; `delete` trả `false`/ném → không ném ra ngoài, tên đó vào danh sách chờ (`pendingWipeCount`).
3. **AC3 – Wipe.** `wipe()` xóa tên hiện tại và lần `acquireProfile()` kế tiếp trả tên **khác**; `delete` trả `false` → thử lại đúng theo `backoffMs` (scheduler giả ghi `[250, 1000, 5000]`), hết lần → `pendingWipeCount == 1` và tên được xóa ở lần acquire/quét kế; xóa được ở lần thử giữa chừng → dừng thử, `pendingWipeCount == 0`; `wipe()` khi chưa acquire → no-op; `wipe()` hai lần liên tiếp → lần hai no-op.
4. **AC4 – Factory & TabManager.** (`TabManagerPrivateTest`) `openTab(url, isPrivate = true)` → factory nhận `isPrivate = true`, `session.isPrivate == true`; `openTab` thường → `false`; `openTabForWindow` của tab ẩn danh → popup ẩn danh (spec 007); tab ẩn danh bị discard rồi chọn lại → session mới vẫn `isPrivate`; `TabManager` khôi phục từ store → mọi tab `isPrivate == false`.
5. **AC5 – Session WebView ẩn danh.** (`WebViewPrivateSessionTest`, Robolectric) `WebViewEngineSession(ctx, "private-x")` với `FakeProfileGateway`: `apply(webView, "private-x")` đúng 1 lần và **trước khi** `webViewClient`/`webChromeClient` của engine được gán (fake kiểm tại thời điểm `apply`); `isPrivate == true`; session thường → `apply` không được gọi, `isPrivate == false`.
6. **AC6 – Fail-closed.** `apply` ném → constructor ném `PrivateUnavailableException`, WebView đã `destroy()`, không có session nào dùng profile mặc định; factory của app khi `PrivateSpace.isAvailable == false` cũng ném `PrivateUnavailableException` (không tạo WebView).
7. **AC7 – Gia cố.** Session ẩn danh: `webView.importantForAutofill == IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`; `privateImeOptions(0) and IME_FLAG_NO_PERSONALIZED_LEARNING != 0` (hàm thuần, JVM); session thường: `importantForAutofill` giữ mặc định và không đổi `imeOptions`.
### TabManager (JVM)
8. **AC8 – Hết tab ẩn danh.** (`TabManagerPrivateTest`) `privateTabCount` đếm tab `isPrivate`. Mở 2 tab ẩn danh, đóng 1 → không gọi `onPrivateTabsEmpty`; đóng tab cuối → gọi **đúng 1 lần**, tại thời điểm session của nó `isClosed == true`. Cũng gọi (1 lần) qua `closeAllTabs()`, `closePrivateTabs()`, `close()`. Đóng tab thường hoặc khởi tạo → không gọi. Mở lại tab ẩn danh rồi đóng → gọi lần nữa.
9. **AC9 – Hoàn tác.** `closeTab` của tab ẩn danh **không phải** cuối → `true`; `undoClose()` khôi phục tab đúng vị trí với `isPrivate == true`. `closeTab` của tab ẩn danh cuối → `false` và mọi tab ẩn danh bị xóa khỏi ngăn xếp undo (kể cả đã đóng trước đó): `undoClose()` không bao giờ trả tab ẩn danh sau khi không gian ẩn danh đã rỗng. `closeAllTabs()` → tab thường vẫn hoàn tác được, tab ẩn danh thì không. `closeTab` của tab thường → `true`.
10. **AC10 – `closePrivateTabs`.** Tab [N1, P1, N2, P2] chọn P2 → đóng P1, P2, trả `2`, chọn tab thường dùng gần nhất, thứ tự N1, N2 giữ nguyên; chỉ có tab ẩn danh → tạo một tab home thường được chọn; không có tab ẩn danh → trả `0`, không đổi gì.
11. **AC11 – Không persist.** `snapshot()` không chứa tab ẩn danh và `selectedTabId` không bao giờ là tab ẩn danh (kể cả khi đang chọn tab ẩn danh); `close()` (ViewModel `onCleared`) xóa hẳn tab ẩn danh khỏi `TabManager` (không `savedState`) rồi gọi `onPrivateTabsEmpty`.
### ViewModel & UI
12. **AC12 – Mở tab.** (`BrowserViewModelPrivateTest`) `onNewPrivateTab()` khi `privateAvailable() == true` → tab home ẩn danh được chọn, `uiState.selectedIsPrivate == true`; khi `false` → không tạo tab, `message == "private_unavailable"`. `onNewTab()` khi đang chọn tab ẩn danh → tab ẩn danh mới; khi chọn tab thường → thường (spec 005 giữ nguyên). `onOpenUrlInNewTab` kế thừa nhóm của tab đang chọn; `onIncomingIntent(OpenUrl)` (spec 006) luôn tạo tab **thường**, kể cả khi đang chọn tab ẩn danh. `canOpenPrivate` phản ánh `privateAvailable()`.
13. **AC13 – Không lưu.** Tab ẩn danh `simulatePageFinished` → `pageVisits.record(PageVisit(isPrivate = true))` (hồi quy spec 012: store thật không ghi); `thumbnails.capture(persist = false)`; khi `onPrivateTabsEmpty` → `thumbnails.purge(<id các tab ẩn danh>)` và `privateMemory.clear()`. Tab ẩn danh dùng `SitePermissionMemory` riêng: Allow camera ở `https://a.com` trong tab ẩn danh **không** làm tab thường tự cấp quyền và ngược lại. `onToggleDesktopSite()` ở tab ẩn danh không ghi `DesktopSitePolicy` (tham chiếu spec 015 AC20).
14. **AC14 – Cảnh báo tải xuống.** (`DownloadDialogTest`) `EngineRequest.Download` ở tab ẩn danh → `download_private_warning` hiện ("Tệp tải về được lưu trên thiết bị và không bị xóa khi đóng tab ẩn danh"); tab thường → không có; nút tải/hủy giữ nguyên hành vi; DownloadManager tạm thời **không** gửi cookie (spec 007).
15. **AC15 – Tự đóng.** (`PrivateAutoCloseGuardTest`, JVM, đồng hồ giả) `Minutes5`: `onBackground(true,false)` rồi `onForeground()` sau 4 phút 59 giây → `false`; sau 5 phút → `true` (một lần; lần gọi tiếp `false`). `Never` → luôn `false`. `hasPrivateTabs = false` hoặc `isChangingConfigurations = true` → không ghi mốc, `onForeground()` `false`. VM `onForegroundReturn()` khi guard trả `true` → `closePrivateTabs()`.
16. **AC16 – Chặn chụp màn hình.** (`SecureWindowPolicyTest`, JVM) bảng chân trị: `blockScreenshots = false` → luôn `false`; `true` → `selectedIsPrivate || (switcherOpen && switcherShowsPrivate)`. (`MainActivityPrivateTest`, Robolectric) chọn tab ẩn danh → `window.attributes.flags` có `FLAG_SECURE`; chọn tab thường → cờ bị gỡ; tắt cài đặt → không có cờ; mở trình chuyển tab nhóm ẩn danh từ tab thường → có cờ.
17. **AC17 – Menu.** (`BrowserMenuPrivateTest`) `menu_new_private_tab` hiện; `assertIsNotEnabled()` khi `!canOpenPrivate`; tap → `onNewPrivateTab` và menu đóng. Khi không khả dụng, chạm mục hiện lời giải thích qua `message`.
18. **AC18 – Chỉ báo & landing.** (`BrowserTopBarTest`, `PrivateLandingTest`) `private_indicator` hiện khi `selectedIsPrivate`, có `contentDescription` "Tab ẩn danh"; tab thường không có. Tab ẩn danh ở home hiển thị `private_landing` (giải thích + bảng bảo đảm + `private_landing_search`) **thay cho** trang chủ có shortcut: `home_*` không tồn tại; `private_landing_search` tap → `onStartEditing`.
19. **AC19 – Trình chuyển tab hai nhóm.** (`TabSwitcherPrivateTest`) `tab_switcher_group_normal/private` (Role.Tab, một nhóm chọn) lọc `tabs` theo `isPrivate`; mặc định nhóm của tab đang chọn; nhóm ẩn danh rỗng → `tab_switcher_private_empty`; FAB "tab mới" tạo tab thuộc **nhóm đang hiển thị**; chọn thẻ tab ở nhóm khác đổi `selectedTabId`; `tab_count` chỉ đếm nhóm của tab đang chọn; `onSwitcherGroupChange` được báo cho VM (`switcherShowsPrivate`). Thumbnail ẩn danh lấy từ bộ nhớ, không ghi đĩa (spec 009).
20. **AC20 – Đóng tất cả ẩn danh.** `tab_switcher_close_private` chỉ hiện ở nhóm ẩn danh khi có tab; tap → hộp xác nhận → `onClosePrivateTabs()` → `closePrivateTabs()`, snackbar **không có** "Hoàn tác" (`lastCloseUndoable == false`); đóng tab ẩn danh cuối bằng nút X cũng không hiện "Hoàn tác". Không có thông báo hệ thống nào được đăng (`ShadowNotificationManager` rỗng).
21. **AC21 – Giao diện tối riêng.** (`PixelThemePrivateTest`, `ThemeResolutionTest`) `PixelTheme(settings, private = true)` luôn dùng bảng màu tối cố định (`PrivateColorScheme`), bỏ qua `dynamicColor` và `ThemeMode`; cặp chữ/nền đạt tương phản WCAG AA (≥ 4,5:1) cho `onSurface/surface`, `onPrimary/primary`; thanh trạng thái/điều hướng dùng icon sáng khi tab ẩn danh được chọn (`SystemBarsEffect`).
22. **AC22 – Trang cài đặt.** (`PrivateBrowsingPageTest`) trang `private_browsing` (phần `privacy`, thay mục cùng id nếu có): `private_browsing_screenshots` (Switch, mặc định bật) → `onChange` đúng 1 lần; `private_browsing_autoclose_*` radio chọn theo `settings.privateAutoClose`; `private_browsing_guarantee` liệt kê "Được bảo đảm / Không bảo đảm" (bảng ở trên); `PrivateSpace.isAvailable == false` → `private_browsing_unavailable` hiện. `SettingsRegistry` có trang này; cập nhật `SettingsPagesTest` (spec 014 AC8).
23. **AC23 – Store.** (`DataStorePrivacySettingsStoreTest`, `FakePrivacySettingsStoreContractTest`) mặc định `PrivacySettings()`; round trip; giá trị `privacy_private_auto_close` lạ (7, -1) → `Never`; ghi xen kẽ với store theme/search không làm đổi khóa của nhau; IOException đọc thử lại như store theme.
24. **AC24 – Nối ứng dụng.** (`MainActivityPrivateTest`, `FakeProfileGateway`, factory Fake) menu → "Tab ẩn danh" → gateway có đúng 1 `apply` với tên `private-*`; mở thêm tab ẩn danh thứ hai → cùng tên; đóng cả hai → thứ tự gọi: session đóng → `thumbnails.purge` → `delete(name)` đúng 1 lần; mở lại → tên mới. Cold start giả lập (PrivateSpace mới, gateway còn `private-old`) + tab ẩn danh đầu tiên → `private-old` bị xóa. `onStop` giữ nguyên tab ẩn danh trong bộ nhớ nhưng `tabs.json` không có chúng.
25. **AC25 – Mỏ vàng "không ghi đĩa".** (`PrivateNoDiskTest`, Robolectric, engine Fake, kho thật: `FileTabSnapshotStore`, `BitmapTabThumbnailRepository`, Room history ở thư mục tạm) Tab ẩn danh mở `https://canary-<uuid>.test/secret?q=<uuid>`, `simulatePageFinished` với tiêu đề chứa token, bật find, đổi desktop, chụp thumbnail, `onStop`, xoay Activity, `closeTab`. Quét **mọi file** dưới `filesDir`, `noBackupFilesDir`, `cacheDir`, `databases/` (kể cả `-wal`): **không** chứa token (UTF-8 và UTF-16). `ShadowLog` không chứa token. Chạy lại với tab thường làm đối chứng: token **có** trong `tabs.json` (chứng minh quét hoạt động).
26. **AC26 – Quyền, thông báo, i18n.** (`ManifestPermissionTest`, lint) không thêm quyền/service/receiver; mọi chuỗi mới có `values` và `values-vi`; `lintDebug` xanh; không tạo `NotificationChannel`.
### Chỉ kiểm được trên máy thật
27. **AC27 – Cách ly thật.** (`PrivateProfileInstrumentedTest`, `ProfileSupportInstrumentedTest`) trên emulator/thiết bị có `MULTI_PROFILE`: (a) `setProfile` ngay sau tạo WebView hoạt động, gọi sau `loadUrl` ném lỗi (ghi lại thông báo); (b) cookie/localStorage/IndexedDB đặt ở profile ẩn danh **không** thấy từ WebView profile mặc định và ngược lại; (c) cache HTTP tách (trang `Cache-Control: max-age` + bộ đếm request ở server cục bộ); (d) `deleteProfile` ném khi còn WebView, thành công sau `destroy`, và dữ liệu biến mất (tạo lại cùng tên → rỗng); (e) hai profile ẩn danh song song không lẫn nhau. Kết quả (a)–(e) ghi vào mục "Quyết định" của spec trước khi merge; nếu (c) sai, ghi lại mức bảo đảm cache và sửa bảng.

## Edge cases
- Máy không có `MULTI_PROFILE`: mục menu vô hiệu, nhóm "Ẩn danh" trong switcher ẩn, landing/`private_browsing_unavailable` giải thích; mọi lối vào khác (popup từ tab ẩn danh không thể xảy ra vì không có tab) an toàn.
- App bị kill khi đang có tab ẩn danh: tab biến mất, profile bị quét ở lần mở sau (AC2); người dùng thấy tab thường như trước.
- `deleteProfile` kẹt lâu: dữ liệu ẩn danh còn trên đĩa tới lần quét kế tiếp (tối đa tới lần mở tab ẩn danh sau hoặc cold start kế tiếp có tab ẩn danh). Cải tiến ghi nhận: quét ở `Application.onCreate` sau khi WebView provider sẵn sàng (cần kiểm chứng không làm chậm khởi động, E05).
- Popup/`window.open` từ tab ẩn danh → tab ẩn danh cùng profile (spec 007 đã kế thừa `isPrivate`).
- File chooser/chụp ảnh từ tab ẩn danh: file tạm trong `cacheDir/captures` vẫn do spec 007 quản lý (không chứa URL); ghi nhận cho E05 dọn chủ động.
- Xoay màn hình/đổi theme: tab ẩn danh nằm trong ViewModel nên giữ; `FLAG_SECURE` áp lại sau `onCreate`.
- Intent từ app ngoài khi đang ở tab ẩn danh: mở tab thường và chuyển nhóm; chrome đổi về giao diện thường (có chuyển cảnh rõ).
- Share/Copy/In từ tab ẩn danh hoạt động (do người dùng chủ động); copy link đánh dấu `EXTRA_IS_SENSITIVE` ở API 33+ (spec 018).
- `FLAG_SECURE` cũng chặn Recents thumbnail và ghi màn hình; người dùng có thể tắt (cài đặt) – khi tắt Recents có thể hiện trang ẩn danh (nêu trong `private_browsing_guarantee`).
- Tab ẩn danh đang tải xuống khi đóng tab cuối: tải xuống ẩn danh bị hủy (spec 019).
- Bàn phím bên thứ ba có thể bỏ qua `IME_FLAG_NO_PERSONALIZED_LEARNING` (chỉ là đề nghị).

## Không test được bằng Robolectric (instrumented / thủ công)
- Mọi thứ thuộc AC27: `Profile` API thật, tách cookie/storage/cache, `deleteProfile` thật, hỗ trợ `MULTI_PROFILE` theo phiên bản WebView (kiểm trên WebView stable và WebView cũ/emulator image không hỗ trợ để thấy nhánh fail-closed).
- `FLAG_SECURE` thực sự chặn ảnh chụp/Recents/ghi màn hình; autofill không đề nghị lưu mật khẩu khi dùng Autofill service thật (Bitwarden/Google); bàn phím không học từ ô nhập (Gboard "chế độ ẩn danh"). → thủ công.
- App bị kill rồi mở lại: profile cũ bị quét, dung lượng thư mục `app_webview` giảm. → thủ công (`adb shell am kill`, kiểm `du`).
- Cảm nhận chuyển cảnh nhóm, TalkBack đọc "Tab ẩn danh", độ tương phản trên màn thật. → thủ công.

## Tests liên quan
- `:core:engine` JVM: `PrivateSpaceTest` (AC1–AC3), `PrivateImeOptionsTest` (AC7); Robolectric: `WebViewPrivateSessionTest` (AC5–AC7); testFixtures `FakeProfileGateway`.
- `:core:tabs`: `TabManagerPrivateTest` (AC4, AC8–AC11), cập nhật `TestDoubles` cho `create(isPrivate)`.
- `:core:data`: `DataStorePrivacySettingsStoreTest`, `FakePrivacySettingsStoreContractTest` (AC23).
- `:core:ui`: `PixelThemePrivateTest` (AC21).
- `:feature:browser`: JVM `BrowserViewModelPrivateTest` (AC12, AC13), `PrivateAutoCloseGuardTest` (AC15), `SecureWindowPolicyTest` (AC16); Robolectric `BrowserMenuPrivateTest` (AC17), `BrowserTopBarTest` + `PrivateLandingTest` (AC18), `TabSwitcherPrivateTest` (AC19, AC20), `DownloadDialogTest` (AC14).
- `:feature:settings`: `PrivateBrowsingPageTest` (AC22), `SettingsRegistryTest`.
- `:app`: `MainActivityPrivateTest` (AC16, AC24), `PrivateNoDiskTest` (AC25), `SettingsPagesTest`, `ManifestPermissionTest` (AC26).
- Instrumented: `PrivateProfileInstrumentedTest`, `ProfileSupportInstrumentedTest` (AC27).

## Out of scope
- Khóa tab ẩn danh bằng sinh trắc học khi quay lại (kiểu Chrome "Lock incognito tabs"); khôi phục tab ẩn danh sau khi app bị kill.
- Che bàn phím/IME hoàn toàn, chống ghi màn hình bằng thiết bị ngoài, chống nhìn trộm vật lý.
- Chặn tracker/DNS riêng/VPN (C02), Do-Not-Track/GPC (C02); chặn cookie bên thứ ba cho tab ẩn danh là spec 017.
- Engine tìm kiếm riêng, dấu trang/lịch sử riêng cho ẩn danh, tải xuống ẩn danh biến mất khi đóng (tệp vẫn nằm trên máy).
- Quét dọn `captures/` và `app_webview` ngoài profile ẩn danh (E05).

## Quyết định
- (spec-writer, 2026-11-10) Cách ly bằng WebView `Profile` (androidx.webkit) với tên không tái dùng + quét khi cold start; **fail-closed** thay vì "xóa sạch toàn cục" khi không hỗ trợ (xóa toàn cục phá phiên đăng nhập của tab thường và không cách ly lúc đang mở).
- (spec-writer) `PrivacySettings` được tạo ở spec này và mở rộng ở 017; dùng chung file DataStore `settings`.
- (spec-writer) `FLAG_SECURE` mặc định bật (riêng tư trước, đánh đổi: không chụp màn hình tab ẩn danh); tự đóng khi rời app mặc định `Never`, kiểm lúc quay lại.
- (spec-writer) Gợi ý omnibox từ lịch sử/dấu trang vẫn hiển thị trong tab ẩn danh (giữ quyết định spec 012); có thể đổi ở B14 nếu người dùng muốn.
- **Q1 (mở):** khi không có `MULTI_PROFILE`, cho phép chế độ ẩn danh "hạn chế" (cookie dùng chung, xóa toàn cục khi đóng) không? Đề xuất: **không** (fail-closed), trừ khi người dùng bật tường minh sau cảnh báo.
- (spec-writer) Chuỗi dọn khi hết tab ẩn danh là một hàm ghép ở `:app` (thumbnail → quyền → tải xuống ẩn danh → `PrivateSpace.wipe()`); 019 thêm bước tải xuống.

## Quyết định (orchestrator, 2026-11-10)
- Chấp nhận các đề xuất của spec-writer: cách ly ẩn danh bằng WebView `Profile` (androidx.webkit `MULTI_PROFILE`), tên profile không tái dùng, fail-closed khi thiếu API; cookie bên thứ ba ở tab thường giữ "cho phép" tới C02 (tab ẩn danh luôn chặn); tải xuống ghi `.part` rồi xuất bản (2× dung lượng tạm thời, tối ưu sau); FGS `dataSync` trước; cài APK thử `ACTION_VIEW` với dự phòng mở ứng dụng Tệp.
- Các mục chỉ kiểm được trên máy thật (Profile isolation, cleartext/network security config, SAF/MediaStore, notification, blob:/data:) được ghi vào E05 và chạy thủ công/instrumented khi có thiết bị; không chặn merge nếu phần JVM/Robolectric xanh.
- Thứ tự triển khai: 016 → 017 → 018 → 019; 019 chia 4 PR (019A lõi, 019B service/thông báo, 019C màn hình/cài đặt/APK, 019D blob:/data:). Việc sửa `network_security_config` (cleartext) trong 017 làm sớm vì ảnh hưởng mọi trang http.
