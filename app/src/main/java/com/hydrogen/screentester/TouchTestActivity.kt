package com.hydrogen.screentester

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme

/**
 * 触摸格子的几何：**短边 8 格、长边 15 格** ——
 * 竖屏即原来的 8×15；横屏自动变成 15×8（相当于把竖屏的网格跟着屏幕转置）。
 * 于是两个方向的"格子相对屏幕的比例"一致，且都铺满整屏、不留白边。
 */
private data class TouchGrid(val cols: Int, val rows: Int, val blockW: Float, val blockH: Float)

private fun touchGridGeometry(w: Float, h: Float): TouchGrid {
    val cols: Int
    val rows: Int
    if (w <= h) {
        cols = 8
        rows = 15
    } else {
        cols = 15
        rows = 8
    }
    return TouchGrid(cols = cols, rows = rows, blockW = w / cols, blockH = h / rows)
}

class TouchTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 全屏沉浸逻辑
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        // 测试窗口设置
        applyTestWindowSettings()

        setContent {
            // 使用ScreenTesterTheme
            ScreenTesterTheme {
                val view = LocalView.current

                // 自动获取莫奈色
                val monetPrimaryColor = MaterialTheme.colorScheme.primary
                val trailColor = MaterialTheme.colorScheme.onPrimaryContainer

                val touchedBlocks = remember { mutableStateListOf<Int>() }
                val gesturePoints = remember { mutableStateListOf<Offset?>() }

                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { gesturePoints.add(it) },
                            onDragEnd = { gesturePoints.add(null) },
                            onDragCancel = { gesturePoints.add(null) },
                            onDrag = { change, _ ->
                                val pos = change.position
                                gesturePoints.add(pos)

                                val g = touchGridGeometry(size.width.toFloat(), size.height.toFloat())
                                val c = (pos.x / g.blockW).toInt().coerceIn(0, g.cols - 1)
                                val r = (pos.y / g.blockH).toInt().coerceIn(0, g.rows - 1)
                                val index = r * g.cols + c

                                if (index !in touchedBlocks) {
                                    touchedBlocks.add(index)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        )
                    }
                ) {
                    val g = touchGridGeometry(size.width, size.height)

                    // 1. 画格子（铺满整屏）
                    for (r in 0 until g.rows) {
                        for (c in 0 until g.cols) {
                            val index = r * g.cols + c
                            drawRect(
                                color = if (index in touchedBlocks) monetPrimaryColor else Color.Gray.copy(alpha = 0.2f),
                                topLeft = Offset(c * g.blockW, r * g.blockH),
                                size = Size(g.blockW - 2f, g.blockH - 2f)
                            )
                        }
                    }

                    // 2. 画轨迹
                    val strokePath = Path()
                    var firstPoint = true
                    gesturePoints.forEach { point ->
                        if (point == null) { firstPoint = true }
                        else {
                            if (firstPoint) {
                                strokePath.moveTo(point.x, point.y)
                                firstPoint = false
                            } else {
                                strokePath.lineTo(point.x, point.y)
                            }
                        }
                    }
                    drawPath(strokePath, trailColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}