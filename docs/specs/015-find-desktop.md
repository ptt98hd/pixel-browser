# Spec 015: Tìm trong trang + Yêu cầu trang web máy tính (B04, B05)
## Story
**Tìm trong trang:** từ menu chọn "Tìm trong trang", một thanh tìm hiện phía trên bàn phím; gõ chữ thì các kết quả được tô sáng trong trang và hiện "2/7"; nút lên/xuống nhảy giữa các kết quả; nút đóng (hoặc Back) tắt thanh và xóa tô sáng.
**Trang web máy tính:** mục menu có dấu tích "Trang web máy tính" đổi User-Agent sang dạng máy tính và bố cục viewport rộng rồi tải lại trang. Lựa chọn nhớ **theo site** (host); có thêm mặc định toàn cục trong Cài đặt. Tab ẩn danh không lưu lựa chọn theo site. Trang đang ở chế độ máy tính hiện biểu tượng nhỏ trên thanh trên.

## Phạm vi & module
- `:core:engine`: `FindState`, `find`/`findNext`/`clearFind`/`findState`, `setDesktopSite`, `DesktopSiteResolver`, `EngineState.isDesktopSite`, `siteKeyOf`, `DesktopUserAgent`, `CacheMode.NoCache`; cập nhật `WebViewEngineSession` và `FakeEngineSession`.
- `:core:data`, package `app.pixel.browser.core.data.site`: `DesktopSiteData`, `DesktopSiteStore`, `FileDesktopSiteStore`, `DesktopSitePolicy`.
- `:feature:browser`: `BrowserViewModel` (find + desktop), `BrowserUiState`, `BrowserActions`, `BrowserMenu`, `BrowserTopBar` (chỉ báo), `FindBar` (Compose), `BrowserScreen` (Back, gesture gate).
- `:feature:settings`: trang `desktop_site` (thay trang giữ chỗ của spec 014 AC7); `:app`: nối `DesktopSitePolicy` vào factory session và `SettingsPages.kt`.
- Phụ thuộc: sau 012 (điều hướng cấp app) và 014 (registry cài đặt); độc lập với 013. Hai tính năng chia sẻ một spec vì cùng sửa `BrowserMenu`/`BrowserActions`/`BrowserUiState`; có thể tách PR (4A Find, 4B Desktop) nếu cần.

## Quyết định thiết kế
### Find
- **Dùng API WebView** `findAllAsync(query)`, `findNext(forward)`, `clearMatches()` và `setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting }`. Kết quả vào `StateFlow<FindState>`; `activeIndex` là **0-based** từ engine, UI hiển thị `activeIndex + 1`.
- **Một thanh tìm toàn cục gắn với tab đang chọn**, không nhớ qua tab. Thanh đóng khi: người dùng đóng/Back, đổi tab, mở tab mới, bấm Home, đóng tab, URL của tab đổi (điều hướng main-frame), bắt đầu sửa URL. Mở lại luôn bắt đầu với ô rỗng (không nhớ từ khóa cũ – đơn giản, tránh trạng thái ẩn).
- **Truy vấn:** chỉ gồm khoảng trắng → coi như rỗng (xóa kết quả); tối đa 256 ký tự (cắt); khoảng trắng bên trong/hai đầu của truy vấn không rỗng được giữ nguyên. Không debounce (WebView tự hủy lần tìm trước); mỗi thay đổi ô nhập gọi `find` một lần.
- **IME:** mở thanh → ô nhập có focus và hiện bàn phím; hành động IME `Search` = `findNext(true)` và ẩn bàn phím (thanh vẫn mở); thanh nằm trên bàn phím (`imePadding`). Back: đóng bàn phím trước nếu đang hiện (hành vi hệ thống), lần sau đóng thanh.
- **Kéo để tải lại tắt khi thanh tìm mở** (reload sẽ xóa kết quả).

