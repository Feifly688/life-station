# 翡栖 App 健身模块 v2.0 — 第 2 阶段（用户自建训练计划）

- 版本：**v1.17.0**（versionCode 97）
- 上一阶段：[`FITNESS_MODULE_V2_STAGE1.md`](./FITNESS_MODULE_V2_STAGE1.md)（四层数据模型，本次直接复用，**未改动表结构**）
- 阶段 1 文档：[`FITNESS_MODULE.md`](./FITNESS_MODULE.md)（30 天静态计划 + 打卡）

---

## 一、本阶段新增功能

| # | 功能 | 说明 |
|---|---|---|
| 1 | **多计划训练** | 健身主页新增「计划切换条」，官方计划与用户计划可自由切换；进度 / 今日训练 / 历史打勾网格**全部跟随当前计划** |
| 2 | **我的训练计划** | 新页面 `MyWorkoutPlansScreen`：官方计划区+ 我的计划区 + 空态引导 |
| 3 | **创建训练计划（4 步向导）** | 新页面 `CreateWorkoutPlanScreen`：基础信息 → 训练周期 → 训练频率 → 训练部位 |
| 4 | **自动排布** | 按「周期 × 每周频率」自动生成训练日 / 休息日，部位按用户勾选**轮转**分配 |
| 5 | **老打卡兼容** | `home30`（旧 id）与 `home_beginner_30`（新官方 id）完成记录**合并计算**，升级后进度不清零 |
| 6 | **自定义周期进度派生** | `computeWorkoutProgress` / `computeStreak` 支持任意总天数（不再硬编码 30） |

**本阶段不含**：删除计划、编辑计划、逐日手调训练动作、动作图片资源。

---

## 二、页面流程

```
健康 Tab
 └─ WorkoutEntryCard（「30天居家训练」+ 进度摘要）
     └─ 健身主页 WorkoutScreen
         ├─ 计划切换条：〔30天居家爆炸训练〕〔7天胸肩循环〕… ＋「我的计划 ›」
         ├─ 进度卡（按当前计划派生：Day x/N · 已完成 m 次 · 连续 k 天）
         ├─ 今日训练卡 → 训练详情 WorkoutDetailScreen → 打卡（写入当前 planId）
         ├─ 历史打勾网格（列数按当前计划总天数自适应）
         │
         ├─►「我的计划 ›」MyWorkoutPlansScreen
         │      ├─ 官方计划区（不可编辑）
         │      ├─ 我的计划区（高亮卡片：名称 / 目标徽章 / N天·我的计划 / 创建于 / 开始训练）
         │      ├─ 空态：插画 + 「创建你的第一个训练计划」
         │      └─「+ 创建计划」→ CreateWorkoutPlanScreen
         │
         └─◄ 创建向导（4 步，可后退）
                Step1 基础信息：名称（≤20 字，实时计数）+ 目标 2×2 单选
                        💪 增肌   🔥 减脂
                        ✨ 塑形   🌱 保持
                Step2 训练周期：〔7〕〔14〕〔30〕 + 自定义天数（1~365）
                Step3 训练频率：〔3〕〔4〕〔5〕〔6〕天/周
                        + 一周排布预览（周一~周日「练 / 休」）
                        + 全周期训练日总数提示
                Step4 训练部位：6 大类多选
                        💪 胸部(胸+三头)  🔙 背部(背+二头)
                        🤸 肩部(肩)          🦵 腿部(腿+臀)
                        💥 手臂(二头+三头)  🔥 腹部(腹)
                        + 计划概览卡（周期 N 天 / 训练 m 天 / 部位清单）
                完成计划 → 写入四表 → 自动切换到新计划并返回主页
```

### 覆盖层状态机

`HealthScreen` 内的健身二级页由**单一枚举**驱动（替代原先的两个布尔量）：

```kotlin
internal enum class WorkoutOverlay { None, Home, MyPlans, Create }
```

`workoutDetailPlan`（训练详情）独立于该枚举，作为最顶层覆盖渲染，返回时枚举状态原样保留。

---

## 三、数据变化

### 3.1 表结构：**无变更**（仍为 v12）

本阶段**不新增字段、不新增表、不写迁移**。四张表在阶段 1 已就绪：

| 表 | 用途 | 本阶段 |
|---|---|---|
| `workout_plans` | 计划头 | 写入 `USER_CREATED` 记录 |
| `workout_days` | 训练日 | 按周期写入 N 条 |
| `exercises` | 动作库 | **只读复用**（按部位取，不新增） |
| `workout_exercises` | 天↔动作关联 | 写入关联行 |
| `workout_records` | 打卡事实 | 写入时按 planId 隔离 |

