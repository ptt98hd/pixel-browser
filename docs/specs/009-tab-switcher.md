# Spec 009: Tab switcher dạng lưới – thumbnail, vuốt để đóng, hoàn tác, tab mới, đóng tất cả (A09)
## Story
Người dùng nhấn nút Tabs và thấy toàn màn hình một lưới thẻ tab (2 cột trên điện thoại), mỗi thẻ có ảnh chụp trang, favicon và tiêu đề; tab đang mở được làm nổi bật. Vuốt thẻ sang ngang để đóng và bấm "Hoàn tác" nếu lỡ tay; nút nổi để mở tab mới; "Đóng tất cả" có xác nhận. Chọn thẻ thì thẻ phóng to thành trang.

## Phạm vi & module
- Thay `TabSwitcherSheet` (ModalBottomSheet, spec 005 AC27–AC30) bằng `TabSwitcher` toàn màn hình. Implement **sau** spec 010 (tab home: `Tab.isHome`, thẻ home không có WebView).
- `:core:tabs`: thay đổi nhỏ ở `undoClose`, thêm `closeAllTabs`.
- `:core:engine`: favicon.
- `:feature:browser`, package `app.pixel.browser.feature.browser.tabs`: `TabSwitcher`, `TabCard`, `ThumbnailCache` (thuần JVM), `thumbnailSize` (thuần), `tabGridColumns` (thuần), `TabThumbnailRepository` (interface, không kiểu Android), `BitmapTabThumbnailRepository` (Android).
- `:app`: tạo `BitmapTabThumbnailRepository(cacheDir/thumbnails, ioDispatcher)` trong `PixelBrowserApplication`, nối `onTrimMemory`.

## Thay đổi API
### `:core:tabs`
- `undoClose(): Tab?` – **đổi hành vi so với spec 002**: tab khôi phục chỉ được chọn nếu nó là tab đang chọn lúc bị đóng; ngược lại lựa chọn hiện tại giữ nguyên. Nếu danh sách hiện chỉ có đúng tab home do `closeTab` (đóng tab cuối) hoặc `closeAllTabs` tự tạo và tab đó chưa dùng (`isHome`, session chưa tải gì), tab home đó bị bỏ (đóng session, không vào ngăn xếp undo) trước khi khôi phục.
- `fun closeAllTabs()` – đóng mọi tab (kể cả ẩn danh) theo thứ tự **từ phải sang trái** (mỗi tab vào ngăn xếp undo với index gốc của nó, nên undo liên tiếp trả về tab trái nhất trước và dựng lại đúng thứ tự cũ; giới hạn `MAX_UNDO` giữ các tab trái nhất), rồi tạo một tab home mới được chọn.
- Cập nhật mục "Quyết định" của spec 002 cho khớp (orchestrator sửa khi merge).

### `:core:engine`
```kotlin
interface EngineSession {
    // ...
    /** Favicon của trang hiện tại; null khi chưa có. Reset về null khi bắt đầu tải trang main-frame mới. */
    val favicon: StateFlow<android.graphics.Bitmap?>
}
```
- WebView: `WebChromeClient.onReceivedIcon` → `dispatchReceivedIcon(bitmap)`; `loadUrl`/`dispatchPageStarted` → `null`. Fake: `simulateFavicon(bitmap: Bitmap?)`.

