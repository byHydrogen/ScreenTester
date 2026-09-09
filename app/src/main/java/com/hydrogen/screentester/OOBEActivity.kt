@file:OptIn(ExperimentalHazeMaterialsApi::class)

package com.hydrogen.screentester

import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// OOBE 状态管理
object OOBEState {
    var currentStep by mutableIntStateOf(0)
    var isCompleted by mutableStateOf(false)
}

class OOBEActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DownloadState.cleanupCache(this)
        ThemeSettings.loadConfig(this)

        // 键盘弹起时不缩小窗口，防止 Canvas 红线被推上去
        // 输入框通过 imePadding() 保证可滚动到可见区域
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 渲染进摄像头挖孔区域，避免全屏时挖孔处出现黑条（等效系统"刘海屏：自动匹配"）
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // 检查是否已完成 OOBE
        val prefs = getSharedPreferences("oobe_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("oobe_completed", false)) {
            goToMain()
            return
        }

        // 全新安装标记（走 OOBE 即首次安装，用于区分新装与旧版本升级）
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit().putBoolean("fresh_install", true).apply()

        OOBEState.currentStep = 0
        OOBEState.isCompleted = false

        setContent {
            ScreenTesterTheme {
                OOBEContent(
                    onComplete = { completeOOBE() },
                    onSkip = { completeOOBE() }
                )
            }
        }
    }

    private fun completeOOBE() {
        getSharedPreferences("oobe_prefs", MODE_PRIVATE)
            .edit().putBoolean("oobe_completed", true).apply()
        OOBEState.isCompleted = true
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OOBEContent(onComplete: () -> Unit, onSkip: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 6 })
    val view = LocalView.current
    val context = LocalContext.current

    val focusManager = LocalFocusManager.current

    var isCalibrating by remember { mutableStateOf(ThemeSettings.useCustomRadius) }

    // 教程状态
    val tutorialTargets = remember { mutableStateMapOf<Int, Rect>() }
    var tutorialActive by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    // 校准状态
    val calibPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var isG2Enabled by remember { mutableStateOf(calibPrefs.getBoolean("is_g2_enabled", false)) }
    val calibTLX = remember { Animatable(calibPrefs.getFloat("r_tl_x", 0f)) }
    val calibTRX = remember { Animatable(calibPrefs.getFloat("r_tr_x", 0f)) }
    val calibBLX = remember { Animatable(calibPrefs.getFloat("r_bl_x", 0f)) }
    val calibBRX = remember { Animatable(calibPrefs.getFloat("r_br_x", 0f)) }
    val calibTLY = remember { Animatable(calibPrefs.getFloat("r_tl_y", 0f)) }
    val calibTRY = remember { Animatable(calibPrefs.getFloat("r_tr_y", 0f)) }
    val calibBLY = remember { Animatable(calibPrefs.getFloat("r_bl_y", 0f)) }
    val calibBRY = remember { Animatable(calibPrefs.getFloat("r_br_y", 0f)) }

    val localDensity = LocalDensity.current
    val systemRadius = try {
        val insets = (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets
        insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f
    } catch (_: Exception) { 100f }

    val calibTL = remember { Animatable(if (ThemeSettings.radiusTL < 0f) systemRadius else ThemeSettings.radiusTL) }
    val calibTR = remember { Animatable(if (ThemeSettings.radiusTR < 0f) systemRadius else ThemeSettings.radiusTR) }
    val calibBL = remember { Animatable(if (ThemeSettings.radiusBL < 0f) systemRadius else ThemeSettings.radiusBL) }
    val calibBR = remember { Animatable(if (ThemeSettings.radiusBR < 0f) systemRadius else ThemeSettings.radiusBR) }
    // 联动模式沿用已保存的值，与校准车间同一份（旧用户切过四角独立，这里进来也是四角独立）
    var isLinked by remember { mutableStateOf(ThemeSettings.isCalibrationLinked) }
    val calibSliderSpec = tween<Float>(800, easing = FastOutSlowInEasing)

    // 切回全局同步时把其余三角对齐到左上角（控制行与教程都会调，定义成一处）
    val setLinkedMode: (Boolean) -> Unit = { newLinked ->
        if (newLinked != isLinked) {
            val wasLinked = isLinked
            isLinked = newLinked
            if (newLinked && !wasLinked) {
                scope.launch { calibTR.animateTo(calibTL.value, calibSliderSpec) }
                scope.launch { calibBL.animateTo(calibTL.value, calibSliderSpec) }
                scope.launch { calibBR.animateTo(calibTL.value, calibSliderSpec) }
                scope.launch { calibTRX.animateTo(calibTLX.value, calibSliderSpec) }
                scope.launch { calibBLX.animateTo(calibTLX.value, calibSliderSpec) }
                scope.launch { calibBRX.animateTo(calibTLX.value, calibSliderSpec) }
                scope.launch { calibTRY.animateTo(calibTLY.value, calibSliderSpec) }
                scope.launch { calibBLY.animateTo(calibTLY.value, calibSliderSpec) }
                scope.launch { calibBRY.animateTo(calibTLY.value, calibSliderSpec) }
            }
        }
    }

    val radiusSet = remember {
        CornerRadiusSet(
            calibTL, calibTR, calibBL, calibBR,
            calibTLX, calibTRX, calibBLX, calibBRX,
            calibTLY, calibTRY, calibBLY, calibBRY
        )
    }

    // 拖拽调整：只在本次 OOBE 内临时生效，不写入 settings。进入时留快照供 × 回滚
    var dragAdjustMode by remember { mutableStateOf(false) }
    var dragUserDragging by remember { mutableStateOf(false) }
    var dragSnapshot by remember { mutableStateOf<CornerRadiusSnapshot?>(null) }
    var dragTutorialActive by remember { mutableStateOf(false) }
    var dragTutorialStep by remember { mutableIntStateOf(0) }

    // 控制行只在页面里这一份，拖拽模式下整块平移到屏幕中央（见 OOBECornerCalibrationStep）

    val enterDragMode: () -> Unit = {
        focusManager.clearFocus()
        dragSnapshot = radiusSet.snapshot()
        dragAdjustMode = true
        dragUserDragging = false
        // 首次进入拖拽时补一次拖拽教程
        if (!tutorialActive && !isDragTutorialShown(context)) {
            dragTutorialStep = 0
            dragTutorialActive = true
        }
    }

    // rollback = true 回滚到进入时的快照（× 与返回键），false 保留本次调整（✓）
    val exitDragMode: (Boolean) -> Unit = { rollback ->
        if (rollback) {
            dragSnapshot?.let {
                radiusSet.restore(scope, it, tween<Float>(400, easing = FastOutSlowInEasing))
            }
        }
        dragSnapshot = null
        dragAdjustMode = false
        dragUserDragging = false
        dragTutorialActive = false
    }

    // 页内与根部两个实例共用同一份回调
    val onToggleG2Mode: () -> Unit = {
        val next = !isG2Enabled
        isG2Enabled = next
        // 注意必须调用 Editor 的 apply() 提交，只写进 Editor 是不落盘的
        calibPrefs.edit().putBoolean("is_g2_enabled", next).apply()
    }
    val toggleDragMode: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (dragAdjustMode) exitDragMode(false) else enterDragMode()
    }
    // 用户手动切换联动模式时才落盘；教程里强制的「全局同步」不落盘
    val toggleLinkedMode: () -> Unit = {
        val next = !isLinked
        setLinkedMode(next)
        ThemeSettings.saveCalibrationLinkedMode(context, next)
    }

    // 拖拽模式下返回键只退出拖拽，语义等同 ✓（保留改动，避免误按丢数据）
    BackHandler(enabled = dragAdjustMode) { exitDragMode(false) }

    // 普通教程期间强制回到普通界面，与校准车间一致
    LaunchedEffect(tutorialActive, tutorialStep) {
        if (tutorialActive && dragAdjustMode) exitDragMode(false)
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 1 && dragAdjustMode) exitDragMode(false)
    }

    // 启动时检测圆心数据可用性（校准步骤之前）
    LaunchedEffect(Unit) {
        ThemeSettings.checkCenterDataAvailability(view)
    }

    LaunchedEffect(pagerState.currentPage) {
        OOBEState.currentStep = pagerState.currentPage
    }

    val isDark = when (ThemeSettings.darkModeState) {
        DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkModeConfig.LIGHT -> false
        DarkModeConfig.DARK -> true
    }

    val backgroundBrush = DeviceUtils.backgroundBrush(isDark)

    // 校准模式背景渐变动画
    val whiteOverlayAlpha by animateFloatAsState(
        targetValue = if (isCalibrating && pagerState.currentPage == 1) 1f else 0f,
        animationSpec = tween(500),
        label = "whiteOverlay"
    )

    // 底部导航浅色主题混合
    val isLightMode = isCalibrating && pagerState.currentPage == 1
    val oobeLightScheme = remember { dynamicLightColorScheme(context) }
    val oobeDarkScheme = MaterialTheme.colorScheme
    val oobeBlend by animateFloatAsState(if (isLightMode) 1f else 0f, tween(500), label = "oobeBlend")
    val oobeBlendedScheme = oobeDarkScheme.copy(
        surface = lerp(oobeDarkScheme.surface, oobeLightScheme.surface, oobeBlend),
        background = lerp(oobeDarkScheme.background, oobeLightScheme.background, oobeBlend),
        onSurface = lerp(oobeDarkScheme.onSurface, oobeLightScheme.onSurface, oobeBlend),
        onSurfaceVariant = lerp(oobeDarkScheme.onSurfaceVariant, oobeLightScheme.onSurfaceVariant, oobeBlend),
        primary = lerp(oobeDarkScheme.primary, oobeLightScheme.primary, oobeBlend),
        onPrimary = lerp(oobeDarkScheme.onPrimary, oobeLightScheme.onPrimary, oobeBlend),
        primaryContainer = lerp(oobeDarkScheme.primaryContainer, oobeLightScheme.primaryContainer, oobeBlend),
        onPrimaryContainer = lerp(oobeDarkScheme.onPrimaryContainer, oobeLightScheme.onPrimaryContainer, oobeBlend),
        surfaceVariant = lerp(oobeDarkScheme.surfaceVariant, oobeLightScheme.surfaceVariant, oobeBlend),
        secondary = lerp(oobeDarkScheme.secondary, oobeLightScheme.secondary, oobeBlend),
        onSecondary = lerp(oobeDarkScheme.onSecondary, oobeLightScheme.onSecondary, oobeBlend),
        secondaryContainer = lerp(oobeDarkScheme.secondaryContainer, oobeLightScheme.secondaryContainer, oobeBlend),
        onSecondaryContainer = lerp(oobeDarkScheme.onSecondaryContainer, oobeLightScheme.onSecondaryContainer, oobeBlend),
    )

    val skipAlpha by animateFloatAsState(
        targetValue = if (pagerState.currentPage in 1..5) 1f else 0f,
        animationSpec = tween(400),
        label = "skipAlpha"
    )

    val buttonText by remember { derivedStateOf {
        when (pagerState.currentPage) {
            0 -> "开始"
            5 -> "开始使用"
            else -> "下一步"
        }
    } }
    val buttonWidth by animateDpAsState(
        targetValue = when (pagerState.currentPage) {
            0 -> 100.dp
            5 -> 150.dp
            else -> 120.dp
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "buttonWidth"
    )

    // 欢迎页按钮淡入
    val animEnabled = ThemeSettings.isAnimationEnabled
    var welcomeAnimPlayed by remember { mutableStateOf(false) }
    val buttonAlpha = remember { Animatable(if (animEnabled && pagerState.currentPage == 0) 0f else 1f) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0 && animEnabled && !welcomeAnimPlayed) {
            welcomeAnimPlayed = true
            buttonAlpha.snapTo(0f)
            delay(440)
            buttonAlpha.animateTo(1f, tween(300))
        } else {
            buttonAlpha.snapTo(1f)
        }
    }

    var realtimeThickness by remember { mutableFloatStateOf(ThemeSettings.testLineThickness) }
    var realtimeSegmentLength by remember { mutableFloatStateOf(if (ThemeSettings.multiColorSegmentLength == 0f) 1f else ThemeSettings.multiColorSegmentLength) }
    var isDragging by remember { mutableStateOf(false) }

    // 底栏遮罩显隐：仅当内容实际延伸到悬浮底栏之下时显示。
    // 固定短页（欢迎/纯净模式/完成）内容不触及底栏，直接隐藏；
    // 滚动步骤由自身 scroll 状态上报（滚动中=有内容经过→显示，滚到底=尾部留白→隐藏）
    var navMaskVisible by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0 || pagerState.currentPage == 3 || pagerState.currentPage == 5) {
            navMaskVisible = false
        }
    }
    val navMaskAlpha by animateFloatAsState(
        targetValue = if (navMaskVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "navMaskAlpha"
    )

    // 圆角校准(步骤1)与测试线条(步骤2)对显示精度敏感：浅色模式下混色较鲜艳、影响观察，
    // 这两步淡出到静态背景；深色模式混色本身很暗，冻结即可。切换带渐变过渡
    val sensitiveStep = pagerState.currentPage == 1 || pagerState.currentPage == 2
    val mixVisible = ThemeSettings.aboutDynamicMixEnabled && !(sensitiveStep && !isDark)
    val mixAlpha by animateFloatAsState(
        targetValue = if (mixVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "oobeMixAlpha"
    )

    // 悬浮底栏遮罩的模糊源（仅底栏模糊开关开启时挂载）：背景与页面内容整体录进源，
    // 遮罩效果节点是其兄弟节点，不会采到自身
    val oobeHazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // clipToBounds：混色光斑绘制半径大于页面，需裁剪在自身范围内
                    .clipToBounds()
                    .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        // —— 可磨砂内容区：单源挂载，与底栏遮罩效果节点分层 ——
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ThemeSettings.navBarBlurEnabled) Modifier.hazeSource(oobeHazeState) else Modifier)
        ) {
        // 静态背景层（常驻，兼作混色关闭/淡出时的背景）
        Box(modifier = Modifier.fillMaxSize().background(backgroundBrush))

        // 动态混色层：混色开启时叠在静态层上，随 mixAlpha 渐显/渐隐；
        // 敏感步骤暂停时间累计，恢复时从冻结位置无缝续播。
        // 淡出未结束前保留图层，开关切换才有渐变过渡（否则会瞬间消失/出现）
        if (ThemeSettings.aboutDynamicMixEnabled || mixAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = mixAlpha }
                    .dynamicMixBackground(isDark, running = !sensitiveStep)
            )
        }

        // 校准模式浅灰色背景叠加层
        if (whiteOverlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0xFFDFDFDF).copy(alpha = whiteOverlayAlpha) else Color.White.copy(alpha = whiteOverlayAlpha))
            )
        }

        // 层级 1：页面内容占满全屏（悬浮底栏下方可滚过内容）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 5,
            userScrollEnabled = false
        ) { pageIndex ->
                when (pageIndex) {
                    0 -> OOBEWelcomeStep(isDark, isVisible = pagerState.currentPage == 0, onContentUnderBar = { navMaskVisible = it })
                    1 -> OOBECornerCalibrationStep(
                        isDark,
                        isVisible = pagerState.currentPage == 1,
                        useCustom = isCalibrating,
                        onCalibrationChanged = { isCalibrating = it },
                        isG2Enabled = isG2Enabled,
                        onToggleG2 = onToggleG2Mode,
                        systemRadius = systemRadius,
                        isLinked = isLinked,
                        onLinkedChange = setLinkedMode,
                        tlAnim = calibTL, trAnim = calibTR, blAnim = calibBL, brAnim = calibBR,
                        dragAdjustMode = dragAdjustMode,
                        onToggleDrag = toggleDragMode,
                        onToggleLinked = toggleLinkedMode,
                        tutorialTargets = tutorialTargets,
                        tutorialActive = tutorialActive,
                        tutorialStep = tutorialStep,
                        onTutorialToggle = { tutorialActive = it },
                        onTutorialStepChange = { tutorialStep = it },
                        onContentUnderBar = { navMaskVisible = it },
                        tlXAnim = calibTLX, trXAnim = calibTRX, blXAnim = calibBLX, brXAnim = calibBRX,
                        tlYAnim = calibTLY, trYAnim = calibTRY, blYAnim = calibBLY, brYAnim = calibBRY
                    )
                    2 -> OOBETestLineStep(isDark,
                        isVisible = pagerState.currentPage == 2,
                        onThicknessChange = { realtimeThickness = it },
                        onSegmentLengthChange = { realtimeSegmentLength = it },
                        onDraggingChange = { isDragging = it },
                        onContentUnderBar = { navMaskVisible = it }
                    )
                    3 -> OOBEPureModeStep(isDark, isVisible = pagerState.currentPage == 3, onContentUnderBar = { navMaskVisible = it })
                    4 -> OOBEAdvancedUIStep(isDark, isVisible = pagerState.currentPage == 4, onContentUnderBar = { navMaskVisible = it })
                    5 -> OOBECompletionStep(isDark, isActive = pagerState.currentPage == 5, isVisible = pagerState.currentPage == 5, onContentUnderBar = { navMaskVisible = it })
                }
            }
        }

        // 悬浮底部导航：按钮位置与原布局一致；上一步/进度指示器/主按钮外套半透明遮罩，
        // 底栏模糊开关开启时遮罩自动变为实时模糊（跟随底栏模糊强度）。
        // 页面指示器仅步骤 1-5 显示，欢迎页隐藏
        // 拖拽模式下底栏淡出：与「欢迎页→校准页」时底栏元素的显隐同口径（淡入淡出+轻微缩放，无位移）。
        // 触摸拦截由顶层拖拽覆盖层负责，这里只管视觉。
        val navBarAnimSpec: AnimationSpec<Float> =
            if (dragAdjustMode) tween(220, easing = FastOutSlowInEasing)
            else tween(400, easing = FastOutSlowInEasing)
        val navBarAlpha by animateFloatAsState(
            targetValue = if (dragAdjustMode) 0f else 1f,
            animationSpec = navBarAnimSpec,
            label = "oobeNavBarAlpha"
        )
        val navBarScale by animateFloatAsState(
            targetValue = if (dragAdjustMode) 0.9f else 1f,
            animationSpec = navBarAnimSpec,
            label = "oobeNavBarScale"
        )

        MaterialTheme(colorScheme = oobeBlendedScheme) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
                    .blockTouchesWhile(dragAdjustMode)
                    .graphicsLayer {
                        alpha = navBarAlpha
                        scaleX = navBarScale
                        scaleY = navBarScale
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 上一步：整块（含遮罩）用 skipAlpha 统一淡入淡出；隐藏期间禁点、
                    // 仍占位，保持下一步按钮右对齐（不再分支移除，消失才有动画）
                    Box(
                        modifier = Modifier
                            .alpha(skipAlpha)
                            .heightIn(min = 40.dp)
                            .clip(G2Shapes.button)
                            .clickable(enabled = pagerState.currentPage in 1..5) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // 遮罩层：内容滚过底栏时渐显，无内容经过时渐隐（只隐遮罩不隐文字）
                        if (navMaskVisible || navMaskAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer { alpha = navMaskAlpha }
                                    .oobeNavMask(oobeHazeState)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), G2Shapes.button)
                            )
                        }
                        Text(
                            text = "上一步",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    // 开始 / 下一步 / 开始使用
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (pagerState.currentPage < 5) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onComplete()
                            }
                        },
                        modifier = Modifier
                            .width(buttonWidth)
                            .heightIn(min = 40.dp)
                            .graphicsLayer { alpha = buttonAlpha.value },
                        shape = G2Shapes.button,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        AnimatedContent(
                            targetState = buttonText,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "btnText"
                        ) { text ->
                            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 进度指示器：固定高度槽位（欢迎页淡出隐藏，按钮行位置不跳动）
                Box(
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = pagerState.currentPage != 0,
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
                        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200))
                    ) {
                        // 胶囊遮罩底：随内容是否经过底栏渐显/渐隐（只隐遮罩不隐圆点）
                        Box(modifier = Modifier.clip(CircleShape)) {
                            if (navMaskVisible || navMaskAlpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = navMaskAlpha }
                                        .oobeNavMask(oobeHazeState)
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                            // 5 个进度点对应步骤 1-5（欢迎页无点）
                            repeat(5) { index ->
                                val isActive = pagerState.currentPage == index + 1
                                val width by animateDpAsState(
                                    targetValue = if (isActive) 24.dp else 8.dp,
                                    animationSpec = tween(200),
                                    label = "dotWidth"
                                )
                                val alpha by animateFloatAsState(
                                    targetValue = if (isActive) 1f else 0.3f,
                                    animationSpec = tween(200),
                                    label = "dotAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .width(width)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                                )
                            }
                            }
                        }
                    }
                }
            }
        }

        // 层级 2：全局圆角和边框预览
        OOBELiveBorderPreview(
            pagerState, realtimeThickness, realtimeSegmentLength, isDragging,
            isG2Enabled = isG2Enabled,
            isLegacyCurveEnabled = ThemeSettings.isLegacyCurveEnabled,
            primaryColor = oobeBlendedScheme.primary,
            offTLX = calibTLX.value, offTLY = calibTLY.value,
            offTRX = calibTRX.value, offTRY = calibTRY.value,
            offBLX = calibBLX.value, offBLY = calibBLY.value,
            offBRX = calibBRX.value, offBRY = calibBRY.value
        )

        // 层级 2.5：拖拽调整覆盖层（控制点 + 工具层）
        MaterialTheme(colorScheme = oobeBlendedScheme) {
            OobeCornerDragOverlay(
                visible = dragAdjustMode,
                radiusSet = radiusSet,
                isLinked = isLinked,
                isG2Enabled = isG2Enabled,
                systemRadius = systemRadius,
                strokeWidth = realtimeThickness,
                handleColor = oobeBlendedScheme.primary,
                onDraggingChanged = { dragUserDragging = it },
                onExitKeep = { exitDragMode(false) },
                onExitRollback = { exitDragMode(true) },
                tutorialTargets = tutorialTargets
            )
        }

        // 层级 3：全屏彩带
        AnimatedVisibility(
            visible = pagerState.currentPage == 5,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(800))
        ) {
            ConfettiFireworks(
                modifier = Modifier.fillMaxSize(),
                isActive = true
            )
        }

        // 层级 4：教程引导
        if (tutorialActive) {
            val steps = listOf(
                "调节模式" to "点击可切换调节模式\n全局同步调节模式下四角圆角一致\n若您的屏幕四角曲率不同可点击按钮切换为四角独立调节，该模式下可分别调整四个角至贴合手机屏幕圆角",
                "G2 平滑圆角" to "开启后使用 G2 连续曲率算法，圆角线条更加圆润流畅，贴合屏幕物理曲率",
                "拖拽调整" to "点击这个手势按钮即可进入拖拽调整模式。进入后可以直接拖动四角控制点，不需要手动计算圆角数值",
                "基础圆角半径" to "您可通过拖动滑块，点击 +/- 按钮或点击数字调起键盘输入数字调节屏幕四个角的圆角半径大小，实时预览线条变化",
                "卡片与调节操作说明" to "长按卡片可固定，点击重置按钮可恢复默认，点击 +/- 按钮可 ± 0.1，长按 +/- 按钮可快速连续加减",
                "横向 X 轴曲率修正" to "微调圆角在水平方向的偏移量",
                "纵向 Y 轴曲率修正" to "微调圆角在垂直方向的偏移量"
            )

            val targetIndex = when (tutorialStep) {
                0 -> 0
                1 -> 1
                2 -> OOBE_DRAG_TARGET_GESTURE_BUTTON
                3, 4 -> 2
                5 -> 3
                6 -> 4
                else -> 0
            }

            TutorialOverlay(
                currentStep = tutorialStep,
                totalSteps = 7,
                targetBounds = tutorialTargets[targetIndex],
                title = steps[tutorialStep].first,
                description = steps[tutorialStep].second,
                onPrevious = if (tutorialStep > 0) {{ tutorialStep-- }} else null,
                onNext = {
                    if (tutorialStep < 6) {
                        tutorialStep++
                    } else {
                        tutorialActive = false
                        markTutorialShown(context)
                    }
                },
                onSkip = {
                    tutorialActive = false
                    markTutorialShown(context)
                },
                isLastStep = tutorialStep == 6
            )
        }

        // 拖拽调整专属教程：复用同一个 TutorialOverlay，但步骤与高亮目标完全独立。
        // 只在第一次真正进入拖拽调整时出现，标记与校准车间共用（看过一次就不再重复打扰）。
        if (dragTutorialActive) {
            val dragSteps = listOf(
                "四角控制点" to "进入拖拽调整后，屏幕四个角都会出现控制点。拖动对应控制点，就能直接贴合实际屏幕圆角",
                "全局同步调节" to "全局同步调节会让四个角保持一致，适合四角曲率基本相同的屏幕。需要分别贴合四个角时，可切换为四角独立调节分别调节",
                "G2 平滑圆角" to "建议先开启 G2 平滑，再开始拖拽调整。开启后圆角曲线会更加连续，更贴合实际屏幕圆角",
                "拖拽调整" to "在拖拽调整模式下，点击这个手势按钮可退出拖拽调整模式回到卡片滑块调整模式",
                "选择调整方式" to "这里可以选择调整什么：全部调整、圆角半径、横向修正或纵向修正。“全部调整”一次拖动即可同时调整圆角半径与横纵修正，可先用它让线条轮廓大致贴合屏幕圆角，再用其它模式单独微调",
                "拖一下就能看到变化" to "把左上角控制点向屏幕内部拖动试试看。上方这一整块就是实际调整区域，轮廓会实时变化，不需要计算数值，直接拖拽调整至与手机屏幕圆角贴合",
                "重置与保留" to "↻ 重置本次拖拽调整\n× 放弃本次拖拽改动并回到普通界面\n✓ 保留本次调整并回到普通界面"
            )

            // 演示框贴住左上、扩展到控制点外侧，下边不超过模式选择器
            val demoFrame = if (dragTutorialStep == 5) {
                val handle = tutorialTargets[OOBE_DRAG_TARGET_HANDLE]
                val controlRow = tutorialTargets[OOBE_DRAG_TARGET_MODE_SELECTOR]
                if (handle == null || controlRow == null) null else {
                    val edgePad = with(localDensity) { 10.dp.toPx() }
                    val expansion = with(localDensity) { 30.dp.toPx() }
                    val minSize = with(localDensity) { 82.dp.toPx() }
                    val anchoredLeft = edgePad
                    val anchoredTop = edgePad
                    val right = maxOf(anchoredLeft + minSize, handle.right + expansion)
                    val desiredBottom = maxOf(anchoredTop + minSize, handle.bottom + expansion)
                    val bottom = minOf(controlRow.top - edgePad, desiredBottom)
                    Rect(anchoredLeft, anchoredTop, right, maxOf(anchoredTop + 1f, bottom))
                }
            } else null

            val dragTargetIndex = when (dragTutorialStep) {
                0 -> OOBE_DRAG_TARGET_HANDLE
                1 -> 0
                2 -> 1
                3 -> OOBE_DRAG_TARGET_GESTURE_BUTTON
                4 -> OOBE_DRAG_TARGET_MODE_SELECTOR
                6 -> OOBE_DRAG_TARGET_ACTIONS
                else -> OOBE_DRAG_TARGET_HANDLE
            }

            TutorialOverlay(
                currentStep = dragTutorialStep,
                totalSteps = 7,
                targetBounds = if (dragTutorialStep == 5) demoFrame else tutorialTargets[dragTargetIndex],
                title = dragSteps[dragTutorialStep].first,
                description = dragSteps[dragTutorialStep].second,
                dragDemoAnchor = tutorialTargets[OOBE_DRAG_TARGET_HANDLE],
                onPrevious = if (dragTutorialStep > 0) ({ dragTutorialStep-- }) else null,
                onNext = {
                    if (dragTutorialStep < 6) {
                        dragTutorialStep++
                    } else {
                        dragTutorialActive = false
                        markDragTutorialShown(context)
                    }
                },
                onSkip = {
                    dragTutorialActive = false
                    markDragTutorialShown(context)
                },
                isLastStep = dragTutorialStep == 6,
                showDragDemo = dragTutorialStep == 5,
                isUserDragging = dragUserDragging
            )
        }
    }
}

