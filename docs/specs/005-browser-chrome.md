# Spec 005: Browser chrome – top bar, bottom bar, long-press, wiring màn hình (A05, A06, A07)
## Story
Người dùng mở app và thấy một trình duyệt dùng được: thanh URL ở trên hiện domain, ổ khóa và tiến độ tải; thanh dưới có 5 nút back / forward / home / tabs / menu, nhấn giữ từng nút cho thao tác nhanh. Nút Back hệ thống quay lại trang trước trước khi thoát app.

## Phạm vi & module
- Module mới `:feature:browser` (Android library + Compose Material 3), package `app.pixel.browser.feature.browser`. Phụ thuộc `:core:engine`, `:core:tabs` (spec 002), `:core:omnibox` (spec 004).
- `:app`: `MainActivity` gọi `setContent { MaterialTheme { BrowserRoute() } }`; theme M3 Expressive đầy đủ là A13.
- Thành phần (tên chốt để test-writer dùng):
  - `BrowserViewModel(tabManager: TabManager, searchEngine: SearchEngine = SearchEngines.Default)` – plain state holder (androidx `ViewModel`), expose `uiState: StateFlow<BrowserUiState>` và các hàm hành động (`onBack`, `onForward`, `onHome`, `onSubmit(input)`, `onScrollToTop`, `onScrollToBottom`, `onNewTab`, `onReload`, `onSelectTab(id)`, `onCloseTab(id)`, ...).
  - `BrowserUiState(url, displayHost, isLoading, progress, securityState, canGoBack, canGoForward, tabCount, tabs: List<TabItem>, selectedTabId, isEditingUrl, suggestions: List<Suggestion>, message: String?)`.
  - Composable stateless: `BrowserScreen(state, actions, engineContent)`, `BrowserTopBar`, `BrowserBottomBar`, `BrowserMenu`, `TabSwitcherSheet`. `BrowserRoute` nối ViewModel ↔ `BrowserScreen`.
  - `homeUrl` = `searchEngine.homeUrl` (spec 004 AC18) cho tới khi có home page A10.
- Test tag (hằng trong `BrowserTestTags`): `url_field`, `url_display`, `security_secure`, `security_insecure`, `progress`, `qr_button`, `btn_back`, `btn_forward`, `btn_home`, `btn_tabs`, `tab_count`, `btn_menu`, `menu_reload`, `menu_new_tab`, `menu_share`, `tab_sheet`, `tab_item_<id>`, `tab_close_<id>`, `tab_sheet_new`, `suggestion_<index>`, `engine_container`, `engine_placeholder`. Test dựa vào tag, không dựa vào chuỗi hiển thị (để đổi ngôn ngữ ở B14 không vỡ test); mọi icon vẫn phải có `contentDescription` từ string resource.

## Thay đổi API `:core:engine` (spec 001)
1. `fun scrollToTop()` / `fun scrollToBottom()` trên `EngineSession`. WebView: `webView.pageUp(true)` / `webView.pageDown(true)`. No-op sau `close()`. `FakeEngineSession` ghi lại lệnh vào `val scrollCommands: List<ScrollCommand>` (`enum class ScrollCommand { Top, Bottom }`).
2. `val view: android.view.View?` trên `EngineSession` – `WebViewEngineSession` trả `webView`, `FakeEngineSession` trả `null`, sau `close()` trả `null`.
3. `securityState` khi trang tải xong (cả Fake `simulatePageFinished` lẫn WebView `dispatchPageFinished`): `https://` → `Secure` nếu chưa có lỗi SSL trong lần tải này, `http://` → `Insecure`, URL khác (`about:blank`, rỗng) → `Unknown`; lỗi SSL luôn giữ `Insecure`. Hiện tại engine chưa bao giờ đặt `Secure`.
4. `EngineSessionContractTest` thêm case cho (1)–(3), chạy cho cả Fake lẫn WebView.

## Acceptance criteria
### Wiring
1. **AC1 – Khởi động.** Given app mở lần đầu (TabManager rỗng), When `MainActivity` khởi chạy, Then có 1 tab được chọn, nó tải `homeUrl`, `engine_container` hiển thị và `tab_count` = `1`. (`MainActivityTest`, Robolectric)
2. **AC2 – View của tab được chọn.** Given tab được chọn có `session.view != null`, Then view đó nằm trong cây view của `engine_container` qua `AndroidView`; Given `view == null` (Fake), Then hiện `engine_placeholder`. (`BrowserScreenTest`)
3. **AC3 – Đổi tab cập nhật chrome.** Given tab A (`https://a.com`, `canGoBack = true`) và tab B (`https://b.com`, `canGoBack = false`), A đang chọn, When chọn B, Then `url_display` hiện `b.com` và `btn_back` không enabled; session của A không bị `close()`. (`BrowserViewModelTest`, `BrowserScreenTest`)
4. **AC4 – Đổi view khi đổi tab không crash.** Given 2 tab dùng `WebViewEngineSession`, When đổi tab qua lại 3 lần, Then không ném `IllegalStateException` ("child already has a parent") – view cũ được gỡ khỏi parent trước khi gắn vào container mới. (`BrowserScreenWebViewTest`, Robolectric)

