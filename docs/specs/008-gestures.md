# Spec 008: Cử chỉ vuốt – back/forward cạnh màn hình, vuốt bottom bar đổi tab, pull-to-refresh (A08)
## Story
Người dùng duyệt web bằng một tay: vuốt từ cạnh màn hình để lùi (và tiến, khi điện thoại dùng 3 nút), thấy mũi tên báo trước khi thả tay; vuốt ngang trên thanh dưới để sang tab trước/sau; kéo trang xuống khi đang ở đầu trang để tải lại – giống Chrome, không xung đột với cử chỉ hệ thống.

## Phạm vi & module
- Implement **sau** spec 007 (`BrowserUiState.isFullscreen`, `pendingRequest`), 010 (`isHome`) và 009 (`TabThumbnailRepository.capture` khi rời tab). Nếu orchestrator đổi thứ tự, điều kiện tương ứng trong AC17 coi như `false` cho tới khi spec kia vào.
- `:core:engine`: tín hiệu cuộn/overscroll cho pull-to-refresh.
- `:feature:browser`, package `app.pixel.browser.feature.browser.gesture`: logic thuần JVM (`EdgeSwipeTracker`, `PullToRefreshTracker`, `TabSwipe`, `GestureGate`) + composable (`EdgeSwipeOverlay`, `BackGestureIndicator`, `PullRefreshIndicator`, modifier `tabSwipe`).

## Quyết định thiết kế
- **Back = cử chỉ hệ thống (predictive back).** Ở chế độ điều hướng cử chỉ, cả hai cạnh màn hình thuộc hệ thống; app không chặn (không dùng `systemGestureExclusionRects`). `BackHandler` của spec 005 AC31 đổi thành `PredictiveBackHandler`; khi nhánh (c) (đi lùi trong tab) đang áp dụng, app vẽ mũi tên `back_gesture_indicator` ở cạnh `BackEventCompat.swipeEdge`, to dần theo `progress`. Ngưỡng commit do hệ thống quyết định. Manifest: `android:enableOnBackInvokedCallback="true"`.
- **Forward bằng cạnh chỉ khi không có điều hướng cử chỉ** (3 nút/2 nút, phát hiện bằng `WindowInsets.systemGestures` trái/phải = 0): dải cảm ứng 20dp ở cạnh trái (lùi) và cạnh phải (tiến) phủ lên nội dung, **chỉ tồn tại khi** `canGoBack`/`canGoForward` tương ứng (để trang nhận chạm ở mép khi không có gì để đi). Ở chế độ cử chỉ, forward chỉ qua nút/menu.
- **Vuốt bottom bar = đổi tab kề**, không vòng (đầu/cuối danh sách thì không làm gì).
- **Pull-to-refresh dựa trên overscroll của root scroller**, không dựa riêng vào `scrollY == 0` (trang có scroller nội bộ sẽ cho `scrollY` luôn 0 → refresh nhầm). Chromium chỉ báo overscroll root khi mọi scroller con đã chạm đỉnh (scroll chaining), nên khớp hành vi Chrome.

## Thay đổi API
### `:core:engine`
```kotlin
data class EngineState(/* ... */ val isAtTop: Boolean = true)   // scrollY của root == 0; true khi bắt đầu tải trang mới
interface EngineSession {
    // ...
    /** Phát mỗi lần root scroller bị kéo quá đỉnh (overscroll dọc ở scrollY == 0). Không replay. */
    val topOverscroll: SharedFlow<Unit>
}
```
- `WebViewEngineSession`: `webView` là `PixelWebView : WebView` override `onScrollChanged` → `dispatchScrollChanged(scrollY)` và `onOverScrolled(scrollX, scrollY, clampedX, clampedY)` → `dispatchOverScrolled(scrollY, clampedY)` (phát `topOverscroll` khi `clampedY && scrollY == 0`). Cả hai `dispatch*` public như các điểm vào hiện có. `dispatchPageStarted`/`loadUrl`/`restoreState` đặt `isAtTop = true`. Kiểu công khai `val webView: WebView` giữ nguyên.
- `FakeEngineSession`: `simulateScroll(y: Int)` (cập nhật `isAtTop = y == 0`), `simulateOverscrollTop()`.

