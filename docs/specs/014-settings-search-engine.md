# Spec 014: Màn hình Cài đặt (khung mở rộng được) + chọn công cụ tìm kiếm (B08, nền tảng cho B14)
## Story
Từ menu, người dùng mở "Cài đặt": một màn hình chia mục (Công cụ tìm kiếm, Giao diện, Quyền riêng tư, Giới thiệu). Ở "Công cụ tìm kiếm" chọn Google, DuckDuckGo, Brave, Bing hoặc nhập công cụ riêng bằng mẫu URL có `%s`; lựa chọn được nhớ và áp ngay cho omnibox, ô tìm kiếm trang chủ, tab mới và intent tìm kiếm từ app khác. "Giao diện" cho đổi Sáng/Tối/dynamic color (như hộp thoại theme ở spec 011). "Giới thiệu" hiện phiên bản, giấy phép GPL-3.0 và các thư viện bên thứ ba. Các spec sau (B07, B12, B14…) thêm trang cài đặt của mình mà không phải sửa màn hình Cài đặt.

## Phạm vi & module
- Module mới `:feature:settings` (Compose), package `app.pixel.browser.feature.settings`: `SettingsRegistry`, `SettingsSection`, `SettingsPage`, `SettingsScreen`, `SettingsRoute`, các trang `SearchEnginePage`, `AppearancePage`, `AboutPage`, `SettingsPlaceholders`, `Licenses`, `SettingsTestTags`. Phụ thuộc `:core:data`, `:core:omnibox`, `:core:ui`. `:feature:browser` **không** phụ thuộc `:feature:settings`.
- `:core:data`, package `app.pixel.browser.core.data.search`: `SearchSettings`, `SearchSettingsStore`, `DataStoreSearchSettingsStore`, `CustomEngineValidator`; đổi phụ thuộc `:core:omnibox` từ `implementation` sang `api` (kiểu `SearchEngine` xuất hiện trong API).
- `:core:omnibox`: `DefaultSuggestionProvider` nhận được nguồn engine động.
- `:feature:browser`: `BrowserViewModel` dùng engine động; menu thêm "Cài đặt".
- `:feature:home`: ô tìm kiếm hiện tên engine hiện tại.
- `:app`: `searchSettings` (cùng mẫu `themeSettings` của spec 011), `AppDestination.Settings` (điều hướng cấp app đã dựng ở spec 012), khai báo trang cài đặt (`SettingsPages.kt`), thông tin phiên bản.
- Phụ thuộc: làm **sau** 012 (dùng `AppDestination`/`BrowserRoute(overlayOpen)`); độc lập với 013, 015 (015 thêm một trang vào registry).

## Quyết định thiết kế
- **Lưu bằng DataStore Preferences, cùng file `settings.preferences_pb`** với theme (spec 011 đã dự kiến B14 thêm khóa vào file này): ba khóa vô hướng `search_engine_id` (String), `search_custom_name` (String), `search_custom_template` (String). Không dùng JSON file (khác home config): không có danh sách có thứ tự. Dùng lại đúng instance DataStore do `PixelBrowserApplication` tạo (DataStore cấm hai instance cùng file); `update` của mỗi store chỉ ghi khóa của nó.
- **Engine tùy chỉnh chỉ có một** (id `"custom"`), giữ lại khi người dùng chuyển sang engine dựng sẵn để chuyển lại không phải nhập lại. Engine hiệu lực = `SearchSettings.engine`: id lạ hoặc custom không hợp lệ → `SearchEngines.Default` (Google). Dữ liệu trong store không tin cậy: luôn kiểm bằng `CustomEngineValidator` khi đọc.
- **Mẫu URL tùy chỉnh** phải là `http`/`https` có host hợp lệ, **đúng một** `%s` và `%s` không nằm trong authority (đúng ràng buộc của `SearchEngine`); không có khoảng trắng/ký tự điều khiển; tối đa 2048 ký tự. Nhập thiếu scheme → tự thêm `https://`. `http://` được nhận nhưng hiện cảnh báo (truy vấn đi không mã hóa). Không bao giờ gọi mạng khi kiểm tra/xem trước (xem trước là chuỗi văn bản tính cục bộ: `searchUrl("pixel browser")`).
- **Khung mở rộng = registry dữ liệu, không phải navigation framework.** Mỗi spec thêm trang bằng một `SettingsPage` trong module của nó và đúng **một dòng** trong `:app/.../SettingsPages.kt`; không sửa `SettingsScreen`. ID trang/mục cố định để tránh xung đột; trang giữ chỗ ("Sắp có") bị trang thật cùng id thay thế tự động (không cần xóa dòng giữ chỗ).
- **Điều hướng trong Cài đặt** là một ngăn xếp id trang trong `rememberSaveable` của `SettingsRoute` (gốc = danh sách mục); Back lùi một cấp, ở gốc thì đóng lớp phủ. Không thêm Navigation Compose.
- **Engine đổi không ảnh hưởng tab đang mở**; chỉ áp cho lần nhập/tìm kế tiếp. Trang chủ native không tải trang của engine (spec 010) nên "tab mới" chỉ khác ở placeholder ô tìm kiếm.

