# Spec 004: Omnibox – phân loại URL/tìm kiếm, chuẩn hóa URL (A04)
## Story
Người dùng gõ bất kỳ thứ gì vào thanh URL rồi nhấn Go: nếu trông như địa chỉ web thì mở trang đó (tự thêm scheme), nếu không thì tìm kiếm bằng search engine đang chọn. Những scheme nguy hiểm bị chặn. Khi không chỉnh sửa, thanh URL chỉ hiện domain gọn gàng.

## Phạm vi & module
- Module mới `:core:omnibox`, Kotlin/JVM thuần (không phụ thuộc Android SDK), test bằng JUnit trên JVM. Package `app.pixel.browser.core.omnibox`.
- API công khai (tên chốt để test-writer dùng):
  - `sealed interface OmniboxResult`: `Navigate(url: String)`, `Search(query: String, url: String)`, `Blocked(input: String, reason: BlockReason)`, `Empty`.
  - `enum class BlockReason { DangerousScheme, UnsupportedScheme }`.
  - `object OmniboxClassifier { fun classify(input: String, engine: SearchEngine): OmniboxResult }`.
  - `data class SearchEngine(val id: String, val name: String, val template: String)` + `fun SearchEngine.searchUrl(query: String): String` + `val SearchEngine.homeUrl: String` (scheme + host của template, kết thúc bằng `/`).
  - `object SearchEngines { val Google; val DuckDuckGo; val Brave; val Bing; val all: List<SearchEngine>; val Default = Google }`.
  - `object UrlDisplay { fun host(url: String): String; fun forEditing(url: String): String }`.
  - Gợi ý: `data class Suggestion(val kind: SuggestionKind, val title: String, val url: String)`, `enum class SuggestionKind { GoTo, Search }` (B01/B02 sẽ thêm `History`, `Bookmark`), `fun interface SuggestionProvider { suspend fun suggest(input: String): List<Suggestion> }`, `class DefaultSuggestionProvider(engine: SearchEngine) : SuggestionProvider`, `class SuggestionAggregator(providers: List<SuggestionProvider>, limit: Int = 8)`.

## Acceptance criteria
Mặc định engine = Google (`https://www.google.com/search?q=%s`) trừ khi ghi khác.

### Chuẩn hóa đầu vào
1. **AC1 – Trim.** Given input `"  example.com \n"`, When `classify`, Then `Navigate("https://example.com")`.
2. **AC2 – Rỗng.** Given input `""`, `"   "` hoặc `"\t\n"`, When `classify`, Then `Empty`.

### Phân loại URL (không có scheme)
3. **AC3 – Domain trần → https.** Given `example.com`, `sub.example.co.uk`, `example.com/path?q=1#frag`, Then lần lượt `Navigate("https://example.com")`, `Navigate("https://sub.example.co.uk")`, `Navigate("https://example.com/path?q=1#frag")` (không tự thêm `/`, path/query/fragment giữ nguyên).
4. **AC4 – Host lowercase, path giữ case.** Given `Example.COM/Path`, Then `Navigate("https://example.com/Path")`.
5. **AC5 – Domain có port.** Given `example.com:8443/a`, Then `Navigate("https://example.com:8443/a")`.
6. **AC6 – localhost → http.** Given `localhost`, `localhost:8080`, `LOCALHOST:8080/api`, Then `Navigate("http://localhost")`, `Navigate("http://localhost:8080")`, `Navigate("http://localhost:8080/api")`.
7. **AC7 – IPv4 → http.** Given `192.168.1.1`, `10.0.0.2:3000/x`, Then `Navigate("http://192.168.1.1")`, `Navigate("http://10.0.0.2:3000/x")`.
8. **AC8 – IPv6 → http, bọc ngoặc.** Given `[::1]`, `[::1]:8080`, `[2001:db8::1]/a`, Then `Navigate("http://[::1]")`, `Navigate("http://[::1]:8080")`, `Navigate("http://[2001:db8::1]/a")`. Given IPv6 hợp lệ không ngoặc `::1` hoặc `2001:db8::1`, Then `Navigate("http://[::1]")`, `Navigate("http://[2001:db8::1]")`.
9. **AC9 – IDN.** Given `bücher.de`, Then `Navigate("https://xn--bcher-kva.de")` (chuyển Punycode bằng `java.net.IDN`).
10. **AC10 – Trailing dot.** Given `example.com.`, Then `Navigate("https://example.com.")`.

