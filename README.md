# TV 应用商店

电视和普通平板都能装的应用商店 App。应用目录来自日本服务器上的 TV 应用商店网站（https://apk.ciweihaha.win），服务端已去重：110 个应用（364 个 APK 文件里去掉了 254 个重复）。

## 功能（1.0.0）

- 启动后联网拉取 https://apk.ciweihaha.win/catalog.json，按分类展示应用列表（图标、名字、版本号、大小）
- 8 个分类：K歌、娱乐、工具、影视、教育、未分类、电视直播、运动健身
- 点一个应用 → DownloadManager 下载 APK → 校验 SHA-256 → 调系统安装程序安装
- SHA-256 对不上就删掉文件并提示，不安装
- 电视遥控器方向键 + OK 键可用（焦点有白边框高亮），平板触摸也可用
- 电视桌面入口：Leanback launcher + 横幅

## 构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

./gradlew testDebugUnitTest
# 单元测试（目录解析、SHA-256、文件名、错误码、DiffUtil 性能等）
```

需要 JDK 17。只出 debug 包，不签名、不发版。

## 结构

```
app/src/main/
├── AndroidManifest.xml          # Leanback 入口、FileProvider、权限
├── java/com/ciweihaha/tvappstore/
│   ├── MainActivity.kt          # 主界面：分类 + 应用网格
│   ├── model/Catalog.kt         # 目录 JSON 数据类
│   ├── util/CatalogClient.kt    # 拉取 catalog.json（缓存、重试、大小上限）
│   ├── util/CatalogParser.kt    # 目录解析：逐条容错，坏条目只跳过那一条
│   ├── util/DownloadErrors.kt   # 下载错误码 → 中文提示
│   ├── util/DownloadHelper.kt   # DownloadManager 下载 + 后台 SHA-256 校验 + 安装
│   ├── util/FileNameUtils.kt    # 下载文件名：防路径穿越、防同名覆盖
│   ├── util/FormatUtils.kt      # 文件大小格式化
│   ├── util/HashUtils.kt        # SHA-256 计算
│   ├── util/IconLoader.kt       # 图标下载 + 内存缓存 + 采样缩小（无第三方图片库）
│   └── ui/
│       ├── AppAdapter.kt        # 应用网格（ListAdapter + DiffUtil）
│       └── CategoryAdapter.kt   # 分类横条
├── res/
│   ├── layout/                  # 主界面、应用卡片、分类小块
│   ├── drawable/                # 焦点样式、占位图标、电视横幅
│   ├── values/                  # 文案、颜色、主题、尺寸
│   └── xml/file_paths.xml       # FileProvider 路径
app/src/test/java/com/ciweihaha/tvappstore/
└── CatalogParserTest / HashUtilsTest / FileNameUtilsTest /
    DownloadErrorsTest / FormatUtilsTest / AppDiffCallbackTest / IconLoaderTest
```

## 技术参数

- 包名：com.ciweihaha.tvappstore，版本 1.0.0（versionCode 1）
- minSdk 21，targetSdk 35，compileSdk 36（core-ktx 1.17.0 要求至少 API 36 编译）；Kotlin + Gradle（Groovy DSL）
- AGP 9.5.1 + Gradle 9.9.1（AGP 9 内置 Kotlin，不再单独应用 kotlin-android 插件）
- 依赖只有 4 个：core-ktx、appcompat、recyclerview、gson
  - core-ktx 1.17.0、appcompat 1.7.1 是仍支持 minSdk 21 的最新版（1.18.0+ / 1.8.0 要求 minSdk 23）
  - recyclerview 1.4.0、gson 2.15.0 是 2026-11-12 最新稳定版
- 项目里不放任何 Token/密钥
- 测试说明见 [TESTING.md](TESTING.md)

## 后续可加（1.0.0 没做）

- 显示“已安装”状态、版本对比提示更新
- 下载断点续传、应用内搜索
- 商店自身更新提醒
