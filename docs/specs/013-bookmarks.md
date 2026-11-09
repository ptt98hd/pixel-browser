# Spec 013: Bookmark + thư mục, gợi ý omnibox, import/export HTML (B02)
## Story
Người dùng bấm "Thêm dấu trang" trong menu để lưu trang hiện tại (ngôi sao hiện khi trang đã được lưu), sửa tên/URL/thư mục, hoặc xóa. Màn hình "Dấu trang" cho duyệt cây thư mục lồng nhau, tạo/đổi tên/di chuyển/xóa (có Hoàn tác), tìm kiếm, mở trong tab hiện tại hoặc tab mới. Dấu trang xuất hiện trong gợi ý omnibox. Có thể nhập từ file HTML kiểu Netscape (Chrome, Firefox, Edge… xuất ra) và xuất ra file cùng định dạng qua trình chọn file của hệ thống. File nhập được coi là không đáng tin: bị giới hạn kích thước/độ sâu/số lượng và không bao giờ thực thi hay tải gì từ nội dung.

## Phạm vi & module
- Module mới `:core:bookmarks` (Android library, không Compose), package `app.pixel.browser.core.bookmarks`: model, `BookmarkRepository` + `RoomBookmarkRepository` (hoặc `SqliteBookmarkRepository` theo quyết định persistence của spec 012), `BookmarkRules`, `BookmarkSuggestionProvider`, `NetscapeBookmarks` (parser + writer, **Kotlin thuần, không import `android.*`**), `InMemoryBookmarkRepository` (testFixtures). Phụ thuộc `:core:omnibox`.
- Module mới `:feature:bookmarks` (Compose), package `app.pixel.browser.feature.bookmarks`: `BookmarksViewModel`, `BookmarksScreen`, `BookmarkEditDialog`, `BookmarksRoute`, `BookmarkEditRoute`, `BookmarkFileGateway` + `ContentResolverBookmarkFile`, `BookmarksTestTags`. Phụ thuộc `:core:bookmarks`, `:core:ui`, `:core:omnibox`. `:feature:browser` **không** phụ thuộc `:feature:bookmarks`.
- `:feature:browser`: `BrowserViewModel` nhận `bookmarks: BookmarkRepository?`; menu thêm mục dấu trang; `BrowserTopBar` thêm chỉ báo ngôi sao.
- `:app`: tạo `bookmarks.db`, nối `AppDestination.Bookmarks` và lớp phủ sửa dấu trang (điều hướng cấp app của spec 012), thêm provider vào `SuggestionAggregator`.
- **Persistence dùng chung toolchain với 012** (Room + KSP, hoặc dự phòng SQLiteOpenHelper nếu spike 012 AC1 thất bại), nhưng file DB riêng `bookmarks.db`. Phải làm **sau** 012 (hoặc cùng nhánh sau khi spike 012 xanh); nếu 012 đã chọn dự phòng thì mọi chỗ "Room" bên dưới đọc là "SQLite", AC giữ nguyên vì viết trên `BookmarkRepository`.

## Quyết định thiết kế
- **Cây một bảng `nodes`** (`id`, `parentId`, `type` folder/bookmark, `title`, `url?`, `urlKey?`, `position`, `createdAt`, `updatedAt`) với một gốc hệ thống `ROOT_ID = 1` ("Dấu trang", không xóa/đổi tên/di chuyển được). Bảng phụ `meta(key, value)` lưu `last_folder_id`.
- **Thư mục mặc định:** chỉ có gốc; khi thêm nhanh từ menu, dấu trang vào *thư mục dùng gần nhất* (`last_folder_id`, mặc định gốc; nếu thư mục đó đã bị xóa → gốc). Mỗi lần nhập tạo thư mục `Đã nhập <yyyy-MM-dd>` dưới gốc. (Không tạo sẵn "Bookmarks bar/Other/Mobile" như Chrome: điện thoại không có thanh dấu trang; xem câu hỏi mở.)
- **Thứ tự hiển thị:** thư mục trước, rồi dấu trang; trong mỗi nhóm theo `position` (thêm vào cuối). Không có kéo-thả sắp xếp ở spec này (`move` chỉ đổi thư mục cha và đưa xuống cuối).
- **Trùng URL:** DB cho phép nhiều dấu trang cùng URL (nhập từ file giữ nguyên); UI thêm nhanh không tạo bản trùng (bấm lần hai mở hộp thoại sửa). Trạng thái ngôi sao = tồn tại ít nhất một dấu trang có cùng `urlKey` (scheme+host viết thường, bỏ fragment, bỏ `/` đơn độc ở cuối path).
- **Giới hạn cứng** (`BookmarkLimits`): 20 000 nút tổng, độ sâu thư mục ≤ 16, title ≤ 200, URL ≤ 2048, file nhập ≤ 10 MiB.
- **Hoàn tác xóa** giữ `DeletedTree` (danh sách nút kèm id/vị trí gốc) trong bộ nhớ của ViewModel tới khi snackbar biến mất; khôi phục lại đúng id/cha/vị trí.
- **Nhập/xuất qua Storage Access Framework** (`OpenDocument`, `CreateDocument`), không xin quyền lưu trữ, không giữ quyền URI lâu dài. Đọc qua `ContentResolver` với giới hạn byte đếm khi đọc (không tin `SIZE`).
- **Parser tự viết, tuyến tính, không đệ quy**: không dùng WebView/`Html.fromHtml`/XML parser (tránh thực thi/entity expansion/DTD). Chỉ nhận các thẻ `DL`, `DT`, `H3`, `A`, bỏ mọi thứ khác (kể cả `script`, `style`, `img`, `ICON`/`ICON_URI` – không giải mã, không tải).

