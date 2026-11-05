# Spec 011: Theme Material 3 Expressive – dynamic color, sáng/tối, shape & motion (A13)
## Story
Pixel trông và chuyển động như một app Pixel hiện đại: màu lấy từ hình nền (dynamic color), người dùng chọn Sáng/Tối/Theo hệ thống và lựa chọn được nhớ; nút đổi hình khi nhấn, chuyển cảnh dùng lò xo, chỉ báo tải dạng hình biến đổi; thanh trạng thái/điều hướng luôn hợp màu với theme. Người bật "Xóa hiệu ứng động" trong Cài đặt thì không thấy animation.

## Phạm vi & module
- Độc lập với 008–010 (có thể làm song song trong worktree riêng; xung đột dự kiến chỉ ở `BrowserMenu` và `BrowserBottomBar` – rebase).
- Module mới `:core:ui` (Compose), package `app.pixel.browser.core.ui`: `PixelTheme`, `PixelColors`, `PixelShapes`, `PixelTypography`, `PixelMotion`, `LocalReducedMotion`, `PixelIconButton`, `PixelLoadingIndicator`, `ColorContrast`. `:feature:browser` (và `:feature:home` của spec 010) phụ thuộc `:core:ui`. Xóa `PixelBrowserTheme` trong `:app`.
- `:core:data` (tạo ở spec 010; nếu 011 vào trước thì 011 tạo module): `ThemeSettings`, `ThemeSettingsStore` + `DataStoreThemeSettingsStore`.
- `:app`: áp night mode, màu system bar, preload setting.