## Thay đổi API
### `:core:data` (`...core.data.search`)
```kotlin
data class CustomSearchEngine(val name: String, val template: String)
data class SearchSettings(
    val selectedId: String = SearchEngines.Default.id,
    val custom: CustomSearchEngine? = null,
) { val engine: SearchEngine }   // id dựng sẵn → engine đó; "custom" + hợp lệ → SearchEngine("custom", name, template); còn lại → Default
object SearchEngineIds { const val CUSTOM = "custom" }
interface SearchSettingsStore { val settings: Flow<SearchSettings>; suspend fun update(transform: (SearchSettings) -> SearchSettings) }
class DataStoreSearchSettingsStore(dataStore: DataStore<Preferences>) : SearchSettingsStore   // retry IOException như DataStoreThemeSettingsStore
sealed interface CustomEngineResult {
    data class Ok(val engine: CustomSearchEngine) : CustomEngineResult       // name/template đã chuẩn hóa
    data object NameEmpty; NameTooLong; TemplateEmpty; NoPlaceholder; MultiplePlaceholders; InvalidUrl; TemplateTooLong
}
object CustomEngineValidator {
    const val MAX_NAME = 40; const val MAX_TEMPLATE = 2048
    fun validate(name: String, template: String): CustomEngineResult
    fun isInsecure(template: String): Boolean      // scheme http
}
```
- `FakeSearchSettingsStore` trong testFixtures của `:core:data`.

### `:core:omnibox`
```kotlin
class DefaultSuggestionProvider(private val engine: () -> SearchEngine) : SuggestionProvider {
    constructor(engine: SearchEngine) : this({ engine })       // giữ tương thích spec 004
}
```
### `:feature:browser`
- `BrowserViewModel(..., searchEngines: StateFlow<SearchEngine> = MutableStateFlow(SearchEngines.Default), suggestionProvider = DefaultSuggestionProvider { searchEngines.value }, ...)`; `val searchEngine: SearchEngine get() = searchEngines.value` (đọc mỗi lần dùng: `onSubmit`, intent, gợi ý). Test cũ truyền `searchEngine = X` đổi sang `searchEngines = MutableStateFlow(X)` (migration trong cùng PR).
- `BrowserActions.onOpenSettings`; `BrowserMenu` thêm `menu_settings` ("Cài đặt", icon bánh răng vẽ bằng `ImageVector` như icon theme), đặt cuối menu.

### `:feature:home`
- `HomeRoute(..., searchEngineName: String)`, `HomePage(..., searchEngineName: String = "")`: `home_search` có placeholder "Tìm kiếm với <tên>" (rỗng → "Tìm kiếm hoặc nhập địa chỉ") và `contentDescription` tương ứng.

