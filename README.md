# 闺蜜机 Launcher × 小暖｜Android 模拟原型

> 三创科技（SANCHUANG） ·「见趣」为**暂定**产品名 ·「小暖」为已确认的 AI 伙伴名
>
> 当前分支：JQ-003 本地模拟交互施工（基于已合并至 `dev` 的 JQ-002 原生 Launcher）。

## V0.1 一句话

这是**普通 Android 11+ 设备可安装的单 APK 演示玩具**，不是量产桌面或真正的养老 Agent。

- **Launcher**：品牌桌面；展示和真正打开 Android 系统允许发现的所有可启动已安装 App。
- **模拟设备订阅**：「我的闺蜜机」里选择虚构套餐、模拟开通当前设备；可选择同时给另外两台**虚构设备**模拟开通。
- **小暖**：同一位伙伴、三种说话风格（慈父 / 御姐 / 小妹），每种四条本地预设问答，标记为「模拟 AI」。
- **一个真实动作**：在小暖页面选择「帮我打开一个应用」，确认后通过 Android 官方 `LauncherApps` 启动实际安装的程序。
- **隐私与重置**：套餐状态存储在本机专用 SharedPreferences；会话**不落盘**，退出或退到后台清空；一键重置所有模拟设备。

**不做**真实 LLM、语音识别合成、云端、支付、账号、机构管理、远程控制、设备锁或医疗建议。所有订阅与 AI 回答都是 Mock，应用目录与启动动作才是真实本机操作。

## 构建

需要 JDK 17+、Android SDK Platform 35。一个工程，一个 APK：

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装后从 Android 的「默认应用 / 主屏幕应用」中手动选择「闺蜜机（演示）」，也可在系统设置中改回原桌面。桌面内「我的闺蜜机 · 设备服务」进入本地演示，不需要网络。

## 本项目的事实源

- [PRD V0.1](docs/product/PRD-v0.1.md)：范围、设备参考、品牌、原型验收
- [AGENTS.md](AGENTS.md)：三创仓库执行和分支约束
- [JQ-002 #2](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/2)：真实 Launcher 底座，已经进入 `dev`
- [JQ-003 #3](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/3)：本地模拟体验的任务合同
- [总里程碑 #1](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/1)：最终设备验收

## 验收边界

三创 Logo 已由用户提供的企业物料提取并适配。「见趣」暂定名称不构成最终品牌决策。供应商的 Android 11+、4GB+64GB 起、21.5–32 英寸可旋转竖屏是**参考配置**；系统 ROM、分辨率、旋转和真机稳定性仍需实际机器核验。

不能把通过 Gradle 构建当作全部 UI 交互/真实设备测试已通过。具体执行证据在相关 PR 中按版本记录。