### `:feature:browser`
```kotlin
// BrowserViewModel thêm
fun onSelectAdjacentTab(delta: Int)          // -1 = tab trước, +1 = tab sau; no-op ngoài biên
// BrowserActions thêm: onSelectAdjacentTab: (Int) -> Unit = {}

class EdgeSwipeTracker(thresholdPx: Float, flingVelocityPx: Float) {
    fun onDrag(dxPx: Float)                  // dx tích lũy theo hướng vào trong màn hình (luôn ≥ 0 sau clamp)
    val progress: Float                      // dx / threshold, clamp 0..1
    val isArmed: Boolean                     // progress == 1
    fun onRelease(velocityPx: Float): Boolean // true = commit
    fun reset()
}
class PullToRefreshTracker(thresholdPx: Float, maxDistancePx: Float) {
    fun onDown(isAtTop: Boolean)
    fun onMove(dyPx: Float)                  // dy dương = kéo xuống
    fun onTopOverscroll()
    val distancePx: Float                    // khoảng hiển thị indicator (đã nhân hệ số cản 0.5, clamp maxDistance)
    val isActive: Boolean
    fun onUp(): Boolean                      // true = reload
    fun onCancel()
}
object TabSwipe { fun target(dxPx: Float, dyPx: Float, thresholdPx: Float, index: Int, count: Int): Int? }
data class GestureAvailability(val edgeBack: Boolean, val edgeForward: Boolean, val tabSwipe: Boolean, val pullToRefresh: Boolean)
object GestureGate { fun of(state: BrowserUiState, overlayOpen: Boolean, systemGestureNav: Boolean): GestureAvailability }
```
- Hằng (dp, đổi sang px ở UI): `EDGE_WIDTH = 20`, `EDGE_THRESHOLD = 96`, `EDGE_FLING_VELOCITY = 1000` dp/s, `TAB_SWIPE_THRESHOLD = 72`, `PULL_THRESHOLD = 80`, `PULL_MAX = 128`.
- Test tag mới (`BrowserTestTags`): `bottom_bar` (Surface của `BrowserBottomBar`), `edge_swipe_left`, `edge_swipe_right`, `edge_swipe_indicator`, `back_gesture_indicator`, `pull_refresh_indicator`.
- `BrowserScreen` thêm tham số `systemGestureNav: Boolean = <tính từ WindowInsets.systemGestures>`.

## Acceptance criteria
### Engine
1. **AC1 – `isAtTop`.** Session mới: `isAtTop == true`. Fake `simulateScroll(300)` / WebView `dispatchScrollChanged(300)` → `false`; về `0` → `true`. `loadUrl`, `reload`, `restoreState` và `dispatchPageStarted`/`simulatePageStarted` → `true`. `goBack`/`goForward` không tự đặt giá trị (WebView khôi phục vị trí cuộn cũ và báo qua `onScrollChanged`). Sau `close()` mọi lời gọi no-op. (`EngineSessionContractTest`, Fake JVM + WebView Robolectric)
2. **AC2 – `topOverscroll`.** `dispatchOverScrolled(scrollY = 0, clampedY = true)` → `topOverscroll` phát đúng 1 lần (Turbine); `(0, false)`, `(500, true)` → không phát. Fake `simulateOverscrollTop()` → phát 1 lần. Collector đăng ký sau sự kiện không nhận lại (không replay). (`EngineSessionContractTest`)