// 卡片颜色：动态混色开启时改半透明让流动背景透出，关闭保持原色
@Composable
fun oobeCardColor(isDark: Boolean): Color {
    val base = DeviceUtils.cardContainerColor(isDark)
    val target = if (ThemeSettings.aboutDynamicMixEnabled) base.copy(alpha = if (isDark) 0.55f else 0.50f) else base
    return animateColorAsState(target, animationSpec = tween(600), label = "oobeCardColor").value
}
fun oobeCardBorder(isDark: Boolean) = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent

// OOBE 悬浮底栏的遮罩底：底栏模糊开关开启时用实时模糊（材质与强度跟随底栏设置），
// 关闭时用半透明表面色
@Composable
fun Modifier.oobeNavMask(hazeState: HazeState): Modifier {
    val containerColor = MaterialTheme.colorScheme.surface

    return if (ThemeSettings.navBarBlurEnabled) {
        val thinStyle = HazeMaterials.thin(containerColor = containerColor)
        this.hazeEffect(hazeState) {
            style = thinStyle.copy(blurRadius = ThemeSettings.navBarBlurRadius.dp)
        }
    } else {
        this.background(containerColor.copy(alpha = 0.9f))
    }
}

// 悬浮底栏遮罩显隐上报：仅本页可见时上报「内容是否延伸到底栏之下」。
// isVisible 参与 key——页面切换时强制重新上报一次，避免沿用离屏期间过期的报告
@Composable
fun ReportNavMaskVisibility(scrollState: ScrollState, isVisible: Boolean, onContentUnderBar: (Boolean) -> Unit) {
    val contentUnderBar = scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue
    LaunchedEffect(isVisible, contentUnderBar) {
        if (isVisible) onContentUnderBar(contentUnderBar)
    }
}

