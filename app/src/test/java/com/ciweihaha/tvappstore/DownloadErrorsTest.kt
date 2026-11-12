package com.ciweihaha.tvappstore

import android.app.DownloadManager
import com.ciweihaha.tvappstore.util.DownloadErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载错误码翻译测试。
 * DownloadManager.ERROR_* 是编译期常量，引用时会被内联，
 * 所以这个测试不需要 Android 运行时就能跑。
 */
class DownloadErrorsTest {

    @Test
    fun insufficientSpaceMessage() {
        assertEquals("存储空间不足，请清理后再试", DownloadErrors.messageFor(DownloadManager.ERROR_INSUFFICIENT_SPACE))
    }

    @Test
    fun allKnownCodesHaveFriendlyMessages() {
        val codes = listOf(
            DownloadManager.ERROR_DEVICE_NOT_FOUND,
            DownloadManager.ERROR_FILE_ALREADY_EXISTS,
            DownloadManager.ERROR_FILE_ERROR,
            DownloadManager.ERROR_HTTP_DATA_ERROR,
            DownloadManager.ERROR_TOO_MANY_REDIRECTS,
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
            DownloadManager.ERROR_CANNOT_RESUME,
            DownloadManager.ERROR_UNKNOWN
        )
        for (code in codes) {
            val message = DownloadErrors.messageFor(code)
            assertTrue("错误码 $code 应有中文提示", message.isNotBlank())
            assertFalse("已知错误码不应再暴露数字，实际：$message", message.contains(code.toString()))
        }
    }

    @Test
    fun unknownCodeFallsBackToCode() {
        assertTrue(DownloadErrors.messageFor(99999).contains("99999"))
    }
}
