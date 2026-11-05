# Spec 001: Browser engine wrapper (A01)
## Story
Là dev, tôi muốn một interface `BrowserEngine`/`EngineSession` che WebView, để logic tab/URL test được bằng fake trên JVM mà không cần WebView thật.

## Acceptance criteria
- Given một `EngineSession` mới, When `loadUrl("https://example.com")`, Then `state.url` đổi và `state.isLoading` = true cho tới khi trang tải xong.
- Given trang đang tải, When tiến độ thay đổi, Then `state.progress` ∈ [0,100] và phát qua `StateFlow`.
- Given session, When `goBack()`/`goForward()`/`reload()`/`stopLoading()`, Then hành vi tương ứng; `canGoBack`/`canGoForward` phản ánh đúng lịch sử.
- Given cài đặt mặc định, Then: JS bật, DOM storage bật, mixed content = never allow, Safe Browsing bật, file/content access tắt.
- Given `close()`, Then WebView bị destroy và không rò rỉ (không còn tham chiếu).
- `FakeEngineSession` (module test) tuân thủ cùng hợp đồng và dùng được trong test.

## Edge cases
URL không hợp lệ → state.error; lỗi SSL → state.securityState = Insecure; gọi sau `close()` là no-op.

## Tests
Contract test dùng chung cho Fake và WebView (Robolectric): `EngineSessionContractTest`.
