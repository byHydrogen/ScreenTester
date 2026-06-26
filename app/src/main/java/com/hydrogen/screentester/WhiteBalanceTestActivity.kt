package com.hydrogen.screentester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class WhiteBalanceTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 1. 设置全屏沉浸，彻底隐藏状态栏和导航栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 2. 亮度逻辑
        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        setContent {
            // 生成 16 个色值，步进为 0x10 (16进制的10，即10进制的16)
            // 结果为: #000000, #101010, #202020 ... #F0F0F0
            val colorList = (0..15).map { i ->
                val value = i * 16
                String.format("#%02X%02X%02X", value, value, value)
            }

            // 使用 Column 填满全屏，内部平分高度
            Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // 每行 2 个，16 个色值共 8 行
                for (row in 0 until 8) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // 左侧块
                        GrayBlock(colorList[row * 2], modifier = Modifier.weight(1f))
                        // 右侧块
                        GrayBlock(colorList[row * 2 + 1], modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun GrayBlock(hexCode: String, modifier: Modifier = Modifier) {
    // 解析颜色
    val bgColor = Color(android.graphics.Color.parseColor(hexCode))

    // 自动计算文字对比色
    val isLight = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) > 0.5f
    val textColor = if (isLight) Color.Black else Color.White

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(bgColor)
            .padding(10.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = hexCode,
            color = textColor.copy(alpha = 0.9f),
            fontSize = 14.sp, // 文字调小一点，确保在小屏上也不拥挤
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}