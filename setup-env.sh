#!/usr/bin/env bash
# 天玄 (TianXuan) 编译环境
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk

# Gradle 9.7 daemon 需要 JDK 25（gradle/gradle-daemon-jvm.properties: toolchainVersion=25）
export JAVA_HOME=/opt/jdk/jdk-25.0.4.1+1

# Kotlin/Java 编译 toolchain 用 JDK 17（build-logic KotlinAndroid.kt: jvmToolchain(17)）
export JDK17_HOME="${HOME}/.sdkman/candidates/java/17.0.14-jbr"

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g"
