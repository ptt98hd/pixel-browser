# Spec 002: Tab manager (A02)
## Story
Người dùng mở nhiều tab, chuyển qua lại mà trang không mất trạng thái.

## Acceptance criteria
- Given không có tab, When mở app, Then có 1 tab (home) được chọn.
- When `openTab(url, select)`, Then tab mới thêm vào cuối; nếu `select` thì thành tab hiện tại.
- When `closeTab(id)` tab hiện tại, Then chọn tab kế cận (ưu tiên tab bên phải, rồi bên trái); đóng tab cuối → tạo tab home mới.
- When đóng tab, Then có thể `undoClose()` khôi phục (tối đa 10 tab gần nhất).
- Given đổi tab, Then `EngineSession` của tab không bị recreate; scroll/form/history giữ nguyên.
- Given xoay màn hình hoặc process death, Then danh sách tab, tab hiện tại, URL và scroll được khôi phục (lưu snapshot qua DataStore + `WebView.saveState` bundle).
- Tab ẩn danh (sau này, B06) dùng cờ `isPrivate` và không được lưu xuống đĩa.
- Giới hạn bộ nhớ: tab nền cũ nhất được discard (giữ snapshot, hủy WebView) khi vượt N tab sống (mặc định 6).

## Edge cases
ID tab duy nhất và ổn định; thao tác đồng thời trên main thread; snapshot hỏng → bỏ qua, mở tab home.

## Tests
`TabManagerTest` (JVM thuần với `FakeEngineSession`), `TabPersistenceTest` (Robolectric).