### Desktop site
- **Hai tầng:** (1) mặc định toàn cục `defaultDesktop`; (2) ngoại lệ theo site (`siteKey` → `true/false`). Hiệu lực = tạm thời của session → ngoại lệ site → mặc định toàn cục. Ngoại lệ chỉ lưu khi **khác** mặc định lúc lưu (bật máy tính ở site khi mặc định = di động lưu `true`; chuyển lại bằng mặc định thì xóa mục); tối đa 1 000 site (LRU theo lần đặt gần nhất).
- **`siteKey`** = host viết thường, bỏ tiền tố `www.` đầu tiên, Punycode như URL gốc, bỏ cổng; chỉ cho `http`/`https`; ngoài ra `null` (không lưu được, chỉ áp tạm thời).
- **Lưu bằng JSON file** `filesDir/site/desktop.json` (`org.json` + `AtomicFile`, `version = 1`), **đọc đồng bộ** lúc tạo `DesktopSitePolicy`: engine cần trả lời "máy tính hay không" ngay khi tải trang đầu của tab được khôi phục lúc khởi động (DataStore là bất đồng bộ nên sẽ đua với việc khôi phục tab). File nhỏ (≤ 1 000 host), cùng mẫu `tabs.json`/`home/config.json`. B07 (Site settings) sẽ gộp vào kho cài đặt theo site chung; `desktop.json` là điểm di trú.
- **Tab ẩn danh:** đọc được ngoại lệ/mặc định đã lưu, nhưng bật/tắt trong tab ẩn danh chỉ là **ngoại lệ tạm thời của session đó** (`EngineSession` giữ `tempOverrides`), không ghi vào policy/đĩa; mất khi tab đóng.
- **Engine quyết định ở từng điều hướng:** `loadUrl` và `shouldOverrideUrlLoading` (main frame, gồm redirect) hỏi `DesktopSiteResolver`; nếu khác chế độ hiện tại thì đổi User-Agent/viewport **trước** khi request đi, không reload thêm. Điều hướng lịch sử (back/forward/`restoreState`) không đi qua `shouldOverrideUrlLoading`: nếu tại `onPageStarted` chế độ hiệu lực của URL khác chế độ hiện tại thì engine áp dụng và `reload()` **một lần** cho điều hướng đó (chống vòng lặp bằng cờ).
- **Đổi User-Agent:** `DesktopUserAgent.derive(mobileUa)` biến UA di động mặc định của WebView (`WebSettings.getDefaultUserAgent`) thành dạng Chrome desktop: thay khối `(Linux; Android …; …; wv)` bằng `(X11; Linux x86_64)`, bỏ token ` Mobile` và `Version/x.y `. Chế độ di động đặt lại UA mặc định (`userAgentString = null`).
- **Viewport:** desktop bật `useWideViewPort = true`, `loadWithOverviewMode = true`, `setSupportZoom(true)`, `builtInZoomControls = true`, `displayZoomControls = false`; chế độ di động khôi phục đúng giá trị ban đầu (đọc lúc khởi tạo session, không đoán). **Giới hạn đã biết:** WebView không có API bỏ qua `<meta name="viewport">`; trang có `width=device-width` vẫn bố cục theo bề rộng màn hình dù UA desktop. Chrome ghi đè meta viewport; spec này **không** chèn JavaScript để ép (rủi ro/độ phức tạp) – ghi nhận, kiểm thủ công, cân nhắc ở B15/B07.
- **Reload khi bật/tắt thủ công** dùng `CacheMode.NoCache` (`LOAD_NO_CACHE`) cho lần tải đó rồi về `Default` khi trang tải xong, để không hiện bản cache theo UA cũ.

## Thay đổi API
### `:core:engine`
```kotlin
data class FindState(
    val query: String = "",
    val activeIndex: Int = 0,        // 0-based; vô nghĩa khi total == 0
    val total: Int = 0,
    val isDone: Boolean = true,      // false trong lúc đang đếm
)
const val MAX_FIND_QUERY = 256

enum class CacheMode { Default, CacheElseNetwork, NoCache }               // thêm NoCache
data class EngineState(/* ... */ val isDesktopSite: Boolean = false)

fun interface DesktopSiteResolver { fun isDesktop(siteKey: String?): Boolean }   // siteKey null: URL không có site (about:blank…)
fun siteKeyOf(url: String): String?
object DesktopUserAgent { fun derive(mobileUserAgent: String): String }

interface EngineSession {
    // ...
    val findState: StateFlow<FindState>
    /** Tìm [query] (rỗng/khoảng trắng → như [clearFind]); cắt còn [MAX_FIND_QUERY]. Không làm gì sau [close]. */
    fun find(query: String)
    /** Nhảy tới kết quả kế tiếp/trước đó (vòng lại ở hai đầu); no-op nếu không có kết quả. */
    fun findNext(forward: Boolean)
    /** Xóa tô sáng và đặt `findState = FindState()`. */
    fun clearFind()
    /** Resolver hỏi ở mỗi điều hướng main-frame; null = luôn di động. */
    fun setDesktopSiteResolver(resolver: DesktopSiteResolver?)
    /**
     * Người dùng bật/tắt thủ công: ghi ngoại lệ tạm của session cho site hiện tại (nếu có `siteKey`), áp dụng ngay
     * và tải lại trang hiện tại (nếu là URL web) với [CacheMode.NoCache]. No-op nếu chế độ không đổi.
     */
    fun setDesktopSite(enabled: Boolean)
}
```
- WebView: `setFindListener` → `dispatchFindResult(activeMatchOrdinal, numberOfMatches, isDoneCounting)`; `find` → `findAllAsync` và `findState = FindState(query, 0, 0, isDone = false)`; `findNext` → `webView.findNext(forward)`; `clearFind` → `clearMatches()`. `dispatchPageStarted` luôn đặt lại `findState` (và `clearMatches()`). Fake: `simulateFindResult(active, total, done = true)`, ghi lại `findQueries`, `findNextCalls`, `clearFindCalls`, `reloadCount`.

