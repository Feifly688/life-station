package com.feiqi.data.model

import com.feiqi.data.entity.ExerciseEntity
import com.feiqi.data.entity.WorkoutDayEntity
import com.feiqi.data.entity.WorkoutExerciseEntity
import com.feiqi.data.entity.WorkoutPlanEntity

/**
 * 官方计划的**种子数据**：把现有静态 `WORKOUT_PLANS` 映射成 v2 的四张表。
 *
 * ## 为什么保留旧静态数据
 * 现有 30 天计划的运行时数据源仍是 `WORKOUT_PLANS`（首页/主页/详情页都在用它），
 * 本阶段**不切换数据源**（避免改变已有体验）。种子只是把同一份内容**再落一份到新表**，
 * 为下一阶段「自定义计划 / 创建页」打好底座。
 *
 * ## 单一数据源
 * 动作名、组数、次数、备注全部来自 `WORKOUT_PLANS`，本文件只补充
 * 「动作 → 部位 / 难度」的人工映射，不重复维护动作清单。
 */
object WorkoutPlanSeed {

    /** v2 官方计划 id（与文档示例一致）。 */
    const val OFFICIAL_PLAN_ID: String = "home_beginner_30"

    /**
     * 旧打卡记录使用的 planId。
     *
     * `workout_records` 按要求**保持不变**，因此历史记录里的 planId 仍是 `home30`；
     * 与 v2 计划 id 的映射留给下一阶段处理（此处只做标注，避免两处硬编码字符串）。
     */
    const val LEGACY_PLAN_ID: String = "home30"

    /**
     * 打卡读取时的**planId 别名表**。
     *
     * ## 为什么需要
     * v2 之前所有打卡记录写入的是 [LEGACY_PLAN_ID]（`home30`），
     * v2 的官方计划 id 却是 [OFFICIAL_PLAN_ID]（`home_beginner_30`）。
     * 若直接按新 id 查询，老用户的 30 天进度会**全部清零**（数据回归）。
     *
     * ## 处理原则（v2 阶段 2 定稿）
     * - **不改历史记录、不做数据迁移**；
     * - 仅在 **Repository 读取层**做别名展开，多个 id 的记录**合并计算**；
     * - UI 层统一以当前展示的计划 id 为准，不感知别名。
     */
    fun readAliases(planId: String): List<String> =
        if (planId == OFFICIAL_PLAN_ID) listOf(OFFICIAL_PLAN_ID, LEGACY_PLAN_ID)
        else listOf(planId)

    /** 默认组间休息（秒）。 */
    const val DEFAULT_REST_SECONDS: Int = 90

    /** 种子写入时间（固定值，保证重复写入幂等、不随时间漂移）。 */
    private const val SEED_CREATED_TIME = 1_767_225_600_000L // 2026-01-01T00:00:00Z

    /**
     * 动作元数据**单一数据源**：[ExerciseLibrarySeed]。
     *
     * v2 阶段 3 起，动作库成为用户可见的独立功能，
     * 官方计划种子不再自带一份 `动作名 → (部位, 难度)` 映射（避免两处重复维护）：
     * - 库中已收录的动作 → 直接用 [ExerciseLibrarySeed] 的完整字段；
     * - 库中未收录的动作（如恢复日的拉伸/ 散步）→ 回退到静态计划的
     *   「部位 = 全身、难度 = 初级」，`category` 留空（不会出现在动作库页面）。
     */
    /** 官方计划头（id 固定为 1，便于确定性关联）。 */
    val plan: WorkoutPlanEntity = WorkoutPlanEntity(
        id = 1L,
        planId = OFFICIAL_PLAN_ID,
        name = "30天居家爆炸训练",
        description = "居家徒手循环训练，每天组合两个身体部位，无需器械",
        type = WorkoutPlanType.OFFICIAL.code,
        goal = WorkoutGoal.SHAPE.code,
        cycleDays = WORKOUT_TOTAL_DAYS,
        createdTime = SEED_CREATED_TIME,
    )

    /** 30 个训练日（id = dayNumber，确定性）。 */
    val days: List<WorkoutDayEntity> = WORKOUT_PLANS.map { source ->
        WorkoutDayEntity(
            id = source.dayNumber.toLong(),
            planId = OFFICIAL_PLAN_ID,
            dayNumber = source.dayNumber,
            title = source.bodyParts,
            bodyParts = source.bodyParts,
            description = source.description,
        )
    }

    /** 动作库（按名去重，id 从 1 递增；元数据复用 [ExerciseLibrarySeed]）。 */
    val exercises: List<ExerciseEntity> = buildList {
        WORKOUT_PLANS
            .flatMap { it.exercises }
            .map { it.name }
            .distinct()
            .forEachIndexed { index, name ->
                val library = ExerciseLibrarySeed.byName(name)
                val note = WORKOUT_PLANS
                    .flatMap { it.exercises }
                    .firstOrNull { it.name == name }
                    ?.note
                    .orEmpty()
                add(
                    library?.copy(id = (index + 1).toLong())
                        ?: ExerciseEntity(
                            id = (index + 1).toLong(),
                            name = name,
                            bodyPart = "全身",
                            difficulty = ExerciseDifficulty.BEGINNER.code,
                            description = note,
                        )
                )
            }
    }

    /** 「天 ↔ 动作」关联（id 从 1 递增；dayId = dayNumber，exerciseId 见 [exercises]）。 */
    val links: List<WorkoutExerciseEntity> = buildList {
        var linkId = 1L
        val exerciseIds = exercises.associate { it.name to it.id }
        WORKOUT_PLANS.forEach { source ->
            val dayId = source.dayNumber.toLong()
            source.exercises.forEach exLoop@{ ex ->
                val exerciseId = exerciseIds[ex.name] ?: return@exLoop
                add(
                    WorkoutExerciseEntity(
                        id = linkId++,
                        dayId = dayId,
                        exerciseId = exerciseId,
                        sets = ex.sets,
                        reps = ex.reps,
                        duration = ex.durationSeconds,
                        restTime = DEFAULT_REST_SECONDS,
                        note = ex.note,
                    )
                )
            }
        }
    }
}
