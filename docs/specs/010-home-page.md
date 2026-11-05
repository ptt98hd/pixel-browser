# Spec 010: Home page tùy chỉnh – ô tìm kiếm, lưới shortcut, ảnh nền (A10)
## Story
Mỗi tab mới và nút Home mở trang chủ native của Pixel (không tải trang của search engine): ô tìm kiếm ở giữa, lưới shortcut tới trang hay dùng mà người dùng thêm/sửa/xóa/kéo để sắp xếp, nền theo màu hệ thống, một màu tùy chọn hoặc ảnh chọn từ thư viện (có lớp làm tối để chữ dễ đọc). Trang chủ hiện ngay, không cần mạng.

## Phạm vi & module
- Implement **trước** 009 và 008 (hai spec đó dùng `Tab.isHome`/`BrowserUiState.isHome`).
- `:core:tabs`: biểu diễn "home" trong tab (thay đổi ngữ nghĩa `HOME_URL`).
- Module mới `:core:data` (Android library, không Compose), package `app.pixel.browser.core.data.home`: model + editor thuần JVM, `HomeConfigStore` + `FileHomeConfigStore`. (Spec 011 thêm `ThemeSettingsStore` vào cùng module.)
- Module mới `:feature:home` (Compose), package `app.pixel.browser.feature.home`: `HomePage` (stateless), `HomeViewModel`, `HomeRoute`, `BackgroundImageImporter` + `AndroidBackgroundImageImporter`. Phụ thuộc `:core:data`, `:core:omnibox` (chuẩn hóa URL, `UrlDisplay`); dùng `:core:ui` nếu spec 011 đã có.
- `:feature:browser` **không** phụ thuộc `:feature:home`: `BrowserScreen` nhận slot `homeContent`, `:app` nối `HomeRoute` vào slot.

## Quyết định thiết kế
- **Home không phải trang trong WebView.** `HOME_URL = "pixel://home"` là trạng thái của tab trong `TabManager`, UI vẽ `HomePage` thay cho `EngineContainer`; không bao giờ `loadUrl(HOME_URL)`. Session (và lịch sử) của tab giữ nguyên bên dưới khi người dùng bấm Home.
- **Điều hướng quanh home** (đơn giản hơn Chrome, không tạo vòng lặp Back):
  - Bấm Home khi đang ở trang P → home "đè lên" P: Back từ home → về P (không reload).
  - Từ home mở URL Q → tab nhớ `backToHome`: khi engine không còn lùi được (`canGoBack = false`), Back → về home; Back tiếp → hành vi mặc định (thoát).
  - Không có Forward từ home (khác Chrome; ghi nhận ở Edge cases).
- **Lưu cấu hình bằng file JSON** (`filesDir/home/config.json`, `org.json` + `AtomicFile`), không dùng DataStore: cấu hình là một tài liệu có danh sách có thứ tự (shortcut) + nền, cần version để migrate; Preferences DataStore không hợp với danh sách, Proto/JSON DataStore cần thêm plugin protobuf/kotlinx-serialization. Cách này khớp `FileTabSnapshotStore` (spec 002), không thêm dependency. Ảnh nền: `filesDir/home/bg-<epochMillis>.jpg`.
- **Icon shortcut là ô chữ cái** (chữ đầu của tiêu đề, màu nền suy từ hash host); favicon thật để B01/B02.

