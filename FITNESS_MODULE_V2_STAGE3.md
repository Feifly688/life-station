# 翡栖 App 健身模块 v2.0 — 第 3 阶段：动作库 Exercise Library

> 版本：v1.18.0（versionCode 98）
> 阶段定位：在**不改动已有训练计划 / 打卡逻辑 / 历史数据**的前提下，落地本地动作库，
> 并让「用户自建训练计划」能够**逐日挑选动作**。

---

## 一、新增功能

| # | 功能 | 入口 | 说明 |
|---|------|------|------|
| 1 | 动作库入口卡 | 健康 Tab → 健身主页 | 计划切换条下方新增 🏋「动作库」卡片，带「进入 ›」 |
| 2 | 动作库列表页 | 动作库入口卡 | 顶部搜索 + 7 大类分类卡 + 2 列动作网格 |
| 3 | 动作详情页 | 点击动作卡 | 目标 / 难度 / 器械 / 介绍 / 步骤 / 注意事项 |
| 4 | 动作选择页 | 创建计划 → 第 4 步 → 某训练日「选择动作 ›」 | 按部位筛选、勾选动作、单日上限 6 个 |
| 5 | 逐日动作配置 | 同上 | 保存进 `PlanDraft.dayExercises`，随计划落库 |
| 6 | 默认动作库 | 首次启动自动种子 | 19 个动作 / 7 大类，含步骤与注意事项 |

**明确不在本阶段范围**：视频播放、AI 动作识别、云端动作库、社区分享、动作删除。
按用户要求，本阶段**只做本地动作库**。

### 页面流程

```
健康 Tab
  └─ 健身主页 (WorkoutScreen)
       ├─ 动作库入口卡 (ExerciseLibraryEntryCard)
       │    └─ 动作库 (ExerciseLibraryScreen)
       │         ├─ 分类卡 / 搜索框 / 动作网格
       │         └─ 动作详情 (ExerciseDetailScreen)   ← 最上层独立覆盖
       └─ 我的计划 → 创建计划 (CreateWorkoutPlanScreen)
            └─ 第 4 步「训练日」→ DayExerciseRow
                 └─ 动作选择 (ExercisePickerScreen)   ← 覆盖在向导之上
```

三层覆盖沿用既有 `WorkoutOverlay` 枚举状态机 + `exerciseDetailId` 顶层覆盖，
未引入新的导航机制，也未改动 `HealthScreen` 原有分支。

---

## 二、动作库说明

### 2.1 两层部位设计

动作库的部位分两层，职责不同，**不可混用**：

| 层 | 字段 | 取值 | 面向 |
|----|------|------|------|
| 训练大类 | `ExerciseEntity.category` | 胸部 / 背部 / 肩部 / 腿部 / 臀部 / 手臂 / 腹部（7 个） | **用户可见**，直接展示在分类卡与动作卡上 |
| 具体部位 | `ExerciseEntity.bodyPart` | 胸 / 三头 / 背 / 二头 / 肩 / 腿 / 臀 / 腹 | **动作库内部**筛选用，用户不直接看到 |

映射关系由 `BodyCategory.detailParts` 单点定义：

| 大类 | emoji | detailParts |
|------|-------|-------------|
| 胸部 | 💪 | 胸 + 三头 |
| 背部 | 🔙 | 背 + 二头 |
| 肩部 | 🤸 | 肩 |
| 腿部 | 🦵 | 腿 |
| 臀部 | 🍑 | 臀 |
| 手臂 | 💥 | 二头 + 三头 |
| 腹部 | 🔥 | 腹 |

**为什么分两层**：用户创建计划时选「胸部」就够，不应暴露专业细粒度分类；
但动作库内部必须按细粒度组织，否则「窄距俯卧撑（胸+三头）」这类动作无法归类。

> ⚠️ `臀部` 在本阶段是**第 7 个独立大类**（用户决策），不并入腿部 ——
> 便于后续扩展臀推 / 单腿臀桥 / 蚌式开合。

### 2.2 默认动作库（19 个动作）

| 大类 | 数量 | 动作 |
|------|------|------|
| 胸部 | 4 | 俯卧撑 / 宽距俯卧撑 / 钻石俯卧撑 / 跪姿俯卧撑 |
| 背部 | 3 | 超人式 / Y 字伸展 / 毛巾划船 |
| 肩部 | 2 | Pike 俯卧撑 / 侧平举 |
| 腿部 | 3 | 深蹲 / 箭步蹲 / 保加利亚分腿蹲 |
| 臀部 | 1 | 臀桥 |
| 腹部 | 4 | 卷腹 / 俄罗斯转体 / 平板支撑 / 登山跑 |
| 手臂 | 2 | 二头弯举 / 臂屈伸 |

**手臂只保留 2 个动作是刻意取舍**（用户决策）：本阶段重点是结构完整而非数量，
不为了凑数强行补动作。后续扩展时再增加窄距俯卧撑 / 锤式弯举 / 反向臂屈伸。

