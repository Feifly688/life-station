package com.feiqi.data.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * 健身统计纯函数（v2 阶段 4）。
 *
 * ## 为什么要单独抽出来
 * 顶部要展示的「连续训练 / 本月完成 / 累计时长」是**自律感数据**，
 * 它们的推导逻辑必须可单测、且与 UI 无关，故全部收在这里。
 *
 * ## 三条口径严格分离（不要混用）
 * | 展示位 | 函数 | 口径 |
 * |--------|------|------|
 * | 顶部「连续训练 N 天」 | [computeCalendarStreak] | **真实日历连续**，跨全部计划 |
 * | 顶部「本月 x / y 次」 | [monthProgress] | **自然月**天数做分母，跨全部计划 |
 * | 顶部「累计约 x 小时」 | [estimateDaySeconds] 之和 | 按动作配置**估算**，跨全部计划 |
 * | 进度卡「Day x / 30」 | `computeWorkoutProgress` | 计划内天序号，**不是日历** |
 *
 * ⚠️ [computeStreak]（旧）是**计划内天序号口径**，只服务进度卡；
 * 与 [computeCalendarStreak] 语义不同，两者不可互相替代、也不可同时展示。
 *
 * ## 时长是估算值
 * `workout_records` **没有**真实计时字段，且本阶段明确不改表结构，
 * 因此时长一律由动作配置（组数 / 次数 / 休息）推算，UI 文案**必须带「约」**。
 */

/** 单个动作默认组间休息（秒）——与 `WorkoutExerciseEntity.restTime` 默认值一致。 */
const val DEFAULT_REST_SECONDS: Int = 90

/** 动作间休息（秒）：每个动作之间额外留出的转换时间。 */
const val EXERCISE_GAP_SECONDS: Int = 60

/** 单组最短估算秒数：避免「力竭」这类描述算出 0 秒。 */
const val MIN_SET_SECONDS: Int = 20

/** 一次动作约需秒数（无时长描述时按「3 秒 / 次」折算）。 */
const val SECONDS_PER_REP: Int = 3

/** 打卡记录的最简形态：只需日期即可参与日历口径统计。 */
data class WorkoutDateMark(
    /** 打卡日期（yyyy-MM-dd 解析出的 [LocalDate]）。 */
    val date: LocalDate,
)

/**
 * 从次数描述里解析出次数数字，取**第一个**整数。
 *
 * 支持「12次」「20次/侧」「12次/侧」「16次」等；
 * 「力竭」「12次一组」这类无法解析时返回 null（由 [MIN_SET_SECONDS] 兜底）。
 */
fun parseRepCount(reps: String): Int? {
    val end = reps.indexOfFirst { !it.isDigit() }
    val digits = if (end < 0) reps else reps.substring(0, end)
    return digits.toIntOrNull()
}

/**
 * 单个动作的估算秒数。
 *
 * ```
 * 单组秒数 = 若是计时动作(durationSeconds>0) → durationSeconds
 *           否则 max(最小单组秒数, 解析出的次数 × 每秒数)
 * 动作秒数 = 单组秒数 × 组数 + 组间休息 × (组数 - 1)
 * ```
 *
 * `durationSeconds` 是**每组**的时长（如平板支撑 3 组 × 45 秒），
 * 因此仍要乘组数；`sets == 1` 时不计组间休息。
 */
fun estimateExerciseSeconds(
    sets: Int,
    reps: String,
    durationSeconds: Int,
    restTime: Int = DEFAULT_REST_SECONDS,
): Int {
    val setCount = sets.coerceAtLeast(1)
    val perSet = if (durationSeconds > 0) {
        durationSeconds
    } else {
        val repCount = parseRepCount(reps)
        if (repCount == null) {
            MIN_SET_SECONDS
        } else {
            (repCount * SECONDS_PER_REP).coerceAtLeast(MIN_SET_SECONDS)
        }
    }
    val rest = restTime.coerceAtLeast(0) * (setCount - 1)
    return perSet * setCount + rest
}