## Thay đổi API
### `:core:tabs`
```kotlin
const val HOME_URL = "pixel://home"                 // trước: "about:blank"
data class Tab(val id: String, val url: String, val title: String = "", val isPrivate: Boolean = false,
               val backToHome: Boolean = false) { val isHome: Boolean get() = url == HOME_URL }
data class TabSnapshot(/* id, url, title, engineState */ val backToHome: Boolean = false)

class TabManager {
    fun showHome(id: String)                        // home đè lên trang hiện tại; session.stopLoading(), không close
    fun loadUrl(id: String, url: String)            // rời home nếu đang ở home (đặt backToHome = true) rồi session.loadUrl
    fun canNavigateBack(id: String): Boolean
    fun navigateBack(id: String): Boolean           // false nếu không có gì để lùi (caller để hệ thống xử lý)
}
```
- `navigateBack` theo thứ tự: (1) đang ở home đè lên trang → rời home; (2) `session.canGoBack` → `goBack()`; (3) không ở home và `backToHome` → `showHome` dạng "không có trang phía sau" (Back tiếp theo trả `false`); (4) `false`.
- Khi ở home, `tabs` phát `url = HOME_URL`, `title = ""` (không đồng bộ từ engine). `snapshot()` ghi `url = HOME_URL`, vẫn ghi `engineState` nếu có trang bên dưới. `FileTabSnapshotStore` thêm khóa tùy chọn `backToHome` (thiếu → `false`).
- **Migration:** tab trong snapshot có `url == "about:blank"` và `engineState == null` (tab home kiểu cũ) → khôi phục thành tab home.

### `:core:data`
```kotlin
data class Shortcut(val id: String, val title: String, val url: String)
sealed interface HomeBackground {
    data object Dynamic : HomeBackground                       // màu surface của theme
    data class Solid(val argb: Int) : HomeBackground           // chỉ màu trong HomeDefaults.colors
    data class Image(val fileName: String, val dim: Float) : HomeBackground  // dim 0f..0.8f, mặc định 0.3f
}
data class HomeConfig(val version: Int = 1, val shortcuts: List<Shortcut>, val background: HomeBackground = HomeBackground.Dynamic)
object HomeDefaults { val shortcuts: List<Shortcut>; val colors: List<Int>; const val MAX_SHORTCUTS = 20 }
sealed interface ShortcutEdit { data class Ok(val config: HomeConfig) : ShortcutEdit; data object Invalid : ShortcutEdit; data object Duplicate : ShortcutEdit; data object Full : ShortcutEdit }
object HomeConfigEditor {
    fun add(c: HomeConfig, title: String, input: String, newId: () -> String): ShortcutEdit
    fun update(c: HomeConfig, id: String, title: String, input: String): ShortcutEdit
    fun remove(c: HomeConfig, id: String): HomeConfig
    fun move(c: HomeConfig, from: Int, to: Int): HomeConfig
    fun setBackground(c: HomeConfig, bg: HomeBackground): HomeConfig   // clamp dim
}
interface HomeConfigStore { fun read(): HomeConfig?; fun write(config: HomeConfig) }   // I/O: gọi trên IO
class FileHomeConfigStore(file: File) : HomeConfigStore
```
- Chuẩn hóa `input` của shortcut: `OmniboxClassifier.classify(input, SearchEngines.Default)` phải là `Navigate` với scheme http/https, ngược lại `Invalid`. `title` trim; rỗng → `UrlDisplay.host(url)`; cắt còn 40 ký tự. Trùng = cùng URL đã chuẩn hóa (so nguyên chuỗi).

### `:feature:home`
```kotlin
class HomeViewModel(store: HomeConfigStore, importer: BackgroundImageImporter, io: CoroutineDispatcher, newId: () -> String = { UUID… }) : ViewModel() {
    val config: StateFlow<HomeConfig?>              // null = đang đọc
    val message: StateFlow<HomeMessage?>
    fun addShortcut(title: String, url: String): ShortcutEdit
    fun updateShortcut(id: String, title: String, url: String): ShortcutEdit
    fun removeShortcut(id: String); fun moveShortcut(from: Int, to: Int)
    fun setBackground(bg: HomeBackground); fun setDim(dim: Float)
    fun onImagePicked(uri: String?)                 // null = người dùng hủy
    fun onMessageShown()
}
interface BackgroundImageImporter {
    suspend fun import(uri: String): String?        // copy + thu nhỏ vào filesDir/home; trả fileName hoặc null nếu lỗi
    fun exists(fileName: String): Boolean
    fun delete(fileName: String)
}
object HomeSemantics { val DimAlpha: SemanticsPropertyKey<Float> }   // để test đọc alpha lớp dim
@Composable fun HomePage(config: HomeConfig?, actions: HomeActions, modifier: Modifier = Modifier)
@Composable fun HomeRoute(viewModel: HomeViewModel, onOpenUrl: (String) -> Unit, onSearchClick: () -> Unit)
fun homeContentColor(argb: Int): Int               // đen/trắng theo độ tương phản WCAG cao hơn
```

