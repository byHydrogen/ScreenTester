package com.hydrogen.screentester

import android.os.Build
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object DeviceUtils {

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
        val vivoOsName = getSystemProperty("ro.vivo.os.name")
        val vivoOsVersion = getSystemProperty("ro.vivo.os.version")
        if (vivoOsName.isNotEmpty()) {
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
    //    红/橙/棕 (0°-40° 或 330°-360°) / 黄/绿 (40°-160°) / 蓝/紫/粉 (160°-330°)
    @Composable
    fun backgroundBrush(isDark: Boolean): Brush {
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
        val isRedOrange = hue < 40f || hue >= 345f
        val isYellowGreen = hue in 40f..160f

        val colors = if (isDark) {
            when {
                isRedOrange  -> listOf(Color(0xFF2B1720), Color(0xFF291E19), Color(0xFF2E2516))  // 深暖红：暗玫瑰/巧棕/墨橄榄
                isYellowGreen -> listOf(Color(0xFF2B2617), Color(0xFF1E2E16), Color(0xFF172B25))  // 深暖黄绿：暗金/墨绿/深湖绿
                else          -> listOf(Color(0xFF1E172B), Color(0xFF2D1929), Color(0xFF161E2E))  // 深冷：紫/暗红/深蓝
            }
        } else {
            when {
                isRedOrange  -> listOf(Color(0xFFFDF0EC), Color(0xFFFCE4D6), Color(0xFFF5E6D0))  // 浅暖红：樱粉/杏橘/暖沙
                isYellowGreen -> listOf(Color(0xFFF8F3DC), Color(0xFFEAFBE7), Color(0xFFE0F5EE))  // 浅暖黄绿：奶黄/薄荷绿/冰绿
                else          -> listOf(Color(0xFFFDE8E9), Color(0xFFE3E1FB), Color(0xFFD6E3F9))  // 浅冷：粉/淡紫/浅蓝
            }
        }
        return Brush.linearGradient(colors = colors, start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
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
        val isRedOrange = hue < 40f || hue >= 345f
        val isYellowGreen = hue in 40f..160f

        return if (isDark) {
            when {
                isRedOrange  -> Color(0xFF2B1720)
                isYellowGreen -> Color(0xFF2B2617)
                else          -> Color(0xFF1E172B)
            }
        } else {
            when {
                isRedOrange  -> Color(0xFFFDF0EC)
                isYellowGreen -> Color(0xFFF8F3DC)
                else          -> Color(0xFFFDE8E9)
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
        val isRedOrange = hue < 40f || hue >= 345f
        val isYellowGreen = hue in 40f..160f

        return if (isDark) {
            when {
                isRedOrange  -> Color(0xFF30201E).copy(alpha = 0.75f)  // 暖红棕
                isYellowGreen -> Color(0xFF242E1E).copy(alpha = 0.75f)  // 暖墨绿
                else          -> Color(0xFF241E30).copy(alpha = 0.75f)  // 冷紫
            }
        } else {
            when {
                isRedOrange  -> Color(0xFFFFF5F0).copy(alpha = 0.85f)  // 暖米白
                isYellowGreen -> Color(0xFFF5F8EC).copy(alpha = 0.85f)  // 暖芽白
                else          -> Color.White.copy(alpha = 0.85f)        // 冷白
            }
        }
    }
}
