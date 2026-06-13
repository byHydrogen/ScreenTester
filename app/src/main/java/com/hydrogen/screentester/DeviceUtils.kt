package com.hydrogen.screentester

import android.os.Build

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

    // ==========================================
    // 1. 获取设备宣传名 (Market Name)
    // ==========================================
    fun getMarketName(): String {
        // 尝试各大厂商写入宣传名的高频字段
        val propsToTry = listOf(
            "ro.product.marketname",           // 小米 / 红米 / 华为 / 老荣耀 等
            "ro.vendor.oplus.market.name",     // 欧加系 (OPPO / 一加 / 真我)
            "ro.vivo.market.name",             // vivo / iQOO
            "ro.honor.market.name",            // 新荣耀
            "ro.product.odm.marketname",       // 部分华为/荣耀/蓝厂机型上的兜底字段
            "ro.product.model.marketname"      // 其他部分机型
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

        // 如果型号里已经带了品牌词，就不重复拼接了 (比如 华为自带 HUAWEI Mate 60)
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    // ==========================================
    // 2. 获取定制操作系统版本 (OS Version)
    // ==========================================
    fun getOSVersion(): String {
        val incremental = Build.VERSION.INCREMENTAL
        val brand = Build.BRAND.lowercase()

        // 【小米系】 HyperOS / MIUI
        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        if (incremental.startsWith("OS") || incremental.startsWith("V")) {
            return incremental // HyperOS 通常就是 OS1.0.X...
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
        if (magicVersion.isNotEmpty() || brand.contains("honor")) {
            return if (magicVersion.isNotEmpty()) "MagicOS $magicVersion" else "MagicOS ($incremental)"
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

        // 兜底方案
        return Build.DISPLAY
    }
}