#!/usr/bin/env python3
"""天玄 · 更新服务

对标 GitHub Releases API 的最小实现，只依赖 Python 标准库。
用途：仓库保持私有时，为应用内「检查更新」提供公开可读的版本清单与 APK 下载。

端点：
    GET /latest              → 与 AppUpdateManager 期望的字段同构
    GET /versions            → 历史版本列表
    GET /apk/<version>       → 下载 APK（支持 Range 断点续传）
    GET /healthz             → 健康检查

设计约束：
  - 只读。不提供上传/删除接口，出包由 release.sh 在服务器本地放置。
  - 清单从 /opt/tianxuan-dist/manifest.json 读取，每次请求实时重读，
    因此放入新包后无需重启服务。
  - 只读文件系统之外的任何路径；APK 必须落在 dist 目录内。
"""
import json
import os
import posixpath
import re
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIST = os.environ.get("TIANXUAN_DIST", "/opt/tianxuan-dist")
MANIFEST = os.path.join(DIST, "manifest.json")
PORT = int(os.environ.get("TIANXUAN_DIST_PORT", "8443"))
BIND = os.environ.get("TIANXUAN_DIST_BIND", "0.0.0.0")
BASE_PATH = os.environ.get("TIANXUAN_BASE_PATH", "")
BASE_PROTO = os.environ.get("TIANXUAN_BASE_PROTO", "http")
UA = "TianXuan-Dist/1.0"

MAX_JSON = 1 << 20  # 1 MiB


