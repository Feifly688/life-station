package com.feiqi.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 逾期判定规则：
 * - 未开启提醒 → 永不判逾期；
 * - **跨日（date < today）且已开提醒 → 过期**（沿用既有口径，无具体时间也算）；
 * - 当天且设置了时间 → 过了该时刻才算过期；当天无时间 → 视为「当天提醒」，不算过期。
 * 判定用 [today]/[now] 显式传入，避免依赖运行时刻。
 */
class ScheduleTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun schedule(
        date: LocalDate,
        time: LocalTime? = null,
        reminder: Boolean = true,
        completed: Boolean = false,
        completedLate: Boolean = false
    ) = Schedule(
        title = "测试",
        date = date,
        time = time,
        reminder = reminder,
        completed = completed,
        completedLate = completedLate
    )

    @Test
    fun todayWithoutTime_isNotOverdue() {
        // 当天、无具体时间：视为「当天提醒」，不判逾期
        assertFalse(schedule(today, time = null).isOverdue(today, LocalTime.of(23, 59)))
    }

    @Test
    fun pastDateWithoutTime_isOverdue() {
        // 跨日仍未完成：沿用既有口径（date < today 即过期），即使没有具体时间
        assertTrue(schedule(today.minusDays(3), time = null).isOverdue(today))
    }

    @Test
    fun noReminder_neverOverdue() {
        assertFalse(
            schedule(today.minusDays(1), LocalTime.of(9, 0), reminder = false).isOverdue(today)
        )
    }

    @Test
    fun pastDate_isOverdue() {
        assertTrue(schedule(today.minusDays(1), LocalTime.of(9, 0)).isOverdue(today))
    }

    @Test
    fun todayWithTime_afterDue_isOverdue() {
        assertTrue(
            schedule(today, LocalTime.of(14, 30)).isOverdue(today, LocalTime.of(14, 31))
        )
    }

    @Test
    fun todayWithTime_beforeDue_isNotOverdue() {
        assertFalse(
            schedule(today, LocalTime.of(14, 30)).isOverdue(today, LocalTime.of(14, 29))
        )
        // 恰好等于应完成时刻：不算逾期
        assertFalse(
            schedule(today, LocalTime.of(14, 30)).isOverdue(today, LocalTime.of(14, 30))
        )
    }

    @Test
    fun futureDate_isNotOverdue() {
        assertFalse(schedule(today.plusDays(1), LocalTime.of(9, 0)).isOverdue(today))
    }

    @Test
    fun completedLate_flagIsIndependentFromDerivedOverdue() {
        // 完成后「已过期」标签由 completedLate 决定，与完成后再推导出的状态解耦：
        // 按时完成（此刻未过期、未标 late）→ 不显示标签
        val onTime = schedule(today, LocalTime.of(20, 0), completed = true, completedLate = false)
        assertFalse(onTime.isOverdue(today, LocalTime.of(19, 0)))
        assertFalse(onTime.completedLate)
        // 过期后补完成 → 标签保留
        val late = schedule(today.minusDays(1), LocalTime.of(9, 0), completed = true, completedLate = true)
        assertTrue(late.completedLate)
    }
}
