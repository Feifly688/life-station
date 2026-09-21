package com.feiqi.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateUtilsTest {

    @Test
    fun friendly_today() {
        val t = DateUtils.today()
        assertEquals("今天", DateUtils.friendly(t))
    }

    @Test
    fun friendly_yesterday() {
        val t = DateUtils.today()
        assertEquals("昨天", DateUtils.friendly(t.minusDays(1)))
    }

    @Test
    fun friendly_tomorrow() {
        val t = DateUtils.today()
        assertEquals("明天", DateUtils.friendly(t.plusDays(1)))
    }

    @Test
    fun friendly_farDate_usesCnDateFormat() {
        val t = DateUtils.today()
        val far = t.plusDays(5)
        val expected = far.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA))
        assertEquals(expected, DateUtils.friendly(far))
    }

    @Test
    fun iso_roundTrip() {
        val d = LocalDate.of(2026, 3, 5)
        assertEquals("2026-03-05", DateUtils.iso(d))
        assertEquals(d, DateUtils.parseIso("2026-03-05"))
    }

    @Test
    fun parseIso_singleDigitPadded() {
        assertEquals(LocalDate.of(2026, 1, 2), DateUtils.parseIso("2026-01-02"))
    }

    @Test
    fun monthStart_and_monthEnd() {
        val feb = LocalDate.of(2026, 2, 15)
        assertEquals(LocalDate.of(2026, 2, 1), DateUtils.monthStart(feb))
        assertEquals(LocalDate.of(2026, 2, 28), DateUtils.monthEnd(feb))
    }

    @Test
    fun monthDay_format() {
        assertEquals("6月15日", DateUtils.monthDay(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun weekDays_startsOnMonday_andHasSevenDays() {
        val monday = LocalDate.of(2026, 6, 15)
        val days = DateUtils.weekDays(monday)
        assertEquals(7, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
        assertEquals(monday, days.first())
    }

    @Test
    fun weekDays_midWeekResolvesToItsMonday() {
        val wednesday = LocalDate.of(2026, 6, 17)
        val days = DateUtils.weekDays(wednesday)
        assertEquals(LocalDate.of(2026, 6, 15), days.first())
        assertEquals(7, days.size)
    }

    @Test
    fun atmosphere_thresholds() {
        assertEquals("新的一个月，从一件小事开始", DateUtils.atmosphere(LocalDate.of(2026, 6, 1)))
        assertEquals("新的一个月，从一件小事开始", DateUtils.atmosphere(LocalDate.of(2026, 6, 5)))
        assertEquals("月中了，给自己一点掌声", DateUtils.atmosphere(LocalDate.of(2026, 6, 15)))
        assertEquals("月底临近，慢慢收尾", DateUtils.atmosphere(LocalDate.of(2026, 6, 25)))
        assertEquals("月末了，整理一下再出发", DateUtils.atmosphere(LocalDate.of(2026, 6, 28)))
    }

    @Test
    fun hm_format() {
        assertEquals("09:05", DateUtils.hm(java.time.LocalTime.of(9, 5)))
    }
}