// 1.欢迎页
@Composable
fun OOBEWelcomeStep(isDark: Boolean, isVisible: Boolean, onContentUnderBar: (Boolean) -> Unit = {}) {
    val scrollState = rememberScrollState()
    ReportNavMaskVisibility(scrollState, isVisible, onContentUnderBar)
    val animEnabled = ThemeSettings.isAnimationEnabled
    val logoAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val logoOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val titleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val titleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val subtitleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val subtitleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }

    val slideSpec = tween<Float>(400, easing = FastOutSlowInEasing)

    LaunchedEffect(Unit) {
        if (!animEnabled) return@LaunchedEffect
        launch {
            launch { logoAlpha.animateTo(1f, tween(400)) }
            logoOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch {
            launch { titleAlpha.animateTo(1f, tween(400)) }
            titleOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch { subtitleAlpha.animateTo(1f, tween(400)) }
        subtitleOffset.animateTo(0f, slideSpec)
    }

    // 横屏判定：底部的让位留白只在竖屏加（见下方注释）
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.let {
        it.screenWidthDp > it.screenHeightDp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            // 底部 124dp 是给悬浮底栏让位。单侧内边距会把视觉中心整体上移 62dp，
            .then(if (isLandscape) Modifier else Modifier.padding(bottom = 124.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer { alpha = logoAlpha.value; translationY = logoOffset.value * density }
                .background(color = MaterialTheme.colorScheme.onPrimaryContainer, shape = G2Shapes.logo),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primaryContainer)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "欢迎使用 ScreenTester",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { alpha = titleAlpha.value; translationY = titleOffset.value * density }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "让我们花一分钟完成初始设置",
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = subtitleAlpha.value; translationY = subtitleOffset.value * density }
        )
    }
}

