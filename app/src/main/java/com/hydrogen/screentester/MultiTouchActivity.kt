package com.hydrogen.screentester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.MaterialTheme
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme

class MultiTouchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        setContent {
            ScreenTesterTheme {
                var touchPoints by remember { mutableStateOf(mapOf<Long, androidx.compose.ui.geometry.Offset>()) }
                var maxPointers by remember { mutableStateOf(0) }
                val monetPrimary = MaterialTheme.colorScheme.primary

                // 获取当前的屏幕密度信息
                val density = LocalDensity.current

                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val newPoints = event.changes
                                    .filter { it.pressed }
                                    .associate { it.id.value to it.position }
                                touchPoints = newPoints
                                if (newPoints.size > maxPointers) maxPointers = newPoints.size
                            }
                        }
                    }
                ) {
                    // 触控圆圈绘制逻辑
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        touchPoints.forEach { (_, pos) ->
                            // 将绝对像素(120f)改为 dp.toPx()，保证所有手机上圆圈物理大小一致
                            drawCircle(color = monetPrimary.copy(alpha = 0.4f), radius = 45.dp.toPx(), center = pos)
                            drawCircle(color = monetPrimary, radius = 5.dp.toPx(), center = pos)
                        }
                    }

                    // 字号的文字显示部分
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "当前触控点: ${touchPoints.size}",
                            color = Color.White,
                            // 使用 dp 强制转为 sp，无视系统字体放大缩小，锁定布局
                            fontSize = with(density) { 22.dp.toSp() }
                        )
                        Text(
                            text = "支持最大触控点: $maxPointers",
                            color = monetPrimary,
                            fontSize = with(density) { 34.dp.toSp() },
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            lineHeight = with(density) { 44.dp.toSp() }
                        )
                    }
                }
            }
        }
    }
}