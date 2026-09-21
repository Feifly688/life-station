# 栖 · 生活工作台

一个面向泛生活人群的集成式生活工作台，安卓 APP，单 HTML 文件内联全部 CSS/JS/图标，本地存储，离线运行，不依赖任何服务器与外部组件。

## 包含模块

- **今日生活指数** —— 问候、生活指数三宫格、心情记录、今日日程/待买/习惯快照、本月概览
- **记账理财** —— 收支记录、月度预算（含分类预算）、6 月对比柱图、消费结构环形图、分类筛选、Excel(CSV) 导出
- **习惯健康** —— 新增/删除习惯，支持勾选 / 计数 / 数值三种打卡方式，30 天热力图，连续天数
- **减脂健身** —— 体重体脂记录、7 日均线趋势图、BMI、目标进度、每日热量缺口、达成预计天数、可自定义周训练计划
- **日程统筹** —— 月历视图、按日添加/完成日程
- **待买清单** —— 添加/勾选、分类、预估花费汇总、已购归档
- **书影音收藏** —— 状态/星级/短评、封面墙与列表双视图、年度统计

## 数据与安全

- 所有数据存储于本机 `localStorage`，不上传任何服务器
- 每次写入前自动留一份 `_bak` 备份；数据损坏时自动回滚并提示
- 新增满 20 条记录自动提醒备份
- 支持导出 JSON 备份、从文件恢复、重置演示数据、清空全部

## 构建 APK

### 方式一：Android Studio（推荐，最简单）

1. 用 Android Studio 打开 `QixiLife` 目录
2. 等待 Gradle 同步完成（自动下载依赖）
3. `Build → Build Bundle(s)/APK(s) → Build APK(s)`
4. 产物在 `app/build/outputs/apk/debug/app-debug.apk`

### 方式二：命令行（需 JDK 17 + Android SDK）

```bash
cd QixiLife
gradle assembleDebug
```

产物同上。Release 版需自行配置签名 keystore；未配置签名时 Gradle 只能生成未签名 APK。

## 技术说明

- `app/src/main/assets/index.html` 即应用本体，可独立在任意浏览器运行
- `MainActivity.java` 用 WebView 加载该 HTML，并注入 `Android` JS 接口用于导出文件、注册文件选择器用于导入备份
- 最低支持 Android 8.0 (API 26)
