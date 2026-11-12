package com.ciweihaha.tvappstore.util

import com.ciweihaha.tvappstore.model.AppInfo
import com.ciweihaha.tvappstore.model.CatalogResponse
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException

/**
 * 目录 JSON 解析：逐条容错。
 *
 * 旧写法是 Gson 一次性反序列化整个 CatalogResponse：一处坏数据
 * （某条缺 package、size 写成了字符串、某条根本不是对象）就让
 * 整个商店加载失败。这里逐条解析，坏条目只跳过那一条，其余照常显示。
 * 纯 JVM 逻辑，可单元测试。
 */
object CatalogParser {

    /** 应用条数上限：防异常目录撑爆内存（2900 个的扩容目录也远到不了这个数）。 */
    private const val MAX_APPS = 10000

    @Throws(IOException::class)
    fun parse(json: String): CatalogResponse {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw IOException("目录格式错误", e)
        }
        val categories = root.optArray("categories")?.mapNotNull { element ->
            runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        val apps = mutableListOf<AppInfo>()
        val appsArray = root.optArray("apps")
        if (appsArray != null) {
            for (element in appsArray) {
                if (apps.size >= MAX_APPS) break
                if (!element.isJsonObject) continue
                parseApp(element.asJsonObject)?.let { apps.add(it) }
            }
        }
        return CatalogResponse(
            generatedAt = root.optString("generated_at"),
            baseUrl = root.optString("base_url"),
            categories = categories,
            apps = apps
        )
    }

    /** 单条应用解析：package 或 url 缺失/为空时返回 null（调用方跳过这条）。 */
    fun parseApp(obj: JsonObject?): AppInfo? {
        if (obj == null) return null
        val packageName = obj.optString("package")?.takeIf { it.isNotBlank() } ?: return null
        val url = obj.optString("url")?.takeIf { it.isNotBlank() } ?: return null
        return AppInfo(
            packageName = packageName,
            label = obj.optString("label"),
            versionName = obj.optString("versionName"),
            versionCode = obj.optLong("versionCode"),
            size = obj.optLong("size"),
            sha256 = obj.optString("sha256"),
            category = obj.optString("category"),
            source = obj.optString("source"),
            url = url,
            file = obj.optString("file"),
            icon = obj.optString("icon")
        )
    }

    private fun JsonObject.optArray(name: String): JsonArray? {
        return try {
            val element = get(name) ?: return null
            if (element.isJsonArray) element.asJsonArray else null
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.optString(name: String): String? {
        return try {
            val element = get(name) ?: return null
            if (element.isJsonNull || !element.isJsonPrimitive) return null
            element.asString
        } catch (e: Exception) {
            null
        }
    }

    /** 数字字段容错：整数、浮点、数字字符串都能读，读不出返回 null。 */
    private fun JsonObject.optLong(name: String): Long? {
        return try {
            val element = get(name) ?: return null
            if (element.isJsonNull || !element.isJsonPrimitive) return null
            val primitive = element.asJsonPrimitive
            when {
                primitive.isNumber -> {
                    try {
                        primitive.asLong
                    } catch (e: NumberFormatException) {
                        primitive.asDouble.toLong()
                    }
                }
                primitive.isString -> {
                    val text = primitive.asString.trim()
                    text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