## Thay đổi API
### `:core:bookmarks`
```kotlin
const val ROOT_ID = 1L
sealed interface BookmarkNode { val id: Long; val parentId: Long?; val title: String; val position: Int; val createdAt: Long }
data class BookmarkFolder(id, parentId, title, position, createdAt) : BookmarkNode
data class Bookmark(id, parentId, title, val url: String, position, createdAt, val updatedAt: Long) : BookmarkNode
data class FolderOption(val id: Long, val title: String, val depth: Int)           // cho bộ chọn thư mục
class DeletedTree internal constructor(/* nút + id/vị trí gốc */)

sealed interface BookmarkResult<out T> {
    data class Ok<T>(val value: T) : BookmarkResult<T>
    data object InvalidUrl : BookmarkResult<Nothing>; data object InvalidTitle : BookmarkResult<Nothing>
    data object NotFound : BookmarkResult<Nothing>;   data object Protected : BookmarkResult<Nothing>   // thao tác lên ROOT
    data object Cycle : BookmarkResult<Nothing>;      data object TooDeep : BookmarkResult<Nothing>
    data object Full : BookmarkResult<Nothing>
}
interface BookmarkRepository {
    fun observeChildren(folderId: Long): Flow<List<BookmarkNode>>
    fun observeByUrl(url: String): Flow<Bookmark?>                       // theo urlKey; nhiều bản → cái tạo sớm nhất
    suspend fun folder(id: Long): BookmarkFolder?
    suspend fun folderOptions(excludeSubtreeOf: Long? = null): List<FolderOption>   // gốc depth 0, theo thứ tự cây
    suspend fun addBookmark(url: String, title: String, parentId: Long): BookmarkResult<Bookmark>
    suspend fun updateBookmark(id: Long, title: String, url: String, parentId: Long): BookmarkResult<Bookmark>
    suspend fun createFolder(parentId: Long, title: String): BookmarkResult<BookmarkFolder>
    suspend fun rename(id: Long, title: String): BookmarkResult<Unit>
    suspend fun move(id: Long, newParentId: Long): BookmarkResult<Unit>
    suspend fun delete(id: Long): BookmarkResult<DeletedTree>
    suspend fun restore(tree: DeletedTree): Boolean
    suspend fun search(query: String, limit: Int): List<Bookmark>
    suspend fun suggest(terms: List<String>, limit: Int): List<Bookmark>
    suspend fun importTree(root: ImportedFolder, folderTitle: String): BookmarkResult<Int>   // một giao dịch, trả số bookmark
    suspend fun exportTree(): ExportFolder
    suspend fun lastFolderId(): Long;  suspend fun setLastFolderId(id: Long)
}
object BookmarkLimits { const val MAX_NODES = 20_000; MAX_DEPTH = 16; MAX_TITLE = 200; MAX_URL = 2048; MAX_IMPORT_BYTES = 10 * 1024 * 1024 }
object BookmarkRules { fun normalizeTitle(title: String, url: String): String; fun validUrl(url: String): String?; fun urlKey(url: String): String }
class BookmarkSuggestionProvider(repo: BookmarkRepository, limit: Int = 3) : SuggestionProvider

// Netscape HTML (thuần Kotlin)
data class ImportedBookmark(val title: String, val url: String, val addedAtSeconds: Long?)
data class ImportedFolder(val title: String, val folders: List<ImportedFolder>, val bookmarks: List<ImportedBookmark>)
typealias ExportFolder = ImportedFolder
sealed interface NetscapeParse {
    data class Parsed(val root: ImportedFolder, val skipped: Int, val truncated: Boolean) : NetscapeParse
    data object NotBookmarkFile : NetscapeParse
    data object TooLarge : NetscapeParse
}
object NetscapeBookmarks { fun parse(bytes: ByteArray): NetscapeParse; fun write(root: ExportFolder): ByteArray }
```
- `Ok` của `parse` có thứ tự cây = thứ tự trong file (thư mục và dấu trang xen kẽ được giữ theo từng nhóm). `skipped` = số `<A>` bị bỏ (scheme không cho phép, URL quá dài/trống). `truncated` = chạm giới hạn độ sâu/số nút.

### `:feature:bookmarks`
```kotlin
interface BookmarkFileGateway {
    suspend fun read(uri: String, maxBytes: Int): FileRead          // Bytes / TooLarge / Unreadable
    suspend fun write(uri: String, bytes: ByteArray): Boolean
}
class BookmarksViewModel(repo: BookmarkRepository, files: BookmarkFileGateway, clock: () -> Long, ...) : ViewModel() {
    val state: StateFlow<BookmarksUiState>      // folderId, breadcrumb title, children, query, results, pendingUndo, message
    fun openFolder(id: Long); fun navigateUp(): Boolean                // false khi đã ở gốc
    fun onQueryChange(q: String)
    fun createFolder(title: String); fun rename(id: Long, title: String); fun move(id: Long, parentId: Long)
    fun edit(id: Long, title: String, url: String, parentId: Long): BookmarkResult<Bookmark>
    fun delete(id: Long); fun undoDelete()
    fun import(uri: String?); fun export(uri: String?)              // null = hủy
    fun onMessageShown()
}
@Composable fun BookmarksScreen(state: BookmarksUiState, actions: BookmarksActions, modifier: Modifier = Modifier)
@Composable fun BookmarksRoute(viewModel: BookmarksViewModel, onOpenUrl: (String) -> Unit, onOpenInNewTab: (String) -> Unit, onClose: () -> Unit)
@Composable fun BookmarkEditDialog(/* title, url, folder, options, error */)
@Composable fun BookmarkEditRoute(repo: BookmarkRepository, bookmarkId: Long, onDismiss: () -> Unit)
```
### `:feature:browser`
- `BrowserViewModel(..., bookmarks: BookmarkRepository? = null)`; `fun onToggleBookmark()`. `BrowserUiState` thêm `canBookmark: Boolean` (URL là `isValidWebUrl`, không ở home), `isBookmarked: Boolean`, `bookmarkId: Long?`.
- `BrowserActions` thêm `onOpenBookmarks`, `onToggleBookmark`, `onEditBookmark: (Long) -> Unit`. `BrowserMenu` thêm `menu_bookmark` (nhãn "Thêm dấu trang"/"Sửa dấu trang"; icon sao rỗng/đặc; disabled khi `!canBookmark`) và `menu_bookmarks` ("Dấu trang"). `BrowserTopBar` thêm `bookmark_indicator` (sao đặc, không nhận chạm) cạnh domain khi `isBookmarked` và không đang sửa URL. `BrowserMessages.BOOKMARK_ADDED` (snackbar có nút "Sửa" → `onEditBookmark(id)`); `BrowserMessages.BOOKMARK_FAILED`.
- `BrowserViewModel.onOpenUrlInNewTab(url)` (thêm ở spec 012) dùng cho "mở trong tab mới".