### `:feature:browser`
```kotlin
/** Không chứa kiểu Android để BrowserViewModel test được trên JVM. */
interface TabThumbnailRepository {
    fun capture(tabId: String, session: EngineSession, persist: Boolean)  // persist = !tab.isPrivate
    fun remove(tabId: String)          // xóa file trên đĩa; bản trong bộ nhớ giữ lại (phục vụ undo)
    fun retainOnly(tabIds: Set<String>) // xóa file mồ côi (gọi một lần khi khởi tạo ViewModel)
    fun clearDisk()
    object None : TabThumbnailRepository
}
class ThumbnailCache<K : Any, V : Any>(maxBytes: Long, sizeOf: (V) -> Int) {
    operator fun get(key: K): V?; fun put(key: K, value: V): Boolean; fun remove(key: K); fun clear(); val sizeBytes: Long
}
data class ThumbnailSize(val width: Int, val height: Int)
fun thumbnailSize(viewWidth: Int, viewHeight: Int, maxWidth: Int = THUMBNAIL_MAX_WIDTH): ThumbnailSize?   // THUMBNAIL_MAX_WIDTH = 360
fun tabGridColumns(widthDp: Int): Int
class BitmapTabThumbnailRepository(dir: File, io: CoroutineDispatcher, maxBytes: Long = 24L shl 20) : TabThumbnailRepository {
    @Composable fun thumbnail(tabId: String): ImageBitmap?   // bộ nhớ trước, miss thì đọc đĩa trên io
    fun trim(level: Int)
}

// BrowserViewModel: tham số constructor mới `thumbnails: TabThumbnailRepository = TabThumbnailRepository.None`
fun onOpenTabSwitcher()
fun onUndoCloseTab()
fun onCloseAllTabs()
// TabItem thêm: isHome: Boolean, isPrivate: Boolean
// BrowserActions thêm: onOpenTabSwitcher, onUndoCloseTab, onCloseAllTabs
```
- `TabSwitcher(tabs, selectedTabId, thumbnail: @Composable (String) -> ImageBitmap?, favicon: @Composable (String) -> ImageBitmap?, onSelect, onClose, onUndoClose, onNewTab, onCloseAll, onDismiss)`.

### Test tag – migration
- Đổi: `TAB_SHEET = "tab_sheet"` → `TAB_SWITCHER = "tab_switcher"`; `TAB_SHEET_NEW = "tab_sheet_new"` → `TAB_SWITCHER_NEW = "tab_switcher_new"` (FAB). Giữ: `tab_item_<id>`, `tab_close_<id>`.
- Mới: `tab_switcher_close_all`, `close_all_dialog`, `close_all_confirm`, `close_all_cancel`, `tab_undo` (action của snackbar), `tab_thumb_<id>`, `tab_thumb_placeholder_<id>`, `tab_favicon_<id>`, `tab_favicon_fallback_<id>`.
- Test hiện có phải sửa trong cùng PR: `TabSwitcherSheetTest` → đổi tên `TabSwitcherTest` (AC27–AC30 của spec 005 chuyển thành AC10–AC16 dưới đây), `BrowserScreenTest` (dòng dùng `TAB_SHEET`), `BrowserBackHandlingTest` (AC31(a)). Xóa `TabSwitcherSheet.kt`.

## Acceptance criteria
### TabManager (JVM, `TabManagerTest`)
1. **AC1 – Undo giữ lựa chọn.** [A, B, C], C chọn: `closeTab(A)` → `undoClose()` → thứ tự [A, B, C], **C vẫn chọn**. `closeTab(C)` (đang chọn → B được chọn) → `undoClose()` → C được chọn lại. Test `undoClose_*` hiện có vẫn xanh.
2. **AC2 – Undo sau khi đóng tab cuối.** Chỉ có A: `closeTab(A)` → có tab home H (mới); `undoClose()` → danh sách `[A]`, A chọn, session của H `isClosed`, `undoClose()` lần nữa → `null` (H không vào ngăn xếp). Nếu H đã được dùng (`loadUrl` qua spec 010) trước khi undo → H giữ nguyên, danh sách `[A, H]`.
3. **AC3 – `closeAllTabs`.** [A, B, C] → `closeAllTabs()` → đúng 1 tab, `isHome`, được chọn, id mới; session A, B, C `isClosed`. `undoClose()` lần lượt trả A, B, C; lần undo đầu bỏ tab home chưa dùng (AC2); sau 3 lần danh sách là [A, B, C]. Với 12 tab → chỉ 10 tab trái nhất khôi phục được, theo đúng thứ tự.

