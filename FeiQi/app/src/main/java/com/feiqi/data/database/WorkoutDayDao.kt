package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.feiqi.data.entity.WorkoutDayEntity
import kotlinx.coroutines.flow.Flow

/** 训练日（v2）读写。 */
@Dao
interface WorkoutDayDao {

    @Query("SELECT * FROM workout_days WHERE planId = :planId ORDER BY dayNumber ASC")
    fun observeByPlan(planId: String): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_days WHERE planId = :planId AND dayNumber = :dayNumber")
    suspend fun getByDay(planId: String, dayNumber: Int): WorkoutDayEntity?

    @Query("SELECT COUNT(*) FROM workout_days WHERE planId = :planId")
    suspend fun countByPlan(planId: String): Int

    /** 同步取整个计划的训练日（装配运行时计划用）。 */
    @Query("SELECT * FROM workout_days WHERE planId = :planId ORDER BY dayNumber ASC")
    suspend fun getByPlan(planId: String): List<WorkoutDayEntity>

    /** 删除计划时级联清理训练日。 */
    @Query("DELETE FROM workout_days WHERE planId = :planId")
    suspend fun deleteByPlan(planId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<WorkoutDayEntity>)
}
