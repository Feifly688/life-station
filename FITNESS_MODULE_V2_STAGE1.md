# 健身模块 v2.0 — 第 1 阶段（数据模型升级）

> 独立说明文件，**不改动原 `README.md` 结构**。
> 本阶段只做**底层数据模型**，不动现有 30 天训练计划的体验。

## 一、本阶段新增内容

### 1. 模型升级：把「一天」拆成「计划 / 训练日 / 动作 / 关联」

原 `WorkoutPlan` 实际是**一个训练日**（含 dayNumber/bodyParts/exercises），无法表达"一个 30 天计划"，也无法让动作复用。v2 拆成四层：

| 新模型 | 职责 | 关键字段 |
| --- | --- | --- |
| `WorkoutPlanEntity`（`workout_plans`） | **计划头**：官方 / 用户自建 | id, planId, name, description, type, goal, cycleDays, createdTime |
| `WorkoutDayEntity`（`workout_days`） | 计划里的**某一天** | id, planId, dayNumber, title, bodyParts, description |
| `ExerciseEntity`（`exercises`） | **动作库**（不含组数/次数） | id, name, bodyPart, difficulty, description, image（预留空串） |
| `WorkoutExerciseEntity`（`workout_exercises`） | **天 ↔ 动作**：当天怎么练 | id, dayId, exerciseId, sets, reps, duration, restTime, note |

**为什么这样拆**：同一动作可以出现在多天，且各天配置不同
（例如「窄距俯卧撑」在 Day5 是 3组×12次、Day6 是 3组×力竭）——动作本体只存一份，训练参数按天存。

### 2. 枚举（存英文码，展示层映射中文）

- `WorkoutPlanType`：`OFFICIAL` / `USER_CREATED`
- `WorkoutGoal`：增肌 / 减脂 / 塑形 / 保持
- `ExerciseDifficulty`：初级 / 中级 / 高级
- `bodyPart` 用中文（胸 / 肩 / 背 / 腿 / 臀 / 腹 / 手臂 / 三头 / 二头 / 全身）

### 3. 官方 30 天计划已"迁移"到新结构（种子）

`WorkoutPlanSeed` 把现有静态 `WORKOUT_PLANS` **原样映射**成四张表：

- 1 条计划：`planId = home_beginner_30`、「30天居家爆炸训练」、OFFICIAL、**塑形**、周期 30 天
- **30 个训练日**：Day1–Day30 的部位与描述与旧静态计划**逐条一致**（Day1 仍是「胸 + 三头」）
- **动作库**：按动作名去重（`name` 唯一索引），补齐部位与难度
- **关联行**：每天的动作连成 `dayId ↔ exerciseId + 组数/次数/时长/休息/备注`

写入方式：`FeiQiApplication` 启动时调用 `seedOfficialPlanIfEmpty()`，**仅当计划表为空才写**（幂等，不覆盖用户后续自建计划）。

### 4. 现有体验零改动

- 运行时的数据源**仍是** `WORKOUT_PLANS`（主页/详情页/打卡逻辑一行未改）；
- `workout_records`（打卡记录）表与读写逻辑**完全不动**；
- 健康 / 待办 / 统计 / 书影音 / 记账模块零改动；
- 底部导航仍是 5 个 Tab，健身仍挂在健康页。

## 二、修改文件列表

**新增（11 个）**
| 文件 | 内容 |
| --- | --- |
| `data/model/WorkoutEnums.kt` | 三个枚举 + 容错解析 |
| `data/model/WorkoutPlanSeed.kt` | 官方计划种子映射（含动作→部位/难度人工映射） |
| `data/entity/WorkoutPlanEntity.kt` | 计划头 |
| `data/entity/WorkoutDayEntity.kt` | 训练日 |
| `data/entity/ExerciseEntity.kt` | 动作库 |
| `data/entity/WorkoutExerciseEntity.kt` | 天↔动作关联 |
| `data/database/WorkoutPlanDao.kt` | 计划读写 |
| `data/database/WorkoutDayDao.kt` | 训练日读写 |
| `data/database/ExerciseDao.kt` | 动作库读写 |
| `data/database/WorkoutExerciseDao.kt` | 关联读写 |
| `data/repository/WorkoutCatalogRepository.kt` | 目录仓库 + 幂等种子 |

