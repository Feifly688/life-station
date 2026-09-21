# 翡栖 (FeiQi)

个人生活记录 App —— 把日常的记账、日程、健康与书影音收藏，收进一个安静的小站。

> 应用名：**翡栖** ｜ 包名：`com.feiqi` ｜ 版本：**v1.4.0** (versionCode 60)

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

首页底部每天展示一条语录，规则是「按日期取模轮换」——同一天固定同一条，跨零点自然切换。语录集为**远程 + 缓存 + 内置**三层：

| 层级 | 位置 | 说明 |
| --- | --- | --- |
| 远程（优先） | 仓库根目录 [`quotes.json`](quotes.json) | App 启动时按 **7 天**周期检查更新。改语录只需在 GitHub 上编辑此文件（手机端也能改），**不需要自建服务器** |
| 本地缓存 | App 私有目录 `quotes_cache.json` | 上一次成功拉取的结果，离线或更新失败时使用 |
| 内置兜底 | `FeiQi/.../utils/Quotes.kt` | 142 条（已去重），任何情况下都保证有内容可展示 |

- **手动更新**：设置 → 内容 → 语录集 →「立即更新」（不受 7 天周期限制）
- **文件格式**：`{"version":1,"updatedAt":"2026-09-21","quotes":[{"text":"…","author":"…（可选）"}]}`
- **校验规则**：解析后条数少于 10 条、或全部条目为空，都会被判定为无效并**继续沿用现有语录**，不会被坏数据顶掉

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
