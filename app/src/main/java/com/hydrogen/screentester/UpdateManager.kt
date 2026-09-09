package com.hydrogen.screentester

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class UpdateInfo(
    val hasUpdate: Boolean,
    val versionName: String?,
    val changelog: String?,
    val downloadUrl: String?,
    val releasePageUrl: String?
)

object UpdateManager {
    private const val PREF_NAME = "app_update_prefs"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    private fun apiUrl(): String = if (ThemeSettings.updateDownloadSource == "github") {
        "https://api.github.com/repos/byHydrogen/ScreenTester/releases/latest"
    } else {
        "https://gitee.com/api/v5/repos/byHydrogen/screen-tester/releases/latest"
    }

    fun releasePageUrl(): String = if (ThemeSettings.updateDownloadSource == "github") {
        "https://github.com/byHydrogen/ScreenTester/releases"
    } else {
        "https://gitee.com/byHydrogen/screen-tester/releases"
    }

    // 版本比较：先比数字段，数字相同再比 Beta 序号（Beta 1 < Beta 2 < 稳定版）
    // 支持 "3.x.x Beta 1" / "3.x.xbeta1" / "3.x.x Beta" 等写法
    private fun parseVersion(version: String): Pair<List<Int>, Int> {
        val match = Regex("""^([0-9]+(?:\.[0-9]+)*)(?:\s*beta\s*(\d*))?""", RegexOption.IGNORE_CASE).find(version.trim())
        if (match == null) return listOf(0) to Int.MAX_VALUE
        val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 }
        val beta = if (match.groupValues[2].isEmpty()) Int.MAX_VALUE else match.groupValues[2].toInt()
        return numbers to beta
    }

    fun isVersionGreater(remoteVersion: String, localVersion: String): Boolean {
        val (remoteNums, remoteBeta) = parseVersion(remoteVersion)
        val (localNums, localBeta) = parseVersion(localVersion)
        val length = maxOf(remoteNums.size, localNums.size)
        for (i in 0 until length) {
            val r = remoteNums.getOrElse(i) { 0 }
            val l = localNums.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        // 数字相同：稳定版 > Beta；Beta 序号大者更新
        return remoteBeta > localBeta
    }

    fun checkUpdate(
        context: Context,
        isManual: Boolean = false,
        onResult: (hasUpdate: Boolean, versionName: String?, changelog: String?, downloadUrl: String?) -> Unit,
        onError: (() -> Unit)? = null
    ) {
        thread {
            try {
                val url = URL(apiUrl())
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val tagName = json.getString("tag_name").replace("v", "", ignoreCase = true)
                val body = json.optString("body", "")
                // 提取 APK 直链（只认 .apk 后缀，跳过 checksum 等其他附件）
                val assets = json.optJSONArray("assets")
                val downloadUrl = if (assets != null && assets.length() > 0) {
                    (0 until assets.length())
                        .mapNotNull { i -> assets.optJSONObject(i)?.optString("browser_download_url", "") }
                        .firstOrNull { it.isNotEmpty() && it.endsWith(".apk", ignoreCase = true) }
                } else null

                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val localVersion = pInfo.versionName ?: ""

                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val ignored = prefs.getString(KEY_IGNORED_VERSION, "")
                val isNewVersion = isVersionGreater(tagName, localVersion) && (isManual || tagName != ignored)

                Handler(Looper.getMainLooper()).post {
                    if (isNewVersion) {
                        onResult(true, tagName, body, downloadUrl)
                    } else {
                        onResult(false, null, null, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    if (onError != null) onError()
                    else onResult(false, null, null, null)
                }
            }
        }
    }

    fun ignoreVersion(context: Context, version: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_IGNORED_VERSION, version) }
    }
}