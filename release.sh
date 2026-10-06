#!/usr/bin/env bash
# 天玄发布链路：构建 → 推送 GitHub → 创建 Release → 同步到分发服务器
#
# 用法：
#   ./release.sh                按 gradle.properties 里的版本号发布（单一真源）
#   ./release.sh <版本号>       仅当与 gradle.properties 一致时放行，否则中止
#   ./release.sh --apk          额外构建 release APK 并附到 Release
#   ./release.sh --publish      额外同步到分发服务器（需 SSH 配置）
#
# 设计约束：
#   - 不触碰服务器上任何既有服务（fusion-gateway / logview / st-rotator / st-auth / nginx）
#   - 分发落点固定在 /opt/tianxuan-dist/<版本号>/，与既有目录隔离
#   - 版本号真源只有 gradle.properties 一处；本脚本不生成、不修改版本号
#   - 构建产物 APK 的实际 versionName/versionCode 会被校验，与 tag 不符即中止
set -uo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

REPO="${TIANXUAN_REPO:-xiaozhe7772222/tianxuan}"
DIST_SSH="${TIANXUAN_DIST_SSH:-root@124.222.37.253}"
DIST_BASE="/opt/tianxuan-dist"
RELEASE_NOTES_FILENAME="RELEASE_NOTES.md"

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m错误: %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 版本号真源
# 真源在 gradle.properties。命令行传版本号不作为第二真源，只当一致性断言：
# 传了就必须与真源相等，不等即中止——否则「以为发布了新版、实际推的还是旧版号」
# 会一直无人察觉，直到用户点更新发现拉不到包。
read_gradle_property() {
  local key="$1"
  local line
  line=$(grep -E "^${key}=" gradle.properties | tail -n 1) || true
  [[ -n "$line" ]] || die "gradle.properties 缺少 ${key}"
  printf '%s' "${line#*=}" | tr -d '[:space:]'
}

VERSION="$(read_gradle_property tianxuan.versionName)"
VERSION_CODE="$(read_gradle_property tianxuan.versionCode)"
[[ "$VERSION_CODE" =~ ^[0-9]+$ && "$VERSION_CODE" -gt 0 ]] \
  || die "tianxuan.versionCode 必须为正整数，实际为 \"$VERSION_CODE\""

WITH_APK=0
PUBLISH=0
for arg in "$@"; do
  case "$arg" in
    --apk)     WITH_APK=1 ;;
    --publish) WITH_APK=1; PUBLISH=1 ;;
    --*)       die "未知参数 $arg" ;;
    -*)
             die "未知参数 $arg（版本号必须形如 0.21.0，不要带 v 前缀）" ;;
    *)
      # 兼容旧调用方式：./release.sh 0.21.0 --apk
      if [[ "$arg" != "$VERSION" ]]; then
        die "传入版本号 \"$arg\" 与 gradle.properties 的 tianxuan.versionName=\"$VERSION\" 不一致。
     版本号唯一真源是 gradle.properties；请改那里后重跑，不要在这里传。"
      fi
      ;;
  esac
done

TAG="v${VERSION}"
# 所有构建都产出 release 变体：debug 变体带 applicationIdSuffix=.debug，
# 包名与正式包不同，用户无法覆盖安装，装两个还会互相抢「无障碍/输入法」等权限。
# 过去本脚本只调 assembleDebug，服务器上分发的一直是 debug 包。
BUILD_VARIANT="release"

step "版本与目标"
echo "版本号    : ${VERSION}（真源 gradle.properties）"
echo "versionCode: ${VERSION_CODE}"
echo "tag        : ${TAG}"
echo "构建变体   : ${BUILD_VARIANT}"

step "环境检查"
export JAVA_HOME="${JAVA_HOME:-/opt/jdk/jdk-25.0.4.1+1}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
command -v java >/dev/null || die "缺少 java，请先执行 source setup-env.sh"
java -version 2>&1 | head -1
gh auth status >/dev/null 2>&1 || die "gh 未登录"

