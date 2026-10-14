package com.feiqi.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * v2 阶段 4 统计规则单测。
 *
 * 重点覆盖三类边界：
 * 1. **日历连续** vs 计划内天序号（口径不同，不能互相替代）
 * 2. **本月进度**分母是自然月天数，不是计划周期
 * 3. **时长估算**对「力竭 / 计时动作 / 单组」的处理
 */
class WorkoutStatsTest {

    private fun d(text: String): LocalDate = LocalDate.parse(text)

    // ---------------- parseRepCount ----------------

    @Test
    fun `解析次数描述取第一个整数`() {
        assertEquals(12, parseRepCount("12次"))
        assertEquals(20, parseRepCount("20次/侧"))
        assertEquals(15, parseRepCount("15次"))
        assertEquals(3, parseRepCount("3组"))
    }

    @Test
    fun `无法解析次数时返回 null`() {
        assertEquals(null, parseRepCount("力竭"))
        assertEquals(null, parseRepCount(""))
        assertEquals(null, parseRepCount("至力竭"))
    }

    // ---------------- estimateExerciseSeconds ----------------

    @Test
    fun `计时动作按每组时长乘组数并加组间休息`() {
        // 平板支撑 3 组 × 45 秒，组间休息 90 → 45*3 + 90*2 = 315
        val seconds = estimateExerciseSeconds(sets = 3, reps = "力竭", durationSeconds = 45, restTime = 90)
        assertEquals(315, seconds)
    }

    @Test
    fun `次数动作按每次三秒折算并保底二十秒`() {
        // 12 次 × 3 秒 = 36 秒，4 组，休息 90 → 36*4 + 90*3 = 414
        assertEquals(414, estimateExerciseSeconds(4, "12次", 0, 90))
    }

    @Test
    fun `力竭无次数时按保底二十秒计算`() {
        // 20 * 3 + 90 * 2 = 240
        assertEquals(240, estimateExerciseSeconds(3, "力竭", 0, 90))
    }

    @Test
    fun `单组不计组间休息`() {
        assertEquals(36, estimateExerciseSeconds(1, "12次", 0, 90))
    }

    @Test
    fun `组数非法时按一组计算`() {
        // sets=0 被强制视为 1 组 → 36，不计休息
        assertEquals(36, estimateExerciseSeconds(0, "12次", 0, 90))
    }

    @Test
    fun `休息时间为负时按零处理`() {
        // 36*3 + (-10→0)*2 = 108
        assertEquals(108, estimateExerciseSeconds(3, "12次", 0, -10))
    }

    // ---------------- estimateDaySeconds ----------------

    @Test
    fun `空计划的估算时长为零`() {
        assertEquals(0, estimateDaySeconds(null))
        assertEquals(0, estimateDaySeconds(WorkoutPlan("p", 1, "胸", "t", "d", emptyList())))
    }

    @Test
    fun `单日估算含每个动作间的转换时间`() {
        val plan = WorkoutPlan(
            planId = "p",
            dayNumber = 1,
            bodyParts = "腹",
            title = "t",
            description = "d",
            exercises = listOf(
                Exercise("卷腹", 3, "20次"),      // 60*3 + 90*2 = 360
                Exercise("平板支撑", 3, "力竭", durationSeconds = 45), // 45*3 + 90*2 = 315
            ),
        )
        // (360 + 315) + 60 * 2 = 795
        assertEquals(795, estimateDaySeconds(plan))
    }

    // ---------------- secondsToMinutes ----------------

    @Test
    fun `秒转分钟四舍五入且保底一分钟`() {
        assertEquals(1, secondsToMinutes(0))
        assertEquals(1, secondsToMinutes(30))
        assertEquals(1, secondsToMinutes(59))
        // 89 秒 = 1.48 分钟 → 四舍五入仍是 1 分（.5 边界在 90 秒）
        assertEquals(1, secondsToMinutes(89))
        assertEquals(2, secondsToMinutes(90))
        assertEquals(35, secondsToMinutes(35 * 60))
    }

    @Test
    fun `负秒数按零处理`() {
        assertEquals(1, secondsToMinutes(-100))
    }

    // ---------------- estimatedDurationLabel ----------------

    @Test
    fun `不足一小时显示分钟`() {
        assertEquals("约 35 分钟", estimatedDurationLabel(35 * 60))
    }

    @Test
    fun `超过一小时显示小时与分钟`() {
        // 6h25m
        assertEquals("约 6 小时 25 分钟", estimatedDurationLabel(6 * 3600 + 25 * 60))
    }

    @Test
    fun `整小时省略分钟`() {
        assertEquals("约 2 小时", estimatedDurationLabel(2 * 3600))
    }

    @Test
    fun `零时长也显示约零分钟而不是空白`() {
        assertEquals("约 1 分钟", estimatedDurationLabel(0))
    }

    // ---------------- computeCalendarStreak ----------------

    @Test
    fun `日历连续从今天往回数`() {
        val today = d("2026-10-14")
        val dates = listOf(d("2026-10-12"), d("2026-10-13"), d("2026-10-14"))
        assertEquals(3, computeCalendarStreak(dates, today))
    }

