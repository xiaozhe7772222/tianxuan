#!/bin/sh
set -e

TOOL_DIR="${TIANXUAN_TOOL_DIR:-/opt/tianxuan/tools/cc-switch}"
echo "[*] Uninstalling CC-Switch Agent Hub..."
rm -f "$TOOL_DIR/bin/cc-switch-daemon"
rm -f "$TOOL_DIR/lib/cc-switch-server"
echo "[+] CC-Switch Agent Hub uninstalled."