## Phiên bản thư viện & Expressive API
- `composeBom = 2026.09.00` resolve `androidx.compose.material3:material3:1.4.0`, compose ui/foundation/animation `1.12.1` (đã kiểm `compose-bom-2026.09.00.pom` trong Gradle cache).
- Ở material3 1.4.0 stable, các API Expressive (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `LoadingIndicator`, `ButtonDefaults.shapes()`/`IconButtonDefaults.shapes()`, `ButtonGroup`) **không chắc có mặt** – chúng được phát triển ở nhánh 1.5.0-alpha và đều cần `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Implementer kiểm bằng `javap`/IDE trên `material3-android-1.4.0` trước khi viết code.
- **Quyết định:** spec định nghĩa token và component của riêng Pixel trên API stable (foundation/animation + `androidx.graphics:graphics-shapes` cho hình biến đổi), nên AC không phụ thuộc phiên bản material3. Nếu orchestrator chọn nâng material3 (xem câu hỏi mở), `PixelTheme`/`PixelLoadingIndicator`/`PixelIconButton` được phép bọc API Expressive tương ứng (kèm `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`) miễn là AC dưới vẫn đúng.
- Dependency mới: `androidx.datastore:datastore-preferences` (stable), `androidx.graphics:graphics-shapes` (stable 1.x). Cả hai Apache-2.0, không có thành phần non-free (F-Droid OK).

## Quyết định thiết kế
- **Night mode qua `UiModeManager.setApplicationNightMode`** (API 31 = minSdk): hệ thống tự lưu và áp cho cửa sổ/splash ngay từ lúc khởi động (không nháy màu), `isSystemInDarkTheme()` phản ánh lựa chọn. Đổi chế độ gây configuration change → Activity tạo lại; WebView sống trong ViewModel (spec 005) nên không mất trang.
- **DataStore Preferences** cho `ThemeSettings` (giá trị vô hướng; B14 sẽ thêm các cài đặt khác vào cùng file `settings.preferences_pb`). Đọc trước trong `PixelBrowserApplication.onCreate` trên IO; trước khi đọc xong dùng `ThemeSettings()` mặc định.
- Màu nội dung web (`prefers-color-scheme`, algorithmic darkening) thuộc C04. Lưu ý: WebView với targetSdk ≥ 33 tự báo `prefers-color-scheme: dark` theo theme của app – chấp nhận như hành vi mặc định, C04 sẽ cho tùy chỉnh.

## Thay đổi API
### `:core:data`
```kotlin
enum class ThemeMode { System, Light, Dark }
data class ThemeSettings(val mode: ThemeMode = ThemeMode.System, val dynamicColor: Boolean = true)
interface ThemeSettingsStore { val settings: Flow<ThemeSettings>; suspend fun update(transform: (ThemeSettings) -> ThemeSettings) }
class DataStoreThemeSettingsStore(dataStore: DataStore<Preferences>) : ThemeSettingsStore   // khóa "theme_mode" (String), "dynamic_color" (Boolean)
```

### `:core:ui`
```kotlin
fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean
@Composable fun PixelTheme(settings: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit)
fun pixelColorScheme(context: Context, dark: Boolean, dynamicColor: Boolean): ColorScheme
object PixelColors { val light: ColorScheme; val dark: ColorScheme }       // scheme tĩnh sinh từ seed #3A5BA0 (Material Theme Builder)
val PixelShapes: Shapes                                                    // extraSmall 4, small 8, medium 16, large 24, extraLarge 32 (dp)
val PixelTypography: Typography                                            // Typography() mặc định M3, font hệ thống
object PixelMotion {
    fun <T> spatialFast(): SpringSpec<T>; fun <T> spatial(): SpringSpec<T>; fun <T> spatialSlow(): SpringSpec<T>
    fun <T> effectsFast(): SpringSpec<T>; fun <T> effects(): SpringSpec<T>; fun <T> effectsSlow(): SpringSpec<T>
}
val LocalReducedMotion: ProvidableCompositionLocal<Boolean>
@Composable fun <T> pixelSpring(spec: SpringSpec<T>): FiniteAnimationSpec<T>  // snap() nếu LocalReducedMotion
object ColorContrast { fun luminance(argb: Int): Double; fun ratio(a: Int, b: Int): Double }
object PixelSemantics { val CornerPercent: SemanticsPropertyKey<Int> }
@Composable fun PixelIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null, interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit)
@Composable fun PixelLoadingIndicator(modifier: Modifier = Modifier)        // vô định, có semantics ProgressBarRangeInfo.Indeterminate
fun interface NightModeController { fun apply(mode: ThemeMode) }
class UiModeNightModeController(context: Context) : NightModeController
```
- Token motion (theo M3 Expressive): spatial fast `(dampingRatio 0.6, stiffness 800)`, spatial default `(0.8, 380)`, spatial slow `(0.8, 200)`; effects fast `(1.0, 3800)`, default `(1.0, 1600)`, slow `(1.0, 800)`.
- `LocalReducedMotion` = `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`, đọc khi `PixelTheme` vào composition và khi `ON_RESUME`.
- Shape morph: `PixelIconButton` bo góc 50% (tròn) khi nghỉ, 30% khi nhấn, animate bằng `pixelSpring(PixelMotion.spatialFast())`; giá trị hiện tại công bố qua `PixelSemantics.CornerPercent` (làm tròn số nguyên).

### `:feature:browser`
- `ChromeButton` của `BrowserBottomBar` dùng `PixelIconButton` (giữ nguyên tag, semantics `disabled()`, custom action, long-press khi disabled – spec 005 AC14–AC23).
- `BrowserMenu` thêm `menu_theme` → `ThemeDialog` (tag `theme_dialog`, `theme_mode_system`, `theme_mode_light`, `theme_mode_dark`, `theme_dynamic`). `BrowserActions` thêm `onThemeChange: (ThemeSettings) -> Unit = {}`; `BrowserScreen` nhận thêm `themeSettings: ThemeSettings = ThemeSettings()`.

### `:app`
- `PixelBrowserApplication`: `themeStore` (DataStore `settings`), `themeSettings: StateFlow<ThemeSettings>` (bắt đầu `ThemeSettings()`, cập nhật từ store).
- `MainActivity`: `setContent { val s by app.themeSettings.collectAsState(); PixelTheme(s) { SystemBarsEffect(dark); BrowserRoute(...) } }`; `onThemeChange` → `store.update` + `nightModeController.apply(mode)` (chỉ khi mode đổi). `SystemBarsEffect`: `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark`, `isAppearanceLightNavigationBars = !dark`; `window.isNavigationBarContrastEnforced = false`.
- Manifest: theme cửa sổ giữ `Theme.DeviceDefault.DayNight` (đi theo night mode của app).

## Acceptance criteria
### Logic thuần (JVM)
1. **AC1 – `resolveDarkTheme`.** `(System, false) → false`, `(System, true) → true`, `(Light, true) → false`, `(Dark, false) → true`. (`ThemeResolutionTest`)
2. **AC2 – Token motion.** `PixelMotion.spatialFast<Float>()` có `dampingRatio == 0.6f`, `stiffness == 800f`; tương tự 5 token còn lại theo bảng ở trên. (`PixelMotionTest`)
3. **AC3 – Shapes & typography.** `PixelShapes.extraSmall == RoundedCornerShape(4.dp)`, … `extraLarge == RoundedCornerShape(32.dp)`; `PixelTypography == Typography()`. (`PixelShapesTest`)
4. **AC4 – Tương phản scheme tĩnh.** Với cả `PixelColors.light` và `.dark`: `ColorContrast.ratio(primary, onPrimary) ≥ 4.5`, `(surface, onSurface) ≥ 4.5`, `(primaryContainer, onPrimaryContainer) ≥ 4.5`, `(surfaceContainer, onSurface) ≥ 4.5`; `light.surface != dark.surface`. `ColorContrast.ratio(0xFFFFFFFF, 0xFF000000) == 21.0` (±0.01), `ratio(x, x) == 1.0`. (`PixelColorsTest`)

### Lưu cài đặt
5. **AC5 – Store.** (Robolectric/JVM với `PreferenceDataStoreFactory` trên file tạm + `TestScope`) Store mới → phát `ThemeSettings(System, true)`. `update { it.copy(mode = Dark) }` → phát `ThemeSettings(Dark, true)`; store mới cùng file đọc lại `Dark`. Giá trị `"theme_mode" = "Purple"` (lạ) → `System`. File hỏng → `CorruptionHandler` thay bằng mặc định, không ném. (`DataStoreThemeSettingsStoreTest`)

### Theme Compose (Robolectric)
6. **AC6 – Color scheme.** Trong `PixelTheme(ThemeSettings(mode = Light, dynamicColor = true))`, `MaterialTheme.colorScheme == dynamicLightColorScheme(context)`; `dynamicColor = false` → `== PixelColors.light`. `mode = Dark` → dark tương ứng. `mode = System` + `@Config(qualifiers = "night")` → dark; `notnight` → light. `MaterialTheme.shapes == PixelShapes`, `typography == PixelTypography`. (`PixelThemeTest`)
7. **AC7 – Reduced motion.** `Settings.Global.ANIMATOR_DURATION_SCALE = 0f` → trong `PixelTheme`, `LocalReducedMotion.current == true` và `pixelSpring(PixelMotion.spatial<Float>())` là `SnapSpec`; `= 1f` → `false` và trả đúng `SpringSpec` truyền vào. Đổi giá trị rồi phát `ON_RESUME` → cập nhật. (`ReducedMotionTest`)
8. **AC8 – Shape morph.** `PixelIconButton` (test tag `b`): khi nghỉ `CornerPercent == 50`. `mainClock.autoAdvance = false`; `performTouchInput { down(center) }` + `advanceTimeBy(500)` → `CornerPercent == 30`; giữa chừng (`advanceTimeBy(16)` đầu tiên) giá trị nằm trong khoảng `(30, 50)` (có animation). `up()` + 500ms → `50`, `onClick` gọi 1 lần. Reduced motion → nhảy thẳng `30`/`50` trong 1 frame. `enabled = false` → nhấn không đổi hình, `onClick` không gọi, `onLongClick` vẫn chạy (khớp spec 005). (`PixelIconButtonTest`)
9. **AC9 – Bottom bar hồi quy.** Toàn bộ `BrowserBottomBarTest`/`BrowserScreenTest` của spec 005 xanh sau khi `ChromeButton` chuyển sang `PixelIconButton`; mỗi `btn_*` có `CornerPercent` (chứng minh dùng component mới). (`BrowserBottomBarTest`)
10. **AC10 – Loading indicator.** `PixelLoadingIndicator(Modifier.testTag("l"))` có `ProgressBarRangeInfo.Indeterminate`, kích thước mặc định 48dp; với `autoAdvance = false` hai ảnh `captureToImage()` cách nhau 300ms khác nhau (hình biến đổi/xoay); reduced motion → hai ảnh giống nhau. (`PixelLoadingIndicatorTest`, `@GraphicsMode(NATIVE)`)

### Áp dụng ở app (Robolectric)
11. **AC11 – Hộp thoại theme.** Tap `btn_menu` → `menu_theme` → `theme_dialog` hiện; mục ứng với `themeSettings.mode` `assertIsSelected()`; `theme_dynamic` `assertIsOn()` khi `dynamicColor`. Tap `theme_mode_dark` → `onThemeChange(ThemeSettings(Dark, true))` đúng 1 lần, dialog đóng. Toggle `theme_dynamic` → `onThemeChange(copy(dynamicColor = false))`, dialog vẫn mở. Back → dialog đóng, không callback. (`ThemeDialogTest`)
12. **AC12 – Night mode.** `UiModeNightModeController(context, setNightMode: (Int) -> Unit = <UiModeManager::setApplicationNightMode>)`: `apply(Dark)` → `setNightMode(MODE_NIGHT_YES)`; `Light` → `MODE_NIGHT_NO`; `System` → `MODE_NIGHT_AUTO` (kiểm bằng lambda ghi lại). Constructor mặc định chạy dưới Robolectric không ném. `MainActivity`: chọn Dark trong dialog → store ghi `Dark` và controller nhận `apply(Dark)` đúng 1 lần; chỉ đổi `dynamicColor` → controller không được gọi. (`NightModeControllerTest`, `MainActivityThemeTest`)
13. **AC13 – System bars theo theme.** `MainActivity` với setting `Light` trong môi trường `night` → `isAppearanceLightStatusBars == true` và `isAppearanceLightNavigationBars == true`; setting `Dark` trong `notnight` → cả hai `false`; đổi `dynamicColor` không đổi giá trị này. `window.isNavigationBarContrastEnforced == false`. (Với night mode thật, Robolectric không tự đổi configuration → test cấp `themeSettings` trực tiếp cho `PixelTheme` qua `app.themeSettings`.) (`MainActivityThemeTest`)
14. **AC14 – Nạp trước khi vẽ.** Store có sẵn `ThemeSettings(Dark, false)`: `Robolectric.buildActivity(MainActivity).setup()` sau khi `app.themeSettings` đã nạp xong (test dispatcher `advanceUntilIdle`) : trước `setup()` `app.themeSettings.value == ThemeSettings(Dark, false)`; ngay sau `setup()` (chưa đẩy thêm frame) `isAppearanceLightStatusBars == false`. Store ném `IOException` khi đọc → `themeSettings` giữ mặc định, Activity khởi động bình thường. (`MainActivityThemeTest`)

## Edge cases
- Đổi Sáng/Tối → Activity tạo lại: menu/dialog đang mở đóng; tab, lịch sử, trang đang xem giữ nguyên (ViewModel); nếu đang sửa URL thì giữ text qua `rememberSaveable` (spec 005).
- Đổi `dynamicColor` không tạo lại Activity (chỉ recomposition).
- Đổi hình nền hệ thống khi app đang chạy → hệ thống gửi configuration change, `dynamic*ColorScheme` tự cập nhật.
- `mode = System` và người dùng đổi Dark theme hệ thống / lịch tự động → theo hệ thống ngay.
- Chế độ tương phản cao của hệ thống (Android 14+ contrast level): dynamic color đã phản ánh; scheme tĩnh không có biến thể tương phản (ghi nhận, làm ở B14 nếu cần).
- Thanh điều hướng 3 nút: `isNavigationBarContrastEnforced = false` có thể làm nút khó thấy trên một số ROM → bottom bar luôn có nền `surfaceContainer` phía sau inset nên vẫn đủ tương phản.
- Reduced motion cũng áp cho animation của 008 (indicator), 009 (shared element), 010 (kéo thả) khi các spec đó dùng `pixelSpring`.
- DataStore chỉ có một instance mỗi file trong process (`PixelBrowserApplication.onCreate` tạo đúng một instance qua `createSettingsDataStore`; không dùng delegate `preferencesDataStore` top-level – xem "Quyết định (implementer)"), nếu không DataStore ném `IllegalStateException`.

## Không test được bằng Robolectric (instrumented / thủ công)
- Màu dynamic thật theo hình nền, chuyển Sáng/Tối không nháy (splash/cửa sổ đúng màu ngay từ khi khởi động lạnh sau khi đổi). → thủ công emulator API 31 và 35+.
- Cảm giác lò xo, shape morph, loading indicator mượt 60/120Hz. → thủ công.
- `setApplicationNightMode` thật được hệ thống lưu qua khởi động lại thiết bị. → thủ công.
- Màu icon status/navigation bar thật trên nhiều ROM; gesture nav handle. → thủ công.

## Tests liên quan
- `:core:ui` JVM: `ThemeResolutionTest` (AC1), `PixelMotionTest` (AC2), `PixelShapesTest` (AC3), `PixelColorsTest` (AC4).
- `:core:ui` Robolectric: `PixelThemeTest` (AC6), `ReducedMotionTest` (AC7), `PixelIconButtonTest` (AC8), `PixelLoadingIndicatorTest` (AC10), `NightModeControllerTest` (AC12).
- `:core:data`: `DataStoreThemeSettingsStoreTest` (AC5).
- `:feature:browser` Robolectric: `BrowserBottomBarTest` (AC9), `ThemeDialogTest` (AC11).
- `:app` Robolectric: `MainActivityThemeTest` (AC12–AC14).

## Out of scope
- Dark mode cho nội dung web, ép tối trang (C04).
- Màn hình Cài đặt đầy đủ, chọn màu seed tùy ý, theme AMOLED đen tuyệt đối (B14).
- Font tùy chỉnh (Google Sans Flex/Roboto Flex) và biến thể "emphasized" của typography – dùng font hệ thống.
- Theme riêng cho tab ẩn danh (B06).
- Icon app động/themed icon (đã có qua adaptive icon nếu cần, ngoài spec này).

## Quyết định (orchestrator, 2026-11-05)
- Chấp nhận các đề xuất của spec-writer: giữ material3 theo BOM (1.4.0) với component tự viết; thêm `datastore-preferences` + `graphics-shapes`; shortcut mặc định Wikipedia/YouTube/GitHub/OpenStreetMap; chưa có Forward từ home; `undoClose` chỉ chọn lại tab nếu tab đó đang được chọn khi đóng (cập nhật spec 002 khi merge 009); pull-to-refresh có fallback `canScrollVertically(-1)` nếu `onOverScrolled` không được gọi.
- Thứ tự: 007 → 010 → 009 → 008; 011 song song (011 tạo `:core:data` nếu làm trước 010).

## Quyết định (implementer, 2026-11-06)
- `PixelIconButton` thêm tham số tùy chọn cuối `onLongClickLabel: String? = null` (ngoài chữ ký trong spec) để bottom bar giữ nhãn trợ năng long-press của spec 005. Nút có `Role.Button`, ripple khi enabled, bo góc qua `graphicsLayer` (animation không gây recompose), kích thước tối thiểu 48dp (`sizeIn`, không ép `size`) để `weight(1f)` của bottom bar vẫn áp dụng.
- AC8 theo test: kịch bản "down + 500ms, up → onClick 1 lần" dùng nút không có `onLongClick` (500ms vượt long-press timeout).
- `PixelLoadingIndicator`: `Morph` (graphics-shapes) giữa "cookie" 7 cánh và ngũ giác bo tròn + xoay liên tục, màu `colorScheme.primary`. Chưa thay `LinearProgressIndicator` trong top bar vì đó là tiến độ xác định (A05); indicator sẵn sàng cho 010/các màn chờ vô định.
- `PixelColors`: giá trị tonal-spot của Material Theme Builder cho seed #3A5BA0 (bản 2024, onPrimaryContainer tone 30 ở light).
- DataStore: không dùng delegate `preferencesDataStore` top-level (bị chia sẻ giữa các test Robolectric); `PixelBrowserApplication.onCreate` tạo đúng một DataStore (`createSettingsDataStore` + `preferencesDataStoreFile("settings")`) trên scope IO riêng, rồi `bindThemeStore(store, ioScope)`. `bindThemeStore` hủy lần gắn trước và chỉ lần gắn hiện tại được ghi `themeSettings`; `IOException` khi đọc bị nuốt (giữ giá trị hiện tại). `DataStoreThemeSettingsStore.settings` thử lại `IOException` tối đa 3 lần (trễ 100/200/300ms); vẫn lỗi thì phát mặc định nếu chưa nạp được gì, đã nạp thì giữ giá trị cũ. `nightModeController` là `lateinit`, khởi tạo trong `onCreate`.
- Đổi theme qua `PixelBrowserApplication.updateTheme(transform)` (review): đặt `themeSettings` ngay trên main (lạc quan), gọi `NightModeController` chỉ khi `mode` khác giá trị hiện tại, rồi ghi store trên `ioScope` (sống qua Activity tạo lại); `IOException` khi ghi chỉ ghi log. Trong lúc còn lần ghi chờ, giá trị store phát ra bị bỏ qua (tránh nháy giá trị cũ). `ThemeDialog` tính giá trị tiếp theo từ lựa chọn mới nhất trong dialog nên hai lần chạm nhanh không dùng giá trị cũ. `PixelIconButton` công bố `disabled()` khi `enabled = false`. System bars đặt bằng `SideEffect`; `isNavigationBarContrastEnforced = false` đặt ngay sau `enableEdgeToEdge()`.
- Chọn lại đúng chế độ đang chọn trong `ThemeDialog` chỉ đóng dialog, không gọi `onThemeChange`.
- Menu item Theme dùng icon "contrast" vẽ bằng `ImageVector` (material-icons-core không có palette/contrast), không thêm `material-icons-extended`.