// 2.圆角校准
@Composable
fun OOBECornerCalibrationStep(
    isDark: Boolean,
    isVisible: Boolean,
    useCustom: Boolean,
    onCalibrationChanged: (Boolean) -> Unit,
    isG2Enabled: Boolean,
    onToggleG2: () -> Unit,
    systemRadius: Float,
    isLinked: Boolean,
    onLinkedChange: (Boolean) -> Unit,
    tlAnim: Animatable<Float, *>, trAnim: Animatable<Float, *>,
    blAnim: Animatable<Float, *>, brAnim: Animatable<Float, *>,
    dragAdjustMode: Boolean,
    onToggleDrag: () -> Unit,
    onToggleLinked: () -> Unit,
    tutorialTargets: MutableMap<Int, Rect>,
    tutorialActive: Boolean,
    tutorialStep: Int,
    onTutorialToggle: (Boolean) -> Unit,
    onTutorialStepChange: (Int) -> Unit,
    tlXAnim: Animatable<Float, *>, trXAnim: Animatable<Float, *>,
    blXAnim: Animatable<Float, *>, brXAnim: Animatable<Float, *>,
    tlYAnim: Animatable<Float, *>, trYAnim: Animatable<Float, *>,
    blYAnim: Animatable<Float, *>, brYAnim: Animatable<Float, *>,
    onContentUnderBar: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var activeSection by remember { mutableStateOf<Int?>(0) }
    var pinnedSections by remember { mutableStateOf(setOf<Int>()) }

    // 教程自动滚动：滚动容器（视口）的窗口坐标，用于计算高亮卡片是否被底部导航遮挡
    // 注意：不能命名为 density —— 局部变量会遮蔽 graphicsLayer {} 作用域里的 density: Float
    val localDensity = LocalDensity.current
    val scrollState = rememberScrollState()
    var containerBounds by remember { mutableStateOf(Rect.Zero) }

    // 横屏两栏：横屏时头部固定在左栏（覆盖层），卡片在右侧区域滚动
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.let {
        it.screenWidthDp > it.screenHeightDp
    }
    // 右侧内容区起点 = 左右平均分（50%），按内容区宽度算（去掉页面左右各 24dp 的 padding）
    val landscapeStartPadding =
        (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 48).dp * 0.5f
    val rightScrollState = rememberScrollState()

    // 控制行平移：拖拽模式下从原位滑到屏幕中央（横屏下右栏不在屏幕中心，X 也要移）
    var rowHomeYInPage by remember { mutableFloatStateOf(0f) }
    var rowHomeXCenterInWindow by remember { mutableFloatStateOf(0f) }
    val rowTravelProgress by animateFloatAsState(
        targetValue = if (dragAdjustMode) 1f else 0f,
        // 弹簧回弹
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "oobeRowTravel"
    )
    val centerGroupHeightPx = with(localDensity) { 210.dp.toPx() }

    // 内容是否延伸到悬浮底栏之下：滚动中=是；滚到底（尾部留白）/内容不足=否
    ReportNavMaskVisibility(scrollState, isVisible, onContentUnderBar)

    // 教程定位时自动展开对应 SectionCard，并把高亮卡片滚动到可见区域
    LaunchedEffect(tutorialActive, tutorialStep) {
        // 把高亮卡片滚到容器底边（即底部导航上沿）之上，避免被上一步/下一步遮挡
        suspend fun ensureTargetVisible() {
            val targetIndex = when (tutorialStep) {
                0 -> 0
                1 -> 1
                2 -> OOBE_DRAG_TARGET_GESTURE_BUTTON
                3, 4 -> 2
                5 -> 3
                6 -> 4
                else -> 0
            }
            val target = tutorialTargets[targetIndex] ?: return
            if (containerBounds.height <= 0f) return
            val marginPx = with(localDensity) { 16.dp.toPx() }
            val topOverflow = (containerBounds.top + marginPx) - target.top
            if (topOverflow > 1f) {
                val clamped = (scrollState.value - topOverflow).roundToInt().coerceIn(0, scrollState.maxValue)
                if (clamped != scrollState.value) {
                    scrollState.animateScrollTo(clamped, tween(350, easing = FastOutSlowInEasing))
                }
                return
            }
            // 悬浮底栏（按钮行+进度指示器+底距约 180dp）会盖住内容，抬高目标时多让出这段高度
            val navClearancePx = with(localDensity) { 180.dp.toPx() }
            val overflow = target.bottom - (containerBounds.bottom - marginPx - navClearancePx)
            if (overflow <= 1f) return
            val clamped = (scrollState.value + overflow).roundToInt().coerceIn(0, scrollState.maxValue)
            if (clamped != scrollState.value) {
                scrollState.animateScrollTo(clamped, tween(350, easing = FastOutSlowInEasing))
            }
        }

        if (tutorialActive) {
            // 每次切换教程步骤时，立刻清除卡片的固定（图钉）状态
            pinnedSections = emptySet()

            // 当切换到 G2圆角高亮（Step 1）及以后时，强制恢复到“全局同步”模式
            if (tutorialStep >= 1) {
                onLinkedChange(true)
            }

            when (tutorialStep) {
                0, 1, 2, 3, 4 -> activeSection = 0 // 前 5 步都停在「基础圆角半径」卡片
                5 -> activeSection = 1              // 横向 X 轴
                6 -> activeSection = 2              // 纵向 Y 轴
            }

            // 卡片展开约 300ms，250ms 起滚（滚动中坐标持续上报，结束后再校验补齐）
            delay(250)
            ensureTargetVisible()
            delay(300)
            ensureTargetVisible()
        } else {
            // 教程结束后，恢复默认状态，也拔掉图钉，并平滑滚回页面顶部
            activeSection = 0
            pinnedSections = emptySet()
            onLinkedChange(true)
            if (scrollState.value > 0) {
                scrollState.animateScrollTo(0, tween(350, easing = FastOutSlowInEasing))
            }
        }
    }

    val sliderSpec = tween<Float>(800, easing = FastOutSlowInEasing)

    // 卡片颜色
    val lightCardColor = oobeCardColor(false)
    val darkCardColor = oobeCardColor(isDark)
    val animatedCardColor by animateColorAsState(
        targetValue = if (useCustom) lightCardColor else darkCardColor,
        animationSpec = tween(500),
        label = "cardColor"
    )
    val animatedCardBorder by animateColorAsState(
        targetValue = if (useCustom) Color.Transparent else oobeCardBorder(isDark),
        animationSpec = tween(500),
        label = "cardBorder"
    )
    // 校准卡片（SectionCard）背景色渐变
    val sectionCardColor by animateColorAsState(
        targetValue = if (useCustom) oobeCardColor(false) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(500),
        label = "sectionCardColor"
    )

    // 实时持久化 + 内存更新
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val _trigger = tlAnim.value + trAnim.value + blAnim.value + brAnim.value +
            tlXAnim.value + trXAnim.value + blXAnim.value + brXAnim.value +
            tlYAnim.value + trYAnim.value + blYAnim.value + brYAnim.value
    // 值没变就跳过写盘，省掉无意义的 Editor 分配和后台写。初始化为 NaN 保证第一次一定写。
    val lastSaved = remember { FloatArray(13) { Float.NaN } }
    SideEffect {
        // 内存态始终同步，不参与跳过判断——跳过只针对磁盘写入
        ThemeSettings.useCustomRadius = useCustom
        if (useCustom) {
            ThemeSettings.radiusTL = tlAnim.value; ThemeSettings.radiusTR = trAnim.value
            ThemeSettings.radiusBL = blAnim.value; ThemeSettings.radiusBR = brAnim.value
        }

        val current = floatArrayOf(
            if (useCustom) 1f else 0f,
            tlAnim.value, trAnim.value, blAnim.value, brAnim.value,
            tlXAnim.value, trXAnim.value, blXAnim.value, brXAnim.value,
            tlYAnim.value, trYAnim.value, blYAnim.value, brYAnim.value
        )
        if (current.contentEquals(lastSaved)) return@SideEffect
        current.copyInto(lastSaved)

        ThemeSettings.saveCustomRadius(context, useCustom, tlAnim.value, trAnim.value, blAnim.value, brAnim.value)
        prefs.edit().apply {
            putFloat("r_tl_x", tlXAnim.value); putFloat("r_tr_x", trXAnim.value)
            putFloat("r_bl_x", blXAnim.value); putFloat("r_br_x", brXAnim.value)
            putFloat("r_tl_y", tlYAnim.value); putFloat("r_tr_y", trYAnim.value)
            putFloat("r_bl_y", blYAnim.value); putFloat("r_br_y", brYAnim.value)
            apply()
        }
    }

    // 浅色主题平滑过渡
    val lightScheme = remember { dynamicLightColorScheme(context) }
    val darkScheme = MaterialTheme.colorScheme
    val blendFactor by animateFloatAsState(
        targetValue = if (useCustom) 1f else 0f,
        animationSpec = tween(500),
        label = "themeBlend"
    )
    val blendedScheme = darkScheme.copy(
        surfaceVariant = lerp(darkScheme.surfaceVariant, lightScheme.surfaceVariant, blendFactor),
        surface = lerp(darkScheme.surface, lightScheme.surface, blendFactor),
        onSurface = lerp(darkScheme.onSurface, lightScheme.onSurface, blendFactor),
        onSurfaceVariant = lerp(darkScheme.onSurfaceVariant, lightScheme.onSurfaceVariant, blendFactor),
        primary = lerp(darkScheme.primary, lightScheme.primary, blendFactor),
        onPrimary = lerp(darkScheme.onPrimary, lightScheme.onPrimary, blendFactor),
        primaryContainer = lerp(darkScheme.primaryContainer, lightScheme.primaryContainer, blendFactor),
        onPrimaryContainer = lerp(darkScheme.onPrimaryContainer, lightScheme.onPrimaryContainer, blendFactor),
        secondary = lerp(darkScheme.secondary, lightScheme.secondary, blendFactor),
        onSecondary = lerp(darkScheme.onSecondary, lightScheme.onSecondary, blendFactor),
        secondaryContainer = lerp(darkScheme.secondaryContainer, lightScheme.secondaryContainer, blendFactor),
        onSecondaryContainer = lerp(darkScheme.onSecondaryContainer, lightScheme.onSecondaryContainer, blendFactor),
        outline = lerp(darkScheme.outline, lightScheme.outline, blendFactor),
        outlineVariant = lerp(darkScheme.outlineVariant, lightScheme.outlineVariant, blendFactor),
    )

    // 拖拽调整模式下页面内容整体淡出 + 轻微缩小。
    // 触摸是否命中由顶层拖拽覆盖层的拦截层负责，这里只负责视觉。
    val modeSwitchSpec: AnimationSpec<Float> =
        if (dragAdjustMode) tween(220, easing = FastOutSlowInEasing)
        else tween(400, easing = FastOutSlowInEasing)
    val calibContentAlpha by animateFloatAsState(
        targetValue = if (dragAdjustMode) 0f else 1f,
        animationSpec = modeSwitchSpec,
        label = "oobeCalibContentAlpha"
    )
    val calibContentScale by animateFloatAsState(
        targetValue = if (dragAdjustMode) 0.9f else 1f,
        animationSpec = modeSwitchSpec,
        label = "oobeCalibContentScale"
    )

    MaterialTheme(colorScheme = blendedScheme) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                containerBounds = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
            }
            // 竖屏：整页可滚（拖拽模式除外）。横屏/拖拽：把滚动修饰符整个移除 ——
            // 必须移除滚动修饰符本身（enabled=false 不够）
            .then(if (isLandscape || dragAdjustMode) Modifier else Modifier.verticalScroll(scrollState))
            .imePadding()
            .padding(horizontal = 24.dp)
            // 竖屏保留原来的上下留白。横屏去掉 —— 否则右栏滚动视口从"屏幕顶+80dp"才开始，
            // 内容往上滚会在半空被裁切（顶部露出的背景看着像白色块）；右栏内部自有 80dp 顶部留白
            .then(if (isLandscape) Modifier else Modifier.padding(top = 80.dp, bottom = 24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 卡片区抽成 lambda（= 组1 剩余 + 组2 + 尾部让位）：横屏时作为右栏。
        // 头部 lambda 也声明在这一层（和分支同一作用域，组1 内部够不到）
        val headerBlock: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("圆角校准", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("绘制的线条圆角贴合你的手机屏幕圆角吗\n若不一致可打开开关手动校准", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        val bodyBlock: @Composable () -> Unit = {
        // 组 1：图标 + 标题 + 说明 + 开关卡片（淡出 + 缩放，拖拽时不再响应触摸）
        Column(
            modifier = Modifier
                // 控件限宽 420；widthIn 必须在 fillMaxWidth 之前
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .blockTouchesWhile(dragAdjustMode)
                .graphicsLayer {
                    alpha = calibContentAlpha
                    scaleX = calibContentScale
                    scaleY = calibContentScale
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 头部已抽到上面的 headerBlock（横屏进左栏、竖屏在此处调用），这里不再重复渲染

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = animatedCardColor),
            border = BorderStroke(1.dp, animatedCardBorder)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自定义圆角半径", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        val displayRadius = if (useCustom) {
                            "当前自定义圆角：${String.format(java.util.Locale.US, "%.1f", tlAnim.value)} px"
                        } else {
                            "当前系统圆角：${String.format(java.util.Locale.US, "%.1f", systemRadius)} px"
                        }
                        Text(displayRadius, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = useCustom,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCalibrationChanged(it)
                            if (it) {
                                ThemeSettings.useCustomRadius = true
                                ThemeSettings.radiusTL = tlAnim.value; ThemeSettings.radiusTR = trAnim.value
                                ThemeSettings.radiusBL = blAnim.value; ThemeSettings.radiusBR = brAnim.value
                                // 首次打开开关时弹出教程
                                if (!isTutorialShown(context)) {
                                    onTutorialStepChange(0)
                                    onTutorialToggle(true)
                                }
                            } else {
                                ThemeSettings.useCustomRadius = false
                                ThemeSettings.radiusTL = -1f; ThemeSettings.radiusTR = -1f
                                ThemeSettings.radiusBL = -1f; ThemeSettings.radiusBR = -1f
                                // 关掉自定义圆角后联动模式回到默认的全局同步
                                onLinkedChange(true)
                                ThemeSettings.saveCalibrationLinkedMode(context, true)
                            }
                        }
                    )
                }
            }
        }

        // 使用旧版曲线开关：仅当未开启自定义圆角半径时，
        // 且设备支持新版曲线（读不到圆心数据的设备已被强制旧版曲线）时显示
        AnimatedVisibility(visible = !useCustom && ThemeSettings.isCenterDataAvailable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = G2Shapes.card,
                colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
                border = BorderStroke(1.dp, oobeCardBorder(isDark))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("使用旧版曲线", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Switch(
                        checked = ThemeSettings.isLegacyCurveEnabled,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            ThemeSettings.saveLegacyCurveConfig(context, it)
                        }
                    )
                }
            }
        }
        } // 组 1 结束

        // 控制行：拖拽模式下整块平移到屏幕中央
        // zIndex 是为了压过后面那组淡出的卡片
        AnimatedVisibility(
            visible = useCustom,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                // 横屏：控制行与卡片左缘对齐
                // 竖屏内容居中，需要 Center
                horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        // 锚点必须和控制行同宽，否则 dx 恒为 0
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .height(0.dp)
                        .onGloballyPositioned { coords ->
                            rowHomeYInPage = coords.positionInWindow().y - containerBounds.top
                            // 控制行中心的窗口 X
                            rowHomeXCenterInWindow = coords.positionInWindow().x + coords.size.width / 2f
                        }
                )

                // 只有控制行这一块被位移
                Box(
                    modifier = Modifier
                        // 与拖拽工具层同一限宽（420dp），拖拽时两行左右对齐；
                        // widthIn 必须在 fillMaxWidth 之前
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                    .offset {
                        val targetY = (containerBounds.height - centerGroupHeightPx) / 2f
                        val dy = (targetY - rowHomeYInPage) * rowTravelProgress
                        // 横屏下右栏不在屏幕中心，拖拽时控制行也要横移到屏幕中线；
                        val screenCenterX = (containerBounds.left + containerBounds.right) / 2f
                        val dx = (screenCenterX - rowHomeXCenterInWindow) * rowTravelProgress
                        IntOffset(dx.roundToInt(), dy.roundToInt())
                    }
                ) {
                    OOBECalibrationControlRow(
                        isLinked = isLinked,
                        isG2Enabled = isG2Enabled,
                        dragAdjustMode = dragAdjustMode,
                        onLinkedChange = { onToggleLinked() },
                        onToggleG2 = onToggleG2,
                        onToggleDrag = onToggleDrag,
                        lightScheme = lightScheme,
                        tutorialTargets = tutorialTargets
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }

        // 组 2：三张调节卡片（淡出 + 缩放，拖拽时不再响应触摸）
        AnimatedVisibility(visible = useCustom) {
            Column(
                modifier = Modifier
                    // 同组1：统一 420dp 限宽（在 fillMaxWidth 之前）
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .blockTouchesWhile(dragAdjustMode)
                    .graphicsLayer {
                        alpha = calibContentAlpha
                        scaleX = calibContentScale
                        scaleY = calibContentScale
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[2] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }) {
                SectionCard(
                    title = "基础圆角半径",
                    isExpanded = activeSection == 0 || 0 in pinnedSections,
                    isPinned = 0 in pinnedSections,
                    containerColor = sectionCardColor,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (0 in pinnedSections) pinnedSections = pinnedSections - 0
                        else if (activeSection == 0) activeSection = null
                        else activeSection = 0
                    },
                    onLongClick = {
                        pinnedSections = if (0 in pinnedSections) pinnedSections - 0 else pinnedSections + 0
                    }
                ) {
                    CalibrationSliderGroup(isLinked, tlAnim, trAnim, blAnim, brAnim, systemRadius, sliderSpec, 0f..300f, scope)
                }
                } // end Box (target 2)

                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[3] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }) {
                SectionCard(
                    title = "横向 (X轴) 曲率修正",
                    isExpanded = activeSection == 1 || 1 in pinnedSections,
                    isPinned = 1 in pinnedSections,
                    containerColor = sectionCardColor,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (1 in pinnedSections) pinnedSections = pinnedSections - 1
                        else if (activeSection == 1) activeSection = null
                        else activeSection = 1
                    },
                    onLongClick = {
                        pinnedSections = if (1 in pinnedSections) pinnedSections - 1 else pinnedSections + 1
                    }
                ) {
                    CalibrationSliderGroup(isLinked, tlXAnim, trXAnim, blXAnim, brXAnim, 0f, sliderSpec, -150f..150f, scope)
                }
                } // end Box (target 3)

                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[4] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }) {
                SectionCard(
                    title = "纵向 (Y轴) 曲率修正",
                    isExpanded = activeSection == 2 || 2 in pinnedSections,
                    isPinned = 2 in pinnedSections,
                    containerColor = sectionCardColor,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (2 in pinnedSections) pinnedSections = pinnedSections - 2
                        else if (activeSection == 2) activeSection = null
                        else activeSection = 2
                    },
                    onLongClick = {
                        pinnedSections = if (2 in pinnedSections) pinnedSections - 2 else pinnedSections + 2
                    }
                ) {
                    CalibrationSliderGroup(isLinked, tlYAnim, trYAnim, blYAnim, brYAnim, 0f, sliderSpec, -150f..150f, scope)
                }
                } // end Box (target 4)

                // 尾部留白：滚动到最后时内容完全停在悬浮底栏上方
        Spacer(Modifier.height(176.dp))
            } // Column（组 2 内容）
        } // AnimatedVisibility(useCustom)
        } // bodyBlock

        // 横屏：右侧滚动区占满整页宽（内容用 start padding 推到右 58% 区域、从区域左缘排），
        // 左栏头部做固定覆盖层。
        // 不能用 Row 分栏：滚动视口会被切成右半边
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rightScrollState, enabled = !dragAdjustMode)
                        // 内容推到右侧区域并靠左排（不再在右半边里居中——那样整页看着偏）
                        .padding(start = landscapeStartPadding),
                    // 内容不足一屏时垂直居中；上下留白需对称
                    // 顶部留白与尾部让位同高（176）
                    // 内容溢出时可滚动，滚到顶时这 176dp 是顶部呼吸区（代价可接受）
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.height(176.dp))
                    bodyBlock()
                    Spacer(Modifier.height(176.dp))
                }
                // 左栏：固定覆盖层（不随右侧内容滚动），挂与"组 1"相同的淡出/缩放
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.graphicsLayer {
                            alpha = calibContentAlpha
                            scaleX = calibContentScale
                            scaleY = calibContentScale
                        }
                    ) { headerBlock() }
                }
            }
        } else {
            // 竖屏：头部也要挂淡出/缩放
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = calibContentAlpha
                    scaleX = calibContentScale
                    scaleY = calibContentScale
                }
            ) { headerBlock() }
            Spacer(Modifier.height(28.dp))
            bodyBlock()
            Spacer(Modifier.height(176.dp))
        }
    }
    }
}

