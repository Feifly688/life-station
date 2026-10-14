# 翡栖 健身模块 v2.0 Stage 4

**版本**：v1.19.0（versionCode 99）
**基线**：v1.18.0（Stage 3 动作库）
**范围**：健身主页重构 + UI 体验优化 + 页面逻辑优化
**数据库**：**无任何变更**（Room 保持 v13，不新增 Migration，`workout_records` 结构与既有打卡数据零改动）

---

## 一、开发原则落实

| 原则 | 落实情况 |
| --- | --- |
| 1. 不修改已有数据库结构 | ✅ Room version 仍为 13，实体文件一字未改 |
| 2. 不新增 Room Migration | ✅ 未新增 `Migration`，未触碰 `FeiQiDatabase` |
| 3. 不改变 `workout_records` 数据结构 | ✅ 只**新增读取**（`observeAll()`），表结构与写入逻辑均未动 |
| 4. 不影响已有训练计划和打卡数据 | ✅ 仅新增派生统计与 UI 呈现；打卡写入路径不变 |
| 5. 保持当前 Material3 风格 | ✅ 沿用暖调令牌与 `FeiQiRadius` / `FeiQiMotion.normal`(200ms)，**零新增颜色** |

---

## 二、UI 调整

### 2.1 健康主页重排（健身成为主内容）

原布局：体重卡占据顶部 → 健身入口卡 → 体重曲线。
新布局自上而下：

| 顺序 | 区块 | 说明 |
| --- | --- | --- |
| 1 | 页面标题行 | 保留 |
| 2 | **健身中心 Hero** | 连续训练天数 / 本月完成 / 累计训练时长，点击进入健身主页 |
| 3 | **今日训练大卡** | 部位 headline、难度徽章、约 N 分钟、动作数、动作预览（>4 折叠）、48dp 主按钮 |
| 4 | **我的训练计划** | 横滑计划切换条 + 「我的计划 ›」入口；无任何计划时给空状态 |
| 5 | 计划进度卡 | 只保留完成次数与进度（**已删除连续天数文案**） |
| 6 | **健康数据**（小卡） | 体重 + BMI 徽章 + 「完善个人信息」，压成一张小卡 |
| 7 | 体重录入卡 | 保留 |
| 8 | 体重曲线卡 | 保留 |
| 9 | 最近记录 | 标题降级为 `titleSmall`，从「独立大区块」降为「附属列表」 |

体重由「页面主角」降级为「辅助小卡」，健身成为健康页的主要内容。

### 2.2 共享 UI 组件（新建 `WorkoutComponents.kt`）

| 组件 | 作用 |
| --- | --- |
| `Modifier.pressable` | 按压缩放（`PRESSED_SCALE = 0.97`）+ 点击，`indication = null` 避免廉价水波纹 |
| `WorkoutEmptyState` | 统一空状态：圆形浅底 + emoji + 标题 + 副文案 + 按钮 |
| `WorkoutHeroPanel` | 顶部统计面板（渐变底 + 细进度条） |
| `ThinProgressBar` | `animateFloatAsState` + `LinearProgressIndicator(strokeCap = Round)` |
| `TodayWorkoutCard` | 今日训练大卡 |
| `SectionHeader` | 带可选右侧动作的区块标题 |

> ⚠️ `pressable` **只能用于非 Material3 组件**。`Button`/`IconButton` 自带点击处理，叠加会注册两个点击源导致事件重复。本阶段踩过一次并已修正。

### 2.3 动效规范

- 时长统一 `FeiQiMotion.normal` = 200ms + `FastOutSlowInEasing`
- 进度条 `animateFloatAsState` 补间
- 步骤指示点：颜色 + 尺寸双动画（`animateColorAsState` + `animateDpAsState`）
- **状态变化只改样式**（alpha/scale/color），不增删组合节点——沿用项目铁律避免跳变

### 2.4 其他页面