### Logic thuần (JVM)
3. **AC3 – `EdgeSwipeTracker`.** threshold 100px, fling 1000px/s: `onDrag(50)` → `progress == 0.5`, `isArmed == false`; `onDrag(80)` tích lũy 130 → `progress == 1`, `isArmed`; `onRelease(0)` → `true`. Kéo 120 rồi lùi về 60 → `onRelease(0) == false`. `dx = 40` + `onRelease(1500)` → `true` (fling khi dx ≥ threshold/3); `dx = 20` + `onRelease(5000)` → `false`. dx âm clamp 0. `reset()` → progress 0. (`EdgeSwipeTrackerTest`)
4. **AC4 – `PullToRefreshTracker`.** threshold 80, max 128: `onDown(isAtTop = true)`, `onMove(100)` khi chưa có overscroll → `isActive == false`, `distancePx == 0`; `onTopOverscroll()` rồi `onMove(200)` → `isActive`, `distancePx == 100` (200 × 0.5); `onUp() == true`. Kéo sau overscroll chỉ 120 → `distancePx == 60` → `onUp() == false`. `onMove(1000)` → `distancePx == 128`. `onDown(isAtTop = false)` → overscroll và kéo sau đó không kích hoạt. Kéo lên lại (dy âm) giảm `distancePx`, không xuống dưới 0. `onCancel()` → `isActive == false`, `onUp() == false`. Chỉ dy tích lũy **sau** `onTopOverscroll` được tính. (`PullToRefreshTrackerTest`)
5. **AC5 – `TabSwipe.target`.** threshold 72, `index = 1`, `count = 3`: `dx = -80, dy = 10` → `2` (vuốt sang trái = tab sau); `dx = +80` → `0`; `dx = -60` → `null`; `dx = -80, dy = 50` → `null` (cần `|dx| > 2|dy|`); `index = 2, dx = -80` → `null`; `index = 0, dx = +80` → `null`; `count = 1` → `null`. Ngôn ngữ RTL: UI đảo dấu `dx` trước khi gọi (AC12). (`TabSwipeTest`)
6. **AC6 – `GestureGate`.** Với state thường (`canGoBack = canGoForward = true`, `tabCount = 2`), `systemGestureNav = false` → cả 4 cờ `true`; `systemGestureNav = true` → `edgeBack = edgeForward = false`, hai cờ còn lại `true`. `canGoBack = false` → `edgeBack = false`; `canGoForward = false` → `edgeForward = false`; `tabCount = 1` → `tabSwipe = false`. Bất kỳ điều kiện nào sau → mọi cờ `false`: `isEditingUrl`, `isFullscreen`, `pendingRequest != null`, `overlayOpen` (menu/tab switcher/dialog). `isHome` → `pullToRefresh = false` (các cờ khác theo luật trên). (`GestureGateTest`)

### Back hệ thống & mũi tên (Robolectric + Compose)
7. **AC7 – Predictive back có indicator.** Given `canGoBack = true`, When `dispatcher.dispatchOnBackStarted(BackEventCompat(…, progress = 0f, swipeEdge = EDGE_LEFT))` rồi `dispatchOnBackProgressed(progress = 0.5f)`, Then `back_gesture_indicator` hiển thị, `boundsInRoot.left` < một nửa chiều rộng màn hình, chưa gọi `goBack()`. `swipeEdge = EDGE_RIGHT` → indicator ở nửa phải. Then `dispatchOnBackCancelled()` → indicator biến mất, `goBack()` không được gọi. Bắt đầu lại và `onBackPressed()` → `goBack()` đúng 1 lần, indicator biến mất. (`BackGestureTest`)
8. **AC8 – Thứ tự AC31 giữ nguyên.** Menu/tab switcher đang mở, hoặc đang sửa URL, hoặc fullscreen (spec 007 AC20): `dispatchOnBackStarted` **không** hiện `back_gesture_indicator`; commit thực hiện đúng nhánh tương ứng như spec 005 AC31/007 AC20. `canGoBack = false` và không nhánh nào áp dụng → handler disabled, activity xử lý mặc định. Mọi test hiện có của `BrowserBackHandlingTest` vẫn xanh. (`BrowserBackHandlingTest`, `BackGestureTest`)

