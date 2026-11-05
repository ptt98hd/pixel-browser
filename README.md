# Pixel Browser

Trình duyệt Android miễn phí, mã nguồn mở (GPL-3.0), chạy trên System WebView, giao diện Material 3 Expressive.

- Roadmap & quyết định: xem [docs/ROADMAP.md](docs/ROADMAP.md)
- Specs (Spec-Driven Development): [docs/specs/](docs/specs/)

## Build
```sh
mise install
mise exec -- ./gradlew assembleDebug test
```
Yêu cầu: Android 12+ (minSdk 31). Package: `app.pixel.browser`.
