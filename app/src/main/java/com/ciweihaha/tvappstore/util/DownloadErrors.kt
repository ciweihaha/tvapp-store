package com.ciweihaha.tvappstore.util

import android.app.DownloadManager

/**
 * 把 DownloadManager 的数字错误码翻译成中文提示。
 * 纯逻辑（错误码是编译期常量，不依赖 Android 运行时），可单元测试。
 */
object DownloadErrors {

    fun messageFor(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足，请清理后再试"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "找不到存储设备"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
        DownloadManager.ERROR_FILE_ERROR -> "文件写入失败"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "网络传输错误，请重试"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载链接重定向过多"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器返回了无法处理的响应"
        DownloadManager.ERROR_CANNOT_RESUME -> "下载无法断点续传，请重新下载"
        DownloadManager.ERROR_UNKNOWN -> "未知下载错误，请重试"
        else -> "下载失败（错误码 $reason）"
    }
}
