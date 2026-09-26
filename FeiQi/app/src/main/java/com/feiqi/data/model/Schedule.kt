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
    val completedDate: LocalDate? = null,

    /**
     * 是否「过期后才补完成」（v10）：
     * 完成的那一刻应完成时间已过 → 完成态下仍保留「已过期」标签。
     * 未完成条目的过期状态由 [isOverdue] 实时推导，不写库。
     */
    val completedLate: Boolean = false
) {
    /** 是否循环条目（兼容既有调用点）。 */
    val isRecurring: Boolean get() = recurrence.isRepeating

    /** 是否处于「无时间」状态：不填时间即以未定时待办存在，不参与逾期判定。 */
    val untimed: Boolean get() = time == null

    /**
     * 是否「已过期（当天未按时）」：
     * - 未开启提醒 → 不参与逾期判定（与既有口径一致）；
     * - `date < today` → 过期（跨日仍未见完成）；
     * - `date == today` 且设置了时间且当前已过该时刻 → 过期；
     * - 其余（含无时间的当天项）→ 未过期。
     *
     * @param today 判定基准日；[now] 判定时刻。两者默认取设备当前值，测试可显式传入。
     */
    fun isOverdue(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): Boolean {
        if (!reminder) return false
        if (date < today) return true
        return date == today && time != null && now > time
    }

    /**
     * 清单条目的「过期」口径：**过了截止日（date < today）仍未完成**即视为已过期。
     *
     * 与 [isOverdue] 的区别：**不看是否设了提醒、也不看当天具体时刻** ——
     * 待办清单的"打卡"语义是"当天结束前勾掉"，所以只看日期是否已跨过。
     */
    fun isDeadlineOverdue(today: LocalDate = LocalDate.now()): Boolean = date < today

    /**
     * 统一的「是否应归入已完成区并标『已过期』」判定（仅对**未完成**条目调用）：
     * - 单条日程（listId == null）：沿用 [isOverdue]（需提醒 + 按时判定）；
     * - 清单条目（listId != null）：用 [isDeadlineOverdue]（只看到期日）。
     */
    fun isExpired(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): Boolean =
        if (listId == null) isOverdue(today, now) else isDeadlineOverdue(today)
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
