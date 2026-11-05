# Spec 003: Back/forward không reload (A03)
## Story
Quay lại trang trước thì hiện ngay vị trí cũ, không tải lại.

## Acceptance criteria
- Given đã duyệt A → B (cuộn xuống 40%), When `goBack()` rồi `goForward()`, Then trang B hiển thị ở đúng vị trí cuộn, không phát request mạng mới nếu cache/bfcache còn hợp lệ.
- Given WebView tab còn sống, Then lịch sử điều hướng nằm trong WebView và `canGoBack` đúng.
- Given tab bị discard rồi khôi phục, Then lịch sử + scroll được restore từ snapshot (không reload trang cuối nếu có thể; nếu buộc phải tải thì dùng cache `LOAD_CACHE_ELSE_NETWORK` cho điều hướng history).
- Mặc định cache mode của điều hướng history = `LOAD_CACHE_ELSE_NETWORK`; điều hướng thường = `LOAD_DEFAULT`.
- Nút back ở bottom bar: nhấn = back; nhấn giữ = cuộn lên đầu (A07, spec riêng).

## Edge cases
Trang `no-store`/POST có thể phải tải lại (chấp nhận, hiển thị xác nhận với form POST); redirect không tạo mục lịch sử thừa.

## Tests
`HistoryNavigationTest` (Robolectric/fake), kiểm thử thủ công trên emulator cho bfcache/scroll.
