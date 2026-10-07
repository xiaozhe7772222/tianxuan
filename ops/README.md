# 天玄 · 基础设施交接

本文件记录天玄（TaiXuan）的代码托管、发布链路与离线资源分发。
**首要约束：不破坏服务器上任何既有服务。**

---

## 一、GitHub

| 项 | 值 |
|---|---|
| 仓库 | `xiaozhe7772222/tianxuan` |
| 可见性 | PRIVATE |
| 默认分支 | `main` |

克隆：

```bash
git clone https://github.com/xiaozhe7772222/tianxuan.git
```

### 更新源是配置项，不是硬编码

应用内「检查更新」与「关于」页的仓库坐标读 `app/src/main/assets/update_source.properties`：

```properties
update.repo=xiaozhe7772222/tianxuan
update.baseUrl=https://124.222.37.253
update.manifestPrefix=/tx-update/
update.manifestUrl=https://124.222.37.253/tx-update/latest
update.fallbackUrls=
update.versionsUrl=https://124.222.37.253/tx-update/versions
update.repoUrl=https://github.com/xiaozhe7772222/tianxuan
```

改这几行即可换发布源，无需改 Kotlin 代码。
读取逻辑在 `core/network/.../UpdateSourceConfig.kt`（76 行），
清单解析与版本比对在同包 `UpdateManifestParser.kt`（88 行，18 项单测覆盖），
缺失或格式非法时逐项回落到 `UpdateSourceConfig` 的 `DEFAULT_*` 常量。

> 例外：`feature/custom_iteration` 刻意不依赖 `core:network`（避免破坏架构依赖白名单），
> 其 `TIANXUAN_OFFICIAL_REPO` 为本地常量。换仓库时这两处需同步。

> **为什么更新源是自建接口而不是 GitHub Releases API**：
> 仓库为 PRIVATE，未认证的 Releases API 请求返回 404（已实测），
> 而 APK 需要公开可下载。因此在自有服务器上实现了一个与 GitHub Releases
> 完全同构的清单服务（字段名一致：`tag_name`/`name`/`body`/`html_url`/
> `published_at`/`assets[].name`/`assets[].size`/`assets[].browser_download_url`），
> 客户端解析逻辑无需为自建源特殊化。GitHub API 作为兜底候选保留在候选列表末位。

---

## 二、发布链路

```bash
./release.sh <版本号> [--apk] [--publish]
```

| 参数 | 作用 |
|---|---|
| 无 | 构建 + 打 tag + 推送 + 创建 Release |
| `--apk` | 额外构建 APK 并附到 Release |
| `--publish` | 额外同步到分发服务器 |

流程固定为：**环境检查 → `architectureCheck` + `AstronomyTest` → 构建 → 提交打 tag →
推送 → 创建 Release →（可选）同步服务器**。

前两步任一失败即中止发布，不允许带病出包。

前置环境：

```bash
source ./setup-env.sh   # JDK 25 daemon / SDK / JDK 17 toolchain
```

### 架构棘轮

`architecture-policy.json` 的 `global.maxFileLines=400`，基线在 `.architecture-baseline.json`。
**基线只许下调，不许上调。** 改动文件使其超限时，先把该文件拆小，而不是抬高基线。

---

## 三、腾讯云服务器（124.222.37.253）

### 既有服务（不得触碰）

`fusion-gateway`（new-api 网关）、`logview`、`st-auth`、`st-rotator`、`nginx` —— 全部由 systemd 管理。

任何操作前后都应核对：

```bash
for s in fusion-gateway logview st-rotator st-auth nginx; do
  printf '%-16s %s\n' "$s" "$(systemctl is-active $s)"
done
```

### 新增路径（与既有目录隔离）

| 路径 | 用途 |
|---|---|
| `/opt/tianxuan-offline` | 6.44 GiB 离线插件包落盘 |
| `/opt/tianxuan-dist` | 对外分发目录，含 `0.20.0/`、`plugins/` |
| `/usr/local/share/tianxuan/tianxuan_dist.py` | 自建更新/分发服务 |
| `/usr/local/share/tianxuan/apkmanifest.py` | 纯标准库 AXML 解析（读 APK 版本号/包名） |
| `/usr/local/bin/tianxuan-fetch.sh` | 离线包下载器 |
| `/usr/local/bin/sync-tianxuan-plugins.sh` | 把已下载完成的包软链进 `dist/plugins` |
| `/usr/local/bin/gen-tianxuan-manifest.sh` | 扫描 dist 生成 `manifest.json` |
| `/etc/systemd/system/tianxuan-fetch.service` | 下载服务单元 |
| `/etc/systemd/system/tianxuan-dist.service` | 分发服务单元 |
| `/var/log/tianxuan-fetch.log` | 下载日志 |