## Acceptance criteria
### Luật & mô hình (JVM, `BookmarkRulesTest`)
1. **AC1 – Quy tắc.** `validUrl("example.com")` → `null` (chỉ nhận URL tuyệt đối http/https; không tự thêm scheme ở lớp này); `validUrl("https://a.com/x")` → chính nó; `javascript:`, `data:`, `file:`, `about:blank`, `pixel://home`, chuỗi có khoảng trắng, URL > 2048 → `null`. `normalizeTitle("  Ví dụ  ", url)` → `"Ví dụ"`; rỗng/khoảng trắng → host (`UrlDisplay.host`); > 200 ký tự → cắt 200; ký tự điều khiển bị bỏ. `urlKey`: `HTTPS://A.com/#x` và `https://a.com` cho cùng khóa; `https://a.com/p/` ≠ `https://a.com/p`; query được giữ (`?a=1` ≠ không query).

### Repository (`BookmarkRepositoryContractTest` chạy trên `InMemoryBookmarkRepository` (JVM) và `RoomBookmarkRepository` (Robolectric, in-memory))
2. **AC2 – Khởi tạo & thêm.** DB mới: `observeChildren(ROOT_ID)` rỗng, `folder(ROOT_ID)` tồn tại (title "Dấu trang"), `lastFolderId() == ROOT_ID`. `addBookmark("https://a.com", "", ROOT_ID)` → `Ok`, title = host, `position` tăng dần cho bản thứ hai, `createdAt`/`updatedAt` > 0. URL không hợp lệ → `InvalidUrl`; `parentId` không tồn tại hoặc là dấu trang (không phải thư mục) → `NotFound`. Đủ `MAX_NODES` (test dùng giới hạn nhỏ có thể tiêm) → `Full`. `observeChildren` phát lại sau mỗi thay đổi; thư mục xếp trước dấu trang.
3. **AC3 – Thư mục.** `createFolder(ROOT_ID, "Tin tức")` → `Ok`; title rỗng/khoảng trắng → `InvalidTitle`; tạo ở độ sâu 17 → `TooDeep` (gốc depth 0; thư mục con trực tiếp depth 1); `rename(ROOT_ID, "x")` → `Protected`; `rename(folderId, "Báo")` đổi title và `updatedAt`. Hai thư mục cùng tên trong cùng cha được phép.
4. **AC4 – Sửa dấu trang.** `updateBookmark(id, title, url, parentId)` đổi cả ba, giữ `id`/`createdAt`, cập nhật `updatedAt`; URL sai → `InvalidUrl` và dữ liệu không đổi; chuyển sang thư mục khác đưa xuống cuối thư mục đó; id không tồn tại → `NotFound`.
5. **AC5 – Di chuyển.** `move(b, folder)` ok; `move(folderA, folderA)` và `move(folderA, <con cháu của A>)` → `Cycle`, cây không đổi; `move(ROOT_ID, …)` → `Protected`; di chuyển làm sâu quá 16 (tính cả cây con của thư mục được chuyển) → `TooDeep`; đích không phải thư mục/không tồn tại → `NotFound`.
6. **AC6 – Xóa & hoàn tác.** Thư mục F chứa dấu trang X và thư mục G chứa Y: `delete(F)` → `Ok(tree)`; F, G, X, Y đều biến mất (`observeChildren`, `observeByUrl` cũng phát `null`). `restore(tree)` → `true`, cây đúng như cũ (cùng id, cha, `position`, title, url, `createdAt`); `restore` lần hai → `false` (không nhân đôi); nếu thư mục cha của gốc cây đã bị xóa → `false`; nếu đã có nút khác chiếm id → `false`. `delete(ROOT_ID)` → `Protected`. Xóa dấu trang đơn lẻ hoạt động tương tự.
7. **AC7 – Trạng thái theo URL.** `observeByUrl("https://a.com/")` phát `null`, rồi phát `Bookmark` sau khi thêm `https://A.com#frag`, rồi `null` sau khi xóa; nhiều bản trùng → bản `createdAt` sớm nhất; URL không hợp lệ → luôn `null`.
8. **AC8 – Thư mục dùng gần nhất.** `setLastFolderId(f)`; `lastFolderId()` → `f`; sau `delete(f)` → `ROOT_ID`; `setLastFolderId(<không tồn tại hoặc dấu trang>)` → bỏ qua. `folderOptions()` trả gốc + mọi thư mục theo thứ tự cây với `depth` đúng; `folderOptions(excludeSubtreeOf = F)` bỏ F và con cháu.
9. **AC9 – Tìm & gợi ý.** `search("wiki", 50)` khớp không phân biệt hoa thường trên title **hoặc** url, nhiều từ = AND, `%`/`_`/`\` là ký tự thường, tìm trên toàn cây; thứ tự kết quả `search` là title A–Z không phân biệt hoa thường, hòa theo `id` tăng dần (ổn định). `suggest(terms, limit)` cùng quy tắc khớp và tôn trọng `limit` (thứ tự xếp hạng do `BookmarkSuggestionProvider` quyết định, AC10).
10. **AC10 – `BookmarkSuggestionProvider`.** Input trống → rỗng (không truy vấn). `"wiki"` → các dấu trang khớp, `kind == SuggestionKind.Bookmark`, `title` (rỗng → url), `url` đầy đủ, tối đa `limit` (mặc định 3), mỗi URL một gợi ý (trùng URL gộp). Xếp: title/host **bắt đầu bằng** từ khóa trước, sau đó theo `createdAt` mới nhất trước. Repo ném lỗi → `SuggestionAggregator` bỏ qua provider. Trong aggregator `[Default, Bookmark, History]` (limit 8): bookmark nằm trước history; URL vừa là bookmark vừa là history chỉ xuất hiện một lần (bản bookmark).

### Netscape HTML (JVM thuần, `NetscapeBookmarksTest`)
11. **AC11 – Parse file chuẩn.** File kiểu Chrome (header `<!DOCTYPE NETSCAPE-Bookmark-file-1>`, `<DL><p>`, `<DT><H3 ADD_DATE="…">Tin tức</H3>`, `<DT><A HREF="https://a.com/" ADD_DATE="1700000000" ICON="data:image/png;base64,…">A</A>`, thư mục lồng nhau 3 cấp) → `Parsed` với cây đúng tên/URL/thứ tự, `addedAtSeconds` đúng (thiếu/không phải số → `null`), `skipped == 0`, `truncated == false`; `ICON` bị bỏ qua và không ảnh hưởng. Thẻ viết thường/lẫn hoa thường, thiếu `</DT>`/`<p>`, thiếu `</DL>` cuối file, xuống dòng/khoảng trắng tùy ý trong thẻ → vẫn parse đúng.
12. **AC12 – Văn bản & thực thể.** `&amp; &lt; &gt; &quot; &#39; &#x27; &nbsp;` và `&#<số>;` hợp lệ được giải mã trong title và giá trị `HREF`; thực thể sai (`&#0;`, `&#xD800;`, `&#1114112;`, `&unknown;`, không có `;`) được giữ nguyên chữ hoặc thay `U+FFFD`, **không ném**. Thẻ lồng trong title (`<A HREF=…><b>Đậm</b> chữ</A>`) → title `"Đậm chữ"`. `<script>…</script>`, `<style>`, comment `<!-- -->`, `<img onerror=…>` bị bỏ hoàn toàn (kể cả nội dung bên trong, không được xuất hiện trong title nào). Title toàn khoảng trắng → host. UTF-8 sai byte → thay `U+FFFD`, không ném.
13. **AC13 – Scheme độc hại.** Các `HREF` sau đều bị bỏ và tính vào `skipped`: `javascript:alert(1)`, `JaVaScRiPt:…`, ` \tjavascript:…` (khoảng trắng/tab đầu), `java\nscript:…` (ký tự điều khiển giữa scheme), `data:text/html,…`, `file:///etc/passwd`, `content://…`, `vbscript:…`, `intent://…`, `about:blank`, `ftp://…`, `//a.com`, `https:///x`, chuỗi rỗng, thiếu `HREF`, URL > 2048 ký tự, URL chứa khoảng trắng bên trong. `http://` và `https://` hợp lệ được giữ. `HREF` không có dấu nháy, nháy đơn, nháy kép đều được nhận; thuộc tính lặp → giá trị đầu tiên thắng.
14. **AC14 – Giới hạn & thù địch.** (`NetscapeBookmarksHostileTest`, mỗi test `@Test(timeout = 5000)`) (a) 10 MiB + 1 byte → `TooLarge`; đúng 10 MiB hợp lệ → xử lý bình thường; (b) 100 000 thẻ `<DL>` lồng nhau → không `StackOverflowError`, `truncated == true`, độ sâu kết quả ≤ 16; (c) 50 000 dấu trang → chỉ giữ tối đa `MAX_NODES` nút, `truncated == true`; (d) 10 MiB toàn ký tự `<` hoặc `<A ` lặp lại → kết thúc trong thời hạn, không ném; (e) giá trị thuộc tính 5 MiB không dấu nháy đóng → không ném, bị bỏ; (f) file không có `<DL>` hoặc không có dấu trang hợp lệ nào (HTML bất kỳ, PNG, rỗng) → `NotBookmarkFile`; (g) `<!DOCTYPE …>` với `<!ENTITY>` hoặc `<?xml …?>` không gây giải mã thực thể ngoài/mở rộng (chỉ bảng thực thể cố định); (h) title/URL vượt giới hạn được cắt/loại theo `BookmarkRules`.
15. **AC15 – Ghi (export) & round trip.** `write(root)` bắt đầu bằng `<!DOCTYPE NETSCAPE-Bookmark-file-1>` và `<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">`, UTF-8 không BOM, thụt lề `<DL><p>` đúng cấu trúc, `ADD_DATE` ghi giây (bỏ nếu `null`), **không** ghi `ICON`. Title/URL được escape (`& < > " '`): title `<script>alert(1)</script>` và `a"b&c` ghi ra không chứa thẻ `<script>` thật. `parse(write(tree))` trả cây bằng nhau (tên, URL, thứ tự, `addedAtSeconds`) cho cây có tiếng Việt, emoji, ký tự đặc biệt, thư mục rỗng, 16 cấp. Cây rỗng → file hợp lệ có `<DL><p></DL><p>` và parse lại là `NotBookmarkFile`.

