package com.hydrogen.screentester

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class ColorTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 1. 设置全屏沉浸，隐藏状态栏和导航栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // 2. 侧滑返回退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        setContent {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

            // 屏幕测试色彩序列
            val testColors = listOf(
                Color.Red,      // 红色：测坏点/色彩偏离
                Color.Green,    // 绿色：测坏点/发光均匀度
                Color.Blue,     // 蓝色：测坏点/色阶
                Color.White,    // 白色：测暗斑/漏光/屏幕发黄发红
                Color.Black,    // 黑色：测亮点/漏光
                Color.Gray,     // 灰色：测屏幕均匀度/抹布屏
                Color.Yellow,
                Color.Cyan,
                Color.Magenta
            )

            var currentIndex by remember { mutableIntStateOf(0) }

            // 刚进入时提示一下怎么操作
            LaunchedEffect(Unit) {
                Toast.makeText(this@ColorTestActivity, "点击屏幕切换颜色", Toast.LENGTH_SHORT).show()
            }

            // 全屏色块
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(testColors[currentIndex])
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (currentIndex + 1 < testColors.size) {
                            currentIndex++
                        } else {
                            finish()
                        }
                    }
            )
        }
    }
}