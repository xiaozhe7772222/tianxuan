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
total="$(find "$PDIR" -maxdepth 1 -type l -name '*.txplugin' | wc -l)"
bytes="$(python3 -c 'import os,sys; d=sys.argv[1]; print(sum(os.path.getsize(os.path.join(d,n)) for n in os.listdir(d) if n.endswith(".txplugin")))' "$PDIR")"
echo "linked_new=$added plugins=$total bytes=$bytes"
