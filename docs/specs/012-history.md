# Spec 012: Lịch sử duyệt web – ghi, tìm, nhóm theo ngày, xóa, gợi ý omnibox (B01)
## Story
Mỗi trang web (tab thường) tải xong được ghi vào lịch sử trên máy. Người dùng mở "Lịch sử" từ menu để xem danh sách nhóm theo ngày (Hôm nay / Hôm qua / ngày), tìm theo chữ, bấm một mục để mở ngay ở tab hiện tại, bấm giữ để mở tab mới/sao chép/xóa, và xóa theo khoảng thời gian (giờ qua, 24 giờ, 7 ngày, tất cả). Khi gõ vào omnibox, các trang hay vào gần đây hiện trong gợi ý (xếp theo "frecency"). Tab ẩn danh không bao giờ ghi lịch sử. Mọi dữ liệu nằm trên máy, không gửi đi đâu.

## Phạm vi & module
- Module mới `:core:history` (Android library, không Compose), package `app.pixel.browser.core.history`: model, `HistoryStore` + `RoomHistoryStore` (hoặc `SqliteHistoryStore` nếu spike AC1 thất bại), `HistoryRecorder`, `Frecency`, `HistoryRange`, `groupByDay`, `HistorySuggestionProvider`, `InMemoryHistoryStore` (testFixtures). Phụ thuộc `:core:omnibox` (`SuggestionProvider`), không phụ thuộc `:core:engine`/`:core:tabs`.
- Module mới `:feature:history` (Compose), package `app.pixel.browser.feature.history`: `HistoryViewModel`, `HistoryScreen` (stateless), `HistoryRoute`, `HistoryTestTags`. Phụ thuộc `:core:history`, `:core:ui`, `:core:omnibox` (`UrlDisplay`). `:feature:browser` **không** phụ thuộc `:feature:history`.
- `:core:engine`: sự kiện `pageFinished` (mới) + cờ lỗi main-frame.
- `:core:omnibox`: thêm `SuggestionKind.History`, `SuggestionKind.Bookmark` (013 dùng `Bookmark`).
- `:feature:browser`: `BrowserViewModel` nhận `PageVisitRecorder`; menu thêm "Lịch sử"; `BrowserTopBar` hiển thị hai kind mới; thêm `BrowserRoute(overlayOpen)` (pause trang khi bị màn hình khác phủ).
- `:app`: `AppDestination` + điều hướng lớp phủ (dùng lại ở 013, 014), tạo database, `HistoryViewModel`.
- Thứ tự triển khai M2 đề xuất: **012 → 014 → 013 → 015** (012 dựng điều hướng cấp app và quyết định persistence; 013 dùng lại cả hai; 014/015 chỉ thêm mục menu/trang – rebase `BrowserMenu`/`BrowserActions`).

