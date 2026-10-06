#!/bin/sh
set -eu

TOOL_DIR="${TIANXUAN_TOOL_DIR:?missing TIANXUAN_TOOL_DIR}"
COMPAT_ROOT="/opt/tianxuan/compat/x86_64"

rm -f /opt/tianxuan/bin/qemu-x86_64

rm -rf "$TOOL_DIR"
rm -rf "$COMPAT_ROOT"

echo "QEMU x86_64 user-mode 插件已卸载"
