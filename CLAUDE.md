# Pixel Browser – hướng dẫn cho agent
- Quy trình: viết spec trong `docs/specs/NNN-*.md` → viết test fail → implement → refactor (SDD + TDD).
- Build/test: `mise exec -- ./gradlew test assembleDebug`. Java 21, Gradle wrapper, compileSdk 37, minSdk 31.
- Git: nhánh `feat/*`, Conventional Commits, CI xanh thì squash-merge vào `main`. Release theo milestone (tag `vX.Y.Z`), APK phải ký bằng keystore cố định và pass `apksigner verify`.
- Không commit keystore/secret. Package: `app.pixel.browser`. License: GPL-3.0.
- Roadmap: `docs/ROADMAP.md`. Backlog phạm vi: `docs/FEATURES.md` (cập nhật trạng thái mỗi PR; không bỏ sót feature).
