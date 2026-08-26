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
    BLUE_PINK,    // 蓝粉
    OCEAN         // 海洋
}

object ThemeSettings {
    var darkModeState by mutableStateOf(DarkModeConfig.FOLLOW_SYSTEM)
    var testLineColor by mutableIntStateOf(android.graphics.Color.WHITE)
    var isMaxBrightnessEnabled by mutableStateOf(false)
    var testBrightnessValue by mutableFloatStateOf(1.0f)

    // 测试页屏幕常亮开关
    var isKeepScreenOnEnabled by mutableStateOf(false)
    var userPresets by mutableStateOf<List<Int>>(emptyList())
    var useCustomRadius by mutableStateOf(false)
    var radiusTL by mutableFloatStateOf(-1f)
    var radiusTR by mutableFloatStateOf(-1f)
    var radiusBL by mutableFloatStateOf(-1f)
    var radiusBR by mutableFloatStateOf(-1f)

    // 线条粗细存储，默认 5.0 像素
    var testLineThickness by mutableFloatStateOf(5f)

    var isAnimationEnabled by mutableStateOf(true)

    // 设置页线条预览开关
    var isSettingsLinePreviewEnabled by mutableStateOf(true)

    // 精简黑边遮挡测试页文字开关状态
    var isCompactModeEnabled by mutableStateOf(false)

    // 黑边遮挡测试长按退出：开关 + 秒数（3-20，默认5）
    var longPressExitEnabled by mutableStateOf(true)
    var longPressExitSeconds by mutableIntStateOf(5)

    // 使用旧版曲线 (drawRoundRect)，关闭则使用 arcTo+center 精确圆弧
    var isLegacyCurveEnabled by mutableStateOf(false)

    // 是否能读取到屏幕圆角圆心数据（检测不到时自动强制旧版曲线）
    var isCenterDataAvailable by mutableStateOf(true)

    // 主页测试项视图模式：true=网格模式，false=列表模式
    var isGridView by mutableStateOf(false)

    // 渐变色条模式状态
    var isMultiColorMode by mutableStateOf(false)
    var multiColorSelectedColors by mutableStateOf<List<Int>>(emptyList()) // 勾选的颜色（最多8个）
    var multiColorSegmentLength by mutableFloatStateOf(0f) // 渐变颜色长度（0表示使用默认中间值）

    // 更新下载源
    var updateDownloadSource by mutableStateOf("gitee")

    // 自动检查更新开关（默认开启，关闭后只能手动检查）
    var autoCheckUpdateEnabled by mutableStateOf(true)

    fun saveUpdateSource(context: Context, source: String) {
        if (updateDownloadSource == source) return
        updateDownloadSource = source
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("update_source", source).apply()
        // 取消当前下载 + 清缓存
        GlobalUpdateState.downloadState.cancel(context)
        GlobalUpdateState.latestDownloadUrl = null
        // 立即用新源重新检查更新
        UpdateManager.checkUpdate(
            context, isManual = true,
            onResult = { has, ver, log, dlUrl ->
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (has && ver != null && UpdateManager.isVersionGreater(ver, pInfo.versionName ?: "")) {
                    GlobalUpdateState.hasNewVersion = true
                    GlobalUpdateState.latestVersionName = ver
                    GlobalUpdateState.latestChangelog = log ?: ""
                    GlobalUpdateState.latestDownloadUrl = dlUrl
                } else {
                    GlobalUpdateState.hasNewVersion = false
                }
            }
        )
    }

    fun saveConfig(context: Context, config: DarkModeConfig) {
        darkModeState = config
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("dark_mode", config.name).apply()
    }

