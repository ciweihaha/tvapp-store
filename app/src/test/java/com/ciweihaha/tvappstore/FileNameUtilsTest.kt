package com.ciweihaha.tvappstore

import com.ciweihaha.tvappstore.model.AppInfo
import com.ciweihaha.tvappstore.util.FileNameUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 下载文件名处理测试：防路径穿越、防同名覆盖。 */
class FileNameUtilsTest {

    private fun app(pkg: String = "com.example.app", file: String? = "app.apk"): AppInfo {
        return AppInfo(
            packageName = pkg,
            label = "示例",
            versionName = "1.0",
            versionCode = 1L,
            size = 1024L,
            sha256 = null,
            category = "工具",
            source = "test",
            url = "https://example.com/app.apk",
            file = file,
            icon = null
        )
    }

    @Test
    fun normalFileGetsPackagePrefix() {
        assertEquals(
            "com.example.app_应用.apk",
            FileNameUtils.safeDownloadFileName(app(file = "应用.apk"))
        )
    }

    @Test
    fun pathTraversalIsStripped() {
        val name = FileNameUtils.safeDownloadFileName(app(file = "../../evil.apk"))
        assertFalse("文件名不能含 /", name.contains('/'))
        assertFalse("文件名不能含 ..", name.contains(".."))
        assertTrue(name.endsWith(".apk"))
    }

    @Test
    fun backslashIsStripped() {
        val name = FileNameUtils.safeDownloadFileName(app(file = "..\\..\\evil.apk"))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains(".."))
    }

    @Test
    fun blankFileFallsBackToPackage() {
        assertEquals("com.example.app.apk", FileNameUtils.safeDownloadFileName(app(file = null)))
        assertEquals("com.example.app.apk", FileNameUtils.safeDownloadFileName(app(file = "   ")))
    }

    @Test
    fun missingExtensionIsAdded() {
        assertEquals(
            "com.example.app_noext.apk",
            FileNameUtils.safeDownloadFileName(app(file = "noext"))
        )
    }

    @Test
    fun differentAppsWithSameFileDoNotCollide() {
        val a = FileNameUtils.safeDownloadFileName(app(pkg = "com.a.one", file = "same.apk"))
        val b = FileNameUtils.safeDownloadFileName(app(pkg = "com.b.two", file = "same.apk"))
        assertFalse(a == b)
    }

    @Test
    fun veryLongNameIsTruncated() {
        val long = "超".repeat(300) + ".apk"
        val name = FileNameUtils.safeDownloadFileName(app(file = long))
        assertTrue("文件名应截断到 200 字符以内，实际 ${name.length}", name.length <= 200)
        assertTrue(name.endsWith(".apk"))
    }
}