### `:feature:settings`
```kotlin
data class SettingsSection(val id: String, val title: @Composable () -> String, val order: Int)
class SettingsPage(
    val id: String, val sectionId: String, val order: Int,
    val title: @Composable () -> String,
    val summary: (@Composable () -> String)? = null,         // dòng phụ ở danh sách (vd. tên engine hiện tại)
    val isPlaceholder: Boolean = false,                       // hàng bị vô hiệu "Sắp có", không mở được
    val content: @Composable (SettingsPageScope) -> Unit,
)
interface SettingsPageScope { fun navigateBack(); fun open(pageId: String); fun openUrl(url: String) }
object SettingsSections { const val SEARCH = "search"; APPEARANCE = "appearance"; PRIVACY = "privacy"; SITE = "site"; ABOUT = "about" }
object SettingsPageIds { SEARCH_ENGINE = "search_engine"; THEME = "theme"; ABOUT = "about"
    // giữ chỗ cho spec sau: SITE_SETTINGS = "site_settings" (B07), CLEAR_DATA = "clear_data" (B12), SAFE_BROWSING = "safe_browsing" (B12), ACCESSIBILITY = "accessibility" (B14), DESKTOP_SITE = "desktop_site" (015) }
class SettingsRegistry(sections: List<SettingsSection>, pages: List<SettingsPage>) {
    val groups: List<Pair<SettingsSection, List<SettingsPage>>>   // đã sắp xếp, đã áp quy tắc thay thế, bỏ mục rỗng
    fun page(id: String): SettingsPage?
}
@Composable fun SettingsScreen(registry: SettingsRegistry, onOpenPage: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)
@Composable fun SettingsRoute(registry: SettingsRegistry, startPageId: String? = null, onClose: () -> Unit, onOpenUrl: (String) -> Unit)
@Composable fun SearchEnginePage(settings: SearchSettings, onChange: ((SearchSettings) -> SearchSettings) -> Unit)
@Composable fun AppearancePage(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit)
data class AppInfo(val versionName: String, val versionCode: Long, val sourceUrl: String)
object Licenses { val all: List<LicenseEntry> }          // LicenseEntry(name, license (SPDX), url)
```
- Test tag (`SettingsTestTags`): `settings_screen`, `settings_title`, `settings_back`, `settings_section_<id>`, `settings_item_<id>`, `settings_placeholder_<id>`, `settings_page_<id>`, `search_engine_option_<google|duckduckgo|brave|bing|custom>`, `search_custom_name`, `search_custom_template`, `search_custom_preview`, `search_custom_error`, `search_custom_http_warning`, `search_custom_save`, `appearance_mode_<system|light|dark>`, `appearance_dynamic`, `about_version`, `about_license`, `about_source`, `about_licenses_list`, `about_license_<index>`.

### `:app`
- `PixelBrowserApplication`: `searchSettings: StateFlow<SearchSettings>` (bắt đầu `SearchSettings()`), `searchStore`, `updateSearch(transform)` — **cùng mẫu** `themeSettings`/`updateTheme`/`bindThemeStore` (cập nhật lạc quan trên main, ghi trên `ioScope`, bỏ qua giá trị store phát ra trong lúc còn ghi chờ, nuốt `IOException` khi đọc). Implementer được trích thành lớp dùng chung nếu giữ nguyên mọi test spec 011.
- `MainActivity`: truyền `searchEngines = app.searchEngine` (`StateFlow<SearchEngine>` map từ `searchSettings`) vào `BrowserViewModel`; `HomeRoute(searchEngineName = …)`; `AppDestination.Settings` → `SettingsRoute(registry = settingsRegistry, …)`; `onOpenUrl` → `viewModel.onOpenUrlInNewTab` rồi đóng Cài đặt.
- `SettingsPages.kt`: `fun settingsRegistry(app, info): SettingsRegistry` = danh sách phần + trang của 014 (Search engine, Theme, About) + trang giữ chỗ.