### Luồng nhập/xuất (JVM, `BookmarksViewModelImportExportTest`, `FakeBookmarkFileGateway`)
16. **AC16 – Nhập.** `import(uri)` đọc tối đa `MAX_IMPORT_BYTES + 1` byte; kết quả `Parsed` → `repo.importTree(root, "Đã nhập 2026-11-10")` đúng 1 lần (một giao dịch: nếu giữa chừng lỗi → không nút nào được thêm), message `ImportDone(imported, skipped)`; thư mục nhập nằm dưới `ROOT_ID`. `TooLarge`/`Unreadable`/`NotBookmarkFile` → message lỗi tương ứng, **không** gọi `importTree`. `import(null)` (hủy) → không đọc, không message. `importTree` trả `Full` → message `ImportFull`, không thay đổi dữ liệu. Nhập hai lần cùng file → hai thư mục riêng (không dedupe).
17. **AC17 – Xuất.** `export(uri)` → `files.write(uri, NetscapeBookmarks.write(repo.exportTree()))` đúng 1 lần; kết quả `ExportDone(count)`; `write` trả `false` → `ExportFailed`; `export(null)` → không ghi. Cây xuất có thư mục gốc "Dấu trang", đúng thứ tự hiển thị.

### Trình duyệt (JVM `BrowserViewModelBookmarkTest`, Robolectric `BrowserMenuBookmarkTest`)
18. **AC18 – Ngôi sao & thêm nhanh.** URL `https://a.com/` chưa lưu: `uiState.canBookmark == true`, `isBookmarked == false`. `onToggleBookmark()` → `addBookmark(url, title, lastFolderId)` đúng 1 lần, `isBookmarked == true`, `bookmarkId` đúng, `message == BOOKMARK_ADDED`; `setLastFolderId` không đổi. Gọi lại khi đã lưu → **không** thêm/xóa, phát yêu cầu sửa (`onEditBookmark(id)` qua `BrowserActions`). Đổi trang/tab → `isBookmarked` cập nhật theo URL mới (so theo `urlKey`); xóa dấu trang ở màn hình Dấu trang → ngôi sao tắt. Ở home, `about:blank` hoặc `bookmarks == null` → `canBookmark == false` và `onToggleBookmark()` không làm gì. `addBookmark` trả `Full`/lỗi → `message == BOOKMARK_FAILED`, không crash. Tab ẩn danh vẫn lưu được dấu trang (người dùng chủ động).
19. **AC19 – Menu & chỉ báo.** `menu_bookmark` hiện đúng nhãn/trạng thái (`isBookmarked` → "Sửa dấu trang", ngược lại "Thêm dấu trang"), `assertIsNotEnabled()` khi `!canBookmark`; tap → `onToggleBookmark` và đóng menu. `menu_bookmarks` → `onOpenBookmarks`, luôn bật. `bookmark_indicator` có khi `isBookmarked && !isEditingUrl`, không có ở home. Snackbar `BOOKMARK_ADDED` có nút "Sửa" gọi `onEditBookmark(bookmarkId)`.
20. **AC20 – Hộp thoại sửa.** (`BookmarkEditDialogTest`) `bookmark_dialog` điền sẵn `bookmark_title_field`/`bookmark_url_field`/thư mục hiện tại; `bookmark_folder_picker` mở danh sách `bookmark_folder_option_<id>` có thụt lề theo `depth`; chọn → thư mục đổi; `bookmark_save` → `updateBookmark` 1 lần, đóng; URL sai → `bookmark_error` và dialog vẫn mở; `bookmark_save` disabled khi URL rỗng; `bookmark_remove` → `delete(id)` và đóng, kèm snackbar "Đã xóa" có Hoàn tác khi dùng trong trình duyệt; Back/hủy → đóng, không ghi. Nội dung nhập giữ qua xoay (`rememberSaveable`). Hộp thoại là lớp phủ trong cây compose (không `AlertDialog` – xem quyết định spec 010).

