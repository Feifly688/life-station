package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 训练计划中的**某一天**（v2 拆分出来的模型）。
 *
 * 一天可属于多个计划吗？不能 —— `planId + dayNumber` 唯一。
 * 一天包含哪些动作由 [WorkoutExerciseEntity] 描述。
 */
@Entity(
    tableName = "workout_days",
    indices = [Index(value = ["planId", "dayNumber"], unique = true)]
)
data class WorkoutDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属计划 id（对应 [WorkoutPlanEntity.planId]）。 */
    val planId: String,

    /** 第几天（从 1 开始）。 */
    val dayNumber: Int,

    /** 当天标题，如「胸 + 三头」。 */
    val title: String,

    /** 当天训练部位，如「胸 + 三头」。 */
    val bodyParts: String,

    val description: String,
)