## Acceptance criteria
### Logic thuần (JVM)
1. **AC1 – Kiểm tra engine tùy chỉnh.** (`CustomEngineValidatorTest`) `validate("Kagi", "https://kagi.com/search?q=%s")` → `Ok` (name/template được trim). `validate("  ", t)` → `NameEmpty`; tên 41 ký tự → `NameTooLong` (40 → ok); template rỗng/khoảng trắng → `TemplateEmpty`; `https://a.com/` → `NoPlaceholder`; `https://a.com/?q=%s&r=%s` → `MultiplePlaceholders`; `https://%s.a.com/` (placeholder ở host), `https://u:p@a.com/?q=%s`, `ftp://a.com/?q=%s`, `javascript:%s`, `https://a.com/?q=%s x` (khoảng trắng), `https:///?q=%s`, ký tự điều khiển → `InvalidUrl`; template > 2048 → `TemplateTooLong`. `a.com/search?q=%s` (thiếu scheme) → `Ok` với template `https://a.com/search?q=%s`. `%s` trong path (`https://a.com/s/%s/`) hợp lệ. Template có `%20` hoặc `%` khác `%s` vẫn hợp lệ. `isInsecure("http://a.com/?q=%s") == true`, `https://` → `false`. Mọi `Ok` đều dựng được `SearchEngine` mà không ném.
2. **AC2 – Engine hiệu lực.** (`SearchSettingsTest`) `SearchSettings().engine == SearchEngines.Google`; mỗi id trong `SearchEngines.all` → engine đó; `selectedId = "custom"` + `custom` hợp lệ → `SearchEngine("custom", name, template)`; `"custom"` mà `custom == null` hoặc không hợp lệ (vd. thiếu `%s`) → `Google`; id lạ (`"yahoo"`) → `Google`; `custom` được giữ nguyên khi `selectedId` là engine dựng sẵn.
3. **AC3 – Store DataStore.** (`DataStoreSearchSettingsStoreTest`, Robolectric/JVM với `PreferenceDataStoreFactory` file tạm) Store mới phát `SearchSettings()`; `update { it.copy(selectedId = "duckduckgo") }` phát giá trị mới và store mới cùng file đọc lại đúng; lưu/đọc `custom` đủ name/template (kể cả tiếng Việt/emoji); khóa thiếu một nửa (`search_custom_name` có, `search_custom_template` không) → `custom == null`; file hỏng → `ReplaceFileCorruptionHandler` đặt mặc định, không ném; `IOException` khi đọc được thử lại tối đa 3 lần như store theme. **Cùng file với theme:** `update` của search không đổi `theme_mode`/`dynamic_color` và ngược lại (test ghi xen kẽ hai store trên một DataStore).
4. **AC4 – Fake store.** `FakeSearchSettingsStoreContractTest` chạy cùng bộ hợp đồng với `DataStoreSearchSettingsStore` (phát giá trị đầu, `update` tuần tự, phát lại khi đổi).

### Registry & khung (JVM, `SettingsRegistryTest`)
5. **AC5 – Sắp xếp & nhóm.** `groups` theo `order` tăng dần của phần, trong phần theo `order` rồi `id`; phần không có trang bị ẩn; mỗi trang xuất hiện đúng một lần.
6. **AC6 – Ràng buộc.** Hai trang thật cùng `id` → `IllegalArgumentException`; trang có `sectionId` không tồn tại → `IllegalArgumentException`; hai phần cùng `id` → ném. `page(id)` trả trang hoặc `null`.
7. **AC7 – Giữ chỗ bị thay thế.** Trang `isPlaceholder = true` id `clear_data` + trang thật id `clear_data` → `groups` chỉ chứa trang thật (kể cả khi trang thật ở phần khác: lấy vị trí/phần của trang thật); hai trang giữ chỗ cùng id → ném. Trang giữ chỗ không có trang thật thay thế vẫn hiện.
8. **AC8 – Danh sách mặc định của app.** (`:app` `SettingsPagesTest`, JVM/Robolectric) `settingsRegistry(...)` chứa theo thứ tự phần: `search` (trang `search_engine`), `appearance` (`theme`), `privacy` (giữ chỗ `clear_data`, `safe_browsing`), `site` (giữ chỗ `site_settings`, `desktop_site`), `about` (`about`); không ném; mọi `PageIds` là duy nhất.

### Áp dụng engine (JVM)
9. **AC9 – Provider động.** (`DefaultSuggestionProviderTest`) `DefaultSuggestionProvider { current }` với `current` đổi từ Google sang DuckDuckGo: lần `suggest("hello world")` sau trả `Suggestion(Search, …, "https://duckduckgo.com/?q=hello%20world")`; constructor `DefaultSuggestionProvider(SearchEngine)` cũ hoạt động như trước (spec 004 xanh).
10. **AC10 – ViewModel.** (`BrowserViewModelSearchEngineTest`) `searchEngines = MutableStateFlow(Google)`: `onSubmit("kotlin")` → `loadUrl("https://www.google.com/search?q=kotlin")`; đặt `value = DuckDuckGo` rồi `onSubmit("kotlin")` → `https://duckduckgo.com/?q=kotlin`; `viewModel.searchEngine` phản ánh ngay; gợi ý `Search` và `GoTo` (chuỗi URL gõ vào) dùng engine hiện tại; `onIncomingIntent(Search(...))` do `IncomingIntentResolver` dựng với engine hiện tại (kiểm ở `MainActivityIntentTest`). Đổi engine không tải lại/đổi tab đang mở. Engine tùy chỉnh `https://kagi.com/search?q=%s` + `onSubmit("a b&c")` → `https://kagi.com/search?q=a%20b%26c`.

