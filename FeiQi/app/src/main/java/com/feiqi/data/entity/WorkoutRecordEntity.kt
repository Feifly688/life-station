package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 健身打卡记录（**已发生的事实**）。
 *
 * ## 只记录「打卡」，不记录计划
 * 计划在 `WorkoutPlan`（静态 30 天数据），这里只存"第几天、什么时候完成的"。
 * 完成状态因此**由本表派生**，计划本身保持不可变。
 *
 * ## 索引
 * - `planId + dayNumber` 唯一：同一天只打卡一次（补打卡走 UPDATE，不重复插入）。
 * - `completedAt`：按时间倒序展示历史 / 计算连续天数。
 *
 * ## 不建外键
 * 计划是静态常量、不在数据库里（见 [com.feiqi.data.model.WorkoutPlan]），
 * 所以这里没有外键可声明。
 */
@Entity(
    tableName = "workout_records",
    indices = [
        Index(value = ["planId", "dayNumber"], unique = true),
        Index(value = ["completedAt"]),
    ]
)
data class WorkoutRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 计划 id，如 `home30`。 */
    val planId: String,
    /** 第几天（1..30）。 */
    val dayNumber: Int,
    /** 打卡日期，yyyy-MM-dd。 */
    val date: String,
    /** 完成时刻（毫秒）。 */
    val completedAt: Long,
)