| 页面 | 改动 |
| --- | --- |
| 动作详情 | 底部**固定**「加入训练计划」按钮（`Scaffold.bottomBar`），点击弹 Snackbar；按钮条带顶部暖调渐隐，滚动到底不显硬切 |
| 动作库 | 动作卡与分类卡接 `pressable` 缩放；空状态换 `WorkoutEmptyState`，按成因给不同出口（清空搜索 / 返回） |
| 我的计划 | 计划卡新增完成进度条（`已完成 x / y 天` + `百分比` + 进度条）；卡片接 `pressable`；空状态换 `WorkoutEmptyState` |
| 创建计划 | 步骤指示由分段横条改为 **`● ● ○ ○` 点状**（已走过的点淡暖色、当前点更大更深、未来点空心）；动作选择覆盖层补 `BackHandler` |
| 训练详情 | 逐组完成状态由 `remember` 改 `rememberSaveable`，旋转/重建不丢训练进度 |

### 2.5 颜色

**无新增颜色**。运动辅助色语义映射到既有令牌：

| 语义 | 令牌 |
| --- | --- |
| 已完成 | `CardGreen` |
| 训练中 | `WarmAccent` |
| 未完成 | `OutlineVariant` |

---

## 三、统计规则（三条口径严格分离）

`workout_records` **没有时长字段**，因此本阶段所有统计均为**派生**，不写库。三条口径的输入、语义、用途完全独立，**不可互换**：

### 3.1 连续训练天数（顶部 Hero）

`computeCalendarStreak(dates: Collection<LocalDate>, today: LocalDate)`

- **真实日历连续**：跨全部计划，只要打卡日期相邻即累加
- 今天在集合 → 从今天往回数；今天不在但昨天在 → 从昨天往回数（当天还没练不算断）；都不在 → 0
- **唯一展示位置：顶部 Hero**
- 进度卡中原本的连续天数文案**已删除**——此前两处口径不同却同名展示，会让用户以为是同一个数

### 3.2 本月完成（Hero 内）

`monthProgress(dates, today)` → `MonthProgress(doneDays, totalDays, fraction, isFull)`

- 分母 = **自然月天数**（10 月 = 31，闰年 2 月 = 29），用 `YearMonth.lengthOfMonth()`
- 只统计 `YearMonth.from(date) == 当前月` 的打卡
- **与「30 天计划进度」是两个独立概念**，分母不同、含义不同，不共用文案

### 3.3 累计训练时长（估算，UI 强制带「约」）

```
parseRepCount("12次")            → 12        parseRepCount("20次/侧")  → 20
parseRepCount("力竭")            → null（按最短一组估）
每组秒 = durationSeconds > 0 ? durationSeconds : max(reps × 3, 20)
动作秒 = 每组秒 × 组数 + restTime × (组数 - 1)     // 单组不计休息
单日秒 = Σ 动作秒 + 60 × 动作数// 动作间 60s 换组
累计秒 = Σ 单日秒（按 "planId#dayNumber" 去重）
```

`estimatedDurationLabel()` 输出恒带「约」：`约 35 分钟` / `约 6 小时 25 分钟` / `约 6 小时`。

> 官方计划存在别名 id 会写出两条记录，故用 `seen: HashSet` 按 `planId#dayNumber` 去重；`planCache` 声明**必须**在 `totalEstimatedSeconds` 之前（Kotlin 属性按声明顺序初始化）。

### 3.4 计划内进度（仅服务进度卡）

`computeStreak(completedDays: Set<Int>, today, totalDays)` 是**计划内天序号口径**，与 `computeCalendarStreak` 语义不同。KDoc 已加警示，两者不可互换。本阶段删除了它唯一的 UI 展示位。

### 3.5 新增单测（`WorkoutStatsTest.kt`，30 项）