### Engine (`EngineSessionContractTest`, Robolectric cho cả Fake và WebView vì cần `Bitmap`)
4. **AC4 – Favicon.** Session mới: `favicon.value == null`. WebView `dispatchReceivedIcon(bmp)` / Fake `simulateFavicon(bmp)` → `favicon.value === bmp`. Sau `loadUrl(...)` hoặc `dispatchPageStarted`/`simulatePageStarted` → `null`. Sau `close()` → `null`, mọi lời gọi no-op.

### Thumbnail (thuần JVM)
5. **AC5 – `ThumbnailCache`.** `maxBytes = 100`, `sizeOf = { it.length }` với `String`: put "a"(40), "b"(40) → `sizeBytes == 80`; `get("a")` rồi put "c"(40) → "b" bị đẩy ra (LRU theo truy cập), "a", "c" còn. Put value 101 byte → trả `false`, không đẩy gì ra. Put trùng key thay giá trị và cập nhật `sizeBytes`. `remove`, `clear` → `sizeBytes` đúng. (`ThumbnailCacheTest`)
6. **AC6 – `thumbnailSize`.** `(1080, 2000)` → `(360, 667)` (làm tròn gần nhất); `(300, 600)` → `(300, 600)` (không phóng to); `(0, 100)`, `(100, 0)` → `null`. (`ThumbnailSizeTest`)
7. **AC7 – `tabGridColumns`.** 320, 411, 599 → 2; 600, 839 → 3; 840, 1280 → 4. (`TabGridColumnsTest`)

### ViewModel (JVM, `FakeTabThumbnailRepository` ghi lại lời gọi kèm `selectedTabId` tại thời điểm gọi)
8. **AC8 – Chụp khi rời tab.** A chọn (đang ở trang web), B nền: `onSelectTab(B)` → `capture(A, sessionA, persist = true)` được gọi **khi A còn đang chọn**, rồi B được chọn. `onNewTab()` và `onOpenTabSwitcher()` → `capture` tab hiện tại. Tab đang ở home (`isHome`) → không `capture`. Tab `isPrivate` → `persist = false`. `onSelectTab(<tab đang chọn>)` → không capture. (`BrowserViewModelThumbnailTest`)
9. **AC9 – Đóng/undo/close all/khởi tạo.** `onCloseTab(B)` → `remove(B)`; `onUndoCloseTab()` → `tabManager.undoClose()`; `onCloseAllTabs()` → `closeAllTabs()` và `remove` cho từng id cũ; ViewModel mới với store có [A, B] → `retainOnly({A, B})` đúng 1 lần (tab ẩn danh không nằm trong tập). `TabItem` có `isHome`, `isPrivate` đúng. (`BrowserViewModelThumbnailTest`)

### Repository Android (Robolectric, `@GraphicsMode(NATIVE)`, `StandardTestDispatcher` làm `io`)
10. **AC10 – Capture.** Session giả trả `view` là `View` đã layout 1080×2000 với nền đỏ: `capture("t1", s, persist = true)` → `thumbnail("t1")` có kích thước 360×667, pixel giữa đỏ, `Bitmap.Config.RGB_565`; sau `advanceUntilIdle()` tồn tại `dir/t1.webp`. `persist = false` → không bao giờ có file `t1.webp` (kể cả sau `advanceUntilIdle`). `view == null` hoặc kích thước 0 → không có thumbnail, không crash. (`BitmapTabThumbnailRepositoryTest`)
11. **AC11 – Vòng đời file.** `remove("t1")` → file bị xóa, `thumbnail("t1")` vẫn trả bitmap (bộ nhớ). Repository mới cùng `dir` (process mới) → `thumbnail("t1")` đọc từ đĩa nếu file còn. `retainOnly({"t2"})` → xóa `t1.webp`, giữ `t2.webp`, bỏ qua file không có đuôi `.webp`. `trim(TRIM_MEMORY_BACKGROUND)` (và mức cao hơn) → bộ nhớ trống, file giữ nguyên; `trim(TRIM_MEMORY_RUNNING_LOW)` → không đổi. File hỏng → `null`, file bị xóa. (`BitmapTabThumbnailRepositoryTest`)

