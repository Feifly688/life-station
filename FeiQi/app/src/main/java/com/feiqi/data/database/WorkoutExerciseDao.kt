package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.feiqi.data.entity.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

/** 「训练日 ↔ 动作」关联（v2）读写。 */
@Dao
interface WorkoutExerciseDao {

    @Query("SELECT * FROM workout_exercises WHERE dayId = :dayId ORDER BY id ASC")
    fun observeByDay(dayId: Long): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises WHERE dayId = :dayId ORDER BY id ASC")
    suspend fun getByDay(dayId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT COUNT(*) FROM workout_exercises WHERE dayId = :dayId")
    suspend fun countByDay(dayId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE dayId = :dayId")
    suspend fun deleteByDay(dayId: Long)
}
