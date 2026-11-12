package com.ciweihaha.tvappstore.util

import java.util.Locale

/** 文件大小格式化。纯逻辑，可单元测试。 */
object FormatUtils {

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 0 -> ""
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