### UI (Robolectric + Compose, `BrowserRoute` với Fake)
12. **AC12 – Mở toàn màn hình.** Tap `btn_tabs` → `tab_switcher` có `boundsInRoot` bằng root (che cả top/bottom bar), `onOpenTabSwitcher` được gọi 1 lần. Có `tab_item_<id>` cho mọi tab; với `@Config(qualifiers = "w411dp-h891dp")` 4 tab xếp 2 cột theo thứ tự `TabManager` (tab i ở hàng i/2, cột i%2 – so `boundsInRoot`); `w840dp` → 4 cột. Với 30 tab và tab thứ 30 được chọn, `tab_item` của nó `assertIsDisplayed()` ngay khi mở (tự cuộn tới). (`TabSwitcherTest`)
13. **AC13 – Nội dung thẻ.** Tiêu đề: title, rỗng thì `UrlDisplay.host(url)`, tab home thì chuỗi resource "Tab mới". Có thumbnail → `tab_thumb_<id>`, không có (hoặc tab home) → `tab_thumb_placeholder_<id>`. Có favicon → `tab_favicon_<id>`; không có → `tab_favicon_fallback_<id>` hiện chữ cái đầu của host (tab home: icon home). Tab chọn: `assertIsSelected()`, các tab khác `assertIsNotSelected()`. Nút `tab_close_<id>` có `contentDescription` chứa tiêu đề, vùng chạm ≥ 48dp. (`TabSwitcherTest`)
14. **AC14 – Chọn.** Tap `tab_item_<B>` → `selectedTabId == B` ngay (trước animation); sau `mainClock.advanceTimeBy(1000)` không còn `tab_switcher`, `url_display` hiện host của B. Back hệ thống khi switcher mở → đóng switcher, lựa chọn không đổi (spec 005 AC31(a)). (`TabSwitcherTest`, `BrowserBackHandlingTest`)
15. **AC15 – Đóng bằng nút/vuốt + hoàn tác.** Tap `tab_close_<B>` → B biến mất, `tab_count` giảm, xuất hiện `tab_undo`; tap `tab_undo` → B về đúng vị trí, `tab_undo` biến mất, lựa chọn theo AC1. `performTouchInput { swipeLeft() }` (và `swipeRight()`) trên `tab_item_<B>` với quãng > 50% chiều rộng thẻ → như tap close; vuốt 20% rồi thả chậm → thẻ trở lại, không đóng. Đóng 2 tab liên tiếp → snackbar thuộc lần đóng sau; undo khôi phục tab đóng sau cùng. Đóng tab cuối cùng → còn 1 thẻ home mới, switcher vẫn mở; undo → AC2. Mỗi thẻ có `CustomAccessibilityAction` "Đóng tab". (`TabSwitcherTest`)
16. **AC16 – Tab mới.** Tap `tab_switcher_new` (FAB, góc dưới phải, phía trên navigation bar) → thêm tab home ở cuối, được chọn, switcher đóng, `home_page` hiển thị (spec 010). (`TabSwitcherTest`)
17. **AC17 – Đóng tất cả.** Tap `tab_switcher_close_all` → `close_all_dialog`; `close_all_cancel` (hoặc Back) → dialog đóng, số tab không đổi; `close_all_confirm` → `closeAllTabs()`, switcher đóng, còn 1 tab home được chọn. Với 1 tab home duy nhất → `tab_switcher_close_all` `assertIsNotEnabled()`. (`TabSwitcherTest`)
18. **AC18 – Motion.** Với `mainClock.autoAdvance = false`: ngay sau tap thẻ, `tab_switcher` vẫn tồn tại (đang animation phóng thẻ ra toàn màn hình); sau 1000ms → không còn. Mở switcher cũng có animation thu trang thành thẻ (switcher tồn tại ngay frame đầu, thẻ chọn hiện đủ sau 1000ms). Không có animation nào chặn input quá 600ms (chọn thẻ khác trong lúc mở vẫn được). Animation dùng spring của spec 011 nếu đã có, ngược lại `spring()` mặc định. (`TabSwitcherMotionTest`)

