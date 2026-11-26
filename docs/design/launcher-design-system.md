# Launcher design system — 日光客厅

产品基线：见趣 Web [design.md](https://github.com/sanchuang-dev/ai-mirror/blob/dev/design.md) 与 [tokens.css](https://github.com/sanchuang-dev/ai-mirror/blob/dev/web/assets/tokens.css)（Token blob 0fcfa5fba2c7138ffd3bc90b212e4d3a94277cac）。

Android 当前仅实现 A 主题；不引入 B 主题、Compose 或新 UI 框架。颜色唯一实现源为 `res/values/colors.xml`，Kotlin 通过 JianquColors 读取，XML 系统栏和 HOME 矢量图标直接引用资源。品牌 accent 与 Web 一致；正文强调及白字主按钮使用 accent-strong，确保可读性。SOS 危险色与功能图标色是 Android 本地扩展，不冒充 Web 原值；语义上区分危险、成功、品牌和表面。保持功能图标辨识度。

JianquDimensions 和 JianquTypography 定义公共尺寸/文字层级；Android 中文使用系统字体，页面仍可自行处理时钟、特殊标题、二维码与横竖屏几何。JianquDrawables 负责形状及按压反馈，光球为独立组件。不要在 Activity 中重新定义颜色、系统栏或公共 Ripple；特定布局尺寸不强制抽象成全局 Token。单一主题色源不等于所有页面必须有相同布局。

本轮不改变业务、导航、Mock、设备认证、支付、权限或发布配置。未完成设备验收前，不声称视觉通过。

## HOME / 设置 / 全部应用视觉升级

沿用 Web 日光客厅的展示标题衬线与正文无衬线组合：Android 使用系统 serif（CJK fallback）和系统 sans，不内置字体。HOME 时间保留清晰数字，小暖采用品牌淡色与细描边；紧凑网格保留固定十入口和 SOS。设置为两组连续列表，组内分隔线替代每行独立卡片。全部应用使用真实系统图标及开放式网格，保留搜索、清除与真实启动；不改导航及权限。仅在 LauncherActivity 使用新的展示标题；其他业务页面不单独改造。小尺寸或大字体仍允许滚动，不能以强行缩小字号换取满屏。

## 图标

功能图标统一使用 Phosphor Icons Duotone 的原生 VectorDrawable，按需从上游 `phosphor-icons/core/assets/duotone/` 引入，保留 APK 内 MIT 许可。新页面复用 `JianquIcons` 图文组合及语义 tint；不混入 Emoji 或临时字符图标。品牌图标采用独立品牌色，微信图标是渠道识别，不表示官方支付已接通；真实支付和客服二维码均须由后续可信服务提供，未配置时显示不可扫描占位。
