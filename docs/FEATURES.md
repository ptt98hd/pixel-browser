# Feature Backlog (nguồn sự thật duy nhất cho phạm vi dự án)

Trạng thái: ⬜ chưa làm · 🟨 đang làm · ✅ xong. Mỗi feature có ID, milestone và spec (`docs/specs/NNN-*.md`) trước khi code.
**Quy tắc**: khi thêm/bớt feature phải sửa file này; mỗi milestone chỉ release khi mọi dòng của nó là ✅ hoặc được chuyển milestone có ghi lý do. Cuối mỗi milestone rà lại mục "Chrome parity" để không sót.

## A. Lõi duyệt web (M1)
| ID | Feature | Status |
|---|---|---|
| A01 | WebView wrapper (interface + fake để test), settings an toàn (JS, DOM storage, safe browsing, mixed content) | ✅ |
| A02 | Quản lý nhiều tab, giữ state khi chuyển tab/xoay màn hình/process death | 🟨 |
| A03 | Back/forward không reload (giữ WebView + history, restore scroll) | 🟨 |
| A04 | Omnibox: nhập URL/tìm kiếm, chuẩn hóa URL, gợi ý (lịch sử/bookmark/search) | 🟨 |
| A05 | Top bar: thanh URL + nút scan QR, hiển thị domain/SSL, progress | 🟨 |
| A06 | Bottom bar 5 nút: back / forward / home / tabs / menu | 🟨 |
| A07 | Long-press: back→lên đầu, forward→xuống cuối, home→focus URL, tabs→tab mới, menu→refresh | 🟨 |
| A08 | Cử chỉ vuốt (vuốt cạnh = back/forward, vuốt bottom bar = đổi tab, pull-to-refresh) | ⬜ |
| A09 | Tab switcher (lưới, thumbnail, đóng bằng vuốt, tab mới) | ⬜ |
| A10 | Home page chỉnh được (shortcut, ảnh nền, ô tìm kiếm, sắp xếp) | ⬜ |
| A11 | File chooser, camera/mic/location permission prompts, fullscreen video, window.open/popup, JS dialogs, HTTP auth | ⬜ |
| A12 | Mở link từ app khác (VIEW intent), đặt làm trình duyệt mặc định (role) | ⬜ |
| A13 | Theme Material 3 Expressive + animation (shape morph, motion spring), dynamic color, light/dark hệ thống | ⬜ |

## B. Chrome parity (M2)
| ID | Feature | Status |
|---|---|---|
| B01 | Lịch sử duyệt web (tìm, xóa theo khoảng thời gian), gợi ý omnibox từ lịch sử/bookmark | ⬜ |
| B02 | Bookmark + thư mục, import/export (HTML) | ⬜ |
| B03 | Downloads: parallel multi-segment, resume/pause, notification, quản lý file, cài APK an toàn | ⬜ |
| B04 | Find in page | ⬜ |
| B05 | Request desktop site (toàn cục + theo site) | ⬜ |
| B06 | Incognito (cookie/cache/history tách biệt) | ⬜ |
| B07 | Site settings: cookies, JS, popup, quyền theo site, zoom/cỡ chữ | ⬜ |
| B08 | Search engine (Google, DuckDuckGo, Brave, Bing, tùy chỉnh) | ⬜ |
| B09 | Share link/page, copy link, add to home screen, print/save PDF | ⬜ |
| B10 | Autofill (Android Autofill framework), lưu mật khẩu qua provider hệ thống | ⬜ |
| B11 | Tab groups, đóng tab hàng loạt, khôi phục tab vừa đóng | ⬜ |
| B12 | Clear browsing data, Safe Browsing, HTTPS-first, cảnh báo chứng chỉ | ⬜ |
| B13 | Long-press link/ảnh menu (mở tab mới, tải ảnh, copy, share) | ⬜ |
| B14 | Cài đặt tổng, đa ngôn ngữ (vi/en), accessibility (cỡ chữ, TalkBack) | ⬜ |
| B15 | Text selection actions, view source, reader-friendly zoom | ⬜ |

## C. Nâng cao I (M3)
| ID | Feature | Status |
|---|---|---|
| C01 | Chặn quảng cáo (EasyList/uBlock syntax, network + cosmetic, whitelist theo site, cập nhật filter) | ⬜ |
| C02 | Privacy: Do-Not-Track, Global Privacy Control (do not sell/share), chặn tracker & third-party cookie, strip tracking params, fingerprint hardening | ⬜ |
| C03 | Tiết kiệm dữ liệu (chặn ảnh/video autoplay, lazy load, hiển thị lượng tiết kiệm) | ⬜ |
| C04 | Dark mode cho nội dung web (algorithmic darkening + fallback CSS, whitelist) | ⬜ |
| C05 | Reading mode (Readability, font/theme, TTS tuỳ chọn) | ⬜ |
| C06 | Scan QR (CameraX + decoder, mở link/tìm kiếm/copy) | ⬜ |
| C07 | Chia sẻ bằng QR | ⬜ |
| C08 | Mở & xem link trong dialog (preview sheet, mở rộng thành tab) | ⬜ |

## D. Nâng cao II (M4)
| ID | Feature | Status |
|---|---|---|
| D01 | Media overlay: phát hiện video, cử chỉ trái/phải = độ sáng/âm lượng, tap = play/pause, seek | ⬜ |
| D02 | Nút xoay hướng video + tốc độ phát | ⬜ |
| D03 | Dịch trang (ML Kit flavor full; FOSS cho flavor fdroid) | ⬜ |
| D04 | AI: BYO key (OpenAI-compatible/Anthropic/Gemini/Ollama), tóm tắt, hỏi đáp trang, dịch; key lưu mã hóa | ⬜ |

## E. Hạ tầng & phát hành (xuyên suốt)
| ID | Feature | Status |
|---|---|---|
| E01 | Repo, CI (test/lint/build), branch → PR → auto-merge, Conventional Commits | 🟨 |
| E02 | Keystore release cố định + workflow release ký APK, verify bằng apksigner, cài thử emulator | ⬜ |
| E03 | Split ABI + universal APK, versionCode theo tag | ⬜ |
| E04 | Metadata F-Droid (fastlane, flavor fdroid không có thư viện non-free), reproducible build | ⬜ |
| E05 | Hiệu năng (startup, memory nhiều tab), crash-safe, không telemetry | ⬜ |
| E06 | Docs: README, CONTRIBUTING, PRIVACY, changelog tự sinh | ⬜ |

## Milestone → feature
- **M1**: A01–A13 · **M2**: B01–B15 · **M3**: C01–C08 · **M4**: D01–D04 · **M5**: E02–E06 + rà soát toàn bộ + v1.0 (E02/E03 làm sớm từ M1 để mỗi milestone có APK cài được)
