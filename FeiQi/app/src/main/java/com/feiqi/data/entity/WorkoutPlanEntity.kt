package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 训练计划（v2 计划**头**，不再等同于"某一天"）。
 *
 * - 官方计划：`type = OFFICIAL`，由内置种子写入，不可编辑；
 * - 用户计划：`type = USER_CREATED`，后续阶段由创建页写入。
 *
 * 计划下的**训练日**在 [WorkoutDayEntity]，动作库在 [ExerciseEntity]。
 *
 * ## 不建外键
 * 与本库既有表（habit_records 等）保持一致：只留索引，不声明外键，
 * 避免 SQLite 外键带来的插入顺序限制。
 */
@Entity(
    tableName = "workout_plans",
    indices = [Index(value = ["planId"], unique = true)]
)
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 计划业务 id，如 `home_beginner_30`。 */
    val planId: String,

    /** 计划名称，如「30天居家爆炸训练」。 */
    val name: String,

    val description: String,

    /** 取值见 `WorkoutPlanType.code`。 */
    val type: String,

    /** 取值见 `WorkoutGoal.code`。 */
    val goal: String,

    /** 训练周期（天），如 30。 */
    val cycleDays: Int,

    /** 创建时间（毫秒）。 */
    val createdTime: Long,
)