### `:feature:browser`
- `BrowserUiState` thêm `isHome: Boolean`. Khi `isHome`: `url = ""`, `displayHost = ""`, `isLoading = false`, `progress = 0`, `securityState = Unknown`, `canGoBack = tabManager.canNavigateBack(id)`, `canGoForward = false`. Ngoài home: `canGoBack = tabManager.canNavigateBack(id)` (gồm `backToHome`).
- `BrowserViewModel`: **bỏ** `homeUrl` và `ensureSelectedLoaded`; `onHome()` → `showHome(selected)` (no-op nếu đang ở home); `onNewTab()` → `openTab(HOME_URL)`; `onBack()` → `navigateBack(selected)`; điều hướng (`onSubmit`, `onSuggestionClick`) đi qua `tabManager.loadUrl(selected, url)`; thêm `fun onOpenUrl(url: String)` (chỉ nhận `isValidWebUrl(url)`, ngược lại bỏ qua).
- `BrowserScreen(state, actions, modifier, engineContent, homeContent: @Composable () -> Unit = {})`: `isHome` → vẽ `homeContent` trong vùng nội dung, **không** compose `engineContent`.
- `BrowserActions` thêm `onOpenUrl: (String) -> Unit = {}`.
- Test tag (`HomeTestTags` trong `:feature:home`): `home_page`, `home_search`, `home_shortcut_<id>`, `home_shortcut_add`, `shortcut_menu_edit`, `shortcut_menu_remove`, `shortcut_dialog`, `shortcut_title`, `shortcut_url`, `shortcut_save`, `shortcut_cancel`, `shortcut_error`, `home_customize`, `home_customize_sheet`, `home_bg_dynamic`, `home_bg_color_<index>`, `home_bg_image`, `home_bg_dim`, `home_bg_image_layer`, `home_bg_dim_layer`.

### Migration test của spec 005 (sửa trong cùng PR)
- AC1 (`MainActivityTest`), AC16 (tap home), AC21/AC25/AC30 (tab mới): "tải `homeUrl`" → "tab ở home: `home_page` hiển thị, session **không** có `loadUrl`". Tất cả chỗ dùng `vm.homeUrl`/`f.homeUrl` trong `BrowserViewModelTest`, `BrowserScreenTest`, `TabSwitcherSheetTest`, `TestDoubles` được thay theo AC dưới. `TabSwitcherSheetTest.ac27_showsTitle_orHostWhenTitleEmpty` mong "google.com" cho tab home → đổi thành chuỗi "Tab mới" (spec 009 AC13).