### nginx 改动（唯一的既有配置改动）

只在 `/etc/nginx/sites-enabled/fusion-gateway` **新增一个 location**，
未修改任何既有 location：

```nginx
location ^~ /tx-update/ {
    # 末尾的 / 让 nginx 剥掉 /tx-update 前缀
    proxy_pass http://127.0.0.1:8443/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;          # 大文件直发，不落 nginx 缓冲
    proxy_request_buffering off;
    proxy_cache off;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
    proxy_set_header Host $host;
    proxy_set_header Range $http_range;      # Range 透传，断点续传必需
    proxy_set_header If-Range $http_if_range;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 1m;
}
```

8443 端口未在腾讯云安全组放行，仅监听回环，不对外暴露。
备份统一放 `/root/nginx-backups/`（**不可放 `sites-enabled/`**，
否则会被 nginx 扫到并触发 `duplicate default server`）。

### 不干扰既有服务的措施

`tianxuan-fetch.service` 的隔离配置：

```ini
Nice=19
IOSchedulingClass=idle
CPUWeight=10
IOWeight=10
MemoryMax=256M
```

`tianxuan-dist.service` 额外加 `ProtectSystem=strict` + `NoNewPrivileges=yes`
+ `ReadWritePaths` 限定只能写日志目录。

叠加脚本内 `wget --limit-rate=8M` 限速。实测 6.44 GiB 全量下载与分发期间
`fusion-gateway`/`logview`/`st-rotator`/`st-auth`/`nginx` 保持 active，
负载峰值 0.08，可用内存 2912M。

### 运维命令

```bash
systemctl status  tianxuan-fetch tianxuan-dist
systemctl restart tianxuan-fetch     # 断点续传，重跑安全
sync-tianxuan-plugins# 补齐软链，幂等
gen-tianxuan-manifest                # 刷新 manifest.json，幂等
python3 /usr/local/share/tianxuan/apkmanifest_test.py   # AXML 解析器自测（7 项）
python3 /usr/local/share/tianxuan/gen_manifest_test.py  # 清单生成自测（6 组）
tail -f /var/log/tianxuan-fetch.log
grep -E 'DONE|FAIL|ALL DONE' /var/log/tianxuan-fetch.log
journalctl -u tianxuan-dist -f
```

发布前另有一道**产物洁净度守卫自测**，在仓库里跑（需要 Android SDK）：

```bash
python3 ops/release_guard_test.py
# release_guard_test: OK（干净包放行、manifest 含组件的包被拦、
#                      dex 含类的包被拦、release.sh 守卫模式逐分支核对通过）
```

它覆盖三件事：① 用 `aapt2 link` 真编出一个含 LeakCanary 组件的 APK，
确认 release.sh 的守卫会拦下它；② 不含组件的干净包必须放行（守卫若「一律拦下」
等于没有守卫）；③ 从 `release.sh` 里**抠出那个真正被 grep 执行的模式**，
逐分支核对——注释行会被先剥掉，所以「把模式拼错一个字」「删掉某个分支」
「把整行注释掉」三类退化都会让它变红。无 SDK 时报 `SKIP` 并以 0 退出，
不误报失败。

### APK 版本号读取（apkmanifest.py）

服务器没有 aapt，也不该为读一个版本号去装整个 Android SDK，
因此用 `struct` + `zipfile` 直接解析二进制 AXML。三个已踩过的坑：

1. **UTF-8 标志位在 flags 的 bit 8（0x100）**。aapt2 生成的清单通常是
   UTF-16（`flags=0x0`），按 UTF-8 解会整张字符串表错位。
2. **chunk 之间要按 header 里的 `size` 整块跳过**，不能只跳 `headerSize`。
   字符串池与首个元素之间还有 `RES_XML_RESOURCE_MAP_TYPE(0x0180)`。
3. **`attributeStart` 相对 attrExt 起点（即 `off + headerSize`）**，
   不是相对 attrExt 末尾 —— 叠加 attrExt 长度会跳过首个属性，
   表现为 `versionCode` 恰好读不到而 `versionName` 正常。

属性名在字符串池里是**裸名**（`versionCode`），命名空间信息存在属性项的
`ns` 字段里，所以按短名比对是对的，不要去前缀。

版本号与包名一律以 APK 实际内容为准，**不从目录名反推** ——
此前 `versionCode` 曾用 `版本号×10` 拼出200这种假值，与 APK 真实的 29 不符。
读不到时留 0，不臆造。

```bash
python3 /usr/local/share/tianxuan/apkmanifest.py <apk>
# package=top.wkbin.tianxuan.debug versionName=0.20.0-debug versionCode=29
```