### 3.2 新写入的数据

**① `workout_plans`（1 条）**

| 字段 | 值 |
|---|---|
| `planId` | `user_` + UUID 前 8 位（如 `user_a1b2c3d4`） |
| `name` | 用户输入（trim，≤20 字） |
| `description` | `每周训练 N 天 · 共 M 个训练日`（自动生成） |
| `type` | `USER_CREATED` |
| `goal` | 用户选择（BULK / CUT / SHAPE / MAINTAIN） |
| `cycleDays` | 周期天数 |
| `createdTime` | `System.currentTimeMillis()` |

**② `workout_days`（N 条）**

| 字段 | 训练日 | 休息日 |
|---|---|---|
| `title` | 部位串，如 `胸 + 三头` | `休息` |
| `bodyParts` | `胸 + 三头` | 空串 |
| `description` | `第 N 天 · 胸 + 三头` | `休息日 · 拉伸放松` |

**③ `workout_exercises`（每训练日最多 6 条）**

`sets = 3`、`reps = "12次"`、`duration = 0`、`restTime = 90`、动作来自动作库按部位筛选。

### 3.3 排布算法（`WorkoutPlanBuilder`，纯函数）

**部位映射**（用户看到大类，动作库保持细粒度）：

| 用户选择 | 展示串 | 动作库部位 |
|---|---|---|
| 胸部 | 胸 + 三头 | 胸、三头 |
| 背部 | 背 + 二头 | 背、二头 |
| 肩部 | 肩 | 肩 |
| 腿部 | 腿 + 臀 | 腿、臀 |
| 手臂 | 二头 + 三头 | 二头、三头 |
| 腹部 | 腹 | 腹 |

**训练日排布**：

```
trainWeekdays = (0 until freq).map { it * 7 / freq + 1 }
  freq=3 → [1, 3, 5]  freq=4 → [1, 2, 4, 6]  freq=6 → [1..6]
limit = min(cycleDays, cycleDays * freq / 7 + cycleDays % 7)
第 d 天是训练日 ⟺ ((d-1) % 7 + 1) ∈ trainWeekdays 且未超 limit
```

**部位轮转**：第 k 个训练日取 `categories[k % categories.size]`，各部位均匀出现。

**动作挑选**：按 `detailParts` 批量取动作库 → 按名去重 → 难度升序（初级优先）→ 取前 6 个。

### 3.4 老打卡兼容（关键：数据不回归）

官方计划 v2 换了 id，历史记录仍在 `home30` 下。处理方式：

| 层面 | 处理 |
|---|---|
| 数据库 | **不改历史记录、不做数据迁移** |
| Repository | `WorkoutPlanSeed.readAliases(planId)` 展开别名；官方计划读取 `home_beginner_30` ∪ `home30` **合并计算** |
| 写入 | 统一写当前展示的 planId；若旧 id 下已有同天记录则同步补写一份（保证取消打卡能两处删干净） |
| UI | 统一以当前计划 id 呈现，不感知别名 |

> 风险点已规避：若只读新 id，老用户 30 天进度会清零；若只读旧 id，新建用户看不到记录。当前实现两者取并集，且 `undo()` 会清理**全部别名**下的同天记录。

---

## 四、修改文件

### 新增（5）

| 文件 | 作用 |
|---|---|
| `data/model/WorkoutPlanBuilder.kt` | 排布算法 + 部位映射 + 校验（纯函数，可单测） |
| `ui/health/MyWorkoutPlansScreen.kt` | 我的训练计划列表页 |
| `ui/health/CreateWorkoutPlanScreen.kt` | 4 步创建向导 |
| `ui/health/WorkoutPlanViewModel.kt` | 计划目录 VM + 向导状态机 |
| `app/src/test/java/com/feiqi/data/model/WorkoutPlanBuilderTest.kt` | 23 项单测 |

### 修改（11）