## Acceptance criteria
### TabManager (JVM, `TabManagerHomeTest`; persistence Robolectric `TabPersistenceTest`)
1. **AC1 – Tab home.** Store rỗng → 1 tab, `isHome`, session không có `loadUrl` (`loadedUrls` rỗng, `state.url == ""`). `openTab(HOME_URL)` → tab mới `isHome`, không `loadUrl`. `HOME_URL == "pixel://home"`; `isValidWebUrl(HOME_URL) == false`.
2. **AC2 – Rời home bằng `loadUrl`.** Tab home H: `loadUrl(H, "https://a.com")` → `isHome == false`, `backToHome == true`, session `loadUrl("https://a.com")` đúng 1 lần. Tab thường T (mở bằng `openTab(url)`): `loadUrl(T, url2)` → `backToHome` giữ `false`. `loadUrl` với id không tồn tại → no-op.
3. **AC3 – Home đè lên trang.** T ở `https://a.com` rồi `https://b.com` (`canGoBack`), đang tải: `showHome(T)` → `tabs` phát T với `url == HOME_URL`, `title == ""`; session **không** `isClosed`, `isLoading == false` (đã `stopLoading`), lịch sử engine giữ (`canGoBack` vẫn true). `canNavigateBack(T) == true`; `navigateBack(T)` → `true`, `isHome == false`, URL là `https://b.com`, engine **không** nhận `goBack()`.
4. **AC4 – Chuỗi Back từ home.** H → `loadUrl(a)` → (finish) → `loadUrl(b)` → (finish): `navigateBack` → engine `goBack` (về a); `navigateBack` → `isHome == true` (do `backToHome`), engine không đổi; `canNavigateBack == false`; `navigateBack` → `false`. Tab thường ở trang đầu tiên, không `backToHome` → `canNavigateBack == false`.
5. **AC5 – Không vòng lặp.** T (có `backToHome`) ở trang a (`canGoBack = false`): `showHome(T)` → `navigateBack` → về a → `navigateBack` → home → `navigateBack` → `false`. Không có chuỗi Back nào lặp lại trạng thái quá 1 lần (test bằng vòng lặp tối đa 10 lần `navigateBack`, kết thúc bằng `false`).
6. **AC6 – Snapshot & migration.** Home đè lên trang có engine state → `snapshot()` ghi `url = HOME_URL`, `engineState != null`; khôi phục → tab `isHome`, chọn nó không `loadUrl`, `navigateBack` → trang cũ (restoreState). `backToHome = true` đi qua `FileTabSnapshotStore` round trip; file cũ không có khóa `backToHome` → `false`. Snapshot `{url: "about:blank", engineState: null}` → tab `isHome`; `{url: "about:blank", engineState: <hợp lệ>}` → không phải home, `restoreState` như cũ. Tab home bị discard rồi chọn lại → không `loadUrl`.

### `:core:data` (JVM thuần trừ khi ghi khác)
7. **AC7 – Shortcut mặc định.** `HomeDefaults.shortcuts` có đúng 4 mục theo thứ tự: Wikipedia `https://www.wikipedia.org/`, YouTube `https://www.youtube.com/`, GitHub `https://github.com/`, OpenStreetMap `https://www.openstreetmap.org/`; id khác nhau; mọi URL `isValidWebUrl`. `HomeDefaults.colors` có 8 màu, không trùng. (`HomeDefaultsTest`)
8. **AC8 – `add`.** `add(c, "", "example.com")` → `Ok`, shortcut cuối có `url == "https://example.com"`, `title == "example.com"`. `add(c, "  Ví dụ  ", "https://example.com/a")` → title `Ví dụ`. Input `hello world`, `javascript:alert(1)`, `file:///a`, `""` → `Invalid`. URL đã có → `Duplicate`. Khi đã có 20 → `Full`. Title 60 ký tự → cắt còn 40. (`HomeConfigEditorTest`)
9. **AC9 – `update`/`remove`/`move`.** `update` đổi title/url, giữ id và vị trí; trùng URL với shortcut **khác** → `Duplicate` (trùng chính nó thì `Ok`); id không tồn tại → `Invalid`. `remove` id không tồn tại → config không đổi. `move(c, 0, 2)` trên [A, B, C, D] → [B, C, A, D]; `move(c, 3, 0)` → [D, A, B, C]; index ngoài biên → clamp; `from == to` → không đổi. `setBackground(Image(f, 1.5f))` → dim `0.8f`; `-1f` → `0f`. (`HomeConfigEditorTest`)
10. **AC10 – Store.** (Robolectric) `FileHomeConfigStore` round trip giữ đủ thứ tự shortcut và cả 3 loại nền (kể cả `Solid` với alpha). File không tồn tại, JSON hỏng, `version` > 1, hoặc shortcut thiếu `url`/URL không hợp lệ → `read()` trả `null` cho file hỏng/version lạ; shortcut lỗi lẻ bị bỏ, phần còn lại giữ. Lỗi I/O khi ghi → giữ file cũ (`AtomicFile`). (`FileHomeConfigStoreTest`)

