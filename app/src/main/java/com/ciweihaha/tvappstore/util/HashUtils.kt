package com.ciweihaha.tvappstore.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * SHA-256 工具：纯 JVM 逻辑，可单元测试。
 * 下载完 APK 后用它算哈希，和目录里的 sha256 对照，一致才安装。
 */
object HashUtils {

    /** 算文件的 SHA-256 十六进制小写串；读不到文件时返回 null。 */
    fun sha256Hex(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            // 注意：Byte 是有符号的，必须 & 0xFF，否则高位置 1 的字节会被符号扩展
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        } catch (e: Exception) {
            null
        }
    }
}