### `:core:data` (`...core.data.site`)
```kotlin
data class DesktopSiteData(val version: Int = 1, val defaultDesktop: Boolean = false, val sites: Map<String, Boolean> = emptyMap())  // giữ thứ tự chèn
interface DesktopSiteStore { fun read(): DesktopSiteData?; fun write(data: DesktopSiteData) }     // I/O: gọi trên IO (write), read đồng bộ lúc khởi tạo
class FileDesktopSiteStore(file: File) : DesktopSiteStore
class DesktopSitePolicy(initial: DesktopSiteData, persist: (DesktopSiteData) -> Unit) {
    val defaultDesktop: StateFlow<Boolean>; val overrideCount: StateFlow<Int>
    fun resolve(siteKey: String?): Boolean                  // ngoại lệ site ?: mặc định
    fun overrideFor(siteKey: String): Boolean?
    fun setSite(siteKey: String, desktop: Boolean)          // bằng mặc định → xóa mục; lưu qua persist
    fun setDefault(desktop: Boolean)
    fun clearOverrides()
    companion object { const val MAX_SITES = 1000 }
}
```
### `:feature:browser`
```kotlin
data class FindUiState(val query: String, val current: Int, val total: Int, val isSearching: Boolean)   // current 1-based, 0 khi total == 0
// BrowserUiState thêm: canFind, find: FindUiState? (null = thanh đóng), canToggleDesktopSite, isDesktopSite
// BrowserViewModel(..., desktopSites: DesktopSitePolicy? = null)
fun onOpenFind(); fun onFindQueryChange(q: String); fun onFindNext(forward: Boolean); fun onCloseFind(); fun onToggleDesktopSite()
// BrowserActions thêm: onOpenFind, onFindQueryChange, onFindNext, onCloseFind, onToggleDesktopSite
```
- `BrowserMenu` thêm `menu_find` ("Tìm trong trang") và `menu_desktop_site` ("Trang web máy tính", `Checkbox` trạng thái `isDesktopSite`, `ToggleableState`). `FindBar` + test tag: `find_bar`, `find_field`, `find_count`, `find_prev`, `find_next`, `find_close`; `BrowserTopBar` thêm `desktop_indicator`.
- `:app`: `sessionFactory` đặt `session.setDesktopSiteResolver { policy.resolve(it) }` cho **mọi** session ngay khi tạo (kể cả tab khôi phục và popup); `PixelBrowserApplication.desktopSites` (lazy, đọc đồng bộ), ghi qua `ioScope`.
- `:feature:settings`: trang `desktop_site` (section `site`, `SettingsPageIds.DESKTOP_SITE`): `settings_desktop_default` (Switch), `settings_desktop_overrides` (số ngoại lệ), `settings_desktop_clear` (xóa ngoại lệ, disabled khi 0).

## Acceptance criteria
### Find – engine (Robolectric cho WebView; JVM cho Fake)
1. **AC1 – Trạng thái tìm.** (`EngineSessionContractTest`, cả Fake và WebView) Session mới: `findState == FindState()`. `find("abc")` → `FindState("abc", 0, 0, isDone = false)`; kết quả `simulateFindResult/dispatchFindResult(active = 1, total = 5, done = true)` → `FindState("abc", 1, 5, true)`; kết quả đang đếm (`done = false`) cập nhật `total`/`activeIndex` mà giữ `isDone = false`. `find("  ")` và `find("")` → `FindState()` (như `clearFind`). Truy vấn 300 ký tự → `query.length == 256`; truy vấn `" a b "` giữ nguyên khoảng trắng. `find` mới thay thế truy vấn cũ (`query` đổi ngay, `total`/`activeIndex` về 0 cho tới khi có kết quả mới). `dispatchFindResult` sau `clearFind()`/`close()` bị bỏ qua (không làm `findState` khác `FindState()`). `findNext(true/false)` không đổi `query`; không làm gì (không ném) khi `total == 0`. `clearFind()` → `FindState()`. Sau `close()`: mọi lời gọi no-op, `findState == FindState()`.
2. **AC2 – Điều hướng xóa kết quả.** `simulatePageStarted`/`dispatchPageStarted` (và `loadUrl`, `reload`, `goBack`, `goForward`) đặt lại `findState = FindState()`. Fake ghi nhận `clearFindCalls`.
3. **AC3 – Gọi WebView thật.** Ánh xạ `find` → `findAllAsync`, `findNext` → `findNext`, `clearFind` → `clearMatches`, và listener → `dispatchFindResult` **không** kiểm được bằng Robolectric (xem phần instrumented); Robolectric chỉ kiểm `dispatchFindResult` cập nhật trạng thái đúng và `find` không ném trên `WebViewEngineSession` thật.