// 圆角校准页的控制行（全局同步 / G2 平滑 / 拖拽调整）。
// 拖拽模式下由调用方整块平移到屏幕中央。
@Composable
internal fun OOBECalibrationControlRow(
    isLinked: Boolean,
    isG2Enabled: Boolean,
    dragAdjustMode: Boolean,
    onLinkedChange: (Boolean) -> Unit,
    onToggleG2: () -> Unit,
    onToggleDrag: () -> Unit,
    lightScheme: ColorScheme,
    tutorialTargets: MutableMap<Int, Rect>
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[0] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }
                .weight(1.2f).height(44.dp),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                focusManager.clearFocus()
                onLinkedChange(!isLinked)
            },
            shape = G2Shapes.gridCard
        ) {
            Text(
                text = if (isLinked) "全局同步调节" else "四角独立调节",
                fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
        }

        val g2BgColor by animateColorAsState(
            targetValue = if (isG2Enabled) lightScheme.primary else lightScheme.surfaceVariant,
            animationSpec = tween(300), label = "g2Bg"
        )
        val g2ContentColor by animateColorAsState(
            targetValue = if (isG2Enabled) lightScheme.onPrimary else lightScheme.onSurfaceVariant,
            animationSpec = tween(300), label = "g2Ct"
        )
        Button(
            onClick = {
                onToggleG2()
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            },
            shape = G2Shapes.gridCard,
            colors = ButtonDefaults.buttonColors(containerColor = g2BgColor, contentColor = g2ContentColor),
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[1] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }
                .weight(0.8f).height(44.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            // 字号过大时 "开/关" 会被挤到第二行并被按钮高度裁掉：
            // 单行显示 + 溢出时按 0.9 等比缩小字号，直到塞进按钮
            var g2TextSize by remember { mutableStateOf(13f) }
            Text(
                text = if (isG2Enabled) "G2平滑:开" else "G2平滑:关",
                fontWeight = FontWeight.Bold, fontSize = g2TextSize.sp,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    if (result.hasVisualOverflow && g2TextSize > 8f) g2TextSize *= 0.9f
                }
            )
        }

        // 拖拽调整入口：进入后隐藏本页内容与悬浮底栏，只留圆角轮廓与四角控制点
        val dragBtnBgColor by animateColorAsState(
            targetValue = if (dragAdjustMode) lightScheme.primary else lightScheme.surfaceVariant,
            animationSpec = tween(300), label = "dragBtnBg"
        )
        val dragBtnContentColor by animateColorAsState(
            targetValue = if (dragAdjustMode) lightScheme.onPrimary else lightScheme.onSurfaceVariant,
            animationSpec = tween(300), label = "dragBtnCt"
        )
        Surface(
            onClick = onToggleDrag,
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[OOBE_DRAG_TARGET_GESTURE_BUTTON] = Rect(
                        pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height
                    )
                }
                .size(44.dp),
            shape = G2Shapes.gridCard,
            color = dragBtnBgColor,
            contentColor = dragBtnContentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Gesture,
                    contentDescription = if (dragAdjustMode) "关闭拖拽调整" else "拖拽调整圆角",
                    tint = dragBtnContentColor
                )
            }
        }
    }
}

