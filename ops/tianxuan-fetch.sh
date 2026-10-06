#!/usr/bin/env bash
# 天玄离线插件包下载器
# 由 systemd tianxuan-fetch.service 承载；与既有服务完全隔离。
set -uo pipefail

DEST="/opt/tianxuan-offline"
MANIFEST="/tmp/tianxuan-manifest.tsv"
LOG="/var/log/tianxuan-fetch.log"

mkdir -p "$DEST"
: > "$LOG"

fail=0
while IFS=$'\t' read -r size name url; do
  [ -z "${url:-}" ] && continue
  target="$DEST/$name"
  echo "[$(date -Is)] START $name ($size bytes)" >> "$LOG"

  # 断点续传；--limit-rate 限速以免抢占既有业务带宽
  for attempt in 1 2 3 4 5; do
    wget -c --limit-rate=8M --tries=3 --timeout=60 \
         --user-agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36" \
         -O "$target" "$url" >> "$LOG" 2>&1
    rc=$?
    actual=$(stat -c %s "$target" 2>/dev/null || echo 0)
    if [ "$rc" -eq 0 ] && [ "$actual" -eq "$size" ]; then
      echo "[$(date -Is)] DONE $name ($actual bytes, attempt $attempt)" >> "$LOG"
      break
    fi
    echo "[$(date -Is)] RETRY $name rc=$rc got=$actual want=$size (attempt $attempt)" >> "$LOG"
    sleep 10
  done

  actual=$(stat -c %s "$target" 2>/dev/null || echo 0)
  if [ "$actual" -ne "$size" ]; then
    echo "[$(date -Is)] FAIL $name got=$actual want=$size" >> "$LOG"
    fail=$((fail+1))
  fi
done < "$MANIFEST"

echo "[$(date -Is)] ALL DONE, failures=$fail" >> "$LOG"
exit 0
