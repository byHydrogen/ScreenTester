package com.hydrogen.screentester

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AllChangelogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark = when (ThemeSettings.darkModeState) {
                DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                DarkModeConfig.LIGHT -> false
                DarkModeConfig.DARK -> true
            }

            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                AllChangelogScreen(isDark = isDark) { finish() }
            }
        }
    }
}

data class LogLineItem(val tag: String?, val mainText: String, val subText: String?)

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

    val changelogs = remember {
        listOf(
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
    }

    var expandedVersions by rememberSaveable {
        mutableStateOf(changelogs.take(2).map { it.first }.toSet())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            topBar = {
                val startColor = DeviceUtils.backgroundBaseColor(isDark)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    startColor,
                                    startColor.copy(alpha = 0.95f),
                                    startColor.copy(alpha = 0.60f),
                                    startColor.copy(alpha = 0.20f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(bottom = 28.dp)
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
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(changelogs) { (version, logText) ->
                    val isItemExpanded = expandedVersions.contains(version)
                    val arrowRotation by animateFloatAsState(targetValue = if (isItemExpanded) 180f else 0f, label = "arrow")

                    val cardContainerColor = DeviceUtils.cardContainerColor(isDark)
                    val cardBorderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardG2Shape,
                        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
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

                                    val parsedItems = remember(logText) {
                                        val lines = logText.split("\n")
                                        val result = mutableListOf<LogLineItem>()
                                        var idx = 0
                                        val tags = listOf("新增", "优化", "修复", "调整", "补充")

                                        while (idx < lines.size) {
                                            val line = lines[idx].trim()
                                            if (line.isEmpty()) { idx++; continue }

                                            if (line.startsWith("（") && line.endsWith("）") && !line.contains("X轴") && !line.contains("Y轴") && result.isNotEmpty()) {
                                                val last = result.removeAt(result.size - 1)
                                                result.add(last.copy(subText = line.substring(1, line.length - 1).trim()))
                                                idx++
                                                continue
                                            }

                                            var foundTag: String? = null
                                            var remainingText = line
                                            for (t in tags) {
                                                if (line.startsWith(t)) {
                                                    foundTag = t
                                                    remainingText = line.substring(t.length).trim()
                                                    break
                                                }
                                            }

                                            var mainText = remainingText
                                            var subText: String? = null
                                            val openIdx = remainingText.indexOf("（")
                                            val closeIdx = remainingText.lastIndexOf("）")
                                            if (openIdx != -1 && closeIdx != -1 && closeIdx > openIdx) {
                                                val inside = remainingText.substring(openIdx + 1, closeIdx)
                                                if (inside != "X轴" && inside != "Y轴") {
                                                    mainText = remainingText.substring(0, openIdx).trim()
                                                    subText = inside.trim()
                                                }
                                            }

                                            result.add(LogLineItem(foundTag, mainText, subText))
                                            idx++
                                        }
                                        result
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        parsedItems.forEach { item ->
                                            ChangelogRowRenderer(item = item, isDark = isDark)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangelogRowRenderer(item: LogLineItem, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (item.tag != null) {
            TagBadge(
                tag = item.tag,
                isDark = isDark,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
        ) {
            Text(
                text = item.mainText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
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
        "修复" -> if (isDark) Color(0xFF422B2D) else Color(0xFFFAD8D8)
        "调整" -> if (isDark) Color(0xFF332B45) else Color(0xFFE9DFF5)
        "补充" -> if (isDark) Color(0xFF3D3228) else Color(0xFFFAE3CB)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (tag) {
        "新增" -> if (isDark) Color(0xFFACD3B6) else Color(0xFF386B49)
        "优化" -> if (isDark) Color(0xFFADC7EF) else Color(0xFF3C5E8E)
        "修复" -> if (isDark) Color(0xFFF3B9BA) else Color(0xFF904A4A)
        "调整" -> if (isDark) Color(0xFFDBBFFE) else Color(0xFF6B4EA2)
        "补充" -> if (isDark) Color(0xFFF3C497) else Color(0xFF825525)
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