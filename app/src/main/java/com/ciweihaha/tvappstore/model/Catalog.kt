package com.ciweihaha.tvappstore.model

import com.google.gson.annotations.SerializedName

/**
 * 目录接口 https://apk.ciweihaha.win/catalog.json 的数据结构。
 * 服务端已去重：110 个应用（364 个 APK 文件里去掉了 254 个重复）。
 */
data class CatalogResponse(
    @SerializedName("generated_at") val generatedAt: String?,
    @SerializedName("base_url") val baseUrl: String?,
    val categories: List<String>?,
    val apps: List<AppInfo>?
)

data class AppInfo(
    // "package" 是 Kotlin 关键字，用 @SerializedName 映射
    @SerializedName("package") val packageName: String,
    val label: String?,
    val versionName: String?,
    val versionCode: Long?,
    val size: Long?,
    val sha256: String?,
    val category: String?,
    val source: String?,
    val url: String?,
    val file: String?,
    val icon: String?
) {
    /** 显示用名字：label 为空时退回包名 */
    val displayName: String
        get() = label?.takeIf { it.isNotBlank() } ?: packageName

    /** 下载保存用的文件名 */
    val downloadFileName: String
        get() = file?.takeIf { it.isNotBlank() } ?: "$packageName.apk"
}
