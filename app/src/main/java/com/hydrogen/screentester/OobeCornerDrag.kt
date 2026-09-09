package com.hydrogen.screentester

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// OOBE 圆角校准页的拖拽调整层。
// 控件复用校准车间的那套，但状态各自持有：校准车间「保存并应用」才落盘，OOBE 实时落盘，
// 所以这里多一个进入时的快照，供 × 回滚。

/** 拖拽模式下让已淡出、但实际仍可点击的内容不再响应触摸 */
internal fun Modifier.blockTouchesWhile(blocked: Boolean): Modifier =
    if (blocked) {
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else this

/** 进入拖拽时的 12 个值，用于 × 回滚 */
internal data class CornerRadiusSnapshot(
    val tl: Float, val tr: Float, val bl: Float, val br: Float,
    val tlX: Float, val trX: Float, val blX: Float, val brX: Float,
    val tlY: Float, val trY: Float, val blY: Float, val brY: Float
)

/** 12 个可调值（4 个基础半径 + 8 个 X/Y 修正）的读写集合，不含持久化 */
@androidx.compose.runtime.Stable
internal class CornerRadiusSet(
    val tl: Animatable<Float, *>, val tr: Animatable<Float, *>,
    val bl: Animatable<Float, *>, val br: Animatable<Float, *>,
    val tlX: Animatable<Float, *>, val trX: Animatable<Float, *>,
    val blX: Animatable<Float, *>, val brX: Animatable<Float, *>,
    val tlY: Animatable<Float, *>, val trY: Animatable<Float, *>,
    val blY: Animatable<Float, *>, val brY: Animatable<Float, *>
) {
    private fun radiusOf(c: CalibrationCorner) = when (c) {
        CalibrationCorner.TOP_LEFT -> tl
        CalibrationCorner.TOP_RIGHT -> tr
        CalibrationCorner.BOTTOM_RIGHT -> br
        CalibrationCorner.BOTTOM_LEFT -> bl
    }

    private fun xOf(c: CalibrationCorner) = when (c) {
        CalibrationCorner.TOP_LEFT -> tlX
        CalibrationCorner.TOP_RIGHT -> trX
        CalibrationCorner.BOTTOM_RIGHT -> brX
        CalibrationCorner.BOTTOM_LEFT -> blX
    }

    private fun yOf(c: CalibrationCorner) = when (c) {
        CalibrationCorner.TOP_LEFT -> tlY
        CalibrationCorner.TOP_RIGHT -> trY
        CalibrationCorner.BOTTOM_RIGHT -> brY
        CalibrationCorner.BOTTOM_LEFT -> blY
    }

    /** 最终生效的圆角（基础半径 + 对应方向修正），负值归零——与轮廓绘制口径一致。 */
    fun effective(c: CalibrationCorner): Offset = Offset(
        (radiusOf(c).value + xOf(c).value).coerceAtLeast(0f),
        (radiusOf(c).value + yOf(c).value).coerceAtLeast(0f)
    )

    fun snapshot() = CornerRadiusSnapshot(
        tl.value, tr.value, bl.value, br.value,
        tlX.value, trX.value, blX.value, brX.value,
        tlY.value, trY.value, blY.value, brY.value
    )

    private fun restoreTargets(s: CornerRadiusSnapshot): List<Pair<Animatable<Float, *>, Float>> = listOf(
        tl to s.tl, tr to s.tr, bl to s.bl, br to s.br,
        tlX to s.tlX, trX to s.trX, blX to s.blX, brX to s.brX,
        tlY to s.tlY, trY to s.trY, blY to s.blY, brY to s.brY
    )

    /** × 回滚：把 12 个值平滑送回进入拖拽时的快照。 */
    fun restore(scope: CoroutineScope, s: CornerRadiusSnapshot, spec: AnimationSpec<Float>) {
        scope.launch {
            restoreTargets(s).forEach { (anim, target) -> launch { anim.animateTo(target, spec) } }
        }
    }

    /** 拖拽位移 → 参数增量，与校准车间同口径 */
    suspend fun applyDrag(
        corner: CalibrationCorner,
        dragAmount: Offset,
        mode: DragAdjustMode,
        linked: Boolean,
        g2Factor: Float
    ) {
        val localX = dragAmount.x * corner.signX / g2Factor
        val localY = dragAmount.y * corner.signY / g2Factor

        val radiusDelta = when (mode) {
            DragAdjustMode.ALL, DragAdjustMode.RADIUS -> (localX + localY) / 2f
            DragAdjustMode.HORIZONTAL, DragAdjustMode.VERTICAL -> 0f
        }
        val xDelta = when (mode) {
            DragAdjustMode.ALL -> localX - radiusDelta
            DragAdjustMode.RADIUS, DragAdjustMode.VERTICAL -> 0f
            DragAdjustMode.HORIZONTAL -> localX
        }
        val yDelta = when (mode) {
            DragAdjustMode.ALL -> localY - radiusDelta
            DragAdjustMode.RADIUS, DragAdjustMode.HORIZONTAL -> 0f
            DragAdjustMode.VERTICAL -> localY
        }

        val targets = if (linked) CalibrationCorner.entries.toList() else listOf(corner)
        targets.forEach { c ->
            val r = radiusOf(c)
            val x = xOf(c)
            val y = yOf(c)
            r.snapTo((r.value + radiusDelta).coerceIn(0f, 300f))
            x.snapTo((x.value + xDelta).coerceIn(-150f, 150f))
            y.snapTo((y.value + yDelta).coerceIn(-150f, 150f))
        }
    }

    /** ↻ 重置的目标值列表；corner 为 null 表示四角全重置 */
    fun resetTargets(
        mode: DragAdjustMode,
        corner: CalibrationCorner?,
        systemRadius: Float
    ): List<Pair<Animatable<Float, *>, Float>> {
        val corners = if (corner != null) listOf(corner) else CalibrationCorner.entries.toList()
        val out = mutableListOf<Pair<Animatable<Float, *>, Float>>()
        corners.forEach { c ->
            when (mode) {
                DragAdjustMode.ALL -> {
                    out += radiusOf(c) to systemRadius
                    out += xOf(c) to 0f
                    out += yOf(c) to 0f
                }
                DragAdjustMode.RADIUS -> out += radiusOf(c) to systemRadius
                DragAdjustMode.HORIZONTAL -> out += xOf(c) to 0f
                DragAdjustMode.VERTICAL -> out += yOf(c) to 0f
            }
        }
        return out
    }
}

/**
 * OOBE 拖拽调整覆盖层。挂在 OOBE 根 Box 上（pager 之外），
 * 所以控制点在轮廓之上、按钮在悬浮底栏之上。
 * @param strokeWidth 轮廓线宽，让控制点严格落在轮廓上
 * @param handleColor 控制点颜色，取混色后的 primary
 * @param onDraggingChanged 是否正在拖动，供教程高亮框跟手
 * @param onExitKeep ✓ 保留改动退出
 * @param onExitRollback × 回滚退出
 */
@Composable
internal fun OobeCornerDragOverlay(
    visible: Boolean,
    radiusSet: CornerRadiusSet,
    isLinked: Boolean,
    isG2Enabled: Boolean,
    systemRadius: Float,
    strokeWidth: Float,
    handleColor: Color,
    onDraggingChanged: (Boolean) -> Unit,
    onExitKeep: () -> Unit,
    onExitRollback: () -> Unit,
    tutorialTargets: MutableMap<Int, Rect>,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    // 拖动状态必须放本组件内：从参数传入会被 pointerInput 捕获成旧值，onDrag 会把自己挡掉
    var draggingCorner by remember { mutableStateOf<CalibrationCorner?>(null) }
    var mode by remember { mutableStateOf(DragAdjustMode.ALL) }
    var resetMenuVisible by remember { mutableStateOf(false) }
    var resetIconRotation by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val handleHitRadiusPx = with(density) { 52.dp.toPx() }
    val handleRadiusPx = with(density) { 7.dp.toPx() }
    val sliderSpec = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)

    val handleRevealProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "oobeDragHandleReveal"
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
        label = "oobeHandlePress"
    )

    // 拖动状态统一上报给外部
    LaunchedEffect(draggingCorner) { onDraggingChanged(draggingCorner != null) }

    // 退出时把工具层的临时状态复位，下次进入回到「全部调整」
    LaunchedEffect(visible) {
        if (!visible) {
            mode = DragAdjustMode.ALL
            resetMenuVisible = false
            draggingCorner = null
        }
    }

    // 控制点始终代表“最终生效圆角”的位置，与轮廓路径同口径。
    fun handlePosition(corner: CalibrationCorner, width: Float, height: Float): Offset {
        val o = strokeWidth / 2f
        val left = o
        val top = o
        val right = width - o
        val bottom = height - o
        val p = if (isG2Enabled) 1.4f else 1f
        val e = radiusSet.effective(corner)
        return when (corner) {
            CalibrationCorner.TOP_LEFT -> Offset(left + p * e.x, top + p * e.y)
            CalibrationCorner.TOP_RIGHT -> Offset(right - p * e.x, top + p * e.y)
            CalibrationCorner.BOTTOM_RIGHT -> Offset(right - p * e.x, bottom - p * e.y)
            CalibrationCorner.BOTTOM_LEFT -> Offset(left + p * e.x, bottom - p * e.y)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        if (handleRevealProgress > 0.001f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                CalibrationCorner.entries.forEach { corner ->
                    val point = handlePosition(corner, size.width, size.height)
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
                        color = handleColor.copy(alpha = alpha),
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

        // 四角控制点各自的独立触摸区域
        if (visible && canvasSize != IntSize.Zero) {
            CalibrationCorner.entries.forEach { corner ->
                val position = handlePosition(
                    corner,
                    canvasSize.width.toFloat(),
                    canvasSize.height.toFloat()
                )
                val hitSize = with(density) { (handleHitRadiusPx * 2f).toDp() }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (position.x - handleHitRadiusPx).roundToInt(),
                                (position.y - handleHitRadiusPx).roundToInt()
                            )
                        }
                        .size(hitSize)
                        .onGloballyPositioned { coords ->
                            if (corner == CalibrationCorner.TOP_LEFT) {
                                val pos = coords.positionInWindow()
                                val centerX = pos.x + coords.size.width / 2f
                                val centerY = pos.y + coords.size.height / 2f
                                val half = with(density) { 26.dp.toPx() }
                                tutorialTargets[OOBE_DRAG_TARGET_HANDLE] = Rect(
                                    centerX - half,
                                    centerY - half,
                                    centerX + half,
                                    centerY + half
                                )
                            }
                        }
                        .zIndex(1.5f)
                        .pointerInput(visible, corner, isLinked, isG2Enabled, mode) {
                            var hapticDistance = 0f
                            val hapticStep = with(density) { 6.dp.toPx() }
                            val g2Factor = if (isG2Enabled) 1.4f else 1f

                            detectDragGestures(
                                onDragStart = {
                                    draggingCorner = corner
                                    resetMenuVisible = false
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
                                        radiusSet.applyDrag(corner, dragAmount, mode, isLinked, g2Factor)
                                    }
                                }
                            )
                        }
                )
            }
        }

        // 工具层：模式选择器 + 提示 + ↻/×/✓，固定居中
        val groupHeightPx = with(density) { 210.dp.toPx() }
        val toolTopY = ((canvasSize.height - groupHeightPx) / 2f).coerceAtLeast(0f) +
                with(density) { 58.dp.toPx() }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 不能 fillMaxWidth：紧约束会压掉内部限宽
                .offset { IntOffset(0, toolTopY.roundToInt()) }
                .padding(horizontal = 24.dp)
                .zIndex(10f),
            // 与 OOBE 底栏同一口径：淡入淡出 + 轻微缩放，不做位移
            enter = fadeIn(tween(400, delayMillis = 40, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.9f),
            exit = fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                    scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.9f)
        ) {
            Column(
                // 控件限宽；widthIn 必须在 fillMaxWidth 之前
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                tutorialTargets[OOBE_DRAG_TARGET_MODE_SELECTOR] = Rect(
                                    pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height
                                )
                            }
                    ) {
                        DragAdjustModeSelector(
                            selectedMode = mode,
                            onModeSelected = { selected ->
                                mode = selected
                                resetMenuVisible = false
                                draggingCorner = null
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when (mode) {
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
                        label = "oobeResetIconRotation"
                    )

                    val resetContentDescription = when (mode) {
                        DragAdjustMode.ALL -> "重置全部调整"
                        DragAdjustMode.RADIUS -> "重置圆角半径"
                        DragAdjustMode.HORIZONTAL -> "重置横向修正"
                        DragAdjustMode.VERTICAL -> "重置纵向修正"
                    }

                    fun resetSelectedCorner(corner: CalibrationCorner?) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        focusManager.clearFocus()
                        draggingCorner = null
                        resetMenuVisible = false
                        resetIconRotation += 360f
                        scope.launch {
                            radiusSet.resetTargets(mode, corner, systemRadius).forEach { (anim, target) ->
                                launch { anim.animateTo(target, sliderSpec) }
                            }
                        }
                    }

                    val showCornerResetCard = !isLinked && resetMenuVisible

                    AnimatedContent(
                        targetState = showCornerResetCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                tutorialTargets[OOBE_DRAG_TARGET_ACTIONS] = Rect(
                                    pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height
                                )
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
                        label = "oobeDragResetContent"
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
                                                resetMenuVisible = false
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
                                // ×：回滚本次拖拽改动并退出拖拽模式
                                IconButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        draggingCorner = null
                                        onExitRollback()
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "放弃本次拖拽改动",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                IconButton(
                                    onClick = {
                                        if (isLinked) {
                                            resetSelectedCorner(null)
                                        } else {
                                            resetMenuVisible = true
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

                                // ✓：保留本次改动并退出拖拽模式
                                IconButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        draggingCorner = null
                                        onExitKeep()
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "保留本次调整",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

// 拖拽教程用到的教程目标下标。与圆角校准页普通教程使用的 0..4 分开，互不干扰。
internal const val OOBE_DRAG_TARGET_GESTURE_BUTTON = 10
internal const val OOBE_DRAG_TARGET_HANDLE = 11
internal const val OOBE_DRAG_TARGET_MODE_SELECTOR = 12
internal const val OOBE_DRAG_TARGET_ACTIONS = 13