**修改（4 个，均为增量）**
| 文件 | 改动 |
| --- | --- |
| `data/database/FeiQiDatabase.kt` | version 11 → **12**；注册 4 实体 + 4 DAO；新增 `MIGRATION_11_12` |
| `FeiQiApplication.kt` | 注册迁移；注入 `workoutCatalogRepository`；启动时幂等种子 |
| `app/build.gradle.kts` | 版本 v1.16.0（code 96） |
| `README.md` / `strings.xml` | 仅版本号同步 |

## 三、数据库变化

**v11 → v12：只 `CREATE TABLE`，零 ALTER。**

```sql
CREATE TABLE workout_plans      (id PK AUTOINCREMENT, planId, name, description, type, goal, cycleDays, createdTime)
  UNIQUE INDEX index_workout_plans_planId (planId)

CREATE TABLE workout_days       (id PK AUTOINCREMENT, planId, dayNumber, title, bodyParts, description)
  UNIQUE INDEX index_workout_days_planId_dayNumber (planId, dayNumber)

CREATE TABLE exercises          (id PK AUTOINCREMENT, name, bodyPart, difficulty, description, image)
  UNIQUE INDEX index_exercises_name (name)

CREATE TABLE workout_exercises  (id PK AUTOINCREMENT, dayId, exerciseId, sets, reps, duration, restTime, note)
  UNIQUE INDEX index_workout_exercises_dayId_exerciseId (dayId, exerciseId)
  INDEX        index_workout_exercises_dayId      (dayId)
  INDEX        index_workout_exercises_exerciseId (exerciseId)
```

- `workout_records` **保持不变**；`todos` / `accounts` / `habit_records` / `medias` **一律不动**；
- **不声明外键**（与本库既有表一致），只用索引 + 应用层保证一致性；
- ⚠️ 本库 `exportSchema = false`（无编译期 schema 校验），因此迁移 SQL 的**列名/索引名必须与实体声明逐字一致**，否则 Room 打开数据库时会抛 `IllegalStateException` 启动即崩；
- `planId` 差异：`workout_records` 仍是 `home30`（按要求不动），v2 官方计划为 `home_beginner_30`，已用常量 `WorkoutPlanSeed.LEGACY_PLAN_ID` 标注，映射留到下一阶段。

## 四、验证

- **单测**：`WorkoutSeedTest` 8 项 —— 计划字段、30 天完整、天与旧静态计划逐条一致、动作库去重且部位齐全、关联行数 = 动作总数、无重复、关联引用有效、组数/次数/备注与旧计划一致（Day1 俯卧撑 4 组/力竭/备注）、同动作多天不同配置。
- **全量**：`:app:testDebugUnitTest` **132 项通过 / 0 失败**；`:app:assembleDebug` 通过。
- **手动**：升级安装后打开健康页 → 30 天训练**照常可用**（体验零变化）；日志可见「官方训练计划已写入计划目录」。

## 五、后续可扩展方向（本阶段刻意不做）

1. **计划创建/编辑页**（USER_CREATED）：基础信息 → 选动作 → 排天数 → 存 `workout_days` / `workout_exercises`；
2. **计划列表页**：读取 `workout_plans`（OFFICIAL + USER_CREATED 混合），可按 goal/部位筛选；
3. **切换数据源**：主页/详情页从 `WORKOUT_PLANS` 改为读库（届时需要 `planId` 映射 + 打卡记录迁移）；
4. **动作图片**：`ExerciseEntity.image` 预留字段，接入资源或 Coil；
5. **动作难度/部位筛选**、按部位统计训练量；
6. **打卡记录迁移**：`home30` → `home_beginner_30` 的映射或数据回填。