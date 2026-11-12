package com.ciweihaha.tvappstore.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 极简图标加载器：内存缓存 + 线程池下载，不引入第三方图片库。
 */
object IconLoader {
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
            return connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }
}