### HomeViewModel (JVM, `StandardTestDispatcher`, fake store/importer)
11. **AC11 – Nạp.** Trước khi `io` chạy, `config.value == null`; sau `advanceUntilIdle()`: store trả `null` → `HomeConfig(shortcuts = HomeDefaults.shortcuts)` và **chưa** ghi store (chỉ ghi khi người dùng sửa); store có dữ liệu → đúng dữ liệu đó. `Image` mà `importer.exists(fileName) == false` → nền `Dynamic`. (`HomeViewModelTest`)
12. **AC12 – Sửa & lưu.** `addShortcut`, `updateShortcut`, `removeShortcut`, `moveShortcut`, `setBackground`, `setDim` cập nhật `config` ngay (đồng bộ) và ghi store đúng 1 lần mỗi thao tác trên `io` (ghi tuần tự, lần ghi sau cùng thắng). Thao tác khi `config == null` → bỏ qua. (`HomeViewModelTest`)
13. **AC13 – Ảnh nền.** `onImagePicked("content://x/1")` → `importer.import` 1 lần; trả `"bg-1.jpg"` → nền `Image("bg-1.jpg", dim = 0.3f)` (giữ dim cũ nếu nền trước đã là ảnh), file ảnh cũ bị `importer.delete` sau khi ghi config mới; trả `null` → nền không đổi, `message == HomeMessage.ImageFailed`. `onImagePicked(null)` → không gọi importer, không message. Chọn `Dynamic`/`Solid` khi đang có ảnh → file ảnh bị xóa. (`HomeViewModelTest`)

### Importer (Robolectric, `@GraphicsMode(NATIVE)`)
14. **AC14 – Import ảnh.** PNG 4000×3000 (file tạm, `Uri.fromFile`) → file JPEG trong `filesDir/home/`, cạnh dài ≤ 2048 px, giữ tỉ lệ (±1 px); ảnh 800×600 giữ nguyên kích thước. URI không đọc được, file không phải ảnh, hoặc ảnh > 50 MB (theo `OpenableColumns.SIZE`/số byte đọc) → `null`, không để lại file rác. Tên file dạng `bg-<số>.jpg`, không lấy từ tên gốc. (`AndroidBackgroundImageImporterTest`)

### ViewModel trình duyệt (JVM, `BrowserViewModelHomeTest`)
15. **AC15 – Khởi động & tab mới.** ViewModel mới với store rỗng → `uiState.isHome`, `url == ""`, `canGoBack == false`, session không `loadUrl`. `onNewTab()` → tab mới `isHome`, không `loadUrl`.
16. **AC16 – Home/Back.** Ở trang P: `onHome()` → `isHome`, `canGoBack == true`; `onBack()` → `isHome == false`, `url == P`, engine không `goBack`. `onHome()` khi đã ở home → không đổi gì.
17. **AC17 – Điều hướng từ home.** Ở home: `onSubmit("example.com")` → rời home, `loadUrl("https://example.com")`; `onOpenUrl("https://a.com/")` → `loadUrl` đúng URL; `onOpenUrl("javascript:x")`, `onOpenUrl("pixel://home")` → bỏ qua. Sau khi trang đầu tải xong (`canGoBack = false`) → `uiState.canGoBack == true` (`backToHome`), `onBack()` → `isHome`.

