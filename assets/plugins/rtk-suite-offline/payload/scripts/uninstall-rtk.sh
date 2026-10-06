#!/bin/sh
set -eu

TOOL_DIR="${TIANXUAN_TOOL_DIR:?missing TIANXUAN_TOOL_DIR}"

rm -f /opt/tianxuan/bin/rtk
rm -f "$TOOL_DIR/bin/rtk"
rm -rf /opt/tianxuan/data/rtk
echo "RTK 终端命令优化插件已卸载"
