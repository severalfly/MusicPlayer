# 音乐播放器（MusicPlayer）

Android 8.0+ 本地音乐播放器，纯本地、无广告、不联网、不申请存储权限（走系统文件选择器）。

## 安装包

可直接在 [Releases](https://github.com/severalfly/MusicPlayer/releases) 下载 `MusicPlayer-v1.0.0.apk`，或从本仓库 `dist/` 取：

- `dist/音乐播放器.apk` —— 正式版（已用 debug 签名，可直接安装）
- `dist/音乐播放器-debug.apk` —— 调试版

拷到手机上点击安装即可（需允许安装未知来源应用）。

## 功能

- **选文件夹**：选一个目录，递归扫描里面的音频（最多 5 层、500 首），按文件名排序
- **选文件**：直接挑一首歌播放，同目录的其它音频自动加入播放列表
- **列表播放**：点列表任意一首即播，支持上一首 / 下一首 / 拖动进度条
- **三种播放模式**（点模式按钮循环切换）：
  - 顺序循环：播完一首自动下一首，列表末尾回到开头
  - 随机播放：随机挑一首不重复的
  - 单曲循环：当前这首无限重复
- **后台播放**：退出界面继续播；通知栏有 播放/暂停/切歌 控制条
- **媒体按键**：耳机线控、蓝牙按键、锁屏控制都响应（上一首/播放暂停/下一首/快进快退±10 秒）
- 支持格式：mp3 / wav / flac / ogg / m4a / aac / opus / mid / amr / 3gp（Android 系统解码器所支持的格式）

## 使用说明

- **选择文件夹**：系统目录选择器里挑放音乐的目录，授权一次后记住
- **列表点歌**：直接播放
- **底部控制条**：当前歌名 · 播放模式、进度条（可拖）、上一首 / 播放暂停 / 下一首
- 播放中按返回键退出界面，音乐继续；通知栏可控制

## 项目结构

```
MusicPlayer/
├─ app/src/main/java/com/leon/musicplayer/
│  ├─ MainActivity.java   选文件夹/选文件、歌曲列表、底部控制条
│  ├─ MusicService.java   后台播放服务（MediaPlayer + MediaSession + 通知栏）
│  └─ Song.java           歌曲数据
├─ app/src/main/res/      界面布局与资源
├─ dist/                  编译好的 APK
└─ build.bat / build.sh   一键重新编译
```

## 重新编译

准备 JDK 17、Android SDK 34（build-tools 34.0.0）、Gradle 8.13，
设好 `JAVA_HOME` / `ANDROID_HOME`（或在 `local.properties` 里写 `sdk.dir`）后：

```bash
gradle assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`，也可以直接用 Android Studio 打开本目录编译。
`build.bat` / `build.sh` 是开发机上的一键脚本（硬编码了本机自包含工具链路径），换机器请用上面的命令。

## 已知限制

- 文件夹扫描是按文件扩展名识别音频，不含子文件夹超深层的文件（上限 5 层）
- 无均衡器、无歌词、无专辑封面（够用版）
- Android 13+ 首次启动会请求通知权限（用于后台播放控制条），拒绝也能正常播，只是没有通知栏控制

## 开源

- 许可证：[MIT](LICENSE)
- 纯本地应用：无广告、不联网、不申请存储权限、不收集任何数据
- 同系列另有 SimpleTextEditor（支持 Markdown 预览的文本编辑器）和 GitSyncApp（Git 自动同步客户端）
- 欢迎 Issue / PR，功能保持「够用就好」
