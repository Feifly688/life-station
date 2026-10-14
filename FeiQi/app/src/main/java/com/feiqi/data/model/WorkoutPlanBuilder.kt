package com.feiqi.data.model

/**
 * 用户自建训练计划的**排布算法**（纯函数，无数据库 / 无 Android 依赖，可单测）。
 *
 * ## 设计要点
 * 1. **创建页给的是「大类部位」**（胸部/背部/肩部/腿部/手臂/腹部），
 *    底层动作库是细粒度部位（胸/三头/背/二头/肩/腿/臀/腹/全身），
 *    映射集中在 [BodyCategory.detailParts]，便于以后扩展动作库。
 * 2. **训练日先铺满再穿插休息日**：避免出现"连休 3 天"或"结尾连续训练 5 天"的极端排布。
 * 3. 排布结果与 UI 无关，UI 只负责渲染 [PlanDraft]。
 */

/** 用户创建计划时可选的 6 大类部位。 */
enum class BodyCategory(
    val label: String,
    val emoji: String,
    /** 映射到动作库的细粒度部位（中文）。 */
    val detailParts: List<String>,
) {
    CHEST("胸部", "💪", listOf("胸", "三头")),
    BACK("背部", "🔙", listOf("背", "二头")),
    SHOULDER("肩部", "🤸", listOf("肩")),
    LEG("腿部", "🦵", listOf("腿", "臀")),
    ARM("手臂", "💥", listOf("二头", "三头")),
    CORE("腹部", "🔥", listOf("腹"));

    /** 「胸 + 三头」这样的展示串。 */
    val displayParts: String get() = detailParts.joinToString(" + ")

    companion object {
        /** 按名称解析（容错）。 */
        fun fromLabel(label: String): BodyCategory =
            entries.firstOrNull { it.label == label } ?: CHEST
    }
}

/** 创建向导第 2 步的周期选项。 */
val WORKOUT_CYCLE_OPTIONS: List<Int> = listOf(7, 14, 30)

/** 创建向导第 3 步的频率选项（每周训练天数）。 */
val WORKOUT_FREQUENCY_OPTIONS: List<Int> = listOf(3, 4, 5, 6)

/** 频率上限：一天最多安排 6 个动作。 */
const val MAX_DAILY_EXERCISES: Int = 6

/** 单个训练日最少动作数（低于此值视为"未配置完成"）。 */
const val MIN_DAILY_EXERCISES: Int = 1

/** 计划名称长度上限。 */
const val MAX_PLAN_NAME_LENGTH: Int = 20

/** 排布后的单个训练日（尚未绑定具体动作 id）。 */
data class PlannedDay(
    val dayNumber: Int,
    /** null = 休息日。 */
    val category: BodyCategory?,
) {
    val isRest: Boolean get() = category == null
    val title: String get() = category?.displayParts ?: "休息"
}

/**
 * 生成周期内的「训练日 / 休息日」排布。
 *
 * ## 规则
 * - 每周训练 [frequencyPerWeek] 天，7 天为一个排布循环；
 * - **训练日优先铺在循环前段**（保证"练 → 休 → 练"的规律节奏），
 *   例如 3 天/周 → 周一、周三、周五训练；
 * - 训练日数上限 = 周期天数（周期短于频率时按周期封顶）；
 * - 至少排 1 个训练日（frequency 与 cycleDays 均为非法值时的兜底）。
 *
 * @param cycleDays 周期天数（≥1）
 * @param frequencyPerWeek 每周训练天数（1..7）
 */
fun layoutTrainingDays(cycleDays: Int, frequencyPerWeek: Int): List<PlannedDay> {
    val days = cycleDays.coerceAtLeast(1)
    val freq = frequencyPerWeek.coerceIn(1, 7)
    // 训练日的「星期几」（1..7），等间隔铺开：freq=3 → [1,3,5]
    val trainWeekdays = (0 until freq).map { (it * 7) / freq + 1 }.distinct().ifEmpty { listOf(1) }
    val limit = days.coerceAtMost(maxOf(1, days * freq / 7 + days % 7))
    var used = 0
    return (1..days).map { day ->
        val weekday = ((day - 1) % 7) + 1
        val isTraining = weekday in trainWeekdays && used < limit
        if (isTraining) used++
        // 具体部位留给 assignCategories 分配，这里只标记"是不是训练日"
        PlannedDay(dayNumber = day, category = if (isTraining) BodyCategory.CHEST else null)
    }
}

/**
 * 把用户勾选的大类部位**轮转**分配到训练日上。
 *
 * - 休息日保持 null；
 * - 第 k 个训练日取 `categories[k % categories.size]`，保证各部位均匀出现；
 * - [categories] 为空时返回全休息日（调用方应据此禁用保存）。
 */
fun assignCategories(
    layout: List<PlannedDay>,
    categories: List<BodyCategory>,
): List<PlannedDay> {
    if (categories.isEmpty()) return layout.map { PlannedDay(it.dayNumber, null) }
    var trainingIndex = 0
    return layout.map { planned ->
        if (planned.isRest) {
            PlannedDay(planned.dayNumber, null)
        } else {
            val category = categories[trainingIndex % categories.size]
            trainingIndex++
            PlannedDay(planned.dayNumber, category)
        }
    }
}

/** 创建向导的最终草稿（可保存）。 */
data class PlanDraft(
    val name: String,
    val goal: WorkoutGoal,
    val cycleDays: Int,
    val frequencyPerWeek: Int,
    val categories: List<BodyCategory>,
    val days: List<PlannedDay>,
) {
    val trainingDayCount: Int get() = days.count { !it.isRest }
    val categorySummary: String
        get() = if (categories.isEmpty()) "未设置部位"
        else categories.joinToString(" · ") { "${it.emoji} ${it.label}" }
}

/** 名称校验（空 / 超长 / 空白）。 */
fun isValidPlanName(name: String): Boolean =
    name.isNotBlank() && name.trim().length <= MAX_PLAN_NAME_LENGTH

/** 草稿是否可保存（名称合法 + 至少 1 个部位 + 至少 1 个训练日）。 */
fun isPlanDraftSaveable(draft: PlanDraft): Boolean =
    isValidPlanName(draft.name) &&
        draft.categories.isNotEmpty() &&
        draft.trainingDayCount >= MIN_DAILY_EXERCISES