## Edge cases
- Chụp thumbnail: chỉ khi `session.view` đã gắn và có kích thước; WebView đang tải dở vẫn chụp (ảnh có thể trắng – chấp nhận). Chụp chạy trên main (vẽ `View` vào `Canvas` bitmap đã thu nhỏ), mã hóa WebP lossy (quality 70) và ghi file trên `io`.
- Tab ẩn danh: thumbnail chỉ trong bộ nhớ, không bao giờ ghi đĩa (AC8, AC10); khi B06 thoát ẩn danh thì xóa khỏi bộ nhớ (B06).
- Tab bị discard (spec 002): không có view, giữ thumbnail cũ trong cache/đĩa.
- Thumbnail cũ sau khi tab điều hướng ở nền (popup, redirect): chấp nhận ảnh cũ tới lần rời tab kế tiếp.
- `onTrimMemory`: `TRIM_MEMORY_BACKGROUND` trở lên xóa cache bộ nhớ; đĩa giữ.
- `cacheDir` có thể bị hệ thống xóa → placeholder, không lỗi.
- Thẻ có tiêu đề rất dài/RTL/emoji: 1 dòng, ellipsis.
- Snackbar undo biến mất khi đóng switcher; ngăn xếp undo vẫn giữ (khôi phục sau qua B11).
- Vuốt đóng nhiều thẻ nhanh: mỗi thẻ đóng đúng một lần (key = tab id, không theo index).
- Xoay màn hình khi switcher mở: switcher vẫn mở (`rememberSaveable`), số cột tính lại theo chiều rộng mới.
- 0 thumbnail trên đĩa khi nâng cấp từ bản cũ: tất cả placeholder cho tới khi rời tab.

## Không test được bằng Robolectric (instrumented / thủ công)
- Chụp WebView thật (Robolectric không render WebView; hardware layer/SurfaceView video ra màu đen). → `TabThumbnailInstrumentedTest` (chụp trang `androidTest/assets` có màu nền xác định, so màu pixel giữa).
- Độ mượt animation shared-element, fling vuốt đóng, hiệu năng lưới 100 tab (jank). → thủ công + Macrobenchmark (E05).
- Áp lực bộ nhớ thật / `onTrimMemory` từ hệ thống. → thủ công (`adb shell am send-trim-memory`).

## Tests liên quan
- `:core:tabs` JVM: `TabManagerTest` (AC1–AC3).
- `:core:engine`: `EngineSessionContractTest` (AC4, Robolectric).
- `:feature:browser` JVM: `ThumbnailCacheTest` (AC5), `ThumbnailSizeTest` (AC6), `TabGridColumnsTest` (AC7), `BrowserViewModelThumbnailTest` (AC8, AC9).
- `:feature:browser` Robolectric: `BitmapTabThumbnailRepositoryTest` (AC10, AC11), `TabSwitcherTest` (AC12–AC17, thay `TabSwitcherSheetTest`), `TabSwitcherMotionTest` (AC18), `BrowserBackHandlingTest` (AC14).
- Instrumented: `TabThumbnailInstrumentedTest`.