## Quyết định thiết kế
- **Persistence: Room + KSP, kèm spike bắt buộc** (AC1) và phương án dự phòng `SQLiteOpenHelper` thuần framework. Lý do chọn Room: truy vấn có điều kiện/nhóm/phân trang, `Flow` quan sát được, schema export và migration có kiểm chứng; dữ liệu lịch sử và bookmark là bảng quan hệ lớn (đến hàng chục nghìn dòng), không hợp với JSON file kiểu `home/config.json` (spec 010). Rủi ro: catalog đang dùng AGP 9.5.0 (Kotlin built-in) + Kotlin 2.4.21 và **chưa có** KSP/Room; KSP phải có bản hỗ trợ Kotlin 2.4.x và AGP 9 built-in Kotlin (implementer tra `symbol-processing-gradle-plugin` trên Maven Central/KSP release notes; Room 2.8.x trở lên; không dùng Room 3 alpha). Spec này không khẳng định một số phiên bản cụ thể vì chưa kiểm được trong môi trường này.
- **Phương án dự phòng** chỉ thay lớp cài đặt `HistoryStore`; mọi AC dưới giữ nguyên vì được viết trên interface (`HistoryStoreContractTest` chạy trên fake và trên cài đặt thật). Ngưỡng chuyển: spike không xanh sau một lượt thử cập nhật phiên bản KSP/Room (không quá nửa ngày) → chuyển sang `SQLiteOpenHelper`, ghi vào mục "Quyết định" của spec.
- **Mỗi miền một file DB** (`history.db` trong 012, `bookmarks.db` trong 013): xóa lịch sử (B12) không đụng bookmark, migration độc lập. Cấu hình Room/KSP dùng chung qua version catalog (`libs.versions.toml`: `ksp`, `room`), không dùng module storage chung.
- **Một dòng = một lượt truy cập** (`visits`), không có bảng URL riêng; tổng hợp theo URL (gợi ý, frecency) làm bằng truy vấn `GROUP BY url`. Dedupe liên tiếp: lượt mới cùng URL (bỏ fragment) với lượt vừa ghi của **cùng tab** → cập nhật `visitedAt`/title của dòng đó thay vì thêm dòng (reload, redirect lặp, SPA).
- **Ghi khi `pageFinished`, không phải khi `onPageStarted`**: trang lỗi/hủy giữa chừng không vào lịch sử. Engine phát sự kiện để ViewModel không phải đoán từ `EngineState`.
- **Frecency** (Firefox-style đơn giản, thuần hàm): `score = min(visitCount, 10) * weight(age(lastVisit))`, `weight`: ≤ 4 ngày 100; ≤ 14 ngày 70; ≤ 31 ngày 50; ≤ 90 ngày 30; còn lại 10.
- **Giới hạn lưu**: tối đa 20 000 dòng và 90 ngày; dọn khi khởi động app và sau mỗi 100 lần ghi.
- **Không có tùy chọn "tắt lịch sử" trong spec này** (thuộc B12/B14); tab ẩn danh (B06) là cách duy nhất không ghi.
- **Điều hướng cấp app**: `:app` giữ `AppDestination` (`Browser`, `History`, `Bookmarks`, `Settings` ở 013/014) trong `rememberSaveable`; các màn hình phụ là lớp phủ toàn màn hình **trên** `BrowserRoute` (vẫn nằm trong cây compose nên menu/tab switcher/URL đang sửa không mất; WebView được pause qua `overlayOpen`). Back hệ thống đóng lớp phủ trước khi tới `BrowserScreen`.

## Thay đổi API
### `:core:engine`
```kotlin
/** Trang main-frame đã tải xong. [isError]: lỗi mạng/SSL/HTTP >= 400 ở main frame trong lần tải này. */
data class PageFinished(val url: String, val title: String, val isError: Boolean)
interface EngineSession { /* ... */ val pageFinished: SharedFlow<PageFinished> }   // replay 0, không phát sau close()
```
- WebView: `dispatchPageFinished` phát sự kiện (title = `state.title` lúc đó); cờ `mainFrameErrorInLoad` đặt bởi `onReceivedError`/`onReceivedHttpError` (`request.isForMainFrame`, status ≥ 400) qua `dispatchMainFrameError()`, và bởi lỗi SSL; xóa ở `loadUrl`/`dispatchPageStarted`/`reload`/`goBack`/`goForward`. Fake: `simulatePageFinished` phát sự kiện; `simulateMainFrameError()`.