### Vuốt cạnh khi không có điều hướng cử chỉ (Compose, Fake)
9. **AC9 – Dải cạnh chỉ có khi đi được.** `BrowserScreen(systemGestureNav = false)`: `canGoBack = true, canGoForward = false` → có `edge_swipe_left` (rộng 20dp, áp mép trái `engine_container`), không có `edge_swipe_right`; ngược lại tương tự. `systemGestureNav = true` → không có cả hai dù đi được. Tham số `systemGestureNav` mặc định tính từ `WindowInsets.systemGestures` (Robolectric mặc định = 0 → `false`). (`EdgeSwipeTest`)
10. **AC10 – Vuốt cạnh trái = back.** `performTouchInput` trên `edge_swipe_left`: `down` ở mép, `moveBy(x = 60dp)` → `edge_swipe_indicator` hiện, chưa `goBack`; tiếp `moveBy(60dp)` (tổng 120 ≥ 96) rồi `up` → `goBack()` đúng 1 lần, indicator biến mất. Kéo 60dp rồi thả chậm → không `goBack`. Vuốt nhanh (`swipeRight(durationMillis = 50)`) dài 40dp → `goBack` (fling AC3). (`EdgeSwipeTest`)
11. **AC11 – Vuốt cạnh phải = forward.** Đối xứng AC10 trên `edge_swipe_right`, kéo sang trái → `goForward()` đúng 1 lần. Vuốt dọc trong dải cạnh (dy lớn, dx nhỏ) → không gì xảy ra. (`EdgeSwipeTest`)

### Vuốt bottom bar đổi tab
12. **AC12 – Đổi tab kề.** Given tab [A, B, C], B chọn, When `performTouchInput { swipeLeft() }` trên `bottom_bar`, Then C được chọn; `swipeRight()` từ B → A. Ở A `swipeRight()` → vẫn A; ở C `swipeLeft()` → vẫn C. Trong layout RTL (`LocalLayoutDirection = Rtl`) chiều đảo ngược. Vuốt không kích hoạt tap/long-press của nút nằm dưới điểm bắt đầu (không `goBack`, không mở menu, không mở switcher). (`BottomBarSwipeTest`)
13. **AC13 – ViewModel.** `onSelectAdjacentTab(+1)` trên [A, B, C] chọn B → C; gọi `TabThumbnailRepository.capture` cho B trước khi chọn C (spec 009 AC4); thoát chế độ sửa URL. `+1` ở tab cuối và `-1` ở tab đầu → no-op, không capture. (`BrowserViewModelGestureTest`, JVM)
14. **AC14 – Accessibility.** `btn_tabs` có thêm 2 `CustomAccessibilityAction` "Tab trước"/"Tab sau" (label từ resource); action chỉ có mặt khi có tab tương ứng; thực thi → như AC12. (`BottomBarSwipeTest`)

### Pull-to-refresh
15. **AC15 – Kéo ở đầu trang.** Given tab Fake, `isAtTop = true`, When `performTouchInput { down(topCenter + 40dp); moveBy(0, 20dp) }` trên `engine_container`, `fake.simulateOverscrollTop()`, rồi `performTouchInput { moveBy(0, 200dp); up() }`, Then `pull_refresh_indicator` đã hiện trong lúc kéo và sau khi thả `reload()` được gọi đúng 1 lần (`fake.state.value.isLoading`), indicator hiển thị trạng thái đang tải (`PixelLoadingIndicator`, spec 011; trước 011: `CircularProgressIndicator`) cho tới khi `isLoading = false` thì biến mất. (`PullToRefreshTest`)
16. **AC16 – Không refresh nhầm.** (a) Không có `simulateOverscrollTop` trong cử chỉ → không reload, không indicator; (b) `simulateScroll(300)` trước `down` → overscroll sau đó bị bỏ qua; (c) kéo sau overscroll < 160dp (khoảng hiển thị < 80dp) → không reload, indicator thu về; (d) cử chỉ bị hủy (`cancel()`) → không reload. Sự kiện chạm vẫn tới engine (observer ở `PointerEventPass.Initial`, không consume) – kiểm bằng `pointerInput` con trong placeholder nhận đủ `down/move/up`. (`PullToRefreshTest`)
17. **AC17 – Tắt theo ngữ cảnh.** Khi `GestureGate` báo `false` (đang sửa URL, fullscreen, có dialog `pendingRequest`, menu/switcher mở, `isHome`), cử chỉ tương ứng AC10–AC12/AC15 không có hiệu lực: không node `edge_swipe_*`, vuốt bottom bar không đổi tab, kéo + overscroll không reload. (`GestureGateTest` cho bảng, `PullToRefreshTest`/`EdgeSwipeTest`/`BottomBarSwipeTest` mỗi file một case `isEditingUrl = true`)