每个动作都包含：名称、大类、具体部位、目标肌肉、难度、器械、介绍、**分步步骤**、注意事项。

### 2.3 器械枚举（4 项）

`ExerciseEquipment`：**无器械 / 弹力带 / 哑铃 / 瑜伽垫**，存英文码（`NONE` / `BAND` / `DUMBBELL` / `MAT`）。

暂不加入壶铃、俯卧撑板等 —— 当前翡栖定位以**居家训练**为主。
瑜伽垫虽非训练器械，但属常见训练辅助装备，予以保留。
后续若扩展健身房训练场景再增加器械类型。

### 2.4 预留字段

`image`（图片资源）与 `videoUrl` 本阶段**统一写空串**，UI 用「渐变底 + emoji」占位。
字段先占位，后续接资源时无需再改 schema。

---

## 三、数据库变化

### 3.1 版本升级

`FeiQiDatabase` **version 12 → 13**，采用扩展已有 `exercises` 表（而非新建 `exercise_library`）：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `category` | TEXT NOT NULL | `''` | 训练大类 |
| `targetMuscle` | TEXT NOT NULL | `''` | 目标肌肉 |
| `equipment` | TEXT NOT NULL | `'NONE'` | 器械码 |
| `steps` | TEXT NOT NULL | `''` | 步骤（换行分隔） |
| `tips` | TEXT NOT NULL | `''` | 注意事项 |
| `videoUrl` | TEXT NOT NULL | `''` | 视频地址（预留） |

迁移方式为 6 条 `ALTER TABLE ADD COLUMN`：**不重写表、不动自增 id**，
因此 `workout_exercises.exerciseId` 的既有引用全部保持有效。

老数据迁移后 6 个新字段为空串，由启动时的 `seedExerciseLibrary()` 按动作名回填。

### 3.2 ⚠️ 写入纪律：禁止 REPLACE

`exercises.name` 上有唯一索引，而 `OnConflictStrategy.REPLACE` 的语义是
**「删旧行 + 插新行」**——自增 `id` 会变。`workout_exercises.exerciseId` 按 id 硬引用，
一旦 REPLACE，**所有已保存计划的训练动作全部指向不存在的 id，训练日动作变空白**。

因此本阶段新增两个 DAO 方法并严格区分用途：

| 方法 | 策略 | 用途 |
|------|------|------|
| `insertIgnore(exercise)` | `OnConflictStrategy.IGNORE` | 新动作入库，name 冲突则跳过（保 id） |
| `update(exercise)` | `@Update` | 已存在动作回填新字段（**保 id**） |
| `insertAll(exercises)` | `REPLACE` | ⚠️ 仅限**空库首次种子**，KDoc 已限定 |

`seedExerciseLibrary()` 的幂等策略：**存在 → update（保 id） / 不存在 → insertIgnore**。

### 3.3 对既有数据的影响

| 表 | 影响 |
|----|------|
| `exercises` | 加 6 列，id 与 name 不变 |
| `workout_plans` / `workout_days` / `workout_exercises` | **完全未改动** |
| `workout_records` | **完全未改动** |

`WorkoutPlanSeed` 中的官方 30 天计划改为**复用 `ExerciseLibrarySeed.byName()`** 读取
难度/部位/要领，避免动作元数据两处维护。未被动作库收录的动作回退为
`bodyPart = "全身"`、`difficulty = BEGINNER`、`category` 留空。

---

## 四、修改文件

### 新增

| 文件 | 职责 |
|------|------|
| `data/model/ExerciseLibrarySeed.kt` | 19 个默认动作 + `byName()` / `categoryOfBodyPart()` |
| `data/model/ExerciseLibraryRules.kt` | 纯函数：`filterExercises` / `exercisesOfCategory` / `exerciseTargetLabel` / `exerciseStepList` / `exerciseDifficultyLabel` / `exerciseEquipmentLabel` / `matchesExerciseKeyword` |
| `ui/health/ExerciseViewModel.kt` | 搜索关键词 + 选中分类 + 动作流 `combine`；`pickedNames` 勾选态；超上限拦截 |
| `ui/health/ExerciseLibraryScreen.kt` | 动作库列表页 + `internal fun ExerciseCard` |
| `ui/health/ExerciseDetailScreen.kt` | 动作详情页（Hero / 信息卡 / 步骤 / 注意事项） |
| `ui/health/ExercisePickerScreen.kt` | 动作选择页（分类 Chip / 勾选放大动画 / 上限 Snackbar） |
| `app/src/test/java/com/feiqi/data/model/ExerciseLibraryTest.kt` | 41 项单测 |

### 修改

