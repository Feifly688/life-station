package com.feiqi.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 待办区 / 已完成区的划分规则：
 * - 单条：已完成 **或** 已过期未打卡 → 已完成区；
 * - 清单：仅「全部条目完成」→ 已完成区。
 */
class ScheduleListRulesTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun single(
        id: Long,
        date: LocalDate = today,
        time: LocalTime? = LocalTime.of(23, 0),
        reminder: Boolean = true,
        completed: Boolean = false
    ) = ScheduleListItem.Single(
        Schedule(
            id = id,
            title = "待办$id",
            date = date,
            time = time,
            reminder = reminder,
            completed = completed
        )
    )

    private fun group(listId: String, completedFlags: List<Boolean>, overdueItem: Boolean = false) =
        ScheduleListItem.Group(
            listId = listId,
            title = "清单$listId",
            items = completedFlags.mapIndexed { index, done ->
                Schedule(
                    title = "条目$index",
                    // overdueItem = true 时把首条设为「昨天 + 已开提醒」→ 过期
                    date = if (overdueItem && index == 0) today.minusDays(1) else today,
                    time = LocalTime.of(9, 0),
                    reminder = true,
                    completed = done
                )
            },
            recurrence = Recurrence.NONE
        )

    @Test
    fun pendingSingle_goesToActiveSection() {
        val item = single(1)
        val (active, completed) = ScheduleListRules.partition(listOf(item), today)
        assertEquals(1, active.size)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun completedSingle_goesToCompletedSection() {
        val item = single(1, completed = true)
        val (active, completed) = ScheduleListRules.partition(listOf(item), today)
        assertTrue(active.isEmpty())
        assertEquals(1, completed.size)
    }

    @Test
    fun overdueUncompletedSingle_goesToCompletedSection() {
        // 昨天、已开提醒、未完成 → 过期 → 进已完成区（仍可补打卡）
        val item = single(1, date = today.minusDays(1))
        val (active, completed) = ScheduleListRules.partition(listOf(item), today)
        assertTrue(active.isEmpty())
        assertEquals(1, completed.size)
        // 只是分区位置变化，未完成状态本身不被篡改
        assertFalse((completed.first() as ScheduleListItem.Single).schedule.completed)
    }

    @Test
    fun partiallyCompletedGroup_staysActive_evenIfOneItemOverdue() {
        // 清单里有一条过期，但清单整体没完成 → 仍留在待办区
        val item = group("L1", listOf(false, true), overdueItem = true)
        val (active, completed) = ScheduleListRules.partition(listOf(item), today)
        assertEquals(1, active.size)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun fullyCompletedGroup_goesToCompletedSection() {
        val item = group("L2", listOf(true, true))
        val (active, completed) = ScheduleListRules.partition(listOf(item), today)
        assertTrue(active.isEmpty())
        assertEquals(1, completed.size)
    }

    @Test
    fun emptyList_yieldsEmptySections() {
        val (active, completed) = ScheduleListRules.partition(emptyList(), today)
        assertTrue(active.isEmpty() && completed.isEmpty())
    }

    @Test
    fun orderIsPreservedWithinSections() {
        val items = listOf(
            single(1),                                  // 待办
            single(2, completed = true),                // 已完成
            single(3, date = today.plusDays(1)),        // 待办
            single(4, date = today.minusDays(2))        // 过期 → 已完成
        )
        val (active, completed) = ScheduleListRules.partition(items, today)
        assertEquals(listOf(1L, 3L), active.map { (it as ScheduleListItem.Single).schedule.id })
        assertEquals(listOf(2L, 4L), completed.map { (it as ScheduleListItem.Single).schedule.id })
    }
}
