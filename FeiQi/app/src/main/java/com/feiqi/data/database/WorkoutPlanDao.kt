package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

/** 训练计划（v2）读写。 */
@Dao
interface WorkoutPlanDao {

    @Query("SELECT * FROM workout_plans ORDER BY cycleDays ASC, id ASC")
    fun observeAll(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE planId = :planId")
    fun observeByPlanId(planId: String): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM workout_plans WHERE planId = :planId")
    suspend fun getByPlanId(planId: String): WorkoutPlanEntity?

    @Query("SELECT COUNT(*) FROM workout_plans")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<WorkoutPlanEntity>)

    @Update
    suspend fun update(plan: WorkoutPlanEntity)

    @Query("DELETE FROM workout_plans WHERE planId = :planId")
    suspend fun deleteByPlanId(planId: String)
}
