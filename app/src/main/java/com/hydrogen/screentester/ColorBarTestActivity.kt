package com.hydrogen.screentester

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class ColorBarTestActivity : ComponentActivity() {
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
            val view = LocalView.current
            val stages = listOf(
                "EBU 100% (标准 8 色)",
                "EBU 75% (标准 75% 饱和度)",
                "SMPTE SD (经典复合信号)",
                "SMPTE HD (高对比度信号)",
                "ARIB STD-B28 (专业 4 层信号)",
                "RGBYCM 垂直阶梯 (色彩平衡)",
                "六色纯度网格 (均匀度测试)",
                "垂直色块阵列 (响应速度参考)"
            )
            var step by remember { mutableIntStateOf(0) }
            var showHint by remember { mutableStateOf(true) }

            LaunchedEffect(step) {
                showHint = true
                delay(2500)
                showHint = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (step < stages.size - 1) step++ else finish()
                    }
            ) {
                // 根据当前步骤渲染对应的复杂组件
                when (step) {
                    0 -> ColorBarEBU(multiplier = 1.0f)
                    1 -> ColorBarEBU(multiplier = 0.75f)
                    2 -> ColorBarSMPTE(isHD = false)
                    3 -> ColorBarSMPTE(isHD = true)
                    4 -> ColorBarARIB()
                    5 -> ColorScaleStep()
                    6 -> ColorGrid()
                    7 -> VerticalColorArray()
                }

                // 悬浮提示胶囊
                AnimatedVisibility(visible = showHint, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.9f), G2Shapes.indicator).padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = "${step + 1}/${stages.size}  ${stages[step]}", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 1 & 2: EBU 标准垂直彩条
@Composable
fun ColorBarEBU(multiplier: Float) {
    val colors = listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta, Color.Red, Color.Blue, Color.Black)
    Row(Modifier.fillMaxSize()) {
        colors.forEach { baseColor ->
            Box(Modifier.weight(1f).fillMaxHeight().background(
                Color(baseColor.red * multiplier, baseColor.green * multiplier, baseColor.blue * multiplier)
            ))
        }
    }
}

// 3 & 4: SMPTE 复合信号
@Composable
fun ColorBarSMPTE(isHD: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(0.66f)) {
            val bars = if(isHD) listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta, Color.Red, Color.Blue)
            else listOf(Color.LightGray, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta, Color.Red, Color.Blue)
            bars.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
        }
        Row(Modifier.fillMaxWidth().weight(0.08f)) {
            val bars = listOf(Color.Blue, Color.Black, Color.Magenta, Color.Black, Color.Cyan, Color.Black, Color.White)
            bars.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
        }
        Row(Modifier.fillMaxWidth().weight(0.26f)) {
            Box(Modifier.weight(1.5f).fillMaxHeight().background(Color(0xFF002147)))
            Box(Modifier.weight(1.5f).fillMaxHeight().background(Color.White))
            Box(Modifier.weight(1.5f).fillMaxHeight().background(Color(0xFF2E0854)))
            Box(Modifier.weight(2.5f).fillMaxHeight().background(Color.Black))
        }
    }
}

// 5: ARIB STD-B28
@Composable
fun ColorBarARIB() {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(0.5f)) {
            listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta, Color.Red, Color.Blue).forEach {
                Box(Modifier.weight(1f).fillMaxHeight().background(it))
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.15f)) {
            listOf(Color.Blue, Color.Black, Color.Magenta, Color.Black, Color.Cyan, Color.Black, Color.White).forEach {
                Box(Modifier.weight(1f).fillMaxHeight().background(it))
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.15f)) {
            listOf(Color(0xFF002147), Color.White, Color(0xFF2E0854), Color.Black).forEach {
                Box(Modifier.weight(1f).fillMaxHeight().background(it))
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.2f)) {
            val grayScale = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f)
            grayScale.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(Color(it, it, it))) }
        }
    }
}

// 6: RGBYCM 垂直色阶
@Composable
fun ColorScaleStep() {
    Column(Modifier.fillMaxSize()) {
        val groups = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta)
        groups.forEach { base ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (i in 0..5) {
                    val factor = (i + 1) / 6f
                    Box(Modifier.weight(1f).fillMaxHeight().background(
                        Color(base.red * factor, base.green * factor, base.blue * factor)
                    ))
                }
            }
        }
    }
}

// 7: 六色纯度网格
@Composable
fun ColorGrid() {
    Column(Modifier.fillMaxSize()) {
        val rows = listOf(
            listOf(Color.Red, Color.Green, Color.Blue),
            listOf(Color.Yellow, Color.Cyan, Color.Magenta)
        )
        rows.forEach { rowItems ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                rowItems.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
            }
        }
    }
}

// 8: 垂直色块阵列
@Composable
fun VerticalColorArray() {
    Row(Modifier.fillMaxSize()) {
        val columns = 12
        for (i in 0 until columns) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val color = if (i % 2 == 0) Color.DarkGray else Color.Gray
                for (j in 0 until 12) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(if ((i + j) % 2 == 0) Color.White else Color.Black))
                }
            }
        }
    }
}