# Pixel Browser — Kế hoạch tổng thể

## Context
Trình duyệt Android miễn phí, mã nguồn mở, chạy trên System WebView, UI Material 3 Expressive. Dự án làm theo Spec-Driven Development (SDD) + TDD, tự động hóa hoàn toàn coding và quản lý repo (branch/commit/push/merge/release) không cần user review.

## Quyết định đã chốt
- Kotlin + Jetpack Compose (Material 3 Expressive), minSdk 31 (Android 12), target/compile SDK mới nhất cài được.
- applicationId: `app.pixel.browser` (theo gợi ý của user; dễ đọc, khớp tên dự án). Repo public `ptt98hd/pixel-browser` (gh đã đăng nhập).
- License GPL-3.0.
- AI: BYO API key, đa nhà cung cấp (OpenAI-compatible, Anthropic, Gemini, Ollama).
- Dịch: ML Kit on-device ở flavor `play`/`full`; flavor `fdroid` dùng nguồn FOSS (kiểm tra chính sách F-Droid, ML Kit là non-free). Adblock: EasyList/uBlock filter tự parse.
- Phát triển theo milestone, mỗi milestone tự tag + GitHub Release (APK ký).

## Yêu cầu APK cài được, không bị chặn
- Mọi APK release phải được ký (APK Signature v2/v3) bằng keystore release cố định (tạo bằng keytool, lưu trong GitHub Secrets + bản sao local ngoài repo, không commit) để cập nhật đè được, không báo "App not installed"/"conflict".
- zipalign + R8/minify đúng; targetSdk mới nhất (Android 14+ chặn cài app target < 24); minSdk 31 khớp thiết bị Android 12+.
- Xuất universal APK + split theo ABI (arm64-v8a, armeabi-v7a, x86_64); `versionCode` tăng đơn điệu theo tag.
- Kiểm tra tự động trong release workflow: `apksigner verify --verbose`, `aapt dump badging`, rồi cài thử bằng `adb install` trên emulator trước khi publish Release.
- Lưu ý trung thực: Play Protect có thể hiện cảnh báo "ứng dụng không xác minh" với app sideload chưa lên Play/F-Droid; đây là cảnh báo cho phép "Cài đặt vẫn tiếp tục", không phải lỗi APK. F-Droid sẽ tự ký bằng key của họ (cần giữ khác key khi chuyển nguồn, hoặc dùng reproducible build).

## Môi trường (hiện trạng)
Repo git rỗng (chưa commit, chưa remote). Có: mise (java temurin 17 & 21, node, python), gh, git, `~/Android/Sdk` (build-tools, cmdline-tools, platforms, platform-tools). Chưa set java global trong mise.
Việc setup: `mise.toml` pin java 21 + gradle wrapper; biến ANDROID_HOME; cài platform/build-tools bằng sdkmanager; kiểm tra KVM cho emulator (Robolectric dùng cho unit test chính).

## Quy trình SDD + TDD
1. `docs/specs/NNN-<feature>.md`: user story, acceptance criteria (Given/When/Then), edge cases — viết trước code.
2. Mỗi criterion → test fail trước (JUnit5/Robolectric, Compose UI test, Turbine cho Flow) → implement → refactor.
3. Git: nhánh `feat/<spec>`, Conventional Commits, CI GitHub Actions (lint, ktlint/detekt, unit test, assemble) → tự squash-merge vào `main` khi xanh. Release-please/semantic tag theo milestone, upload APK lên GitHub Releases.
4. Kiến trúc: multi-module (`app`, `core:engine` (WebView wrapper/interface để test được bằng fake), `core:data` (Room, DataStore), `feature:*`), MVVM/UDF, Hilt.

## Milestones
**M0 – Khởi tạo**: git init remote, LICENSE, README, CLAUDE.md, mise.toml, Gradle version catalog, CI, specs mẫu, theme M3 Expressive.
**M1 – Lõi duyệt web**: WebView tab manager (nhiều tab, giữ state, back/forward không reload qua bfcache/giữ WebView), thanh URL (top) + omnibox/search engine, bottom bar 5 nút với long-press (back→lên đầu, forward→xuống cuối, home→focus URL, tabs→tab mới, menu→refresh) + cử chỉ vuốt, tab switcher, home page tùy chỉnh (shortcut/ảnh nền), progress, SSL indicator, hỗ trợ file chooser/permissions/fullscreen video.
**M2 – Tính năng kiểu Chrome**: lịch sử, bookmark, downloads (parallel multi-segment download, resume), tìm trong trang, desktop site, incognito, cài đặt (search engine, cookies, site settings, permissions), share, print/save PDF, autofill/password (Autofill framework), tab groups, clear data, request desktop, translate menu hooks, custom tabs-like intents, default browser role, import/export.
**M3 – Nâng cao I**: adblock (shouldInterceptRequest + cosmetic filter CSS), privacy (DNT, GPC "do not sell/share", chặn tracker/third-party cookies, HTTPS-only), data saver (chặn ảnh/lazy/nén, block heavy media), dark mode nội dung web (algorithmic darkening + CSS injection), reading mode (Readability.js), QR scan (CameraX + ML Kit/ZXing) và chia sẻ link bằng QR, link preview dialog.
**M4 – Nâng cao II**: media overlay (JS bridge phát hiện `<video>`, cử chỉ dọc trái/phải = độ sáng/âm lượng, tap = play/pause, nút xoay & tốc độ), dịch trang, AI (tóm tắt, hỏi đáp trang, dịch) với provider BYO key lưu EncryptedSharedPreferences.
**M5 – Hoàn thiện**: hiệu năng, accessibility, i18n (vi/en), metadata F-Droid (fastlane, reproducible build), v1.0.

## Files quan trọng (tạo mới)
`settings.gradle.kts`, `gradle/libs.versions.toml`, `mise.toml`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `docs/specs/*`, `app/`, `core/*`, `feature/*`.

## Kiểm chứng
- `./gradlew test lint detekt assembleDebug` (CI + local).
- Compose UI test + Robolectric cho từng spec; instrumented test trên emulator API 34 khi có KVM.
- Chạy app thật trên emulator (adb) để smoke test từng milestone trước khi tag release.
- Kiểm tra release: APK ký xuất hiện trong GitHub Releases, changelog tự sinh.

## Bước đầu tiên sau khi duyệt
Thực hiện M0 (setup môi trường, repo GitHub, CI, khung dự án + spec đầu tiên), rồi lần lượt M1→M5 tự động.
