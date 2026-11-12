# TV 应用商店

免费 TV 应用商店：网页版商店 + Android 原生应用，共用同一份 APK 目录（JSON）。

- **网页版商店**：https://apk.ciweihaha.win/
- **Android 应用**：本仓库根目录的 Android 工程（包名 `com.ciweihaha.tvappstore`），见 [Android 应用](#android-应用)
- **目录**：https://apk.ciweihaha.win/catalog.json（按包名去重后，每包一个条目）
- **全量目录**：https://apk.ciweihaha.win/catalog-all.json（全部 APK，含被去重的旧版本，`kept` 字段标识）

目录已去重：**110 个应用**（364 个 APK 文件里去掉了 254 个重复），8 个分类。APK 来自两个开源收集库（均已镜像到 Oracle 日本免费 VM，通过 Cloudflare Tunnel 对外提供）：

- [ciweihaha/tvapp_store](https://github.com/ciweihaha/tvapp_store)
- [ciweihaha/tvapp-xyzzb](https://github.com/ciweihaha/tvapp-xyzzb)

## 网页版商店

静态单页（`index.html`，无构建步骤，纯 HTML/JS/CSS），打开即用：

- 按应用名、包名、文件名搜索
- 按分类筛选，8 个分类：K歌、娱乐、工具、影视、教育、未分类、电视直播、运动健身
- 按名称或大小排序
- 每款可直接下载，并可复制 SHA-256 校验值

部署即复制到服务器的 `/srv/tvapp/index.html`（与 `catalog.json` 同目录，页面通过相对路径 `catalog.json` 拉取目录）。无需构建、无需 Node。

```bash
scp index.html oracle-1:/srv/tvapp/index.html
```

## Android 应用

电视和普通平板都能装的应用商店 App。

### 功能（1.0.0）

- 启动后联网拉取 https://apk.ciweihaha.win/catalog.json，按分类展示应用列表（图标、名字、版本号、大小）
- 8 个分类：K歌、娱乐、工具、影视、教育、未分类、电视直播、运动健身
- 点一个应用 → DownloadManager 下载 APK → 校验 SHA-256 → 调系统安装程序安装
- SHA-256 对不上就删掉文件并提示，不安装
- 电视遥控器方向键 + OK 键可用（焦点有白边框高亮），平板触摸也可用
- 电视桌面入口：Leanback launcher + 横幅

### 构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

./gradlew testDebugUnitTest
# 单元测试（目录解析、SHA-256、文件名、错误码、DiffUtil 性能等）
```

需要 JDK 17。只出 debug 包，不签名、不发版。

CI（`.github/workflows/build-apk.yml`）：推送到 `main` 时自动构建，APK 保存为 Actions Artifacts（保留 7 天），在仓库的 Actions 页面下载；不发 Release、不打 tag。

### 技术参数

- 包名：`com.ciweihaha.tvappstore`，版本 1.0.0（versionCode 1）
- minSdk 21，targetSdk 35，compileSdk 36（core-ktx 1.17.0 要求至少 API 36 编译）；Kotlin + Gradle（Groovy DSL）
- AGP 9.5.1 + Gradle 9.9.1（AGP 9 内置 Kotlin，不再单独应用 kotlin-android 插件）
- 依赖只有 4 个：core-ktx、appcompat、recyclerview、gson
  - core-ktx 1.17.0、appcompat 1.7.1 是仍支持 minSdk 21 的最新版（1.18.0+ / 1.8.0 要求 minSdk 23）
  - recyclerview 1.4.0、gson 2.15.0 是 2026-11-12 最新稳定版
- 项目里不放任何 Token/密钥
- 测试说明见 [TESTING.md](TESTING.md)

### 后续可加（1.0.0 没做）

- 显示“已安装”状态、版本对比提示更新
- 下载断点续传、应用内搜索
- 商店自身更新提醒

## 目录 schema（catalog.json）

```jsonc
{
  "generated_at": "2026-11-12T04:47:36Z",   // UTC，目录生成时间
  "base_url": "https://apk.ciweihaha.win",
  "stats": {
    "total_apks": 364,          // 扫描到的 APK 总数
    "unique_packages": 110,     // 去重后唯一包数（= apps.length）
    "dropped_duplicates": 254,  // 被去重的重复包数
    "aapt_failed": 2,           // aapt 解析失败的 APK 数（仍收录，包名为空）
    "categories": { "影视": 36, "电视直播": 29, "工具": 11, "未分类": 9, "娱乐": 8, "K歌": 7, "教育": 6, "运动健身": 4 },
    "sources": { "tvapp_store": 80, "tvapp-xyzzb": 30 }
  },
  "categories": ["K歌", "娱乐", "工具", "影视", "教育", "未分类", "电视直播", "运动健身"],
  "apps": [ { /* 见下 */ } ]
}
```

每个 app 条目：

| 字段 | 类型 | 说明 |
|---|---|---|
| `package` | string \| null | Android 包名（aapt 解析失败时为 null） |
| `label` | string | 应用名（aapt 的 application-label；解析失败时用文件名） |
| `versionName` | string | 版本名（解析失败时为空字符串） |
| `versionCode` | int | 版本号（解析失败时为 0） |
| `size` | int | 文件字节数 |
| `sha256` | string | 文件 SHA-256（下载后校验用） |
| `category` | string | 统一分类（见下表） |
| `sourceCategory` | string | 原始子目录名（仅当存在时） |
| `source` | string | 来源库：`tvapp_store` 或 `tvapp-xyzzb` |
| `url` | string | APK 直链（URL 编码后，可直接下载） |
| `file` | string | 原始文件名 |
| `icon` | string | 图标 URL（仅当按应用名匹配到 tvapp_store/icons 里的图标时才有） |

`catalog-all.json` 与 `catalog.json` 结构相同，但包含全部 APK，每个条目多一个 `kept` 布尔值（`true` = 该包名保留的最高版本，`false` = 被去重的旧版本/重复变体）。

### 分类映射

分类取自各库的子目录名，两个库的命名不同，生成脚本统一映射为中文类目：

| tvapp_store 子目录 | tvapp-xyzzb 子目录 | 统一分类 |
|---|---|---|
| `live` | `电视直播` | 电视直播 |
| `video` | `影视` | 影视 |
| `ktv` | `K歌` | K歌 |
| `entertainment` | — | 娱乐 |
| `education` | — | 教育 |
| `movement` | — | 运动健身 |
| `utility` | — | 工具 |
| — | `TVBox` | TVBox |
| — | （根目录的 APK，无子目录） | 未分类 |

### 去重规则

同一包名只保留一个条目：**最高 `versionCode` 胜出**；`versionCode` 相同则优先 `tvapp_store`，再按文件更大、文件名字典序决定（保证结果可复现）。`aapt` 解析失败（无包名）的 APK 不参与去重，全部保留。

> 注意：TVBox 系列构建的 `versionCode` 全是 1，版本号无法区分新旧，因此去重时按平局规则取了 `tvapp_store` 的版本；去重后目录里没有单独的 `TVBox` 类目（存活的 TVBox 条目分类为 `影视`）。需要全部 TVBox 变体（java/python、arm64/armeabi、海信版）时请读 `catalog-all.json`，按 `category == "TVBox"` 筛选。

### 目录接入说明（给客户端）

1. 启动时拉取 `catalog.json`（约几百 KB），本地缓存；`generated_at` 变化时刷新。
2. 搜索：对 `label` / `package` / `file` 做包含匹配（建议转小写后比较）。
3. 分类筛选：用 `category` 字段；类目列表取顶层 `categories`。
4. 下载：直接用 `url`；下载后用 `sha256` 校验完整性。
5. 图标：`icon` 字段存在时加载，不存在时用首字占位。
6. 需要全部版本（含旧版）时读 `catalog-all.json`，按 `kept` 区分。

## 仓库结构

```
README.md                  本文件
index.html                 网页版商店（无构建步骤的静态单页，纯 HTML/JS/CSS）
scripts/gen-catalog.py     目录生成脚本（在服务器上运行，见下）
.github/workflows/         CI：推送到 main 时构建 Android debug APK
app/                       Android 应用工程
build.gradle               根构建脚本（Groovy DSL）
settings.gradle            工程设置
gradlew / gradlew.bat      Gradle Wrapper
```

Android 应用源码结构：

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
└── res/
    ├── layout/                  # 主界面、应用卡片、分类小块
    ├── drawable/                # 焦点样式、占位图标、电视横幅
    ├── values/                  # 文案、颜色、主题、尺寸
    └── xml/file_paths.xml       # FileProvider 路径
app/src/test/java/com/ciweihaha/tvappstore/
└── CatalogParserTest / HashUtilsTest / FileNameUtilsTest /
    DownloadErrorsTest / FormatUtilsTest / AppDiffCallbackTest / IconLoaderTest
```

## 重新生成目录（服务器操作）

在 oracle-1（Tailscale 内，`ubuntu@100.107.219.60`）上：

```bash
# 首次需要安装 aapt（apt 需强制 IPv4）
sudo apt-get update -o Acquire::ForceIPv4=true
sudo apt-get install -y -o Acquire::ForceIPv4=true aapt

# 生成目录（约 12.4GB 的 APK，sha256 + aapt 解析需几分钟）
python3 scripts/gen-catalog.py --root /srv/tvapp --out-dir /srv/tvapp
```

脚本只读写 `/srv/tvapp` 下的文件与 `/tmp`，不改动 nginx 配置与服务器上其他服务。

## 许可证

本仓库的商店代码（`index.html`、Android 应用、生成脚本）以 MIT 许可证开源。APK 本身的版权归各应用作者所有，本仓库仅提供目录与下载链接，不重新分发修改后的应用。