### Màn hình Dấu trang (`BookmarksViewModelTest` JVM; `BookmarksScreenTest` Robolectric)
21. **AC21 – Điều hướng thư mục.** Mở ở gốc: `bookmarks_title` = "Dấu trang", không có `bookmarks_up`. `openFolder(F)` → title = tên F, có `bookmarks_up`; `navigateUp()` → cha của F (trả `true`), ở gốc trả `false`. Back hệ thống ở thư mục con → lên một cấp (không đóng màn hình); ở gốc → đóng. Thư mục đang xem bị xóa từ nơi khác → tự về cha còn tồn tại gần nhất. Thư mục hiện tại giữ qua xoay màn hình.
22. **AC22 – Danh sách.** `bookmark_folder_<id>` (icon thư mục, title, số mục con) trước `bookmark_item_<id>` (ô chữ cái màu theo host như spec 010, title, host). Tap thư mục → mở thư mục; tap dấu trang → `onOpenUrl(url)` 1 lần, màn hình đóng. Rỗng → `bookmarks_empty` (chuỗi khác nhau cho thư mục rỗng và kết quả tìm rỗng). `LazyColumn`: 2 000 mục không compose hết.
23. **AC23 – Tìm kiếm.** `bookmarks_search` mở ô tìm; `onQueryChange` debounce 300 ms; kết quả phẳng trên toàn cây (kèm tên thư mục cha), tap mở URL; xóa chữ hoặc Back → về danh sách thư mục.
24. **AC24 – Hành động trên mục.** Long-press (không kích hoạt tap) → menu: dấu trang có `bookmark_menu_edit`, `bookmark_menu_open_new_tab`, `bookmark_menu_copy`, `bookmark_menu_move`, `bookmark_menu_delete`; thư mục có `bookmark_menu_rename`, `bookmark_menu_move`, `bookmark_menu_delete`. Đổi tên thư mục → hộp thoại, trùng/rỗng → lỗi. `bookmark_menu_move` mở bộ chọn **không** liệt kê chính nó và con cháu của nó (`folderOptions(excludeSubtreeOf)`); chọn → `move`; `Cycle` không thể xảy ra từ UI nhưng nếu repo trả → message lỗi. `bookmarks_new_folder` tạo thư mục trong thư mục đang xem.
25. **AC25 – Xóa & Hoàn tác.** `bookmark_menu_delete` → mục biến mất ngay và snackbar có `bookmarks_undo`; tap → `restore(tree)` → mục về đúng vị trí cũ (cả thư mục cùng toàn bộ con). Xóa mục thứ hai khi snackbar đầu còn hiện → tree thứ nhất không còn hoàn tác được (chỉ giữ cái gần nhất); snackbar tự tắt → `pendingUndo` bị bỏ. Có `CustomAccessibilityAction` "Xóa", "Sửa" cho mỗi hàng.
26. **AC26 – Nhập/xuất UI.** `bookmarks_import` mở trình chọn qua `ActivityResultContracts.OpenDocument` với MIME `text/html`, `application/xhtml+xml` và `*/*` dự phòng; kết quả URI → `import(uri)`; `null` → `import(null)`. `bookmarks_export` mở `CreateDocument("text/html")` với tên gợi ý `pixel-bookmarks-<yyyyMMdd>.html`. Kiểm bằng `TestActivityResultRegistry` (như spec 010 AC23). Kết quả hiện snackbar `bookmarks_import_result` (số đã nhập, số bị bỏ) hoặc thông báo lỗi; `ContentResolverBookmarkFile` đọc `content://` (Robolectric `ContentProvider` giả) giới hạn byte đếm khi đọc, không tin `OpenableColumns.SIZE`; URI không đọc được → `Unreadable` (không ném).
27. **AC27 – Điều hướng ứng dụng.** (`MainActivityBookmarksTest`) Tap `btn_menu` → `menu_bookmarks` → `bookmarks_screen` phủ toàn màn hình, session của tab chọn `isPaused`; tap dấu trang → màn hình đóng và `loadUrl` đúng URL ở tab chọn; "mở trong tab mới" → tab mới được chọn; Back/`bookmarks_close` → đóng, trạng thái trình duyệt giữ nguyên. Từ snackbar "Sửa" mở `BookmarkEditRoute` cho đúng bookmark.
28. **AC28 – Gợi ý end-to-end.** Thêm bookmark "Wikipedia", gõ `wiki` vào `url_field` → có hàng `suggestion_<i>` kind `Bookmark` hiển thị title; tap → tải URL.
29. **AC29 – i18n & quyền.** Chuỗi mới có đủ `values`/`values-vi` (`lintDebug` xanh, `MissingTranslation`); `:core:bookmarks`/`:feature:bookmarks` không khai báo quyền `INTERNET`/`READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE` (`ManifestPermissionTest`); không có code nào mở kết nối từ nội dung file nhập (không có HTTP client trong dependency của hai module).

