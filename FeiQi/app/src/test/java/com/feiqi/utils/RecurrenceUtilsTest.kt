package com.feiqi.utils

import com.feiqi.data.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 重复规则的日期推算用例。
 * 固定使用具体日期，不依赖「今天」，保证任何时间运行结果一致。
 */
class RecurrenceUtilsTest {

    private fun d(text: String): LocalDate = LocalDate.parse(text)

    // ---------- nextOccurrence ----------

    @Test
    fun next_none_keepsOriginalDate() {
        val from = d("2026-09-21")
        assertEquals(from, RecurrenceUtils.nextOccurrence(from, Recurrence.NONE))
    }

    @Test
    fun next_daily_plusOneDay() {
        assertEquals(d("2026-02-01"), RecurrenceUtils.nextOccurrence(d("2026-01-31"), Recurrence.DAILY))
    }

    @Test
    fun next_weekdays_fromFriday_jumpsToMonday() {
        // 2026-09-18 是周五 → 下一个工作日为 2026-09-21 周一
        assertEquals(d("2026-09-21"), RecurrenceUtils.nextOccurrence(d("2026-09-18"), Recurrence.WEEKDAYS))
    }

    @Test
    fun next_weekdays_fromSaturday_jumpsToMonday() {
        assertEquals(d("2026-09-21"), RecurrenceUtils.nextOccurrence(d("2026-09-19"), Recurrence.WEEKDAYS))
    }

    @Test
    fun next_weekdays_fromSunday_jumpsToMonday() {
        assertEquals(d("2026-09-21"), RecurrenceUtils.nextOccurrence(d("2026-09-20"), Recurrence.WEEKDAYS))
    }

    @Test
    fun next_weekdays_withinWeek_isNextDay() {
        assertEquals(d("2026-09-22"), RecurrenceUtils.nextOccurrence(d("2026-09-21"), Recurrence.WEEKDAYS))
    }

    @Test
    fun next_weekly_plusSevenDays() {
        assertEquals(d("2026-09-28"), RecurrenceUtils.nextOccurrence(d("2026-09-21"), Recurrence.WEEKLY))
    }

    @Test
    fun next_monthly_sameDayNextMonth() {
        assertEquals(d("2026-10-15"), RecurrenceUtils.nextOccurrence(d("2026-09-15"), Recurrence.MONTHLY))
    }

    @Test
    fun next_monthly_clampsToShorterMonth() {
        // 1/31 → 2 月没有 31 日，取 2 月最后一天
        assertEquals(d("2026-02-28"), RecurrenceUtils.nextOccurrence(d("2026-01-31"), Recurrence.MONTHLY))
    }

    @Test
    fun next_monthly_keepsMonthEndAnchor() {
        // 2/28 是 2026 年 2 月最后一天 → 下个月取月末 3/31（避免锚点丢成「28 日」）
        assertEquals(d("2026-03-31"), RecurrenceUtils.nextOccurrence(d("2026-02-28"), Recurrence.MONTHLY))
    }

    @Test
    fun next_yearly_sameMonthDay() {
        assertEquals(d("2027-09-21"), RecurrenceUtils.nextOccurrence(d("2026-09-21"), Recurrence.YEARLY))
    }

    @Test
    fun next_yearly_leapDayFallsBackToFeb28() {
        assertEquals(d("2025-02-28"), RecurrenceUtils.nextOccurrence(d("2024-02-29"), Recurrence.YEARLY))
    }

    // ---------- advanceToOnOrAfter ----------

    @Test
    fun advance_daily_landsOnTarget() {
        assertEquals(
            d("2026-09-21"),
            RecurrenceUtils.advanceToOnOrAfter(d("2026-09-01"), Recurrence.DAILY, d("2026-09-21"))
        )
    }

    @Test
    fun advance_weekly_landsOnNextMatchingWeekday() {
        // 上次停在 9/9（周三），今天是 9/21（周一）→ 推到 9/23（周三）
        assertEquals(
            d("2026-09-23"),
            RecurrenceUtils.advanceToOnOrAfter(d("2026-09-09"), Recurrence.WEEKLY, d("2026-09-21"))
        )
    }

    @Test
    fun advance_weekdays_skipsWeekend() {
        // 上周五 → 今天（周一）即为下一个工作日
        assertEquals(
            d("2026-09-21"),
            RecurrenceUtils.advanceToOnOrAfter(d("2026-09-18"), Recurrence.WEEKDAYS, d("2026-09-21"))
        )
    }

    @Test
    fun advance_monthly_skipsOccurrenceAlreadyPassed() {
        // 8/15 的每月条目，今天 9/21：本月 9/15 已过 → 顺到 10/15
        assertEquals(
            d("2026-10-15"),
            RecurrenceUtils.advanceToOnOrAfter(d("2026-08-15"), Recurrence.MONTHLY, d("2026-09-21"))
        )
    }

    @Test
    fun advance_monthly_landsOnSameDayThisMonthWhenNotYetPassed() {
        // 8/25 的每月条目，今天 9/21：本月 9/25 尚未到 → 落到 9/25
        assertEquals(
            d("2026-09-25"),
            RecurrenceUtils.advanceToOnOrAfter(d("2026-08-25"), Recurrence.MONTHLY, d("2026-09-21"))
        )
    }

    @Test
    fun advance_none_doesNotMove() {
        val from = d("2026-01-01")
        assertEquals(from, RecurrenceUtils.advanceToOnOrAfter(from, Recurrence.NONE, d("2026-09-21")))
    }

    @Test
    fun advance_futureDate_keptAsIs() {
        val from = d("2026-12-31")
        assertEquals(from, RecurrenceUtils.advanceToOnOrAfter(from, Recurrence.DAILY, d("2026-09-21")))
    }
}
