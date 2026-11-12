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
```

需要 JDK 17。只出 debug 包，不签名、不发版。

## 结构

```
app/src/main/
├── AndroidManifest.xml          # Leanback 入口、FileProvider、权限
├── java/com/ciweihaha/tvappstore/
│   ├── MainActivity.kt          # 主界面：分类 + 应用网格
│   ├── model/Catalog.kt         # 目录 JSON 数据类
│   ├── util/CatalogClient.kt    # 拉取 catalog.json
│   ├── util/IconLoader.kt       # 图标下载 + 内存缓存（无第三方图片库）
│   ├── util/DownloadHelper.kt   # DownloadManager 下载 + SHA-256 校验 + 安装
│   └── ui/
│       ├── AppAdapter.kt        # 应用网格
│       └── CategoryAdapter.kt   # 分类横条
└── res/
    ├── layout/                  # 主界面、应用卡片、分类小块
    ├── drawable/                # 焦点样式、占位图标、电视横幅
    ├── values/                  # 文案、颜色、主题、尺寸
    └── xml/file_paths.xml       # FileProvider 路径
```

## 技术参数

- 包名：com.ciweihaha.tvappstore，版本 1.0.0（versionCode 1）
- minSdk 21，targetSdk 34，compileSdk 34；Kotlin + Gradle（Groovy DSL）
- 依赖只有 4 个：core-ktx、appcompat、recyclerview、gson
- 项目里不放任何 Token/密钥

## 后续可加（1.0.0 没做）

- 显示“已安装”状态、版本对比提示更新
- 下载断点续传、应用内搜索
- 商店自身更新提醒