### Find – ViewModel (JVM, `BrowserViewModelFindTest`, `FakeEngineSession`)
4. **AC4 – Mở/đóng.** Tab web (`https://a.com`): `uiState.canFind == true`, `find == null`. `onOpenFind()` → `find == FindUiState("", 0, 0, false)`, không gọi `session.find`, `isEditingUrl == false` (đang sửa URL thì hủy sửa). Ở home/`about:blank` → `canFind == false`, `onOpenFind()` không làm gì. `onCloseFind()` → `find == null` và `clearFind()` gọi đúng 1 lần (kể cả khi chưa gõ). `onStartEditing()` khi thanh mở → thanh đóng + `clearFind()`. Mở lại → ô rỗng.
5. **AC5 – Gõ & hiển thị.** `onFindQueryChange("kot")` → `session.find("kot")` đúng 1 lần mỗi lần đổi (không debounce); `""`/khoảng trắng → `clearFind()` thay vì `find`. Engine phát `FindState("kot", 2, 7, true)` → `find == FindUiState("kot", current = 3, total = 7, isSearching = false)`; `total == 0 && isDone` → `current = 0`; `isDone == false` → `isSearching = true`. `uiState.find.query` là chuỗi người dùng gõ (không bị cắt hiển thị; engine mới cắt 256). Khi thanh đóng, `onFindQueryChange` bị bỏ qua.
6. **AC6 – Next/prev.** `onFindNext(true)` → `session.findNext(true)`; `onFindNext(false)` → `findNext(false)`; khi `total == 0` hoặc thanh đóng → không gọi.
7. **AC7 – Tự đóng.** Thanh mở rồi: `onSelectTab(other)`, `onNewTab()`, `onHome()`, `onCloseTab(selected)`, `onOpenTabSwitcher()`, đổi URL của tab (`simulatePageStarted("https://b.com")` main-frame) → `find == null` và `clearFind()` gọi trên session cũ; quay lại tab cũ **không** hiện lại thanh. Thanh mở ở tab A, tab B (nền) điều hướng → không ảnh hưởng. `onSelectTab(<tab đang chọn>)` không đóng.

### Find – UI (Robolectric + Compose)
8. **AC8 – Thanh tìm.** (`FindBarTest`) `FindBar(state = FindUiState("a", 2, 7, false))` có `find_bar`, `find_field` (giá trị "a"), `find_count` hiện "2/7" (`current`/`total`), `find_prev`, `find_next`, `find_close`; `total == 0` và có chữ và không đang tìm → `find_count` hiện "0/0" + `contentDescription` "Không tìm thấy", `find_prev`/`find_next` `assertIsNotEnabled()`; ô rỗng → `find_count` không hiện; `isSearching` → hiện số hiện tại. `find_count` là vùng `liveRegion = Polite` để TalkBack đọc khi đổi. Nút ≥ 48dp, `contentDescription` có chữ ("Kết quả trước/sau", "Đóng"). Tap → `onFindNext(false/true)`, `onCloseFind`; nhập chữ → `onFindQueryChange` từng lần đổi.
9. **AC9 – IME.** Khi `find` chuyển từ `null` sang không-null: `find_field` có focus (`assertIsFocused()`) và yêu cầu hiện bàn phím (`LocalSoftwareKeyboardController.show` được gọi, kiểm bằng controller giả); `ImeAction.Search` (kiểm `SemanticsProperties.ImeAction`) → `onFindNext(true)` và ẩn bàn phím (`hide` gọi) nhưng thanh vẫn mở; `KeyboardType.Text`, không tự sửa. Thanh dùng `imePadding` (xem phần instrumented cho vị trí thật). Nội dung đang gõ giữ qua xoay (state ở ViewModel).
10. **AC10 – Menu & Back.** (`BrowserScreenFindTest`, `BrowserBackHandlingTest`) `menu_find` `assertIsNotEnabled()` khi `!canFind`; tap → `onOpenFind` và menu đóng. Thứ tự Back khi thanh mở (spec 005 AC31 mở rộng): tab switcher → menu → **thanh tìm** → sửa URL → lịch sử. Back với thanh mở → `onCloseFind` (không `goBack`, không hiện `back_gesture_indicator`); Back lần hai mới `goBack` nếu `canGoBack`. Vuốt cạnh (spec 008) và kéo-để-tải-lại tắt khi `find != null` (`GestureGate.pullToRefresh == false`).
11. **AC11 – End-to-end.** (`MainActivityFindTest`, Fake) Mở trang → menu → Tìm → gõ "kot" → `session.findQueries == ["k","ko","kot"]`; `simulateFindResult(0, 4)` → `find_count` "1/4"; tap `find_next` → `findNextCalls == [true]`, `simulateFindResult(1, 4)` → "2/4"; tap `find_close` → thanh biến mất, `clearFindCalls == 1`.