### Phân loại tìm kiếm
11. **AC11 – Không giống URL → Search.** Mỗi input sau cho ra `Search`: `hello`, `example` (một từ, không dấu chấm), `hello world`, `example.com is down` (có khoảng trắng bên trong), `3.14` (TLD là số), `1.2.3` (IPv4 thiếu octet), `256.1.1.1` (octet > 255), `example.c` (TLD 1 ký tự), `-bad-.com` (label bắt đầu/kết thúc bằng `-`), `example.com:0`, `example.com:65536`, `example.com:abc` (port sai), `user@example.com` (có `@` trong phần host), `1234`, `what is 2+2?`.
12. **AC12 – URL search đúng template.** Given `hello world`, Then `Search(query = "hello world", url = "https://www.google.com/search?q=hello%20world")`.
13. **AC13 – Percent-encoding UTF-8.** Given `a&b=c#d/e?f+g%`, Then `url` = `https://www.google.com/search?q=a%26b%3Dc%23d%2Fe%3Ff%2Bg%25`. Given `phở bò`, Then `url` = `https://www.google.com/search?q=ph%E1%BB%9F%20b%C3%B2`. Quy tắc: giữ nguyên ký tự unreserved RFC 3986 (`A-Z a-z 0-9 - . _ ~`), còn lại percent-encode theo byte UTF-8 (hex in hoa), dấu cách → `%20`.
14. **AC14 – Mọi `%s` được thay.** Given engine tùy chỉnh template `https://s.example/?q=%s&alt=%s`, When `searchUrl("x y")`, Then `https://s.example/?q=x%20y&alt=x%20y`.
15. **AC15 – Danh sách engine mặc định.** `SearchEngines.all` gồm đúng 4 phần tử theo thứ tự Google, DuckDuckGo, Brave, Bing với template lần lượt `https://www.google.com/search?q=%s`, `https://duckduckgo.com/?q=%s`, `https://search.brave.com/search?q=%s`, `https://www.bing.com/search?q=%s`; `SearchEngines.Default == SearchEngines.Google`; id duy nhất.
16. **AC16 – Đổi engine.** Given engine = DuckDuckGo, input `kotlin`, Then `Search("kotlin", "https://duckduckgo.com/?q=kotlin")`.
17. **AC17 – Template không hợp lệ.** Given template không chứa `%s` hoặc scheme khác `http`/`https` (vd `javascript:%s`), When tạo `SearchEngine`, Then ném `IllegalArgumentException` (validate trong `init`).
18. **AC18 – homeUrl.** `SearchEngines.Google.homeUrl == "https://www.google.com/"`, `SearchEngines.DuckDuckGo.homeUrl == "https://duckduckgo.com/"`.

### Có scheme
19. **AC19 – http/https giữ nguyên, chuẩn hóa scheme + host.** Given `HTTPS://Example.com/A`, Then `Navigate("https://example.com/A")`. Given `http://example.com`, Then `Navigate("http://example.com")` (không nâng cấp lên https – việc của B12).
20. **AC20 – http(s) thiếu host → Search.** Given `https://`, `http:///path`, Then `Search` với query là input đã trim.
21. **AC21 – Scheme nguy hiểm bị chặn.** Given `javascript:alert(1)`, `JavaScript:alert(1)`, `  javascript:void(0)`, `file:///sdcard/a.html`, `content://com.android.contacts/x`, `intent://scan#Intent;scheme=zxing;end`, Then `Blocked(input, DangerousScheme)` và không bao giờ trả `Navigate`/`Search` cho các input này.
22. **AC22 – `data:` bị chặn.** Given `data:text/html,<script>alert(1)</script>`, Then `Blocked(input, DangerousScheme)` (top-level `data:` là vector phishing; engine cũng không nhận).
23. **AC23 – `about:`.** Given `about:blank` hoặc `ABOUT:BLANK`, Then `Navigate("about:blank")`. Given `about:config` hoặc `about:` khác, Then `Blocked(input, UnsupportedScheme)`.
24. **AC24 – Scheme lạ → Search.** Given `foo:bar`, `mailto:a@b.c`, `tel:123`, Then `Search` (mailto/tel qua intent thuộc A12).
25. **AC25 – `host:port` không bị nhầm là scheme.** Given `localhost:8080`, `example.com:8080` → `Navigate` (AC5/AC6); Given `foo:8080` (một từ không phải localhost) → `Search`.

### Hiển thị URL
26. **AC26 – `UrlDisplay.host`.** `https://www.example.com/path?q=1` → `example.com`; `http://example.com:8080/a` → `example.com:8080`; `https://www2.example.com` → `www2.example.com` (chỉ bỏ đúng tiền tố `www.`); `https://www.com` → `www.com` (không bỏ nếu phần còn lại không có dấu chấm); `http://[::1]:8080/` → `[::1]:8080`; `https://xn--bcher-kva.de` → `xn--bcher-kva.de` (giữ Punycode, chống homograph); `about:blank` → `""`; `""` → `""`; chuỗi không parse được → trả nguyên chuỗi.
27. **AC27 – `UrlDisplay.forEditing`.** Trả URL đầy đủ để sửa: `https://example.com/a` → `https://example.com/a` (không cắt); `about:blank` → `""`.

