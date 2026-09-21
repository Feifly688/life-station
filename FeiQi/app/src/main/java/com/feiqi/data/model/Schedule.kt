package com.feiqi.data.model

import java.time.LocalDate
import java.time.LocalTime

enum class ScheduleFilter {
    ALL, TODAY, PLANNED, COMPLETED
}

data class Schedule(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val groupName: String = "生活",
    val note: String = "",
    val reminder: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    // 清单相关（v6）
    val listId: String? = null,
    val listTitle: String = title,
    /** 重复规则；未开启提醒时恒为 [Recurrence.NONE]。 */
    val recurrence: Recurrence = Recurrence.NONE,
    val itemOrder: Int = 0,
    val lastResetDate: LocalDate? = null,
    val completedDate: LocalDate? = null
) {
    /** 是否循环条目（兼容既有调用点）。 */
    val isRecurring: Boolean get() = recurrence.isRepeating

    /** 是否处于「无时间」状态：不填时间即以未定时待办存在，不参与逾期判定。 */
    val untimed: Boolean get() = time == null
}

data class ScheduleUiState(
    val schedules: List<Schedule> = emptyList(),
    val filteredSchedules: List<Schedule> = emptyList(),
    // 聚合后的清单/单条视图数据
    val listItems: List<ScheduleListItem> = emptyList(),
    val todayCount: Int = 0,
    val overdueCount: Int = 0,
    val upcoming7Count: Int = 0,
    val completedCount: Int = 0,
    val selectedDate: LocalDate = com.feiqi.utils.DateUtils.today(),
    val filter: ScheduleFilter = ScheduleFilter.ALL
)

/** 日程页聚合后的列表项：要么是一个独立日程，要么是一个清单。 */
sealed class ScheduleListItem {
    data class Single(
        val schedule: Schedule
    ) : ScheduleListItem()

    data class Group(
        val listId: String,
        val title: String,
        val items: List<Schedule>,
        /** 清单的重复规则（清单内各条目一致）。 */
        val recurrence: Recurrence,
        val expanded: Boolean = false
    ) : ScheduleListItem() {
        val total: Int get() = items.size
        val completedCount: Int get() = items.count { it.completed }
        val allCompleted: Boolean get() = total > 0 && completedCount == total

        /** 是否循环清单（兼容既有调用点）。 */
        val isRecurring: Boolean get() = recurrence.isRepeating

        /** 清单是否处于「无时间」状态。 */
        val untimed: Boolean get() = items.all { it.time == null }
    }
}
