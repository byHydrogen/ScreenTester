package com.hydrogen.screentester

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme
import kotlin.math.roundToInt

class TouchSamplingActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        setContent {
            ScreenTesterTheme {
                val monetPrimary = MaterialTheme.colorScheme.primary
                val monetContainer = MaterialTheme.colorScheme.primaryContainer
                val monetOnContainer = MaterialTheme.colorScheme.onPrimaryContainer

                var displayHz by remember { mutableIntStateOf(0) }
                var peakHz by remember { mutableIntStateOf(0) }

                val eventTimestamps = remember { mutableStateListOf<Long>() }
                val hzHistory = remember { mutableStateListOf<Int>() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                        .pointerInteropFilter { event ->
                            val eventTime = event.eventTime
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    eventTimestamps.clear()
                                    eventTimestamps.add(eventTime)
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    for (i in 0 until event.historySize) {
                                        eventTimestamps.add(event.getHistoricalEventTime(i))
                                    }
                                    eventTimestamps.add(eventTime)
                                    while (eventTimestamps.isNotEmpty() && eventTime - eventTimestamps.first() > 200) {
                                        eventTimestamps.removeAt(0)
                                    }
                                    if (eventTimestamps.size >= 2) {
                                        val duration = eventTimestamps.last() - eventTimestamps.first()
                                        val calculatedHz = if (duration > 0) ((eventTimestamps.size - 1).toFloat() / duration * 1000f).roundToInt() else 0
                                        displayHz = calculatedHz
                                        if (calculatedHz > peakHz) peakHz = calculatedHz
                                        if (hzHistory.isEmpty() || Math.abs(calculatedHz - (hzHistory.lastOrNull() ?: 0)) > 2) {
                                            hzHistory.add(calculatedHz)
                                            if (hzHistory.size > 100) hzHistory.removeAt(0)
                                        }
                                    }
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    displayHz = 0
                                    eventTimestamps.clear()
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    SamplingChart(hzHistory, monetPrimary, Modifier.fillMaxSize().alpha(0.4f))

                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("硬件采样率", color = Color.Gray, fontSize = 16.sp)
                        Text(
                            text = "$displayHz Hz",
                            color = Color.White,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black
                        )

                        Spacer(Modifier.height(40.dp))

                        Surface(
                            color = monetContainer,
                            shape = G2Shapes.button
                        ) {
                            Text(
                                text = "稳定峰值: $peakHz Hz",
                                color = monetOnContainer,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // 底部提示与免责声明
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "请快速划圈以触发硬件最高采样率",
                            color = Color.Gray.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "注：受限于系统底层事件分发机制与算法误差\n数据仅供参考娱乐，不代表真实硬件规格",
                            color = monetPrimary.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SamplingChart(history: List<Int>, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas
        val path = Path()
        val step = size.width / 100f
        val maxHzScale = 500f
        history.forEachIndexed { i, hz ->
            val x = i * step
            val y = size.height - (hz.toFloat() / maxHzScale * size.height * 0.4f) - (size.height * 0.3f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
    }
}