## Edge cases
- Dấu trang cho trang đang tải dở/chưa có title: title rỗng → host; không cập nhật tự động sau này.
- URL dài với fragment/tracking: lưu nguyên (trừ giới hạn 2048); ngôi sao so theo `urlKey` nên `#fragment` không làm mất trạng thái.
- Thư mục bị xóa trong khi đang mở hộp thoại chọn thư mục: lưu thất bại `NotFound` → về gốc, thông báo lỗi.
- File nhập có hàng nghìn dấu trang trùng: giữ nguyên (không dedupe); người dùng xóa cả thư mục "Đã nhập" bằng một thao tác (có Hoàn tác).
- Hoàn tác khi app bị kill: mất (chỉ bộ nhớ) – chấp nhận.
- Xuất khi cây rất lớn: tạo `ByteArray` trong bộ nhớ ≤ vài MB (20 000 nút × ~300 B ≈ 6 MB) trên IO; chấp nhận.
- Import file lớn đang chạy rồi người dùng xoay/thoát màn hình: chạy trong `viewModelScope` của `BookmarksViewModel` (Activity-scoped) nên không bị hủy khi xoay; nếu process chết → không có gì được thêm (giao dịch).
- Nhập `ADD_DATE` quá lớn/âm → `addedAtSeconds = null`.
- Bookmark ở thư mục sâu 16: tạo thư mục con thất bại `TooDeep` (thông báo), bookmark vẫn thêm được vào thư mục sâu 16.
- Chế độ ẩn danh: không có hành vi đặc biệt (dấu trang do người dùng chủ động); B06 có thể ẩn thumbnail/favicon.