### `:core:history`
```kotlin
data class HistoryEntry(val id: Long, val url: String, val title: String, val visitedAt: Long)   // epoch ms
data class HistoryHit(val url: String, val title: String, val visitCount: Int, val lastVisitedAt: Long)
interface HistoryStore {
    suspend fun insert(url: String, title: String, visitedAt: Long): Long
    suspend fun touch(id: Long, title: String, visitedAt: Long): Boolean       // false nếu id không còn
    fun entries(query: String, limit: Int): Flow<List<HistoryEntry>>          // mới nhất trước; query rỗng = tất cả
    suspend fun suggest(terms: List<String>, candidateLimit: Int): List<HistoryHit>   // nhóm theo url, title của lượt mới nhất
    suspend fun delete(id: Long)
    suspend fun deleteBetween(fromInclusive: Long, toInclusive: Long): Int
    suspend fun prune(maxEntries: Int, olderThan: Long): Int
    suspend fun count(): Int
}
class RoomHistoryStore(db: HistoryDatabase) : HistoryStore
fun createHistoryDatabase(context: Context, name: String = "history.db"): HistoryDatabase

data class PageVisit(val tabId: String, val url: String, val title: String, val isPrivate: Boolean, val isError: Boolean)
interface PageVisitRecorder { fun record(visit: PageVisit); fun forgetTab(tabId: String); object None : PageVisitRecorder }
class HistoryRecorder(store: HistoryStore, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis) : PageVisitRecorder

enum class HistoryRange { LastHour, LastDay, LastWeek, All; fun from(now: Long): Long }   // All -> 0
class HistoryClearer(store: HistoryStore, clock: () -> Long) { suspend fun clear(range: HistoryRange): Int }
data class HistoryDay(val date: LocalDate, val entries: List<HistoryEntry>)
fun groupByDay(entries: List<HistoryEntry>, zone: ZoneId): List<HistoryDay>   // giữ thứ tự mới nhất trước
object Frecency { fun score(visitCount: Int, lastVisitedAt: Long, now: Long): Int }
class HistorySuggestionProvider(store: HistoryStore, clock: () -> Long, limit: Int = 4) : SuggestionProvider
object HistoryRetention { const val MAX_ENTRIES = 20_000; const val MAX_AGE_DAYS = 90; const val MAX_URL = 2048; const val MAX_TITLE = 200 }
```
### `:feature:history`
```kotlin
class HistoryViewModel(store: HistoryStore, clearer: HistoryClearer, ...) : ViewModel() {
    val state: StateFlow<HistoryUiState>        // query, days: List<HistoryDay>, canLoadMore, isEmpty
    fun onQueryChange(q: String); fun onLoadMore(); fun onDelete(id: Long); fun onClear(range: HistoryRange)
}
@Composable fun HistoryScreen(state: HistoryUiState, actions: HistoryActions, modifier: Modifier = Modifier)
@Composable fun HistoryRoute(viewModel: HistoryViewModel, onOpenUrl: (String) -> Unit, onOpenInNewTab: (String) -> Unit, onClose: () -> Unit)
```
- Test tag (`HistoryTestTags`): `history_screen`, `history_search`, `history_search_clear`, `history_day_<yyyy-MM-dd>`, `history_item_<id>`, `history_menu_open_new_tab`, `history_menu_copy`, `history_menu_delete`, `history_clear_button`, `history_clear_dialog`, `history_clear_range_<LastHour|LastDay|LastWeek|All>`, `history_clear_confirm`, `history_clear_cancel`, `history_empty`, `history_load_more`, `history_close`.

### `:core:omnibox`, `:feature:browser`, `:app`
- `enum class SuggestionKind { GoTo, Search, History, Bookmark }`; `BrowserTopBar` ánh xạ `History`/`Bookmark` → hiển thị `title` (nếu rỗng thì `url`), icon riêng; `when` vẫn exhaustive.
- `BrowserViewModel(..., pageVisits: PageVisitRecorder = PageVisitRecorder.None)`; `fun onOpenUrlInNewTab(url: String)` (chỉ `isValidWebUrl`; `captureCurrentThumbnail()` rồi `tabManager.openTab(url)`).
- `BrowserActions` thêm `onOpenHistory: () -> Unit = {}`; `BrowserMenu` thêm `menu_history`; `BrowserRoute(overlayOpen: Boolean = false)`: `overlayOpen` → `session?.onPause()` và `EngineContainer(paused = …)` như tab switcher.
- `:app`: `PixelBrowserApplication.historyStore` (lazy), `historyRecorder`, `suggestionProvider = SuggestionAggregator([DefaultSuggestionProvider, HistorySuggestionProvider])` (013 thêm bookmark), dọn lịch sử trên `ioScope` khi `onCreate` (`prune`).

