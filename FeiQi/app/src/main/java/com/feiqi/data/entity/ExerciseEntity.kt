package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * **动作库**：一个动作一条记录，可被多天、多计划复用。
 *
 * 注意：动作本身**不含组数/次数** —— 那是"某一天怎么练"的配置，
 * 记录在 [WorkoutExerciseEntity]，避免同一动作在不同天无法配不同组数。
 *
 * `name` 唯一：同名动作只入库一次（内置种子按名字去重）。
 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 动作名，如「俯卧撑」。 */
    val name: String,

    /** 主要部位（中文，如「胸」「三头」）。 */
    val bodyPart: String,

    /** 难度，取值见 `ExerciseDifficulty.code`。 */
    val difficulty: String,

    /** 动作要领（复用旧计划里的 note）。 */
    val description: String,

    /** 图片资源位：**预留字段**，当前统一写空串，后续阶段再补资源。 */
    val image: String = "",
)