### UI (Robolectric + Compose)
18. **AC18 – Slot home.** `BrowserScreen(state.copy(isHome = true), homeContent = { Box(Modifier.testTag("x")) })` → có `x`, **không** có `engine_container`; `isHome = false` → ngược lại. Với `BrowserRoute` + Fake: ở home, session của tab chọn `isPaused == true` (EngineContainer bị gỡ); rời home → resume. `url_display` ở home hiện placeholder (spec 005 AC5); `menu_share` disabled. (`BrowserScreenHomeTest`, `SessionLifecycleTest`)
19. **AC19 – Trang chủ.** `HomePage(config = HomeConfig(HomeDefaults.shortcuts))`: có `home_page`, `home_search`, 4 `home_shortcut_<id>` theo thứ tự (so `boundsInRoot`: `w411dp` → 4 cột; `w600dp` → 6 cột), `home_shortcut_add`. `config == null` → có `home_page`, **không** có shortcut và `home_shortcut_add` (không nháy shortcut mặc định). Đủ 20 shortcut → không có `home_shortcut_add`. Tap `home_shortcut_<id>` → `onOpenUrl(url)`. Tap `home_search` → `onSearchClick` (qua `:app`: `url_field` hiện và `assertIsFocused`). Mỗi shortcut có `contentDescription` = title. (`HomePageTest`, `MainActivityHomeTest`)
20. **AC20 – Thêm/sửa/xóa.** Tap `home_shortcut_add` → `shortcut_dialog`; nhập `shortcut_url = "example.com"` + `shortcut_save` → shortcut mới ở cuối, dialog đóng. Nhập `hello world` → `shortcut_error` hiện, dialog vẫn mở; trùng → `shortcut_error` (thông điệp khác). `shortcut_save` disabled khi URL rỗng. Long-press shortcut (không kéo) → `shortcut_menu_edit`, `shortcut_menu_remove`; Remove → shortcut biến mất; Edit → `shortcut_dialog` điền sẵn title/url, lưu → cập nhật tại chỗ. (`HomePageTest` + `HomeViewModel` thật với fake store)
21. **AC21 – Sắp xếp.** Long-press `home_shortcut_<A>` (giữ ≥ 500ms) rồi kéo tới tâm `home_shortcut_<C>` và thả → thứ tự [B, C, A, D] (AC9), menu ngữ cảnh không hiện; store được ghi. Mỗi shortcut có `CustomAccessibilityAction` "Di chuyển sang trái"/"Di chuyển sang phải" (không có ở biên) cho kết quả `move(i, i∓1)`. (`HomePageReorderTest`)
22. **AC22 – Nền.** `Dynamic` → nền `home_page` = `colorScheme.surface`, không có `home_bg_image_layer`. `Solid(c)` → nền màu `c`, chữ/icon dùng `homeContentColor(c)`. `Image(f, 0.4f)` → có `home_bg_image_layer` và `home_bg_dim_layer` có `HomeSemantics.DimAlpha == 0.4f`, chữ màu trắng. `homeContentColor` (JVM): trắng `#FFFFFF` → đen; `#000000` → trắng; `#1E88E5` → trắng; `#FFEB3B` → đen. (`HomeBackgroundTest`, `HomeContentColorTest`)
23. **AC23 – Tùy chỉnh nền.** Tap `home_customize` → `home_customize_sheet` có `home_bg_dynamic`, `home_bg_color_0..7`, `home_bg_image`; mục đang dùng `assertIsSelected()`. Tap `home_bg_color_2` → `setBackground(Solid(colors[2]))`. Tap `home_bg_image` → launcher `PickVisualMedia(ImageOnly)` được gọi (kiểm qua `TestActivityResultRegistry` cấp bằng `LocalActivityResultRegistryOwner`); registry trả URI → `onImagePicked(uri)`; trả `null` → `onImagePicked(null)`. `home_bg_dim` (slider 0..0.8) chỉ hiện khi nền là ảnh; kéo → `setDim`. (`HomeCustomizeTest`)