## Edge cases
- Trang tự xử lý vuốt ngang (carousel, bản đồ) ở mép: chỉ dải 20dp bị chiếm khi đi được; ở chế độ cử chỉ thì hệ thống vốn chiếm mép.
- Trang dùng `overscroll-behavior: none/contain` trên root → Chromium không báo overscroll → không pull-to-refresh (giống Chrome).
- Trang chặn cuộn (`touch-action: none`, game canvas) → không overscroll → không refresh.
- Đang tải mà kéo lại → `reload()` khởi động lại tải (cho phép như Chrome).
- Đổi tab trong lúc kéo/vuốt dở (vd. intent mới) → tracker `reset()/onCancel()`, không commit.
- Xoay màn hình giữa cử chỉ → cử chỉ bị hủy (Compose hủy pointer input), không commit.
- Bàn phím đang mở (`isEditingUrl`) → bottom bar nằm trên IME; vuốt bị tắt để không xung đột với thao tác chọn chữ.
- Tab bị discard (`view == null`, placeholder) vẫn nhận vuốt bottom bar; pull-to-refresh không kích hoạt vì không có overscroll.
- Haptic: một nhịp `HapticFeedbackType.GestureThresholdActivate` (hoặc `LongPress` nếu API chưa có) khi `isArmed`/pull vượt ngưỡng lần đầu trong cử chỉ; không lặp lại khi dao động quanh ngưỡng.

## Không test được bằng Robolectric (instrumented / thủ công)
- Cử chỉ back hệ thống thật (gesture nav, cả hai cạnh), giá trị `swipeEdge`/`progress` thật, animation predictive back. → thủ công emulator API 34+ ở cả chế độ cử chỉ và 3 nút.
- `onOverScrolled`/`onScrollChanged` thật của Chromium WebView (Robolectric không cuộn/không render), scroll chaining với scroller nội bộ, `overscroll-behavior`. → `PullToRefreshInstrumentedTest` với trang HTML trong `androidTest/assets` (trang dài thường, trang có `div` overflow nội bộ, trang `overscroll-behavior: none`).
- Phát hiện chế độ điều hướng qua `WindowInsets.systemGestures` thật. → thủ công / instrumented.
- Haptic thật; cảm giác ngưỡng (96dp/72dp/80dp) – tinh chỉnh thủ công trước khi tag M1.

## Tests liên quan
- `:core:engine`: `EngineSessionContractTest` + AC1, AC2 (Fake JVM, WebView Robolectric).
- `:feature:browser` JVM: `EdgeSwipeTrackerTest` (AC3), `PullToRefreshTrackerTest` (AC4), `TabSwipeTest` (AC5), `GestureGateTest` (AC6, AC17), `BrowserViewModelGestureTest` (AC13).
- `:feature:browser` Robolectric + Compose: `BackGestureTest` (AC7, AC8), `BrowserBackHandlingTest` (AC8 hồi quy), `EdgeSwipeTest` (AC9–AC11, AC17), `BottomBarSwipeTest` (AC12, AC14, AC17), `PullToRefreshTest` (AC15–AC17).
- Instrumented: `PullToRefreshInstrumentedTest`.

## Out of scope
- Cử chỉ media (D01), vuốt để ẩn/hiện thanh khi cuộn, vuốt xuống trên top bar để mở switcher, vuốt lên bottom bar để mở switcher.
- Tùy chọn bật/tắt từng cử chỉ, chỉnh ngưỡng (B14).
- Pull-to-refresh trên home page (A10 không có nội dung để tải lại).
- Ảnh xem trước trang trước/sau khi vuốt cạnh (chỉ mũi tên).

## Quyết định (orchestrator, 2026-11-05)
- Chấp nhận các đề xuất của spec-writer: giữ material3 theo BOM (1.4.0) với component tự viết; thêm `datastore-preferences` + `graphics-shapes`; shortcut mặc định Wikipedia/YouTube/GitHub/OpenStreetMap; chưa có Forward từ home; `undoClose` chỉ chọn lại tab nếu tab đó đang được chọn khi đóng (cập nhật spec 002 khi merge 009); pull-to-refresh có fallback `canScrollVertically(-1)` nếu `onOverScrolled` không được gọi.
- Thứ tự: 007 → 010 → 009 → 008; 011 song song (011 tạo `:core:data` nếu làm trước 010).
