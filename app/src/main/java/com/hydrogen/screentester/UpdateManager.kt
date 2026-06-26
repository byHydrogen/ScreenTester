package com.hydrogen.screentester

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateManager {
    private const val PREF_NAME = "app_update_prefs"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    // 版本比较函数
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
        onResult: (Boolean, String?, String?) -> Unit,
        onError: (() -> Unit)? = null
    ) {
        thread {
            try {
                val url = URL("https://api.github.com/repos/byHydrogen/ScreenTester/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val tagName = json.getString("tag_name").replace("v", "", ignoreCase = true)
                val body = json.getString("body")

                // --- 获取本地版本号 ---
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val localVersion = pInfo.versionName ?: ""

                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val ignored = prefs.getString(KEY_IGNORED_VERSION, "")
                // 使用版本比较：只有远程版本更高时才显示更新
                val isNewVersion = isVersionGreater(tagName, localVersion) && (isManual || tagName != ignored)

                Handler(Looper.getMainLooper()).post {
                    if (isNewVersion) {
                        onResult(true, tagName, body)
                    } else {
                        onResult(false, null, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    if (onError != null) {
                        onError()
                    } else {
                        onResult(false, null, null)
                    }
                }
            }
        }
    }

    // 忽略特定版本
    fun ignoreVersion(context: Context, version: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_IGNORED_VERSION, version) }
    }
}