#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

echo "[CI] 1/4 模块边界校验"
./gradlew verifyModuleBoundaries

echo "[CI] 2/4 单元测试"
./gradlew :app:testDebugUnitTest

echo "[CI] 3/4 Lint 门禁"
if [[ "${ALLOW_LINT_FAILURE:-false}" == "true" ]]; then
  ./gradlew :app:lintDebug || {
    echo "[CI] lint 失败已被 ALLOW_LINT_FAILURE=true 放行（仅限本地临时排查）"
  }
else
  ./gradlew :app:lintDebug
fi

echo "[CI] 4/4 Debug 包构建"
./gradlew :app:assembleDebug

echo "[CI] 质量门禁通过"