### Desktop – thuần (JVM)
12. **AC12 – `siteKeyOf`.** `https://www.Example.com/a?b#c` → `example.com`; `http://m.example.com:8080/` → `m.example.com`; `https://www.www.a.com` → `www.a.com` (chỉ bỏ một `www.`); `https://[::1]:3000/` → `[::1]`; `https://bücher.de` → `xn--bcher-kva.de`; `about:blank`, `pixel://home`, `file:///x`, `javascript:1`, `https:///`, chuỗi rỗng → `null`.
13. **AC13 – `DesktopUserAgent`.** UA mẫu `Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A.240305.019.A1; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.6478.122 Mobile Safari/537.36` → `Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Safari/537.36`. UA không có `wv`/`Version/` (Chrome Android: `…(Linux; Android 10; K) … Chrome/126.0.0.0 Mobile Safari/537.36`) cũng chuyển đúng. UA không có khối `(Linux; Android …)` → chỉ bỏ token ` Mobile`, phần còn lại giữ nguyên; chuỗi rỗng → rỗng; kết quả không chứa `Android`, `Mobile`, `wv` hay `Version/`; derive hai lần cho cùng kết quả (idempotent).
14. **AC14 – Policy.** (`DesktopSitePolicyTest`) Mặc định `resolve("a.com") == false`; `setDefault(true)` → mọi site (và `null`) `true`; `setSite("a.com", true)` khi mặc định `false` → `overrideFor == true`, `overrideCount == 1`, `persist` gọi 1 lần với dữ liệu đúng; `setSite("a.com", false)` (bằng mặc định) → xóa mục, `overrideCount == 0`; ngoại lệ thắng mặc định (default `true`, `setSite("b.com", false)` → `resolve("b.com") == false`). `setDefault` không xóa ngoại lệ. `clearOverrides()` xóa hết và `persist`. `resolve(null)` = mặc định. Đặt site thứ 1 001 → site cũ nhất bị loại (LRU theo lần đặt), `overrideCount == 1000`; đặt lại một site cũ đưa nó về "mới nhất". `siteKey` rỗng/khoảng trắng → bỏ qua, không `persist`.
15. **AC15 – Store file.** (`FileDesktopSiteStoreTest`, Robolectric) round trip giữ `defaultDesktop` và thứ tự `sites`; file không tồn tại → `null`; JSON hỏng, `version` > 1 → `null` (file lạ version được đổi tên `.unsupported` trước lần ghi đầu, như spec 010); mục lẻ (key không phải host hợp lệ — chứa `/`, khoảng trắng, quá 253 ký tự, giá trị không phải boolean) bị bỏ, phần còn lại giữ; tối đa 1 000 mục khi đọc (mục thừa bị bỏ); lỗi ghi → giữ file cũ (`AtomicFile`). `DesktopSitePolicy(initial = store.read() ?: DesktopSiteData())` không ném khi `read()` trả `null`.

### Desktop – engine (JVM cho Fake; Robolectric cho WebView)
16. **AC16 – Bật/tắt thủ công.** (`EngineSessionContractTest`, cả hai) Trang `https://a.com` đã tải: `setDesktopSite(true)` → `state.isDesktopSite == true`, đúng một reload với `cacheMode == NoCache` (Fake: `reloadCount == 1`; WebView: `settings.cacheMode == LOAD_NO_CACHE` và `userAgentString` đã đổi), cache mode về `Default` sau `simulatePageFinished`. `setDesktopSite(true)` lần nữa → không reload thêm. `setDesktopSite(false)` → `isDesktopSite == false`, reload. Chưa có trang (url rỗng/`about:blank`) → đổi trạng thái nhưng không reload. Sau `close()` → no-op.
17. **AC17 – Cài đặt WebView.** (`WebViewEngineSessionTest`, Robolectric) `setDesktopSite(true)` → `settings.userAgentString == DesktopUserAgent.derive(<UA mặc định>)`, `useWideViewPort`, `loadWithOverviewMode`, `supportZoom`, `builtInZoomControls` đều `true`, `displayZoomControls == false`. `setDesktopSite(false)` → `userAgentString` về UA mặc định (bằng giá trị ban đầu của session) và `useWideViewPort`/`loadWithOverviewMode`/`builtInZoomControls`/`displayZoomControls` về **đúng giá trị đã đọc lúc khởi tạo** (khẳng định khôi phục không đoán).
18. **AC18 – Resolver mỗi điều hướng.** (Fake + WebView) Với resolver `{ key -> key == "a.com" }`: `loadUrl("https://www.a.com/x")` → `isDesktopSite == true` **trước** khi có `simulatePageFinished` và **không** reload thêm; sau đó `loadUrl("https://b.com")` → `false`. `simulateNavigation("https://a.com/", isMainFrame = true)` (hoặc `dispatchNavigation`) đổi chế độ không reload; `isMainFrame = false` không hỏi resolver. Resolver `null` → di động. Điều hướng bị chặn/`External` không đổi chế độ. Ngoại lệ tạm của session (`setDesktopSite(true)` ở `b.com`) thắng resolver cho `b.com` trong các điều hướng sau, không ảnh hưởng `c.com`; `siteKey == null` thì không ghi ngoại lệ tạm, chế độ chỉ áp cho trang hiện tại và điều hướng kế tiếp quay về resolver.
19. **AC19 – Điều hướng lịch sử.** (Fake) Trang A (mobile) → B (desktop theo resolver) → `goBack()` rồi `simulatePageStarted(A)`: chế độ hiện tại (desktop) khác chế độ hiệu lực của A (mobile) → đổi sang mobile và reload **đúng một lần**; reload đó không kích hoạt lại chính nó (không vòng lặp, `reloadCount == 1`). `restoreState` (khôi phục tab) với trang cần desktop → như vậy (tối đa một lần).

