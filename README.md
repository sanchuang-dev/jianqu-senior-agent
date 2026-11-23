# 闺蜜机 Launcher × 小暖｜Android 原型

> 三创科技（SANCHUANG） ·「见趣」为暂定产品名 ·「小暖」为已确认的 AI 伙伴名
>
> **工程基线：** JQ-002 的真实 Android Launcher 底座与 JQ-003 的本地设备订阅／小暖 Mock 均已并入 `dev`；第二期 HOME 改版和十个固定入口以 [PRD V0.2](docs/product/PRD-v0.2.md) 与 [JQ-004 #7](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/7) 为依据。**尚未完成实际闺蜜机验收。**

## V0.1 已有能力

这是普通 Android 11+ 设备可安装的单 APK 演示原型，不是量产桌面或真正的养老 Agent。

- **真实系统能力**：可作为普通 Android HOME，显示并启动系统允许发现的所有可启动已安装 App。
- **模拟设备订阅**：在「我的闺蜜机」选择虚构套餐、模拟开通当前机器；可选同时演示另外两台虚构设备。
- **小暖模拟**：一个 AI 伙伴名称、三种候选说话风格，各四条本地预设问答；显式标明「模拟 AI」。
- **受控真实动作**：用户确认后通过 Android `LauncherApps` 启动系统真实已安装的 App。
- **演示数据**：套餐资格仅存在本机 `SharedPreferences`，会话不落盘，退出清理；可重置本地 Mock。

不包括真实 LLM、ASR/TTS、云端服务、支付、账号、医疗建议、机构管理、设备锁。构建通过不代表真实设备已验收。

## 第二期 V0.2

设计新的平衡型 HOME：时间日期与天气、小暖专区、十个固定功能、独立 SOS、单独全部应用页面。09「设置」、10「全部应用」固定；01–08 的名称／排列为讨论候选。服务源、微信通话、SOS、天气等真实接入需要单独验证，不应以 Mock 冒充上线能力。

**版本真相源按阶段划分，不混用：**
- [PRD V0.1](docs/product/PRD-v0.1.md)：JQ-002/JQ-003 已有原型实施及设备验收合同。
- [PRD V0.2](docs/product/PRD-v0.2.md)：第二期 Launcher 界面与业务入口产品地图。
- [AGENTS.md](AGENTS.md)：仓库分支、权限与数据边界。
- [JQ-001 #1](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/1)：一期最终设备验收。
- [JQ-004 #7](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/7)：HOME 主屏 UI 与交互实施合同。

## 最新工程验证快照

- [2026-11-23｜V0.2 工程进度与 Windows Android 11 验证](docs/verification/PROJECT-STATUS-2026-11-23.md)：发布位置、138 项定向回归、真实 HOME 链路、截图及尚未闭环的签名/OEM 验收风险。该报告是日期快照，不替代 PRD 或 Issue 验收合同。

## 构建与验收

环境：JDK 17+、Android SDK Platform 35。使用仓库内 Gradle Wrapper，一个工程，一个 APK：

```bash
./gradlew :app:assembleDebug :app:lintDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

通过 Android 的「默认应用 / 主屏幕应用」选择「闺蜜机（演示）」，可以在系统设置中恢复原桌面。调试 APK 未承诺正式签名或可覆盖升级。

**验收边界**：供应商所述 21.5—32 英寸、Android 11+、4GB+64GB、可旋转竖屏是参考值；真实机型、ROM、分辨率、方向、Home 返回和实际应用启动均需设备证据。PR/CI/编译成功只代表对应层级的检查。
