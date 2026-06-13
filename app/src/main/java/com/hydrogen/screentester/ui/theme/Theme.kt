package com.hydrogen.screentester.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.hydrogen.screentester.DarkModeConfig
import com.hydrogen.screentester.ThemeSettings

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun ScreenTesterTheme(
    content: @Composable () -> Unit
) {
    // 1. 判断当前应该用深色还是浅色
    val darkTheme = when (ThemeSettings.darkModeState) {
        DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkModeConfig.LIGHT -> false
        DarkModeConfig.DARK -> true
    }

    // 2. 开启莫奈（Material You 动态取色）
    // 只要系统是 Android 12 (API 31) 及以上，就提取壁纸颜色
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}