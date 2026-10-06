#!/bin/sh
set -eu

test -x /opt/tianxuan/bin/rtk || { echo "rtk binary is missing or not executable" >&2; exit 1; }
/opt/tianxuan/bin/rtk --version || exit 1
