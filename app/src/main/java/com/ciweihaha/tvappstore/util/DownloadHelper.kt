package com.ciweihaha.tvappstore.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.ciweihaha.tvappstore.model.AppInfo
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 下载 + 校验 + 安装。
 *
 * 流程：DownloadManager 下载到应用专属外部目录（无需存储权限）→
 * 收到完成广播 → 校验 SHA-256 → 一致则调系统安装程序，不一致则删除并提示。
 */
class DownloadHelper(private val context: Context) {

    interface Listener {
        /** 下载完成且校验通过，已发起安装 */
        fun onInstallStarted(app: AppInfo)

        /** 下载失败或校验失败 */
        fun onFailed(app: AppInfo, reason: String)

        /** 下载进度（0~100），-1 表示未知 */
        fun onProgress(app: AppInfo, percent: Int)
    }

    var listener: Listener? = null

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        /** downloadId -> 应用；放 companion 里，Activity 重建（比如平板旋转）后下载状态不丢 */
        private val pending = mutableMapOf<Long, AppInfo>()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val app = pending.remove(id) ?: return
            handleCompleted(id, app)
        }
    }

    fun register() {
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // 未注册时忽略
        }
    }

    /** 开始下载，返回 true 表示已入队 */
    fun download(app: AppInfo): Boolean {
        val url = app.url?.takeIf { it.isNotBlank() } ?: return false
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(app.displayName)
            .setDescription("TV 应用商店")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                app.downloadFileName
            )
        val id = downloadManager.enqueue(request)
        pending[id] = app
        return true
    }

    /** 查询所有进行中下载的进度（供轮询调用） */
    fun pollProgress() {
        for ((id, app) in pending.toList()) {
            val query = DownloadManager.Query().setFilterById(id)
            downloadManager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val status =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_RUNNING) {
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else -1
                    listener?.onProgress(app, percent)
                }
            }
        }
    }

    fun isDownloading(app: AppInfo): Boolean =
        pending.values.any { it.packageName == app.packageName }

    private fun handleCompleted(id: Long, app: AppInfo) {
        val query = DownloadManager.Query().setFilterById(id)
        var status = -1
        var reason = 0
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                listener?.onFailed(app, "找不到下载记录")
                return
            }
            status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            listener?.onFailed(app, "下载失败（$reason）")
            return
        }
        // 下载目标是确定的：应用专属外部 Download 目录 + 文件名
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = dir?.let { File(it, app.downloadFileName) }
        if (file == null || !file.exists()) {
            listener?.onFailed(app, "下载文件不存在")
            return
        }
        // SHA-256 校验：对不上就删除，不安装
        val expected = app.sha256?.takeIf { it.isNotBlank() }
        if (expected != null) {
            val actual = sha256Hex(file)
            if (actual == null || !expected.equals(actual, ignoreCase = true)) {
                file.delete()
                listener?.onFailed(
                    app,
                    "文件校验失败（SHA-256 不一致），为安全起见不安装，请重新下载"
                )
                return
            }
        }
        install(file)
        listener?.onInstallStarted(app)
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sha256Hex(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            // 注意：Byte 是有符号的，必须 & 0xFF 否则高位置 1 的字节会被符号扩展成 8 位
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        } catch (e: Exception) {
            null
        }
    }
}
