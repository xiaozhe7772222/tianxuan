#!/usr/bin/env bash
# 天玄发布链路：构建 → 推送 GitHub → 创建 Release → 同步到分发服务器
#
# 用法：
#   ./release.sh <版本号>        仅构建并推送 GitHub（含 tag）
#   ./release.sh <版本号> --apk  额外构建 release APK 并附到 Release
#   ./release.sh <版本号> --publish 额外同步到分发服务器（需 SSH 配置）
#
# 设计约束：
#   - 不触碰服务器上任何既有服务（fusion-gateway / logview / st-rotator / st-auth / nginx）
#   - 分发落点固定在 /opt/tianxuan-dist/<版本号>/，与既有目录隔离
set -uo pipefail

VERSION="${1:-}"
WITH_APK="${2:-}"
PUBLISH="${3:-}"

REPO="${TIANXUAN_REPO:-xiaozhe7772222/tianxuan}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
DIST_SSH="${TIANXUAN_DIST_SSH:-root@124.222.37.253}"
DIST_BASE="/opt/tianxuan-dist"

if [[ -z "$VERSION" ]]; then
  echo "用法: $0 <版本号> [--apk] [--publish]" >&2
  exit 2
fi

TAG="v${VERSION}"
cd "$ROOT"

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

step "环境检查"
export JAVA_HOME="${JAVA_HOME:-/opt/jdk/jdk-25.0.4.1+1}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
command -v java >/dev/null || { echo "缺少 java，请先执行 source setup-env.sh" >&2; exit 1; }
java -version 2>&1 | head -1
gh auth status >/dev/null 2>&1 || { echo "gh 未登录" >&2; exit 1; }

step "静态校验：架构棘轮 + 星象不变量 + 社区坐标一致性"
# core:model 是纯 Kotlin 模块（任务名 test 而非 testDebugUnitTest），
# 其中的 CommunityTest 锁住群号唯一真源；settings 的守卫测试扫描
# 源码与资源，确保群号没有被当字面量复制到别处。
# database 的 *Migration* 是另一道闸：校验 Room 迁移链从 27 连续到当前
# 版本（含端到端升级），断链意味着存量用户一升级就崩且无法自愈。
# 任一失败即中止发布。
./gradlew --no-daemon architectureCheck \
  :core:common:testDebugUnitTest --tests '*AstronomyTest*' \
  :core:model:test --tests '*CommunityTest*' \
  :core:network:testDebugUnitTest --tests '*UpdateManifestParserTest*' \
  :core:database:testDebugUnitTest --tests '*Migration*' \
  :feature:settings:testDebugUnitTest --tests '*CommunityNoHardcodedGroupIdTest*' \
  -q || {
  echo "架构或单测未通过，中止发布" >&2; exit 1; }

step "构建 APK"
APK_ARGS=()
[[ "$WITH_APK" == "--apk" || "$WITH_APK" == "--publish" ]] && APK_ARGS=(-Prelease)
./gradlew --no-daemon assembleDebug "${APK_ARGS[@]:-}" || exit 1

step "提交并推送"
git add -A
if git diff --cached --quiet; then
  echo "工作区无变更"
else
  git commit -m "release: ${TAG}"
fi
git tag -a "$TAG" -m "天玄 ${VERSION}"
git push origin main --tags || exit 1

step "创建 GitHub Release"
APK_PATH=$(find . -path ./build -prune -o -name "*-release-unsigned.apk" -print -o -name "app-debug.apk" -print 2>/dev/null | head -1 || true)
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release ${TAG} 已存在"
else
  if [[ -n "${APK_PATH:-}" && -f "$APK_PATH" ]]; then
    gh release create "$TAG" "$APK_PATH" \
      --title "天玄 ${VERSION}" \
      --notes "北斗为纲，七星为枢。本版本含星象常量体系、提示词分层与 UI 星象化改造。"
  else
    gh release create "$TAG" \
      --title "天玄 ${VERSION}" \
      --notes "北斗为纲，七星为枢。本版本含星象常量体系、提示词分层与 UI 星象化改造。"
  fi
fi

if [[ "$PUBLISH" == "--publish" ]]; then
  step "同步到分发服务器 ${DIST_SSH}"
  ssh "$DIST_SSH" "install -d -m 755 '${DIST_BASE}/${VERSION}'"
  scp -r assets/plugins/. "${DIST_SSH}:${DIST_BASE}/${VERSION}/plugins/" 2>/dev/null \
    || ssh "$DIST_SSH" "install -d -m 755 '${DIST_BASE}/${VERSION}/plugins'"
  if [[ -n "${APK_PATH:-}" && -f "$APK_PATH" ]]; then
    scp "$APK_PATH" "${DIST_SSH}:${DIST_BASE}/${VERSION}/"
  fi
  ssh "$DIST_SSH" "install -d -m 755 '${DIST_BASE}/current' && \
    ln -sfn '${DIST_BASE}/${VERSION}' '${DIST_BASE}/current' && \
    chmod 755 '${DIST_BASE}' && \
    echo '分发目录：'${DIST_BASE}'/'$(basename \$(readlink -f ${DIST_BASE}/current))"
  echo "提醒：本次未改动任何既有服务（fusion-gateway / logview / st-rotator / st-auth / nginx）"
fi

step "完成"
gh release view "$TAG" --json url --jq .url