## Acceptance criteria
### Persistence (spike, chạy đầu tiên)
1. **AC1 – Spike Room + KSP.** `./gradlew :core:history:testDebugUnitTest` xanh với module `:core:history` dùng Room + KSP (plugin và thư viện khai báo trong `libs.versions.toml`, schema export bật); `RoomHistorySpikeTest` (Robolectric, `Room.inMemoryDatabaseBuilder`) insert 1 dòng rồi `entries("", 10)` trả đúng dòng đó; `./gradlew spotlessCheck lintDebug assembleDebug` toàn dự án xanh; `assembleRelease` (R8) không cảnh báo Room. Nếu không đạt theo ngưỡng ở "Quyết định thiết kế" → cài đặt dự phòng `SqliteHistoryStore` (cùng interface), AC1 đổi thành "`SqliteHistorySpikeTest` insert + `entries` xanh", ghi quyết định.
2. **AC2 – Hợp đồng store.** `HistoryStoreContractTest` chạy trên `InMemoryHistoryStore` (JVM) và `RoomHistoryStore` (Robolectric, in-memory): (a) `entries("", n)` mới nhất trước, `limit` được tôn trọng; (b) `entries("goo")` khớp không phân biệt hoa thường trên url **hoặc** title, nhiều từ cách nhau bằng khoảng trắng phải khớp **tất cả** (AND); (c) `%`, `_`, `\` trong query là ký tự thường (`entries("100%")` chỉ khớp chuỗi chứa `100%`); (d) `delete(id)` chỉ xóa dòng đó và `entries` (Flow) phát lại danh sách mới; (e) `deleteBetween(a, b)` xóa theo khoảng **đóng** [a, b] và trả đúng số dòng; (f) `touch` đổi title + `visitedAt`, id không tồn tại → `false`; (g) `suggest(["wiki"], 100)` nhóm theo url: `visitCount` đúng, `title` của lượt mới nhất, `lastVisitedAt` đúng; (h) `prune(maxEntries = 2, olderThan = t)` xóa dòng cũ hơn `t` rồi xóa dòng cũ nhất cho tới khi còn ≤ 2, trả số dòng đã xóa; `count()` đúng.

### Ghi lịch sử
3. **AC3 – Chính sách ghi.** (`HistoryRecorderTest`, JVM, `InMemoryHistoryStore` + `TestScope`) `record` với `https://a.com/x`, title "A" → 1 dòng đúng url/title/`visitedAt = clock()`. **Không ghi** khi: `isPrivate`; `isError`; scheme khác http/https (`about:blank`, `pixel://home`, `file:`, `javascript:`, `data:`, `blob:`, `content:`); URL chứa userinfo (`https://u:p@a.com/`); URL dài hơn `MAX_URL` (2048). Title rỗng/khoảng trắng → host (`UrlDisplay.host`); title > 200 ký tự → cắt còn 200; fragment được giữ nguyên trong url đã ghi.
4. **AC4 – Dedupe liên tiếp.** Cùng `tabId`: ghi `https://a.com/p`, rồi `https://a.com/p#top` (cùng URL bỏ fragment) → vẫn 1 dòng, `visitedAt` và title là của lần sau. Ghi `a`, `b`, `a` → 3 dòng (không liên tiếp). Hai `tabId` khác nhau cùng ghi `a` → 2 dòng. `forgetTab(t)` rồi ghi lại cùng url ở tab `t` → dòng mới. `record` không chặn caller (ghi trên `scope`; trước `advanceUntilIdle()` chưa có dòng).
5. **AC5 – Ngoại lệ không làm sập.** `store.insert` ném `IOException`/`SQLiteException` → `record` không ném, các lần ghi sau vẫn hoạt động. Tab ẩn danh không làm thay đổi trạng thái dedupe của tab thường (record private trước, rồi thường cùng url → vẫn ghi).
6. **AC6 – Sự kiện engine.** (`EngineSessionContractTest`, cả Fake và WebView, Robolectric) `pageFinished` phát đúng một `PageFinished(url, title, isError = false)` cho mỗi `simulatePageFinished`/`dispatchPageFinished`; sau `simulateMainFrameError()`/`dispatchMainFrameError()` rồi finish → `isError = true`, và lần finish kế tiếp (sau `loadUrl`) lại `false`; `dispatchSslError` + finish → `isError = true`; sau `close()` không phát. Collector đăng ký muộn không nhận sự kiện cũ.
7. **AC7 – ViewModel nối ghi.** (`BrowserViewModelHistoryTest`, JVM, `FakePageVisitRecorder`) Tab thường: `simulatePageFinished("https://a.com")` + title → `record(PageVisit(tabId, url, title, isPrivate = false, isError = false))` đúng 1 lần với `tabId` của session phát sự kiện (kể cả tab nền). Tab `isPrivate` → `record` được gọi với `isPrivate = true` (recorder quyết định bỏ). Tab home không phát sự kiện. `onCloseTab(id)` và `onCloseAllTabs()` → `forgetTab` cho từng id. Session bị gỡ (discard) rồi dựng lại vẫn được ghi. Không `record` hai lần cho một `pageFinished` khi `recompute()` chạy lại.