    // 保存自动检查更新开关
    fun saveAutoCheckUpdate(context: Context, enabled: Boolean) {
        autoCheckUpdateEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("auto_check_update_enabled", enabled).apply()
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

    // 保存测试页屏幕常亮开关
    fun saveKeepScreenOn(context: Context, enabled: Boolean) {
        isKeepScreenOnEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("keep_screen_on_enabled", enabled).apply()
    }

    // 保存线条粗细
    fun saveLineThickness(context: Context, value: Float) {
        testLineThickness = value.coerceIn(1f, 15f)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putFloat("line_thickness", testLineThickness).apply()
    }

    fun saveAnimationConfig(context: Context, enabled: Boolean) {
        isAnimationEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("is_animation_enabled", enabled).apply()
    }

    fun saveSettingsLinePreviewConfig(context: Context, enabled: Boolean) {
        isSettingsLinePreviewEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("settings_line_preview", enabled).apply()
    }

    // 保存精简模式设置
    fun saveCompactModeConfig(context: Context, enabled: Boolean) {
        isCompactModeEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("is_compact_mode_enabled", enabled).apply()
    }

    // 保存黑边遮挡测试长按退出设置
    fun saveLongPressExitConfig(context: Context, enabled: Boolean) {
        longPressExitEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("long_press_exit_enabled", enabled).apply()
    }

    fun saveLongPressExitSeconds(context: Context, seconds: Int) {
        longPressExitSeconds = seconds.coerceIn(3, 20)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("long_press_exit_seconds", seconds.coerceIn(3, 20)).apply()
    }

    // 保存旧版曲线设置
    fun saveLegacyCurveConfig(context: Context, enabled: Boolean) {
        isLegacyCurveEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("is_legacy_curve_enabled", enabled).apply()
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

    // 存储用户自定义的渐变方案
    var customGradientSchemes by mutableStateOf<List<Pair<String, List<Int>>>>(emptyList())

    // 保存自定义渐变方案
    fun saveCustomGradientSchemes(context: Context, schemes: List<Pair<String, List<Int>>>) {
        customGradientSchemes = schemes
        val str = schemes.joinToString("|") { "${it.first}:${it.second.joinToString(",")}" }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_gradient_schemes", str)
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
            PresetScheme.BLUE_PINK -> listOf(
                0xFF72A7FF.toInt(), // 蓝
                0xFFFF83B6.toInt(), // 粉
            )
            PresetScheme.OCEAN -> listOf(
                0xFF008BFF.toInt(), // 蓝
                0xFF008B9E.toInt(), // 青
                0xFF00779D.toInt(), // 深蓝
            )
        }

        // 设置勾选状态（最多8个）
        multiColorSelectedColors = colors.take(8)
        saveMultiColorSelectedColors(context, multiColorSelectedColors)

        // 重置渐变颜色长度为默认值
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

        // 读取黑边遮挡测试长按退出设置
        longPressExitEnabled = prefs.getBoolean("long_press_exit_enabled", true)
        longPressExitSeconds = prefs.getInt("long_press_exit_seconds", 5).coerceIn(3, 20)

        // 读取旧版曲线设置
        isLegacyCurveEnabled = prefs.getBoolean("is_legacy_curve_enabled", false)

        // 读取圆心数据检测结果（首次检测前不存在该 key，检测后保存）
        if (prefs.contains("is_center_data_available")) {
            isCenterDataAvailable = prefs.getBoolean("is_center_data_available", true)
            if (!isCenterDataAvailable && !isLegacyCurveEnabled) {
                isLegacyCurveEnabled = true
            }
        }

        // 读取视图模式设置
        isGridView = prefs.getBoolean("is_grid_view", false)

        // 读取设置页线条预览开关
        isSettingsLinePreviewEnabled = prefs.getBoolean("settings_line_preview", true)

        // 读取渐变色条模式设置
        isMultiColorMode = prefs.getBoolean("is_multi_color_mode", false)
        val selectedStr = prefs.getString("multi_color_selected", null)
        if (selectedStr != null && selectedStr.isNotEmpty()) {
            multiColorSelectedColors = selectedStr.split(",").mapNotNull { it.toIntOrNull() }
        }

        // 读取渐变颜色长度
        multiColorSegmentLength = prefs.getFloat("multi_color_segment_length", 0f)

        // 读取更新下载源
        updateDownloadSource = prefs.getString("update_source", "gitee") ?: "gitee"

        // 读取自动检查更新开关
        autoCheckUpdateEnabled = prefs.getBoolean("auto_check_update_enabled", true)

        // 读取外观设置
        val savedDark = prefs.getString("dark_mode", DarkModeConfig.FOLLOW_SYSTEM.name)
        darkModeState = try { DarkModeConfig.valueOf(savedDark ?: DarkModeConfig.FOLLOW_SYSTEM.name) } catch (e: Exception) { DarkModeConfig.FOLLOW_SYSTEM }

        // 读取线条颜色
        testLineColor = prefs.getInt("line_color", android.graphics.Color.WHITE)

        val customSchemesStr = prefs.getString("custom_gradient_schemes", "") ?: ""
        if (customSchemesStr.isNotEmpty()) {
            customGradientSchemes = customSchemesStr.split("|").mapNotNull { s ->
                try {
                    if (s.contains(":")) {
                        val parts = s.split(":")
                        val name = parts[0]
                        val colors = parts[1].split(",").mapNotNull { it.toIntOrNull() }
                        if (colors.isNotEmpty()) name to colors else null
                    } else {
                        val colors = s.split(",").mapNotNull { it.toIntOrNull() }
                        if (colors.isNotEmpty()) "自定义" to colors else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        // 读取亮度设置
        isMaxBrightnessEnabled = prefs.getBoolean("max_brightness", false)
        testBrightnessValue = prefs.getFloat("test_brightness_val", 1.0f)

        // 读取测试页屏幕常亮开关
        isKeepScreenOnEnabled = prefs.getBoolean("keep_screen_on_enabled", false)

        // 读取线条粗细设置
        testLineThickness = prefs.getFloat("line_thickness", 5f).coerceIn(1f, 15f)

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

    // 检测屏幕圆角圆心数据是否可用
    fun checkCenterDataAvailability(view: android.view.View) {
        val context = view.context
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.contains("is_center_data_available")) return  // 已检测过，跳过

        val insets = view.rootWindowInsets ?: return
        val tl = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
        val tr = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)
        val br = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)
        val bl = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)
        val available = tl?.center != null && tr?.center != null && br?.center != null && bl?.center != null
        isCenterDataAvailable = available
        prefs.edit().putBoolean("is_center_data_available", available).apply()
        if (!available && !isLegacyCurveEnabled) {
            isLegacyCurveEnabled = true
        }
    }

    // 硬件性能检测算法
    private fun checkDevicePerformance(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager

        // 1. 低内存设备（Low RAM），默认关闭动画
        if (am.isLowRamDevice) return false

        // 2. 利用 Performance Class 辨别性能层级（minSdk=31，无需版本判断）
        //    若设备不支持 Performance Class，则用 CPU 核心数 and 内存兜底判断
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