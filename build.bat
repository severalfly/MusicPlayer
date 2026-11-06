@echo off
rem 一键编译音乐播放器
setlocal
set JAVA_HOME=D:\leon\SimpleTextEditor\toolchain\jdk17
set ANDROID_HOME=D:\leon\SimpleTextEditor\toolchain\android-sdk
set GRADLE=D:\leon\SimpleTextEditor\toolchain\gradle-8.13\bin\gradle.bat

cd /d %~dp0
call %GRADLE% assembleRelease assembleDebug --no-daemon --console=plain %*
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)
if not exist dist mkdir dist
copy /y app\build\outputs\apk\release\app-release.apk dist\音乐播放器.apk >nul
copy /y app\build\outputs\apk\debug\app-debug.apk dist\音乐播放器-debug.apk >nul
echo.
echo BUILD OK -^> dist\音乐播放器.apk