## Edge cases
- Bấm Home khi trang đang tải → `stopLoading`, trang bên dưới có thể dở dang; Back về thì không tự reload.
- Trang bên dưới tự điều hướng khi đang ở home (timer/redirect): WebView đã pause nên hiếm; nếu xảy ra, tab vẫn ở home (state engine bị bỏ qua khi `isHome`).
- Từ home → Q → Back về home: không có Forward về Q (khác Chrome).
- Home đè lên trang P rồi mở Q từ home: lịch sử engine P → Q; Back từ Q về P (không qua home) – khác Chrome, chấp nhận.
- Intent ngoài (spec 006 AC13) "tái dùng tab home chưa dùng" dùng `tabManager.loadUrl(id, url)`; tab đó có `backToHome = true` nhưng spec 006 AC14 (đóng tab, `moveTaskToBack`) được ưu tiên cho tab mở từ intent.
- `pixel://home` gõ vào omnibox → bị chặn như scheme lạ (spec 004); không có cách điều hướng WebView tới home.
- Ảnh nền bị xóa ngoài ý muốn (dọn dữ liệu) → fallback `Dynamic` (AC11), không crash.
- Ảnh HEIC/WebP/ảnh xoay EXIF: dùng `ImageDecoder` (tự áp EXIF); định dạng không hỗ trợ → lỗi AC14.
- Shortcut URL IDN → lưu Punycode (chuẩn hóa omnibox), hiển thị title.
- Nhiều tab cùng hiện home: chung một `HomeViewModel` (scope Activity) → sửa ở một tab thấy ngay ở tab khác.
- Xoay màn hình khi dialog shortcut mở: nội dung đang nhập giữ bằng `rememberSaveable`.
- Tab ẩn danh (B06) hiện cùng home; không lưu gì riêng.

## Không test được bằng Robolectric (instrumented / thủ công)
- Photo Picker hệ thống thật và quyền đọc URI trả về (persisted permission không cần vì ảnh được copy ngay). → thủ công.
- Kéo thả sắp xếp mượt, haptic khi bắt đầu kéo; hiển thị ảnh nền thật với lớp dim trên nhiều kích thước màn hình. → thủ công.
- Không có WebView nào được tạo/tải khi mở tab mới (đo bằng `adb shell dumpsys meminfo`/network log). → thủ công trước tag M1.

## Tests liên quan
- `:core:tabs`: `TabManagerHomeTest` (AC1–AC5, JVM), `TabPersistenceTest` (AC6, Robolectric); sửa `TabManagerTest` nếu dùng `HOME_URL = "about:blank"`.
- `:core:data`: `HomeDefaultsTest` (AC7), `HomeConfigEditorTest` (AC8, AC9) – JVM; `FileHomeConfigStoreTest` (AC10) – Robolectric.
- `:feature:home`: `HomeViewModelTest` (AC11–AC13, JVM); Robolectric: `AndroidBackgroundImageImporterTest` (AC14), `HomePageTest` (AC19, AC20), `HomePageReorderTest` (AC21), `HomeBackgroundTest` + `HomeContentColorTest` (AC22; phần màu JVM), `HomeCustomizeTest` (AC23).
- `:feature:browser`: `BrowserViewModelHomeTest` (AC15–AC17, JVM), `BrowserScreenHomeTest`, `SessionLifecycleTest` (AC18); migration các test spec 005 (mục trên).
- `:app`: `MainActivityHomeTest` (AC19 phần search → omnibox), `MainActivityTest` (AC1 spec 005 sửa).

## Out of scope
- Favicon thật cho shortcut, "trang hay truy cập" tự động, gợi ý từ lịch sử (B01/B02).
- Mục menu "Thêm trang này vào trang chủ" (có thể làm cùng B02).
- Tin tức/feed, thời tiết, đồng hồ; widget/shortcut launcher (B09 "add to home screen" là màn hình Android).
- Đặt một URL bất kỳ làm trang chủ thay cho home native (B14).
- Chọn màu tự do (color picker) – chỉ 8 màu dựng sẵn.
- Đồng bộ/sao lưu cấu hình home (app tắt backup).

## Quyết định (orchestrator, 2026-11-05)
- Chấp nhận các đề xuất của spec-writer: giữ material3 theo BOM (1.4.0) với component tự viết; thêm `datastore-preferences` + `graphics-shapes`; shortcut mặc định Wikipedia/YouTube/GitHub/OpenStreetMap; chưa có Forward từ home; `undoClose` chỉ chọn lại tab nếu tab đó đang được chọn khi đóng (cập nhật spec 002 khi merge 009); pull-to-refresh có fallback `canScrollVertically(-1)` nếu `onOverScrolled` không được gọi.
- Thứ tự: 007 → 010 → 009 → 008; 011 song song (011 tạo `:core:data` nếu làm trước 010).
