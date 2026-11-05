# Đóng góp cho Pixel Browser

## Thiết lập
```sh
mise install            # Java 21, Gradle
mise exec -- ./gradlew spotlessApply lintDebug testDebugUnitTest assembleDebug
```
Cần Android SDK (platform 37, build-tools 36) tại `~/Android/Sdk` hoặc `ANDROID_HOME`.

## Quy trình (Spec-Driven + TDD)
1. Chọn/đề xuất feature trong [docs/FEATURES.md](docs/FEATURES.md); mở issue nếu là feature mới.
2. Viết spec `docs/specs/NNN-*.md` (acceptance criteria dạng Given/When/Then).
3. Viết test fail trước, rồi code, rồi refactor.
4. Nhánh từ `main`: `feat/...`, `fix/...`, `docs/...`, `chore/...`. Không push thẳng vào `main`.
5. Mở PR; tiêu đề theo [Conventional Commits](https://www.conventionalcommits.org). PR được **squash-merge** và nhánh tự xóa.

## Yêu cầu để PR được merge
CI xanh (Format, Lint, Unit tests, Build, PR title), 1 review từ code owner, các cuộc thảo luận đã giải quyết.
Format dùng ktlint qua Spotless: `./gradlew spotlessApply`.

## Quy tắc
- Không thêm telemetry/analytics hay thư viện non-free vào flavor F-Droid.
- Không commit keystore, khóa API hay secret.
- Giấy phép GPL-3.0: đóng góp của bạn được cấp phép theo GPL-3.0.
