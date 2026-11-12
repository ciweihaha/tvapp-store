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

    // 注意：packageName 在类型上是非空的，但 Gson 反序列化不检查 Kotlin 非空；
    // 目录解析统一走 CatalogParser，它会跳过缺 package 的条目，保证这里不为 null。
    // 下载文件名不要直接用 file 字段（可能含路径分隔符/同名），见 FileNameUtils。
}
