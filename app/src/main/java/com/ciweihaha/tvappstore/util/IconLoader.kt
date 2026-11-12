package com.ciweihaha.tvappstore.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 极简图标加载器：内存缓存 + 线程池下载，不引入第三方图片库。
 *
 * 图标只显示约 96dp：下载后按需采样缩小再进缓存（原图多大都不怕，
 * 不会因为一张几 MB 的大图 OOM）；单张图标超过 2 MB 直接放弃。
 */
object IconLoader {

    /** 单张图标最多 2 MB，超过就不要了（图标正常只有几十 KB）。 */
    private const val MAX_ICON_BYTES = 2 * 1024 * 1024

    /** 解码后图标最长边 256px（显示尺寸约 96dp，2 倍密度下足够清晰）。 */
    private const val MAX_ICON_PX = 256

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)

    fun load(imageView: ImageView, url: String?, placeholderRes: Int) {
        if (url.isNullOrBlank()) {
            imageView.setImageResource(placeholderRes)
            return
        }
        val cached = cache.get(url)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }
        imageView.setImageResource(placeholderRes)
        imageView.tag = url
        executor.execute {
            try {
                val bitmap = download(url) ?: return@execute
                cache.put(url, bitmap)
                imageView.post {
                    // 视图可能已被复用，只在 tag 仍是同一地址时才设置
                    if (imageView.tag == url) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                // 图标加载失败时保持占位图，不打扰用户
            }
        }
    }

    private fun download(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { readCapped(it, MAX_ICON_BYTES) }
                ?: return null
            // 先读尺寸再采样：大图按比例缩小，解码出的位图最多 256px
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_ICON_PX)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            connection.disconnect()
        }
    }

    /** 读满上限就返回 null（不继续读，避免大响应占内存）。 */
    private fun readCapped(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** 采样倍率（2 的幂）：让最长边不超过 maxPx。internal 以便单元测试。 */
    internal fun calculateSampleSize(width: Int, height: Int, maxPx: Int): Int {
        if (width <= 0 || height <= 0 || maxPx <= 0) return 1
        var sample = 1
        while (width / sample > maxPx || height / sample > maxPx) sample *= 2
        return sample
    }
}
