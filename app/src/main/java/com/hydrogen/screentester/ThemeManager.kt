package com.hydrogen.screentester

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DarkModeConfig { FOLLOW_SYSTEM, LIGHT, DARK }

// 预设方案枚举
enum class PresetScheme {
    RAINBOW,      // 彩虹色
    WARM,         // 暖色
    COOL,         // 冷色
    HIGH_CONTRAST, // 高对比
    MONET         // 莫奈色
}

object ThemeSettings {
    var darkModeState by mutableStateOf(DarkModeConfig.FOLLOW_SYSTEM)
    var testLineColor by mutableIntStateOf(android.graphics.Color.WHITE)
    var isMaxBrightnessEnabled by mutableStateOf(false)
    var testBrightnessValue by mutableFloatStateOf(1.0f)
    var userPresets by mutableStateOf<List<Int>>(emptyList())
    var useCustomRadius by mutableStateOf(false)
    var radiusTL by mutableFloatStateOf(-1f)
    var radiusTR by mutableFloatStateOf(-1f)
    var radiusBL by mutableFloatStateOf(-1f)
    var radiusBR by mutableFloatStateOf(-1f)

    // 线条粗细存储，默认 5.0 像素
    var testLineThickness by mutableFloatStateOf(5f)

    var isAnimationEnabled by mutableStateOf(true)

    // 精简黑边遮挡测试页文字开关状态
    var isCompactModeEnabled by mutableStateOf(false)

    // 主页测试项视图模式：true=网格模式，false=列表模式
    var isGridView by mutableStateOf(false)

    // 渐变色条模式状态
    var isMultiColorMode by mutableStateOf(false)
    var multiColorSelectedColors by mutableStateOf<List<Int>>(emptyList()) // 勾选的颜色（最多8个）
    var multiColorSegmentLength by mutableFloatStateOf(0f) // 渐变颜色长度（0表示使用默认中间值）

    fun saveConfig(context: Context, config: DarkModeConfig) {
        darkModeState = config
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("dark_mode", config.name).apply()
    }

    fun saveLineColor(context: Context, color: Int) {
        testLineColor = color
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("line_color", color).apply()
    }

    fun saveMaxBrightness(context: Context, enabled: Boolean) {
        isMaxBrightnessEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("max_brightness", enabled).apply()
    }

    fun saveTestBrightnessValue(context: Context, value: Float) {
        testBrightnessValue = value
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putFloat("test_brightness_val", value).apply()
    }

    // 保存线条粗细
    fun saveLineThickness(context: Context, value: Float) {
        testLineThickness = value
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putFloat("line_thickness", value).apply()
    }

    fun saveAnimationConfig(context: Context, enabled: Boolean) {
        isAnimationEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("is_animation_enabled", enabled).apply()
    }

    // 保存精简模式设置
    fun saveCompactModeConfig(context: Context, enabled: Boolean) {
        isCompactModeEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("is_compact_mode_enabled", enabled).apply()
    }

    // 保存视图模式设置
    fun saveGridViewConfig(context: Context, enabled: Boolean) {
        isGridView = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("is_grid_view", enabled).apply()
    }

    // 保存渐变色条模式开关
    fun saveMultiColorMode(context: Context, enabled: Boolean) {
        isMultiColorMode = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("is_multi_color_mode", enabled).apply()
    }

    // 保存渐变色条模式勾选的颜色
    fun saveMultiColorSelectedColors(context: Context, colors: List<Int>) {
        multiColorSelectedColors = colors
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("multi_color_selected", colors.joinToString(","))
            .apply()
    }

    // 保存渐变颜色长度
    fun saveMultiColorSegmentLength(context: Context, length: Float) {
        multiColorSegmentLength = length
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putFloat("multi_color_segment_length", length)
            .apply()
    }

    // 应用预设方案
    fun applyPresetScheme(context: Context, scheme: PresetScheme) {
        val colors = when (scheme) {
            PresetScheme.RAINBOW -> listOf(
                android.graphics.Color.RED,
                android.graphics.Color.rgb(255, 165, 0), // 橙
                android.graphics.Color.YELLOW,
                android.graphics.Color.GREEN,
                android.graphics.Color.BLUE,
                android.graphics.Color.rgb(75, 0, 130), // 靛
                android.graphics.Color.rgb(139, 0, 255)  // 紫
            )
            PresetScheme.WARM -> listOf(
                android.graphics.Color.RED,
                android.graphics.Color.rgb(255, 165, 0), // 橙
                android.graphics.Color.YELLOW
            )
            PresetScheme.COOL -> listOf(
                android.graphics.Color.BLUE,
                android.graphics.Color.GREEN,
                android.graphics.Color.rgb(139, 0, 255)  // 紫
            )
            PresetScheme.HIGH_CONTRAST -> listOf(
                android.graphics.Color.RED,
                android.graphics.Color.GREEN,
                android.graphics.Color.BLUE
            )
            PresetScheme.MONET -> listOf(
                -7981735, // Primary
                -1845525, // Secondary
                -1254181, // PrimaryContainer
                -1845525, // TertiaryContainer
                -5431481  // Error
            )
        }

        // 设置勾选状态（最多8个）
        multiColorSelectedColors = colors.take(8)
        saveMultiColorSelectedColors(context, multiColorSelectedColors)

        // 重置渐变颜色长度为默认值（0表示使用默认中间值）
        multiColorSegmentLength = 0f
        saveMultiColorSegmentLength(context, 0f)
    }

