@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class
)

package com.hydrogen.screentester

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsControllerCompat
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme

/**
 * 线条与配色页：上方按屏幕比例 + 系统圆角画"手机形状"实时预览，下方调粗细/颜色/字体。
 * 竖屏单栏（预览跟手缩小窗），横屏两栏（左预览 + 右面板）。
 */
class TestLineAppearanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        setContent {
            ScreenTesterTheme {
                val isDark = when (ThemeSettings.darkModeState) {
                    DarkModeConfig.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    DarkModeConfig.LIGHT -> false
                    DarkModeConfig.DARK -> true
                }
                TestLineAppearanceScreen(isDark = isDark) { finish() }
            }
        }
    }
}

@Composable
internal fun TestLineAppearanceScreen(isDark: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    var colorTarget by remember { mutableStateOf("line") }

    // 状态栏/导航栏图标必须跟"应用内"深浅色走
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val topBarHazeState = rememberHazeState()
        val contentScrollState = rememberScrollState()
        // 覆盖式顶栏：滚动内容从顶栏下面经过，渐变/模糊才有东西可盖
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val topBarBand = statusBarTop + 64.dp + 28.dp
        val topBarBase = MaterialTheme.colorScheme.background
        // thin 材质（染色）要在组合期算好
        val topBarThinStyle = HazeMaterials.thin(containerColor = topBarBase)

        // 横屏 = 左预览 + 右面板
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        val panelScrollState = rememberScrollState()

        // 选择器 + 卡片：竖屏单栏滚动列、横屏右栏共用这一份
        val panelContent: @Composable () -> Unit = {
            // 调色对象切换
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                SegmentedSlideSelector(
                    items = listOf("line" to "线条", "bg" to "背景", "text" to "文字"),
                    selected = colorTarget,
                    onSelect = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        colorTarget = it
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // 与设置页同一套控件（同一份状态）
            AnimatedContent(
                targetState = colorTarget,
                transitionSpec = {
                    // 按"目标项在第几位"判断方向
                    val order = listOf("line", "bg", "text")
                    val forward = order.indexOf(targetState) > order.indexOf(initialState)
                    (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it else it } + fadeOut())
                },
                label = "colorTargetCard"
            ) { target ->
                // 内边距放在动画容器与卡片之间：容器保持满宽，滑动才从屏幕边缘进出
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    // 线条面板自带上下各 12dp 内边距，这里扣掉，让三个 tab 的上下空隙一致
                    val isLineTab = target == "line"
                    val cardTopPadding = if (isLineTab) 4.dp else 16.dp
                    val cardBottomPadding = if (isLineTab) 4.dp else 16.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(G2Shapes.card)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(top = cardTopPadding, bottom = cardBottomPadding)
                    ) {
                        when (target) {
                            "line" -> TestLineSettingsPanel(showTopDivider = false)
                            "bg" -> BackgroundColorPanel()
                            else -> TextColorPanel()
                        }
                    }
                }
            }
        }

        if (isLandscape) {
            // 横屏两栏：左预览常驻（不缩小、不吸附），右面板独立滚动
            val ratio = if (configuration.screenHeightDp <= 0) 9f / 19.5f
            else configuration.screenWidthDp.toFloat() / configuration.screenHeightDp
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .then(if (ThemeSettings.topBarGradientBlur) Modifier.hazeSource(topBarHazeState) else Modifier)
            ) {
                // 左栏：预览常驻。尺寸靠 aspectRatio 自适应——先按可用高度铺满，宽度不够时自动收窄
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = 20.dp, end = 20.dp,
                            top = topBarBand + 16.dp,
                            bottom = navBarBottom + 16.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    DevicePreview(
                        scrollState = contentScrollState,
                        topBarHeight = topBarBand,
                        dockEnabled = false,
                        baseFraction = 1f,
                        modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    )
                }
                // 右栏固定 420dp（半个 ContentMaxWidth）⇒ 卡片宽度与单栏一致，余宽都给左栏
                Column(
                    modifier = Modifier
                        .width(DeviceUtils.ContentMaxWidth / 2)
                        .fillMaxHeight()
                        .verticalScroll(panelScrollState)
                ) {
                    Spacer(Modifier.height(topBarBand))
                    panelContent()
                    Spacer(Modifier.height(navBarBottom + 24.dp))
                }
            }
        } else {
            // 竖屏单栏：预览跟手上滑并缩成右上角小窗。
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = DeviceUtils.FormMaxWidth)
                    .fillMaxHeight()
                    .then(if (ThemeSettings.topBarGradientBlur) Modifier.hazeSource(topBarHazeState) else Modifier)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // 左右内边距移到各子项内部：这里保持满宽，卡片滑动才是从屏幕边缘进出
                        .verticalScroll(contentScrollState)
                ) {
                    Spacer(Modifier.height(topBarBand))

                    DevicePreview(
                        scrollState = contentScrollState,
                        topBarHeight = topBarBand,
                        modifier = Modifier
                            .fillMaxWidth()
                            // zIndex 必须挂在"滚动列的直接子节点"上：挂到 DevicePreview 内部对层级无效
                            .zIndex(1f)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    )

                    panelContent()

                    // 底部让出导航栏高度：三大金刚键会挡住内容，手势条几乎不占高度所以看不出
                    Spacer(Modifier.height(navBarBottom + 56.dp))
                }
            }
        }

        // 顶栏覆盖层：透明 TopAppBar（自带状态栏 insets）+ 渐变/模糊二选一。
        // 竖屏限宽跟内容走；横屏内容铺满
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (isLandscape) Modifier else Modifier.widthIn(max = DeviceUtils.FormMaxWidth))
                .then(
                    if (ThemeSettings.topBarGradientBlur) {
                        Modifier.hazeEffect(topBarHazeState) {
                            style = topBarThinStyle
                            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        }
                    } else {
                        Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        topBarBase.copy(alpha = 1f),
                                        topBarBase.copy(alpha = 0.75f),
                                        topBarBase.copy(alpha = 0.50f),
                                        topBarBase.copy(alpha = 0.25f),
                                        topBarBase.copy(alpha = 0f)
                                    )
                                )
                            )
                            .padding(bottom = 28.dp)
                    }
                )
        ) {
            TopAppBar(
                title = { Text("线条与配色", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * 预览：把真实渲染器 [TestFrameView] 按真机尺寸布局后整体缩放塞进来 ⇒ 几何、渐变、粗细比例都与真机一致。
 */
@Composable
private fun DevicePreview(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    topBarHeight: Dp,
    /** 横屏两栏时传 false：预览常驻，不缩小、不吸附、不接管手势 */
    dockEnabled: Boolean = true,
    /** 未缩小时预览占容器宽度的比例（两栏时按左栏可用高度算出来传进来） */
    baseFraction: Float = 0.62f
) {
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp.dp
    val screenH = configuration.screenHeightDp.dp
    val ratio = if (configuration.screenHeightDp <= 0) 9f / 19.5f else screenW.value / screenH.value

    // 原生 View 不响应 Compose state：这组设置做 key，任一变化就主动刷一次预览
    val redrawKey = listOf(
        ThemeSettings.testLineThickness,
        ThemeSettings.testLineColor,
        ThemeSettings.isMultiColorMode,
        ThemeSettings.multiColorSegmentLength,
        ThemeSettings.isLegacyCurveEnabled,
        ThemeSettings.testLineBgColor,
        ThemeSettings.testLineTextColor,
        ThemeSettings.textFollowsLine,
        ThemeSettings.titleFollowsText,
        ThemeSettings.precisionUsesCustomBg,
        ThemeSettings.deviceNameOpacityPct,
        ThemeSettings.isCompactModeEnabled
    )
    // 用普通持有对象而不是 state：factory 里赋值会在组合期触发重组，可能重建同页其它组件
    val previewViewHolder = remember { arrayOfNulls<TestFrameView>(1) }
    LaunchedEffect(redrawKey) {
        previewViewHolder[0]?.postInvalidateOnAnimation()
    }

    // 裁切形状用与真实渲染同一份 path（含自定义半径/G2/新版曲线），半径随 scale 缩放
    val previewContext = LocalContext.current
    val previewInsets = LocalView.current.rootWindowInsets
    val previewView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    // 预览的精度模式（仅预览用，不写设置——真机测试页每次打开都是圆角模式）
    var previewAdvanced by remember { mutableStateOf(false) }
    // 缩小后用户拖出来的额外位移：X 松手吸附左右边，Y 回弹且不进导航栏
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    // 松手时的甩动速度（决定落点），以及轴锁定状态
    val dragVelocityTracker = remember { VelocityTracker() }
    var dragAccumulated by remember { mutableStateOf(Offset.Zero) }
    var dragAxis by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val outerWidth = maxWidth
        val density = LocalDensity.current

        // 上划时预览缩到右上角停靠 —— 往下调颜色时预览一直看得见
        val scrollMax = scrollState.maxValue.toFloat()
        val previewRatio = if (ratio > 0f) ratio else 9f / 19.5f
        // 槽位高度固定 = 预览未缩小时的高度，防止内容总高随缩放变化引起滚动范围震荡
        val previewSlotHeight = outerWidth * baseFraction / previewRatio
        // 缩小进度按"预览滚出屏幕"算（不按整页长度，否则卡片长短一变大小就不一致）；前 15% 跟手
        val shrinkStartPx = with(density) { topBarHeight.toPx() }
        val shrinkEndPx = (shrinkStartPx + with(density) { previewSlotHeight.toPx() } * 0.75f)
            .coerceAtMost(scrollMax)
        val totalPx = (shrinkEndPx - shrinkStartPx).coerceAtLeast(1f)
        val shrinkFollowPx = totalPx * 0.15f
        val shrinkSpanPx = (totalPx - shrinkFollowPx).coerceAtLeast(1f)
        val canShrink = dockEnabled && scrollMax > shrinkStartPx + with(density) { 24.dp.toPx() }
        val shrinkTarget =
            if (!canShrink) 0f
            else ((scrollState.value - shrinkStartPx - shrinkFollowPx) / shrinkSpanPx).coerceIn(0f, 1f)
        val shrink by animateFloatAsState(
            shrinkTarget,
            tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "previewShrink"
        )
        val minPreviewFraction = baseFraction * 0.30f      // 最小缩到原预览宽的 30%
        val previewFraction = baseFraction + (minPreviewFraction - baseFraction) * shrink
        val previewWidth = outerWidth * previewFraction
        // 在居中容器里把右缘推到容器右侧，需要右移"缩掉宽度"的一半
        val shiftX = (outerWidth - previewWidth) / 2f * shrink
        // 横向范围：两端保持与"刚缩成小窗"相同的空隙，按当前预览宽度算
        val maxDragRight = 0f
        val maxDragLeft = -with(density) { (outerWidth - previewWidth).toPx() }
        // 底边不能压进底部导航栏
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val previewHeight = outerWidth * previewFraction / (if (ratio > 0f) ratio else 9f / 19.5f)
        val maxDragDown = with(density) {
            (screenH - navBarBottom - topBarHeight - 16.dp - previewHeight).toPx().coerceAtLeast(0f)
        }
        // 手势与拖动位移按缩小进度淡入：滚回顶部自动归位
        val dragFactor = (shrink / 0.2f).coerceIn(0f, 1f)
        // 轴锁定的判定距离，以及惯性滑行的等效时长（速度 × 时长 = 落点；越小越迟钝）
        val axisLockPx = with(density) { 8.dp.toPx() }
        val flingGlideSeconds = 0.12f
        val maxLeftState = rememberUpdatedState(maxDragLeft)
        val maxRightState = rememberUpdatedState(maxDragRight)
        val maxDownState = rememberUpdatedState(maxDragDown)

        val realWpx = with(density) { screenW.toPx() }.roundToInt()
        val realHpx = with(density) { screenH.toPx() }.roundToInt()
        val scale = with(density) { previewWidth.toPx() } / realWpx
        val scaledWpx = (realWpx * scale).roundToInt()
        val scaledHpx = (realHpx * scale).roundToInt()

        // 用普通 val（不要写成 state）：在组合期给 state 赋值会造成反复重组
        val previewClipShape: androidx.compose.ui.graphics.Shape = remember(scale) {
            object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(
                    size: androidx.compose.ui.geometry.Size,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    density: androidx.compose.ui.unit.Density
                ): androidx.compose.ui.graphics.Outline =
                    androidx.compose.ui.graphics.Outline.Generic(
                        buildScreenBorderPath(
                            widthPx = size.width,
                            heightPx = size.height,
                            insets = previewInsets,
                            thickness = ThemeSettings.testLineThickness * scale,
                            context = previewContext,
                            scale = scale,
                            forClip = true      // 取线条外缘：整条线完整保留，且四角不露底色
                        ).asComposePath()
                    )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(previewSlotHeight),
            contentAlignment = Alignment.TopCenter
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(previewFraction)
                .aspectRatio(previewRatio)
                // 缩小后钉在右上角：横向按进度右移，纵向抵消滚动
                .offset {
                    IntOffset(
                        shiftX.roundToPx() + (dragX.value * dragFactor).roundToInt(),
                        (scrollState.value * shrink + dragY.value * dragFactor).roundToInt()
                    )
                }
                // 缩小后可以拖开（挡住内容时挪一下）、点一下回顶部；没缩小时不接管手势
                .then(
                    if (dockEnabled && shrink > 0.2f) Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    val velocity = dragVelocityTracker.calculateVelocity()
                                    val leftLimit = maxLeftState.value
                                    val rightLimit = maxRightState.value
                                    val downLimit = maxDownState.value
                                    // 惯性滑行落点（速度 × 等效时长）决定吸附到哪：轻划落点就在原地附近 ⇒ 回原位；
                                    // 用力甩落点够远 ⇒ 换边 / 到底。不用二值阈值，力度与结果连续对应
                                    val projectedX = dragX.value + velocity.x * flingGlideSeconds
                                    val targetX = if (projectedX < (leftLimit + rightLimit) / 2f) leftLimit else rightLimit
                                    val targetY = (dragY.value + velocity.y * flingGlideSeconds).coerceIn(0f, downLimit)
                                    dragVelocityTracker.resetTracking()
                                    dragAccumulated = Offset.Zero
                                    dragAxis = null
                                    coroutineScope.launch {
                                        dragX.animateTo(targetX, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    }
                                    coroutineScope.launch {
                                        dragY.animateTo(targetY, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // 累计的手指位移喂给速度追踪：节点跟着手指在动，
                                    // change.position 是相对节点的坐标、几乎恒定，测不出速度
                                    dragAccumulated += dragAmount
                                    dragVelocityTracker.addPosition(change.uptimeMillis, dragAccumulated)
                                    // 轴锁定：起手按累计位移先定主方向，之后只动那一轴
                                    if (dragAxis == null &&
                                        (abs(dragAccumulated.x) > axisLockPx || abs(dragAccumulated.y) > axisLockPx)
                                    ) {
                                        dragAxis = if (abs(dragAccumulated.x) >= abs(dragAccumulated.y)) "x" else "y"
                                    }
                                    when (dragAxis) {
                                        "x" -> coroutineScope.launch { dragX.snapTo(dragX.value + dragAmount.x) }
                                        "y" -> coroutineScope.launch { dragY.snapTo(dragY.value + dragAmount.y) }
                                        else -> Unit
                                    }
                                }
                            )
                        }
                        .clickable {
                            previewView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                    else Modifier
                )
                // 纯净模式：不显示按钮，双击切换
                .then(
                    if (ThemeSettings.isCompactModeEnabled && shrink < 0.2f) Modifier.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { previewAdvanced = !previewAdvanced })
                    } else Modifier
                )
                // 裁切半径与预览里画的线条一致；精度预览且未开"用自定义背景"时底色同步成黑
                .clip(previewClipShape)
                .background(
                    when {
                        previewAdvanced && !ThemeSettings.precisionUsesCustomBg -> Color.Black
                        else -> Color(ThemeSettings.testLineBgColor)
                    }
                )
        ) {
            // 缩放必须设在原生 View 自己身上（graphicsLayer 对 AndroidView 不生效）
            AndroidView(
                factory = { ctx ->
                    TestFrameView(ctx).apply {
                        pivotX = 0f
                        pivotY = 0f
                        previewViewHolder[0] = this
                    }
                },
                // 测量给真机像素、上报只占缩放后尺寸：几何按真机算，节点不溢出预览框
                modifier = Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(realWpx, realHpx))
                    layout(scaledWpx, scaledHpx) { placeable.place(0, 0) }
                },
                update = { v ->
                    v.scaleX = scale
                    v.scaleY = scale
                    v.isAdvancedMode = previewAdvanced
                    v.invalidate()   // 设置变化后必须主动重绘（原生 View 不会自己刷新）
                }
            )
            // 预览图内的模式切换按钮；缩成小窗或纯净模式时渐隐并停止响应点击。
            val modeBtnAlpha by animateFloatAsState(
                targetValue = if (ThemeSettings.isCompactModeEnabled) 0f
                else (1f - shrink / 0.2f).coerceIn(0f, 1f),
                label = "modeBtnAlpha"
            )
            Text(
                text = if (previewAdvanced) "切换圆角模式" else "切换精度模式",
                fontSize = 12.sp,
                color = Color(
                    if (ThemeSettings.textFollowsLine) ThemeSettings.testLineColor
                    else ThemeSettings.testLineTextColor
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // 位置/大小按"真机→预览"缩放比复刻（真机按钮 150px/50px 边距、12sp 文字）
                    .offset { IntOffset(-(50 * scale).roundToInt(), (150 * scale).roundToInt()) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0f)
                        alpha = modeBtnAlpha
                    }
                    .clip(RoundedCornerShape(10.dp))
                    // 精度预览 + 未开"用自定义背景"时，测试页的按钮底色是黑 27%，这里对齐
                    .background(
                        if (previewAdvanced && !ThemeSettings.precisionUsesCustomBg) Color(0x44000000)
                        else Color(ThemeSettings.testLineBgColor).copy(alpha = 0x44 / 255f)
                    )
                    // 震感由 TestFrameView.isAdvancedMode 的 setter 触发
                    .then(
                        if (modeBtnAlpha > 0.01f) Modifier.clickable { previewAdvanced = !previewAdvanced }
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            }
        }
    }
}

/**
 * 背景色调色面板：复用设置页的 [SingleColorModePanel]（RGB + 十六进制 + 预设），
 * 状态自成一套，保存写到 testLineBgColor。
 */
@Composable
private fun BackgroundColorPanel() {
    val context = LocalContext.current
    val view = LocalView.current
    val initial = Color(ThemeSettings.testLineBgColor)
    val redAnim = remember { Animatable(initial.red) }
    val greenAnim = remember { Animatable(initial.green) }
    val blueAnim = remember { Animatable(initial.blue) }

    val currentArgb = android.graphics.Color.rgb(redAnim.value, greenAnim.value, blueAnim.value)
    var instantTarget by remember { mutableStateOf<Int?>(null) }
    val effectiveArgb = instantTarget ?: currentArgb
    val hexS = String.format(
        java.util.Locale.US, "#%02X%02X%02X",
        (redAnim.value * 255).toInt(), (greenAnim.value * 255).toInt(), (blueAnim.value * 255).toInt()
    )
    var hexText by remember { mutableStateOf(hexS) }
    var isHexFocused by remember { mutableStateOf(false) }
    var presetsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(redAnim.value, greenAnim.value, blueAnim.value) {
        if (!isHexFocused) hexText = hexS
        ThemeSettings.saveLineBgColor(context, currentArgb)
        // 背景色与线条色相同 → 线条看不见，自动把线条换成对比色（换完即不再相等，不会反复触发）
        if (ThemeSettings.testLineColor == currentArgb) {
            val candidates = listOf(
                0xFFFFFFFF.toInt(),   // 白
                0xFF72A7FF.toInt(),   // 蓝
                0xFFFF3B30.toInt(),   // 红
                0xFFFFD60A.toInt()    // 黄
            )
            ThemeSettings.saveLineColor(context, candidates.filter { it != currentArgb }.random())
            android.widget.Toast.makeText(context, "线条与背景颜色相同，已自动更换线条颜色", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // 外部（预设行/其它入口）改动后同步滑块
    LaunchedEffect(ThemeSettings.testLineBgColor) {
        val t = Color(ThemeSettings.testLineBgColor)
        if (t.toArgb() != currentArgb) {
            instantTarget = null
            redAnim.snapTo(t.red)
            greenAnim.snapTo(t.green)
            blueAnim.snapTo(t.blue)
        }
    }

    // 预设按深浅排序（黑 → 白）
    val presets = listOf(
        Color.Black,
        Color(0xFF303030),
        Color(0xFFD8D8D8),
        Color.White,
        Color(0xFF72A7FF),
        Color(0xFF00FF00)
    )

    // 精度模式背景开关放最上面：开启后黑边遮挡测试的精度模式也使用下面的自定义背景
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("「精度模式」使用自定义背景", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "关闭则精度模式仍使用默认黑色背景",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = ThemeSettings.precisionUsesCustomBg,
            onCheckedChange = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                ThemeSettings.savePrecisionUsesCustomBg(context, it)
            }
        )
    }
    Spacer(Modifier.height(16.dp))

    // 补 SingleColorModePanel 缺的左右内边距
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
    SingleColorModePanel(
        redAnim = redAnim,
        greenAnim = greenAnim,
        blueAnim = blueAnim,
        currentColorArgb = currentArgb,
        instantTargetColor = instantTarget,
        effectiveArgb = effectiveArgb,
        hexText = hexText,
        isHexFocused = isHexFocused,
        onHexChange = { hexText = it },
        onHexFocusChange = { isHexFocused = it },
        onInstantColorChange = { instantTarget = it },
        allPresets = presets + ThemeSettings.bgUserPresets.map { Color(it) },
        // 背景色的预设保存到独立存储
        onSavePreset = { ThemeSettings.addBgUserPreset(context, it) },
        onRemovePreset = { ThemeSettings.removeBgUserPreset(context, it) },
        defaultPresets = presets,
        isCurrentInPresets = (presets + ThemeSettings.bgUserPresets.map { Color(it) }).any { it.toArgb() == effectiveArgb },
        isPresetsExpanded = presetsExpanded,
        onPresetsExpandedChange = { presetsExpanded = it }
    )
    }
}

/**
 * 文字色调色面板：与背景色面板同结构，保存写到 testLineTextColor。
 * 与背景色相同时自动换一个对比色（否则文字看不清）。
 */
@Composable
private fun TextColorPanel() {
    val context = LocalContext.current
    val view = LocalView.current
    val initial = Color(ThemeSettings.testLineTextColor)
    val redAnim = remember { Animatable(initial.red) }
    val greenAnim = remember { Animatable(initial.green) }
    val blueAnim = remember { Animatable(initial.blue) }

    val currentArgb = android.graphics.Color.rgb(redAnim.value, greenAnim.value, blueAnim.value)
    var instantTarget by remember { mutableStateOf<Int?>(null) }
    val effectiveArgb = instantTarget ?: currentArgb
    val hexS = String.format(
        java.util.Locale.US, "#%02X%02X%02X",
        (redAnim.value * 255).toInt(), (greenAnim.value * 255).toInt(), (blueAnim.value * 255).toInt()
    )
    var hexText by remember { mutableStateOf(hexS) }
    var isHexFocused by remember { mutableStateOf(false) }
    var presetsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(redAnim.value, greenAnim.value, blueAnim.value) {
        if (!isHexFocused) hexText = hexS
        ThemeSettings.saveLineTextColor(context, currentArgb)
        // 与背景色相同 → 文字看不清，自动换成对比色（换完即不再相等，不会反复触发）
        if (ThemeSettings.testLineBgColor == currentArgb) {
            val candidates = listOf(
                0xFFFFFFFF.toInt(),   // 白
                0xFF72A7FF.toInt(),   // 蓝
                0xFFFF3B30.toInt(),   // 红
                0xFFFFD60A.toInt()    // 黄
            )
            ThemeSettings.saveLineTextColor(context, candidates.filter { it != currentArgb }.random())
            android.widget.Toast.makeText(context, "文字与背景颜色相同，已自动更换文字颜色", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // 外部改动后同步滑块
    LaunchedEffect(ThemeSettings.testLineTextColor) {
        val t = Color(ThemeSettings.testLineTextColor)
        if (t.toArgb() != currentArgb) {
            instantTarget = null
            redAnim.snapTo(t.red)
            greenAnim.snapTo(t.green)
            blueAnim.snapTo(t.blue)
        }
    }

    // 预设与线条色共用同一套
    val presets = listOf(
        Color.White,
        Color(0xFF72A7FF),
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.error
    )

    // 纯净模式
    var showPureModeHelp by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("纯净模式", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "帮助",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            showPureModeHelp = !showPureModeHelp
                        }
                        .padding(4.dp)
                )
            }
            AnimatedVisibility(visible = showPureModeHelp) {
                Text(
                    "开启后将隐藏测试页圆角模式中部部分文案和底部参数\n隐藏右上角切换按钮，改为双击屏幕任意位置切换模式",
                    fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Switch(
            checked = ThemeSettings.isCompactModeEnabled,
            onCheckedChange = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                ThemeSettings.saveCompactModeConfig(context, it)
            }
        )
    }
    Spacer(Modifier.height(16.dp))

    // 机型文字不透明度
    val nameScope = rememberCoroutineScope()
    val nameFocusManager = LocalFocusManager.current
    val nameAnim = remember { Animatable(ThemeSettings.deviceNameOpacityPct.toFloat()) }
    var nameInput by remember { mutableStateOf(ThemeSettings.deviceNameOpacityPct.toString()) }
    var isNameFocused by remember { mutableStateOf(false) }

    LaunchedEffect(nameAnim.value) {
        if (!isNameFocused) nameInput = nameAnim.value.roundToInt().toString()
        ThemeSettings.saveDeviceNameOpacityPct(context, nameAnim.value.roundToInt())
    }

    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("机型文字不透明度", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            // 重置按钮
            IconButton(
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    nameInput = "100"
                    nameScope.launch {
                        nameAnim.animateTo(100f, tween(durationMillis = 800, easing = FastOutSlowInEasing))
                    }
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重置",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.weight(1f))

            // 数值编辑框
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                BasicTextField(
                    value = nameInput,
                    onValueChange = { newVal ->
                        if (newVal.length <= 3) {
                            val num = newVal.toIntOrNull()
                            if (num != null) {
                                val clamped = num.coerceIn(40, 120)
                                nameInput = clamped.toString()
                                nameScope.launch {
                                    nameAnim.animateTo(clamped.toFloat(), tween(400, easing = FastOutSlowInEasing))
                                }
                            } else if (newVal.isEmpty()) {
                                nameInput = newVal
                            }
                        }
                    },
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = 35.dp)
                        .onFocusChanged { isNameFocused = it.isFocused },
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { nameFocusManager.clearFocus() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
                Text(
                    "%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        HapticSlider(
            l = "",
            c = MaterialTheme.colorScheme.primary,
            v = (nameAnim.value - 40f) / 80f
        ) {
            nameFocusManager.clearFocus()
            nameScope.launch { nameAnim.snapTo(it * 80f + 40f) }
        }
    }
    Spacer(Modifier.height(16.dp))

    // 两个"跟随"开关
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("文字跟随线条颜色", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "开启后所有文字都用线条颜色（关闭后才可用自定义文字色）",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = ThemeSettings.textFollowsLine,
                onCheckedChange = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    ThemeSettings.saveTextFollowsLine(context, it)
                }
            )
        }
        // 文字跟随线条开启时，下面的开关与调色收起隐藏
        AnimatedVisibility(
        visible = !ThemeSettings.textFollowsLine,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
            Column {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("「黑边遮挡测试」跟随文字色", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "关闭则这一行仍跟随线条颜色",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ThemeSettings.titleFollowsText,
                onCheckedChange = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    ThemeSettings.saveTitleFollowsText(context, it)
                }
            )
        }
        Spacer(Modifier.height(16.dp))
            }
        }
    }

    AnimatedVisibility(
        visible = !ThemeSettings.textFollowsLine,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        SingleColorModePanel(
            redAnim = redAnim,
            greenAnim = greenAnim,
            blueAnim = blueAnim,
            currentColorArgb = currentArgb,
            instantTargetColor = instantTarget,
            effectiveArgb = effectiveArgb,
            hexText = hexText,
            isHexFocused = isHexFocused,
            onHexChange = { hexText = it },
            onHexFocusChange = { isHexFocused = it },
            onInstantColorChange = { instantTarget = it },
            allPresets = ThemeSettings.userPresets.map { Color(it) },
            defaultPresets = presets,
            isCurrentInPresets = (presets + ThemeSettings.userPresets.map { Color(it) }).any { it.toArgb() == effectiveArgb },
            isPresetsExpanded = presetsExpanded,
            onPresetsExpandedChange = { presetsExpanded = it }
        )
    }
    }
}
