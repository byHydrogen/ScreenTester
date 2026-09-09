package com.hydrogen.screentester

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun <T> SegmentedSlideSelector(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {

    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()
    val gap = 6.dp
    val padding = 3.dp
    var selectorWidthPx by remember { mutableIntStateOf(0) }

    // 拖动期间由普通 State 负责 1:1 跟手；松手 / 点击后再由 Animatable 做弹簧吸附。
    var isDragging by remember { mutableStateOf(false) }
    var dragIndicatorX by remember { mutableFloatStateOf(0f) }
    val indicatorAnim = remember { Animatable(0f) }

    val horizontalPaddingPx = with(density) { padding.toPx() }
    val gapPx = with(density) { gap.toPx() }
    // 按实际项数分配
    val availableWidthPx =
        (selectorWidthPx - horizontalPaddingPx * 2f - gapPx * (items.size - 1)).coerceAtLeast(0f)
    val itemWidthPx = if (items.isEmpty()) 0f else availableWidthPx / items.size
    val stepPx = itemWidthPx + gapPx
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val firstX = horizontalPaddingPx
    val minX = firstX
    val maxX = (firstX + stepPx * items.lastIndex).coerceAtLeast(minX)
    val selectedTargetX = firstX + selectedIndex * stepPx

    val springSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 400f
    )

    fun targetXFor(mode: T): Float {
        val index = items.indexOfFirst { it.first == mode }.coerceAtLeast(0)
        return firstX + index * stepPx
    }

    LaunchedEffect(selectorWidthPx) {
        if (selectorWidthPx > 0) {
            val target = selectedTargetX
            indicatorAnim.snapTo(target)
            dragIndicatorX = target
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .onSizeChanged { selectorWidthPx = it.width }
            .clip(G2Shapes.gridCard)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .pointerInput(selectorWidthPx, selectedIndex) {
                // 自己判方向：阈值低于系统 touchSlop，横向为主才抢手势；
                // 纵向为主直接放行给页面滚动，避免拖动选择器时误触发页面滚动
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    var claimed = false
                    val claimSlop = 6.dp.toPx()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (!claimed) {
                            if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat() > claimSlop) {
                                if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                    claimed = true
                                    isDragging = true
                                    dragIndicatorX = indicatorAnim.value.coerceIn(minX, maxX)
                                    scope.launch { indicatorAnim.stop() }
                                } else {
                                    break
                                }
                            }
                        }
                        if (claimed) {
                            change.consume()
                            if (selectorWidthPx > 0 && itemWidthPx > 0f) {
                                val nextX = dragIndicatorX + delta.x
                                dragIndicatorX = when {
                                    nextX < minX -> minX + (nextX - minX) * 0.25f
                                    nextX > maxX -> maxX + (nextX - maxX) * 0.25f
                                    else -> nextX
                                }
                            }
                        }
                        if (!change.pressed) break
                    }
                    if (claimed) {
                        if (selectorWidthPx > 0 && stepPx > 0f) {
                            val releaseX = dragIndicatorX.coerceIn(minX, maxX)
                            val rawIndex = ((releaseX - firstX) / stepPx).roundToInt()
                            val targetIndex = rawIndex.coerceIn(0, items.lastIndex)
                            val targetX = firstX + targetIndex * stepPx
                            isDragging = false
                            onSelect(items[targetIndex].first)
                            scope.launch {
                                indicatorAnim.stop()
                                indicatorAnim.snapTo(releaseX)
                                indicatorAnim.animateTo(targetX, springSpec)
                            }
                        } else {
                            isDragging = false
                        }
                    }
                }
            }
    ) {
        // 任何时候都从实际指示块位置计算文本覆盖范围：
        // 拖动时实时跟手，点击时随弹簧动画移动，因此文字不会等松手才切换。
        val visualIndicatorX = if (isDragging) dragIndicatorX else indicatorAnim.value

        if (itemWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            visualIndicatorX.roundToInt(),
                            horizontalPaddingPx.roundToInt()
                        )
                    }
                    .width(with(density) { itemWidthPx.toDp() })
                    .height(40.dp)
                    .clip(G2Shapes.gridCard)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (mode, label) ->
                val itemStartX = firstX + index * stepPx
                val itemEndX = itemStartX + itemWidthPx
                val indicatorEndX = visualIndicatorX + itemWidthPx

                // 指示块覆盖到哪个字，哪个字的对应部分就变成白色+粗体。
                // 这样拖动时可以同时看到两个标签的“接力”变化，而不是整段文字跳变。
                val overlapStart = (visualIndicatorX.coerceAtLeast(itemStartX) - itemStartX)
                    .coerceIn(0f, itemWidthPx)
                val overlapEnd = (indicatorEndX.coerceAtMost(itemEndX) - itemStartX)
                    .coerceIn(0f, itemWidthPx)
                val hasOverlap = overlapEnd > overlapStart + 0.5f

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(G2Shapes.gridCard)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isDragging = false
                            val targetX = targetXFor(mode)
                            onSelect(mode)
                            scope.launch {
                                indicatorAnim.stop()
                                indicatorAnim.snapTo(indicatorAnim.value.coerceIn(minX, maxX))
                                indicatorAnim.animateTo(targetX, springSpec)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 基础文字
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 只绘制被当前指示块“覆盖”的部分，因此颜色和字重会随着滑动实时推进。
                    if (hasOverlap) {
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    drawContext.canvas.save()
                                    drawContext.canvas.clipRect(
                                        overlapStart,
                                        0f,
                                        overlapEnd,
                                        size.height
                                    )
                                    drawContent()
                                    drawContext.canvas.restore()
                                },
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