// 3.测试线条
@Composable
fun OOBETestLineStep(
    isDark: Boolean,
    isVisible: Boolean,
    onThicknessChange: ((Float) -> Unit)? = null,
    onSegmentLengthChange: ((Float) -> Unit)? = null,
    onDraggingChange: ((Boolean) -> Unit)? = null,
    onContentUnderBar: (Boolean) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    // 内容是否延伸到悬浮底栏之下：滚动中=是；滚到底（尾部留白）/内容不足=否
    ReportNavMaskVisibility(scrollState, isVisible, onContentUnderBar)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val landscapeMinHeight = configuration.screenHeightDp.dp

    // 横屏右栏独立滚动
    val rightScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 竖屏：整页滚动
            // 横屏：右栏滚动，左栏头部固定
            .then(if (isLandscape) Modifier else Modifier.verticalScroll(scrollState))
            .padding(horizontal = 24.dp)
            // 竖屏保留上下留白；横屏垂直居中
            .then(
                if (isLandscape) Modifier.heightIn(min = landscapeMinHeight)
                else Modifier.padding(top = 80.dp, bottom = 24.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top
    ) {
        // 头部抽成 lambda：竖屏流内，横屏左栏
        val headerBlock: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("测试线条", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("自定义测试线条的粗细和颜色", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        }
        // 卡片区抽成 lambda：横屏作为右栏
        val bodyBlock: @Composable () -> Unit = {
        // 竖屏"副标题 → 卡片"的间距；横屏不需要
        if (!isLandscape) Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            Column {
                TestLineSettingsPanel(
                    showTopDivider = false,
                    onThicknessChange = onThicknessChange,
                    onSegmentLengthChange = onSegmentLengthChange,
                    onDraggingChange = onDraggingChange
                )
                // 与设置页同一份入口：进「线条与配色」页调线条/背景/文字并实时预览
                MoreSettingsEntry(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                )
            }
        }
        }

        // 横屏：与圆角校准步同一套结构 —— 全宽滚动（内容用 start padding 推到右半区、靠左排）+
        val landscapeStartPadding = (configuration.screenWidthDp - 48).dp * 0.5f
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rightScrollState)
                        .padding(start = landscapeStartPadding),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 顶部留白 48；内容溢出时 Center 不生效
                    Spacer(Modifier.height(48.dp))
                    Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                        bodyBlock()
                    }
                    Spacer(Modifier.height(176.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) { headerBlock() }
            }
        } else {
            headerBlock()
            // 内容限宽 420
            Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                bodyBlock()
            }
            Spacer(Modifier.height(176.dp))
        }
    }
}

