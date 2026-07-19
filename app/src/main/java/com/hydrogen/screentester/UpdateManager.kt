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

    fun isVersionGreater(remoteVersion: String, localVersion: String): Boolean {
        val remoteParts = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = localVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val length = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
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
                // 提取 APK 直链
                val assets = json.optJSONArray("assets")
                val downloadUrl = if (assets != null && assets.length() > 0) {
                    assets.getJSONObject(0).optString("browser_download_url", "").ifEmpty { null }
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