    @Test
    fun `今天没练但昨天练了连续记录保留`() {
        val today = d("2026-10-14")
        val dates = listOf(d("2026-10-12"), d("2026-10-13"))
        assertEquals(2, computeCalendarStreak(dates, today))
    }

    @Test
    fun `中断后重新计算只数昨天及以后`() {
        val today = d("2026-10-14")
        // 今天(14) 未打卡但昨天(13) 打了 → 从 13 往回数，10~13 连续 4 天
        // 10 号之前若断在 9 号，9 号不会被计入（cursor 走到 10 号发现不在集合即停）
        val dates = listOf(d("2026-10-10"), d("2026-10-11"), d("2026-10-12"), d("2026-10-13"))
        assertEquals(4, computeCalendarStreak(dates, today))
        // 10 号缺失 → 只能数回 11 12 13 共 3 天
        val gap = listOf(d("2026-10-11"), d("2026-10-12"), d("2026-10-13"))
        assertEquals(3, computeCalendarStreak(gap, today))
    }

    @Test
    fun `今天昨天都没练则连续为零`() {
        val today = d("2026-10-14")
        val dates = listOf(d("2026-10-10"), d("2026-10-11"))
        assertEquals(0, computeCalendarStreak(dates, today))
    }

    @Test
    fun `空集合连续为零`() {
        assertEquals(0, computeCalendarStreak(emptyList(), d("2026-10-14")))
    }

    @Test
    fun `重复日期不重复计数`() {
        val today = d("2026-10-14")
        val dates = listOf(d("2026-10-14"), d("2026-10-14"), d("2026-10-13"))
        assertEquals(2, computeCalendarStreak(dates, today))
    }

    @Test
    fun `日历连续与计划内天序号口径结果不同`() {
        val today = d("2026-10-14")
        // 计划内第 3、4、5 天完成，但日历上只练了 10-13 一天（今天没练、昨天也没练）
        val planDays = setOf(3, 4, 5)
        val calendarDates = listOf(d("2026-10-13"))
        val byDayIndex = computeStreak(planDays, 6, 30)
        val byCalendar = computeCalendarStreak(calendarDates, today)
        assertTrue("两个口径允许得出不同结果", byDayIndex != byCalendar)
    }

    // ---------------- monthProgress ----------------

    @Test
    fun `本月进度分母是当月自然天数`() {
        val today = d("2026-10-14")
        val dates = (1..18).map { d("2026-10-%02d".format(it)) }
        val progress = monthProgress(dates, today)
        assertEquals(18, progress.doneDays)
        assertEquals(31, progress.totalDays)
        assertFalse(progress.isFull)
    }

    @Test
    fun `二月份按闰年二十九天计算`() {
        val today = d("2024-02-15")
        assertEquals(29, monthProgress(emptyList(), today).totalDays)
        val today2 = d("2026-02-15")
        assertEquals(28, monthProgress(emptyList(), today2).totalDays)
    }

    @Test
    fun `进度比例在零到一之间`() {
        val today = d("2026-10-14")
        val dates = listOf(d("2026-10-01"), d("2026-10-02"))
        val progress = monthProgress(dates, today)
        assertTrue(progress.fraction > 0f && progress.fraction < 1f)
    }

    @Test
    fun `练满整月时标记为满`() {
        val today = d("2026-10-31")
        val dates = (1..31).map { d("2026-10-%02d".format(it)) }
        val progress = monthProgress(dates, today)
        assertTrue(progress.isFull)
        assertEquals(1f, progress.fraction, 0.0001f)
    }

    @Test
    fun `跨月记录不计入本月`() {
        val today = d("2026-10-14")
        val dates = listOf(d("2026-09-30"), d("2026-10-01"), d("2026-11-01"))
        assertEquals(1, monthProgress(dates, today).doneDays)
    }

    @Test
    fun `重复打卡同一天只计一次`() {
        val today = d("2026-10-14")
        val dates = listOf(d("2026-10-01"), d("2026-10-01"), d("2026-10-01"))
        assertEquals(1, monthProgress(dates, today).doneDays)
    }

    @Test
    fun `零进度时比例为零`() {
        val progress = monthProgress(emptyList(), d("2026-10-14"))
        assertEquals(0f, progress.fraction, 0.0001f)
        assertEquals(0, progress.doneDays)
    }

    // ---------------- estimateTotalSeconds ----------------

    @Test
    fun `累计时长只累加符合条件的记录`() {
        val marks = listOf(
            WorkoutDateMark(d("2026-10-01")),
            WorkoutDateMark(d("2026-10-02")),
            WorkoutDateMark(d("2026-10-03")),
        )
        val total = estimateTotalSeconds(
            marks = marks,
            isCompleted = { it.date.dayOfMonth != 2 },
            secondsFor = { 600 },
        )
        assertEquals(1200, total)
    }

    @Test
    fun `空记录累计为零`() {
        assertEquals(0, estimateTotalSeconds(emptyList(), secondsFor = { 600 }))
    }

    // ---------------- 与年度月的对应 ----------------

    @Test
    fun `本月进度与年月对象一致`() {
        val today = d("2026-10-14")
        val ym = YearMonth.of(2026, 10)
        assertEquals(ym.lengthOfMonth(), monthProgress(emptyList(), today).totalDays)
    }
}
