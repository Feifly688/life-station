package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 日程表（v6）。
 * 支持两种形态：
 * - 独立日程：listId 为 null，自身即单条待办。
 * - 清单：多条记录共享同一个 listId，listTitle 为清单标题；清单内所有条目 completed 后，清单才视为完成。
 * - recurrence：重复规则（NONE/DAILY/WEEKDAYS/WEEKLY/MONTHLY/YEARLY），
 *   未开启提醒时恒为 NONE；循环清单完成后按规则顺延到下一个应提醒日。
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val date: String, // yyyy-MM-dd
    val time: String?, // HH:mm
    val groupName: String = "生活",
    val note: String = "",
    val reminder: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    // 清单相关（v6 新增）
    val listId: String? = null,
    val listTitle: String = title,
    val recurrence: String = "NONE",
    val itemOrder: Int = 0,
    val lastResetDate: String? = null, // yyyy-MM-dd，记录循环清单最后一次重置日期
    val completedDate: String? = null // yyyy-MM-dd，记录清单全部完成时的日期
)
