package com.hydrogen.screentester

import android.os.Build
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

object DeviceUtils {

    // ── 大屏适配 ──────────────────────────────────────────────
    // 大屏 = 最小宽度 sw ≥ 600dp（平板 / 折叠内屏 / 桌面窗口）
    const val LARGE_SCREEN_MIN_WIDTH_DP = 600

    // 内容最大宽度：大屏上居中并限宽
    // 手机宽度不足时不生效，行为不变
    val ContentMaxWidth = 840.dp

    // 表单类页面（设置页、关于页）上限
    val FormMaxWidth = 640.dp

    // 底部导航栏在大屏上的宽度上限
    val NavBarMaxWidth = 480.dp

    // 当前是否大屏（sw ≥ 600dp）
    @Composable
    fun isLargeScreen(): Boolean =
        LocalConfiguration.current.smallestScreenWidthDp >= LARGE_SCREEN_MIN_WIDTH_DP

    // 提取的公共底层反射方法，用来偷看各大厂商藏在底层的配置
    private fun getSystemProperty(key: String): String {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            val value = getMethod.invoke(null, key) as String
            value
        } catch (e: Exception) {
            ""
        }
    }

    // 1. 获取设备宣传名 (Market Name)
    fun getMarketName(): String {
        // 尝试各大厂商写入宣传名的高频字段
        val propsToTry = listOf(
            "ro.product.marketname",           // 小米 / 红米 / 华为 / 老荣耀 等
            "ro.vendor.oplus.market.name",     // 欧加系 (OPPO / 一加 / 真我)
            "ro.vivo.market.name",             // vivo / iQOO
            "ro.honor.market.name",            // 新荣耀
            "ro.product.odm.marketname",       // 部分华为/荣耀/蓝厂机型上的兜底字段
            "ro.product.model.marketname",     // 其他部分机型
            "ro.meizu.product.model",          // 魅族
            "ro.nubia.model",                  // 努比亚 / 红魔
            "ro.product.commercial.model",     // 荣耀部分机型
            "ro.config.marketing_name",        // 荣耀部分机型
            "ro.product.vendor.marketname"     // 荣耀部分机型
        )

        for (prop in propsToTry) {
            val name = getSystemProperty(prop)
            if (name.isNotEmpty() && name != "unknown") {
                if (name.startsWith("Xiaomi Redmi", ignoreCase = true)) {
                    return name.substring(7)
                }
                return name
            }
        }

        // 格式化厂商名（首字母大写）
        val manufacturer = Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        val model = Build.MODEL
        if (manufacturer.equals("Xiaomi", ignoreCase = true)) {
            if (model.startsWith("Redmi", ignoreCase = true) || model.startsWith("POCO", ignoreCase = true)) {
                return model
            }
        }

        // 如果型号里已经带了品牌词，就不重复拼接了
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    // 2. 获取定制操作系统版本 (OS Version)
    fun getOSVersion(): String {
        val incremental = Build.VERSION.INCREMENTAL
        val brand = Build.BRAND.lowercase()

        // 【小米系】 HyperOS / MIUI
        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        if (incremental.startsWith("OS") || incremental.startsWith("V")) {
            return incremental
        }
        if (miuiVersion.isNotEmpty()) {
            return "$miuiVersion ($incremental)"
        }

        // 【欧加系】 ColorOS / OxygenOS / RealmeUI
        val oplusRom = getSystemProperty("ro.build.version.oplusrom")
        if (oplusRom.isNotEmpty()) {
            val osName = when {
                brand.contains("oneplus") -> "OxygenOS / ColorOS"
                brand.contains("realme") -> "Realme UI"
                else -> "ColorOS"
            }
            return "$osName $oplusRom"
        }

        // 【蓝厂系】 OriginOS / FuntouchOS
        val vivoOsDisplayId = getSystemProperty("ro.vivo.os.build.display.id")
        val vivoOsName = getSystemProperty("ro.vivo.os.name")
        val vivoOsVersion = getSystemProperty("ro.vivo.os.version")
        if (vivoOsDisplayId.contains("OriginOS", ignoreCase = true)) {
            // 新 OriginOS 直接使用 display.id
            return vivoOsDisplayId.trim()
        } else if (vivoOsName.isNotEmpty()) {
            // 老 FuntouchOS 旧机型
            return "$vivoOsName $vivoOsVersion"
        }

        // 【荣耀】 MagicOS (确保荣耀即使触发了老旧的华为残留字段，也优先走 MagicOS 独立逻辑)
        val magicVersion = getSystemProperty("ro.build.version.magic")
        val honorOsVersion = getSystemProperty("ro.honor.os.version")
        val honorBuildVersion = getSystemProperty("ro.honor.build.version")
        if (magicVersion.isNotEmpty() || honorOsVersion.isNotEmpty() || honorBuildVersion.isNotEmpty() || brand.contains("honor")) {
            val rawVersion = when {
                magicVersion.isNotEmpty() -> magicVersion
                honorOsVersion.isNotEmpty() -> honorOsVersion
                honorBuildVersion.isNotEmpty() -> honorBuildVersion
                else -> incremental
            }
            val cleaned = rawVersion.replace("_", " ").trim()
            return if (cleaned.startsWith("MagicOS", ignoreCase = true)) {
                cleaned
            } else {
                "MagicOS $cleaned"
            }
        }

        // 【华为】 HarmonyOS / EMUI
        val emuiVersion = getSystemProperty("ro.build.version.emui")
        if ((emuiVersion.isNotEmpty() || incremental.contains("Harmony") || brand.contains("huawei"))) {
            val harmonyName = getSystemProperty("ro.huawei.build.display.id")
            return harmonyName.ifEmpty { "HarmonyOS / EMUI $incremental" }
        }

        // 【三星】 One UI
        val oneUiVersionRaw = getSystemProperty("ro.build.version.oneui")
        if (oneUiVersionRaw.isNotEmpty() && oneUiVersionRaw.toIntOrNull() != null) {
            val ver = oneUiVersionRaw.toInt()
            val major = ver / 10000
            val minor = (ver % 10000) / 100
            return "One UI $major.$minor ($incremental)"
        }

        // 【魅族】 Flyme
        val meizuModel = getSystemProperty("ro.meizu.product.model")
        if (meizuModel.isNotEmpty()) {
            val flymeVersion = getSystemProperty("ro.build.display.id")
            return if (flymeVersion.isNotEmpty()) "Flyme $flymeVersion" else "Flyme $incremental"
        }

        // 【努比亚 / 红魔】 RedMagic OS
        val nubiaModel = getSystemProperty("ro.nubia.model")
        if (nubiaModel.isNotEmpty()) {
            val nubiaOsVersion = getSystemProperty("ro.build.display.id")
            return if (nubiaOsVersion.isNotEmpty()) nubiaOsVersion else "RedMagic OS $incremental"
        }

        // 兜底方案
        return Build.DISPLAY
    }

    // 3. 根据系统莫奈取色色相，返回和谐的页面背景渐变
    //    统一用 dynamicLightColorScheme 检测色相，确保浅色/深色模式分类一致
    //    红/橙/棕 (0°-40° 或 345°-360°) / 黄/绿 (40°-160°) / 青/蓝绿 (160°-210°) / 蓝 (210°-270°) / 紫/粉 (270°-345°)
    @Composable
    fun backgroundBrush(isDark: Boolean): Brush {
        return Brush.linearGradient(
            colors = backgroundColors(isDark),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    // 3.1 按莫奈色相分类的三色背景色板（静态渐变与动态混色共用，保证开关切换前后色系一致）
    @Composable
    fun backgroundColors(isDark: Boolean): List<Color> {
        val context = LocalContext.current
        val monetPrimary = dynamicLightColorScheme(context).primary
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (monetPrimary.red * 255).toInt(),
            (monetPrimary.green * 255).toInt(),
            (monetPrimary.blue * 255).toInt(),
            hsv
        )
        val hue = hsv[0]

        return if (isDark) {
            when {
                hue < 40f || hue >= 345f  -> listOf(Color(0xFF2B1720), Color(0xFF291E19), Color(0xFF2E2516))  // 深暖红：暗玫瑰/巧棕/墨橄榄
                hue in 40f..160f          -> listOf(Color(0xFF2B2617), Color(0xFF1E2E16), Color(0xFF172B25))  // 深暖黄绿：暗金/墨绿/深湖绿
                hue in 160f..210f         -> listOf(Color(0xFF1A2E28), Color(0xFF172B2E), Color(0xFF1E282E))  // 深青蓝绿：深海绿/暗青/深蓝灰
                hue in 210f..270f         -> listOf(Color(0xFF1A1E2E), Color(0xFF1B1D30), Color(0xFF221E2E))  // 深蓝：藏蓝/暗蓝/深紫蓝
                else                      -> listOf(Color(0xFF1E172B), Color(0xFF2D1929), Color(0xFF161E2E))  // 深紫粉：紫/暗红/深蓝
            }
        } else {
            when {
                hue < 40f || hue >= 345f  -> listOf(Color(0xFFFDF0EC), Color(0xFFFCE4D6), Color(0xFFF5E6D0))  // 浅暖红：樱粉/杏橘/暖沙
                hue in 40f..160f          -> listOf(Color(0xFFF8F3DC), Color(0xFFEAFBE7), Color(0xFFE0F5EE))  // 浅暖黄绿：奶黄/薄荷绿/冰绿
                hue in 160f..210f         -> listOf(Color(0xFFDDF3EC), Color(0xFFD3EDF5), Color(0xFFDAE9F4))  // 浅青蓝绿：薄荷/冰蓝/雾蓝
                hue in 210f..270f         -> listOf(Color(0xFFE8ECF8), Color(0xFFE3E8FA), Color(0xFFEAE5F5))  // 浅蓝：浅蓝/冰蓝/淡紫
                else                      -> listOf(Color(0xFFFDE8E9), Color(0xFFE3E1FB), Color(0xFFD6E3F9))  // 浅紫粉：粉/淡紫/浅蓝
            }
        }
    }

    // 3.2 动态混色专用色板（HyperOS「我的设备」风格）：底色 + 三个光斑色。
    //     色相在莫奈主色相基础上 ±40°~60° 拉开、饱和度比静态渐变高一档，
    //     光斑漂移时的颜色变化才肉眼可见；深浅色各自成套。
    @Composable
    fun dynamicMixColors(isDark: Boolean): List<Color> {
        val context = LocalContext.current
        val monetPrimary = dynamicLightColorScheme(context).primary
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (monetPrimary.red * 255).toInt(),
            (monetPrimary.green * 255).toInt(),
            (monetPrimary.blue * 255).toInt(),
            hsv
        )
        val hue = hsv[0]

        // 青蓝绿档收窄光斑色相跨度：默认跨度会同时混进蓝紫和绿，这一档尤其显脏
        val nearSpan = if (hue in 160f..210f) 25f else 45f
        val farSpan = if (hue in 160f..210f) 25f else 60f

        return if (isDark) listOf(
            Color.hsv(hue, 0.30f, 0.12f),                          // 底色：近黑的莫奈色调
            Color.hsv(hue, 0.55f, 0.42f),                          // 主色斑
            Color.hsv((hue + nearSpan) % 360f, 0.50f, 0.36f),      // 邻近色相光斑
            Color.hsv((hue - farSpan + 360f) % 360f, 0.42f, 0.38f) // 另一侧光斑
        ) else listOf(
            Color.hsv(hue, 0.14f, 0.985f),                         // 底色：近乎白的莫奈色调
            Color.hsv(hue, 0.52f, 0.87f),                          // 主色斑
            Color.hsv((hue + nearSpan) % 360f, 0.46f, 0.90f),      // 邻近色相光斑
            Color.hsv((hue - farSpan + 360f) % 360f, 0.40f, 0.91f) // 另一侧光斑
        )
    }

    // 4. 返回背景主色（用于顶部栏渐变淡出等场景）
    @Composable
    fun backgroundBaseColor(isDark: Boolean): Color {
        val context = LocalContext.current
        val monetPrimary = dynamicLightColorScheme(context).primary
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (monetPrimary.red * 255).toInt(),
            (monetPrimary.green * 255).toInt(),
            (monetPrimary.blue * 255).toInt(),
            hsv
        )
        val hue = hsv[0]

        return if (isDark) {
            when {
                hue < 40f || hue >= 345f  -> Color(0xFF2B1720)
                hue in 40f..160f          -> Color(0xFF2B2617)
                hue in 160f..210f         -> Color(0xFF1A2E28)
                hue in 210f..270f         -> Color(0xFF1A1E2E)
                else                      -> Color(0xFF1E172B)
            }
        } else {
            when {
                hue < 40f || hue >= 345f  -> Color(0xFFFDF0EC)
                hue in 40f..160f          -> Color(0xFFF8F3DC)
                hue in 160f..210f         -> Color(0xFFDDF3EC)
                hue in 210f..270f         -> Color(0xFFE8ECF8)
                else                      -> Color(0xFFFDE8E9)
            }
        }
    }

    // 5. 返回与背景混色和谐的卡片背景色
    @Composable
    fun cardContainerColor(isDark: Boolean): Color {
        val context = LocalContext.current
        val monetPrimary = dynamicLightColorScheme(context).primary
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (monetPrimary.red * 255).toInt(),
            (monetPrimary.green * 255).toInt(),
            (monetPrimary.blue * 255).toInt(),
            hsv
        )
        val hue = hsv[0]

        return if (isDark) {
            when {
                hue < 40f || hue >= 345f  -> Color(0xFF30201E).copy(alpha = 0.75f)  // 暖红棕
                hue in 40f..160f          -> Color(0xFF242E1E).copy(alpha = 0.75f)  // 暖墨绿
                hue in 160f..210f         -> Color(0xFF1E302A).copy(alpha = 0.75f)  // 青蓝绿
                hue in 210f..270f         -> Color(0xFF1E2230).copy(alpha = 0.75f)  // 深蓝紫
                else                      -> Color(0xFF241E30).copy(alpha = 0.75f)  // 蓝紫
            }
        } else {
            when {
                hue < 40f || hue >= 345f  -> Color(0xFFFFF5F0).copy(alpha = 0.85f)  // 暖米白
                hue in 40f..160f          -> Color(0xFFF5F8EC).copy(alpha = 0.85f)  // 暖芽白
                hue in 160f..210f         -> Color(0xFFF0F8F5).copy(alpha = 0.85f)  // 青白
                hue in 210f..270f         -> Color(0xFFF0F2FA).copy(alpha = 0.85f)  // 蓝白
                else                      -> Color.White.copy(alpha = 0.85f)  // 冷白
            }
        }
    }
}
