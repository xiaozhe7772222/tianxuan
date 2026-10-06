#!/usr/bin/env bash
# 天玄 · 生成更新清单
#
# 扫描 ${TIANXUAN_DIST}/<版本>/ 下的 APK，写出 manifest.json。
# 幂等：可重复执行；内容无变化时不改动文件。
set -uo pipefail

DIST="${TIANXUAN_DIST:-/opt/tianxuan-dist}"
MANIFEST="$DIST/manifest.json"
APKMANIFEST="${TIANXUAN_APKMANIFEST:-/usr/local/share/tianxuan/apkmanifest.py}"

[[ -d "$DIST" ]] || { echo "dist 目录不存在: $DIST" >&2; exit 1; }
[[ -f "$APKMANIFEST" ]] || { echo "缺少 APK 解析模块: $APKMANIFEST" >&2; exit 1; }

python3 - "$DIST" "$MANIFEST" "$APKMANIFEST" <<'PY'
import hashlib, json, os, re, subprocess, sys

dist, out, apkmod_path = sys.argv[1], sys.argv[2], sys.argv[3]

sys.path.insert(0, os.path.dirname(apkmod_path))
import importlib.util
spec = importlib.util.spec_from_file_location("apkmanifest", apkmod_path)
apkmanifest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apkmanifest)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def newest(path):
    ts = os.path.getmtime(path)
    return subprocess.run(
        ["date", "-u", "-d", "@%d" % int(ts), "+%Y-%m-%dT%H:%M:%SZ"],
        capture_output=True, text=True).stdout.strip()


def ver_key(v):
    return [int(x) for x in re.findall(r"\d+", v)[:3]] or [0]


def rel_version(v):
    """清一版名：0.20.0-debug -> 0.20.0（后缀是构建类型，不属于版本号）"""
    return re.split(r"[-+]", v, 1)[0]


versions = []
app_identity = {}
for entry in sorted(os.listdir(dist)):
    d = os.path.join(dist, entry)
    if not os.path.isdir(d):
        continue
    apks = [f for f in sorted(os.listdir(d)) if f.lower().endswith(".apk")]
    if not apks:
        continue
    apk = apks[0]
    apk_path = os.path.join(d, apk)

    # 版本号与包名一律以 APK 实际内容为准，不从目录名反推：
    # 目录名是人写的，可能与构建产物不一致；versionCode 尤其不能猜。
    pkg, vname, vcode = apkmanifest.apk_identity(apk_path)
    version = rel_version(vname) if vname else entry
    app_identity = {"package": pkg, "versionName": version}

    versions.append({
        "version": version,
        "tag_name": "v" + version,
        "name": "天玄 " + version,
        # 读不到 versionCode 时留 0，而不是拿版本号拼一个假值：
        # 客户端目前按 versionName 比对，此字段仅供外部系统参考。
        "versionCode": int(vcode) if vcode.isdigit() else 0,
        "apk": "%s/%s" % (entry, apk),
        "apkName": apk,
        "size": os.path.getsize(apk_path),
        "sha256": sha256(apk_path),
        "published_at": newest(d),
        "notes_file": "%s/RELEASE_NOTES.md" % entry,
    })

versions.sort(key=lambda v: ver_key(v["version"]), reverse=True)

manifest = {
    "app": {"package": app_identity.get("package", ""),
            "versionName": app_identity.get("versionName", "")},
    "latest": versions[0]["tag_name"] if versions else "",
    "versions": versions,
}

if os.path.exists(out):
    try:
        if json.load(open(out, encoding="utf-8")) == manifest:
            print("清单无变化")
            raise SystemExit(0)
    except (ValueError, OSError):
        pass

with open(out, "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
print("清单已更新：%d 个版本，最新 %s" % (len(versions), manifest["latest"] or "—"))
for v in versions:
    print("  %-12s %10d  versionCode=%-5d %s"
          % (v["version"], v["size"], v["versionCode"], v["sha256"][:16] + "…"))
PY