### Desktop – ViewModel & UI
20. **AC20 – Bật/tắt từ ViewModel.** (`BrowserViewModelDesktopTest`, JVM) Tab web thường tại `https://a.com`, policy mặc định `false`: `uiState.isDesktopSite == false`, `canToggleDesktopSite == true`. `onToggleDesktopSite()` → `session.setDesktopSite(true)` và `policy.setSite("a.com", true)` đúng 1 lần mỗi thứ, `uiState.isDesktopSite == true`; bật lần nữa (tắt) → `setSite("a.com", false)` (xóa ngoại lệ khi bằng mặc định). Tab **ẩn danh**: `session.setDesktopSite(...)` có gọi nhưng `policy` **không** bị gọi (`persist` không chạy, `overrideFor == null`). `siteKey == null` → chỉ `session.setDesktopSite`. Ở home/`about:blank` → `canToggleDesktopSite == false`, `onToggleDesktopSite()` không làm gì. Tab đang chọn khác nhau có `isDesktopSite` riêng (theo `session.state`). `desktopSites == null` → toggle vẫn đổi session nhưng không lưu.
21. **AC21 – Menu & chỉ báo.** (`BrowserMenuDesktopTest`, `BrowserTopBarTest`) `menu_desktop_site` có `ToggleableState.On/Off` theo `isDesktopSite`, `assertIsNotEnabled()` khi `!canToggleDesktopSite`; tap → `onToggleDesktopSite` và menu đóng. `desktop_indicator` hiện khi `isDesktopSite && !isEditingUrl`, không hiện ở home; có `contentDescription` "Đang xem trang máy tính".
22. **AC22 – Trang cài đặt.** (`DesktopSiteSettingsPageTest`, Robolectric) `settings_desktop_default` phản ánh `defaultDesktop` (`assertIsOn()`); bật/tắt → `policy.setDefault(...)` đúng 1 lần; `settings_desktop_overrides` hiện số ngoại lệ ("0 trang"/"3 trang"); `settings_desktop_clear` disabled khi 0, tap → `clearOverrides()`; trang đăng ký với id `desktop_site` thay trang giữ chỗ trong registry (spec 014 AC7), mục `settings_item_desktop_site` mở được và `settings_placeholder_desktop_site` biến mất.
23. **AC23 – Nối ứng dụng.** (`MainActivityDesktopTest`, Fake factory + `FileDesktopSiteStore` tạm) Store có `example.com = true`: session đầu tiên của tab khôi phục/tab mới `loadUrl("https://example.com")` → `isDesktopSite == true` ngay (policy đọc đồng bộ, không cần chờ IO); tab khác `loadUrl("https://other.com")` → `false`. Đặt mặc định toàn cục `true` → tab **mới** điều hướng thành desktop, tab đang mở không bị reload. Bật máy tính ở tab thường → file `desktop.json` được ghi (sau `ioScope` idle) và tab mới cùng site tự là desktop; ở tab ẩn danh → file không đổi. Popup (`openTabForWindow`) cũng có resolver.
24. **AC24 – i18n, quyền.** Mọi chuỗi mới (menu, thanh tìm, mô tả trợ năng, cài đặt) có `values` và `values-vi`; `lintDebug` xanh. Không thêm quyền; `desktop.json`/tab snapshot không chứa URL đầy đủ của tab ẩn danh (chỉ `siteKey` của ngoại lệ đã lưu ở tab thường).

