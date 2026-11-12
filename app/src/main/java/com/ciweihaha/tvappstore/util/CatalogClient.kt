package com.ciweihaha.tvappstore.util

import com.ciweihaha.tvappstore.model.CatalogResponse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 拉取目录 JSON。同步调用，调用方必须在后台线程执行。
 *
 * - 10 分钟内存缓存：旋转屏幕、切回前台重建 Activity 时不重复拉取；
 * - 失败自动重试一次（电视 Wi-Fi 偶尔抖一下）；
 * - 显式 User-Agent：服务端前面的 Cloudflare 会拦默认 UA；
 * - 响应大小上限 20 MB：防异常响应撑爆内存。
 */
object CatalogClient {
    const val CATALOG_URL = "https://apk.ciweihaha.win/catalog.json"

    private const val MAX_RESPONSE_BYTES = 20 * 1024 * 1024L // 20 MB
    private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 分钟
    private const val USER_AGENT = "TVAppStore/1.0.0 (Android)"

    @Volatile
    private var cached: CatalogResponse? = null

    @Volatile
    private var cachedAt = 0L

    @Throws(IOException::class)
    fun fetch(): CatalogResponse {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < CACHE_TTL_MS) return it }
        var lastError: IOException? = null
        repeat(2) { attempt ->
            try {
                val result = fetchOnce()
                cached = result
                cachedAt = System.currentTimeMillis()
                return result
            } catch (e: IOException) {
                lastError = e
                if (attempt == 0) {
                    try {
                        Thread.sleep(1000)
                    } catch (ignored: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }
        throw lastError ?: IOException("目录加载失败")
    }

    /** 清空缓存（测试用；正常使用不需要调）。 */
    fun clearCache() {
        cached = null
        cachedAt = 0L
    }

    @Throws(IOException::class)
    private fun fetchOnce(): CatalogResponse {
        val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code")
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                var total = 0L
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_RESPONSE_BYTES) throw IOException("目录过大")
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
            return CatalogParser.parse(json)
        } finally {
            connection.disconnect()
        }
    }
}
