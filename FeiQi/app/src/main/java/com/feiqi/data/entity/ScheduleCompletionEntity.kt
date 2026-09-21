package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待办「已完成」记录（循环提醒每次触发时生成一条）。
 *
 * 与 schedules 表的区别：schedules 的 completed 字段表示「该事项是否停在已完成状态」；
 * 而循环提醒的待办永远不会被标记为 completed（否则会停止循环）。因此每次提醒触发时，
 * 这里单独记一条完成记录，待办本身保持 active 并把提醒时间顺延 +1 天继续循环。
 *
 * 注意：indices 必须与 MIGRATION_7_8 中创建的索引一一对应，否则 Room 在升级安装时
 * 会因「期望索引集合」与「实际索引集合」不一致而校验失败、打开数据库即崩溃。
 */
@Entity(
    tableName = "schedule_completions",
    indices = [
        Index(value = ["scheduleId"]),
        Index(value = ["listId"])
    ]
)
data class ScheduleCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: Long,            // 对应的 schedules 行 id
    val listId: String? = null,      // 若属于清单，记录清单 id（便于按清单查历史）
    val date: String,                // yyyy-MM-dd，触发完成的当天日期
    val time: String? = null,        // HH:mm，提醒时间（快照）
    val title: String,               // 标题快照，便于历史展示
    val createdAt: Long = System.currentTimeMillis()
)
