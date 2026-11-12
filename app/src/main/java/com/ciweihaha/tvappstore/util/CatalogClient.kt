package com.ciweihaha.tvappstore.util

import com.ciweihaha.tvappstore.model.CatalogResponse
import com.google.gson.Gson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 拉取目录 JSON。同步调用，调用方必须在后台线程执行。
 */
object CatalogClient {
    const val CATALOG_URL = "https://apk.ciweihaha.win/catalog.json"

    @Throws(IOException::class)
    fun fetch(): CatalogResponse {
        val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code")
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return Gson().fromJson(json, CatalogResponse::class.java)
                ?: throw IOException("目录为空")
        } finally {
            connection.disconnect()
        }
    }
}
