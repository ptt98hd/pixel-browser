#!/usr/bin/env bash
# Auto-format Kotlin after edits (non-blocking).
cd "$(dirname "$0")/../.." || exit 0
payload=$(cat)
case "$payload" in
  *.kt\"*|*.kts\"*) mise exec -- ./gradlew spotlessApply -q --console=plain >/dev/null 2>&1 || true ;;
esac
exit 0