## Không test được bằng Robolectric (instrumented / thủ công)
- Trình chọn file thật (SAF `OpenDocument`/`CreateDocument`) và ghi vào Drive/USB; file Chrome/Firefox xuất thật mở được. → thủ công trên emulator + thiết bị với file Chrome/Firefox mẫu (`androidTest/assets/bookmarks-chrome.html`, `bookmarks-firefox.html`); `BookmarkImportInstrumentedTest` dùng `DocumentsProvider` giả.
- Room/KSP trên APK release (R8) và migration thật khi sau này schema đổi. → thủ công + `MigrationTestHelper` khi có v2.
- Hiệu năng cuộn/tìm với 20 000 bookmark. → thủ công + Macrobenchmark (E05).

## Tests liên quan
- `:core:bookmarks` JVM: `BookmarkRulesTest` (AC1), `BookmarkRepositoryContractTest` trên `InMemoryBookmarkRepository` (AC2–AC9), `BookmarkSuggestionProviderTest` (AC10), `NetscapeBookmarksTest` (AC11–AC13, AC15), `NetscapeBookmarksHostileTest` (AC14).
- `:core:bookmarks` Robolectric: `BookmarkRepositoryContractTest` trên `RoomBookmarkRepository` (AC2–AC9).
- `:feature:bookmarks`: JVM `BookmarksViewModelTest` (AC21, AC23 logic), `BookmarksViewModelImportExportTest` (AC16, AC17); Robolectric `BookmarkEditDialogTest` (AC20), `BookmarksScreenTest` (AC21–AC25), `BookmarksImportExportUiTest` + `ContentResolverBookmarkFileTest` (AC26), `ManifestPermissionTest` (AC29).
- `:feature:browser`: JVM `BrowserViewModelBookmarkTest` (AC18); Robolectric `BrowserMenuBookmarkTest` (AC19), `BrowserTopBarTest` (chỉ báo).
- `:app` Robolectric: `MainActivityBookmarksTest` (AC27, AC28).
- Instrumented: `BookmarkImportInstrumentedTest`.

## Out of scope
- Kéo-thả sắp xếp lại bookmark/thư mục; chọn nhiều mục.
- Favicon thật trong danh sách (dùng ô chữ cái; có thể nối `FaviconStore` của 012 AC22 sau).
- "Thêm vào trang chủ" từ bookmark; sao chép bookmark từ Chrome Sync; đồng bộ giữa thiết bị.
- Nhập từ Chrome/Firefox trực tiếp (không phải file HTML), nhập JSON/CSV; xuất JSON.
- Đọc `ICON` trong file nhập.
- Thư mục "Thanh dấu trang/Khác/Di động" kiểu Chrome desktop.

## Quyết định
- (spec-writer, 2026-11-10) Chỉ một gốc hệ thống "Dấu trang" + thư mục nhập theo ngày; thêm nhanh vào thư mục dùng gần nhất. Orchestrator xác nhận hoặc yêu cầu thư mục mặc định cố định.
- (spec-writer) Không có sắp xếp thủ công trong M2; `move` chỉ đổi cha.
- (spec-writer) Parser/writer thuần Kotlin trong `:core:bookmarks` (JVM test), không phụ thuộc WebView/Jsoup (tránh thêm dependency và bề mặt tấn công).

## Quyết định (orchestrator, 2026-11-10)
- Persistence: Room 2.8.5 + KSP 2.3.13 (đã xác nhận có trên Maven; Kotlin 2.4.21/AGP 9.5.0) với spike bắt buộc ở 012 AC1; nếu không xanh sau một lượt cập nhật phiên bản thì fallback SQLiteOpenHelper (AC viết trên interface nên không đổi). Mỗi miền một file DB (`history.db`, `bookmarks.db`).
- Chấp nhận các đề xuất của spec-writer: gốc bookmark "Dấu trang" + thư mục "Đã nhập <ngày>"; thứ tự gợi ý Search/GoTo → Bookmark → History (≤ 8); desktop site không ép bỏ meta viewport (giới hạn đã biết, thử trên máy thật); ngoại lệ desktop lưu JSON file đọc đồng bộ; 015 có thể tách 2 PR (find / desktop); tùy chọn tắt lịch sử thuộc B12/B14.
- Thứ tự triển khai: 012 → 014 → 013 → 015 (rebase giữa các PR vì cùng sửa BrowserMenu/BrowserActions).

