package com.hydrogen.screentester

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class GrayscaleTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 全屏沉浸
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // 2. 侧滑返回退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        setContent {
            val view = LocalView.current

            val stages = listOf(
                "8 级灰阶 (高对比)",
                "16 级灰阶 (基础测试)",
                "32 级灰阶 (标准测试)",
                "64 级灰阶 (断层测试)",
                "256 级极限灰阶 (专业检测)",
                "平滑灰阶 (垂直渐变)",
                "平滑灰阶 (水平渐变)"
            )

            var step by remember { mutableIntStateOf(0) }
            var showHint by remember { mutableStateOf(true) }

            LaunchedEffect(step) {
                showHint = true
                delay(2200)
                showHint = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (step < stages.size - 1) step++ else finish()
                    }
            ) {
                // 绘制内容
                when (step) {
                    0 -> SeamlessGrayscaleCanvas(levels = 8)
                    1 -> SeamlessGrayscaleCanvas(levels = 16)
                    2 -> SeamlessGrayscaleCanvas(levels = 32)
                    3 -> SeamlessGrayscaleCanvas(levels = 64)
                    4 -> SeamlessGrayscaleCanvas(levels = 256)
                    5 -> SmoothGradientCanvas(vertical = true)
                    6 -> SmoothGradientCanvas(vertical = false)
                }

                // 悬浮提示胶囊
                AnimatedVisibility(
                    visible = showHint,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(25.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, null, tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${step + 1}/${stages.size}  ${stages[step]}",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// 绘制阶梯灰阶（物理对齐算法）
@Composable
fun SeamlessGrayscaleCanvas(levels: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenHeight = size.height
        val screenWidth = size.width

        for (i in 0 until levels) {
            // 计算灰度值
            val colorValue = i / (levels - 1).toFloat()

            // 计算当前块的起始像素和结束像素
            // top 是当前索引占总高度的比例
            // bottom 是下一索引占总高度的比例
            val top = (i * screenHeight / levels)
            val bottom = ((i + 1) * screenHeight / levels)

            drawRect(
                color = Color(colorValue, colorValue, colorValue),
                topLeft = Offset(0f, top),
                size = Size(screenWidth, bottom - top + 1f) // +1f 轻微重叠，消灭空隙
            )
        }
    }
}

// 绘制平滑渐变
@Composable
fun SmoothGradientCanvas(vertical: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val brush = if (vertical) {
            Brush.verticalGradient(listOf(Color.Black, Color.White))
        } else {
            Brush.horizontalGradient(listOf(Color.Black, Color.White))
        }
        drawRect(brush = brush)
    }
}