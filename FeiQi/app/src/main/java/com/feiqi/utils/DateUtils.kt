package com.feiqi.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val cnMonthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val cnDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
    private val hms: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val fullDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val shortDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 HH:mm:ss", Locale.CHINA)

    fun today(): LocalDate = LocalDate.now()

    fun now(): LocalDateTime = LocalDateTime.now().withNano(0)

    fun iso(date: LocalDate): String = date.format(isoFormatter)

    fun parseIso(value: String): LocalDate = LocalDate.parse(value, isoFormatter)

    fun hms(time: LocalTime): String = time.format(hms)

    fun hm(time: LocalTime): String = time.format(hm)

    fun fullDateTime(value: LocalDateTime): String = value.format(fullDateTime)

    fun shortDateTime(value: LocalDateTime): String = value.format(shortDateTime)

    fun friendly(date: LocalDate): String {
        val today = today()
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            today.plusDays(1) -> "明天"
            else -> date.format(cnDate)
        }
    }

    fun monthDay(date: LocalDate): String = date.format(cnMonthDay)

    /**
     * 日程卡片用的**相对日期标签**：今天 / 昨天 / 明天 / 其余「M月d日」。
     *
     * 与 [friendly] 的区别：其余日期**不带星期**，保持卡片里原有的紧凑样式（[monthDay] 同款）。
     * @param today 判定基准，默认取设备当天；测试可显式传入以免依赖运行日期。
     */
    fun relativeDate(date: LocalDate, today: LocalDate = today()): String = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.plusDays(1) -> "明天"
        else -> monthDay(date)
    }

    /**
     * 日程卡片用的「日期 + 时间」标签。
     *
     * **只有近期待办（今天 / 明天）才精确到时分**，其余只显示日期：
     * - 今天、有提醒时间 → 「今天 14:30」
     * - 明天、有提醒时间 → 「明天 14:30」
     * - 今天 / 明天、无时间 → 「今天」/「明天」
     * - 其他日期 → 「9月26日」（不带时分；[relativeDate] 对昨天/更早同样只给日期）
     *
     * 只比较**日期**（不看具体时刻），因此「今天」覆盖当天 00:00–23:59 的全部日程。
     */
    fun dateTimeLabel(date: LocalDate, time: LocalTime?, today: LocalDate = today()): String {
        val label = relativeDate(date, today)
        val isNearTerm = date == today || date == today.plusDays(1)
        return if (time != null && isNearTerm) "$label ${hm(time)}" else label
    }

    fun monthStart(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun monthEnd(date: LocalDate): LocalDate = date.withDayOfMonth(date.lengthOfMonth())

    fun weekDays(date: LocalDate): List<LocalDate> {
        // 用 ISO DayOfWeek 直接算出本周周一，避免依赖 WeekFields 的本地化周起始（中文习惯周一），
        // 否则在部分 JVM/设备 locale 数据下可能误判为周日起始，导致整周偏移。
        val monday = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        return (0 until 7).map { monday.plusDays(it.toLong()) }
    }

    fun greeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when (hour) {
            in 5..10 -> "早安，慢慢来"
            in 11..13 -> "午安，刚刚好"
            in 14..17 -> "下午好，慢慢来"
            in 18..21 -> "晚上好，今天辛苦了"
            else -> "夜深了，早点休息"
        }
    }

    fun atmosphere(date: LocalDate): String {
        val dayOfMonth = date.dayOfMonth
        return when {
            dayOfMonth <= 5 -> "新的一个月，从一件小事开始"
            dayOfMonth <= 15 -> "月中了，给自己一点掌声"
            dayOfMonth <= 25 -> "月底临近，慢慢收尾"
            else -> "月末了，整理一下再出发"
        }
    }
}