### Truy vấn & tiện ích thuần
8. **AC8 – Nhóm theo ngày.** (`GroupByDayTest`) `groupByDay` với `ZoneId` cố định: 3 mục lúc 23:59, 00:01 (cùng ngày) và 23:50 ngày hôm trước → 2 nhóm, thứ tự ngày mới nhất trước, thứ tự mục trong nhóm giữ nguyên (mới nhất trước); danh sách rỗng → `emptyList()`; ranh giới ngày theo múi giờ truyền vào (cùng timestamp, hai zone khác nhau → hai kết quả khác nhau); DST không làm mục rơi sai ngày (`Europe/Berlin` ngày chuyển giờ).
9. **AC9 – Khoảng xóa.** (`HistoryClearerTest`) `now = 1_000_000_000L`: `LastHour.from(now) == now - 3_600_000`, `LastDay` = `now - 86_400_000`, `LastWeek` = `now - 7*86_400_000`, `All.from == 0`. Với mục ở `now - 1h` (đúng biên) và `now - 1h - 1ms`: `clear(LastHour)` xóa mục đầu, giữ mục sau; `clear(All)` xóa hết; trả số dòng đã xóa.
10. **AC10 – Giới hạn lưu.** (`HistoryRetentionTest`) Với `MAX_ENTRIES = 20_000` (test dùng hằng số có thể ghi đè nhỏ hơn): sau mỗi 100 lần ghi `HistoryRecorder` gọi `prune(MAX_ENTRIES, now - 90 ngày)` đúng một lần; dưới 100 lần ghi thì không gọi. `PixelBrowserApplication` gọi `prune` một lần ở `onCreate` trên `ioScope` (kiểm bằng store giả được tiêm).
11. **AC11 – Frecency.** (`FrecencyTest`) `score(1, now)` = 100; `score(3, now - 5 ngày)` = 210; `score(50, now - 100 ngày)` = 100 (giới hạn 10 lượt × 10); `score(1, now - 4 ngày)` = 100 (biên ≤ 4 ngày), `score(1, now - 4 ngày - 1ms)` = 70; `lastVisitedAt` trong tương lai (đồng hồ lệch) coi như `age = 0`; `visitCount <= 0` → 0.

