# Launcher design system — 日光客厅

产品基线：见趣 Web [design.md](https://github.com/sanchuang-dev/ai-mirror/blob/dev/design.md) 与 [tokens.css](https://github.com/sanchuang-dev/ai-mirror/blob/dev/web/assets/tokens.css)（Token blob 0fcfa5fba2c7138ffd3bc90b212e4d3a94277cac）。

Android 当前仅实现 A 主题；不引入 B 主题、Compose 或新 UI 框架。颜色唯一实现源为 `res/values/colors.xml`，Kotlin 通过 JianquColors 读取，XML 系统栏和 HOME 矢量图标直接引用资源。品牌 accent 与 Web 一致；正文强调及白字主按钮使用 accent-strong，确保可读性。SOS 危险色与功能图标色是 Android 本地扩展，不冒充 Web 原值；语义上区分危险、成功、品牌和表面。保持功能图标辨识度。

JianquDimensions 定义公共触控与图标/圆角尺寸；JianquTypography 是共享文字层级和字体入口，正文使用系统 sans，展示标题/时钟使用系统 serif，中文使用系统 fallback。JianquSpacing 统一 4dp 间距节奏及既有紧凑半步值（以代码常量为唯一数值来源）。现有字号层级保持已验收的尺寸，不在卫生改动中强制把相邻字号合并；新页面优先采用已有语义层级。HOME 的方向尺寸、比例和网格仍由 HomeLayout 管理，二维码、数字大字、特殊图形尺寸及少量精确布局偏移保留在页面本地。JianquDrawables 负责形状及按压反馈，光球为独立组件。不要在 Activity 中重新定义颜色、系统栏或公共 Ripple；特定布局尺寸不强制抽象成全局 Token。单一主题色源不等于所有页面必须有相同布局。

本轮不改变业务、导航、Mock、设备认证、支付、权限或发布配置。未完成设备验收前，不声称视觉通过。

## HOME / 设置 / 全部应用视觉升级

沿用 Web 日光客厅的展示标题衬线与正文无衬线组合：Android 使用系统 serif（CJK fallback）和系统 sans，不内置字体。HOME 时间保留清晰数字，小暖采用品牌淡色与细描边；HOME 网格保留八个具名业务入口；应用搜索、设置、全部应用、SOS 以纯图标按钮排列于品牌栏右侧，搜索位于设置左侧，触控区至少 48dp，保留无障碍名称和原跳转。SOS 页面仍明确标示未接入真实求助。HOME 搜索使用独立圆角浮层、顶部输入框和真实应用图标网格，共用系统应用目录与匹配规则，不联网、不索引其他数据。设置由 `settings/SettingsScreen` 独立渲染：横屏左侧分类、右侧实际设置，竖屏分组列表；设备与环境（网络、显示、声音、天气城市）、设备管理（默认主屏幕）、关于与帮助。系统设置调用 Android 原生入口，不伪造系统权限；天气城市沿用已有偏好。组内分隔线替代每行独立卡片；使用共享色彩、字体、尺寸及按压反馈，设置页使用实色表面以保证可读性。全部应用使用真实系统图标及开放式网格，保留搜索、清除与真实启动；不改导航及权限。Launcher HOME 渲染由 home/HomeScreen 负责，入口名称与图标由 HomeAction 单一定义，方向和网格尺寸由 HomeLayout 管理；LauncherActivity 保留准入、生命周期和实际路由。设置、全部应用沿用展示标题；其他业务页面不单独改造。小尺寸或大字体仍允许滚动，不能以强行缩小字号换取满屏。

## 图标

辅助操作图标使用 Phosphor Icons Duotone 的原生 VectorDrawable，按需从上游 `phosphor-icons/core/assets/duotone/` 引入，保留 APK 内 MIT 许可。新页面复用 `JianquIcons` 图文组合及语义 tint；不混入 Emoji 或临时字符图标；设置新增的简洁线性矢量图标由本项目绘制。品牌图标采用独立品牌色，微信图标是渠道识别，不表示官方支付已接通；真实支付和客服二维码均须由后续可信服务提供，未配置时显示不可扫描占位。

## 设备租赁欢迎页背景

使用用户提供的日光客厅纯背景 WebP（`drawable-nodpi/jq_daylight_background.webp`）；固定在滚动内容下方，横竖屏等比中心裁切，浅色遮罩和半透明面板使用颜色资源。移除欢迎区独立光球，保留真正的原生交互、开发态标记和全部准入/付款逻辑。素材不包含文字、设备或界面。

## HOME 图像素材

首页八个业务入口、设置、全部应用和 SOS 使用已确认的 3D 图标套装（`jianqu-launcher-icons.zip`），品牌、语音、天气使用先前 `jianqu-home-icons.zip` 的对应 WebP。运行时仅保留实际引用的 WebP，位于 `app/src/main/res/drawable-nodpi/`；不将原图、预览图或未采用的 PNG 打进 APK。完整素材源包与预览归档见 [assets/README.md](assets/README.md)，保留两代原始资源，便于后续视觉更新。小暖人物立绘未提供，不使用替代图。

玻璃材质统一由 `JianquGlassMaterial`（TILE / PANEL / OVERLAY）和 `colors.xml` 的 `jq_glass_*` 语义 Token 定义。HOME、租赁页、搜索浮层共用 `FrostedWallpaperBackground`：局部静态壁纸采样、模糊缓存、薄白色覆盖、细高光边缘；浮层采用更强的模糊，隔离背后文字和图标，控件使用轻透材质。不同窗口采用屏幕坐标对齐，键盘弹出不改变壁纸采样坐标。不是实时全窗口背景模糊，不引入逐帧滤镜。文字与图标保持实色，危险/主操作语义色不玻璃化。
