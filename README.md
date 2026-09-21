# 翡栖 (FeiQi)

个人生活记录 App —— 把日常的记账、日程、健康与书影音收藏，收进一个安静的小站。

> 应用名：**翡栖** ｜ 包名：`com.feiqi` ｜ 版本：**v1.1.2** (versionCode 51)

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

<br>

## 关于本仓库

- 仓库根目录另存有近期构建产物 `翡栖-vX.Y.Z.apk` 与各功能页参考图。
- 本项目为**个人自用 App**，非商业用途。
