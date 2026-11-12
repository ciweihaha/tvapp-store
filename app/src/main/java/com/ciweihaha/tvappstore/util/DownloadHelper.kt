package com.ciweihaha.tvappstore.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ciweihaha.tvappstore.model.AppInfo
import java.io.File
import java.util.concurrent.Executors

/**
 * 下载 + 校验 + 安装。
 *
 * 流程：DownloadManager 下载到应用专属外部目录（无需存储权限）→
 * 收到完成广播 → 后台线程校验 SHA-256 → 一致则调系统安装程序，不一致则删除并提示。
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

    /** 发起下载的结果：调用方按结果给用户具体提示，而不是笼统的“下载失败”。 */
    sealed class StartResult {
        object Started : StartResult()
        object AlreadyDownloading : StartResult()
        object NoSpace : StartResult()
        object InvalidUrl : StartResult()
        object Failed : StartResult()
    }

    var listener: Listener? = null

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** SHA-256 校验线程：大文件在主线程算哈希会卡界面，放后台跑。 */
    private val verifyExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        /** 进行中的下载；放 companion 里，Activity 重建（比如平板旋转）后下载状态不丢。 */
        private val pending = mutableMapOf<Long, PendingDownload>()

        /** 旧安装包超过 7 天就清掉，不然下载目录越堆越大。 */
        private const val OLD_FILE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }

    private data class PendingDownload(
        val app: AppInfo,
        val fileName: String,
        val enqueuedAt: Long = System.currentTimeMillis()
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val record = pending.remove(id) ?: return
            handleCompleted(id, record)
        }
    }

    fun register() {
        // targetSdk 34+ 注册广播必须声明导出行为；下载完成是系统广播，用 NOT_EXPORTED。
        // 旧写法（无 flag 的两参数版）在 Android 14+ 上会直接抛 SecurityException 闪退。
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // 未注册时忽略
        }
    }

    /** Activity 销毁时调：停掉校验线程（进行中的下载不受影响，由系统继续跑）。 */
    fun shutdown() {
        verifyExecutor.shutdown()
    }

    /** 开始下载，返回发起结果。 */
    fun download(app: AppInfo): StartResult {
        if (isDownloading(app)) return StartResult.AlreadyDownloading
        val url = app.url?.takeIf { it.isNotBlank() } ?: return StartResult.InvalidUrl

        // 事前检查存储空间：目录里有大小且空间不够时直接提示，不用等系统报错
        val size = app.size
        if (size != null && size > 0) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null) {
                val free = try {
                    StatFs(dir.path).availableBytes
                } catch (e: Exception) {
                    -1L
                }
                if (free >= 0 && free < size) return StartResult.NoSpace
            }
        }

        val fileName = FileNameUtils.safeDownloadFileName(app)
        val request = try {
            DownloadManager.Request(Uri.parse(url))
                .setTitle(app.displayName)
                .setDescription("TV 应用商店")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
        } catch (e: Exception) {
            return StartResult.InvalidUrl
        }
        val id = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            // 非法 URL 等情况下 enqueue 会抛异常，不能让它闪退
            return StartResult.Failed
        }
        pending[id] = PendingDownload(app, fileName)
        return StartResult.Started
    }

    /** 查询所有进行中下载的进度（供轮询调用）。 */
    fun pollProgress() {
        for ((id, record) in pending.toList()) {
            val app = record.app
            var exists = false
            downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return@use
                exists = true
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
            if (!exists && System.currentTimeMillis() - record.enqueuedAt > 5000) {
                // 下载记录消失（用户在系统下载列表里取消了，或系统清掉了）：
                // 清掉残留状态，不然这个应用永远点不下去
                pending.remove(id)
                listener?.onFailed(app, "下载已取消")
            }
        }
    }

    fun isDownloading(app: AppInfo): Boolean =
        pending.values.any { it.app.packageName == app.packageName }

    /** 清理 7 天前的旧安装包（进行中的下载除外）。启动时调一次。 */
    fun cleanupOldFiles() {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val activeNames = pending.values.map { it.fileName }.toSet()
        val cutoff = System.currentTimeMillis() - OLD_FILE_MAX_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk", ignoreCase = true) &&
                file.name !in activeNames && file.lastModified() < cutoff
            ) {
                file.delete()
            }
        }
    }

    private fun handleCompleted(id: Long, record: PendingDownload) {
        val app = record.app
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
            listener?.onFailed(app, DownloadErrors.messageFor(reason))
            return
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = dir?.let { File(it, record.fileName) }
        if (file == null || !file.exists()) {
            listener?.onFailed(app, "下载文件不存在")
            return
        }
        // SHA-256 校验放后台线程：92 MB 的包在主线程算哈希会卡界面
        verifyExecutor.execute {
            val expected = app.sha256?.takeIf { it.isNotBlank() }
            if (expected != null) {
                val actual = HashUtils.sha256Hex(file)
                if (actual == null || !expected.equals(actual, ignoreCase = true)) {
                    file.delete()
                    postFail(
                        app,
                        "文件校验失败（SHA-256 不一致），为安全起见不安装，请重新下载"
                    )
                    return@execute
                }
            }
            mainHandler.post {
                try {
                    install(file)
                    listener?.onInstallStarted(app)
                } catch (e: Exception) {
                    // 极少数设备没有系统安装程序时 startActivity 会抛异常
                    listener?.onFailed(app, "没有可用的安装程序")
                }
            }
        }
    }

    private fun postFail(app: AppInfo, reason: String) {
        mainHandler.post { listener?.onFailed(app, reason) }
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
}
