#!/bin/sh
# 一键编译音乐播放器（Git Bash / Linux）
set -e
cd "$(dirname "$0")"
export JAVA_HOME="D:/leon/SimpleTextEditor/toolchain/jdk17"
export ANDROID_HOME="D:/leon/SimpleTextEditor/toolchain/android-sdk"
GRADLE="D:/leon/SimpleTextEditor/toolchain/gradle-8.13/bin/gradle"

"$GRADLE" assembleRelease assembleDebug --no-daemon --console=plain "$@"
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/音乐播放器.apk
cp app/build/outputs/apk/debug/app-debug.apk dist/音乐播放器-debug.apk
echo "BUILD OK -> dist/音乐播放器.apk"