step "工作区必须干净"
# 发布脚本自己做 git add -A 会把本地产物（keystore.properties、build 残留、
# 临时脚本）一并带进发布提交，且此时 tag 已打、内容难追。改为要求先自行提交。
if [[ -n "$(git status --porcelain)" ]]; then
  git status --short | head -20
  die "工作区有未提交变更。发布前请先自行 commit；本脚本不再代为 git add -A。"
fi

step "静态校验：架构棘轮 + 星象不变量 + 社区坐标一致性 + 迁移链连续性"
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
  -q || die "架构或单测未通过，中止发布"

# 运维脚本的守卫。清单脚本决定了「客户端拿到哪个包」，
# 排序错一次就会把旧包推成 latest，且不抛异常、不留日志——只能靠测试拦。
python3 ops/apkmanifest_test.py >/dev/null || die "apkmanifest 自测未通过"
python3 ops/gen_manifest_test.py >/dev/null || die "清单生成自测未通过"
echo "运维脚本自测通过"

step "构建 ${BUILD_VARIANT} APK"
./gradlew --no-daemon "assemble${BUILD_VARIANT^}" || die "APK 构建失败"

APK_PATH="app/build/outputs/apk/${BUILD_VARIANT}/tianxuan-v${VERSION}-${BUILD_VARIANT}.apk"
[[ -f "$APK_PATH" ]] || die "未找到构建产物：$APK_PATH（app/build.gradle.kts 里的输出命名若变更，此处需同步）"

step "校验产物身份"
# 不信任 Gradle 声明的版本，直接读 APK 里的 AndroidManifest.xml。
# 两者一旦不符（例如输出文件名用了别处的版本号），发出去的就是「标签与包不符」的
# 发布，用户装到的是旧版却被告知已升级。
APK_IDENTITY=$(python3 ops/apkmanifest.py "$APK_PATH") \
  || die "无法解析 APK 清单：$APK_PATH"
echo "$APK_IDENTITY"
APK_VERSION=$(printf '%s' "$APK_IDENTITY" | sed -E 's/.*versionName=([^ ]*).*/\1/')
APK_CODE=$(printf '%s' "$APK_IDENTITY" | sed -E 's/.*versionCode=([^ ]*).*/\1/')

# 去掉构建类型后缀（release 变体无后缀，保留判定以防将来加 -dev）
APK_VERSION_BASE="${APK_VERSION%%[-+]*}"
[[ "$APK_VERSION_BASE" == "$VERSION" ]] \
  || die "产物 versionName=$APK_VERSION_BASE 与目标 $VERSION 不符，中止发布"
[[ "$APK_CODE" == "$VERSION_CODE" ]] \
  || die "产物 versionCode=$APK_CODE 与 gradle.properties 的 $VERSION_CODE 不符，中止发布"

APK_SHA=$(sha256sum "$APK_PATH" | cut -d' ' -f1)
echo "sha256: $APK_SHA"
# 服务器端 gen-manifest.sh 独立复算一次；两者不一致说明传输或存储有问题，
# 那时客户端按 sha256 校验下载必然失败。不等就中止，别发一个坏包。
echo "${APK_SHA}  $(basename "$APK_PATH")" > "${APK_PATH}.sha256"

step "推送源码与 tag"
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  die "tag ${TAG} 已存在。版本号已发布过——请先在 gradle.properties 里提升版本号。"
fi
git push origin main || die "推送 main 失败"
git tag -a "$TAG" -m "天玄 ${VERSION}"
git push origin "$TAG" || die "推送 tag 失败"

