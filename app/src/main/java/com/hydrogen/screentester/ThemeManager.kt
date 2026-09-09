package com.hydrogen.screentester

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DarkModeConfig { FOLLOW_SYSTEM, LIGHT, DARK }

// 设备性能分级（仅用于各动效的首次默认值；用户手动开关后以手动设置为准），由轻到重三档：
// LOW  —— 低内存设备 / 内存 < 4GB：重动效全部默认关
// MID  —— 内存 ≥ 4GB：开动态混色（单 pass 背景重绘，开销最小）
// HIGH —— 内存 ≥ 8GB 或性能分级 ≥ S：再加底栏模糊（常驻实时模糊）
enum class PerfTier { LOW, MID, HIGH }

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
    // 线条测试模式的自定义背景色（默认黑，与旧行为一致）
    var testLineBgColor by mutableIntStateOf(android.graphics.Color.BLACK)
    // 线条测试模式的文字颜色（默认白）
    var testLineTextColor by mutableIntStateOf(android.graphics.Color.WHITE)
    // 全局：所有文字是否跟随线条颜色（默认 false —— 默认使用自定义字体色）
    var textFollowsLine by mutableStateOf(false)
    // "黑边遮挡测试"那一行单独控制：true = 跟随字体色，false = 跟随线条色
    var titleFollowsText by mutableStateOf(false)
    // 精度模式是否也使用自定义背景色（默认 false）
    var precisionUsesCustomBg by mutableStateOf(false)
    // 测试页"机型"文字不透明度（%）：100 = 原始，范围 40~120
    var deviceNameOpacityPct by mutableIntStateOf(100)
    var isMaxBrightnessEnabled by mutableStateOf(false)
    var testBrightnessValue by mutableFloatStateOf(1.0f)

    // 测试页屏幕常亮开关
    var isKeepScreenOnEnabled by mutableStateOf(false)

    // 主页底栏模糊开关（默认关闭；开启后主页底栏背景实时模糊显示下方内容）
    var navBarBlurEnabled by mutableStateOf(false)

    // 底栏模糊强度（blurRadius，6-32dp，默认 20）
    var navBarBlurRadius by mutableFloatStateOf(20f)

    // 关于页卡片毛玻璃开关（默认关闭；开启后关于页卡片背景实时模糊）
    var aboutCardBlurEnabled by mutableStateOf(false)

    // 关于页动态混色开关（默认关闭；开启后关于页背景色缓慢流动混合，关闭保持静态渐变）
    var aboutDynamicMixEnabled by mutableStateOf(false)

    // 顶栏渐变模糊开关（默认关闭；开启后主页/设置页顶栏使用渐进模糊呈现）
    var topBarGradientBlur by mutableStateOf(false)
    var userPresets by mutableStateOf<List<Int>>(emptyList())
    // 背景色卡片的独立用户预设（与线条色分开）
    var bgUserPresets by mutableStateOf<List<Int>>(emptyList())
    var useCustomRadius by mutableStateOf(false)
    var radiusTL by mutableFloatStateOf(-1f)
    var radiusTR by mutableFloatStateOf(-1f)
    var radiusBL by mutableFloatStateOf(-1f)
    var radiusBR by mutableFloatStateOf(-1f)

    // 圆角校准页：四角 X/Y 曲率修正值。继续使用原有 SharedPreferences key，兼容已有用户数据。
    var radiusTLX by mutableFloatStateOf(0f)
    var radiusTRX by mutableFloatStateOf(0f)
    var radiusBLX by mutableFloatStateOf(0f)
    var radiusBRX by mutableFloatStateOf(0f)
    var radiusTLY by mutableFloatStateOf(0f)
    var radiusTRY by mutableFloatStateOf(0f)
    var radiusBLY by mutableFloatStateOf(0f)
    var radiusBRY by mutableFloatStateOf(0f)

    // 圆角校准页：是否记忆上次使用的拖拽调整模式
    var isDragAdjustModeEnabled by mutableStateOf(false)
    var isCalibrationLinked by mutableStateOf(true)

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
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putString("update_source", source) }
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
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putString("dark_mode", config.name) }
    }

    // 保存自动检查更新开关
    fun saveAutoCheckUpdate(context: Context, enabled: Boolean) {
        autoCheckUpdateEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("auto_check_update_enabled", enabled) }
    }

    fun saveLineColor(context: Context, color: Int) {
        testLineColor = color
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putInt("line_color", color) }
    }

    // 保存线条测试模式的自定义背景色
    fun saveLineBgColor(context: Context, color: Int) {
        testLineBgColor = color
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putInt("test_line_bg_color", color) }
    }

    // 保存线条测试模式的自定义文字颜色
    fun saveLineTextColor(context: Context, color: Int) {
        testLineTextColor = color
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putInt("test_line_text_color", color) }
    }

    // 全局：所有文字是否跟随线条颜色
    fun saveTextFollowsLine(context: Context, enabled: Boolean) {
        textFollowsLine = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putBoolean("test_text_follows_line", enabled) }
    }

    // "黑边遮挡测试"那行字单独控制：true = 跟随字体色
    fun saveTitleFollowsText(context: Context, enabled: Boolean) {
        titleFollowsText = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putBoolean("title_follows_text", enabled) }
    }

    // 精度模式是否也使用自定义背景色
    fun savePrecisionUsesCustomBg(context: Context, enabled: Boolean) {
        precisionUsesCustomBg = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putBoolean("test_precision_uses_custom_bg", enabled) }
    }

    // 测试页"机型"文字不透明度（%）
    fun saveDeviceNameOpacityPct(context: Context, pct: Int) {
        deviceNameOpacityPct = pct
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putInt("device_name_opacity_pct", pct) }
    }

    fun saveMaxBrightness(context: Context, enabled: Boolean) {
        isMaxBrightnessEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("max_brightness", enabled) }
    }

    fun saveTestBrightnessValue(context: Context, value: Float) {
        testBrightnessValue = value
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putFloat("test_brightness_val", value) }
    }

    // 保存测试页屏幕常亮开关
    fun saveKeepScreenOn(context: Context, enabled: Boolean) {
        isKeepScreenOnEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("keep_screen_on_enabled", enabled) }
    }

    // 保存底栏模糊开关
    fun saveNavBarBlurConfig(context: Context, enabled: Boolean) {
        navBarBlurEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("nav_bar_blur_enabled", enabled) }
    }

    // 保存底栏模糊强度
    fun saveNavBarBlurRadius(context: Context, radius: Float) {
        navBarBlurRadius = radius.coerceIn(6f, 32f)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putFloat("nav_bar_blur_radius", navBarBlurRadius) }
    }

    // 保存关于页卡片模糊开关
    fun saveAboutCardBlurConfig(context: Context, enabled: Boolean) {
        aboutCardBlurEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("about_card_blur_enabled", enabled) }
    }

    // 保存关于页动态混色开关
    fun saveAboutDynamicMixConfig(context: Context, enabled: Boolean) {
        aboutDynamicMixEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("about_dynamic_mix_enabled", enabled) }
    }

    // 保存顶栏渐变模糊开关
    fun saveTopBarGradientBlurConfig(context: Context, enabled: Boolean) {
        topBarGradientBlur = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("top_bar_gradient_blur", enabled) }
    }

    // 保存线条粗细
    fun saveLineThickness(context: Context, value: Float) {
        testLineThickness = value.coerceIn(1f, 15f)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putFloat("line_thickness", testLineThickness) }
    }

    fun saveAnimationConfig(context: Context, enabled: Boolean) {
        isAnimationEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("is_animation_enabled", enabled) }
    }

    fun saveSettingsLinePreviewConfig(context: Context, enabled: Boolean) {
        isSettingsLinePreviewEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("settings_line_preview", enabled) }
    }

    // 保存精简模式设置
    fun saveCompactModeConfig(context: Context, enabled: Boolean) {
        isCompactModeEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("is_compact_mode_enabled", enabled) }
    }

    // 保存黑边遮挡测试长按退出设置
    fun saveLongPressExitConfig(context: Context, enabled: Boolean) {
        longPressExitEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("long_press_exit_enabled", enabled) }
    }

    fun saveLongPressExitSeconds(context: Context, seconds: Int) {
        longPressExitSeconds = seconds.coerceIn(3, 20)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putInt("long_press_exit_seconds", seconds.coerceIn(3, 20)) }
    }

    // 保存旧版曲线设置
    fun saveLegacyCurveConfig(context: Context, enabled: Boolean) {
        isLegacyCurveEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putBoolean("is_legacy_curve_enabled", enabled)
        }
    }

    // 保存视图模式设置
    fun saveGridViewConfig(context: Context, enabled: Boolean) {
        isGridView = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("is_grid_view", enabled) }
    }

    // 保存渐变色条模式开关
    fun saveMultiColorMode(context: Context, enabled: Boolean) {
        isMultiColorMode = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("is_multi_color_mode", enabled) }
    }

    // 保存渐变色条模式勾选的颜色
    fun saveMultiColorSelectedColors(context: Context, colors: List<Int>) {
        multiColorSelectedColors = colors
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putString("multi_color_selected", colors.joinToString(","))
        }
    }

    // 存储用户自定义的渐变方案
    var customGradientSchemes by mutableStateOf<List<Pair<String, List<Int>>>>(emptyList())

    // 保存自定义渐变方案
    fun saveCustomGradientSchemes(context: Context, schemes: List<Pair<String, List<Int>>>) {
        customGradientSchemes = schemes
        val str = schemes.joinToString("|") { "${it.first}:${it.second.joinToString(",")}" }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putString("custom_gradient_schemes", str)
        }
    }

    // 保存渐变颜色长度
    fun saveMultiColorSegmentLength(context: Context, length: Float) {
        multiColorSegmentLength = length
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putFloat("multi_color_segment_length", length)
        }
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

    // 添加背景色卡片的用户预设
    fun addBgUserPreset(context: Context, color: Int) {
        if (!bgUserPresets.contains(color)) {
            bgUserPresets = bgUserPresets + color
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit { putString("test_line_bg_presets", bgUserPresets.joinToString(",")) }
        }
    }

    // 从背景色卡片移除用户预设
    fun removeBgUserPreset(context: Context, color: Int) {
        bgUserPresets = bgUserPresets - color
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putString("test_line_bg_presets", bgUserPresets.joinToString(",")) }
    }

    fun addUserPreset(context: Context, color: Int) {
        if (!userPresets.contains(color)) {            userPresets = userPresets + color
            savePresetsToLocal(context)
        }
    }

    fun removeUserPreset(context: Context, color: Int) {
        userPresets = userPresets - color
        savePresetsToLocal(context)
    }

    private fun savePresetsToLocal(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putString("user_presets", userPresets.joinToString(",")) }
    }

    fun saveCustomRadius(context: Context, enabled: Boolean, tl: Float, tr: Float, bl: Float, br: Float) {
        useCustomRadius = enabled
        radiusTL = tl; radiusTR = tr; radiusBL = bl; radiusBR = br
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putBoolean("use_custom_radius", enabled)
            putFloat("r_tl", tl)
            putFloat("r_tr", tr)
            putFloat("r_bl", bl)
            putFloat("r_br", br)
        }
    }

    // 保存圆角校准页四角 X/Y 曲率修正值。保留原有 key，确保旧版本数据无缝迁移。
    fun saveRadiusCorrections(
        context: Context,
        tlX: Float, trX: Float, blX: Float, brX: Float,
        tlY: Float, trY: Float, blY: Float, brY: Float
    ) {
        radiusTLX = tlX
        radiusTRX = trX
        radiusBLX = blX
        radiusBRX = brX
        radiusTLY = tlY
        radiusTRY = trY
        radiusBLY = blY
        radiusBRY = brY

        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putFloat("r_tl_x", radiusTLX)
            putFloat("r_tr_x", radiusTRX)
            putFloat("r_bl_x", radiusBLX)
            putFloat("r_br_x", radiusBRX)
            putFloat("r_tl_y", radiusTLY)
            putFloat("r_tr_y", radiusTRY)
            putFloat("r_bl_y", radiusBLY)
            putFloat("r_br_y", radiusBRY)
        }
    }

    // 圆角校准页：是否记忆上次使用的拖拽调整模式
    fun saveDragAdjustMode(context: Context, enabled: Boolean) {
        isDragAdjustModeEnabled = enabled
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putBoolean("drag_adjust_mode_enabled", enabled)
        }
    }

    fun saveCalibrationLinkedMode(context: Context, linked: Boolean) {
        isCalibrationLinked = linked
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putBoolean("calibration_is_linked", linked)
        }
    }

    // App每次启动时读取所有设置
    fun loadConfig(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        // 读取动效高级设置
        if (!prefs.contains("is_animation_enabled")) {
            val defaultEnabled = checkDevicePerformance(context)
            isAnimationEnabled = defaultEnabled
            prefs.edit { putBoolean("is_animation_enabled", defaultEnabled) }
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
        testLineBgColor = prefs.getInt("test_line_bg_color", android.graphics.Color.BLACK)
        testLineTextColor = prefs.getInt("test_line_text_color", android.graphics.Color.WHITE)
        textFollowsLine = prefs.getBoolean("test_text_follows_line", false)
        titleFollowsText = prefs.getBoolean("title_follows_text", false)
        precisionUsesCustomBg = prefs.getBoolean("test_precision_uses_custom_bg", false)
        deviceNameOpacityPct = prefs.getInt("device_name_opacity_pct", 100)

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

        // 读取底栏实时模糊 / 动态混色的首次默认值（按性能分级，手动设置优先）
        val perfTier = detectPerfTier(context)

        if (!prefs.contains("nav_bar_blur_enabled")) {
            navBarBlurEnabled = perfTier >= PerfTier.HIGH
            prefs.edit { putBoolean("nav_bar_blur_enabled", navBarBlurEnabled) }
        } else {
            navBarBlurEnabled = prefs.getBoolean("nav_bar_blur_enabled", false)
        }
        navBarBlurRadius = prefs.getFloat("nav_bar_blur_radius", 20f)

        // 关于页卡片模糊不自动开启
        aboutCardBlurEnabled = prefs.getBoolean("about_card_blur_enabled", false)

        if (!prefs.contains("about_dynamic_mix_enabled")) {
            aboutDynamicMixEnabled = perfTier >= PerfTier.MID
            prefs.edit { putBoolean("about_dynamic_mix_enabled", aboutDynamicMixEnabled) }
        } else {
            aboutDynamicMixEnabled = prefs.getBoolean("about_dynamic_mix_enabled", false)
        }
        topBarGradientBlur = prefs.getBoolean("top_bar_gradient_blur", false)

        // 读取线条粗细设置
        testLineThickness = prefs.getFloat("line_thickness", 5f).coerceIn(1f, 15f)

        // 每次打开 App 自动恢复上次调好的圆角数据
        useCustomRadius = prefs.getBoolean("use_custom_radius", false)
        radiusTL = prefs.getFloat("r_tl", -1f)
        radiusTR = prefs.getFloat("r_tr", -1f)
        radiusBL = prefs.getFloat("r_bl", -1f)
        radiusBR = prefs.getFloat("r_br", -1f)

        // 读取圆角校准页四角 X/Y 曲率修正值：沿用旧 key，不丢失已有用户数据
        radiusTLX = prefs.getFloat("r_tl_x", 0f)
        radiusTRX = prefs.getFloat("r_tr_x", 0f)
        radiusBLX = prefs.getFloat("r_bl_x", 0f)
        radiusBRX = prefs.getFloat("r_br_x", 0f)
        radiusTLY = prefs.getFloat("r_tl_y", 0f)
        radiusTRY = prefs.getFloat("r_tr_y", 0f)
        radiusBLY = prefs.getFloat("r_bl_y", 0f)
        radiusBRY = prefs.getFloat("r_br_y", 0f)

        // 读取圆角校准页最近一次使用的拖拽调整模式
        isDragAdjustModeEnabled = prefs.getBoolean("drag_adjust_mode_enabled", false)
        isCalibrationLinked = prefs.getBoolean("calibration_is_linked", true)

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

        // 背景色卡片独立预设（无默认值）
        val bgPresetStr = prefs.getString("test_line_bg_presets", "") ?: ""
        bgUserPresets = bgPresetStr.split(",").mapNotNull { it.toIntOrNull() }
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
        prefs.edit { putBoolean("is_center_data_available", available) }
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

    // 性能分级检测（LOW/MID/HIGH，详见 PerfTier 注释）
    // 模糊/混色是持续的 GPU 填充率开销，最容易掉帧的是"核心数多但 GPU 弱"的入门机（常见 8 核 + 4GB），
    // 因此以内存为主线、性能分级（Performance Class）兜底，核心数/内存兜底挡不住它们
    private fun detectPerfTier(context: Context): PerfTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        if (am.isLowRamDevice) return PerfTier.LOW

        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = info.totalMem / (1024 * 1024 * 1024f)
        val perfClass = android.os.Build.VERSION.MEDIA_PERFORMANCE_CLASS

        return when {
            ramGb >= 8f || perfClass >= android.os.Build.VERSION_CODES.S -> PerfTier.HIGH
            ramGb >= 4f -> PerfTier.MID
            else -> PerfTier.LOW
        }
    }
}