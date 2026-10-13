# 健身模块说明 FITNESS MODULE（v1.15.0 新增）

> 独立说明文件，**不改动原 `README.md` 结构**。
> 入口：**健康 Tab → 「30天居家训练」卡片**（作为健康页的二级页，不新增底部导航 Tab）。

## 一、模块简介

面向居家场景的 **30 天徒手训练计划**：以 7 天为一个循环（胸+三头 / 肩+腹 / 背+二头 / 腿+臀 / 胸+肩 / 腹+手臂 / 恢复），
每次训练组合两个身体部位，逐日推进并**打卡记录**，用进度与连续天数提升自律感。

- 纯本地存储（Room），无网络、无账号、无排行榜
- 训练计划是**静态数据**（`WorkoutPlan`），完成状态由打卡记录派生（计划里**没有** completed 字段，避免状态双写）
- 视觉沿用现有规范：卡片圆角（`FeiQiRadius`）、Material3 配色（`Primary` / `CardAmber` / `CardGreen`）、200ms 统一动效（`FeiQiMotion.normal`）

## 二、新增功能

| 功能 | 说明 |
| --- | --- |
| 计划总览 | `Day x / 30`、完成次数、**进度条**（200ms 缓动）、连续训练天数 |
| 今日训练卡 | 部位标签（🔥 胸 + 三头）、描述、动作清单（组数 × 次数/时长、备注）、「开始训练」按钮 |
| 训练详情页 | 日期 + 部位；每个动作一张卡（组数/次数/备注/完成态）；逐个「完成本组」（带缩放与对勾动效）；全部完成后底部「完成今日训练」可点 |
| 打卡反馈 | 打卡后底部弹出庆祝横幅（淡入 + 上移 200ms），并有 Snackbar 提示 |
| 历史记录 | 30 天网格，已完成显示对勾；点击已打卡的一天可**取消打卡** |
| 数据持久化 | Room 新表 `workout_records`（`planId + dayNumber` 唯一索引，保证同一天只打卡一次） |
| 数据库迁移 | `v10 → v11`：**只新建表**，既有表（待办/记账/习惯/书影音）零改动，用户数据不受影响 |

## 三、修改文件

**新增**
| 文件 | 作用 |
| --- | --- |
| `data/model/WorkoutPlan.kt` | 30 天计划数据（`Exercise` / `WorkoutPlan` / `WORKOUT_PLANS`）+ 进度、连续天数纯函数 |
| `data/entity/WorkoutRecordEntity.kt` | 打卡记录实体 |
| `data/database/WorkoutDao.kt` | 打卡读写（upsert / 查询 / 删除） |
| `data/repository/WorkoutRepository.kt` | 仓库：打卡、取消打卡、已完成天数流 |
| `ui/health/WorkoutViewModel.kt` | 进度 / 连续天数 / 打卡动作 |
| `ui/health/WorkoutCards.kt` | 进度卡、动作卡、完成徽标、历史 chip、部位标签 |
| `ui/health/WorkoutScreen.kt` | 健身主页（二级页） |
| `ui/health/WorkoutDetailScreen.kt` | 训练详情 + 逐组打卡 + 庆祝动效 |
| `app/src/test/.../WorkoutPlanTest.kt` | 计划完整性与进度/连续天数派生（10 项单测） |

**修改**
| 文件 | 改动 |
| --- | --- |
| `data/database/FeiQiDatabase.kt` | 版本 10 → 11；注册 `WorkoutRecordEntity` 与 `workoutDao()`；新增 `MIGRATION_10_11` |
| `FeiQiApplication.kt` | 注册迁移；注入 `workoutRepository` |
| `ui/MainActivity.kt` | 创建并传入 `WorkoutViewModel` |
| `ui/health/HealthScreen.kt` | 新增「30天居家训练」入口卡（含进度摘要）+ 健身主页/详情二级页覆盖 |
| `res/values/strings.xml` | 健身文案 + 版本号 |
| `app/build.gradle.kts` | 版本 v1.15.0（code 95） |

> 待办、待办集、专注、统计、书影音、记账等既有模块**逻辑与页面均未改动**。

## 四、使用方法

1. 打开 App → 底部 **健康** Tab；
2. 点击顶部「**30天居家训练**」卡片（副标题显示 `Day x/30 · 已完成 n 次 · 连续 m 天`）；
3. 健身主页查看进度条与今日训练，点「**开始训练**」进入详情页；
4. 逐个动作点「**完成本组**」（按钮变为「已完成」并显示对勾）；
5. 全部动作完成后点「**完成今日训练**」→ 打卡成功，弹出庆祝横幅；
6. 回到主页，底部「历史记录」中已完成的 Day 带对勾；**点击已打卡的 Day 可取消打卡**。

### 计划编排

- 第 1–7 天为指定内容：胸+三头 / 肩+腹 / 背+二头 / 腿+臀 / 胸+肩 / 腹+手臂 / 恢复训练
- 第 8–30 天按同一 7 天循环续排（后续可替换为更多计划，计划以 `planId` 区分）

## 五、验证与测试

- **自动**：`WorkoutPlanTest` 覆盖 30 天完整性、首周动作清单、循环续排、进度派生（空/中途/全完成/越界/去重）、连续天数（连续/今天未练/断档）；全量 `:app:testDebugUnitTest` 通过。
- **迁移**：`MIGRATION_10_11` 只建表与索引，且索引与实体 `@Entity(indices=…)` 严格一致（Room 打开时逐项比对，不一致会启动即崩）。
- **手动**：健康页入口 → 主页 → 详情 → 逐组完成 → 打卡 → 返回主页看进度与历史 → 取消打卡。