# 翡栖 (FeiQi)

个人生活记录 App —— 把日常的记账、日程、健康与书影音收藏，收进一个安静的小站。

> 应用名：**翡栖** ｜ 包名：`com.feiqi` ｜ 版本：**v1.8.0** (versionCode 79)

<br>

## 功能模块

底部五个 Tab：

| 模块 | 说明 |
| --- | --- |
| 首页 | 当日概览 |
| 记账 | 收支记录与分类统计 |
| 日程 | 待办清单、提醒、每日重复清单 |
| 健康 | 习惯打卡等健康记录 |
| 书影音 | 书 / 影 / 音 收藏记录 |

<br>

## 技术栈

- **Kotlin + Jetpack Compose**（Compose BOM `2024.06.00`，Material3）
- **Room** 本地数据库、**DataStore** 偏好设置
- **Navigation Compose**、**Gson**
- 单 Activity + 底部导航
- Gradle **Kotlin DSL**（AGP `8.5.2` / Kotlin `1.9.24` / KSP），JDK 17

<br>

## 目录结构

```
FeiQi/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/feiqi/   # 源码（data / ui / utils 分层）
│       ├── main/res/              # 资源
│       └── test/java/com/feiqi/   # 纯 JVM 单元测试
├── build.gradle.kts               # 顶层构建脚本
├── settings.gradle.kts
└── gradle/
```

<br>

## 语录集（首页底部）

首页底部每天展示一条语录，规则是「按日期取模轮换」——同一天固定同一条，跨零点自然切换。语录集由**两层集合**长期累积：

| 集合 | 来源 | 更新方式 |
| --- | --- | --- |
| **基础集** | 内置 142 条 或 仓库根目录 [`quotes.json`](quotes.json)（人工精选） | 距上次成功 ≥ **7 天**时静默更新（App 启动时机） |
| **自动收集集** | [一言](https://hitokoto.cn)「文学 + 诗词」类别随机取句 | **每周首次打开 App** 静默收集 **5~10 条**，去重后追加（上限 400 条） |

- **全自动、无入口**：设置里没有手动更新开关。触发条件是**本周尚未更新过**（不限定周几：周一没打开就在本周首次打开时补做；本周已收集过则后续打开不做任何请求）。失败静默（只写日志）、不打扰用户；若本周首次尝试联网失败（一条都没取到），本周不算完成——稍后再打开（间隔 ≥3 小时）会自动重试。
- **去重规则**：只保留字母/数字/汉字后比对（丢掉空白与全部标点），所以「慢慢来，比较快。」「慢慢来比较快」「慢慢来, 比较快。」视为同一条；既与现有集合比，也在同一批内部比。
- **不丢内容**：基础集远程更新时**不会**清掉已收集的语录，两者合并后展示。
- **离线兜底**：远程与收集都失败时，仍用本地缓存 / 内置 142 条，首页照常有内容。
- 缓存文件：App 私有目录 `quotes_cache.json`（`base` + `collected` + 两个时间戳），旧格式自动兼容。

<br>

<br>

## 构建

环境要求：**JDK 17**、**Android SDK**（compileSdk 35）。

- 用 **Android Studio** 打开 `FeiQi/` 直接运行 / 打包即可；
- 或用本地 Gradle 执行 `:app:assembleDebug`，产物位于 `app/build/outputs/apk/debug/app-debug.apk`。
- **发布构建**：执行 `:app:assembleRelease`。若在 `FeiQi/keystore.properties`（**不提交**）中配置了发布密钥，即用发布密钥签名；未配置时自动回退 debug 签名，便于他人直接构建。详见 [SECURITY.md](SECURITY.md)。

<br>

## 安全与校验

安全基线、密钥与签名文件保管、备份与应急响应规范见 **[SECURITY.md](SECURITY.md)**。

下载安装包后请核对完整性（每个 Release 的说明中都附有 SHA-256）：

```bash
sha256sum feiqi-vX.Y.Z.apk
```

<br>

## 分支与发布

本项目采用**单一分支 + Releases** 的发布方式：

| 位置 | 内容 |
| --- | --- |
| `main` 分支 | 项目源代码（不含任何构建产物） |
| **Releases** | 每个版本的 APK 安装包，见右侧 Releases 页 |

**获取最新 APK**：打开本仓库的 [Releases](../../releases/latest) 页面，下载 `feiqi-vX.Y.Z.apk` 安装即可。

> 说明：GitHub 会过滤 Release 附件名中的非 ASCII 字符，故 APK 附件使用 ASCII 文件名
> `feiqi-vX.Y.Z.apk`，而 Release 标题仍为中文「翡栖 vX.Y.Z」。

仓库根目录另附各功能页参考图。本项目为**个人自用 App**，非商业用途。
