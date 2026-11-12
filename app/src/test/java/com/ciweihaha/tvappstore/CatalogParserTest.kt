package com.ciweihaha.tvappstore

import com.ciweihaha.tvappstore.util.CatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * 目录解析测试：重点是“坏条目不拖垮整个目录”。
 * 运行：./gradlew testDebugUnitTest
 */
class CatalogParserTest {

    private fun appJson(
        pkg: String = "com.example.app",
        label: String = "示例应用",
        url: String = "https://example.com/app.apk",
        extra: String = ""
    ): String {
        return """{"package":"$pkg","label":"$label","versionName":"1.2.3","versionCode":123,"size":1024,"sha256":"abcdef","category":"工具","source":"test","url":"$url","file":"app.apk","icon":"https://example.com/icon.png"$extra}"""
    }

    @Test
    fun parseValidCatalog() {
        val json = """{"generated_at":"2026-11-12T00:00:00Z","base_url":"https://example.com","categories":["工具","影视"],"apps":[${appJson()},${appJson("com.example.two", "第二个")}]}"""
        val catalog = CatalogParser.parse(json)
        assertEquals("2026-11-12T00:00:00Z", catalog.generatedAt)
        assertEquals(listOf("工具", "影视"), catalog.categories)
        assertEquals(2, catalog.apps!!.size)
        assertEquals("示例应用", catalog.apps[0].label)
        assertEquals(123L, catalog.apps[0].versionCode)
        assertEquals(1024L, catalog.apps[0].size)
    }

    @Test
    fun skipsAppMissingPackage() {
        val json = """{"apps":[{"label":"没有包名","url":"https://example.com/a.apk"},${appJson()}]}"""
        val catalog = CatalogParser.parse(json)
        assertEquals(1, catalog.apps!!.size)
        assertEquals("com.example.app", catalog.apps[0].packageName)
    }

    @Test
    fun skipsAppMissingUrl() {
        val json = """{"apps":[{"package":"com.example.nourl","label":"没有链接"},${appJson()}]}"""
        val catalog = CatalogParser.parse(json)
        assertEquals(1, catalog.apps!!.size)
        assertEquals("com.example.app", catalog.apps[0].packageName)
    }

    @Test
    fun skipsNonObjectElement() {
        val json = """{"apps":["这条根本不是对象",42,null,${appJson()}]}"""
        val catalog = CatalogParser.parse(json)
        assertEquals(1, catalog.apps!!.size)
    }

    @Test
    fun coercesSizeFromStringAndDouble() {
        val stringSize = appJson(extra = "").replace("\"size\":1024", "\"size\":\"2048\"")
        val doubleSize = appJson(extra = "").replace("\"size\":1024", "\"size\":3072.9")
        val catalog = CatalogParser.parse("""{"apps":[$stringSize,$doubleSize]}""")
        assertEquals(2, catalog.apps!!.size)
        assertEquals(2048L, catalog.apps[0].size)
        assertEquals(3072L, catalog.apps[1].size)
    }

    @Test
    fun invalidSizeBecomesNullNotCrash() {
        val bad = appJson().replace("\"size\":1024", "\"size\":\"不是数字\"")
        val catalog = CatalogParser.parse("""{"apps":[$bad]}""")
        assertEquals(1, catalog.apps!!.size)
        assertNull(catalog.apps[0].size)
    }

    @Test
    fun emptyAndMissingApps() {
        assertEquals(0, CatalogParser.parse("""{"apps":[]}""").apps!!.size)
        assertEquals(0, CatalogParser.parse("""{}""").apps!!.size)
        assertNull(CatalogParser.parse("""{}""").categories)
    }

    @Test
    fun invalidJsonThrowsIOException() {
        try {
            CatalogParser.parse("{这不是 JSON")
            fail("应该抛 IOException")
        } catch (e: IOException) {
            // 预期
        }
    }

    @Test
    fun nonObjectRootThrowsIOException() {
        try {
            CatalogParser.parse("""[1,2,3]""")
            fail("应该抛 IOException")
        } catch (e: IOException) {
            // 预期
        }
    }

    /**
     * 压力测试：2900 个应用的目录解析耗时与内存。
     * 按真实目录结构生成（每个条目约 600 字节，总计约 1.7 MB）。
     */
    @Test
    fun parse2900Apps_performance() {
        val sb = StringBuilder("""{"generated_at":"2026-11-12T00:00:00Z","categories":["工具"],"apps":[""")
        for (i in 0 until 2900) {
            if (i > 0) sb.append(',')
            sb.append(appJson(pkg = "com.example.app$i", label = "应用 $i", url = "https://example.com/app$i.apk"))
        }
        sb.append("]}")
        val json = sb.toString()
        println("2900 条目录 JSON 大小：${json.length / 1024} KB")

        val runtime = Runtime.getRuntime()
        System.gc()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()
        val start = System.nanoTime()
        val catalog = CatalogParser.parse(json)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        println("2900 条目录解析耗时：${elapsedMs}ms，内存增量约 ${(memAfter - memBefore) / 1024} KB")

        assertEquals(2900, catalog.apps!!.size)
        assertEquals("com.example.app2899", catalog.apps[2899].packageName)
        assertTrue("解析 2900 条不应超过 10 秒，实际 ${elapsedMs}ms", elapsedMs < 10_000)
    }
}
