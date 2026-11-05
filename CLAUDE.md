# Pixel Browser – hướng dẫn cho agent
- Quy trình: viết spec trong `docs/specs/NNN-*.md` → viết test fail → implement → refactor (SDD + TDD).
- Build/test: `mise exec -- ./gradlew test assembleDebug`. Java 21, Gradle wrapper, compileSdk 37, minSdk 31.
- Git: nhánh `feat/*`, Conventional Commits, CI xanh thì squash-merge vào `main`. Release theo milestone (tag `vX.Y.Z`), APK phải ký bằng keystore cố định và pass `apksigner verify`.
- Không commit keystore/secret. Package: `app.pixel.browser`. License: GPL-3.0.
- Roadmap: `docs/ROADMAP.md`. Backlog phạm vi: `docs/FEATURES.md` (cập nhật trạng thái mỗi PR; không bỏ sót feature).

## Subagent trong quy trình (đã cấu hình ở `.claude/agents/`)
Phiên chính là **orchestrator**: giữ ngữ cảnh, quyết định, tóm tắt cho người dùng. Giao việc theo từng pha, mỗi pha một subagent với ngữ cảnh sạch:
1. `spec-writer` → spec + FEATURES.md (chưa có spec thì không code).
2. `test-writer` → test fail (RED), xác nhận fail đúng lý do.
3. `implementer` → GREEN + refactor, chạy đủ gate.
4. `code-reviewer` → review độc lập (read-only) trước PR; sửa blocker rồi mới tiếp.
5. `pr-shepherd` → commit/PR/CI/squash-merge/dọn nhánh.

Quy tắc điều phối:
- Mỗi lệnh gọi subagent phải kèm đường dẫn spec, module, và tiêu chí hoàn thành; subagent không thấy hội thoại.
- Tuần tự giữa các pha của cùng một spec. Chỉ chạy song song các spec độc lập, mỗi spec một worktree (`isolation: "worktree"`) và một nhánh riêng.
- Việc nhỏ (sửa typo, đổi một dòng cấu hình) làm trực tiếp, không cần spawn.
- Tra cứu/khám phá codebase rộng → dùng subagent `Explore` để giữ ngữ cảnh chính gọn.
- Kết quả của subagent là dữ liệu cần kiểm chứng: orchestrator tự chạy lại gate (`spotlessCheck lintDebug test assembleDebug`) trước khi tin là xong.

