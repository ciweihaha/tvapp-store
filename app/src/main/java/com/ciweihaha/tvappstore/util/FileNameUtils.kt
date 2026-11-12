package com.ciweihaha.tvappstore.util

import com.ciweihaha.tvappstore.model.AppInfo

/**
 * 下载文件名处理。纯逻辑，可单元测试。
 *
 * 目录里的 file 字段是服务端给的文件名，不能直接用：
 * 一是可能含 "/" 或 ".."（路径穿越，会写到下载目录外面）；
 * 二是不同应用可能同名（互相覆盖）。
 * 修法：清理分隔符 + ".."，包名作前缀保证唯一，超长截断。
 */
object FileNameUtils {

    /** 文件名最长 200 字符（多数文件系统单文件名上限 255 字节，中文占 3 字节，留余量）。 */
    private const val MAX_NAME_LENGTH = 200

    fun safeDownloadFileName(app: AppInfo): String {
        val raw = app.file?.takeIf { it.isNotBlank() } ?: "${app.packageName}.apk"
        var cleaned = raw
            .replace('/', '_')
            .replace('\\', '_')
            .replace("..", "_")
            .trim()
        if (cleaned.isBlank()) cleaned = "${app.packageName}.apk"
        if (!cleaned.endsWith(".apk", ignoreCase = true)) cleaned += ".apk"
        // 先加包名前缀，再截断：否则前缀会让已截断到 200 字符的名字重新超长。
        var name = if (cleaned.startsWith(app.packageName)) {
            cleaned
        } else {
            "${app.packageName}_$cleaned"
        }
        if (name.length > MAX_NAME_LENGTH) {
            name = name.take(MAX_NAME_LENGTH - 4) + ".apk"
        }
        return name
    }
}