// 4.纯净模式
@Composable
fun OOBEPureModeStep(isDark: Boolean, isVisible: Boolean, onContentUnderBar: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current

    val scrollState = rememberScrollState()
    ReportNavMaskVisibility(scrollState, isVisible, onContentUnderBar)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    // 横屏垂直居中需要最小高度
    val landscapeMinHeight = configuration.screenHeightDp.dp

    // 横屏右栏独立滚动
    val rightScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 竖屏：整页滚动
            // 横屏：右栏滚动，左栏头部固定
            .then(if (isLandscape) Modifier else Modifier.verticalScroll(scrollState))
            .padding(horizontal = 24.dp)
            // 竖屏保留上下留白；横屏垂直居中
            .then(
                if (isLandscape) Modifier.heightIn(min = landscapeMinHeight)
                else Modifier.padding(top = 80.dp, bottom = 24.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // 横屏：内容整体垂直居中
        verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top
    ) {
        // 头部抽成 lambda：竖屏流内，横屏左栏
    val headerBlock: @Composable () -> Unit = {
        // 必须包一层 Column：直接放进 Box 会重叠
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("纯净模式", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "开启后将隐藏测试页中的部分文案和底部参数\n隐藏右上角切换按钮，改为双击屏幕切换模式",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }

        // 卡片区抽成 lambda：横屏作为右栏
        val bodyBlock: @Composable () -> Unit = {
        // 竖屏"副标题 → 卡片"的间距；横屏不需要
        if (!isLandscape) Spacer(Modifier.height(40.dp))

        // 直接读 ThemeSettings：本地镜像状态在从"更多设置"页回来时不会同步
        val isEnabled = ThemeSettings.isCompactModeEnabled

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用纯净模式", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled) "已开启 - 测试页将显示纯净界面" else "已关闭 - 测试页将显示完整信息",
                        fontSize = 13.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        ThemeSettings.saveCompactModeConfig(context, it)
                    }
                )
            }
        }

        }

        // 横屏两栏：全宽滚动 + 左栏固定覆盖层
        val landscapeStartPadding = (configuration.screenWidthDp - 48).dp * 0.5f
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rightScrollState)
                        .padding(start = landscapeStartPadding),
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.height(176.dp))
                    Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                        bodyBlock()
                    }
                    Spacer(Modifier.height(176.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) { headerBlock() }
            }
        } else {
            headerBlock()
            // 内容限宽 420
            Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                bodyBlock()
            }
            Spacer(Modifier.height(176.dp))
        }
    }
}

// 5.界面高级动效
@Composable
fun OOBEAdvancedUIStep(isDark: Boolean, isVisible: Boolean, onContentUnderBar: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current

    val scrollState = rememberScrollState()
    // 内容是否延伸到悬浮底栏之下：滚动中=是；滚到底（尾部留白）/内容不足=否
    ReportNavMaskVisibility(scrollState, isVisible, onContentUnderBar)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val landscapeMinHeight = configuration.screenHeightDp.dp

    // 横屏右栏独立滚动
    val rightScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 竖屏：整页滚动
            // 横屏：右栏滚动，左栏头部固定
            .then(if (isLandscape) Modifier else Modifier.verticalScroll(scrollState))
            .padding(horizontal = 24.dp)
            // 竖屏保留上下留白；横屏垂直居中
            .then(
                if (isLandscape) Modifier.heightIn(min = landscapeMinHeight)
                else Modifier.padding(top = 80.dp, bottom = 24.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top
    ) {
        // 头部抽成 lambda：竖屏流内，横屏左栏
        val headerBlock: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("界面高级动效", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "自定义卡片动效、线条预览与背景混色",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
        }

        // 卡片区抽成 lambda：横屏作为右栏
        val bodyBlock: @Composable () -> Unit = {
        // 竖屏"副标题 → 卡片"的间距；横屏不需要
        if (!isLandscape) Spacer(Modifier.height(40.dp))

        // 直接读 ThemeSettings，避免本地镜像状态不同步
        val isEnabled = ThemeSettings.isAnimationEnabled

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用卡片动效", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled) "已开启 - 卡片将带有更多动画效果" else "已关闭 - 卡片将直接显示",
                        fontSize = 13.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        ThemeSettings.saveAnimationConfig(context, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("设置页线条预览", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (ThemeSettings.isSettingsLinePreviewEnabled) "已开启 - 调节线条时将显示实时线条预览" else "已关闭 - 调节线条时将不会显示线条预览",
                        fontSize = 13.sp,
                        color = if (ThemeSettings.isSettingsLinePreviewEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ThemeSettings.isSettingsLinePreviewEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        ThemeSettings.saveSettingsLinePreviewConfig(context, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 背景动态混色
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("背景动态混色", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (ThemeSettings.aboutDynamicMixEnabled) "已开启 - 背景将缓慢流动混色" else "已关闭 - 背景使用静态渐变",
                        fontSize = 13.sp,
                        color = if (ThemeSettings.aboutDynamicMixEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ThemeSettings.aboutDynamicMixEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        ThemeSettings.saveAboutDynamicMixConfig(context, it)
                    }
                )
            }
        }
        }

        // 横屏两栏：全宽滚动 + 左栏固定覆盖层
        val landscapeStartPadding = (configuration.screenWidthDp - 48).dp * 0.5f
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rightScrollState)
                        .padding(start = landscapeStartPadding),
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.height(176.dp))
                    Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                        bodyBlock()
                    }
                    Spacer(Modifier.height(176.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) { headerBlock() }
            }
        } else {
            headerBlock()
            // 内容限宽 420
            Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                bodyBlock()
            }
            Spacer(Modifier.height(176.dp))
        }
    }
}

// 6.完成页
@Composable
fun OOBECompletionStep(isDark: Boolean, isActive: Boolean = false, isVisible: Boolean, onContentUnderBar: (Boolean) -> Unit = {}) {
    val scrollState = rememberScrollState()
    ReportNavMaskVisibility(scrollState, isVisible, onContentUnderBar)
    val animEnabled = ThemeSettings.isAnimationEnabled
    var animPlayed by remember { mutableStateOf(false) }
    val iconAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val iconOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val titleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val titleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val subtitleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val subtitleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }

    val slideSpec = tween<Float>(400, easing = FastOutSlowInEasing)

    LaunchedEffect(isActive) {
        if (!isActive || animPlayed) return@LaunchedEffect
        if (!animEnabled) {
            iconAlpha.snapTo(1f); iconOffset.snapTo(0f)
            titleAlpha.snapTo(1f); titleOffset.snapTo(0f)
            subtitleAlpha.snapTo(1f); subtitleOffset.snapTo(0f)
            animPlayed = true
            return@LaunchedEffect
        }
        animPlayed = true
        launch {
            launch { iconAlpha.animateTo(1f, tween(400)) }
            iconOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch {
            launch { titleAlpha.animateTo(1f, tween(400)) }
            titleOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch { subtitleAlpha.animateTo(1f, tween(400)) }
        subtitleOffset.animateTo(0f, slideSpec)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                // 底部让位是单侧的，会把中心上移
                .then(
                    if (androidx.compose.ui.platform.LocalConfiguration.current
                            .let { it.screenWidthDp > it.screenHeightDp }
                    ) Modifier else Modifier.padding(bottom = 124.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Celebration,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer { alpha = iconAlpha.value; translationY = iconOffset.value * density },
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "设置完成",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha.value; translationY = titleOffset.value * density }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "一切准备就绪\n开始测试你的屏幕吧",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha.value; translationY = subtitleOffset.value * density }
            )
        }
    }
}

// 彩带动画
@Composable
fun ConfettiFireworks(modifier: Modifier = Modifier, isActive: Boolean = true) {
    val particles = remember { mutableStateListOf<ConfettiParticle>() }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val colors = listOf(
            Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
            Color(0xFFA8E6CF), Color(0xFFFF8B94), Color(0xFF6C5CE7),
            Color(0xFF00B894), Color(0xFFFD79A8), Color(0xFFE17055),
            Color(0xFF0984E3), Color(0xFF00CEC9), Color(0xFFFDCB6E),
            Color(0xFFFF4757), Color(0xFF2ED573), Color(0xFF1E90FF)
        )
        val random = Random(System.currentTimeMillis())

        val leftX = screenWidthPx * 0.1f
        val rightX = screenWidthPx * 0.9f
        val burstCount = if (ThemeSettings.isAnimationEnabled) 12 else 6  // 低性能设备彩带对半砍
        for (burst in 0..burstCount) {
            delay(150L + random.nextInt(250).toLong())
            for (side in 0..1) {
                val burstX = if (side == 0) leftX else rightX
                for (i in 0..24) {
                    // 三种角度并行：每颗粒子随机命中一种
                    val type = random.nextInt(20)
                    val rawAngle = when {
                        type < 7  -> -90f + (random.nextFloat() - 0.5f) * 20f     // 垂直向上 ×7
                        type < 14 -> -45f + (random.nextFloat() - 0.5f) * 20f     // 45° 斜喷 ×7
                        else      -> -70f                                         // 70° 固定 ×6
                    }
                    val angle = if (side == 0) rawAngle else -180f - rawAngle
                    val speed = if (type < 14) 1800f + random.nextFloat() * 1200f else 1200f + random.nextFloat() * 1000f  // 垂直/45° ≥3/4屏，70° ≥3/5屏
                    val rad = Math.toRadians(angle.toDouble()).toFloat()
                    particles.add(ConfettiParticle(
                        x = burstX + (random.nextFloat() - 0.5f) * 30f,
                        y = screenHeightPx,
                        vx = cos(rad) * speed,
                        vy = sin(rad) * speed,
                        color = colors[random.nextInt(colors.size)],
                        size = 3f + random.nextFloat() * 5f,
                        sizeH = 15f + random.nextFloat() * 25f,
                        rotation = random.nextFloat() * 360f,
                        rotationSpeed = (random.nextFloat() - 0.5f) * 1440f,
                        alpha = 1f,
                        lifetime = 5000f + random.nextFloat() * 3000f,
                        age = 0f
                    ))
                }
            }
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(16)
            val toRemove = mutableListOf<ConfettiParticle>()
            for (i in particles.indices) {
                val p = particles[i]
                val newAge = p.age + 16f
                if (newAge >= p.lifetime) { toRemove.add(p); continue }
                val lifeRatio = newAge / p.lifetime
                particles[i] = p.copy(
                    x = p.x + p.vx * 0.016f,
                    y = p.y + p.vy * 0.016f,
                    vx = p.vx * 0.992f,
                    vy = p.vy * 0.992f + 12f,
                    rotation = p.rotation + p.rotationSpeed * 0.016f,
                    alpha = if (lifeRatio > 0.7f) 1f - ((lifeRatio - 0.7f) / 0.3f) else 1f,
                    age = newAge
                )
            }
            if (toRemove.isNotEmpty()) particles.removeAll(toRemove)
            tick++
        }
    }

    val particleSnapshot = particles.toList()
    Canvas(modifier = modifier) {
        for (p in particleSnapshot) {
            rotate(p.rotation, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = p.color.copy(alpha = p.alpha),
                    topLeft = Offset(p.x - p.size / 2, p.y - p.sizeH / 2),
                    size = Size(p.size, p.sizeH)
                )
            }
        }
    }
}