### Top bar (A05)
5. **AC5 – Hiện domain.** Given `state.url = "https://www.example.com/path?q=1"`, Then `url_display` có text `example.com` (`UrlDisplay.host`, spec 004 AC26). Given `url = "about:blank"` hoặc rỗng, Then hiện placeholder gợi ý (text từ resource, không rỗng).
6. **AC6 – Ổ khóa theo `SecurityState`.** `Secure` → tồn tại `security_secure`, không có `security_insecure`; `Insecure` → ngược lại; `Unknown` → không có cả hai. (`BrowserTopBarTest`)
7. **AC7 – Progress.** Given `isLoading = true, progress = 40`, Then `progress` hiển thị với `ProgressBarRangeInfo.current = 0.4`. Given `isLoading = false`, Then không có node `progress`. Cập nhật từ `FakeEngineSession.simulateProgress(70)` phản ánh 0.7 trên màn hình. (`BrowserTopBarTest`, `BrowserScreenTest`)
8. **AC8 – Nút QR ẩn.** Given build hiện tại (C06 chưa có), Then không tồn tại node `qr_button`. Composable nhận cờ `showQrButton: Boolean = false`; khi `true` thì nút hiện nhưng callback mặc định no-op (C06 sẽ nối). (`BrowserTopBarTest`)
9. **AC9 – Vào chế độ sửa.** When tap `url_display`, Then `url_field` xuất hiện, được focus, chứa `UrlDisplay.forEditing(url)` và toàn bộ text được chọn (`TextRange(0, length)`). (`BrowserTopBarTest`)
10. **AC10 – Submit.** Given đang sửa, When nhập `example.com` và IME action Go, Then `session.loadUrl("https://example.com")` được gọi đúng 1 lần và thoát chế độ sửa. Nhập `hello world` → `loadUrl` với URL search của engine (spec 004 AC12). (`BrowserScreenTest` với Fake: kiểm `fake.state.value.url`)
11. **AC11 – Submit bị chặn/rỗng.** Nhập `javascript:alert(1)` → không gọi `loadUrl`, `uiState.message` khác null (hiện snackbar), vẫn ở chế độ sửa. Nhập chuỗi rỗng → không gọi `loadUrl`, vẫn ở chế độ sửa, không có message. (`BrowserViewModelTest`)
12. **AC12 – Gợi ý.** Given đang sửa, When nhập `example.com`, Then hiện `suggestion_0` (GoTo) và `suggestion_1` (Search) theo spec 004 AC28; tap `suggestion_1` → `loadUrl` URL search tương ứng và thoát sửa. (`BrowserScreenTest`)
13. **AC13 – Hủy sửa.** Given đang sửa và đã gõ dở, When nhấn Back hệ thống, Then thoát sửa, `url_display` hiện lại domain hiện tại, không gọi `loadUrl`. (`BrowserBackHandlingTest`)

### Bottom bar (A06)
14. **AC14 – Đủ 5 nút đúng thứ tự.** Then có `btn_back`, `btn_forward`, `btn_home`, `btn_tabs`, `btn_menu`, xếp trái → phải theo thứ tự đó (so sánh `boundsInRoot.left`), mỗi nút có `contentDescription` không rỗng và kích thước chạm ≥ 48dp. (`BrowserBottomBarTest`)
15. **AC15 – Trạng thái enabled.** `canGoBack = false` → `btn_back` `assertIsNotEnabled`; `true` → `assertIsEnabled`; tương tự `btn_forward` với `canGoForward`. `btn_home`, `btn_tabs`, `btn_menu` luôn enabled. Trạng thái đi theo `FakeEngineSession`: sau A → B (`simulatePageFinished`) thì back enabled; sau `goBack` + finish thì forward enabled. (`BrowserBottomBarTest`, `BrowserScreenTest`)
16. **AC16 – Tap.** Tap `btn_back` (enabled) → `goBack()`; tap `btn_forward` → `goForward()`; tap `btn_home` → `loadUrl(homeUrl)` trên tab hiện tại; tap `btn_tabs` → mở `tab_sheet`; tap `btn_menu` → hiện `menu_reload`, `menu_new_tab`, `menu_share`. Tap `btn_back` khi disabled → không gọi `goBack()`. (`BrowserScreenTest`)
17. **AC17 – Badge số tab.** `tab_count` hiện số tab: 1 → `1`, 12 → `12`, 99 → `99`, 100 → `99+`. Đổi theo `TabManager` (mở thêm tab → tăng ngay). `contentDescription` của `btn_tabs` chứa số tab. (`BrowserBottomBarTest`)

