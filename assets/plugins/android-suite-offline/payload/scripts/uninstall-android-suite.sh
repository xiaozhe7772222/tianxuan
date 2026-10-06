#!/bin/sh
set -eu

TOOL_DIR="${TIANXUAN_TOOL_DIR:?missing TIANXUAN_TOOL_DIR}"
rm -f /opt/tianxuan/bin/java /opt/tianxuan/bin/javac /opt/tianxuan/bin/gradle /opt/tianxuan/bin/cmake /opt/tianxuan/bin/ninja /opt/tianxuan/bin/flutter /opt/tianxuan/bin/dart
rm -rf "$TOOL_DIR"
rm -rf /opt/android-sdk /opt/gradle-8.14.2 /opt/tianxuan/toolchains/android /opt/flutter
rm -f /root/.gradle/init.d/tianxuan-android-ndk.gradle
