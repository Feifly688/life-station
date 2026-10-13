package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「训练日 ↔ 动作」关联表：描述**某一天**某个动作怎么练。
 *
 * 同一动作可以在多天出现，各自的组数/次数/休息各不相同；
 * 因此动作本体在 [ExerciseEntity]，训练参数在这里。
 *
 * `dayId + exerciseId` 唯一 —— 防止同一动作在同一天重复插入。
 */
@Entity(
    tableName = "workout_exercises",
    indices = [
        Index(value = ["dayId", "exerciseId"], unique = true),
        Index(value = ["dayId"]),
        Index(value = ["exerciseId"]),
    ]
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 关联 [WorkoutDayEntity.id]。 */
    val dayId: Long,

    /** 关联 [ExerciseEntity.id]。 */
    val exerciseId: Long,

    /** 组数。 */
    val sets: Int,

    /** 次数描述，如「力竭」「12次」。 */
    val reps: String,

    /** 时长（秒）；非计时动作为 0。 */
    val duration: Int = 0,

    /** 组间休息（秒）。 */
    val restTime: Int = 90,

    /** 当天的动作提示（可为空）。 */
    val note: String = "",
)