data class ConfettiParticle(
    val x: Float, val y: Float,
    val vx: Float, val vy: Float,
    val color: Color, val size: Float,
    val sizeH: Float = size * 3f,
    val rotation: Float, val rotationSpeed: Float,
    val alpha: Float, val lifetime: Float, val age: Float
)

// 圆角边框预览
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OOBELiveBorderPreview(
    pagerState: androidx.compose.foundation.pager.PagerState,
    realtimeThickness: Float = ThemeSettings.testLineThickness,
    realtimeSegmentLength: Float = if (ThemeSettings.multiColorSegmentLength == 0f) 1f else ThemeSettings.multiColorSegmentLength,
    isDragging: Boolean = false,
    isG2Enabled: Boolean = false,
    isLegacyCurveEnabled: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    fixedAlpha: Float? = null,
    offTLX: Float = 0f, offTLY: Float = 0f,
    offTRX: Float = 0f, offTRY: Float = 0f,
    offBLX: Float = 0f, offBLY: Float = 0f,
    offBRX: Float = 0f, offBRY: Float = 0f
) {
    val targetAlpha = fixedAlpha ?: if (pagerState.currentPage in 1..2) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(500),
        label = "borderAlpha"
    )

    if (alpha == 0f) return

    val context = LocalContext.current

    val systemRadius = try {
        val insets = (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets
        insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f
    } catch (_: Exception) { 100f }

    val baseTL = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusTL.coerceAtLeast(0f) else systemRadius
    val baseTR = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusTR.coerceAtLeast(0f) else systemRadius
    val baseBL = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusBL.coerceAtLeast(0f) else systemRadius
    val baseBR = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusBR.coerceAtLeast(0f) else systemRadius

    val tlX = (baseTL + if (ThemeSettings.useCustomRadius) offTLX else 0f).coerceAtLeast(0f)
    val tlY = (baseTL + if (ThemeSettings.useCustomRadius) offTLY else 0f).coerceAtLeast(0f)
    val trX = (baseTR + if (ThemeSettings.useCustomRadius) offTRX else 0f).coerceAtLeast(0f)
    val trY = (baseTR + if (ThemeSettings.useCustomRadius) offTRY else 0f).coerceAtLeast(0f)
    val blX = (baseBL + if (ThemeSettings.useCustomRadius) offBLX else 0f).coerceAtLeast(0f)
    val blY = (baseBL + if (ThemeSettings.useCustomRadius) offBLY else 0f).coerceAtLeast(0f)
    val brX = (baseBR + if (ThemeSettings.useCustomRadius) offBRX else 0f).coerceAtLeast(0f)
    val brY = (baseBR + if (ThemeSettings.useCustomRadius) offBRY else 0f).coerceAtLeast(0f)

    val isCalibrationPage = if (fixedAlpha != null) false else (pagerState.currentPage == 1)

    // 拖动时实时更新，页面切换时保留动画
    val animatedStrokeW by animateFloatAsState(
        targetValue = if (isCalibrationPage && !ThemeSettings.useCustomRadius) 10f else realtimeThickness,
        animationSpec = if (isDragging) tween(0) else tween(400),
        label = "strokeWAnimation"
    )

    val transitionT by animateFloatAsState(
        targetValue = if (isCalibrationPage) 1f else 0f,
        animationSpec = tween(400),
        label = "colorTransition"
    )

    // 读取渐变/单色状态
    val isMultiColor = ThemeSettings.isMultiColorMode
    val multiColors = ThemeSettings.multiColorSelectedColors
    val singleColor = ThemeSettings.testLineColor

    Canvas(modifier = Modifier.fillMaxSize().alpha(alpha)) {
        val offset = animatedStrokeW / 2f
        val L = offset
        val T = offset
        val R = size.width - offset
        val B = size.height - offset

        val path = androidx.compose.ui.graphics.Path()

        if (isG2Enabled && ThemeSettings.useCustomRadius) {
            val p = 1.4f
            val c = 0.45f
            path.moveTo(L + p * tlX, T)
            path.lineTo(R - p * trX, T)
            path.cubicTo(R - c * trX, T, R, T + c * trY, R, T + p * trY)
            path.lineTo(R, B - p * brY)
            path.cubicTo(R, B - c * brY, R - c * brX, B, R - p * brX, B)
            path.lineTo(L + p * blX, B)
            path.cubicTo(L + c * blX, B, L, B - c * blY, L, B - p * blY)
            path.lineTo(L, T + p * tlY)
            path.cubicTo(L, T + c * tlY, L + c * tlX, T, L + p * tlX, T)
            path.close()
        } else if (!ThemeSettings.useCustomRadius && !isLegacyCurveEnabled) {
            // === arcTo+center: 用圆心精确定位每个角的圆弧 ===
            val act = context as? android.app.Activity
            val insets = act?.window?.decorView?.rootWindowInsets
            val tlRC = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
            val trRC = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)
            val brRC = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)
            val blRC = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)

            fun cx(rc: android.view.RoundedCorner?, fallback: Float): Float =
                rc?.center?.x?.toFloat() ?: fallback
            fun cy(rc: android.view.RoundedCorner?, fallback: Float): Float =
                rc?.center?.y?.toFloat() ?: fallback
            fun rx(rc: android.view.RoundedCorner?): Float =
                ((rc?.radius?.toFloat() ?: 100f) - offset).coerceAtLeast(0f)

            val tlCX = cx(tlRC, L + 100f); val tlCY = cy(tlRC, T + 100f); val tlR = rx(tlRC)
            val trCX = cx(trRC, R - 100f); val trCY = cy(trRC, T + 100f); val trR = rx(trRC)
            val brCX = cx(brRC, R - 100f); val brCY = cy(brRC, B - 100f); val brR = rx(brRC)
            val blCX = cx(blRC, L + 100f); val blCY = cy(blRC, B - 100f); val blR = rx(blRC)

            path.moveTo(tlCX, T)
            path.lineTo(trCX, T)
            path.arcTo(androidx.compose.ui.geometry.Rect(trCX - trR, trCY - trR, trCX + trR, trCY + trR), 270f, 90f, false)
            path.lineTo(R, brCY)
            path.arcTo(androidx.compose.ui.geometry.Rect(brCX - brR, brCY - brR, brCX + brR, brCY + brR), 0f, 90f, false)
            path.lineTo(blCX, B)
            path.arcTo(androidx.compose.ui.geometry.Rect(blCX - blR, blCY - blR, blCX + blR, blCY + blR), 90f, 90f, false)
            path.lineTo(L, tlCY)
            path.arcTo(androidx.compose.ui.geometry.Rect(tlCX - tlR, tlCY - tlR, tlCX + tlR, tlCY + tlR), 180f, 90f, false)
            path.close()
        } else {
            path.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = L, top = T, right = R, bottom = B,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(tlX, tlY),
                    topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(trX, trY),
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(brX, brY),
                    bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(blX, blY)
                )
            )
        }

        // 绘制测试线条颜色
        if (transitionT < 1f) {
            val testBrush = if (isMultiColor && multiColors.size >= 2) {
                val segmentLength = if (realtimeSegmentLength == 0f) 1.0f else realtimeSegmentLength
                val totalLength = size.width.coerceAtLeast(size.height)
                val repeatCount = (totalLength / (segmentLength * 200f)).toInt().coerceAtLeast(1)

                val colorsList = mutableListOf<Color>()
                val colorStopsList = mutableListOf<Float>()

                for (i in 0 until repeatCount) {
                    for ((index, color) in multiColors.withIndex()) {
                        colorsList.add(Color(color))
                        colorStopsList.add((i * multiColors.size + index).toFloat() / (repeatCount * multiColors.size))
                    }
                }
                colorsList.add(Color(multiColors.last()))
                colorStopsList.add(1.0f)

                val colorStopsArray = colorStopsList.zip(colorsList).toTypedArray()

                Brush.linearGradient(
                    *colorStopsArray,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            } else {
                val fallbackColor = if (isMultiColor && multiColors.isNotEmpty()) multiColors.first() else singleColor
                androidx.compose.ui.graphics.SolidColor(Color(fallbackColor))
            }

            drawPath(
                path = path,
                brush = testBrush,
                alpha = 1f - transitionT,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = animatedStrokeW)
            )
        }

        // 绘制校准车间的主题色
        if (transitionT > 0f) {
            drawPath(
                path = path,
                brush = androidx.compose.ui.graphics.SolidColor(primaryColor),
                alpha = transitionT,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = animatedStrokeW)
            )
        }
    }
}