package com.ciweihaha.tvappstore

import com.ciweihaha.tvappstore.util.HashUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** SHA-256 工具测试：用已知向量核对，不依赖 Android。 */
class HashUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sha256KnownVector() {
        val file = tempFolder.newFile("abc.txt")
        file.writeText("abc")
        // "abc" 的 SHA-256 是公开的标准测试向量
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashUtils.sha256Hex(file)
        )
    }

    @Test
    fun sha256EmptyFile() {
        val file = tempFolder.newFile("empty.bin")
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            HashUtils.sha256Hex(file)
        )
    }

    @Test
    fun sha256MissingFileReturnsNull() {
        assertNull(HashUtils.sha256Hex(File(tempFolder.root, "不存在的文件.apk")))
    }

    @Test
    fun sha256LargeFile() {
        // 10 MB 随机文件：算得出来且两次结果一致（模拟大 APK 校验）
        val file = tempFolder.newFile("big.bin")
        val bytes = ByteArray(10 * 1024 * 1024) { (it % 251).toByte() }
        file.writeBytes(bytes)
        val first = HashUtils.sha256Hex(file)
        val second = HashUtils.sha256Hex(file)
        assertEquals(64, first!!.length)
        assertEquals(first, second)
    }
}
