package com.hydrogen.screentester

import android.app.Activity
import android.view.WindowManager

/**
 * 统一应用测试页窗口设置：
 * - 自定义测试亮度
 * - 测试页屏幕常亮（FLAG_KEEP_SCREEN_ON 随页面销毁自动失效）
 */
fun Activity.applyTestWindowSettings() {
    if (ThemeSettings.isMaxBrightnessEnabled) {
        val lp = window.attributes
        lp.screenBrightness = ThemeSettings.testBrightnessValue
        window.attributes = lp
    }
    if (ThemeSettings.isKeepScreenOnEnabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
