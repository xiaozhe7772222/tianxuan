#!/usr/bin/env bash
# 把 /opt/tianxuan-offline 下已下载完成的离线包软链进 dist 的 plugins 目录。
# 幂等：已存在的正确链接会被跳过，失效链接会被重建。绝不动 plugins 之外的任何文件。
set -euo pipefail
OFFLINE="${TIANXUAN_OFFLINE:-/opt/tianxuan-offline}"
DIST="${TIANXUAN_DIST:-/opt/tianxuan-dist}"
PDIR="$DIST/plugins"
mkdir -p "$PDIR"
added=0
for f in "$OFFLINE"/*.txplugin; do
  [ -e "$f" ] || continue
  name="$(basename "$f")"
  link="$PDIR/$name"
  if [ -L "$link" ] && [ "$(readlink -f "$link")" = "$(readlink -f "$f")" ]; then
    continue
  fi
  ln -sfn "$f" "$link"
  added=$((added + 1))
done
# -xtype f：跟随软链后仍是普通文件，即排除悬空链接。-type l 会把悬空的一项
# 也算成插件，报出的数量比实际可下载的多。
total="$(find "$PDIR" -maxdepth 1 -type l -xtype f -name '*.txplugin' | wc -l)"
# 只统计**可读**的包：pdir 里是软链，离线包被清理或下载失败时有链接会悬空。
# 直接喂给 os.path.getsize 会抛 FileNotFoundError，而本脚本是 set -euo pipefail，
# 于是「补软链」这件已经成功的事会以失败收场，误导排障。跳过不可读项即可。
# 与 tianxuan_dist.py 的 _serve_plugins 保持一致：那边也用 os.path.isfile 过滤。
bytes="$(python3 -c '
import os, sys
d = sys.argv[1]
print(sum(os.path.getsize(os.path.join(d, n))
          for n in os.listdir(d)
          if n.endswith(".txplugin") and os.path.isfile(os.path.join(d, n))))
' "$PDIR")"
echo "linked_new=$added plugins=$total bytes=$bytes"