覆盖 `parseRepCount`（次数/力竭/空串）、`estimateExerciseSeconds`（计时动作 315s、次数动作 414s、力竭 240s、单组不计休息、sets=0、负 restTime）、`estimateDaySeconds`（795s）、`secondsToMinutes`、`estimatedDurationLabel`（35分钟/6小时25分/整小时/零值）、`computeCalendarStreak`（今天连续/昨天保留/中断重算/都为0/空集/重复日期）、**两口径结果不同的对照测试**、`monthProgress`（31天分母/闰年29 vs 平年28/跨月/重复打卡）、`estimateTotalSeconds`。

---

## 四、页面逻辑变化

### 4.1 历史记录交互反转（重要）

| | 改前 | 改后 |
| --- | --- | --- |
| 点击已完成 Day | **静默取消打卡** | **查看记录** |
| 二次确认 | 无 | 不需要（已不可撤销） |
| 目标页 | — | 新建 `WorkoutHistoryDetailScreen` |

**改动理由**：原先点一下即静默取消打卡，无确认、无出口，误触即丢数据。

新详情页展示：日期、训练部位、`完成于 %s`、完成动作列表（序号圆点 + 名称 + 组数×次数/时长），底部提示「记录不可撤销」。计划数据读取不到时（`plan == null`）渲染空状态兜底而非白屏。

> 「取消打卡」按需求本阶段**移除**；如需恢复应放到详情页的更多菜单，并保留二次确认。

### 4.2 返回机制

**新增顶层 `BackHandler(enabled = workoutVisible)`**（此前健身二级页**完全没有接** `BackHandler`，在健身主页按返回会直接退出 App）。关闭顺序：

```
动作详情 → 历史详情 → 训练详情 → 动作库/创建计划 → 我的计划 → 健身主页 → 健康主页
```

**覆盖层互斥护栏**：`exerciseDetailId` / `workoutDetailDay` / `historyRecordKey` 原为三个独立顶层 state，可同时非空导致双层叠加（此前靠隐式约定维持）。现由 `LaunchedEffect` 集中收敛为「最多一层详情」。

**状态恢复**：覆盖层状态 `rememberSaveable` 化 —— 枚举存 `name` 字符串，详情 id 用 `-1L` 哨兵（`null` 不是 `rememberSaveable` 的合法默认值）。

### 4.3 五个主页面退出二次确认（新增需求）

`MainActivity` 中 `MAIN_ROUTES = {home, accounting, schedule, health, media}`：

- 停留在五个主页面时，返回键 / 边缘向内滑 → 弹确认框「确认退出翡栖？」，按钮「再练会儿」/「退出」
- `settings` 保持原逻辑：返回回首页，不退出
- `BackHandler` 同时覆盖系统返回键与手势返回，两者行为一致
- 健身二级页内层自己注册了 `BackHandler`，Compose 取**最内层启用者**，因此退出弹窗只在健身覆盖层全关后才会出现

### 4.4 覆盖层互斥

新建三个覆盖层互斥护栏，防止双层叠加造成的「返回键关不掉下层」。

---

## 五、修改文件

### 新增
| 文件 | 内容 |
| --- | --- |
| `data/model/WorkoutStats.kt` | 全部统计推导纯函数 + 三口径说明（约 180 行） |
| `ui/health/WorkoutComponents.kt` | 共享 UI 组件（pressable / 空状态 / Hero / 进度条 / 今日卡 / 区块标题） |
| `ui/health/WorkoutHistoryDetailScreen.kt` | 训练记录详情页（约 215 行） |
| `app/src/test/.../WorkoutStatsTest.kt` | 30 项单测 |

