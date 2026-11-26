# 闺蜜机 Launcher × 小暖

> 三创科技（SANCHUANG） · Android Launcher 产品工程  
> 「见趣」仍是暂定产品名；「小暖」是 AI 伙伴名。

这个仓库已经进入**持续产品研发**，不是一次性原型存档。当前 `dev` 仍以 V0.2 预览实现为基础：底层 Android HOME 与应用启动是真实系统能力，天气使用真实外部服务；设备租赁、小暖、家人通话、内容、商城、SOS 等仍包含明显的 Mock / 未接入部分。后续真实租赁、会员、支付、AI 与通信能力必须按独立任务逐步替换，不能把可点击页面当成已上线服务。

## 当前 dev 能力

- **Android HOME**：普通第三方 APK 可被用户选择为默认桌面。
- **真实应用目录**：通过 Android `LauncherApps` 枚举系统允许发现的可启动 App，并真实启动。
- **天气**：手选城市、MET Norway 真实天气请求、缓存和失败回退；不读取设备 GPS。
- **设备服务原型**：本机租赁与五项服务组合仍是本地演示数据，不会真实扣款或远程授权。
- **小暖 / 家人通话 / 内容 / 本地生活 / SOS**：现阶段混合了本地脚本、Mock 状态和未接入承接页；界面必须继续诚实标识。
- **真实设备验收未完成**：Android 11 模拟器证据不能替代目标 OEM 大屏设备。

下一阶段产品讨论当前由 [Draft PR #37](https://github.com/sanchuang-dev/jianqu-senior-agent/pull/37) 承载；在其产品决策被正式收敛并拆成工程 Issue 前，不应直接据此改真实支付或外部服务。

## 开发入口

环境：

- JDK 17+
- Android SDK Platform 35 / Build Tools 35
- 使用仓库内 Gradle Wrapper

完整本地质量检查：

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

安装调试包：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

> Debug APK 不是稳定签名的量产包，不能据此承诺覆盖升级。

更完整的代码组织、测试层级、Mock/真实边界、秘密管理和技术债见 [工程开发指南](docs/engineering/development.md)。

## 仓库地图

```text
app/
  src/main/                 Android 产品代码与资源
  src/test/                 不依赖设备/网络的 JVM 单元测试
docs/
  product/                  分阶段产品基线与产品讨论
  design/                   已确认的界面/设计说明
  engineering/              仓库级工程约束与开发说明
  verification/             某个版本/日期的真实验证快照与证据索引
.github/workflows/           构建、质量门禁与预发布流程
AGENTS.md                    本仓库 AI / 工程协作边界
```

文档职责不要互相替代：产品文档回答“要做什么”，Issue 回答“这一张卡做到什么算完成”，代码/配置回答“现在实际上是什么”，验证文档只记录当时真实执行过的证据。

## 分支与发布

日常工程链路：

```text
feat/* → dev → test → prod
```

- 功能实现从最新 `dev` 开分支，PR 回 `dev`。
- `test` / `prod` 只在维护者明确批准后晋级。
- 预发布 tag 只允许来自 `prod` 当前提交；现有 release 工作流发布的是明确标注的 Debug prerelease。
- 构建、CI、预发布成功都不等于 OEM 真机或真实外部服务验收完成。

## 产品与工程真相源

- [PRD V0.1](docs/product/PRD-v0.1.md)：早期 Launcher / 本地 Mock 产品基线。
- [PRD V0.2](docs/product/PRD-v0.2.md)：当前预览界面和入口阶段基线。
- [AGENTS.md](AGENTS.md)：仓库工程、数据、高风险能力和分支边界。
- [工程开发指南](docs/engineering/development.md)：本地命令、质量门禁、代码组织与技术债。
- [JQ-001 #1](https://github.com/sanchuang-dev/jianqu-senior-agent/issues/1)：目标 OEM 真机阶段验收，仍保持 Open。
- [2026-11-23 工程快照](docs/verification/PROJECT-STATUS-2026-11-23.md)：历史验证证据，不代表今天的运行事实。
