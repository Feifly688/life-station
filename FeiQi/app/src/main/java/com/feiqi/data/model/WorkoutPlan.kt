package com.feiqi.data.model

/**
 * 30 天居家训练计划（**静态数据**，与打卡记录分离）。
 *
 * ## 为什么计划不带 completed
 * 计划是**不可变常量**，完成状态一律由 `WorkoutRecordEntity`（Room 表）派生 ——
 * 避免"静态数据 + 数据库"双写同一状态而出现不一致。
 *
 * ## 计划编排
 * 采用「7 天为一个循环」的居家分化训练编排，30 天依次循环：
 * 胸+三头 / 肩+腹 / 背+二头 / 腿+臀 / 胸+肩 / 腹+手臂 / 恢复。
 * 第 1–7 天为需求指定的正式内容，第 8 天起按同一循环续排，
 * 便于后续替换/扩展为更多训练计划（见 [PLAN_ID]）。
 */

/** 计划动作。 */
data class Exercise(
    val name: String,
    /** 组数，如 4。 */
    val sets: Int,
    /** 次数描述：如「12次」「力竭」。 */
    val reps: String,
    /** 时长动作的秒数（如平板支撑 45 秒）；非计时动作为 0。 */
    val durationSeconds: Int = 0,
    /** 动作提示/替代动作。 */
    val note: String = "",
)

/** 某一天的训练。 */
data class WorkoutPlan(
    /** 计划 id，便于以后接入多套计划。 */
    val planId: String,
    /** 第几天，从 1 开始。 */
    val dayNumber: Int,
    /** 训练部位，如「胸 + 三头」。 */
    val bodyParts: String,
    val title: String,
    val description: String,
    val exercises: List<Exercise>,
)

/** 当前计划 id。 */
const val PLAN_ID: String = "home30"

/** 训练总天数。 */
const val WORKOUT_TOTAL_DAYS: Int = 30

/** 7 天循环模板（第 1–7 天）。 */
private val CYCLE: List<Pair<String, List<Exercise>>> = listOf(
    "胸 + 三头" to listOf(
        Exercise("俯卧撑", 4, "力竭", note = "做不了标准动作可以改跪姿"),
        Exercise("宽距俯卧撑", 3, "12次"),
        Exercise("慢速俯卧撑", 3, "力竭", note = "下放 3 秒，感受胸部拉伸"),
        Exercise("钻石俯卧撑", 3, "力竭"),
        Exercise("臂屈伸", 3, "15次"),
    ),
    "肩 + 腹" to listOf(
        Exercise("Pike 俯卧撑", 3, "12次", note = "臀部抬高，肩部发力"),
        Exercise("侧平举", 3, "15次", note = "可用水瓶代替"),
        Exercise("卷腹", 3, "20次"),
        Exercise("平板支撑", 3, "力竭", durationSeconds = 45),
        Exercise("登山跑", 3, "40秒", durationSeconds = 40),
    ),
    "背 + 二头" to listOf(
        Exercise("超人式", 3, "15次", note = "顶端停留 1 秒"),
        Exercise("Y 字伸展", 3, "12次"),
        Exercise("毛巾划船", 3, "15次", note = "毛巾绕过门把手，脚后撤发力"),
        Exercise("弯举", 3, "15次", note = "可用水瓶代替"),
    ),
    "腿 + 臀" to listOf(
        Exercise("深蹲", 4, "20次"),
        Exercise("箭步蹲", 3, "12次/侧"),
        Exercise("臀桥", 3, "20次", note = "顶峰夹紧 1 秒"),
        Exercise("靠墙静蹲", 3, "45秒", durationSeconds = 45),
    ),
    "胸 + 肩" to listOf(
        Exercise("标准俯卧撑", 3, "力竭"),
        Exercise("窄距俯卧撑", 3, "12次"),
        Exercise("反向飞鸟", 3, "15次", note = "俯身反向开合"),
        Exercise("耸肩", 3, "20次"),
    ),
    "腹 + 手臂" to listOf(
        Exercise("俄罗斯转体", 3, "20次"),
        Exercise("死虫式", 3, "16次"),
        Exercise("窄距俯卧撑", 3, "力竭"),
        Exercise("跪姿俯卧撑", 3, "15次"),
        Exercise("侧支撑", 3, "30秒/侧", durationSeconds = 30),
    ),
    "恢复训练" to listOf(
        Exercise("全身拉伸", 1, "10分钟", durationSeconds = 600, note = "每个动作 30 秒"),
        Exercise("散步", 1, "20分钟", durationSeconds = 1200),
        Exercise("深呼吸放松", 1, "5分钟", durationSeconds = 300),
    ),
)

/** 30 天完整计划（按 7 天循环续排）。 */
val WORKOUT_PLANS: List<WorkoutPlan> = (1..WORKOUT_TOTAL_DAYS).map { day ->
    val (parts, exercises) = CYCLE[(day - 1) % CYCLE.size]
    WorkoutPlan(
        planId = PLAN_ID,
        dayNumber = day,
        bodyParts = parts,
        title = "Day $day · $parts",
        description = if (parts == "恢复训练") "轻量恢复日，重点拉伸与放松" else "居家徒手 · ${exercises.size} 个动作",
        exercises = exercises,
    )
}

/** 第 dayNumber 天（1..30）的计划。 */
fun planOfDay(dayNumber: Int): WorkoutPlan? =
    WORKOUT_PLANS.firstOrNull { it.dayNumber == dayNumber }

/**
 * 依据已完成的天数集合，推导「当前进度」。
 *
 * @param completedDays 已完成打卡的天数
 * @return 当前应训练的天（已完成的下一项，循环推进到 30 为止）、完成数、进度百分比
 */
data class WorkoutProgress(
    val currentDay: Int,
    val completedCount: Int,
    val percent: Float,
)

/** 完成度进度（纯函数，便于单测）。 */
fun computeWorkoutProgress(completedDays: Set<Int>): WorkoutProgress {
    val clamped = completedDays.filter { it in 1..WORKOUT_TOTAL_DAYS }.toSet()
    val completed = clamped.size
    // 当前训练天数：第一个未完成的天；全部完成则停在最后一天
    val current = (1..WORKOUT_TOTAL_DAYS).firstOrNull { it !in clamped } ?: WORKOUT_TOTAL_DAYS
    return WorkoutProgress(
        currentDay = current,
        completedCount = completed,
        percent = completed * 100f / WORKOUT_TOTAL_DAYS,
    )
}

/**
 * 连续训练天数（纯函数）。
 *
 * 规则：从今天（或昨天）往前数连续已完成的天数；今天还没练但昨天练了，
 * 连续记录**保留**（当天补练不影响连续），否则归零。
 */
fun computeStreak(completedDays: Set<Int>, today: Int): Int {
    if (completedDays.isEmpty()) return 0
    val clamped = completedDays.filter { it in 1..WORKOUT_TOTAL_DAYS }.toSet()
    var cursor = when {
        today in clamped -> today
        today - 1 in clamped -> today - 1
        else -> return 0
    }
    var streak = 0
    while (cursor in clamped) {
        streak++
        cursor--
    }
    return streak
}