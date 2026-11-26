# Launcher design system — 日光客厅

产品基线：见趣 Web [design.md](https://github.com/sanchuang-dev/ai-mirror/blob/dev/design.md) 与 [tokens.css](https://github.com/sanchuang-dev/ai-mirror/blob/dev/web/assets/tokens.css)（Token blob 0fcfa5fba2c7138ffd3bc90b212e4d3a94277cac）。

Android 当前仅实现 A 主题；不引入 B 主题、Compose 或新 UI 框架。颜色唯一实现源为 `res/values/colors.xml`，Kotlin 通过 JianquColors 读取，XML 系统栏直接引用资源。品牌 accent 与 Web 一致；正文强调及白字主按钮使用 accent-strong，确保可读性。SOS 危险色与功能图标色是 Android 本地扩展，不冒充 Web 原值；语义上区分危险、成功、品牌和表面。保持功能图标辨识度。

JianquDimensions 和 JianquTypography 定义公共尺寸/文字层级；Android 中文使用系统字体，页面仍可自行处理时钟、特殊标题、二维码与横竖屏几何。JianquDrawables 负责形状及按压反馈，光球为独立组件。不要在 Activity 中重新定义颜色、系统栏或公共 Ripple；特定布局尺寸不强制抽象成全局 Token。单一主题色源不等于所有页面必须有相同布局。

本轮不改变业务、导航、Mock、设备认证、支付、权限或发布配置。未完成设备验收前，不声称视觉通过。