### Gợi ý (chỉ search-engine, chưa có lịch sử/bookmark)
28. **AC28 – Input giống URL.** Given `DefaultSuggestionProvider(Google)`, When `suggest("example.com")`, Then đúng 2 gợi ý theo thứ tự: `GoTo(url = "https://example.com")`, `Search(url = "https://www.google.com/search?q=example.com")`.
29. **AC29 – Input là truy vấn.** When `suggest("hello world")`, Then đúng 1 gợi ý `Search` với url của AC12.
30. **AC30 – Rỗng/bị chặn.** When `suggest("")` hoặc `suggest("javascript:alert(1)")`, Then danh sách rỗng.
31. **AC31 – Aggregator.** Given 2 provider (một `DefaultSuggestionProvider` và một fake trả 10 gợi ý, trong đó 1 trùng `url` với gợi ý GoTo), When `SuggestionAggregator.suggest(input)`, Then kết quả giữ thứ tự provider, loại trùng theo `url` (giữ bản xuất hiện trước), cắt còn tối đa `limit` (= 8).
32. **AC32 – Provider lỗi không làm hỏng kết quả.** Given một provider ném exception, Then aggregator bỏ qua provider đó và vẫn trả gợi ý của provider khác (trừ `CancellationException` phải được ném lại).

## Edge cases
- Input rất dài (≥ 10 000 ký tự, cả dạng URL lẫn truy vấn) không ném exception (test trong `OmniboxClassifierTest`).
- Ký tự điều khiển/zero-width (`​`) ở đầu/cuối được trim như khoảng trắng; nếu nằm giữa phần host → `Search`.
- Khoảng trắng bên trong luôn → `Search`, kể cả khi có scheme (`https://exa mple.com` → `Search`).
- Input chỉ là `/`, `.`, `:` hoặc `?` → `Search`.
- Phát hiện scheme: phần trước `:` đầu tiên khớp `[A-Za-z][A-Za-z0-9+.-]*` và phần sau không phải port số → coi là scheme; so sánh không phân biệt hoa thường.
- Thứ tự kiểm tra: trim → rỗng → scheme bị chặn (AC21–AC23) → whitespace bên trong → URL/Search. Vì vậy `javascript:alert('a b')` (có khoảng trắng) vẫn là `Blocked`, không phải `Search`.
- `javascript:` có tab/newline chen giữa (`java\tscript:alert(1)`) không được thành `Navigate` (test khẳng định "không phải Navigate"; kết quả mong đợi `Search` theo luật whitespace).
- Mặc định https cho domain công khai; http cho `localhost`/IP literal vì dev server hiếm khi có TLS. HTTPS-first/upgrade là B12.
- Lưu engine người dùng chọn (DataStore) và UI chọn engine thuộc B08; spec này chỉ nhận `SearchEngine` làm tham số.

## Tests liên quan
Tất cả là JUnit trên JVM trong `:core:omnibox` (không cần Robolectric, không cần instrumented):
- `OmniboxClassifierTest` – AC1–AC11, AC19–AC25, edge cases (dùng parameterized test cho bảng input).
- `SearchEngineTest` – AC12–AC18.
- `UrlDisplayTest` – AC26–AC27.
- `DefaultSuggestionProviderTest`, `SuggestionAggregatorTest` – AC28–AC32 (`kotlinx-coroutines-test` `runTest`).

## Out of scope
- Gợi ý từ lịch sử (B01) và bookmark (B02); gợi ý từ server search engine (autocomplete online – cân nhắc privacy, có thể không làm).
- Inline autocomplete (điền phần đuôi domain), "query in omnibox" (hiện từ khóa thay vì URL trang kết quả).
- UI chọn/lưu search engine, engine tùy chỉnh do người dùng thêm (B08).
- HTTPS-first/upgrade (B12); mở `mailto:`/`tel:`/`intent:` sang app khác (A12).
- Hiển thị IDN dạng Unicode an toàn (cần chính sách chống homograph riêng).
- UI omnibox (TextField, dropdown gợi ý) – thuộc spec 005.

## Quyết định (orchestrator, 2026-11-05)
- `localhost`/IP literal → `http://`; còn lại → `https://`.
- Chỉ cho phép `about:blank` trong nhóm `about:`; engine (`isValidWebUrl`) phải chấp nhận `about:blank` khi làm spec 005.
- A04 được đánh ✅ khi spec này xong; gợi ý từ lịch sử/bookmark chuyển sang B01/B02 (qua `SuggestionProvider`).