### Gợi ý omnibox
12. **AC12 – `HistorySuggestionProvider`.** (JVM, `InMemoryHistoryStore`) Input trống/khoảng trắng → rỗng (không truy vấn store). `"wiki"` → các URL có `wiki` trong url hoặc title (bỏ `https://`, `www.` khi so khớp url), mỗi URL **một** gợi ý, `kind == SuggestionKind.History`, `title` = title mới nhất (rỗng → url), `url` = URL đầy đủ của lượt mới nhất. Xếp theo `Frecency.score` giảm dần, hòa thì `lastVisitedAt` mới hơn trước; tối đa `limit` (mặc định 4). Nhiều từ = AND. Input chứa `%`/`_` không làm khớp sai. Store ném lỗi → `SuggestionAggregator` bỏ qua provider (không crash, giữ gợi ý khác).
13. **AC13 – Tích hợp omnibox.** (`BrowserViewModelSuggestionTest`, `BrowserTopBarTest`) Với aggregator `[DefaultSuggestionProvider, HistorySuggestionProvider]`: gõ `wiki` → hàng 0 là `Search`/`GoTo` mặc định (hành vi spec 004 giữ nguyên), các hàng sau là `History`, tổng ≤ 8, bỏ trùng url với `GoTo`. `suggestion_<i>` của hàng `History` hiển thị title; tap → `loadUrl(url)` (tab chuyển khỏi home nếu cần). Tab ẩn danh vẫn thấy gợi ý từ lịch sử (đọc được, chỉ không ghi).

### Màn hình Lịch sử
14. **AC14 – ViewModel.** (`HistoryViewModelTest`, JVM, `StandardTestDispatcher`) Trước khi nạp xong: `state.isEmpty == false`, `days` rỗng, không có `canLoadMore`; sau nạp: `days` = `groupByDay(entries)`. `onQueryChange("wiki")` lọc theo `store.entries("wiki", …)`, bỏ qua khoảng trắng đầu/cuối; thay đổi liên tiếp trong 300 ms chỉ truy vấn lần cuối (debounce); `query` rỗng trả lại toàn bộ. Trang đầu 100 mục; `canLoadMore` khi số mục == giới hạn hiện tại, `onLoadMore()` tăng giới hạn thêm 100. `onDelete(id)` → `store.delete(id)` 1 lần và danh sách cập nhật. `onClear(range)` → `HistoryClearer.clear(range)` 1 lần. `isEmpty` đúng cho cả "chưa có lịch sử" và "không khớp tìm kiếm" (`state.query` khác rỗng để UI phân biệt).
15. **AC15 – Danh sách.** (`HistoryScreenTest`, Robolectric + Compose) Có `history_screen`; mỗi nhóm có `history_day_<yyyy-MM-dd>` (nhãn "Hôm nay"/"Hôm qua"/ngày theo locale); mục `history_item_<id>` theo thứ tự mới nhất trước, hiển thị title (rỗng → host) và host, một dòng, ellipsis; vùng chạm ≥ 48dp. `history_empty` khi không có mục (chuỗi khác nhau cho "chưa có lịch sử" và "không tìm thấy"). `history_load_more` chỉ hiện khi `canLoadMore`. Cuộn 1 000 mục không compose cùng lúc (`LazyColumn`; kiểm số node `history_item_*` hiển thị < 100).
16. **AC16 – Tìm kiếm.** Nhập `history_search` → `onQueryChange` đúng 1 lần mỗi lần đổi; `history_search_clear` chỉ hiện khi có chữ và xóa chữ; IME action = Search (ẩn bàn phím). Văn bản giữ qua xoay màn hình (`rememberSaveable`).
17. **AC17 – Mở & menu giữ.** Tap `history_item_<id>` → `onOpenUrl(url)` đúng 1 lần (qua `:app`: tab hiện tại tải URL, màn hình đóng, `url_display` hiện host). Long-press → menu có `history_menu_open_new_tab` (`onOpenInNewTab`, tab mới được chọn, màn hình đóng), `history_menu_copy` (clipboard có URL; kiểm bằng `ClipboardManager`), `history_menu_delete` (`onDelete(id)`, mục biến mất). Long-press không kích hoạt tap. Mỗi mục có `CustomAccessibilityAction` "Xóa" và "Mở trong tab mới".
18. **AC18 – Xóa theo khoảng.** Tap `history_clear_button` → `history_clear_dialog` với 4 lựa chọn `history_clear_range_*` (mặc định `LastHour`, `assertIsSelected()`); `history_clear_confirm` → `onClear(<range đã chọn>)` đúng 1 lần, dialog đóng; `history_clear_cancel`/Back → đóng, không callback. Nút `history_clear_button` bị disable khi lịch sử trống và không có query.
19. **AC19 – Điều hướng.** (`MainActivityHistoryTest`) Tap `btn_menu` → `menu_history` → `history_screen` phủ toàn màn hình (`boundsInRoot` bằng root); session của tab chọn `isPaused == true`; `history_close` hoặc Back hệ thống → đóng, session resume, tab/URL/menu không đổi; xoay màn hình khi đang mở → vẫn mở (`rememberSaveable`). `menu_history` luôn bật (kể cả ở home).
20. **AC20 – Ghi end-to-end.** (`MainActivityHistoryTest`, Fake engine, `HistoryRecorder` thật + `InMemoryHistoryStore`) Tab thường `simulatePageFinished` → mở Lịch sử → thấy mục; tab ẩn danh (`TabManager.openTab(url, isPrivate = true)`) finish → không thấy; trang lỗi (`simulateMainFrameError`) → không thấy.

