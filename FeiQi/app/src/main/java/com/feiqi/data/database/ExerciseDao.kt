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

    /** 按 id 批量取（装配某一天的动作时用）。 */
    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ExerciseEntity>

    /** 按部位批量取（生成用户计划时按部位挑动作）。 */
    @Query("SELECT * FROM exercises WHERE bodyPart IN (:parts) ORDER BY id ASC")
    suspend fun getByBodyParts(parts: List<String>): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    /** `name` 唯一：同名动作只保留一条（REPLACE 会覆盖既有难度/要领）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)
}
