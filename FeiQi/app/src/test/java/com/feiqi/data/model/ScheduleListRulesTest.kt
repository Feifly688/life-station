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

    /**
     * 默认 **不带时间**：`Schedule.isOverdue` 对"当天"的判定会读真实时钟
     * （`now > time`），若默认给一个固定时刻，测试会在一天中的某些时段随机失败。
     * 需要验证"当天过了打卡时刻"时，请显式传 [time]。
     */
    private fun single(
        id: Long,
        date: LocalDate = today,
        time: LocalTime? = null,
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
        // 清单里有一条过期且未完成 → 整张清单算"逾期未完成"，移入已完成区（v1.10 起口径）
        val item = group("L1", listOf(false, true), overdueItem = true)
        val (active, completed) = ScheduleListRules.partition(listOf(item), today)
        assertTrue(active.isEmpty())
        assertEquals(1, completed.size)
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

    @Test
    fun buildItems_keepsListItemsGrouped_evenWhenOverdue() {
        // 清单里的过期条目也必须留在清单组内，绝不拆成单条（修复"取消勾选→拆开 / 完成→重组"）
        val overdue = Schedule(title = "过期项", date = today.minusDays(1), listId = "L", listTitle = "清单", itemOrder = 0)
        val active = Schedule(title = "进行中", date = today, listId = "L", listTitle = "清单", itemOrder = 1)
        val items = ScheduleListRules.buildItems(listOf(overdue, active))

        assertEquals(1, items.filterIsInstance<ScheduleListItem.Group>().size)
        assertEquals(0, items.filterIsInstance<ScheduleListItem.Single>().size)
        val group = items.filterIsInstance<ScheduleListItem.Group>().first()
        assertEquals(listOf("过期项", "进行中"), group.items.map { it.title })
    }

    @Test
    fun group_isOverdue_whenAnyUncompletedItemPassedDeadline() {
        val done = Schedule(title = "已做完", date = today.minusDays(1), completed = true, completedDate = today, listId = "L", listTitle = "清单")
        val overdue = Schedule(title = "没过期但没做完", date = today.minusDays(1), listId = "L", listTitle = "清单")
        val group = ScheduleListItem.Group(listId = "L", title = "清单", items = listOf(done, overdue), recurrence = Recurrence.NONE)
        assertTrue(group.isOverdue(today))
        // 全完成 → 不算过期
        val allDone = ScheduleListItem.Group(listId = "L", title = "清单",
            items = listOf(done, overdue.copy(completed = true, completedDate = today)), recurrence = Recurrence.NONE)
        assertFalse(allDone.isOverdue(today))
    }

    @Test
    fun overdueUncompletedGroup_goesToCompletedSection() {
        // 逾期未完成的整张清单 → 进已完成区（且不拆分）
        val overdue = Schedule(title = "A", date = today.minusDays(1), listId = "L", listTitle = "清单")
        val group = ScheduleListItem.Group(listId = "L", title = "清单", items = listOf(overdue), recurrence = Recurrence.NONE)
        val (active, completed) = ScheduleListRules.partition(listOf(group), today)
        assertTrue(active.isEmpty())
        assertEquals(1, completed.size)
        assertEquals(group, completed.first())
    }

    @Test
    fun nonOverdueUncompletedGroup_staysActive() {
        val item = Schedule(title = "A", date = today, listId = "L", listTitle = "清单")
        val group = ScheduleListItem.Group(listId = "L", title = "清单", items = listOf(item), recurrence = Recurrence.NONE)
        val (active, completed) = ScheduleListRules.partition(listOf(group), today)
        assertEquals(1, active.size)
        assertTrue(completed.isEmpty())
    }

    // ---------------- 排序（v1.13.0）：待办按创建时间倒序 / 已完成按完成时间倒序 ----------------

    @Test
    fun pendingSection_sortsByCreatedAtDesc() {
        val older = Schedule(title = "旧添加", date = today, createdAt = 1_000L)
        val newer = Schedule(title = "新添加", date = today, createdAt = 2_000L)
        val items = ScheduleListRules.buildItems(listOf(older, newer))
        assertEquals(
            listOf("新添加", "旧添加"),
            items.map { (it as ScheduleListItem.Single).schedule.title }
        )
    }

    @Test
    fun pendingComesBeforeCompleted() {
        val done = Schedule(title = "已完成", date = today, completed = true, completedDate = today)
        val pending = Schedule(title = "待办", date = today, createdAt = 9_999L)
        val items = ScheduleListRules.buildItems(listOf(done, pending))
        assertEquals("待办", (items.first() as ScheduleListItem.Single).schedule.title)
    }

    @Test
    fun completedSection_sortsByCompletionDateDesc() {
        val early = Schedule(title = "早完成", date = today, completed = true, completedDate = today.minusDays(2))
        val late = Schedule(title = "晚完成", date = today, completed = true, completedDate = today.minusDays(1))
        val (_, completed) = ScheduleListRules.partition(
            ScheduleListRules.buildItems(listOf(early, late)),
            today
        )
        assertEquals(
            listOf("晚完成", "早完成"),
            completed.map { (it as ScheduleListItem.Single).schedule.title }
        )
    }

    @Test
    fun completedSection_autoMovedItems_alsoRankByCompletion() {
        // 自动移入（跨日未完成）的条目也参与"完成时间"排序：用其到期日
        val movedOld = Schedule(title = "前天到期", date = today.minusDays(2), reminder = true)
        val movedNew = Schedule(title = "昨天到期", date = today.minusDays(1), reminder = true)
        val doneToday = Schedule(title = "今天完成", date = today, completed = true, completedDate = today)
        val (active, completed) = ScheduleListRules.partition(
            ScheduleListRules.buildItems(listOf(movedOld, movedNew, doneToday)),
            today
        )
        assertTrue(active.isEmpty())
        assertEquals(
            listOf("今天完成", "昨天到期", "前天到期"),
            completed.map { (it as ScheduleListItem.Single).schedule.title }
        )
    }
}