| 文件 | 改动 |
|------|------|
| `data/entity/ExerciseEntity.kt` | 新增 6 字段 + `EQUIPMENT_NONE` 常量 + 禁止 REPLACE 的 KDoc |
| `data/database/ExerciseDao.kt` | 新增 `getAllOnce` / `observeByCategory` / `getByCategoryAndParts` / `observeById` / `getById` / `insertIgnore` / `update` |
| `data/database/FeiQiDatabase.kt` | `version = 13` + `MIGRATION_12_13` |
| `FeiQiApplication.kt` | 注册 `MIGRATION_12_13`；`seedWorkoutCatalog()` 调用 `seedExerciseLibrary()` |
| `data/repository/WorkoutCatalogRepository.kt` | `seedExerciseLibrary()`（保 id 幂等）+ 4 个动作查询方法；`saveUserPlan` 改用 `resolveDayExercises` |
| `data/model/WorkoutPlanSeed.kt` | 删除 `EXERCISE_META`，改为复用动作库元数据 |
| `data/model/WorkoutPlanBuilder.kt` | `BodyCategory` 增至 7 类（新增 `HIP`）；新增 `ExerciseEquipment` 枚举；`PlanDraft` 新增 `dayExercises` / `hasManualExercises()` / `manualDayCount`；新增 `resolveDayExercises()` |
| `ui/health/WorkoutScreen.kt` | 新增 `ExerciseLibraryEntryCard` + `onOpenLibrary` 参数 |
| `ui/health/HealthScreen.kt` | 新增 `exerciseViewModel` 参数；`WorkoutOverlay` 加 `ExerciseLibrary`；新增 `exerciseDetailId` 顶层覆盖 |
| `ui/health/WorkoutPlanViewModel.kt` | 新增 `_dayExercises` / `setDayExercises()` / `dayExercisesOf()`；`buildDraft` 传 `dayExercises` |
| `ui/health/CreateWorkoutPlanScreen.kt` | 第 4 步新增 `DayExerciseRow` + `pickingDay` 覆盖层接 `ExercisePickerScreen` |
| `ui/MainActivity.kt` | 注册 `ExerciseViewModel` 并传入 `HealthScreen` |
| `res/values/strings.xml` | 新增 32 条 `exercise_*` 文案；`version_value` → v1.18.0 |
| `app/build.gradle.kts` | `versionCode` 97 → 98；`versionName` → 1.18.0 |
| `app/src/test/java/com/feiqi/data/model/WorkoutPlanBuilderTest.kt` | 适配 7 大类（`HIP` 断言、部位全覆盖、分类数 = 7） |

---

## 五、验证

| 项 | 结果 |
|----|------|
| `clean :app:testDebugUnitTest` | **197 项全绿，0 失败**（新增 41 项动作库单测） |
| `clean :app:assembleDebug` | **零 error 零 warning** |
| Room schema 核对 | `FeiQiDatabase_Impl.java` 的 `CREATE TABLE exercises` 12 列与 `MIGRATION_12_13` 列名**逐字一致**；`workout_exercises` 未变 |
| APK SHA-256 | `99343f3f926c9f67028a1885803a3dab6376ea8e4d8d5304ca4fd490b4813456` |

### 关键边界场景（单测覆盖）

- 分类卡计数与实际列表**严格一致**（不会出现「胸部 4 个」但只列出 3 个）
- 搜索命中优先级：动作名 > 目标肌肉 > 大类
- 老数据只有 `bodyPart` 没有 `category` 时，`exerciseTargetLabel` 逐级回退
  （`category` → `targetMuscle` → `bodyPart` → 「全身」）而非直接空
- 手动指定的动作若不在动作库中，保存时**丢弃**而非写入悬空引用
- 单日动作数超过上限 6 时 `togglePicked` 返回 `false`，选择页弹 Snackbar 提示
- `equipment` 未知码回退 `NONE`；`BodyCategory.fromLabel` 未知标签回退 `CHEST`
- 动作详情页 `exercise == null` 时渲染「动作不存在或已被删除」+ 返回按钮，**不出现白屏无出口**

---

## 六、后续扩展方向

| 方向 | 说明 | 前置依赖 |
|------|------|------------|
| **计划软删除** | `deleted` 字段 + 删除确认弹窗 + **保留 `workout_records` 历史** | 阶段 2 已明确推迟到本阶段之后，是下一优先项 |
| **动作资源** | 填充 `image` / `videoUrl`，动作库列表与详情页接真实图/视频 | 资源文件与 `videoUrl` 的可达性方案 |
| **动作库扩充** | 手臂补窄距俯卧撑 / 锤式弯举 / 反向臂屈伸；臀部补臀推 / 单腿臀桥 / 蚌式开合 | 无（纯数据扩充） |
| **自定义动作** | 用户自建动作入库（需解决 `name` 唯一索引的命名冲突策略） | 软删除先落地，否则误删无法恢复 |
| **器械扩展** | 壶铃 / 俯卧撑板 / 龙门架等，适配健身房场景 | 需先确定是否区分「居家 / 健身房」两套动作库 |
| **部位细粒度 UI** | 大类筛选下增加二级部位筛选（胸 / 三头 分开选） | 现有 `detailParts` 已就绪，仅需 UI |
| **训练日组数配置** | 选定动作后逐动作配置组数/次数/休息，动作本体与当天配置继续分离 | `WorkoutExercise` 已预留 `sets`/`reps`/`restTime` |