## Quyết định / lệch spec (implementer, 2026-11-10)
- `BookmarkRepository.bookmark(id)` được thêm (orchestrator chấp nhận) cho `BookmarkEditRoute` và nút "Sửa" của snackbar. `DeletedTree` có constructor công khai (không `internal`) để fixture/test UI tạo được. `BookmarksViewModel.edit` là `suspend`.
- `maxNodes` (mặc định 20 000) không tính gốc hệ thống; thư mục bọc "Đã nhập <ngày>" của `importTree` nằm ngoài giới hạn (`đã có + số nút nhập > maxNodes` → `Full`), nên file 20 000 nút nhập được vào DB rỗng. `InMemoryBookmarkRepository.importTree` (testFixtures) được chỉnh một dòng cho khớp `RoomBookmarkRepository`. Parser không kiểm tra độ sâu của thư mục bọc, nên cây nhập có thể sâu 17 (không ảnh hưởng thao tác khác).
- Gốc hệ thống `Dấu trang` được tạo lười ở lần dùng đầu tiên của `RoomBookmarkRepository` (không dùng callback tạo DB) để test dựng DB in-memory trực tiếp; tên gốc lưu tiếng Việt, màn hình hiển thị đúng chuỗi lưu (chưa bản địa hóa tên gốc cho locale en).
- Tìm kiếm Room dùng `LIKE ... ESCAPE` và `COLLATE NOCASE`: không phân biệt hoa thường chỉ với ASCII (SQLite), khác `InMemory` (Unicode). Chấp nhận; chữ có dấu tiếng Việt phải gõ đúng chữ hoa/thường.
- Parser: `<A>` đặt dưới `<DL>` lồng quá sâu hoặc quá giới hạn nút vẫn được giữ vào thư mục sâu nhất cho phép (không bỏ nội dung), đặt `truncated = true`; thư mục không có tiêu đề (`<H3>` rỗng) đặt tên `-`; file chỉ có thư mục rỗng (không dấu trang hợp lệ nào) trả `NotBookmarkFile`.
- Hoàn tác xóa từ hộp thoại sửa trong trình duyệt: `BookmarkEditRoute.onRemoved(DeletedTree)` → `BrowserViewModel.onBookmarkRemoved` (thông báo `BOOKMARK_REMOVED` có nút "Hoàn tác" → `onUndoBookmarkRemoved`, chỉ giữ lần xóa gần nhất). Lưu thành công trong hộp thoại cũng gọi `setLastFolderId(thư mục đã chọn)`.
- Hộp thoại sửa/đổi tên/di chuyển là lớp phủ trong cây compose (không `AlertDialog`), bộ chọn thư mục trong hộp thoại sửa mở inline. Đổi tên thư mục trùng tên được phép; chỉ tên rỗng là lỗi.
- Dữ liệu `bookmarks.db` đã nằm trong diện loại trừ của `data_extraction_rules.xml` (domain `database`) và `allowBackup=false`: dấu trang không được sao lưu/chuyển thiết bị (dùng xuất HTML để sao lưu).
- Nhập qua `ImportDone(imported, skipped)` không báo `truncated`; chưa có thông báo riêng cho trường hợp bị cắt.

## Quyết định (implementer, sau review, 2026-11-10)
- **Tên gốc / tiền tố nhập bản địa hóa:** kho vẫn lưu `ROOT_TITLE = "Dấu trang"`. `BookmarksViewModel` nhận `rootTitle` và `importFolderPrefix` (mặc định "Dấu trang"/"Đã nhập" vì AC16/AC21 cố định chuỗi này); ánh xạ `id == ROOT_ID` → `rootTitle` làm ở ViewModel cho tiêu đề màn hình, `FolderOption` và `BookmarkHit.folderTitle` (không ở Screen vì test Screen/Route cố định "Dấu trang" ở locale en). `MainActivity` truyền chuỗi `bookmarks_root_title`/`bookmarks_import_folder_prefix` (strings của `:app`, en + vi); `BookmarkEditRoute` có tham số `rootTitle`.
- **Lệch yêu cầu review:** `<H1>` của file xuất không cố định "Bookmarks" mà là `rootTitle` (VM thay tên gốc trước khi ghi), vì `ac17_exportedTree_hasRootTitleAndDisplayOrder` yêu cầu file chứa "Dấu trang" ở cấu hình mặc định; app truyền chuỗi theo locale. `NetscapeBookmarks.write` vẫn ghi `root.title`.
- `edit`/`folderOptions`/`runSearch` đi qua `guarded`: lỗi lưu trữ → `NotFound` / danh sách rỗng / tiêu đề thư mục rỗng. `edit` thành công gọi `setLastFolderId` (thống nhất với `BookmarkEditRoute`).
- `BookmarksRoute` có `DisposableEffect` gọi `onUndoExpired()` khi rời composition, nên `pendingUndo` không treo (cái giá: xoay màn hình khi snackbar còn hiện cũng bỏ khả năng hoàn tác).
- `BookmarkRepository.childCounts()` (một truy vấn `GROUP BY parentId`) thay N lần `observeChildren(...).first()`.
- `restore` kiểm tra thêm: `DeletedTree.foldersHeight()` (id duy nhất, cha của mọi nút ngoài gốc thuộc cây và là thư mục, URL dấu trang hợp lệ) và độ sâu `depth(cha)+1+chiều cao ≤ MAX_DEPTH`; vi phạm → `false`.
- `onToggleBookmark` kiểm lại `observeByUrl(url).first()` trước khi thêm; đã có → phát yêu cầu sửa, không thêm bản trùng.
- `importTree` giữ `ADD_DATE` hợp lệ làm `createdAt` (giây×1000), nên xuất lại bảo toàn `addedAtSeconds`; thiếu/không hợp lệ → thời điểm nhập.
- `suggest` ở repo xếp khớp đầu (title, hoặc URL sau scheme/`www.`) của từ khóa đầu tiên trước, rồi `createdAt` mới nhất, nên khớp đầu cũ không bị cắt bởi trần 100 ứng viên của provider. Room xấp xỉ "host" bằng `url LIKE 'http(s)://(www.)từ%'`.
- Giới hạn âm/0 → danh sách rỗng (SQLite `LIMIT -1` là không giới hạn).
- `cleanTitle` bỏ ký tự định dạng Unicode (Cf: bidi, zero-width, BOM) trừ ZWNJ/ZWJ (giữ emoji ghép và chữ viết cần chúng); `validUrl` từ chối mọi ký tự Cf.
- Parser: tên thẻ phải theo sau bởi khoảng trắng, `/` hoặc `>` (hoặc hết file); `<a-b ...>` không còn bị coi là `<A>`.
- Giới hạn đã biết: file nhập chỉ đọc UTF-8 (không đọc `charset` trong META; byte sai thành U+FFFD). Ghi SAF dùng chế độ `"wt"`.
- Chạm scrim không còn đóng hộp thoại có ô nhập (sửa dấu trang, tên thư mục); Back/Hủy vẫn đóng. Hộp thoại chọn thư mục đích vẫn đóng khi chạm ngoài.