### Long-press (A07)
18. **AC18 – Back giữ → lên đầu.** `longClick` trên `btn_back` → `fake.scrollCommands == [Top]`, không gọi `goBack()`. Hoạt động cả khi `canGoBack = false` (nút chỉ disabled cho tap; long-press vẫn nhận – xem Edge cases).
19. **AC19 – Forward giữ → xuống cuối.** `longClick` trên `btn_forward` → `scrollCommands == [Bottom]`, không gọi `goForward()`, kể cả khi `canGoForward = false`.
20. **AC20 – Home giữ → focus URL.** `longClick` trên `btn_home` → `url_field` hiện và `assertIsFocused`; không gọi `loadUrl`.
21. **AC21 – Tabs giữ → tab mới.** `longClick` trên `btn_tabs` → `TabManager` có thêm 1 tab ở cuối, được chọn, tải `homeUrl`; `tab_count` tăng 1; `tab_sheet` không mở.
22. **AC22 – Menu giữ → refresh.** `longClick` trên `btn_menu` → `reload()` trên session hiện tại (`fake.state.value.isLoading == true`); menu không mở.
23. **AC23 – Accessibility cho long-press.** Mỗi nút có `CustomAccessibilityAction` tương ứng (label từ resource) và semantics `onLongClick`; thực thi action qua `performSemanticsAction`/custom action cho kết quả giống AC18–AC22. (`BrowserBottomBarTest`)

### Menu tối thiểu
24. **AC24 – Reload.** Tap `menu_reload` → `reload()`, menu đóng.
25. **AC25 – Tab mới.** Tap `menu_new_tab` → như AC21, menu đóng.
26. **AC26 – Share link.** Given `url = "https://example.com/a"`, `title = "A"`, When tap `menu_share`, Then activity kế tiếp được start (Robolectric `shadowOf(activity).nextStartedActivity`) là `Intent.ACTION_CHOOSER` bọc `ACTION_SEND` có `type = "text/plain"`, `EXTRA_TEXT = "https://example.com/a"`, `EXTRA_SUBJECT = "A"`. Given `url` rỗng/`about:blank`, Then `menu_share` `assertIsNotEnabled`. (`ShareLinkTest`)

### Tab switcher tối thiểu
27. **AC27 – Liệt kê.** Given 3 tab, When mở `tab_sheet`, Then có `tab_item_<id>` cho cả 3 theo thứ tự `TabManager`, mỗi mục hiện title (nếu rỗng thì `UrlDisplay.host(url)`), mục được chọn có semantics `selected = true`.
28. **AC28 – Chọn.** Tap `tab_item_<id>` → `TabManager` chọn tab đó, sheet đóng, chrome cập nhật theo AC3.
29. **AC29 – Đóng.** Tap `tab_close_<id>` → `TabManager.closeTab(id)`; mục biến mất, sheet vẫn mở; đóng tab cuối cùng → TabManager tạo tab home (spec 002), sheet hiện 1 mục.
30. **AC30 – Tab mới trong sheet.** Tap `tab_sheet_new` → như AC21, sheet đóng.

### Back hệ thống
31. **AC31 – Thứ tự ưu tiên.** Khi nhấn Back hệ thống (`onBackPressedDispatcher.onBackPressed()`): (a) `tab_sheet`/menu đang mở → đóng nó; (b) đang sửa URL → AC13; (c) `canGoBack = true` → `session.goBack()`, activity không finish; (d) ngược lại → hành vi mặc định (activity `isFinishing` hoặc chuyển nền), không gọi `goBack()`. Mỗi nhánh một test. (`BrowserBackHandlingTest`)