# ---- 生成更新说明（服务器清单的 notes_file 会指向它，缺了「更新说明」就是空的）
RELEASE_NOTES_FILE="$ROOT/${RELEASE_NOTES_FILENAME}"
PREV_TAG=$(git tag --sort=-version:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sed -n '2p' || true)
{
  echo "## 天玄 ${VERSION}"
  echo
  if [[ -n "$PREV_TAG" ]]; then
    echo "> 本版本包含自 \`${PREV_TAG}\` 起的全部变更"
  fi
  echo
  git log --pretty=format:"- %s (%h)" ${PREV_TAG:+"${PREV_TAG}.."}HEAD | sed -n '1,60p'
  echo
  echo "---"
  echo "**SHA-256 校验**"
  echo '```'
  cat "${APK_PATH}.sha256"
  echo '```'
} > "$RELEASE_NOTES_FILE"

step "创建 GitHub Release"
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release ${TAG} 已存在，跳过创建"
else
  gh release create "$TAG" "$APK_PATH" "${APK_PATH}.sha256" "$RELEASE_NOTES_FILE" \
    --title "天玄 ${VERSION}" \
    --notes-file "$RELEASE_NOTES_FILE" || die "创建 Release 失败"
fi

if [[ "$PUBLISH" == "1" ]]; then
  step "同步到分发服务器 ${DIST_SSH}"
  ssh "$DIST_SSH" "install -d -m 755 '${DIST_BASE}/${VERSION}'" || die "服务器建目录失败"

  if [[ -d assets/plugins ]]; then
    scp -r assets/plugins/. "${DIST_SSH}:${DIST_BASE}/${VERSION}/plugins/" 2>/dev/null \
      || ssh "$DIST_SSH" "install -d -m 755 '${DIST_BASE}/${VERSION}/plugins'"
  fi

  # 上传到 .incoming 后校验 sha256 再原子改名：
  # 直接 scp 到目标路径时，传一半断线会留下一个大小不完整的 APK，
  # 而清单若已刷新，客户端就会拉到坏包且无法安装。
  REMOTE_TMP="${DIST_BASE}/.incoming/${VERSION}-$(basename "$APK_PATH")"
  ssh "$DIST_SSH" "install -d -m 755 '${DIST_BASE}/.incoming'"
  scp "$APK_PATH" "$RELEASE_NOTES_FILE" "${DIST_SSH}:${REMOTE_TMP}" || die "APK 上传失败"
  ssh "$DIST_SSH" "install -d -m 755 '${DIST_BASE}/${VERSION}' && \
    mv -f '${REMOTE_TMP}' '${DIST_BASE}/${VERSION}/' && \
    cd '${DIST_BASE}/${VERSION}' && \
    echo '${APK_SHA}  $(basename "$APK_PATH")' | sha256sum -c - || {
      echo '服务器侧 SHA-256 校验失败，已中止（未改动 current 指向）' >&2; exit 1; }" \
    || die "服务器侧 SHA-256 校验失败"

  # current 是符号链接目标，过去用 `install -d current` 先造目录，
  # 再 `ln -sfn` 只会把链接建到目录里面去，形成 /current/current。
  # 正确做法是 ln -sfnT：-T 表示把目标当普通文件替换，而不是当成目录。
  ssh "$DIST_SSH" "ln -sfnT '${DIST_BASE}/${VERSION}' '${DIST_BASE}/current' && \
    chmod 755 '${DIST_BASE}' && \
    echo 'current -> ' \$(readlink '${DIST_BASE}/current')"

  step "刷新服务器清单"
  # 路径与 ops/DEPLOY.md 的对应表一致：gen-manifest.sh 装在 /usr/local/bin/gen-tianxuan-manifest.sh。
  # 路径写错时 ssh 会静默失败（|| 落到 echo），清单停留在旧版本，
  # 用户点「检查更新」看到的是上一个版本——所以这里显式区分成功与失败。
  ssh "$DIST_SSH" "TIANXUAN_DIST='${DIST_BASE}' TIANXUAN_APKMANIFEST='/usr/local/share/tianxuan/apkmanifest.py' \
    /usr/local/bin/gen-tianxuan-manifest.sh" \
    || die "清单刷新失败：请确认服务器已部署 gen-tianxuan-manifest.sh（见 ops/DEPLOY.md）"

  step "公网自检"
  # 必须走客户端真实使用的入口：APP 内 UpdateSourceConfig.DEFAULT_BASE_URL
  # 是 https://<IP>，请求路径带 /tx-update 前缀（nginx 反代到 127.0.0.1:8443）。
  # 过去这里默认打 http://<IP>:8443，但 8443 只监听在本机、且腾讯云安全组
  # 未放行该端口，公网必然连不上——于是每次带 --publish 的发布都在最后一步
  # 中止，而此时 tag、Release、APK 都已落地，current 也已指向新版，
  # 留下「发布成功但脚本报失败」的假象，且没有任何回滚。
  # 端口通不通由腾讯云安全组决定，不能作为发布成败的判据；
  # 自检只验证「客户端会用的那条路」是否通。
  BASE_URL="${TIANXUAN_DIST_URL:-https://${DIST_SSH##*@}/tx-update}"
  curl -fsS --max-time 15 "${BASE_URL}/healthz" >/dev/null || die "/healthz 不通"
  echo "/healthz 正常"

  # 用 JSON 解析而不是 grep 字面量。服务器输出的是 json.dumps 默认格式，
  # 键值之间有空格（{"version": "0.21.0"}），脚本原先 grep '"version":"X"'
  # 一律匹配不上——自检永远失败，且失败点在发布已完成之后。
  # 用解析器比对字段，格式变化（空格、缩进、字段顺序）都不会再误判。
  LATEST_JSON="$(curl -fsS --max-time 15 "${BASE_URL}/latest")" \
    || die "/latest 请求失败"
  REMOTE_VERSION="$(printf '%s' "$LATEST_JSON" | python3 -c \
    'import json,sys; print(json.load(sys.stdin).get("version",""))' 2>/dev/null)" \
    || die "/latest 返回的不是合法 JSON：${LATEST_JSON:0:200}"
  [[ "$REMOTE_VERSION" == "$VERSION" ]] \
    || die "服务器清单里的最新版本是 '${REMOTE_VERSION}'，不是 ${VERSION}，客户端会拉到旧包"
  echo "/latest 已指向 ${VERSION}"

  # 光验证清单不够：清单说有这版、实际下载 404 或拿到 HTML 错误页，
  # 用户点「立即更新」照样失败。Range 请求返回 206 既验证了文件存在，
  # 也验证了断点续传可用（38MB 的包在移动网络下几乎必须）。
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 \
    -H 'Range: bytes=0-1023' "${BASE_URL}/apk/${VERSION}")
  [[ "$HTTP_CODE" == "206" ]] \
    || die "/apk/${VERSION} Range 请求返回 ${HTTP_CODE}，期望 206（APK 不可下载或不支持续传）"
  echo "/apk/${VERSION} Range 返回 206，断点续传可用"

  # 客户端按清单里的 sha256 校验下载，服务器上的包必须与清单登记的一致。
  REMOTE_SHA="$(printf '%s' "$LATEST_JSON" | python3 -c \
    'import json,sys; print(json.load(sys.stdin).get("sha256",""))' 2>/dev/null)"
  [[ -n "$REMOTE_SHA" ]] || die "清单里没有 sha256 字段，客户端无法校验下载"
  [[ "$REMOTE_SHA" == "$APK_SHA" ]] \
    || die "清单登记的 sha256=${REMOTE_SHA} 与本机构建产物 ${APK_SHA} 不一致，客户端会拒绝安装"
  echo "清单 sha256 与本机构建产物一致"
  echo "提醒：本次未改动任何既有服务（fusion-gateway / logview / st-rotator / st-auth / nginx）"
fi

step "完成"
gh release view "$TAG" --json url --jq .url
rm -f "$RELEASE_NOTES_FILE"
echo "版本 ${VERSION} 发布完毕（versionCode ${VERSION_CODE}）"