### Màn hình Cài đặt (Robolectric + Compose)
11. **AC11 – Danh sách mục.** (`SettingsScreenTest`) `SettingsScreen` có `settings_screen`, `settings_title`, `settings_back`; với registry mặc định: `settings_section_search`, `…_appearance`, `…_privacy`, `…_site`, `…_about` theo thứ tự (so `boundsInRoot`), `settings_item_search_engine` (có dòng phụ = tên engine hiện tại), `settings_item_theme`, `settings_item_about`; trang giữ chỗ là `settings_placeholder_<id>` **disabled** (không có `onClick`, có `assertIsNotEnabled()`, chữ "Sắp có"). Tap `settings_item_<id>` → `onOpenPage(id)`; `settings_back` → `onClose`. Hàng cao ≥ 48dp, `Role.Button`, mỗi hàng đọc được "tiêu đề, dòng phụ" cho TalkBack (`mergeDescendants`).
12. **AC12 – Điều hướng trang.** (`SettingsRouteTest`) Mở route → danh sách; tap trang → `settings_page_<id>` hiện (danh sách ẩn), tiêu đề thanh trên = tên trang, `settings_back` lùi về danh sách; Back hệ thống ở trang → về danh sách, ở danh sách → `onClose` đúng 1 lần. `startPageId = "about"` mở thẳng trang đó, Back lần đầu về danh sách. `startPageId` lạ/giữ chỗ → danh sách. Trang gọi `scope.open(other)` chồng lên ngăn xếp, `navigateBack()` lùi một cấp. Ngăn xếp giữ qua xoay (`StateRestorationTester`).
13. **AC13 – Trang Công cụ tìm kiếm.** (`SearchEnginePageTest`) 4 `search_engine_option_*` + `search_engine_option_custom` theo thứ tự Google, DuckDuckGo, Brave, Bing, Tùy chỉnh; mục theo `settings.engine` `assertIsSelected()` (Role.RadioButton, `selectableGroup`). Tap `search_engine_option_brave` → `onChange` được gọi và áp transform cho ra `selectedId == "brave"`, `custom` giữ nguyên. Hiển thị tên + host của template (không hiện mẫu URL đầy đủ ở dòng chính).
14. **AC14 – Engine tùy chỉnh (UI).** Chọn `search_engine_option_custom` mở `search_custom_name` + `search_custom_template` (điền sẵn nếu đã có `custom`); `search_custom_save` disabled khi một ô rỗng. Nhập "Kagi" + `kagi.com/search?q=%s` → `search_custom_preview` hiện `https://kagi.com/search?q=pixel%20browser`; lưu → `onChange` cho ra `selectedId == "custom"` và `custom == CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")` (đã chuẩn hóa), `search_custom_error` không còn. Template sai → `search_custom_error` hiện chuỗi riêng cho từng loại lỗi (`NoPlaceholder`, `MultiplePlaceholders`, `InvalidUrl`, `NameTooLong`, `TemplateTooLong`), không gọi `onChange`, không đóng ô nhập. `http://` hợp lệ → lưu được **và** `search_custom_http_warning` hiện. Nội dung đang nhập giữ qua xoay (`rememberSaveable`); ô nhập dùng `KeyboardType.Uri`, không viết hoa, IME Done = lưu nếu hợp lệ. Hộp nhập là lớp phủ/trong trang (không `AlertDialog` — Compose test không idle với ô nhập trong cửa sổ phụ, xem spec 010).
15. **AC15 – Trang Giao diện.** (`AppearancePageTest`) `appearance_mode_system/light/dark` (radio, mục theo `settings.mode` được chọn) và `appearance_dynamic` (`Switch`, `assertIsOn()` theo `dynamicColor`). Tap `appearance_mode_dark` → `onChange(ThemeSettings(Dark, dynamicColor))` đúng 1 lần; bật/tắt `appearance_dynamic` → `onChange(copy(dynamicColor = …))`; chọn lại mục đang chọn → không callback. Cùng hành vi với `ThemeDialog` (spec 011 AC11); `:app` nối `onChange` vào `app.updateTheme` nên `NightModeController` chỉ chạy khi mode đổi.
16. **AC16 – Trang Giới thiệu.** (`AboutPageTest`) `about_version` hiện `versionName` và `versionCode` từ `AppInfo`; `about_license` hiện "GPL-3.0" (license của dự án, theo `CLAUDE.md`); `about_source` → `scope.openUrl(sourceUrl)`; `about_licenses_list` liệt kê `Licenses.all` (mỗi mục tên + SPDX, `about_license_<i>`), bấm mở `openUrl(url)`. `Licenses.all` (JVM `LicensesTest`) không rỗng, tên duy nhất, mọi `url` là `isValidWebUrl`, mọi `license` thuộc danh sách SPDX cho phép của dự án (`Apache-2.0`, `GPL-3.0`, `MIT`, `BSD-3-Clause`…) và gồm tối thiểu: AndroidX/Jetpack Compose, Kotlin/kotlinx.coroutines, AndroidX DataStore, AndroidX Graphics Shapes, (nếu đã chọn Room theo spec 012) AndroidX Room. `openUrl` trong `:app` → mở **tab mới** trong trình duyệt và đóng Cài đặt.
17. **AC17 – Nối ứng dụng.** (`PixelBrowserApplicationSearchTest`, `bindSearchStore` với Fake store + test dispatcher) Trước khi nạp xong `searchSettings.value == SearchSettings()`; store có `("duckduckgo")` → sau nạp `searchEngine.value == DuckDuckGo`; `updateSearch { it.copy(selectedId = "bing") }` đặt giá trị ngay (lạc quan) và ghi store đúng 1 lần; store ném `IOException` khi đọc → giữ mặc định, không crash; `IOException` khi ghi → chỉ log, giá trị bộ nhớ giữ nguyên.
18. **AC18 – Menu → Cài đặt.** (`MainActivitySettingsTest`) Tap `btn_menu` → `menu_settings` → `settings_screen` phủ toàn màn hình (`boundsInRoot` = root); session của tab chọn `isPaused`; `settings_back`/Back → đóng, session resume, tab/URL/menu không đổi; `menu_settings` luôn bật (kể cả ở home); xoay màn hình khi đang ở một trang con → vẫn ở trang đó.
19. **AC19 – End-to-end tìm kiếm.** (`MainActivitySettingsTest`, Fake engine) Settings → Công cụ tìm kiếm → chọn DuckDuckGo → Back về trình duyệt; ở home gõ `kotlin` vào omnibox + submit → session `loadUrl("https://duckduckgo.com/?q=kotlin")`; `home_search` có placeholder chứa "DuckDuckGo"; gợi ý `suggestion_0` là "Search for kotlin" với URL DuckDuckGo. Đặt engine tùy chỉnh → cùng luồng dùng mẫu đã lưu. Khởi động lại Activity (store giữ `duckduckgo`) → vẫn dùng DuckDuckGo.
20. **AC20 – i18n, quyền, riêng tư.** Mọi chuỗi mới (kể cả thông báo lỗi 7 loại, nhãn phần, "Sắp có") có ở `values` và `values-vi`, `lintDebug` xanh (`MissingTranslation`); `:feature:settings` không khai báo quyền `INTERNET`; không có code nào gọi mạng để kiểm tra/xem trước engine: `search_custom_preview` bằng đúng `searchUrl("pixel browser")` tính cục bộ (AC14), và `:feature:settings` không có quyền `INTERNET` nên không thể mở kết nối.