## Edge cases
- Nút back/forward disabled cho tap nhưng vẫn nhận long-press (cuộn đầu/cuối hữu ích ở trang đầu tiên): dùng semantics `disabled()` + `combinedClickable` có `onClick` no-op khi không đi được, hoặc pointerInput riêng; test AC15 + AC18/AC19 cùng khẳng định.
- Nhấn liên tục (double tap) vào back: mỗi tap chỉ gọi `goBack()` 1 lần, không crash khi lịch sử hết giữa chừng.
- Long-press không kích hoạt luôn tap (không gọi `goBack()` sau khi thả tay).
- `progress` nhảy về 0 khi tải trang mới; tải lỗi (`state.error != null`) → ẩn progress, giữ domain.
- URL rất dài/IPv6/IDN hiển thị qua `UrlDisplay` (một dòng, ellipsis), không làm vỡ layout.
- Session của tab bị discard (spec 002) có `view == null` tạm thời → hiện `engine_placeholder` rồi chuyển sang WebView khi khôi phục.
- Xoay màn hình: ViewModel và WebView không bị tạo lại (WebView tạo bằng `MutableContextWrapper` của application, gắn base context sang Activity khi attach để tránh rò Activity). Persist khi process death thuộc spec 002.
- Menu "Share" khi đang tải: dùng `state.url` hiện tại (URL đang tải), không chặn.
- Badge số tab không tính tab ẩn danh riêng (B06 làm sau).

## Không test được bằng Robolectric (cần instrumented test / thủ công trên emulator)
- Cuộn thật của WebView khi `scrollToTop`/`scrollToBottom` (Robolectric shadow WebView không render/không cuộn); trang dùng scroll container nội bộ (không phải document) có thể không cuộn bằng `pageUp/pageDown` → ghi nhận, có thể cần fallback JS sau. → `BrowserScrollInstrumentedTest`.
- Bàn phím IME thật sự hiện khi long-press home / tap URL (Robolectric chỉ kiểm được focus). → `UrlFieldImeInstrumentedTest`.
- Haptic feedback khi long-press; animation hiện/ẩn progress và bottom sheet; hiển thị trang thật và ổ khóa với chứng chỉ thật/lỗi SSL thật (badssl.com) → kiểm thủ công trên emulator trước khi tag M1.
- Chooser chia sẻ hiển thị thực tế (Robolectric chỉ kiểm Intent).

## Tests liên quan
- `:core:engine`: `EngineSessionContractTest` (bổ sung scroll, `view`, securityState), `FakeEngineSessionContractTest`, `WebViewEngineSessionTest`.
- `:feature:browser` (JVM/Robolectric + Compose `createComposeRule`):
  - `BrowserViewModelTest` (JVM thuần, Fake + TabManager thật, Turbine) – AC3, AC10, AC11, AC21, AC22, AC28–AC30.
  - `BrowserTopBarTest` – AC5–AC9.
  - `BrowserBottomBarTest` – AC14, AC15, AC17, AC23.
  - `BrowserScreenTest` – AC2, AC3, AC7, AC10, AC12, AC16, AC18–AC22, AC24, AC25.
  - `BrowserScreenWebViewTest` – AC4.
  - `TabSwitcherSheetTest` – AC27–AC30.
  - `ShareLinkTest` – AC26.
  - `BrowserBackHandlingTest` – AC13, AC31.
- `:app`: `MainActivityTest` – AC1.
- Instrumented (`androidTest`, chạy khi có emulator/KVM): `BrowserScrollInstrumentedTest`, `UrlFieldImeInstrumentedTest`.

## Out of scope
- Quét QR (C06) – chỉ chừa chỗ nút, ẩn.
- Cử chỉ vuốt cạnh/bottom bar, pull-to-refresh (A08); tab switcher dạng lưới có thumbnail, vuốt để đóng (A09); home page tùy chỉnh (A10).
- Menu đầy đủ: lịch sử, bookmark, downloads, find in page, desktop site, cài đặt, copy link, add to home screen (B01–B15). Menu hiện chỉ có Reload, New tab, Share.
- Nút Stop khi đang tải (menu chỉ Reload ở spec này), ẩn/hiện thanh khi cuộn.
- Gợi ý lịch sử/bookmark trong dropdown (B01/B02 qua `SuggestionProvider`).
- Theme Expressive đầy đủ, dynamic color, motion (A13); Hilt DI (dùng factory thủ công trong spec này).
- Trang lỗi SSL/cảnh báo chứng chỉ có nút tiếp tục (B12).

## Quyết định (orchestrator, 2026-11-05)
- Long-press back/forward hoạt động cả khi nút disabled cho tap (custom gesture, không dùng `combinedClickable(enabled=false)`).
- Home URL tạm thời = trang chủ của search engine mặc định cho đến khi có A10.
- API `TabManager` lấy theo code thật của spec 002 (đã merge trước spec 005); test-writer đối chiếu với code, không theo văn bản spec 002.
