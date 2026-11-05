# Spec 002: Tab manager (A02)
## Story
Người dùng mở nhiều tab, chuyển qua lại mà trang không mất trạng thái.

## Acceptance criteria
- Given không có tab, When mở app, Then có 1 tab (home) được chọn.
- When `openTab(url, select)`, Then tab mới thêm vào cuối; nếu `select` thì thành tab hiện tại.
- When `closeTab(id)` tab hiện tại, Then chọn tab kế cận (ưu tiên tab bên phải, rồi bên trái); đóng tab cuối → tạo tab home mới.
- When đóng tab, Then có thể `undoClose()` khôi phục (tối đa 10 tab gần nhất).
- Given đổi tab, Then `EngineSession` của tab không bị recreate; scroll/form/history giữ nguyên.
- Given xoay màn hình hoặc process death, Then danh sách tab, tab hiện tại, URL và scroll được khôi phục (lưu snapshot vào file JSON (org.json) qua `TabSnapshotStore` + `WebView.saveState` bundle).
- Tab ẩn danh (sau này, B06) dùng cờ `isPrivate` và không được lưu xuống đĩa.
- Giới hạn bộ nhớ: tab nền cũ nhất được discard (giữ snapshot, hủy WebView) khi vượt N tab sống (mặc định 6).

## Edge cases
ID tab duy nhất và ổn định; thao tác đồng thời trên main thread; snapshot hỏng → bỏ qua, mở tab home.

## Quyết định
- Lưu trữ: file JSON (org.json) qua `TabSnapshotStore` (`FileTabSnapshotStore`); `engineState` mã hóa Base64. JSON/Base64 hỏng → bỏ snapshot (hoặc bỏ riêng engine state), mở tab home.
- Engine state: `EngineSession.saveState()/restoreState()`; với WebView là bundle của `WebView.saveState` được marshall bằng `Parcel` (chỉ đảm bảo cùng phiên bản Android/WebView; lỗi → trả null/false và tải lại URL).
- Discard: nạn nhân là tab nền ít được chọn gần đây nhất (mở tab cũng tính là "dùng"); không bao giờ discard tab đang chọn. Tab bị discard giữ snapshot (url, title, engine state) và session bị đóng; khi được chọn lại thì tạo session mới qua `EngineSessionFactory` rồi `restoreState` (thất bại → `loadUrl`).
- `undoClose()` khôi phục tab về vị trí cũ (clamp theo số tab hiện có), cùng id, chọn tab đó và khôi phục lịch sử từ engine state đã lưu. Ngăn xếp undo chỉ nằm trong bộ nhớ.
- Ngăn xếp undo giữ cả tab ẩn danh nhưng chỉ trong bộ nhớ (không bao giờ ghi đĩa); khi thoát chế độ ẩn danh (B06) phải xóa các mục ẩn danh khỏi ngăn xếp.
- File snapshot phải nằm trong `context.noBackupFilesDir` (không thuộc Auto Backup). `FileTabSnapshotStore` ghi bằng `android.util.AtomicFile`; lỗi I/O khi ghi được nuốt (giữ bản cũ, dọn file tạm), khi đọc thì trả null.
- `TabSnapshotStore.read/write` làm I/O đĩa: caller gọi ngoài main thread (`Dispatchers.IO`); `snapshot()` chụp trên main thread. Constructor `TabManager` gọi `store.read()`.
- `TabManager.close()` giải phóng mọi session sống (giữ engine state trong snapshot) và xóa ngăn xếp undo. `maxLiveSessions` phải ≥ 1.
- Khi tạo lại session mà `restoreState` thất bại, chỉ `loadUrl` nếu URL là URL web hợp lệ (http/https); URL khác trong snapshot (vd. `file:`, `javascript:`) bị bỏ qua.
- Khi khôi phục từ đĩa chỉ tab được chọn có session ngay; tab nền khôi phục lười khi được chọn. ID trùng trong snapshot được cấp ID mới.
- Tab ẩn danh không bao giờ được ghi; nếu tab đang chọn là ẩn danh thì `selectedTabId` lưu là tab thường dùng gần nhất.
- Tab home (`HOME_URL = "about:blank"`) không gọi `loadUrl`.
- Khôi phục vị trí cuộn (scroll) dựa vào `WebView.saveState`/bfcache: kiểm thử thủ công trên emulator, không có unit test.

## Tests
`TabManagerTest` (JVM thuần với `FakeEngineSession`), `TabPersistenceTest` (Robolectric, `FileTabSnapshotStore`). Kiểm thử thủ công: khôi phục scroll sau xoay màn hình/process death.