### Hạ tầng
21. **AC21 – i18n & quyền.** Mọi chuỗi mới có ở `values` (en) và `values-vi`; `lintDebug` (đã `warningsAsErrors`, gồm `MissingTranslation`) xanh. `:core:history` và `:feature:history` không khai báo quyền `INTERNET` và không phụ thuộc thư viện mạng (kiểm bằng `ManifestPermissionTest` trên manifest hợp nhất của hai module: không có `uses-permission`).
22. **AC22 – Favicon (tùy chọn, làm sau AC1–AC21).** `FaviconStore` (`:core:data`, package `favicon`): `put(host, pngBytes)`, `get(host): ByteArray?`, `retainOnly`/`clear`; `FileFaviconStore` lưu `filesDir/favicons/<sha1(host)>.png`, tối đa 500 file/4 MB (LRU theo `lastModified`), ảnh decode được và ≤ 64×64 sau thu nhỏ, không ghi cho tab ẩn danh. Hàng lịch sử dùng favicon nếu có, ngược lại ô chữ cái (như spec 010). Tests: `FileFaviconStoreTest` (Robolectric, `@GraphicsMode(NATIVE)`). Nếu cắt khỏi PR đầu: ghi "chưa làm" vào FEATURES.md ghi chú B01.

## Edge cases
- URL trùng giữa hai tab cùng lúc: dedupe theo từng tab, nên hai dòng.
- Trang tải xong rồi title đổi muộn (SPA): lịch sử giữ title lúc `pageFinished`; hạn chế được chấp nhận (làm tốt hơn khi có sự kiện title riêng).
- Back/forward về trang cũ: engine phát `pageFinished` → ghi thêm lượt (như Chrome); không dedupe nếu không liên tiếp.
- Redirect: chỉ URL cuối được ghi (chỉ `pageFinished` cuối).
- Process death giữa lúc ghi: lượt đó mất (không ảnh hưởng dữ liệu cũ; Room/SQLite giao dịch).
- Đồng hồ bị đặt lùi: `visitedAt` lùi; `Frecency` coi tương lai là `age = 0`; thứ tự theo `visitedAt` rồi `id`.
- Query rất dài: cắt 256 ký tự trước khi truy vấn.
- Xóa lịch sử không xóa thumbnail tab, cookie, cache (B12).
- Dữ liệu nằm trong `databases/` của app; app đã tắt backup (`allowBackup=false`, spec 010) nên không rời máy.
- Tab restore (process death) tải lại trang qua `restoreState`: engine phát `pageFinished` → có thể ghi lượt mới; chấp nhận.

