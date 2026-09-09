package com.hydrogen.screentester

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import androidx.core.content.edit
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class CornerCalibrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 渲染进摄像头挖孔区域，避免全屏时挖孔处出现黑条（等效系统"刘海屏：自动匹配"）
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowInsetsControllerCompat(window, view).apply {
                        hide(WindowInsetsCompat.Type.systemBars())
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }

            MaterialTheme(colorScheme = dynamicLightColorScheme(LocalContext.current)) {
                CalibrationScreen { finish() }
            }
        }
    }
}

@Composable
fun CalibrationScreen(onExit: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // 普通教程状态
    val tutorialTargets = remember { mutableStateMapOf<Int, Rect>() }
    var tutorialActive by remember { mutableStateOf(!isTutorialShown(context)) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    // 拖拽调整专属教程：与普通教程完全独立，只在第一次进入拖拽调整时出现。
    var dragTutorialActive by remember { mutableStateOf(false) }
    var dragTutorialStep by remember { mutableIntStateOf(0) }

    // 双击返回键拦截机制
    var lastBackTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackTime < 2000) {
            onExit() // 两秒内连按两次，执行退出
        } else {
            lastBackTime = currentTime
            Toast.makeText(context, "再按一次返回键退出校准车间\n若未保存将丢失此次更改", Toast.LENGTH_SHORT).show()
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) // 震动反馈提示
        }
    }

    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

    val insets = LocalView.current.rootWindowInsets
    val systemRadius = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f

    val tlAnim = remember { Animatable(if (ThemeSettings.radiusTL < 0f) systemRadius else ThemeSettings.radiusTL) }
    val trAnim = remember { Animatable(if (ThemeSettings.radiusTR < 0f) systemRadius else ThemeSettings.radiusTR) }
    val blAnim = remember { Animatable(if (ThemeSettings.radiusBL < 0f) systemRadius else ThemeSettings.radiusBL) }
    val brAnim = remember { Animatable(if (ThemeSettings.radiusBR < 0f) systemRadius else ThemeSettings.radiusBR) }

    val tlXAnim = remember { Animatable(ThemeSettings.radiusTLX) }
    val trXAnim = remember { Animatable(ThemeSettings.radiusTRX) }
    val blXAnim = remember { Animatable(ThemeSettings.radiusBLX) }
    val brXAnim = remember { Animatable(ThemeSettings.radiusBRX) }

    val tlYAnim = remember { Animatable(ThemeSettings.radiusTLY) }
    val trYAnim = remember { Animatable(ThemeSettings.radiusTRY) }
    val blYAnim = remember { Animatable(ThemeSettings.radiusBLY) }
    val brYAnim = remember { Animatable(ThemeSettings.radiusBRY) }

    // G2 平滑圆角状态管理开关
    var isG2Enabled by remember { mutableStateOf(prefs.getBoolean("is_g2_enabled", false)) }
    var isLinked by remember { mutableStateOf(prefs.getBoolean("calibration_is_linked", true)) }
    var activeSection by remember { mutableStateOf<Int?>(0) }
    var pinnedSections by remember { mutableStateOf(setOf<Int>()) }

    // 拖拽调整模式：在屏幕四角显示可拖拽控制点，调整最终轮廓。
    var dragAdjustMode by remember { mutableStateOf(prefs.getBoolean("drag_adjust_mode_enabled", false)) }
    var dragAdjustModeType by remember { mutableStateOf(DragAdjustMode.ALL) }
    var resetCornerMenuVisible by remember { mutableStateOf(false) }
    var resetIconRotation by remember { mutableFloatStateOf(0f) }
    var draggingCorner by remember { mutableStateOf<CalibrationCorner?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // 普通教程期间只展示普通参数界面；结束后恢复用户之前保存的状态。
    LaunchedEffect(tutorialActive, tutorialStep) {
        if (tutorialActive) {
            dragAdjustMode = false
            draggingCorner = null
            resetCornerMenuVisible = false
            pinnedSections = emptySet()

            if (tutorialStep >= 1) {
                isLinked = true
            }

            when (tutorialStep) {
                0, 1, 2, 3, 4 -> activeSection = 0
                5 -> activeSection = 1
                6 -> activeSection = 2
            }
        } else {
            activeSection = 0
            pinnedSections = emptySet()
            isLinked = prefs.getBoolean("calibration_is_linked", true)
            dragAdjustMode = prefs.getBoolean("drag_adjust_mode_enabled", false)
            dragAdjustModeType = DragAdjustMode.ALL
            draggingCorner = null
            resetCornerMenuVisible = false
        }
    }

    // 如果用户上次已经保存为拖拽调整模式，但还从未看过拖拽教程，
    // 普通教程结束后也会自动补上一次拖拽教程。
    LaunchedEffect(tutorialActive, dragAdjustMode) {
        if (!tutorialActive && dragAdjustMode && !dragTutorialActive && !isDragTutorialShown(context)) {
            dragTutorialStep = 0
            dragTutorialActive = true
            dragAdjustModeType = DragAdjustMode.ALL
            resetCornerMenuVisible = false
            draggingCorner = null
        }
    }

    val sliderSpec = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleHitRadiusPx = with(density) { 52.dp.toPx() }
    val handleRadiusPx = with(density) { 7.dp.toPx() }
    val dragHandleColor = MaterialTheme.colorScheme.primary
    val handleRevealProgress by animateFloatAsState(
        targetValue = if (dragAdjustMode) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 400f
        ),
        label = "dragHandleReveal"
    )

    // 按住控制点时的放大进度。lastPressedCorner 是为了让松手后的缩小也能走完动画
    // （松手瞬间 draggingCorner 就变 null 了，只看它会立刻缩回去）
    var lastPressedCorner by remember { mutableStateOf<CalibrationCorner?>(null) }
    LaunchedEffect(draggingCorner) {
        if (draggingCorner != null) lastPressedCorner = draggingCorner
    }
    val handlePressProgress by animateFloatAsState(
        targetValue = if (draggingCorner != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "handlePress"
    )

    // 拖拽调整点始终代表“最终有效圆角”的控制位置。
    // 斜向拖动主要改变基础半径；横/纵偏移会同时体现在 X/Y 修正里，最终轮廓始终与拖动点一致。
    fun cornerHandlePosition(corner: CalibrationCorner, width: Float, height: Float): Offset {
        val strokeOffset = ThemeSettings.testLineThickness / 2f
        val left = strokeOffset
        val top = strokeOffset
        val right = width - strokeOffset
        val bottom = height - strokeOffset
        val p = if (isG2Enabled) 1.4f else 1f

        return when (corner) {
            CalibrationCorner.TOP_LEFT -> Offset(left + p * (tlAnim.value + tlXAnim.value).coerceAtLeast(0f), top + p * (tlAnim.value + tlYAnim.value).coerceAtLeast(0f))
            CalibrationCorner.TOP_RIGHT -> Offset(right - p * (trAnim.value + trXAnim.value).coerceAtLeast(0f), top + p * (trAnim.value + trYAnim.value).coerceAtLeast(0f))
            CalibrationCorner.BOTTOM_RIGHT -> Offset(right - p * (brAnim.value + brXAnim.value).coerceAtLeast(0f), bottom - p * (brAnim.value + brYAnim.value).coerceAtLeast(0f))
            CalibrationCorner.BOTTOM_LEFT -> Offset(left + p * (blAnim.value + blXAnim.value).coerceAtLeast(0f), bottom - p * (blAnim.value + blYAnim.value).coerceAtLeast(0f))
        }
    }

    fun nearestCornerHandle(point: Offset): CalibrationCorner? {
        if (canvasSize == IntSize.Zero) return null
        val width = canvasSize.width.toFloat()
        val height = canvasSize.height.toFloat()
        val corners = CalibrationCorner.entries
        return corners
            .map { corner -> corner to (cornerHandlePosition(corner, width, height) - point).getDistance() }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= handleHitRadiusPx }
            ?.first
    }

    suspend fun applyDragAdjust(corner: CalibrationCorner, dragAmount: Offset) {
        val factor = if (isG2Enabled) 1.4f else 1f
        val localX = dragAmount.x * corner.signX / factor
        val localY = dragAmount.y * corner.signY / factor

        // 拖拽调整模式下，根据当前模式决定修改哪些参数。
        // 全部调整：拖动会综合改变最终圆角。
        // 圆角半径：只改变基础半径。
        // 横向修正：只改变 X 修正。
        // 纵向修正：只改变 Y 修正。
        val radiusDelta = when (dragAdjustModeType) {
            DragAdjustMode.ALL -> (localX + localY) / 2f
            DragAdjustMode.RADIUS -> (localX + localY) / 2f
            DragAdjustMode.HORIZONTAL, DragAdjustMode.VERTICAL -> 0f
        }
        val xCorrectionDelta = when (dragAdjustModeType) {
            DragAdjustMode.ALL -> localX - radiusDelta
            DragAdjustMode.RADIUS, DragAdjustMode.VERTICAL -> 0f
            DragAdjustMode.HORIZONTAL -> localX
        }
        val yCorrectionDelta = when (dragAdjustModeType) {
            DragAdjustMode.ALL -> localY - radiusDelta
            DragAdjustMode.RADIUS, DragAdjustMode.HORIZONTAL -> 0f
            DragAdjustMode.VERTICAL -> localY
        }

        suspend fun applyTo(
            radius: Animatable<Float, *>,
            xCorrection: Animatable<Float, *>,
            yCorrection: Animatable<Float, *>
        ) {
            val newRadius = (radius.value + radiusDelta).coerceIn(0f, 300f)
            val newX = (xCorrection.value + xCorrectionDelta).coerceIn(-150f, 150f)
            val newY = (yCorrection.value + yCorrectionDelta).coerceIn(-150f, 150f)
            radius.snapTo(newRadius)
            xCorrection.snapTo(newX)
            yCorrection.snapTo(newY)
        }

        if (isLinked) {
            applyTo(tlAnim, tlXAnim, tlYAnim)
            applyTo(trAnim, trXAnim, trYAnim)
            applyTo(blAnim, blXAnim, blYAnim)
            applyTo(brAnim, brXAnim, brYAnim)
        } else {
            when (corner) {
                CalibrationCorner.TOP_LEFT -> applyTo(tlAnim, tlXAnim, tlYAnim)
                CalibrationCorner.TOP_RIGHT -> applyTo(trAnim, trXAnim, trYAnim)
                CalibrationCorner.BOTTOM_RIGHT -> applyTo(brAnim, brXAnim, brYAnim)
                CalibrationCorner.BOTTOM_LEFT -> applyTo(blAnim, blXAnim, blYAnim)
            }
        }
    }

    // “拖一下就能看到变化”：高亮框始终真正贴住左边和上边，
    // 右边、下边跟随左上控制点向屏幕内部扩大。
    // 这个计算放在外层 CalibrationScreen 作用域，教程 Overlay 与实际内容都可以直接访问。
    fun calculateDragDemoFrameBounds(): Rect? {
        if (!(dragTutorialActive && dragAdjustMode && dragTutorialStep == 5)) {
            return null
        }

        val currentHandle = tutorialTargets[7] ?: return null
        val controlRow = tutorialTargets[10] ?: return null

        val edgePad = with(density) { 10.dp.toPx() }
        val expansion = with(density) { 30.dp.toPx() }

        // TutorialOverlay 的描边会向外扩 edgePad，因此这里向内收同等距离，
        // 让最终可见描边严格贴住屏幕左边和上边。
        val anchoredLeft = edgePad
        val anchoredTop = edgePad

        val right = maxOf(
            anchoredLeft + with(density) { 82.dp.toPx() },
            currentHandle.right + expansion
        )

        // 底部最多到顶部三个调节入口所在栏目的上边缘，绝不越过顶栏。
        val maxTargetBottom = controlRow.top - edgePad
        val desiredBottom = maxOf(
            anchoredTop + with(density) { 82.dp.toPx() },
            currentHandle.bottom + expansion
        )
        val bottom = minOf(maxTargetBottom, desiredBottom)

        return Rect(
            left = anchoredLeft,
            top = anchoredTop,
            right = right,
            bottom = maxOf(anchoredTop + 1f, bottom)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.White)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0.5f)
                .onSizeChanged { canvasSize = it }
        ) {
            val strokeW = ThemeSettings.testLineThickness
            val offset = strokeW / 2f

            val L = offset
            val T = offset
            val R = size.width - offset
            val B = size.height - offset

            val tlx = (tlAnim.value + tlXAnim.value).coerceAtLeast(0f)
            val tly = (tlAnim.value + tlYAnim.value).coerceAtLeast(0f)
            val trx = (trAnim.value + trXAnim.value).coerceAtLeast(0f)
            val tryY = (trAnim.value + trYAnim.value).coerceAtLeast(0f)
            val brx = (brAnim.value + brXAnim.value).coerceAtLeast(0f)
            val bry = (brAnim.value + brYAnim.value).coerceAtLeast(0f)
            val blx = (blAnim.value + blXAnim.value).coerceAtLeast(0f)
            val bly = (blAnim.value + blYAnim.value).coerceAtLeast(0f)

            drawPath(
                path = Path().apply {
                    if (isG2Enabled) {
                        // 高精度 G2 连续曲率超椭圆数学重绘（使用单边双阶控制因子平滑消阶）
                        val p = 1.4f  // 缓进斜率基数
                        val c = 0.45f // 连续性平滑修正点

                        moveTo(L + p * tlx, T)
                        lineTo(R - p * trx, T)
                        // 右上角曲率衔接
                        cubicTo(R - c * trx, T, R, T + c * tryY, R, T + p * tryY)
                        lineTo(R, B - p * bry)
                        // 右下角曲率衔接
                        cubicTo(R, B - c * bry, R - c * brx, B, R - p * brx, B)
                        lineTo(L + p * blx, B)
                        // 左下角曲率衔接
                        cubicTo(L + c * blx, B, L, B - c * bly, L, B - p * bly)
                        lineTo(L, T + p * tly)
                        // 左上角曲率衔接
                        cubicTo(L, T + c * tly, L + c * tlx, T, L + p * tlx, T)
                        close()
                    } else {
                        // 经典标准普通圆角（G1 衔接）
                        addRoundRect(RoundRect(
                            left = L, top = T, right = R, bottom = B,
                            topLeftCornerRadius = CornerRadius(tlx, tly),
                            topRightCornerRadius = CornerRadius(trx, tryY),
                            bottomRightCornerRadius = CornerRadius(brx, bry),
                            bottomLeftCornerRadius = CornerRadius(blx, bly)
                        ))
                    }
                },
                color = Color.Red,
                style = Stroke(width = strokeW)
            )

            if (handleRevealProgress > 0.001f) {
                CalibrationCorner.entries.forEach { corner ->
                    val point = cornerHandlePosition(corner, size.width, size.height)
                    // 按住的那个点放大 20%，松手再缩回去（同一时刻只会有一个）
                    val press = if (corner == (draggingCorner ?: lastPressedCorner)) handlePressProgress else 0f
                    val scale = (0.62f + 0.38f * handleRevealProgress) * (1f + 0.2f * press)
                    val alpha = handleRevealProgress
                    drawCircle(
                        color = Color.White.copy(alpha = 0.96f * alpha),
                        radius = (handleRadiusPx + 3.dp.toPx()) * scale,
                        center = point
                    )
                    drawCircle(
                        color = dragHandleColor.copy(alpha = alpha),
                        radius = handleRadiusPx * scale,
                        center = point
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 2.dp.toPx() * scale,
                        center = point
                    )
                }
            }
        }

        // 四角控制点各自拥有独立的触摸区域。
        // 不再让一个覆盖整屏的 pointerInput 截获中央按钮的触摸。
        if (dragAdjustMode && canvasSize != IntSize.Zero) {
            CalibrationCorner.entries.forEach { corner ->
                val handlePosition = cornerHandlePosition(
                    corner,
                    canvasSize.width.toFloat(),
                    canvasSize.height.toFloat()
                )
                val hitSize = with(density) { (handleHitRadiusPx * 2f).toDp() }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (handlePosition.x - handleHitRadiusPx).roundToInt(),
                                (handlePosition.y - handleHitRadiusPx).roundToInt()
                            )
                        }
                        .size(hitSize)
                        .onGloballyPositioned { coords ->
                            if (corner == CalibrationCorner.TOP_LEFT) {
                                val pos = coords.positionInWindow()
                                val centerX = pos.x + coords.size.width / 2f
                                val centerY = pos.y + coords.size.height / 2f
                                // 教程框只取控制点附近的小方框；真正触摸区域仍保持较大的 hit box。
                                val half = with(density) { 26.dp.toPx() }
                                tutorialTargets[7] = Rect(
                                    centerX - half,
                                    centerY - half,
                                    centerX + half,
                                    centerY + half
                                )
                            }
                        }
                        .zIndex(1.5f)
                        .pointerInput(
                            dragAdjustMode,
                            corner,
                            isLinked,
                            isG2Enabled,
                            dragAdjustModeType
                        ) {
                            var hapticDistance = 0f
                            val hapticStep = with(density) { 6.dp.toPx() }

                            detectDragGestures(
                                onDragStart = {
                                    draggingCorner = corner
                                    resetCornerMenuVisible = false
                                    hapticDistance = 0f
                                    focusManager.clearFocus()
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                },
                                onDragCancel = {
                                    if (draggingCorner == corner) draggingCorner = null
                                    hapticDistance = 0f
                                },
                                onDragEnd = {
                                    if (draggingCorner == corner) draggingCorner = null
                                    hapticDistance = 0f
                                },
                                onDrag = { _, dragAmount ->
                                    if (draggingCorner != corner) return@detectDragGestures

                                    hapticDistance += kotlin.math.abs(dragAmount.x) + kotlin.math.abs(dragAmount.y)
                                    if (hapticDistance >= hapticStep) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        hapticDistance = 0f
                                    }

                                    scope.launch {
                                        applyDragAdjust(corner, dragAmount)
                                    }
                                }
                            )
                        }
                )
            }
        }

        fun saveCalibration() {
            ThemeSettings.saveCustomRadius(
                context,
                true,
                tlAnim.targetValue,
                trAnim.targetValue,
                blAnim.targetValue,
                brAnim.targetValue
            )

            // 全局同步 / 四角独立只在点击“保存并应用”后持久化。
            // 用户在本次编辑过程中的切换不会污染上一次已保存的调节模式。
            ThemeSettings.saveCalibrationLinkedMode(context, isLinked)

            // X/Y 曲率修正统一交给 ThemeSettings 保存，但仍使用原有 key，旧用户数据完全兼容。
            ThemeSettings.saveRadiusCorrections(
                context,
                tlXAnim.targetValue, trXAnim.targetValue, blXAnim.targetValue, brXAnim.targetValue,
                tlYAnim.targetValue, trYAnim.targetValue, blYAnim.targetValue, brYAnim.targetValue
            )

            prefs.edit { putBoolean("is_g2_enabled", isG2Enabled) }
        }

        // 共享控制栏采用“占位 + 顶层实例”：占位跟随普通布局移动，
        // 真正按钮脱离普通布局
        var normalControlRowYInRootPx by remember { mutableIntStateOf(0) }
        var rootWindowX by remember { mutableIntStateOf(0) }
        var rootWindowY by remember { mutableIntStateOf(0) }
        var rootHeightPx by remember { mutableIntStateOf(0) }

        val controlRowHeight = 44.dp
        val dragAdjustGroupHeight = 210.dp
        val dragAdjustGroupHeightPx = with(density) { dragAdjustGroupHeight.toPx().roundToInt() }
        val dragAdjustGroupTopY = if (rootHeightPx > 0) {
            ((rootHeightPx - dragAdjustGroupHeightPx) / 2).coerceAtLeast(0)
        } else {
            0
        }

        val modeSwitchSpec: AnimationSpec<Float> =
            if (dragAdjustMode) tween(220, easing = FastOutSlowInEasing)
            else tween(400, easing = FastOutSlowInEasing)
        val normalContentAlpha by animateFloatAsState(
            targetValue = if (dragAdjustMode) 0f else 1f,
            animationSpec = modeSwitchSpec,
            label = "normalContentAlpha"
        )
        val normalContentScale by animateFloatAsState(
            targetValue = if (dragAdjustMode) 0.9f else 1f,
            animationSpec = modeSwitchSpec,
            label = "normalContentScale"
        )

        // 普通模式：按钮回到当前占位的真实位置。
        // 拖拽调整模式：按钮固定在中央目标位置；占位怎么变化都不会影响它。
        val targetControlRowYInRootPx = if (dragAdjustMode) {
            dragAdjustGroupTopY
        } else {
            normalControlRowYInRootPx
        }
        val animatedControlRowYInRootPx by animateIntAsState(
            targetValue = targetControlRowYInRootPx,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "sharedControlRowY"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .onSizeChanged { rootHeightPx = it.height }
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    rootWindowX = pos.x.toInt()
                    rootWindowY = pos.y.toInt()
                }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    // 控件限宽 420；padding 放在 widthIn 之前
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // 仅占位：保持原来的布局关系，让卡片展开/收起时这里自然移动。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlRowHeight)
                        .onGloballyPositioned { coords ->
                            normalControlRowYInRootPx =
                                coords.positionInWindow().y.toInt() - rootWindowY
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .pointerInput(dragAdjustMode) {
                            if (dragAdjustMode) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.forEach { change ->
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                        .zIndex(if (dragAdjustMode) -1f else 0f)
                        .graphicsLayer {
                            alpha = normalContentAlpha
                            scaleX = normalContentScale
                            scaleY = normalContentScale
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tutorialTargets[2] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                    }) {
                        SectionCard(
                            title = "基础圆角半径",
                            isExpanded = activeSection == 0 || 0 in pinnedSections,
                            isPinned = 0 in pinnedSections,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (0 in pinnedSections) {
                                    pinnedSections = pinnedSections - 0
                                } else if (activeSection == 0) {
                                    activeSection = null
                                } else {
                                    activeSection = 0
                                }
                            },
                            onLongClick = {
                                pinnedSections = if (0 in pinnedSections) pinnedSections - 0 else pinnedSections + 0
                            }
                        ) {
                            CalibrationSliderGroup(
                                isLinked,
                                tlAnim,
                                trAnim,
                                blAnim,
                                brAnim,
                                systemRadius,
                                sliderSpec,
                                0f..300f,
                                scope
                            )
                        }
                    }

                    Box(modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tutorialTargets[3] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                    }) {
                        SectionCard(
                            title = "横向 (X轴) 曲率修正",
                            isExpanded = activeSection == 1 || 1 in pinnedSections,
                            isPinned = 1 in pinnedSections,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (1 in pinnedSections) {
                                    pinnedSections = pinnedSections - 1
                                } else if (activeSection == 1) {
                                    activeSection = null
                                } else {
                                    activeSection = 1
                                }
                            },
                            onLongClick = {
                                pinnedSections = if (1 in pinnedSections) pinnedSections - 1 else pinnedSections + 1
                            }
                        ) {
                            CalibrationSliderGroup(
                                isLinked,
                                tlXAnim,
                                trXAnim,
                                blXAnim,
                                brXAnim,
                                0f,
                                sliderSpec,
                                -150f..150f,
                                scope
                            )
                        }
                    }

                    Box(modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tutorialTargets[4] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                    }) {
                        SectionCard(
                            title = "纵向 (Y轴) 曲率修正",
                            isExpanded = activeSection == 2 || 2 in pinnedSections,
                            isPinned = 2 in pinnedSections,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (2 in pinnedSections) {
                                    pinnedSections = pinnedSections - 2
                                } else if (activeSection == 2) {
                                    activeSection = null
                                } else {
                                    activeSection = 2
                                }
                            },
                            onLongClick = {
                                pinnedSections = if (2 in pinnedSections) pinnedSections - 2 else pinnedSections + 2
                            }
                        ) {
                            CalibrationSliderGroup(
                                isLinked,
                                tlYAnim,
                                trYAnim,
                                blYAnim,
                                brYAnim,
                                0f,
                                sliderSpec,
                                -150f..150f,
                                scope
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragAdjustMode) -1f else 0f)
                        .graphicsLayer {
                            alpha = normalContentAlpha
                            scaleX = normalContentScale
                            scaleY = normalContentScale
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        enabled = !dragAdjustMode,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onExit()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = G2Shapes.gridCard,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("取消", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        enabled = !dragAdjustMode,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            saveCalibration()
                            onExit()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = G2Shapes.gridCard,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("保存并应用", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 真正的共享控制栏脱离普通 Column，直接作为顶层交互层。
            Box(
                modifier = Modifier
                    // 控制行独立渲染，单独限宽
                    // padding 放在 widthIn 之前
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(controlRowHeight)
                    .offset {
                        IntOffset(0, animatedControlRowYInRootPx.coerceAtLeast(0))
                    }
                    .zIndex(20f)
            ) {
                CalibrationControlRow(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isLinked = isLinked,
                    isG2Enabled = isG2Enabled,
                    dragAdjustMode = dragAdjustMode,
                    onToggleLinked = {
                        isLinked = !isLinked
                        resetCornerMenuVisible = false
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        focusManager.clearFocus()
                        if (isLinked) {
                            scope.launch { trAnim.animateTo(tlAnim.value, sliderSpec) }
                            scope.launch { blAnim.animateTo(tlAnim.value, sliderSpec) }
                            scope.launch { brAnim.animateTo(tlAnim.value, sliderSpec) }
                            scope.launch { trXAnim.animateTo(tlXAnim.value, sliderSpec) }
                            scope.launch { blXAnim.animateTo(tlXAnim.value, sliderSpec) }
                            scope.launch { brXAnim.animateTo(tlXAnim.value, sliderSpec) }
                            scope.launch { trYAnim.animateTo(tlYAnim.value, sliderSpec) }
                            scope.launch { blYAnim.animateTo(tlYAnim.value, sliderSpec) }
                            scope.launch { brYAnim.animateTo(tlYAnim.value, sliderSpec) }
                        }
                    },
                    onToggleG2 = {
                        isG2Enabled = !isG2Enabled
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    onToggleDirect = {
                        focusManager.clearFocus()
                        dragAdjustMode = !dragAdjustMode
                        if (!tutorialActive) {
                            ThemeSettings.saveDragAdjustMode(context, dragAdjustMode)
                        }
                        resetCornerMenuVisible = false
                        draggingCorner = null
                        if (dragAdjustMode) {
                            keyboardController?.hide()
                            dragAdjustModeType = DragAdjustMode.ALL

                            // 第一次真正进入拖拽调整时，再单独展示拖拽教程。
                            // 不依赖普通教程是否已经看过
                            if (!tutorialActive && !isDragTutorialShown(context)) {
                                dragTutorialStep = 0
                                dragTutorialActive = true
                            }
                        } else {
                            dragTutorialActive = false
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    tutorialTargets = tutorialTargets
                )
            }
        }


        // 拖拽调整内容固定为一个居中的 210dp 高区域，不再参与普通参数区的布局计算。
        // AD 不能 fillMaxWidth：紧约束会压掉内部限宽
        AnimatedVisibility(
            visible = dragAdjustMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .offset { IntOffset(0, dragAdjustGroupTopY + with(density) { 58.dp.toPx().roundToInt() }) }
                .zIndex(10f),
            enter = fadeIn(tween(400, delayMillis = 40, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.9f),
            exit = fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                    scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.9f)
        ) {
            Column(
                modifier = Modifier
                    // 宽度 = min(视口−48, 420)
                    // 不要再加内层 padding（会与 AD 外边距叠加）
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            tutorialTargets[6] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                        }
                ) {
                    DragAdjustModeSelector(
                        selectedMode = dragAdjustModeType,
                        onModeSelected = { mode ->
                            dragAdjustModeType = mode
                            resetCornerMenuVisible = false
                            draggingCorner = null
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (dragAdjustModeType) {
                        DragAdjustMode.ALL -> "拖动四角控制点，综合调整圆角轮廓"
                        DragAdjustMode.RADIUS -> "拖动四角控制点调整圆角半径"
                        DragAdjustMode.HORIZONTAL -> "拖动四角控制点调整横向修正"
                        DragAdjustMode.VERTICAL -> "拖动四角控制点调整纵向修正"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                val resetIconRotationAnimated by animateFloatAsState(
                    targetValue = resetIconRotation,
                    animationSpec = tween(700, easing = FastOutSlowInEasing),
                    label = "resetIconRotation"
                )

                val resetContentDescription = when (dragAdjustModeType) {
                    DragAdjustMode.ALL -> "重置全部调整"
                    DragAdjustMode.RADIUS -> "重置圆角半径"
                    DragAdjustMode.HORIZONTAL -> "重置横向修正"
                    DragAdjustMode.VERTICAL -> "重置纵向修正"
                }

                fun animateResetForCorner(corner: CalibrationCorner) {
                    when (dragAdjustModeType) {
                        DragAdjustMode.ALL -> {
                            when (corner) {
                                CalibrationCorner.TOP_LEFT -> {
                                    scope.launch { tlAnim.animateTo(systemRadius, sliderSpec) }
                                    scope.launch { tlXAnim.animateTo(0f, sliderSpec) }
                                    scope.launch { tlYAnim.animateTo(0f, sliderSpec) }
                                }
                                CalibrationCorner.TOP_RIGHT -> {
                                    scope.launch { trAnim.animateTo(systemRadius, sliderSpec) }
                                    scope.launch { trXAnim.animateTo(0f, sliderSpec) }
                                    scope.launch { trYAnim.animateTo(0f, sliderSpec) }
                                }
                                CalibrationCorner.BOTTOM_LEFT -> {
                                    scope.launch { blAnim.animateTo(systemRadius, sliderSpec) }
                                    scope.launch { blXAnim.animateTo(0f, sliderSpec) }
                                    scope.launch { blYAnim.animateTo(0f, sliderSpec) }
                                }
                                CalibrationCorner.BOTTOM_RIGHT -> {
                                    scope.launch { brAnim.animateTo(systemRadius, sliderSpec) }
                                    scope.launch { brXAnim.animateTo(0f, sliderSpec) }
                                    scope.launch { brYAnim.animateTo(0f, sliderSpec) }
                                }
                            }
                        }
                        DragAdjustMode.RADIUS -> {
                            when (corner) {
                                CalibrationCorner.TOP_LEFT -> scope.launch { tlAnim.animateTo(systemRadius, sliderSpec) }
                                CalibrationCorner.TOP_RIGHT -> scope.launch { trAnim.animateTo(systemRadius, sliderSpec) }
                                CalibrationCorner.BOTTOM_LEFT -> scope.launch { blAnim.animateTo(systemRadius, sliderSpec) }
                                CalibrationCorner.BOTTOM_RIGHT -> scope.launch { brAnim.animateTo(systemRadius, sliderSpec) }
                            }
                        }
                        DragAdjustMode.HORIZONTAL -> {
                            when (corner) {
                                CalibrationCorner.TOP_LEFT -> scope.launch { tlXAnim.animateTo(0f, sliderSpec) }
                                CalibrationCorner.TOP_RIGHT -> scope.launch { trXAnim.animateTo(0f, sliderSpec) }
                                CalibrationCorner.BOTTOM_LEFT -> scope.launch { blXAnim.animateTo(0f, sliderSpec) }
                                CalibrationCorner.BOTTOM_RIGHT -> scope.launch { brXAnim.animateTo(0f, sliderSpec) }
                            }
                        }
                        DragAdjustMode.VERTICAL -> {
                            when (corner) {
                                CalibrationCorner.TOP_LEFT -> scope.launch { tlYAnim.animateTo(0f, sliderSpec) }
                                CalibrationCorner.TOP_RIGHT -> scope.launch { trYAnim.animateTo(0f, sliderSpec) }
                                CalibrationCorner.BOTTOM_LEFT -> scope.launch { blYAnim.animateTo(0f, sliderSpec) }
                                CalibrationCorner.BOTTOM_RIGHT -> scope.launch { brYAnim.animateTo(0f, sliderSpec) }
                            }
                        }
                    }
                }

                fun resetAllDragAdjustParameters() {
                    scope.launch {
                        when (dragAdjustModeType) {
                            DragAdjustMode.ALL -> {
                                launch { tlAnim.animateTo(systemRadius, sliderSpec) }
                                launch { trAnim.animateTo(systemRadius, sliderSpec) }
                                launch { blAnim.animateTo(systemRadius, sliderSpec) }
                                launch { brAnim.animateTo(systemRadius, sliderSpec) }
                                launch { tlXAnim.animateTo(0f, sliderSpec) }
                                launch { trXAnim.animateTo(0f, sliderSpec) }
                                launch { blXAnim.animateTo(0f, sliderSpec) }
                                launch { brXAnim.animateTo(0f, sliderSpec) }
                                launch { tlYAnim.animateTo(0f, sliderSpec) }
                                launch { trYAnim.animateTo(0f, sliderSpec) }
                                launch { blYAnim.animateTo(0f, sliderSpec) }
                                launch { brYAnim.animateTo(0f, sliderSpec) }
                            }
                            DragAdjustMode.RADIUS -> {
                                launch { tlAnim.animateTo(systemRadius, sliderSpec) }
                                launch { trAnim.animateTo(systemRadius, sliderSpec) }
                                launch { blAnim.animateTo(systemRadius, sliderSpec) }
                                launch { brAnim.animateTo(systemRadius, sliderSpec) }
                            }
                            DragAdjustMode.HORIZONTAL -> {
                                launch { tlXAnim.animateTo(0f, sliderSpec) }
                                launch { trXAnim.animateTo(0f, sliderSpec) }
                                launch { blXAnim.animateTo(0f, sliderSpec) }
                                launch { brXAnim.animateTo(0f, sliderSpec) }
                            }
                            DragAdjustMode.VERTICAL -> {
                                launch { tlYAnim.animateTo(0f, sliderSpec) }
                                launch { trYAnim.animateTo(0f, sliderSpec) }
                                launch { blYAnim.animateTo(0f, sliderSpec) }
                                launch { brYAnim.animateTo(0f, sliderSpec) }
                            }
                        }
                    }
                }

                fun resetSelectedCorner(corner: CalibrationCorner?) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    focusManager.clearFocus()
                    draggingCorner = null
                    resetCornerMenuVisible = false
                    resetIconRotation += 360f

                    if (corner == null) {
                        resetAllDragAdjustParameters()
                    } else {
                        animateResetForCorner(corner)
                    }
                }

                val showCornerResetCard = !isLinked && resetCornerMenuVisible

                AnimatedContent(
                    targetState = showCornerResetCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            tutorialTargets[8] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                        },
                    transitionSpec = {
                        val enter =
                            fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                                    slideInVertically(
                                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                                        initialOffsetY = { -it / 3 }
                                    ) +
                                    scaleIn(
                                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                                        initialScale = 0.94f,
                                        transformOrigin = TransformOrigin(0.5f, 1f)
                                    )
                        val exit =
                            fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(
                                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                                        targetOffsetY = { -it / 4 }
                                    ) +
                                    scaleOut(
                                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                                        targetScale = 0.94f,
                                        transformOrigin = TransformOrigin(0.5f, 1f)
                                    )
                        (enter togetherWith exit).using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ -> tween(1) }
                            )
                        )
                    },
                    label = "dragResetContent"
                ) { showingResetCard ->
                    if (showingResetCard) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            shape = G2Shapes.card,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "重置哪个角",
                                        modifier = Modifier.weight(1f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = {
                                            resetCornerMenuVisible = false
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "关闭角选择",
                                            modifier = Modifier.size(17.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    CornerResetOption(
                                        label = "左上角",
                                        modifier = Modifier.weight(1f),
                                        onClick = { resetSelectedCorner(CalibrationCorner.TOP_LEFT) }
                                    )
                                    CornerResetOption(
                                        label = "右上角",
                                        modifier = Modifier.weight(1f),
                                        onClick = { resetSelectedCorner(CalibrationCorner.TOP_RIGHT) }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    CornerResetOption(
                                        label = "左下角",
                                        modifier = Modifier.weight(1f),
                                        onClick = { resetSelectedCorner(CalibrationCorner.BOTTOM_LEFT) }
                                    )
                                    CornerResetOption(
                                        label = "右下角",
                                        modifier = Modifier.weight(1f),
                                        onClick = { resetSelectedCorner(CalibrationCorner.BOTTOM_RIGHT) }
                                    )
                                }

                                CornerResetOption(
                                    label = "全部四角",
                                    modifier = Modifier.fillMaxWidth(),
                                    emphasize = true,
                                    onClick = { resetSelectedCorner(null) }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    draggingCorner = null
                                    onExit()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消并退出",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(
                                onClick = {
                                    if (isLinked) {
                                        resetSelectedCorner(null)
                                    } else {
                                        resetCornerMenuVisible = true
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = if (isLinked) resetContentDescription else "选择要重置的角",
                                    modifier = Modifier.graphicsLayer { rotationZ = resetIconRotationAnimated },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    draggingCorner = null
                                    saveCalibration()
                                    onExit()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "保存并应用",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 普通教程：只负责首次进入校准页面的基础功能说明。
    if (tutorialActive) {
        val steps = listOf(
            "调节模式" to "点击可切换调节模式\n全局同步调节四角圆角一致，若您的屏幕四角曲率不同可切换为四角独立调节，该模式下可分别调整四个角至贴合手机屏幕圆角",
            "G2 平滑圆角" to "开启后使用 G2 连续曲率算法，圆角线条更加圆润流畅，贴合屏幕物理曲率",
            "拖拽调整" to "点击这个手势按钮即可进入拖拽调整模式。进入后可以直接拖动四角控制点，不需要手动计算圆角数值",
            "基础圆角半径" to "您可通过拖动滑块、点击 +/- 按钮或点击数字输入数值调节屏幕四个角的圆角半径大小，并实时预览线条变化",
            "卡片与调节操作说明" to "长按卡片可固定，点击重置按钮可恢复默认，点击 +/- 按钮可 ± 0.1，长按 +/- 按钮可快速连续加减",
            "横向 X 轴曲率修正" to "微调圆角在水平方向的偏移量",
            "纵向 Y 轴曲率修正" to "微调圆角在垂直方向的偏移量"
        )

        val targetIndex = when (tutorialStep) {
            0 -> 0
            1 -> 1
            2 -> 5
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
            onPrevious = if (tutorialStep > 0) ({ tutorialStep-- }) else null,
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

    // 拖拽调整专属教程：第一次进入拖拽调整模式时出现。
    if (dragTutorialActive) {
        val dragSteps = listOf(
            "四角控制点" to "进入拖拽调整后，屏幕四个角都会出现控制点。拖动对应控制点，就能直接贴合实际屏幕圆角",
            "全局同步调节" to "全局同步调节会让四个角保持一致，适合四角曲率基本相同的屏幕。需要分别贴合四个角时，可切换为四角独立调节分别调节",
            "G2 平滑圆角" to "建议先开启 G2 平滑，再开始拖拽调整。开启后圆角曲线会更加连续，更贴合实际屏幕圆角",
            "拖拽调整" to "在拖拽调整模式下，点击这个手势按钮可退出拖拽调整模式回到卡片滑块调整模式",
            "选择调整方式" to "这里可以选择调整什么：全部调整、圆角半径、横向修正或纵向修正。“全部调整”一次拖动即可同时调整圆角半径与横纵修正，可先用它让线条轮廓大致贴合屏幕圆角，再用其它模式单独微调",
            "拖一下就能看到变化" to "把左上角控制点向屏幕内部拖动试试看。上方这一整块就是实际调整区域，轮廓会实时变化，不需要计算数值，直接拖拽调整至与手机屏幕圆角贴合",
            "重置与保存" to "↻ 重置本次拖拽调整\n× 放弃本次修改并退出校准\n✓ 保存并应用"
        )

        val dragTargetIndex = when (dragTutorialStep) {
            0 -> 7
            1 -> 0
            2 -> 1
            3 -> 5
            4 -> 6
            5 -> 9
            6 -> 8
            else -> 7
        }

        TutorialOverlay(
            currentStep = dragTutorialStep,
            totalSteps = 7,
            targetBounds = if (dragTutorialStep == 5) calculateDragDemoFrameBounds() else tutorialTargets[dragTargetIndex],
            title = dragSteps[dragTutorialStep].first,
            description = dragSteps[dragTutorialStep].second,
            dragDemoAnchor = tutorialTargets[7],
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
            isUserDragging = draggingCorner != null
        )
    }

}

@Composable
private fun CalibrationControlRow(
    modifier: Modifier = Modifier,
    isLinked: Boolean,
    isG2Enabled: Boolean,
    dragAdjustMode: Boolean,
    onToggleLinked: () -> Unit,
    onToggleG2: () -> Unit,
    onToggleDirect: () -> Unit,
    tutorialTargets: MutableMap<Int, Rect>
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                tutorialTargets[10] = Rect(
                    pos.x,
                    pos.y,
                    pos.x + coords.size.width,
                    pos.y + coords.size.height
                )
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[0] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }
                .weight(1.2f)
                .height(44.dp),
            onClick = onToggleLinked,
            shape = G2Shapes.gridCard
        ) {
            Text(
                text = if (isLinked) "全局同步调节" else "四角独立调节",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        val g2ButtonContainerColor by animateColorAsState(
            targetValue = if (isG2Enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            animationSpec = tween(300),
            label = "g2ButtonContainerColor"
        )
        val g2ButtonContentColor by animateColorAsState(
            targetValue = if (isG2Enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(300),
            label = "g2ButtonContentColor"
        )

        Button(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[1] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }
                .weight(0.8f)
                .height(44.dp),
            onClick = onToggleG2,
            shape = G2Shapes.gridCard,
            colors = ButtonDefaults.buttonColors(
                containerColor = g2ButtonContainerColor,
                contentColor = g2ButtonContentColor
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            // 字号过大时按比例缩小，保证单行
            var g2TextSize by remember { mutableStateOf(13f) }
            Text(
                text = if (isG2Enabled) "G2平滑:开" else "G2平滑:关",
                fontWeight = FontWeight.Bold,
                fontSize = g2TextSize.sp,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    if (result.hasVisualOverflow && g2TextSize > 8f) g2TextSize *= 0.9f
                }
            )
        }

        val dragAdjustButtonContainerColor by animateColorAsState(
            targetValue = if (dragAdjustMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            animationSpec = tween(300),
            label = "dragAdjustButtonContainerColor"
        )
        val dragAdjustButtonContentColor by animateColorAsState(
            targetValue = if (dragAdjustMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(300),
            label = "dragAdjustButtonContentColor"
        )

        Surface(
            onClick = onToggleDirect,
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[5] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }
                .size(44.dp),
            shape = G2Shapes.gridCard,
            color = dragAdjustButtonContainerColor,
            contentColor = dragAdjustButtonContentColor,
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
                    tint = dragAdjustButtonContentColor
                )
            }
        }
    }
}

internal enum class DragAdjustMode {
    ALL,
    RADIUS,
    HORIZONTAL,
    VERTICAL
}

@Composable
internal fun CornerResetOption(
    label: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (emphasize) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val contentColor = if (emphasize) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(34.dp)
            .clip(G2Shapes.gridCard),
        shape = G2Shapes.gridCard,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = if (emphasize) 12.5.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
internal fun DragAdjustModeSelector(
    selectedMode: DragAdjustMode,
    onModeSelected: (DragAdjustMode) -> Unit
) = SegmentedSlideSelector(
    items = listOf(
        DragAdjustMode.ALL to "全部调整",
        DragAdjustMode.RADIUS to "圆角半径",
        DragAdjustMode.HORIZONTAL to "横向修正",
        DragAdjustMode.VERTICAL to "纵向修正"
    ),
    selected = selectedMode,
    onSelect = onModeSelected
)

// 通用分段滑块选择器已抽到 SegmentedSlideSelector.kt

internal enum class CalibrationCorner(val signX: Float, val signY: Float) {
    TOP_LEFT(1f, 1f),
    TOP_RIGHT(-1f, 1f),
    BOTTOM_RIGHT(-1f, -1f),
    BOTTOM_LEFT(1f, -1f)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SectionCard(
    title: String,
    isExpanded: Boolean,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )
    val pinAlpha by animateFloatAsState(
        targetValue = if (isPinned) 1f else 0f,
        label = "pinAlpha"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = G2Shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                // 固定图标
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已固定",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { alpha = pinAlpha },
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
fun CalibrationSliderGroup(
    isLinked: Boolean,
    tl: Animatable<Float, *>, tr: Animatable<Float, *>, bl: Animatable<Float, *>, br: Animatable<Float, *>,
    defaultVal: Float, sliderSpec: AnimationSpec<Float>, valueRange: ClosedFloatingPointRange<Float>,
    scope: CoroutineScope
) {
    AnimatedContent(
        targetState = isLinked,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = ""
    ) { linked ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (linked) {
                CalibrationItem("同步调节", tl, defaultVal, valueRange) { t, imm ->
                    scope.launch {
                        if (imm) { tl.snapTo(t); tr.snapTo(t); bl.snapTo(t); br.snapTo(t) }
                        else { launch { tl.animateTo(t, sliderSpec) }; launch { tr.animateTo(t, sliderSpec) }; launch { bl.animateTo(t, sliderSpec) }; launch { br.animateTo(t, sliderSpec) } }
                    }
                }
            } else {
                CalibrationItem("左上角", tl, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) tl.snapTo(t) else tl.animateTo(t, sliderSpec) } }
                CalibrationItem("右上角", tr, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) tr.snapTo(t) else tr.animateTo(t, sliderSpec) } }
                CalibrationItem("左下角", bl, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) bl.snapTo(t) else bl.animateTo(t, sliderSpec) } }
                CalibrationItem("右下角", br, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) br.snapTo(t) else br.animateTo(t, sliderSpec) } }
            }
        }
    }
}

@Composable
fun CalibrationItem(
    label: String,
    animatable: Animatable<Float, *>,
    systemDefault: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..300f,
    onAnimate: (Float, Boolean) -> Unit
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }

    val displayValue = if (isFocused) typedText else String.format(java.util.Locale.US, "%.1f", animatable.value)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                IconButton(
                    onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); focusManager.clearFocus(); onAnimate(systemDefault, false) },
                    modifier = Modifier.size(30.dp)
                ) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // - 按钮
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(valueRange) {
                            coroutineScope {
                                var pressed = false
                                launch {
                                    detectTapGestures(
                                        onPress = {
                                            pressed = true
                                            focusManager.clearFocus()
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onAnimate((animatable.value - 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                            tryAwaitRelease()
                                            pressed = false
                                        }
                                    )
                                }
                                launch {
                                    while (true) {
                                        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                                        kotlinx.coroutines.delay(500.milliseconds)
                                        if (pressed) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            while (pressed) {
                                                onAnimate((animatable.value - 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                kotlinx.coroutines.delay(80.milliseconds)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = displayValue,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal == "-" || newVal.matches(Regex("^-?\\d*\\.?\\d*$"))) {
                                if (newVal.length <= 6) {
                                    typedText = newVal
                                    val num = newVal.toFloatOrNull() ?: 0f
                                    onAnimate(num.coerceIn(valueRange.start, valueRange.endInclusive), false)
                                }
                            }
                        },
                        modifier = Modifier
                            .width(64.dp)
                            .onFocusChanged {
                                isFocused = it.isFocused
                                if (it.isFocused) typedText = String.format(java.util.Locale.US, "%.1f", animatable.value)
                            },
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                    Text("px", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
                }
                // + 按钮
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(valueRange) {
                            coroutineScope {
                                var pressed = false
                                launch {
                                    detectTapGestures(
                                        onPress = {
                                            pressed = true
                                            focusManager.clearFocus()
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onAnimate((animatable.value + 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                            tryAwaitRelease()
                                            pressed = false
                                        }
                                    )
                                }
                                launch {
                                    while (true) {
                                        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                                        kotlinx.coroutines.delay(500.milliseconds)
                                        if (pressed) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            while (pressed) {
                                                onAnimate((animatable.value + 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                kotlinx.coroutines.delay(80.milliseconds)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Slider(
            value = animatable.value,
            valueRange = valueRange,
            onValueChange = { newValue ->
                focusManager.clearFocus()
                onAnimate(newValue, true)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}