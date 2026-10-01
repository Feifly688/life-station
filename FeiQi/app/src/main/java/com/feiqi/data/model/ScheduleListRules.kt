package com.feiqi.data.model

import java.time.LocalDate

/**
 * 日程列表的**分组、分区规则**（纯函数，与 Compose 解耦，可直接单测）。
 *
 * 原先这些判断散落在 `ScheduleScreen` 与 `ScheduleViewModel` 里，改规则时既难验证也容易漏改；
 * 收敛到模型层后：UI 只负责渲染，规则改动只需动这里 + 补一条单测。
 */
object ScheduleListRules {

    /**
     * 把日程全量列表聚合成视图项（单条 + 清单组）。
     *
     * **清单始终作为一个整体分组，绝不拆开**：清单里的过期条目仍留在清单组内，
     * 由 [partition] 依据「整张清单是否过期」决定它进入待办区还是已完成区。
     * （v1.8.0 曾把过期条目拆成单条，导致"取消勾选→拆成多条 / 逐条完成→重新组合"的抖动，已回退。）
     *
     * 排序（v1.13.0 起）：
     * - **待办区**：按**创建时间**倒序（最新添加的在最上）；
     * - **已完成区**：按**完成时间**倒序；自动移入（无完成日期）的条目用其到期日参与排序。
     *
     * @return 按上述顺序排好的视图项（先待办区、后已完成区）。
     */
    fun buildItems(all: List<Schedule>): List<ScheduleListItem> {
        val (withList, singles) = all.partition { it.listId != null }

        val groups = withList.groupBy { it.listId!! }.map { (listId, items) ->
            ScheduleListItem.Group(
                listId = listId,
                title = items.firstOrNull { it.listTitle.isNotBlank() }?.listTitle
                    ?: items.first().title,
                items = items.sortedBy { it.itemOrder },
                recurrence = items.firstOrNull { it.recurrence.isRepeating }?.recurrence
                    ?: Recurrence.NONE
            )
        }

        val items = singles.map { ScheduleListItem.Single(it) } + groups

        val today = LocalDate.now()
        return items.sortedWith(
            // ① 待办区在前、已完成区在后
            compareBy<ScheduleListItem> { if (it.pending(today)) 0 else 1 }
                // ② 区内排序：待办按创建时间倒序；已完成按完成时间倒序
                .thenByDescending {
                    if (it.pending(today)) it.pendingSortKey() else it.completedSortKey()
                }
                // ③ 兜底：同一时间键时按 id 倒序（新建的在后）
                .thenByDescending { it.maxId() }
        )
    }

    /** 待办区排序键：条目**创建时间**（数值越大越新，排越前）。 */
    private fun ScheduleListItem.pendingSortKey(): Long = when (this) {
        is ScheduleListItem.Single -> schedule.createdAt
        is ScheduleListItem.Group -> items.maxOf { it.createdAt }
    }

    /**
     * 已完成区排序键：**完成时间**倒序。
     * 自动移入已完成区的条目没有 [Schedule.completedDate]，用其**到期日**参与排序（保证同段可比）。
     */
    private fun ScheduleListItem.completedSortKey(): Long = when (this) {
        is ScheduleListItem.Single ->
            (schedule.completedDate ?: schedule.date).toEpochDay()

        is ScheduleListItem.Group ->
            (items.mapNotNull { it.completedDate }.maxOrNull() ?: items.maxOf { it.date }).toEpochDay()
    }

    private fun ScheduleListItem.maxId(): Long = when (this) {
        is ScheduleListItem.Single -> schedule.id
        is ScheduleListItem.Group -> items.maxOf { it.id }
    }

    /**
     * 把聚合后的列表分成「待办区」与「已完成区」。
     *
     * - **单条**：已完成、或**已过期但没打卡**（[Schedule.isExpired]）→ 已完成区；
     * - **清单**：**全部完成** 或 **已过期未完成**（[ScheduleListItem.Group.isOverdue]）→ 已完成区。
     *   整张清单始终作为一个整体移动，**不会拆开**；逾期与未逾期的清单行为一致。
     *
     * @return `first` = 待办区，`second` = 已完成区，顺序与原列表一致。
     */
    fun partition(
        items: List<ScheduleListItem>,
        today: LocalDate = LocalDate.now()
    ): Pair<List<ScheduleListItem>, List<ScheduleListItem>> = items.partition { it.pending(today) }

    /** 该条目是否仍属于「待办区」。 */
    fun ScheduleListItem.pending(today: LocalDate = LocalDate.now()): Boolean = when (this) {
        is ScheduleListItem.Single -> !schedule.completed && !schedule.isExpired(today)
        is ScheduleListItem.Group -> !allCompleted && !isOverdue(today)
    }
}

/**
 * 清单是否「已过期未完成」：只要还有**未完成**的条目过了截止日（[Schedule.isExpired]，
 * 清单条目口径 = `date < today`），整张清单就算过期。
 */
fun ScheduleListItem.Group.isOverdue(today: LocalDate = LocalDate.now()): Boolean =
    items.any { !it.completed && it.isExpired(today) }
