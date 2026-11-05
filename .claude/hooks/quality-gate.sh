#!/usr/bin/env bash
# Block Claude from finishing while format/lint/tests fail (only when code changed).
cd "$(dirname "$0")/../.." || exit 0
git diff --quiet HEAD -- '*.kt' '*.kts' '*.xml' 2>/dev/null && [ -z "$(git ls-files -o --exclude-standard -- '*.kt' '*.kts')" ] && exit 0
out=$(mise exec -- ./gradlew spotlessCheck lintDebug testDebugUnitTest --console=plain -q 2>&1) && exit 0
echo "Quality gate failed (format/lint/test). Fix before finishing:" >&2
echo "$out" | tail -40 >&2
exit 2
