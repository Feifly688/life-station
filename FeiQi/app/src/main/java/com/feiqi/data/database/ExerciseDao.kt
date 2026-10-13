package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.feiqi.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

/** 动作库（v2）读写。 */
@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY id ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name = :name")
    suspend fun getByName(name: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    /** `name` 唯一：同名动作只保留一条（REPLACE 会覆盖既有难度/要领）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)
}
