@file:OptIn(ExperimentalHazeMaterialsApi::class)

package com.hydrogen.screentester

import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AllChangelogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 关闭三键导航栏的半透明对比遮罩
        window.isNavigationBarContrastEnforced = false
        setContent {
            val isDark = when (ThemeSettings.darkModeState) {
                DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                DarkModeConfig.LIGHT -> false
                DarkModeConfig.DARK -> true
            }

            // 状态栏/导航栏图标跟随「应用内」深色设置
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    androidx.core.view.WindowInsetsControllerCompat(window, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            }

            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                AllChangelogScreen(isDark = isDark) { finish() }
            }
        }
    }
}

data class LogLineItem(val tag: String?, val mainText: String, val subText: String?)

// 顶部内容渐隐：不着色，只把内容按竖向遮罩淡出
internal fun Modifier.fadeOutAtTop(fadeEnd: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                (fadeEnd.toPx() / size.height).coerceIn(0.01f, 1f) to Color.Black
            ),
            blendMode = BlendMode.DstIn
        )
    }

@OptIn(ExperimentalLayoutApi::class)
internal val changelogEntries = listOf(
            "3.0" to "#归类\n新增 OOBE 悬浮底栏\n新增 OOBE 底栏按钮 及 进度指示器 支持模糊效果\n新增 OOBE 圆角校准步骤 使用旧版曲线 开关\n新增 OOBE 圆角校准步骤 拖拽调整模式\n新增 OOBE 测试线条步骤 底部「更多设置」入口\n新增 OOBE 界面高级动效步骤 背景动态混色 开关\n新增 OOBE 动态混色背景\n新增 校准车间页 拖拽调整模式\n新增 黑边遮挡测试 支持自定义背景颜色\n新增 黑边遮挡测试 支持自定义文字颜色\n新增 主页 全新渐变顶栏\n新增 主页 顶栏渐变模糊\n新增 设置页 全新渐变顶栏\n新增 设置页 顶栏渐变模糊\n新增 设置页 外观设置 顶栏渐变模糊 开关\n新增 设置页 外观设置 底栏模糊 开关\n新增 设置页 外观设置 关于页卡片模糊 开关\n新增 设置页 外观设置 模糊强度滑块（可调整模糊强度）\n新增 设置页 界面高级动效 背景动态混色 开关\n新增 设置页 自定义测试线条粗细和颜色 底部「更多设置」入口（点击后进入线条与配色页）\n新增 线条与配色页（可调整黑边遮挡测试的线条 / 背景 / 文字并实时预览调整效果）\n新增 关于页 顶栏渐变模糊\n新增 关于页 卡片模糊\n新增 关于页 动态混色背景\n新增 关于页 更新日志板块 支持更新日志按模块自动归类\n新增 关于页 致谢卡片加入Haze\n新增 历史更新日志页 顶栏渐变模糊\n新增 历史更新日志页 卡片模糊\n新增 历史更新日志页 动态混色背景\n新增 历史更新日志页 支持更新日志按模块自动归类（点分类只看该模块 / 长按可叠加多个 / 点击全部显示完整更新日志）\n新增 赞赏页 动态混色背景\n优化 平板部分页面显示布局\n优化 黑边遮挡测试 圆角/精度模式切换动画\n优化 设置页 下载与更新 更新下载源选择UI\n优化 关于页 设备信息卡片排版\n优化 关于页 部分设备布局显示问题\n修改 黑边遮挡测试 精度模式下 居中文本（带黑边膜挡屏测试 → 黑边遮挡测试）\n修改 黑边遮挡测试 精度模式下 长按退出提示文本（退出倒计时：x 秒 → 请继续按住 x 秒...）\n修复 黑边遮挡测试 精度模式下 部分设备居中文本超出分区下沿的问题\n修复 关于页 未滚动到的卡片仍可点击的问题\n修复 部分设备机型 OS 版本读取异常的问题\n修复 OOBE 自定义圆角半径引导 高亮卡片被遮挡时未自动抬高的问题\n修复 了一些已知问题",
            "2.9.6" to "重构 主页 网格 / 列表\n新增 主页 全新 卡片视图切换动画\n优化 主页 搜索测试项结果卡片动画\n修复 主页 网格视图下搜索时卡片未重排的问题\n修复 Android 15 以下 Android 版本 主界面 导航条有半透明遮罩的问题\n修复 Android 15 以下 Android 版本 历史更新日志页 导航条有半透明遮罩的问题\n修复 Android 15 以下 Android 版本 赞赏页 导航条有半透明遮罩的问题\n修复 部分 Android 版本下 HDR检测界面 状态栏 / 导航条不沉浸的问题\n修复 部分 Android 设备 测试页始终显示刘海的问题（渲染进摄像头挖孔区域，避免全屏时挖孔处出现黑条）\n修复 了一些已知问题",
            "2.9.1" to "新增 测试亮度设置 测试页屏幕常亮 开关\n修复 黑边遮挡测试 精度模式 遮挡宽度毫米数值为固定数值的问题（改为按设备屏幕实际密度动态计算）\n修复 了一些已知问题",
            "2.9" to "新增 新版「系统默认曲线」\n新增 设置页 自定义黑边遮挡测试 使用旧版曲线 开关\n新增 设置页 自定义黑边遮挡测试 长按退出 开关\n新增 设置页 自定义黑边遮挡测试 自定义退出时长 滑块\n新增 设置页 下载与更新 自动检查更新开关\n优化 主页 首次启动时的「加入QQ交流群」弹窗（改为显示横幅，30秒后自动消失）\n修复 系统导航为导航键时 底栏被遮挡的问题\n修复 了一些已知问题",
            "2.8" to "新增 支持应用内下载更新包\n新增 Gitee 更新下载源\n新增 设置页 下载与更新卡片（切换下载源）\n新增 设置页 渐变色条 蓝粉预设方案\n新增 设置页 渐变色条 海洋预设方案\n移除 设置页 渐变色条 莫奈色预设方案\n修复 黑边遮挡测试 渐变色条和设置预览时显示不一致的问题\n修改 关于页 更新日志卡片 版本更新卡片",
            "2.5" to "新增 OOBE 圆角校准步骤 / 校准车间页 首次进入时的功能介绍\n新增 OOBE 界面高级动效步骤 设置页线条预览开关\n新增 设置页 界面高级动效 线条预览开关\n新增 设置页 渐变色条 自定义渐变色条颜色（创建渐变方案）\n优化 设置页 渐变色条 预设方案选中时的背景颜色\n优化 OOBE 莫奈取色为青 / 蓝绿 / 蓝下的背景混色\n优化 关于页 莫奈取色为青 / 蓝绿 / 蓝下的背景混色\n优化 历史更新日志页 莫奈取色为青 / 蓝绿 / 蓝下的背景混色\n修改 OOBE 步骤4 标题文字（卡片动效 → 界面高级动效）\n修复 了一些已知问题",
            "2.1" to "新增 OOBE 圆角校准步骤 支持内嵌校准卡片（无需跳转校准车间）\n新增 主页 加入QQ交流群弹窗（仅弹一次）\n新增 关于页 加入QQ交流群卡片\n新增 校准车间页 +/- 按钮（点击后 ±0.1 ，长按可快速加减）\n新增 校准车间页 固定卡片功能（长按卡片可固定，固定后卡片不会被折叠，固定后点击/长按卡片标题栏可取消固定）\n新增 校准车间页 卡片展开/折叠时的箭头旋转动效\n优化 校准车间页 卡片圆角\n优化 主页 搜索框 输入文本动画\n优化 屏幕灰阶测试 & 屏幕彩条测试 悬浮提示胶囊圆角\n优化 触控采样率测试 触控采样率算法\n优化 触控采样率测试 “稳定峰值”卡片圆角\n修复 自定义深/浅色模式下的状态栏反色问题\n修复 了一些已知问题",
            "2.0" to "新增 OOBE 开箱体验\n新增 主页 网格视图\n新增 主页 发现新版本横幅 及 更新弹窗卡片\n新增 设置页 渐变色条设置项\n新增 关于页 支持开发者卡片\n 新增 关于页 致谢卡片\n新增 赞赏开发者页面\n新增 黑边遮挡测试 支持显示渐变色条\n新增 主页 搜索栏搜索无结果提示\n新增 关于页更新日志卡片 检查更新失败提示\n修复 部分设备机型宣传名读取失败的问题\n修复 多指触控检测 状态栏及导航条未隐藏的问题\n修复 了一些已知问题\n优化 关于页 莫奈取色为黄绿/红橙棕下的背景混色\n优化 历史更新日志页 莫奈取色为黄绿/红橙棕下的背景混色\n优化 关于页 下半部分卡片淡入淡出动画\n优化 应用 流畅度",
            "1.2.9.1" to "新增 关于页 开源项目地址卡片\n新增 关于页 更新日志板块\n新增 关于页 检查更新按钮\n优化 主页 顶部渐变效果\n优化 设置页 顶部渐变效果",
            "1.2.9" to "新增 设置页 卡片高级动效开关\n（开启后卡片有淡入动效，高性能设备默认开启，低性能设备默认关闭）\n新增 设置页 纯净模式开关\n（开启后将隐藏测试页圆角模式中部部分文案和底部参数，并隐藏右上角切换按钮，改为双击屏幕任意位置切换模式）\n调整 设置页 卡片\n（将精准圆角校准卡片 改为 自定义黑边遮挡测试）\n修复 了一些已知问题",
            "1.2.8" to "新增 屏幕 HDR 检测\n优化 设置页 卡片圆角\n新增 设置页 卡片淡入动效\n新增 设置页 卡片展开/折叠时的箭头旋转动效\n优化 黑边遮挡测试 文案（按返回键返回 → 按返回键）",
            "1.2.7.2" to "优化 ScreenTester 图标",
            "1.2.7" to "优化 黑边遮挡测试 文案（侧滑返回 → 按返回键返回）\n优化 主页 搜索框圆角\n优化 主页 卡片动画\n优化 主页 搜索动画\n优化 底栏 圆角\n优化 关于页 图标 LOGO 圆角\n优化 关于页 设备信息卡片圆角\n优化 关于页 作者卡片圆角及动画\n优化 校准车间页 返回逻辑（改为需按两次返回键返回）\n修复 了一些已知问题",
            "1.2.6.9" to "新增 G2平滑圆角开关\n新增 横向（X轴）曲率修正\n新增 纵向（Y轴）曲率修正\n新增 设置页 线条粗细重置按钮\n优化 校准车间内UI\n优化 设置页 外观模式 UI\n优化 主页 UI\n修复 了一些已知问题",
            "1.2.6" to "新增 G2平滑圆角开关\n新增 横向（X轴）曲率修正\n新增 纵向（Y轴）曲率修正\n新增 设置页 线条粗细重置按钮\n优化 校准车间内UI\n优化 设置页 外观模式 UI\n修复 了一些已知问题",
            "1.2.2" to "优化 设置页 精准圆角校准卡片 折叠\n优化 校准车间 拆分调节/合并调节动画\n修复 关于页 部分机型文字重叠的问题\n修复 了一些已知问题",
            "1.2.0.1 Beta" to "修复 了一些已知问题",
            "1.2.0 Beta" to "新增 设置页 精准圆角校准，若系统读取圆角无法对齐屏幕可选择手动调整\n修复 多指触控测试页 部分机型文字显示重叠的问题",
            "1.1.1" to "补充 黑边遮挡测试 精度模式部分说明",
            "1.1.0" to "新增 黑边遮挡测试 精度模式\n新增 设置页 线条粗细调整选项",
            "1.0" to "ScreenTester 首个版本"
)

