package com.hydrogen.screentester

import android.app.Activity
import android.view.WindowManager

/**
 * 统一应用测试页窗口设置：
 * - 自定义测试亮度
 * - 测试页屏幕常亮（FLAG_KEEP_SCREEN_ON 随页面销毁自动失效）
 */
fun Activity.applyTestWindowSettings() {
    // 渲染进摄像头挖孔区域，避免全屏时挖孔处出现黑条（等效系统"刘海屏：自动匹配"）
    window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    if (ThemeSettings.isMaxBrightnessEnabled) {
        val lp = window.attributes
        lp.screenBrightness = ThemeSettings.testBrightnessValue
        window.attributes = lp
    }
    if (ThemeSettings.isKeepScreenOnEnabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