/** 某个训练日的估算秒数：Σ 动作秒数 + 动作间休息。空计划返回 0。 */
fun estimateDaySeconds(plan: WorkoutPlan?): Int {
    if (plan == null || plan.exercises.isEmpty()) return 0
    val moves = plan.exercises.sumOf { exercise ->
        estimateExerciseSeconds(
            sets = exercise.sets,
            reps = exercise.reps,
            durationSeconds = exercise.durationSeconds,
            restTime = exercise.restTime,
        )
    }
    return moves + EXERCISE_GAP_SECONDS * plan.exercises.size
}

/** 估算秒数 → 分钟（四舍五入，至少 1 分钟，避免显示「0 分钟」）。 */
fun secondsToMinutes(seconds: Int): Int = ((seconds.coerceAtLeast(0) + 30) / 60).coerceAtLeast(1)

/**
 * 估算一批打卡记录对应的累计训练秒数。
 *
 * @param marks 打卡记录（只需 [WorkoutDateMark.date] 参与判断）
 * @param isCompleted 某条记录是否计入（用于过滤脏数据）
 * @param secondsFor 某条记录的估算秒数（无法解析到训练日时返回 0）
 */
fun estimateTotalSeconds(
    marks: List<WorkoutDateMark>,
    isCompleted: (WorkoutDateMark) -> Boolean = { true },
    secondsFor: (WorkoutDateMark) -> Int = { 0 },
): Int = marks.filter(isCompleted).sumOf(secondsFor)

/**
 * **真实日历连续**训练天数（跨全部计划）。
 *
 * 规则（与日程模块的「连续打卡」一致）：
 * - 今天已练 → 从今天往回数连续天数；
 * - 今天还没练但昨天练了 → 从昨天往回数（**当天补练不影响连续记录**）；
 * - 今天昨天都没练 → 0。
 *
 * @param dates 所有打卡日期（去重前亦可，重复日期不重复计数）
 * @param today 今天（传参便于单测，避免依赖系统时间）
 */
fun computeCalendarStreak(dates: Collection<LocalDate>, today: LocalDate = LocalDate.now()): Int {
    if (dates.isEmpty()) return 0
    val set = dates.toSet()
    var cursor = when {
        today in set -> today
        today.minusDays(1) in set -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    while (cursor in set) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** 本月训练进度。 */
data class MonthProgress(
    /** 本月已训练天数（去重后的打卡日期数）。 */
    val doneDays: Int,
    /** 本月自然天数（1..31），作分母。 */
    val totalDays: Int,
) {
    /** 进度百分比 0f..1f，供进度条使用。 */
    val fraction: Float
        get() = if (totalDays <= 0) 0f else (doneDays.toFloat() / totalDays).coerceIn(0f, 1f)

    /** 是否已练满整月。 */
    val isFull: Boolean get() = totalDays > 0 && doneDays >= totalDays
}

/**
 * 本月训练进度（**自然月**口径）。
 *
 * 分母是当月自然天数（28/29/30/31），**不是**训练计划周期 ——
 * 「本月练了 18 天」与「30 天计划走到 Day 12」是两个独立概念，不可混用同一条进度条。
 *
 * @param dates 全部打卡日期
 * @param today 今天（传参便于单测）
 */
fun monthProgress(
    dates: Collection<LocalDate>,
    today: LocalDate = LocalDate.now(),
): MonthProgress {
    val ym = YearMonth.of(today.year, today.month)
    val done = dates.toSet().count { YearMonth.from(it) == ym }
    return MonthProgress(doneDays = done, totalDays = ym.lengthOfMonth())
}

/**
 * 累计训练时长的可读文案（**必须体现「约」**，避免被误读为真实计时）。
 *
 * - < 1 小时 → 「约 N 分钟」
 * - ≥ 1 小时 → 「约 N 小时 M 分钟」（整小时则省略分钟）
 */
fun estimatedDurationLabel(totalSeconds: Int): String {
    val minutes = secondsToMinutes(totalSeconds)
    if (minutes < 60) return "约 $minutes 分钟"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "约 $h 小时" else "约 $h 小时 $m 分钟"
}
