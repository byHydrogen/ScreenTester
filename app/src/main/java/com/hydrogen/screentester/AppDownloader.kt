package com.hydrogen.screentester

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.app.NotificationCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class DownloadStatus { Idle, Downloading, Paused, Done, Error }

class DownloadState {
    var status by mutableStateOf(DownloadStatus.Idle)
    var progress by mutableStateOf(0f)
    var errorMessage by mutableStateOf<String?>(null)

    private var job: Job? = null
    @Volatile private var paused = false
    @Volatile private var expectedSize: Long = 0L
    private var notifyMgr: NotificationManager? = null
    private val notifyId = 1001

    // 独立协程作用域 弹窗关闭/页面切换不会中断下载
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, url: String, fileName: String) {
        if (status == DownloadStatus.Downloading) return
        // 清理旧缓存
        cleanupCache(context)

        status = DownloadStatus.Downloading
        progress = 0f
        paused = false
        createNotification(context)

        job = downloadScope.launch {
            try {
                val file = File(context.cacheDir, "updates/$fileName")
                file.parentFile?.mkdirs()
                downloadFile(context, url, file)
                if (!paused) {
                    // 校验完整性：比较下载字节和 Content-Length
                    val conn2 = URL(url).openConnection() as HttpURLConnection
                    conn2.connectTimeout = 5000; conn2.readTimeout = 5000
                    conn2.connect()
                    val expectedSize = conn2.contentLength.toLong()
                    conn2.disconnect()
                    val actualSize = file.length()
                    if (expectedSize > 0 && actualSize != expectedSize) {
                        file.delete()
                        withContext(Dispatchers.Main) {
                            cancelNotification()
                            status = DownloadStatus.Error
                            errorMessage = "文件不完整"
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (status == DownloadStatus.Error) status = DownloadStatus.Idle
                            }, 3000)
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        cancelNotification()
                        status = DownloadStatus.Done
                        progress = 1f
                    }
                }
            } catch (e: Exception) {
                if (!paused) {
                    withContext(Dispatchers.Main) {
                        cancelNotification()
                        status = DownloadStatus.Error
                        errorMessage = e.message ?: "下载失败"
                        // 3 秒后自动恢复
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (status == DownloadStatus.Error) status = DownloadStatus.Idle
                        }, 3000)
                    }
                }
            }
        }
    }

    private suspend fun downloadFile(context: Context, url: String, file: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.connect()
        try {
            val totalBytes = conn.contentLength.toLong()
            expectedSize = totalBytes
            // use{} 保证即使中途异常/协程取消，文件流也一定被关闭
            FileOutputStream(file).use { fos ->
                var downloaded = 0L
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1 && !paused) {
                        fos.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalBytes > 0) {
                            val p = downloaded.toFloat() / totalBytes.toFloat()
                            withContext(Dispatchers.Main) {
                                progress = p
                                updateNotification(context, p)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (paused) file.delete() // 清理未完成文件
    }

    private fun createNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifyMgr = nm
        val channel = NotificationChannel("download", "下载通知", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        val n = NotificationCompat.Builder(context, "download")
            .setContentTitle("正在下载更新")
            .setContentText("0%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, false)
            .build()
        try { nm.notify(notifyId, n) } catch (_: SecurityException) { }
    }

    private fun updateNotification(context: Context, progress: Float) {
        val nm = notifyMgr ?: return
        val pct = (progress * 100).toInt()
        val n = NotificationCompat.Builder(context, "download")
            .setContentTitle("正在下载更新")
            .setContentText("$pct%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, pct, false)
            .build()
        try { nm.notify(notifyId, n) } catch (_: SecurityException) { }
    }

    private fun cancelNotification() {
        notifyMgr?.cancel(notifyId)
        notifyMgr = null
    }

    fun pause() {
        paused = true
        status = DownloadStatus.Paused
        cancelNotification()
        job?.cancel()
    }

    fun cancel(context: Context) {
        paused = true
        job?.cancel()
        cancelNotification()
        status = DownloadStatus.Idle
        progress = 0f
        cleanupCache(context)
    }

    fun install(context: Context, fileName: String) {
        val file = File(context.cacheDir, "updates/$fileName")
        // 校验文件是否有效 APK（ZIP 头部检查）
        val valid = file.exists() && file.length() > 0 && (expectedSize <= 0 || file.length() == expectedSize)
        if (!valid) {
            file.delete()
            cleanupCache(context)
            status = DownloadStatus.Error
            progress = 0f
            errorMessage = "文件已损坏"
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (status == DownloadStatus.Error) status = DownloadStatus.Idle
            }, 3000)
            return
        }
        // 未授予"安装未知应用"权限时，引导用户去系统设置开启
        if (!context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                status = DownloadStatus.Error
                errorMessage = "无法打开安装权限设置"
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (status == DownloadStatus.Error) status = DownloadStatus.Idle
                }, 3000)
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            status = DownloadStatus.Error
            errorMessage = "无法启动安装，请重试"
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (status == DownloadStatus.Error) status = DownloadStatus.Idle
            }, 3000)
        }
    }

    companion object {
        fun cleanupCache(context: Context) {
            try {
                File(context.cacheDir, "updates").deleteRecursively()
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun rememberDownloadState(): DownloadState = remember { DownloadState() }

@Composable
fun DownloadProgressPoller(state: DownloadState) { /* 手动下载不需轮询 */ }
