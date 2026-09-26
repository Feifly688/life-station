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
     * 把日程全量列表聚合成视图项（单条 + 清单组），并**把"过期未完成"的清单条目从清单里拆出来**，
     * 单独作为一条 [ScheduleListItem.Single] 展示（从而进入「已完成」区并标「已过期」）。
     *
     * 拆分规则：
     * - 清单条目里 `!completed && isExpired(today)`（过了截止日仍未完成）→ 拆出为单条；
     * - 其余条目仍留在清单组内；若整张清单的条目都被拆空 → 不再生成该清单组。
     *
     * @return 先单条、后清单组（与旧实现保持一致的展示顺序）。
     */
    fun buildItems(all: List<Schedule>, today: LocalDate = LocalDate.now()): List<ScheduleListItem> {
        val (withList, singles) = all.partition { it.listId != null }

        val lifted = mutableListOf<Schedule>()
        val groups = withList.groupBy { it.listId!! }.mapNotNull { (listId, items) ->
            val sorted = items.sortedBy { it.itemOrder }
            val (overdue, active) = sorted.partition { !it.completed && it.isExpired(today) }
            lifted += overdue
            if (active.isEmpty()) {
                null
            } else {
                ScheduleListItem.Group(
                    listId = listId,
                    title = active.firstOrNull { it.listTitle.isNotBlank() }?.listTitle
                        ?: active.first().title,
                    items = active,
                    recurrence = active.firstOrNull { it.recurrence.isRepeating }?.recurrence
                        ?: Recurrence.NONE
                )
            }
        }

        val singleItems = (singles + lifted).map { ScheduleListItem.Single(it) }

        // 排序：未完成的在前，按日期；已完成的在后。
        val sortedGroups = groups.sortedWith(
            compareByDescending<ScheduleListItem.Group> { !it.allCompleted }
                .thenBy { it.items.firstOrNull()?.date ?: LocalDate.MAX }
        )
        val sortedSingles = singleItems.sortedWith(
            compareByDescending<ScheduleListItem.Single> { !it.schedule.completed }
                .thenBy { it.schedule.date }
        )
        return sortedSingles + sortedGroups
    }

    /**
     * 把聚合后的列表分成「待办区」与「已完成区」。
     *
     * - **单条**：已完成、或**已过期但没打卡**（[Schedule.isExpired]）→ 已完成区；
     *   过期条目仍保留勾选框，用户可随时补完成。
     * - **清单组**：只有**全部条目完成**才进已完成区（组内的过期条目已在 [buildItems] 阶段拆出，
     *   不会出现"整张清单被一条过期项拖进已完成区"）。
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
        is ScheduleListItem.Group -> !allCompleted
    }
}