| 文件 | 改动 |
|---|---|
| `data/database/WorkoutDayDao.kt` | ＋`getByPlan()`、`deleteByPlan()` |
| `data/database/WorkoutExerciseDao.kt` | ＋`deleteByDayIds()` |
| `data/database/ExerciseDao.kt` | ＋`getByIds()`、`getByBodyParts()` |
| `data/repository/WorkoutCatalogRepository.kt` | ＋`saveUserPlan()`、`deletePlanCascade()`、`loadRuntimePlan()`、`exercisesByBodyParts()` |
| `data/repository/WorkoutRepository.kt` | planId 参数化 + 别名合并读取 + `undo()` 清理别名 |
| `data/model/WorkoutPlan.kt` | `computeWorkoutProgress` / `computeStreak` 支持任意 `totalDays` |
| `data/model/WorkoutPlanSeed.kt` | ＋`readAliases()`；修正 lambda label 警告 |
| `ui/health/WorkoutViewModel.kt` | planId 参数化：新增 `activePlanId` / `runtimePlan` / `totalDays`，注入 `WorkoutCatalogRepository` |
| `ui/health/WorkoutScreen.kt` | ＋计划切换条；数据源改走 `runtimePlan`；历史网格按总天数渲染 |
| `ui/health/HealthScreen.kt` | 布尔量 → `WorkoutOverlay` 状态机；接入两个新页面与新 VM |
| `ui/MainActivity.kt` | 注册 `WorkoutPlanViewModel`，`WorkoutViewModel.factory` 双参数 |
| `res/values/strings.xml` | ＋28 条文案；`version_value` → v1.17.0 |
| `app/build.gradle.kts` | versionCode 96 → 97，versionName 1.16.0 → 1.17.0 |

### 明确未改动

`FeiQiDatabase.kt`（仍 v12）、4 个实体的表结构、`WorkoutDetailScreen`（参数是 `WorkoutPlan`，用户计划同构接入即可）、`WorkoutCards.kt`、`WorkoutPlanSeed` 的种子内容、`README.md` 结构。

---

## 五、验证

| 项 | 结果 |
|---|---|
| 单测 | **155 项全绿**（新增 `WorkoutPlanBuilderTest` 23 项，既有 132 项无回归） |
| 编译 | `:app:assembleDebug` BUILD SUCCESSFUL，零 error |
| 新增测试覆盖 | 频率×周期组合（3/4/5/6 × 7/14/30）、周期短于频率的封顶、非法入参收敛、部位轮转顺序、休息日保持空、6 大类映射全覆盖动作库部位、名称校验、草稿可保存三条件、planId 别名、自定义周期进度与连续天数 |
| Room | 本阶段无迁移；v12 schema 未变 |
| 需真机验证 | 官方 30 天进度升级后不清零；创建 7 天/3 次计划并检查排布；用户计划打卡后切回官方进度仍在；小屏（<340dp）Step4 部位网格不溢出 |

### 实现中修正的两处（单测暴露）

1. `layoutTrainingDays` 首版用 `(it-1)*freq%7 < freq` 判断训练日，30 天周期算出 14 天（多算）；改为等间隔 `it*7/freq+1` 后为 13 天（4 整周×3 + 余 2 天命中周一）。
2. `computeStreak(setOf(3,5), today=6)` 首版断言写 0，实际按既定规则「今天没练但昨天练了，连续记录保留」应为 1 —— 是断言错，非实现错，已改断言并补「今天与昨天都没练 → 0」用例。

---

## 六、后续扩展建议

### 第 3 阶段候选（按优先级）

| 优先级 | 项目 | 说明 |
|---|---|---|
| P0 | **计划删除（软删除）** | 计划头加 `deleted` 字段（0/1）+ 删除确认弹窗；删除只置标记，**保留 `workout_records` 历史**；列表默认过滤 `deleted=1` |
| P0 | **动作选择自由度** | Step4 支持逐日展开、手动增减动作（当前只做「按部位自动挑 3~6 个」） |
| P1 | **训练日 ↔ 日历** | 用户计划按 `createdTime` 推算「今天练第几天」，避免长期停在 Day 1 |
| P1 | **动作图片资源** | `ExerciseEntity.image` 已预留；接入 assets 后列表可展示动作图 |
| P1 | **计划编辑** | 复用创建向导，预填已有计划 |
| P2 | **难度选择** | 创建时选初级/中级/高级，影响动作筛选范围 |
| P2 | **统计** | 训练量趋势、部位均衡度（各部位训练次数占比） |
| P2 | **提醒** | 用户计划的训练日提醒（复用现有 ReminderScheduler 契约） |

### 技术债备忘

- `WorkoutViewModel.runtimePlan` 用 `flow { emit(loadRuntimePlan()) }` 包装 suspend 调用，切计划时重新查库；用户计划规模小（≤365 天）暂无性能问题，若后续计划数增长，可改为 Room `Flow` 直连。
- `saveUserPlan` 依赖 `dayDao.getByPlan()` 回查自增 id 而非 `@Insert` 返回值（`insertAll` 是批量 API）。若未来要在同一事务内拿到 id，需改 DAO 签名。
- 官方计划的运行时数据源仍是静态 `WORKOUT_PLANS`（阶段 1 决定），与 `workout_days` 表内容重复；后续可统一改为读表，届时 `readAliases` 的兼容逻辑需重新评估。