@Composable
private fun ChangelogCategoryChips(
    groups: List<Pair<String, Int>>,
    selected: Set<String>,
    showAll: Boolean,
    accent: Color,
    onSelect: (String?) -> Unit,
    onLongSelect: (String) -> Unit
) {
    // null 表示"全部"；分类按固定顺序排在后面
    val chips = listOf<Triple<String?, String, Int?>>(Triple(null, "全部", null)) +
            groups.map { Triple(it.first as String?, it.first, it.second as Int?) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (key, label, count) ->
            val isSelected = if (key == null) showAll else !showAll && key in selected
            val chipBackground by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "changelogChipBg"
            )
            val chipContentColor by animateColorAsState(
                targetValue = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "changelogChipText"
            )
            Box(
                modifier = Modifier
                    .clip(G2Shapes.gridCard)
                    .background(chipBackground)
                    .combinedClickable(
                        onClick = { onSelect(key) },
                        // 长按分类可叠加多选（"全部"除外）
                        onLongClick = { if (key != null) onLongSelect(key) }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (count == null) label else "$label $count",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = chipContentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllChangelogScreen(isDark: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current

    val currentVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    val systemMonetPrimary = remember(isDark) {
        if (isDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    }

    val backgroundBrush = DeviceUtils.backgroundBrush(isDark)

    val cardG2Shape = remember {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val path = androidx.compose.ui.graphics.Path()
                val w = size.width; val h = size.height
                val radius = with(density) { 24.dp.toPx() }
                val p = (1.4f * radius).coerceAtMost(h / 2f)
                val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }

    val changelogs = changelogEntries

    var expandedVersions by rememberSaveable {
        mutableStateOf(changelogs.take(2).map { it.first }.toSet())
    }

    // 两个模糊源分开：卡片毛玻璃只采背景(cardHazeState)；顶栏要采滚过的内容(changelogHazeState =
    // 背景层+列表)。源只能挂在与效果节点同级的兄弟层上——之前挂在根 Box 上
    val changelogHazeState = remember { HazeState() }
    val cardHazeState = remember { HazeState() }
    val cardTintScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val cardThinStyle = HazeMaterials.thin(containerColor = cardTintScheme.background)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // 顶栏模糊源（外）与卡片模糊源（内）分层挂载
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ThemeSettings.topBarGradientBlur) Modifier.hazeSource(changelogHazeState) else Modifier)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (ThemeSettings.aboutCardBlurEnabled) Modifier.hazeSource(cardHazeState) else Modifier)
                    .then(
                        // 与关于页共用同一动态混色开关；独立 Activity 仅在可见时组合，running 恒为 true
                        if (ThemeSettings.aboutDynamicMixEnabled) Modifier.dynamicMixBackground(isDark, running = true)
                        else Modifier.background(backgroundBrush)
                    )
            )
        }
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 平板限宽居中
                .widthIn(max = DeviceUtils.NavBarMaxWidth)
                .fillMaxSize()
                .then(if (ThemeSettings.topBarGradientBlur) Modifier.hazeSource(changelogHazeState) else Modifier)
                // 不开模糊但开了动态混色时，用内容渐隐代替顶栏铺色
                .then(
                    if (!ThemeSettings.topBarGradientBlur && ThemeSettings.aboutDynamicMixEnabled) {
                        Modifier.fadeOutAtTop(statusBarTop + 96.dp)
                    } else Modifier
                ),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = statusBarTop + 96.dp, // TopAppBar(64dp) + 渐变带(28dp) + 4dp
                bottom = navBarBottom + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                items(changelogs) { (version, logText) ->
                    val isItemExpanded = expandedVersions.contains(version)
                    val arrowRotation by animateFloatAsState(targetValue = if (isItemExpanded) 180f else 0f, label = "arrow")

                    val cardContainerColor = DeviceUtils.cardContainerColor(isDark)
                    val cardBorderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 卡片毛玻璃：跟随关于页卡片模糊开关，thin 材质 + 底栏模糊强度
                            .then(
                                if (ThemeSettings.aboutCardBlurEnabled) Modifier
                                    .clip(cardG2Shape)
                                    .hazeEffect(cardHazeState) {
                                        style = cardThinStyle.copy(blurRadius = ThemeSettings.navBarBlurRadius.dp)
                                    }
                                else Modifier
                            ),
                        shape = cardG2Shape,
                        colors = CardDefaults.cardColors(
                            // 无模糊时且动态混色开启时半透明，让流动背景透出
                            containerColor = if (ThemeSettings.aboutCardBlurEnabled) Color.Transparent
                            else oobeCardColor(isDark)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        expandedVersions = if (isItemExpanded) expandedVersions - version else expandedVersions + version
                                    }
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "版本 $version",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (version == currentVersionName) systemMonetPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer { rotationZ = arrowRotation },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }

                            AnimatedVisibility(visible = isItemExpanded) {
                                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )

                                    ChangelogGroupedContent(
                                        logText = logText,
                                        version = version,
                                        isDark = isDark,
                                        accent = systemMonetPrimary
                                    )
                                }
                            }
                        }
                    }
                }
        }

        // 顶栏 overlay：叠在列表上方，滚动内容从其下方经过，渐变模糊才有内容可采。
        // 渐变模糊开启：纯模糊无染色层；
        // 关闭：同色渐变——底色跟随实际背景来源（动态混色开启时用混色底色+半透明档位，
        // 否则用静态色板底色+原档位），避免渐变带和背景颜色不符
        val topBarBase = if (ThemeSettings.aboutDynamicMixEnabled) DeviceUtils.dynamicMixColors(isDark).first()
        else DeviceUtils.backgroundBaseColor(isDark)
        // 混色开启时流动光斑在渐变带后面漂移，扁平色带会切出明显边界；
        // 用更速明的半透明"洗层"档位弱化边界，静态背景时保持原不透明档位
        val topBarAlphas = if (ThemeSettings.aboutDynamicMixEnabled) listOf(0.78f, 0.60f, 0.35f, 0.12f, 0f)
        else listOf(1f, 0.95f, 0.60f, 0.20f, 0f)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(
                    when {
                        // 模糊分支不加 28dp 尾巴：hazeEffect 铺满节点高度，多铺一截等于把下面的内容也糊掉
                        ThemeSettings.topBarGradientBlur -> Modifier.hazeEffect(changelogHazeState) {
                            style = HazeStyle(tints = emptyList(), noiseFactor = 0f)
                            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        }
                        // 混色开启时不铺同色渐变（和流动的混色背景对不上，会切出边界）——
                        // 改由列表自身在顶部渐隐，见 fadeOutAtTop
                        ThemeSettings.aboutDynamicMixEnabled -> Modifier.padding(bottom = 28.dp)
                        else -> Modifier.background(
                            Brush.verticalGradient(
                                colors = topBarAlphas.map { topBarBase.copy(alpha = it) }
                            )
                        ).padding(bottom = 28.dp)
                    }
                )
        ) {
            TopAppBar(
                title = { Text("历史更新日志", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun ChangelogRowRenderer(
    item: LogLineItem,
    isDark: Boolean,
    useTagBadge: Boolean = true,
    bodyFontSize: TextUnit = 14.sp,
    bodyLineHeight: TextUnit = 20.sp,
    bodyColor: Color = MaterialTheme.colorScheme.onSurface,
    plainLine: Boolean = false
) {
    // 整行一个 Text：标签与正文之间用原文的空格，间距/基线天然一致
    if (plainLine) {
        Text(
            text = buildString {
                if (item.tag != null) append(item.tag).append(' ')
                append(item.mainText)
                if (item.subText != null) append('（').append(item.subText).append('）')
            },
            fontSize = bodyFontSize,
            lineHeight = bodyLineHeight,
            color = bodyColor
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (item.tag != null) {
            if (useTagBadge) {
                TagBadge(
                    tag = item.tag,
                    isDark = isDark,
                    modifier = Modifier.alignByBaseline()
                )
            } else {
                // 关于页不用彩色小标签，标签只加粗
                // 与正文同号同色，只加粗
                Text(
                    item.tag,
                    fontWeight = FontWeight.Bold,
                    fontSize = bodyFontSize,
                    color = bodyColor,
                    modifier = Modifier.alignByBaseline()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
        ) {
            Text(
                text = item.mainText,
                fontSize = bodyFontSize,
                fontWeight = FontWeight.Medium,
                color = bodyColor,
                lineHeight = bodyLineHeight
            )
            if (item.subText != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.subText,
                    fontSize = 11.5.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
fun TagBadge(tag: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val containerColor = when (tag) {
        "新增" -> if (isDark) Color(0xFF2A3A2E) else Color(0xFFD2E7D6)
        "优化" -> if (isDark) Color(0xFF25354A) else Color(0xFFD2E4FF)
        "重构" -> if (isDark) Color(0xFF1F3A38) else Color(0xFFD2F0ED)
        "修复" -> if (isDark) Color(0xFF422B2D) else Color(0xFFFAD8D8)
        "调整" -> if (isDark) Color(0xFF332B45) else Color(0xFFE9DFF5)
        "补充" -> if (isDark) Color(0xFF3D3228) else Color(0xFFFAE3CB)
        "修改" -> if (isDark) Color(0xFF3A2E1A) else Color(0xFFF5E1C0)
        "移除" -> if (isDark) Color(0xFF4A2222) else Color(0xFFE8C8C8)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (tag) {
        "新增" -> if (isDark) Color(0xFFACD3B6) else Color(0xFF386B49)
        "优化" -> if (isDark) Color(0xFFADC7EF) else Color(0xFF3C5E8E)
        "重构" -> if (isDark) Color(0xFF9FDCD6) else Color(0xFF2E7A72)
        "修复" -> if (isDark) Color(0xFFF3B9BA) else Color(0xFF904A4A)
        "调整" -> if (isDark) Color(0xFFDBBFFE) else Color(0xFF6B4EA2)
        "补充" -> if (isDark) Color(0xFFF3C497) else Color(0xFF825525)
        "修改" -> if (isDark) Color(0xFFE8C885) else Color(0xFF8B6914)
        "移除" -> if (isDark) Color(0xFFE8A0A0) else Color(0xFF8B4848)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .background(color = containerColor, shape = RoundedCornerShape(9.dp))
            .padding(horizontal = 6.5.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = tag,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}