## Edge cases
- Find: trang SPA đổi nội dung sau khi tìm → số kết quả có thể cũ tới khi gõ lại (hành vi WebView, chấp nhận); trang trong iframe cross-origin có thể không đếm hết.
- Find: tìm khi trang đang tải → kết quả cập nhật khi `isDone` (hiện `isSearching`); chuyển sang trang khác → xóa.
- Find: ký tự đặc biệt/regex là chữ thường (WebView tìm chuỗi thuần); không phân biệt hoa thường (hành vi WebView).
- Find: xoay màn hình khi thanh mở → trạng thái ở ViewModel nên thanh mở lại với chuỗi cũ; focus/bàn phím được yêu cầu lại.
- Find ở tab có `isFullscreen` → thanh đóng (fullscreen bỏ chrome; `canFind == false` khi `isFullscreen`).
- Desktop: bật máy tính rồi tải lại trang chuyển hướng sang `m.site.com` → `siteKey` khác (`m.site.com`) nên theo mặc định; chấp nhận (Chrome theo origin cũng vậy).
- Desktop: `www.a.com` và `a.com` dùng chung ngoại lệ; `a.com` và `m.a.com` thì không.
- Desktop: popup kế thừa mặc định/ngoại lệ theo host của chính nó, không kế thừa `tempOverrides` của opener.
- Desktop: đổi mặc định toàn cục không reload tab đang mở; thông báo UI không cần thiết (hiệu lực ở điều hướng sau).
- Desktop: DevTools/user script phát hiện UA – ngoài phạm vi; một số trang vẫn trả layout di động do meta viewport (giới hạn đã biết).
- Desktop: ngoại lệ lưu nhiều site (người dùng bật ở ~1 000 site) → LRU loại site cũ nhất.
- Desktop + C02 (Do-Not-Track/GPC/fingerprint): UA đổi không đụng header; C02 phải dùng `DesktopSitePolicy` khi cần đồng nhất UA (ghi cho spec sau).

## Không test được bằng Robolectric (instrumented / thủ công)
- `findAllAsync`/`findNext`/`clearMatches` và `FindListener` thật: tô sáng, đếm đúng, nhảy kết quả, vòng lại ở hai đầu, tìm tiếng Việt có dấu. → `FindInPageInstrumentedTest` (trang `androidTest/assets` có số lần xuất hiện biết trước, kiểm `findState` = `n/m` đúng).
- Vị trí thanh tìm phía trên bàn phím thật (`imePadding`, `adjustResize` + edge-to-edge), bàn phím tự hiện, Back đóng bàn phím trước. → thủ công API 31 và 35+.
- UA thật gửi lên server và bố cục rộng: dùng trang `androidTest/assets` echo `navigator.userAgent`/`window.innerWidth` qua `WebViewAssetLoader` (`DesktopSiteInstrumentedTest`): UA chứa `X11; Linux x86_64`, không chứa `Mobile`; `innerWidth` ≥ 900 khi bật desktop (giới hạn meta viewport – ghi nhận kết quả thực tế).
- Pinch-zoom hoạt động ở chế độ máy tính (`builtInZoomControls`), không hiện nút zoom; cuộn ngang mượt. → thủ công.
- Điều hướng lịch sử giữa hai site khác chế độ (AC19) trên WebView thật: không nháy hai lần tải. → thủ công.
- Tính đúng của `LOAD_NO_CACHE` sau reload (không dùng cache UA cũ). → thủ công với trang phục vụ nội dung theo UA.

## Tests liên quan
- `:core:engine` JVM: `SiteKeyTest` (AC12), `DesktopUserAgentTest` (AC13), `FakeEngineSessionDesktopTest` (AC18, AC19 trên Fake); `EngineSessionContractTest` (AC1, AC2, AC16, cả Fake và WebView), `WebViewEngineSessionTest` (AC3, AC17, AC18 phần WebView – Robolectric).
- `:core:data`: JVM `DesktopSitePolicyTest` (AC14); Robolectric `FileDesktopSiteStoreTest` (AC15).
- `:feature:browser`: JVM `BrowserViewModelFindTest` (AC4–AC7), `BrowserViewModelDesktopTest` (AC20); Robolectric `FindBarTest` (AC8, AC9), `BrowserScreenFindTest` + `BrowserBackHandlingTest` (AC10), `BrowserMenuDesktopTest` + `BrowserTopBarTest` (AC21).
- `:feature:settings`: `DesktopSiteSettingsPageTest` (AC22), `SettingsRegistryTest` (cập nhật cho id `desktop_site`).
- `:app`: `MainActivityFindTest` (AC11), `MainActivityDesktopTest` (AC23); lint/`ManifestPermissionTest` (AC24).
- Instrumented: `FindInPageInstrumentedTest`, `DesktopSiteInstrumentedTest`.

## Out of scope
- Tìm trong trang: nhấn mạnh phân biệt hoa thường/từ nguyên, thay thế, tìm trong PDF viewer, đánh dấu trên thanh cuộn, nhớ từ khóa cũ.
- Desktop: ép bỏ `<meta viewport>` bằng JavaScript, tùy biến chuỗi UA, UA riêng theo site ngoài hai chế độ, danh sách và chỉnh sửa ngoại lệ từng site (B07), nhớ mức zoom theo site (B07/B15).
- Desktop site cho trang chủ native (spec 010 không dùng WebView).
- Đồng bộ ngoại lệ giữa thiết bị; sao lưu `desktop.json` (app tắt backup).