    fun addUserPreset(context: Context, color: Int) {
        if (!userPresets.contains(color)) {
            userPresets = userPresets + color
            savePresetsToLocal(context)
        }
    }

    fun removeUserPreset(context: Context, color: Int) {
        userPresets = userPresets - color
        savePresetsToLocal(context)
    }

    private fun savePresetsToLocal(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("user_presets", userPresets.joinToString(",")).apply()
    }

    fun saveCustomRadius(context: Context, enabled: Boolean, tl: Float, tr: Float, bl: Float, br: Float) {
        useCustomRadius = enabled
        radiusTL = tl; radiusTR = tr; radiusBL = bl; radiusBR = br
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
        prefs.putBoolean("use_custom_radius", enabled)
        prefs.putFloat("r_tl", tl)
        prefs.putFloat("r_tr", tr)
        prefs.putFloat("r_bl", bl)
        prefs.putFloat("r_br", br)
        prefs.apply()
    }

    // App每次启动时读取所有设置
    fun loadConfig(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        // 读取动效高级设置
        if (!prefs.contains("is_animation_enabled")) {
            val defaultEnabled = checkDevicePerformance(context)
            isAnimationEnabled = defaultEnabled
            prefs.edit().putBoolean("is_animation_enabled", defaultEnabled).apply()
        } else {
            isAnimationEnabled = prefs.getBoolean("is_animation_enabled", true)
        }

        // 读取精简黑边遮挡测试页文字设置
        isCompactModeEnabled = prefs.getBoolean("is_compact_mode_enabled", false)

        // 读取视图模式设置
        isGridView = prefs.getBoolean("is_grid_view", false)

        // 读取渐变色条模式设置
        isMultiColorMode = prefs.getBoolean("is_multi_color_mode", false)
        val selectedStr = prefs.getString("multi_color_selected", null)
        if (selectedStr != null && selectedStr.isNotEmpty()) {
            multiColorSelectedColors = selectedStr.split(",").mapNotNull { it.toIntOrNull() }
        }

        // 读取渐变颜色长度（0表示使用默认中间值）
        multiColorSegmentLength = prefs.getFloat("multi_color_segment_length", 0f)

        // 读取外观设置
        val savedDark = prefs.getString("dark_mode", DarkModeConfig.FOLLOW_SYSTEM.name)
        darkModeState = try { DarkModeConfig.valueOf(savedDark ?: DarkModeConfig.FOLLOW_SYSTEM.name) } catch (e: Exception) { DarkModeConfig.FOLLOW_SYSTEM }

        // 读取线条颜色
        testLineColor = prefs.getInt("line_color", android.graphics.Color.WHITE)

        // 读取亮度设置
        isMaxBrightnessEnabled = prefs.getBoolean("max_brightness", false)
        testBrightnessValue = prefs.getFloat("test_brightness_val", 1.0f)

        // 读取线条粗细设置
        testLineThickness = prefs.getFloat("line_thickness", 5f)

        // 每次打开 App 自动恢复上次调好的圆角数据
        useCustomRadius = prefs.getBoolean("use_custom_radius", false)
        radiusTL = prefs.getFloat("r_tl", -1f)
        radiusTR = prefs.getFloat("r_tr", -1f)
        radiusBL = prefs.getFloat("r_bl", -1f)
        radiusBR = prefs.getFloat("r_br", -1f)

        // 读取预设列表 (如果为空则初始化默认 6 色)
        val presetStr = prefs.getString("user_presets", null)
        if (presetStr == null) {
            userPresets = listOf(
                -1, // 白色
                -9263105, // 莫奈蓝
                -1845525, // TertiaryContainer
                -1254181, // PrimaryContainer
                -7981735, // Primary
                -5431481  // Error 红
            )
            savePresetsToLocal(context)
        } else if (presetStr.isNotEmpty()) {
            userPresets = presetStr.split(",").mapNotNull { it.toIntOrNull() }
        }
    }

    // 硬件性能检测算法
    private fun checkDevicePerformance(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager

        // 1. 低内存设备（Low RAM），默认关闭动画
        if (am.isLowRamDevice) return false

        // 2. 利用 Performance Class 辨别性能层级（minSdk=31，无需版本判断）
        //    若设备不支持 Performance Class，则用 CPU 核心数和内存兜底判断
        if (android.os.Build.VERSION.MEDIA_PERFORMANCE_CLASS < android.os.Build.VERSION_CODES.S) {
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val totalRamGb = info.totalMem / (1024 * 1024 * 1024f)
            if (Runtime.getRuntime().availableProcessors() < 4 || totalRamGb < 4f) {
                return false
            }
        }
        return true
    }
}