## Edge cases
- Engine tùy chỉnh bị hỏng trong DataStore (sửa tay/phiên bản cũ): `engine` về Google, trang cài đặt hiển thị lựa chọn đang áp dụng là Google nhưng vẫn điền sẵn dữ liệu custom hỏng để người dùng sửa.
- Người dùng chọn "Tùy chỉnh" nhưng chưa lưu rồi thoát: giữ engine trước đó.
- Template chứa `%s` trong fragment/query lẫn dấu `?` bị lặp: hợp lệ nếu thỏa AC1; truy vấn luôn được percent-encode (spec 004).
- Tên engine có ký tự RTL/emoji/rất dài: một dòng, ellipsis trong danh sách; giới hạn 40 ký tự.
- Đổi engine khi đang sửa URL trong omnibox (không thể: Cài đặt là lớp phủ; omnibox hủy sửa khi lớp phủ mở, Back trả về trạng thái cũ).
- Tab ẩn danh (B06) dùng cùng engine; không có engine riêng.
- Đổi ngôn ngữ/locale khi đang ở Cài đặt: `Activity` tạo lại, ngăn xếp trang giữ qua `rememberSaveable`.
- Hai spec cùng thêm trang vào `SettingsPages.kt`: xung đột hợp nhất chỉ ở một dòng danh sách; id trùng bị `SettingsRegistryTest`/AC8 bắt.
- Hộp thoại theme ở menu (spec 011) giữ nguyên song song với trang Giao diện (cùng `updateTheme`).

