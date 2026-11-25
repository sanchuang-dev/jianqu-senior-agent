# 工程开发指南

本文是 `jianqu-senior-agent` 的**仓库级工程入口**。组织通用的权限、Issue 生命周期、环境模型和验收哲学由 `sanchuang-dev/.github` 维护；这里不复制组织规则，只记录本 Android 项目的具体实现约束。

## 1. 现在是什么

当前工程是一个 Kotlin Android 单模块应用：

```text
:JianquLauncher
└── :app
    ├── src/main/java/cn/sanchuang/jianqu/
    ├── src/main/res/
    └── src/test/
```

运行基线：

- `minSdk = 30`（Android 11）
- `compileSdk = 35`
- 当前 `targetSdk = 33`
- Java / Kotlin JVM target = 17
- 一个普通可安装 APK；不依赖 Root、系统签名、Device Owner 或 OEM ROM。

`targetSdk=33` 是已知技术债，不在普通卫生卡中顺手升级。升级 target SDK 会改变平台兼容和权限行为，应独立建卡并做设备回归。

## 2. 真相源怎么分工

- **代码 / Gradle / Manifest / Workflow / 运行时**：当前技术现实。
- **产品文档**：阶段产品边界和方向；Draft 产品讨论不直接授权编码。
- **Issue**：一张工程卡的目标、范围、验收和风险。
- **PR / CI / 构建 / 模拟器 / 真机**：实施证据。
- **verification 文档**：某个日期的证据快照，不可覆盖后来的代码事实。

遇到冲突先修正过时的投影，不要再新建第三份说明。

## 3. 本地开发与质量门禁

完整基线：

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

开发中可以先跑最小充分检查，但提交前至少要知道完整基线是否通过。CI 对 PR 与 `dev/test/prod` 运行同一层级的 JVM unit test、lint 与 Debug APK 构建。

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

设备行为验证至少记录：APK 对应 commit、Android/ROM、分辨率/方向、实际操作路径和未测项。不要拿旧截图证明新提交。

## 4. 测试分层

### JVM unit test

适合：

- 解析、映射、排序、状态转换；
- 金额/期限/权益等纯规则（未来接入时）；
- 不依赖 Android 生命周期、设备和网络的确定性逻辑。

纯逻辑尽量保持 Android-free，这样可以用 `app/src/test` 快速保护。一个确定性 bug 修复，在成本合理时应留下会阻止回归的测试。

### Android / 模拟器

适合：

- Activity 生命周期、返回路径、方向切换；
- HOME 行为、`LauncherApps` 枚举和系统 App 启动；
- 权限拒绝、SharedPreferences、本机缓存；
- UI 触控和可见状态。

### OEM 真机

必须用于最终判断：

- 默认 HOME / 重启恢复；
- 大屏密度、旋转、触控；
- 安装/卸载刷新和长期稳定性；
- 摄像头、麦克风、系统后台策略等厂家差异。

### 外部服务

真实支付、TWeCall、AI、SOS 送达、会员中台等必须验证真实服务端或第三方事实。Mock、前端成功态、二维码截图和 CI 不能证明资金/消息/通信已经发生。

## 5. 代码组织

当前历史代码大量集中在 `cn.sanchuang.jianqu` 根 package，且多个 Activity 已偏大。这是债务，不是继续复制的模板。

新增非平凡功能时：

1. 先找清楚真实职责再拆，不为了“架构感”造空层。
2. 可独立测试的规则/解析/状态机优先放纯 Kotlin 类型，避免直接依赖 Activity/Context。
3. Android 系统能力（LauncherApps、权限、生命周期）留在 Android 边界。
4. 网络/第三方 SDK 用小而明确的 adapter/client 隔离，不把 HTTP、签名、持久化和 UI 全塞进 Activity。
5. 新的真实服务如果需要服务端真相源，不得用客户端 SharedPreferences 伪装账号、租期、支付或长期身份。
6. 同一个概念只有一个真相源；不要在多个 Activity 复制价格、服务定义、状态枚举和错误文案。

现有大型 Activity 的拆分应结合后续真实功能接入逐步进行；无测试保护的大规模搬文件不是“卫生”。

## 6. Mock 与真实能力

当前仓库仍包含大量演示能力。规则很简单：

- Mock 必须可识别，不能冒充真实成功。
- 真能力上线后，旧 Mock 要么删除，要么有明确的开发用途和关闭条件。
- 不允许生产流程在真实依赖失败时静默回退成“模拟成功”。
- 支付成功、SOS 送达、家人接通、AI 生成、会员授权等高价值事实只能来自对应可信边界。

## 7. 数据与秘密

- APK 内不保存商户私钥、APIv3 Key、OAuth AppSecret、模型密钥或长期服务器令牌。
- GitHub 仓库、Issue、PR、日志、截图、测试数据不得包含真实秘密。
- 当前 `SharedPreferences` 只承载本地 UI/原型状态；未来真实会员、租期、支付事实必须以服务端合同为准。
- 设备租赁状态和个人身份/健康/聊天数据分域处理；换人/回收不能泄露前任数据。

## 8. 依赖原则

- 优先 Android/Kotlin 标准能力和已经存在的依赖。
- 新依赖必须解决当前明确问题，并考虑 APK 体积、Android 11 兼容、许可证、维护状态和安全面。
- 不为了一个简单状态机引入通用框架；也不要为了“零依赖”重复实现成熟安全协议。
- 第三方 SDK 必须有退出/失败路径，不能让 Launcher 基础 HOME 被外部服务拖死。

## 9. 发布与版本

当前公开工作流发布的是 **Debug prerelease**。已知限制：

- GitHub Runner 与本地 Debug 签名可能不同；
- 不能保证同一设备覆盖升级；
- 正式签名、密钥保管、版本升级和数据迁移尚未形成生产合同；
- 发布成功不等于 OEM 真机接受。

稳定签名与升级链路应作为独立高风险工作验收，不夹带进普通功能卡。

## 10. 当前技术债清单

优先级不代表已经授权施工：

1. **P0：稳定签名 / 覆盖升级 / OEM 真机** — 量产前必须闭环。
2. **P1：大型 Activity / 根 package** — 在真实服务接入时按职责渐进拆分。
3. **P1：可移植回归** — 将仍在 Windows 外部目录的 smoke 证据逐步仓库化或替换为自动测试。
4. **P1：targetSdk 评估** — 从 33 升级前先审 Android 行为变化和目标设备兼容。
5. **P1：真实租赁/会员/支付状态模型** — 不能继续沿用本地 Mock 作为未来服务端真相。