### 修改
| 文件 | 改动 |
| --- | --- |
| `data/model/WorkoutPlan.kt` | `Exercise` 新增 `restTime`（**仅模型层**，列本就存在）；`computeStreak` KDoc 加口径警示 |
| `data/database/WorkoutDao.kt` | 新增 `observeAll()`（**纯新增查询**，不改表） |
| `data/repository/WorkoutRepository.kt` | 新增 `allRecords()` |
| `data/repository/WorkoutCatalogRepository.kt` | `loadRuntimePlan` 补 `restTime = link.restTime`（此前被丢弃） |
| `ui/health/WorkoutViewModel.kt` | 新增 `allRecords` / `allDates` / `calendarStreak` / `monthProgress` / `planCache` / `totalEstimatedSeconds` / `estimateSecondsOfDay`；`undo()` 保留但 UI 不再暴露 |
| `ui/health/HealthScreen.kt` | 主内容重排、覆盖层 `rememberSaveable` 化 + 互斥护栏 + 顶层 `BackHandler`、`PlanStrip`、`WeightSummaryCard`；删除 `WorkoutEntryCard` |
| `ui/health/WorkoutScreen.kt` | 历史交互反转（移除 `undo` 调用）、改用 `TodayWorkoutCard` |
| `ui/health/WorkoutCards.kt` | `WorkoutProgressCard` 接 `planName` 并去连续天数文案；`ExerciseCard` → `TrainingExerciseCard`（解除同名冲突） |
| `ui/health/WorkoutDetailScreen.kt` | 引用改名；`doneExercises` 改 `rememberSaveable`（逗号串编码） |
| `ui/health/ExerciseDetailScreen.kt` | 底部固定「加入训练计划」+ Snackbar |
| `ui/health/ExerciseLibraryScreen.kt` | 卡片接 `pressable`；空状态换 `WorkoutEmptyState` + 清空搜索出口 |
| `ui/health/MyWorkoutPlansScreen.kt` | 计划卡进度条 + `pressable`；空状态换 `WorkoutEmptyState` |
| `ui/health/CreateWorkoutPlanScreen.kt` | 步骤指示改点状；`pickingDay` 覆盖层 `BackHandler` |
| `ui/MainActivity.kt` | 五主页面退出二次确认 |
| `res/values/strings.xml` | 新增约 40 条 |
| `ui/home/HomeScreen.kt` | 清理 2 处既有 warning（未使用变量 / 名称遮蔽） |
| `ui/schedule/ScheduleCards.kt` | 清理 2 处既有 warning（未使用变量 / 未使用参数） |
| `ui/schedule/ScheduleListEditDialog.kt` | 清理 1 处既有 warning（`mapIndexed` → `map`） |

**未改动**：`README.md`（结构保持原样）、任何 Room 实体、`FeiQiDatabase`。

## 六、验证

| 项 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest` | **230 项全绿，0 失败**（Stage 3 的 197 + Stage 4 新增 33） |
| `clean :app:assembleDebug` | **BUILD SUCCESSFUL，零 error** |
| 本阶段触及模块的 warning | **0**（已清理 HomeScreen 2 / ScheduleCards 2 / ScheduleListEditDialog 1 / WorkoutViewModel 1） |
| 全项目遗留 warning | 12 处（`Schedule.kt`、`AccountingViewModel`、`SwipeToDeleteContainer`、`ExerciseViewModel`、`WorkoutPlanViewModel`、`WorkoutViewModel` 的 unchecked cast、`HomeViewModel`、`ScheduleViewModel` 2 处、`Theme.kt` 2 处 deprecated）——**均为改动前既有**，非本阶段引入，留待单独清理 |
| Room schema | 无变更，无需迁移核对 |
| 版本同步 | `build.gradle.kts`(99 / 1.19.0)、`strings.xml` 的 `version_value`、APK 文件名 三处一致 |

---

## 七、后续优化方向

1. **计划软删除**：加 `deleted` 字段 + 确认弹窗 + 保留 `workout_records` 历史（级联删除的一致性需一并解决）
2. **动作详情「加入训练计划」真正实现**：需支持跨计划写入 `workout_exercises`
3. **恢复取消打卡**：如确有需要，放在记录详情页的更多菜单，并保留二次确认
4. **累计时长精确化**：可选增加 `workout_records` 时长字段与手动校正入口（本阶段受"不改表"约束）
5. **训练页组间计时**：利用 `restTime` 做实际倒计时，让估算值可被实测校正
6. **健身页独立 Tab**：当前挂在健康页，若使用频率上升可考虑独立入口