## Không test được bằng Robolectric (instrumented / thủ công)
- Tìm kiếm thật trên từng engine (Google/DDG/Brave/Bing) và trên engine tùy chỉnh (mạng, cookie consent). → thủ công trước tag M2.
- Cử chỉ Back dự đoán (predictive back) từ trang con về danh sách; animation chuyển trang. → thủ công.
- TalkBack đọc hàng cài đặt/radio; cỡ chữ lớn 200% không cắt nội dung; bố cục máy tính bảng. → thủ công (`SettingsAccessibilityManualCheck`).
- IME thật (bàn phím Uri, Done) ở ô nhập engine. → thủ công.

## Tests liên quan
- `:core:data` JVM: `CustomEngineValidatorTest` (AC1), `SearchSettingsTest` (AC2); Robolectric: `DataStoreSearchSettingsStoreTest` (AC3), `FakeSearchSettingsStoreContractTest` (AC4).
- `:core:omnibox`: `DefaultSuggestionProviderTest` (AC9).
- `:feature:settings` JVM: `SettingsRegistryTest` (AC5–AC7), `LicensesTest` (AC16 phần dữ liệu); Robolectric: `SettingsScreenTest` (AC11), `SettingsRouteTest` (AC12), `SearchEnginePageTest` (AC13, AC14), `AppearancePageTest` (AC15), `AboutPageTest` (AC16), `ManifestPermissionTest` (AC20).
- `:feature:browser`: `BrowserViewModelSearchEngineTest` (AC10); migration các test dùng `searchEngine =`.
- `:feature:home`: `HomePageTest` (placeholder tên engine, AC19 phần home).
- `:app`: `SettingsPagesTest` (AC8), `PixelBrowserApplicationSearchTest` (AC17), `MainActivitySettingsTest` (AC18, AC19), `MainActivityIntentTest` (engine động cho intent tìm kiếm).

## Out of scope
- Gợi ý tìm kiếm từ server của engine (cần mạng, riêng tư) – 004 không làm.
- Nhiều engine tùy chỉnh, từ khóa tắt kiểu `@yt`, quản lý danh sách engine, engine theo tab ẩn danh.
- Các trang cài đặt thật cho Quyền riêng tư/Site settings/Clear data/Safe Browsing/Accessibility (B07, B12, B14 – chỉ có giữ chỗ).
- Cỡ chữ, ngôn ngữ ứng dụng (per-app locale), tùy chọn trang chủ (B14).
- Nhập/xuất cài đặt; tìm kiếm trong Cài đặt.

## Quyết định
- (spec-writer, 2026-11-10) Registry dữ liệu + ngăn xếp id trong `rememberSaveable`, không dùng Navigation Compose; trang giữ chỗ tự bị thay bởi trang thật cùng id.
- (spec-writer) Một engine tùy chỉnh, `%s` đúng một lần, `http` được nhận kèm cảnh báo, thiếu scheme → `https://`.
- (spec-writer) Dùng lại DataStore `settings` của spec 011; không tạo file DataStore thứ hai.

## Quyết định (orchestrator, 2026-11-10)
- Persistence: Room 2.8.5 + KSP 2.3.13 (đã xác nhận có trên Maven; Kotlin 2.4.21/AGP 9.5.0) với spike bắt buộc ở 012 AC1; nếu không xanh sau một lượt cập nhật phiên bản thì fallback SQLiteOpenHelper (AC viết trên interface nên không đổi). Mỗi miền một file DB (`history.db`, `bookmarks.db`).
- Chấp nhận các đề xuất của spec-writer: gốc bookmark "Dấu trang" + thư mục "Đã nhập <ngày>"; thứ tự gợi ý Search/GoTo → Bookmark → History (≤ 8); desktop site không ép bỏ meta viewport (giới hạn đã biết, thử trên máy thật); ngoại lệ desktop lưu JSON file đọc đồng bộ; 015 có thể tách 2 PR (find / desktop); tùy chọn tắt lịch sử thuộc B12/B14.
- Thứ tự triển khai: 012 → 014 → 013 → 015 (rebase giữa các PR vì cùng sửa BrowserMenu/BrowserActions).