def load_manifest():
    try:
        with open(MANIFEST, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return {}
    except (OSError, ValueError) as exc:
        sys.stderr.write("manifest 读取失败: %s\n" % exc)
        return {}


def pick_apk(releases):
    """取最新版本里名字含 apk 的资产。"""
    for rel in releases:
        for asset in rel.get("assets", []):
            name = asset.get("name", "")
            if name.lower().endswith(".apk"):
                return rel, asset
    return None, None


class Handler(BaseHTTPRequestHandler):
    server_version = "TianXuanDist/1.0"
    protocol_version = "HTTP/1.1"

    # ---------- 工具 ----------

    def _send(self, code, body=b"", ctype="application/json; charset=utf-8", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        # 应用为 https 上下文时不会有 mixed-content 问题；明文端口下仅作提示
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD" and body:
            self.wfile.write(body)

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        if len(body) > MAX_JSON:
            body = b'{"error":"payload too large"}'
            code = 500
        self._send(code, body)

    def _fail(self, code, msg):
        self._json(code, {"message": msg, "retcode": code})

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (UA, fmt % args))

    def _base(self):
        """推导对外基址。

        反代到子路径（如 nginx `location ^~ /tx-update/` → `proxy_pass .../`）时，
        请求路径会被剥掉前缀，仅凭 Host 拼出的地址会缺前缀，导出的下载链接因此失效。
        故优先取 X-Forwarded-Prefix，其次读 TIANXUAN_BASE_PATH 环境变量。
        """
        host = self.headers.get("X-Forwarded-Host") or self.headers.get("Host", "127.0.0.1")
        prefix = self.headers.get("X-Forwarded-Prefix", "").strip()
        if not prefix:
            prefix = BASE_PATH.strip()
        prefix = "/" + prefix.strip("/") if prefix.strip("/") else ""
        proto = self.headers.get("X-Forwarded-Proto", "").strip() or BASE_PROTO
        if proto not in ("http", "https"):
            proto = "http"
        return "%s://%s%s" % (proto, host, prefix)

    # ---------- 路由 ----------

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = posixpath.normpath(urllib.parse.unquote(parsed.path))
        if path in ("/", "/index.html"):
            return self._serve_index()
        if path == "/healthz":
            return self._json(200, {"status": "ok", "service": "tianxuan-dist"})
        if path == "/latest":
            return self._serve_latest()
        if path == "/versions":
            return self._serve_versions()
        m = re.fullmatch(r"/apk/([0-9][0-9A-Za-z._+-]*)", path)
        if m:
            return self._serve_apk(m.group(1))
        if path == "/plugins":
            return self._serve_plugins()
        m = re.fullmatch(r"/plugin/([^/]+)", path)
        if m:
            return self._serve_plugin(m.group(1))
        return self._fail(404, "not found")

    # ---------- 处理 ----------

    def _serve_index(self):
        manifest = load_manifest()
        app = manifest.get("app", {})
        rel = manifest.get("latest", "")
        html = (
            "<!doctype html><html lang=zh-CN><head><meta charset=utf-8>"
            "<meta name=viewport content='width=device-width,initial-scale=1'>"
            "<title>天玄 更新服务</title><style>"
            "body{font:16px/1.7 system-ui,sans-serif;max-width:44rem;margin:3rem auto;padding:0 1.2rem;color:#1a1c1e}"
            "h1{font-size:1.5rem}code{background:#f1f3f5;padding:.15em .4em;border-radius:4px}"
            "li{margin:.4rem 0}a{color:#008cff}</style></head><body>"
            "<h1>天玄 TaiXuan · 更新服务</h1>"
            "<p>当前版本：<code>%s</code></p>"
            "<p>最新发布：<code>%s</code></p>"
            "<h2>端点</h2><ul>"
            "<li><code>GET /latest</code> — 版本清单</li>"
            "<li><code>GET /versions</code> — 历史版本</li>"
            "<li><code>GET /apk/&lt;version&gt;</code> — 下载 APK</li>"
            "<li><code>GET /healthz</code> — 健康检查</li>"
            "</ul></body></html>"
        ) % (app.get("versionName", "—"), rel or "—")
        self._send(200, html.encode("utf-8"), "text/html; charset=utf-8")

    def _serve_versions(self):
        manifest = load_manifest()
        self._json(200, manifest.get("versions", []))

    def _serve_latest(self):
        manifest = load_manifest()
        rel_tag = manifest.get("latest")
        if not rel_tag:
            return self._json(200, {
                "tag_name": "",
                "name": "",
                "body": "",
                "html_url": self._base() + "/",
                "published_at": "",
                "assets": [],
            })
        for rel in manifest.get("versions", []):
            if rel.get("tag_name") == rel_tag:
                out = dict(rel)
                out["html_url"] = self._base() + "/versions"
                ver = rel.get("version") or rel_tag.lstrip("v")
                # AppUpdateManager 从 assets[].browser_download_url 取包，
                # 字段名与 GitHub Releases 保持一致，便于两端同构。
                assets = []
                if rel.get("apk"):
                    assets.append({
                        "name": rel.get("apkName") or os.path.basename(rel["apk"]),
                        "size": rel.get("size"),
                        "sha256": rel.get("sha256"),
                        "browser_download_url": "%s/apk/%s" % (self._base(), ver),
                    })
                out["assets"] = assets
                # release body 用 notes 文件，便于「更新说明」直接展示
                notes = rel.get("notes_file")
                if notes:
                    path = self._within_dist(notes)
                    if path and os.path.isfile(path):
                        try:
                            with open(path, "r", encoding="utf-8") as f:
                                out["body"] = f.read(20000)
                        except OSError:
                            pass
                return self._json(200, out)
        return self._fail(404, "latest release not found in manifest")

    def _send_file(self, path, download_name, extra_headers=None):
        """按 Range 回传文件。Range / If-Range 均已由 nginx 透传。"""
        size = os.path.getsize(path)
        start, end = 0, size - 1
        status = 200
        rng = self.headers.get("Range")
        if rng and rng.startswith("bytes="):
            spec = rng[6:].split(",")[0].strip()
            m = re.fullmatch(r"(\d*)-(\d*)", spec)
            if m:
                g1, g2 = m.group(1), m.group(2)
                if g1:
                    start = int(g1)
                    if g2:
                        end = min(int(g2), size - 1)
                elif g2:
                    start = max(0, size - int(g2))
                if start > end or start >= size:
                    self.send_response(416)
                    self.send_header("Content-Range", "bytes */%d" % size)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                status = 206

        length = end - start + 1
        self.send_response(status)
        self.send_header("Content-Type", (extra_headers or {}).get("Content-Type") or "application/octet-stream")
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header(
            "Content-Disposition",
            "attachment; filename*=utf-8''%s" % urllib.parse.quote(download_name),
        )
        for k, v in (extra_headers or {}).items():
            if v and k != "Content-Type":
                self.send_header(k, v)
        if status == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        self.end_headers()
        if self.command == "HEAD":
            return
        with open(path, "rb") as f:
            f.seek(start)
            remaining = length
            while remaining > 0:
                chunk = f.read(min(256 * 1024, remaining))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return
                remaining -= len(chunk)

    def _within_dist(self, relative):
        """把相对路径解析为 dist 内的绝对路径，越界返回 None。

        用 abspath 而非 realpath：离线包以软链形式指向 /opt/tianxuan-offline，
        realpath 会解到 dist 之外而误判为越界。越界防护针对的是 `..` 与绝对路径，
        这两者都会在 abspath 阶段暴露。
        """
        if relative.startswith("/") or ".." in relative.split("/"):
            return None
        root = os.path.abspath(DIST)
        path = os.path.abspath(os.path.join(root, relative))
        if path == root or not path.startswith(root + os.sep):
            return None
        return path

    def _serve_plugin(self, filename):
        if "/" in filename or ".." in filename or not filename.endswith(".txplugin"):
            return self._fail(400, "bad plugin name")
        path = self._within_dist(os.path.join("plugins", filename))
        if path is None:
            self.log_message("plugin 越界: %r -> %r", filename, path)
            return self._fail(403, "forbidden")
        if not os.path.isfile(path):
            self.log_message("plugin 不存在: %r -> %r", filename, path)
            return self._fail(404, "plugin file missing")
        self._send_file(path, filename)

    def _serve_plugins(self):
        """离线插件包清单。文件名可直接用于 /plugin/<name> 下载。"""
        pdir = os.path.join(DIST, "plugins")
        items = []
        if os.path.isdir(pdir):
            for name in sorted(os.listdir(pdir)):
                p = os.path.join(pdir, name)
                if os.path.isfile(p) and name.endswith(".txplugin"):
                    items.append({
                        "name": name,
                        "size": os.path.getsize(p),
                        "download_url": "%s/plugin/%s" % (self._base(), urllib.parse.quote(name)),
                    })
        self._json(200, {"count": len(items),
                         "total_bytes": sum(i["size"] for i in items),
                         "plugins": items})

    def _serve_apk(self, version):
        manifest = load_manifest()
        target = None
        for rel in manifest.get("versions", []):
            ver = rel.get("version") or rel.get("tag_name", "").lstrip("v")
            if ver == version:
                target = rel
                break
        if target is None:
            return self._fail(404, "version not found")

        filename = target.get("apk")
        if not filename:
            return self._fail(404, "version has no apk")

        path = self._within_dist(filename)
        if path is None:
            return self._fail(403, "forbidden")
        if not os.path.isfile(path):
            return self._fail(404, "apk file missing")

        self._send_file(
            path,
            target.get("apkName") or os.path.basename(path),
            {"X-Apk-Sha256": target.get("sha256"), "Content-Type": "application/vnd.android.package-archive"},
        )


def main():
    srv = ThreadingHTTPServer((BIND, PORT), Handler)
    srv.daemon_threads = True
    sys.stderr.write("%s 监听 %s:%d，dist=%s\n" % (UA, BIND, PORT, DIST))
    sys.stderr.flush()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()


if __name__ == "__main__":
    main()