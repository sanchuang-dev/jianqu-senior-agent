# 闺蜜机 Launcher × 小暖｜Android 原型

> 三创科技（SANCHUANG） ·「见趣」为暂定产品名 ·「小暖」为已确认的 AI 伙伴名
>
> **当前 dev 代码基线：JQ-002 Launcher 最小底座**。设备订阅与小暖本地 Mock 在 [JQ-003 Draft PR](https://github.com/sanchuang-dev/jianqu-senior-agent/pull/5)，未合入 dev，真实闺蜜机尚未验收。**第二期 V0.2 目前仅是产品/界面讨论稿，不代表第二期功能已实现。**

## 项目范围

一个普通安装的 Kotlin Android APK（Android 11+）：三创品牌桌面、全部系统允许发现的可启动已安装应用、真实打开应用。后续加入本地模拟设备订阅及小暖预设对话。

**V0.1 只是玩具**：无后端、真实模型、语音识别/合成、支付或账户；不需要 Root、厂家签名或系统级锁机。屏幕以供应商资料中的可旋转竖屏为参考，尚未真机验收。

## 构建和试用 JQ-002

环境：JDK 17+、Android SDK Platform 35；使用仓库内 Gradle Wrapper，无需系统 Gradle。

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装后，可从系统的「默认应用/主屏幕应用」中选择「闺蜜机（演示）」。首次进入后应显示当前用户可启动的系统应用；点击会实际启动相应 App。可通过系统设置改回原桌面。

桌面内「设备服务」目前只显示下一阶段的提示，这是 **JQ-002 预留入口，不代表已完成订阅与小暖**。企业标识已从用户提供的 `sanchuang-web.zip` 中的 `logo-icon-512.png` 派生轻量透明 PNG，并用于桌面品牌栏与 APK 图标。素材包原件仍在对话附件中，本仓库只纳入适配后的这一张图；「见趣」仍是暂定产品名。

## 文档

- [第一期 PRD V0.1 — 已有工程实施基线](docs/product/PRD-v0.1.md)
- [第二期 PRD V0.2 — 新版桌面与功能原型讨论稿](docs/product/PRD-v0.2.md)
- [仓库约束](AGENTS.md)
- [阶段里程碑](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/1)
- [JQ-002 Launcher 底座](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/2)
- [JQ-003 本地 Mock](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/3)

## 验收边界

- **已验证**：JQ-002 构建证据见其对应 PR；JQ-003 的 Debug APK 也曾完成构建与 GitHub Actions 检查，证据见 Draft PR。构建不等于真机功能验收。
- **尚未验证**：真实设备将本应用设为默认 HOME、App 启动行为、屏幕旋转、换机适配；不能用构建成功代替这些行为测试。
