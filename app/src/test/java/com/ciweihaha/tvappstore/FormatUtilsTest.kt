package com.ciweihaha.tvappstore

import com.ciweihaha.tvappstore.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** 文件大小格式化测试。 */
class FormatUtilsTest {

    @Test
    fun bytes() {
        assertEquals("0 B", FormatUtils.formatSize(0))
        assertEquals("512 B", FormatUtils.formatSize(512))
    }

    @Test
    fun kilobytes() {
        assertEquals("1 KB", FormatUtils.formatSize(1024))
        assertEquals("2 KB", FormatUtils.formatSize(1536))
    }

    @Test
    fun megabytes() {
        assertEquals("1.0 MB", FormatUtils.formatSize(1024 * 1024))
        // 真实目录里最大的包约 92.2 MB
        assertEquals("92.2 MB", FormatUtils.formatSize(96698037))
    }

    @Test
    fun negativeIsEmpty() {
        assertEquals("", FormatUtils.formatSize(-1))
    }
}
