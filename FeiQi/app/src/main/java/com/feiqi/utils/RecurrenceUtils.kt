package com.feiqi.utils

import com.feiqi.data.model.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 重复规则的日期推算。
 *
 * 语义约定：
 * - [Recurrence.DAILY] 每天 → +1 天
 * - [Recurrence.WEEKDAYS] 周一至周五 → +1 天并跳过周六/周日
 * - [Recurrence.WEEKLY] 每周 → +7 天（同一星期几）
 * - [Recurrence.MONTHLY] 每月 → +1 个月（同日；目标月无该日取月末；当日本身就是月末时下月也取月末，
 *   避免 1/31 → 2/28 → 3/28 这种「锚点丢失」）
 * - [Recurrence.YEARLY] 每年 → +1 年（同月日；2/29 在平年退到 2/28，后续保持 2/28）
 *
 * 注意：[Recurrence.NONE] 不做任何顺延，原样返回。
 */
object RecurrenceUtils {

    /** 推算循环的上限步数，纯防御（防止脏数据导致死循环）。 */
    private const val MAX_STEPS = 4000

    /** 按重复规则推算「下一个应提醒日」。 */
    fun nextOccurrence(from: LocalDate, recurrence: Recurrence): LocalDate = when (recurrence) {
        Recurrence.NONE -> from
        Recurrence.DAILY -> from.plusDays(1)
        Recurrence.WEEKDAYS -> {
            var next = from.plusDays(1)
            while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                next = next.plusDays(1)
            }
            next
        }
        Recurrence.WEEKLY -> from.plusDays(7)
        Recurrence.MONTHLY -> nextMonth(from)
        Recurrence.YEARLY -> from.plusYears(1)
    }

    /**
     * 把日期推进到「不早于 [target] 的下一个应提醒日」，用于 App 启动时补上错过的循环。
     *
     * 例：每周三的清单上次停在两周前，[target] 为今天 → 推到本周（或下周）三。
     * 循环条目**不回补历史欠账**，直接落到下一个应提醒日。
     */
    fun advanceToOnOrAfter(from: LocalDate, recurrence: Recurrence, target: LocalDate): LocalDate {
        if (!recurrence.isRepeating) return from
        if (from >= target) return from
        var cursor = from
        var steps = 0
        while (cursor < target && steps < MAX_STEPS) {
            cursor = nextOccurrence(cursor, recurrence)
            steps++
        }
        return cursor
    }

    /** +1 个月：普通情况取同日；当日为月末时下月也取月末（保住「每月最后一天」的语义）。 */
    private fun nextMonth(from: LocalDate): LocalDate {
        val next = from.plusMonths(1)
        return if (from.dayOfMonth == from.lengthOfMonth()) {
            next.withDayOfMonth(next.lengthOfMonth())
        } else {
            next
        }
    }
}
