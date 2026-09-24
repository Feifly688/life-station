package com.feiqi.data.model

import java.time.LocalDate

/**
 * 日程列表的**分区与分组规则**（纯函数，与 Compose 解耦，可直接单测）。
 *
 * 原先这些判断散落在 `ScheduleScreen` 的 Composable 里，改规则时既难验证也容易漏改；
 * 收敛到模型层后：UI 只负责渲染，规则改动只需动这里 + 补一条单测。
 */
object ScheduleListRules {

    /**
     * 把聚合后的列表分成「待办区」与「已完成区」。
     *
     * - **单条日程**：已完成、或**已过期但没打卡**（[Schedule.isOverdue]）→ 已完成区；
     *   过期条目仍保留勾选框，用户可随时补完成。
     * - **待办清单**：只有**全部条目完成**才进已完成区 —— 不会因为清单里某一条过期，
     *   就把整张清单从待办区挪走。
     *
     * @param today 判定基准日（只用于单条的过期判断，与时刻无关的跨日口径一致）。
     * @return `first` = 待办区，`second` = 已完成区，顺序与原列表一致。
     */
    fun partition(
        items: List<ScheduleListItem>,
        today: LocalDate = LocalDate.now()
    ): Pair<List<ScheduleListItem>, List<ScheduleListItem>> = items.partition { it.pending(today) }

    /** 该条目是否仍属于「待办区」。 */
    fun ScheduleListItem.pending(today: LocalDate = LocalDate.now()): Boolean = when (this) {
        is ScheduleListItem.Single -> !schedule.completed && !schedule.isOverdue(today)
        is ScheduleListItem.Group -> !allCompleted
    }
}