## Out of scope
- Tab groups, nhóm theo site, kéo thả sắp xếp tab, danh sách "tab đã đóng gần đây" (B11).
- Khu vực/tab ẩn danh riêng trong switcher, `FLAG_SECURE` cho thumbnail ẩn danh (B06).
- Tìm kiếm trong danh sách tab; dạng danh sách (list) thay cho lưới.
- Lưu favicon xuống đĩa (dùng chung với lịch sử/bookmark ở B01/B02).

## Quyết định (orchestrator, 2026-11-05)
- Chấp nhận các đề xuất của spec-writer: giữ material3 theo BOM (1.4.0) với component tự viết; thêm `datastore-preferences` + `graphics-shapes`; shortcut mặc định Wikipedia/YouTube/GitHub/OpenStreetMap; chưa có Forward từ home; `undoClose` chỉ chọn lại tab nếu tab đó đang được chọn khi đóng (cập nhật spec 002 khi merge 009); pull-to-refresh có fallback `canScrollVertically(-1)` nếu `onOverScrolled` không được gọi.
- Thứ tự: 007 → 010 → 009 → 008; 011 song song (011 tạo `:core:data` nếu làm trước 010).
- Triển khai (2026-11-09), điểm lệch/ghi chú:
  - `BrowserViewModel(thumbnails = ...)`; `BrowserRoute(thumbnails: BitmapTabThumbnailRepository? = null)` cấp ảnh cho thẻ (null → placeholder); favicon lấy từ `session.favicon` của từng tab qua `BrowserViewModel.faviconOf(tabId)`. `PixelBrowserApplication.thumbnails` (lazy, `cacheDir/thumbnails`, `Dispatchers.IO`) nhận `onTrimMemory`.
  - `TabSwitcher` là overlay trong cây compose của `BrowserScreen` (không phải Dialog) để phủ đúng root; có `SnackbarHost` riêng (nút `tab_undo`) và `modifier` thêm cuối danh sách tham số. Chọn thẻ/tab mới/đóng tất cả gọi callback ngay rồi chạy animation thoát (phóng to quanh thẻ + mờ, `PixelMotion.spatial()` qua `pixelSpring`) và gọi `onDismiss`. Back hệ thống đóng switcher không animation.
  - Khi switcher mở, `BrowserRoute` pause session phía sau và resume khi đóng (nếu lifecycle RESUMED, không ở home). Chụp thumbnail diễn ra trước khi pause.
  - `BitmapTabThumbnailRepository` chạy mọi thao tác file tuần tự trên `io.limitedParallelism(1)`, ghi qua file `.tmp` rồi rename; đọc đĩa dùng RGB_565.
  - `TabManager.undoClose`: tab home tự tạo được theo dõi bằng `autoHomeId`; chỉ bị bỏ khi là tab duy nhất, còn `isHome`, không có trang bên dưới và `backToHome == false`.
  - `TabItem.displayTitle()` chuyển sang package `tabs`, trả "" cho tab home (UI dùng `browser_untitled_tab`). Xóa `TabSwitcherSheet.kt`, `TAB_SHEET*`.
  - `TabThumbnailInstrumentedTest` chưa viết (cần thiết bị; nằm ngoài phạm vi JVM/Robolectric).
- Review (2026-11-10): `EngineContainer(session, paused)` bỏ qua `onResume` theo lifecycle khi switcher mở (test `TabSwitcherPauseTest`). Repository dọn `*.webp.tmp` trong `retainOnly`/`clearDisk` và xóa tmp khi ghi lỗi; `fileOf` từ chối id chứa `/`, `\`, `..` hoặc rỗng (không đọc/ghi/xóa file). Assertion cũ `ac11_retainOnly...` giữ lại `t3.webp.tmp` được đổi thành "bị xóa" theo yêu cầu review. Thẻ vuốt đóng nhưng không bị gỡ thì được đặt lại (`closing = false`, offset 0). TODO B06: xóa bitmap tab ẩn danh đã đóng khỏi bộ nhớ. Không làm: bỏ qua favicon của URL cũ (engine đã reset favicon khi bắt đầu trang mới).
