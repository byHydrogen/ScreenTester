package com.hydrogen.screentester

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateManager {
    private const val PREF_NAME = "app_update_prefs"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    fun checkUpdate(context: Context, isManual: Boolean = false, onResult: (Boolean, String?, String?) -> Unit) {
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

                // --- 在这里获取本地版本号 ---
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val localVersion = pInfo.versionName ?: ""

                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val ignored = prefs.getString(KEY_IGNORED_VERSION, "")
                val isNewVersion = (tagName != localVersion) && (tagName != ignored)

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
                    onResult(false, null, null)
                }
            }
        }
    }

    // 忽略特定版本
    fun ignoreVersion(context: Context, version: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_IGNORED_VERSION, version).apply()
    }
}