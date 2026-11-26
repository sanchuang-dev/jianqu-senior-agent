# 见趣 Launcher 视觉素材库

此目录保存原始设计素材，不属于 Android APK 资源目录。

| 源包 | 内容 | 用途 |
| --- | --- | --- |
| [jianqu-launcher-icons.zip](jianqu-launcher-icons.zip) | 11 个 3D 图标，PNG / WebP 与 preview.png | 当前 HOME 十入口及 SOS 的正式图标源 |
| [jianqu-home-icons.zip](jianqu-home-icons.zip) | 30 个 3D 图标，PNG / WebP、README 与 preview.jpg | 品牌、天气、语音及备用图标；保留早期版本 |

## 当前运行时映射

| 新图标源文件（webp/） | Android drawable 名称 |
| --- | --- |
| family-call.webp | jq_home_contacts |
| family-album.webp | jq_home_album |
| video.webp | jq_home_video |
| local-life.webp | jq_home_shopping |
| music-opera.webp | jq_home_music_opera |
| health.webp | jq_home_health |
| governance.webp | jq_home_governance |
| my-services.webp | jq_home_my_services |
| settings.webp | jq_home_settings |
| all-apps.webp | jq_home_all_apps |
| sos.webp | jq_home_alarm |

旧包仍供品牌蝴蝶、语音和七类天气图标使用；剩余图标保留备用，不重复打包至 APK。两个背景原始 WebP 已原样纳入运行时资源：jq_home_background.webp、jq_daylight_background.webp，故无需再复制归档。

两份 ZIP 均来自用户提供的 E:\tomz 素材，保留源包内 PNG、WebP 和预览。不要将整个 ZIP 或备用图片复制到 app/src/main/assets 或 res。按需采用 WebP 并保持业务入口语义。小暖立绘目前未提供。