输出与 `aapt2 dump badging` 逐字段一致（非APK / 截断 / 损坏文件均返回空值不抛异常）。

---

## 四、自建更新服务（对外端点）

基址 `https://124.222.37.253/tx-update`，与 GitHub Releases 字段同构。

| 端点 | 用途 |
|---|---|
| `GET /tx-update/healthz` | 存活检查 |
| `GET /tx-update/latest` | 最新版清单（含 `assets[]`，GitHub 同构） |
| `GET /tx-update/versions` | 历史版本列表 |
| `GET /tx-update/apk/<version>` | APK 下载，支持 Range |
| `GET /tx-update/plugins` | 离线插件清单 |
| `GET /tx-update/plugin/<name>` | 插件下载，支持 Range |

已验证（`nginx -s reload` 回归后复测，2026-10-07）：

```
/healthz  → {"status":"ok","service":"tianxuan-dist"}
/latest   → tag_name=v0.20.0, versionCode=29,
            assets[0].browser_download_url = https://124.222.37.253/tx-update/apk/0.20.0
/apk/0.20.0 → 206, content-range: bytes 0-1023/51349736,
            content-type: application/vnd.android.package-archive
/plugins  → count=11, total_bytes=6911578380
/plugin/<11 个中文包名> → 全部 206 + 1024 字节
```

> `/latest` 返回的是**单个版本对象**（与 GitHub 的 `/releases/latest` 一致），
> `versions` 数组在 `/versions` 端点。

插件名覆盖了中文、`+`、`·`(U+00B7)、`、`(U+3001) 等极端字符，
11 个 URL 全部可下载 —— 说明百分号编码与 `Content-Disposition` 链路正确。

**路径安全**：`_within_dist()` 用 `abspath` 而非 `realpath`
（`realpath` 会把软链解到 `dist` 外从而误判越界），
显式拒绝绝对路径与 `..`，并要求 `.txplugin` 后缀。
已回归验证 `..`、绝对路径、非 `.txplugin` 三类输入均被拦截。

**代理前缀感知**：服务从 `X-Forwarded-Proto` / `X-Forwarded-Prefix` 推导
对外基址，systemd 单元里设 `TIANXUAN_BASE_PATH=/tx-update`、
`TIANXUAN_BASE_PROTO=https`，保证下发的 `browser_download_url` 是
带前缀的 https 绝对地址，可直接被客户端使用。

---

## 五、6.44 GiB 离线插件包

**状态：11/11 已下载完成，`failures=0`，字节数与 QQ 清单逐项精确一致。**

11 个 `.txplugin`，合计 `6911578380` 字节（6.44 GiB），arm64。

| 包 | 字节 |
|---|---|
| Rust系统与Android-JNI交叉编译链-1.0.0 | 530441917 |
| 现代包管理器-yarn-1.0.0 | 49901704 |
| Android全栈开发套件-单体离线包-1.0.9 | 3009295789 |
| 代码检索四件套-rg、fd、fzf、bat-1.0.0 | 11138636 |
| Python3核心运行基座-1.0.0 | 17164034 |
| Node.js核心运行时-1.0.0 | 61283397 |
| C、C++与NDK原生构建链-1.0.0 | 637157113 |
| Flutter跨平台开发环境-1.0.0 | 1205967688 |
| AI科学计算与C扩展编译库-1.0.0 | 118207075 |
| Android逆向分析与代码审计-1.0.0 | 162991149 |
| Android核心基础环境-1.0.0 | 1108029878 |

规格清单：`ops/offline-packages.json`（含 `name` / `size`，**已入库**，总字节 `total_bytes`）。
含直链的完整清单**不入库** —— 直链带 QQ 闪传的 `rkey` 会话凭证且14 天过期，
由 `ops/.gitignore` 排除，按下节「取链过程」重新生成即可。

### 完整性校验

下载器按字节数逐个比对，任一不符即重试（最多 5 轮，`wget -c` 断点续传）。
事后可再核一遍：

```bash
cd /opt/tianxuan-offline
while IFS=$'\t' read -r size name url; do
  printf '%-12s %s\n' "$(stat -c %s "$name" 2>/dev/null || echo MISSING)" "$name"
done < /tmp/tianxuan-manifest.tsv
```

### 直链来源与时效

直链来自 QQ 闪传分享页 `https://qfile.qq.com/q/x4LyndiNna`，鉴权靠 URL 内的 `rkey`。
**该分享 14 天后过期**，过期后需重新取链。重取步骤见 `天玄改造台账.md` 第三节。
直链支持 `Range`，中断后可续传。