## Không test được bằng Robolectric (instrumented / thủ công)
- `WebViewEngineSession` thật phát `onReceivedError`/`onReceivedHttpError` đúng main-frame với trang lỗi thật (Robolectric không tải mạng). → `HistoryInstrumentedTest` (trang `androidTest/assets` + URL không tồn tại): trang lỗi không vào lịch sử, trang ok vào.
- KSP/Room trên CI thật (cache Gradle, R8 release APK mở được DB trên máy thật). → thủ công/emulator trước tag M2; `assembleRelease` + `apksigner verify` đã nằm trong gate.
- Hiệu năng truy vấn với 20 000 dòng (jank khi cuộn/tìm). → thủ công + Macrobenchmark (E05).
- Clipboard UI toast Android 13+ khi sao chép. → thủ công.

## Tests liên quan
- `:core:history` JVM: `HistoryRecorderTest` (AC3–AC5), `GroupByDayTest` (AC8), `HistoryClearerTest` (AC9), `HistoryRetentionTest` (AC10), `FrecencyTest` (AC11), `HistorySuggestionProviderTest` (AC12), `HistoryStoreContractTest` trên `InMemoryHistoryStore` (AC2).
- `:core:history` Robolectric: `RoomHistorySpikeTest` (AC1), `HistoryStoreContractTest` trên `RoomHistoryStore` (AC2).
- `:core:engine`: `EngineSessionContractTest` (AC6, Fake + WebView, Robolectric).
- `:core:data`: `FileFaviconStoreTest` (AC22).
- `:feature:browser`: `BrowserViewModelHistoryTest` (AC7), `BrowserViewModelSuggestionTest` + `BrowserTopBarTest` (AC13).
- `:feature:history`: `HistoryViewModelTest` (AC14, JVM); Robolectric: `HistoryScreenTest` (AC15–AC18), `ManifestPermissionTest` (AC21).
- `:app` Robolectric: `MainActivityHistoryTest` (AC19, AC20).
- Instrumented: `HistoryInstrumentedTest`.

## Out of scope
- Bật/tắt lịch sử, xóa lịch sử gộp cùng cookie/cache (B12 dùng lại `HistoryClearer`).
- Đồng bộ lịch sử giữa thiết bị; xuất/nhập lịch sử.
- Trang "hay truy cập" trên home (có thể làm sau bằng `HistoryStore.suggest`).
- Xóa nhiều mục bằng chọn nhiều (multi-select); chọn khoảng ngày tùy ý.
- Lịch sử theo tab (danh sách điều hướng back/forward) – đã có ở engine.
- Gợi ý tìm kiếm từ server của search engine (cần mạng; 004 không làm).

## Quyết định
- (spec-writer, 2026-11-10) Room + KSP có spike AC1, dự phòng `SQLiteOpenHelper`; chưa kiểm được phiên bản KSP/Room tương thích AGP 9.5.0 + Kotlin 2.4.21 ở thời điểm viết spec. Orchestrator xác nhận hoặc đổi sang SQLiteOpenHelper ngay nếu muốn tránh KSP.
- (spec-writer) Một DB riêng mỗi miền; điều hướng cấp app bằng lớp phủ trong `:app` (không thêm Navigation Compose).

## Quyết định (orchestrator, 2026-11-10)
- Persistence: Room 2.8.5 + KSP 2.3.13 (đã xác nhận có trên Maven; Kotlin 2.4.21/AGP 9.5.0) với spike bắt buộc ở 012 AC1; nếu không xanh sau một lượt cập nhật phiên bản thì fallback SQLiteOpenHelper (AC viết trên interface nên không đổi). Mỗi miền một file DB (`history.db`, `bookmarks.db`).
- Chấp nhận các đề xuất của spec-writer: gốc bookmark "Dấu trang" + thư mục "Đã nhập <ngày>"; thứ tự gợi ý Search/GoTo → Bookmark → History (≤ 8); desktop site không ép bỏ meta viewport (giới hạn đã biết, thử trên máy thật); ngoại lệ desktop lưu JSON file đọc đồng bộ; 015 có thể tách 2 PR (find / desktop); tùy chọn tắt lịch sử thuộc B12/B14.
- Thứ tự triển khai: 012 → 014 → 013 → 015 (rebase giữa các PR vì cùng sửa BrowserMenu/BrowserActions).
