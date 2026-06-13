package com.hydrogen.screentester

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class TouchTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸逻辑
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        // 亮度设置逻辑
        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        setContent {
            // 使用ScreenTesterTheme
            ScreenTesterTheme {
                val view = LocalView.current

                // 自动获取莫奈色
                val monetPrimaryColor = MaterialTheme.colorScheme.primary
                val trailColor = MaterialTheme.colorScheme.onPrimaryContainer

                val rows = 15
                val cols = 8
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

                                val blockW = size.width / cols
                                val blockH = size.height / rows
                                val c = (pos.x / blockW).toInt().coerceIn(0, cols - 1)
                                val r = (pos.y / blockH).toInt().coerceIn(0, rows - 1)
                                val index = r * cols + c

                                if (index !in touchedBlocks) {
                                    touchedBlocks.add(index)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        )
                    }
                ) {
                    val blockW = size.width / cols
                    val blockH = size.height / rows

                    // 1. 画格子
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val index = r * cols + c
                            drawRect(
                                color = if (index in touchedBlocks) monetPrimaryColor else Color.Gray.copy(alpha = 0.2f),
                                topLeft = Offset(c * blockW, r * blockH),
                                size = Size(blockW - 2f, blockH - 2f)
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