## Quyết định
- (spec-writer, 2026-11-10) Hai tính năng chung một spec (cùng chạm menu/actions/uiState); có thể tách PR.
- (spec-writer) Ngoại lệ desktop lưu bằng JSON file đọc đồng bộ (không DataStore) để chế độ đúng ngay ở lần tải đầu khi khôi phục tab; B07 sẽ hợp nhất vào kho site settings.
- (spec-writer) Không chèn JS để ép viewport desktop; ghi nhận là giới hạn đã biết, quyết định lại sau khi thử trên thiết bị thật.
- (spec-writer) Tab ẩn danh bật/tắt desktop bằng ngoại lệ tạm trong session, không ghi policy.

## Quyết định (orchestrator, 2026-11-10)
- Persistence: Room 2.8.5 + KSP 2.3.13 (đã xác nhận có trên Maven; Kotlin 2.4.21/AGP 9.5.0) với spike bắt buộc ở 012 AC1; nếu không xanh sau một lượt cập nhật phiên bản thì fallback SQLiteOpenHelper (AC viết trên interface nên không đổi). Mỗi miền một file DB (`history.db`, `bookmarks.db`).
- Chấp nhận các đề xuất của spec-writer: gốc bookmark "Dấu trang" + thư mục "Đã nhập <ngày>"; thứ tự gợi ý Search/GoTo → Bookmark → History (≤ 8); desktop site không ép bỏ meta viewport (giới hạn đã biết, thử trên máy thật); ngoại lệ desktop lưu JSON file đọc đồng bộ; 015 có thể tách 2 PR (find / desktop); tùy chọn tắt lịch sử thuộc B12/B14.
- Thứ tự triển khai: 012 → 014 → 013 → 015 (rebase giữa các PR vì cùng sửa BrowserMenu/BrowserActions).

## Quyết định (implementer, 2026-11-10)
- `imePadding` không áp riêng trong `FindBar`: `BrowserBottomBar` đã áp `WindowInsets.ime` (adjustResize + edge-to-edge) và `FindBar` nằm ngay trên nó nên tự nổi trên bàn phím; cần kiểm thủ công API 31/35+.
- Điều hướng main-frame chỉ đổi chế độ desktop khi URL là http(s) (`isValidWebUrl`); `javascript:`/`data:`/`blob:` không đổi UA (áp dụng cho `WebViewEngineSession` và `FakeEngineSession`).
- `findNext` ở `WebViewEngineSession` là no-op khi `total == 0`; `restoreState` không đổi chế độ trực tiếp, chế độ đúng được áp ở `onPageStarted` (reload tối đa một lần/URL, cờ `modeReloadedFor`).
- Điều hướng lịch sử đổi chế độ dùng `LOAD_NO_CACHE` cho lần reload đó (cache theo UA cũ không dùng lại).
- Trang cài đặt hiển thị số ngoại lệ bằng `plurals` (en: one/other, vi: other).

## Quyết định (implementer, vòng review)
- Ngoại lệ tạm của session (`tempOverrides`) chỉ dùng cho tab ẩn danh, tab không có policy hoặc URL không có `siteKey`: `setDesktopSite(enabled, rememberInSession = false)` được VM dùng cho tab thường có policy (policy là nguồn duy nhất, "Xóa ngoại lệ" có hiệu lực ở điều hướng kế tiếp).
- `onReload` và `onToggleDesktopSite` đóng thanh tìm. `validateFind` không còn đóng chỉ vì URL khác (redirect/pushState); thanh đóng khi engine đã đặt lại kết quả của một truy vấn không rỗng (điều hướng/reload), hoặc đổi tab/home/fullscreen. Thanh mở với ô rỗng không bị đóng bởi điều hướng (không có gì để xóa).
- `FileDesktopSiteStore`: file > 256 KB coi như hỏng (`read()` null, không đọc/đổi tên khi ghi); `write` và `read` cùng lọc khóa bằng `isValidHost` (không có `/ \ ? # @ %`, ký tự điều khiển/khoảng trắng, ≤ 253).
- `siteKeyOf`: bỏ dấu chấm cuối trước khi bỏ `www.`; host chứa `%`/ký tự điều khiển → null. `DesktopSitePolicy.setDefault` không ghi khi giá trị không đổi. `loadUrl` chỉ áp chế độ desktop cho URL web.
- `imePadding` của `FindBar` dựa vào `BrowserBottomBar` (kiểm thủ công API 31/35+). Chưa có `FindInPageInstrumentedTest`/`DesktopSiteInstrumentedTest` (ghi ở FEATURES E05).
