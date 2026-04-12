# 网易云点歌 Android App

这是原 AstrBot 插件的原生 Android 实现，功能完整对应插件的所有核心能力。

## ✨ 功能

| 功能 | 插件对应 | 说明 |
|------|---------|------|
| 关键词搜索歌曲 | `search_and_show` | 搜索结果列表，点击选歌 |
| 歌曲详情展示 | `play_selected_song` | 封面、歌手、专辑、时长 |
| 内置音频播放器 | `Record(file=audio_url)` | ExoPlayer，含进度条、播放/暂停 |
| 后台播放 & 通知栏控制 | 无 (原插件不支持) | Media3 `MediaSessionService` |
| 实时滚动歌词 | `_render_lyrics_image` | LRC 解析 + 高亮当前行 |
| 设置页面 | `_conf_schema.json` | API 地址、音源、各功能开关 |

## 🏗️ 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **网络**：Retrofit 2 + OkHttp 4
- **图片**：Coil
- **音频**：Media3 / ExoPlayer
- **架构**：MVVM + Repository

## 📁 项目结构

```
android/
├── app/
│   └── src/main/
│       ├── java/com/example/neteasemusic/
│       │   ├── data/
│       │   │   ├── api/
│       │   │   │   ├── NeteaseApiService.kt   # Retrofit 接口
│       │   │   │   └── RetrofitClient.kt      # OkHttp 客户端工厂
│       │   │   ├── model/
│       │   │   │   └── Models.kt              # 所有数据类
│       │   │   └── repository/
│       │   │       ├── MusicRepository.kt     # 音乐 API + LRC 解析
│       │   │       └── SettingsRepository.kt  # SharedPreferences 封装
│       │   ├── ui/
│       │   │   ├── search/
│       │   │   │   ├── SearchScreen.kt
│       │   │   │   └── SearchViewModel.kt
│       │   │   ├── player/
│       │   │   │   ├── PlayerScreen.kt
│       │   │   │   ├── PlayerViewModel.kt
│       │   │   │   └── LyricsView.kt
│       │   │   ├── settings/
│       │   │   │   ├── SettingsScreen.kt
│       │   │   │   └── SettingsViewModel.kt
│       │   │   └── theme/
│       │   │       └── Theme.kt
│       │   ├── MainActivity.kt
│       │   └── PlaybackService.kt
│       ├── AndroidManifest.xml
│       └── res/
├── gradle/libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

## ⚙️ 配置与运行

### 前置条件

1. 自行部署 [NeteaseCloudMusicApi Enhanced](https://github.com/neteasecloudmusicapienhanced/api-enhanced)，确保手机可访问（**不能用 `127.0.0.1`**，需用局域网 IP 或公网地址）。
2. 安装 [Android Studio](https://developer.android.google.cn/studio)（推荐 Hedgehog 或更新版本）。

### 构建步骤

1. 用 Android Studio 打开 `android/` 目录。
2. 等待 Gradle 同步完成。
3. 在设备或模拟器上运行（minSdk = 26 / Android 8.0）。
4. 首次启动后进入「**设置**」页面，填写正确的 API 地址。

## 🔑 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 请求音乐 API |
| `FOREGROUND_SERVICE` | 后台音频播放 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ 前台服务类型声明 |
| `POST_NOTIFICATIONS` | 播放通知栏（Android 13+） |
| `usesCleartextTraffic` | 支持 `http://` API 地址（局域网部署） |

## 📝 逻辑对应关系

| Python 插件 | Android App |
|------------|------------|
| `NeteaseMusicAPI.search_songs()` | `MusicRepository.searchSongs()` |
| `NeteaseMusicAPI.get_song_details()` | `MusicRepository.getSongDetail()` |
| `NeteaseMusicAPI.get_audio_url()` + `_extract_match_audio_url()` | `MusicRepository.getAudioUrl()` + `AudioUrlResponse.resolvedUrl` |
| `NeteaseMusicAPI.get_song_lyrics()` | `MusicRepository.getLyrics()` |
| `_clean_lyrics_text()` + `_is_non_lyric_line()` | `MusicRepository.parseLrc()` + `isNonLyricLine()` |
| `_render_lyrics_image()` | `LyricsView` 实时滚动（无需渲染图片） |
| `_conf_schema.json` | `SettingsRepository` + `SettingsScreen` |
| `waiting_users` / `song_cache` | `ViewModel` 状态管理 |
