# 闺蜜机 Launcher × 小暖｜Android 模拟原型

> 三创科技（SANCHUANG） ·「见趣」为暂定产品名 ·「小暖」为已确认的 AI 伙伴名
>
> **当前代码分支：JQ-002 的 Launcher 最小底座**。设备订阅与小暖模拟在 [JQ-003](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/3) 实施；不是完整产品。

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

桌面内「设备服务」目前只显示下一阶段的提示，这是 **JQ-002 预留入口，不代表已完成订阅与小暖**。界面中的「三创 / SANCHUANG」当前采用文字标识，原始 `sanchuang-web.zip` 仍属于对话附件，尚未提交到 GitHub，不能假称已集成正式图标。

## 文档

- [唯一产品 PRD](docs/product/PRD-v0.1.md)
- [仓库约束](AGENTS.md)
- [阶段里程碑](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/1)
- [JQ-002 Launcher 底座](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/2)
- [JQ-003 本地 Mock](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/3)

## 验收边界

- **已验证**（当前 JQ-002 本地工作）：Gradle `assembleDebug` 可在授权 Mac 上完成，结果见对应 PR 的证据。
- **尚未验证**：真实设备将本应用设为默认 HOME、App 启动行为、屏幕旋转、换机适配；不能用构建成功代替这些行为测试。
