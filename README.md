# 翡栖 (FeiQi)

个人生活记录 App —— 把日常的记账、日程、健康与书影音收藏，收进一个安静的小站。

> 应用名：**翡栖** ｜ 包名：`com.feiqi` ｜ 版本：**v1.3.0** (versionCode 58)

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
