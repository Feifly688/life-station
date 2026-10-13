package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.feiqi.data.entity.WorkoutRecordEntity
import kotlinx.coroutines.flow.Flow

/** 健身打卡记录读写。 */
@Dao
interface WorkoutDao {

    /** 某计划下的全部打卡记录（按完成时间正序，便于派生进度）。 */
    @Query("SELECT * FROM workout_records WHERE planId = :planId ORDER BY completedAt ASC")
    fun observeByPlan(planId: String): Flow<List<WorkoutRecordEntity>>

    /** 某天是否已打卡。 */
    @Query("SELECT COUNT(*) FROM workout_records WHERE planId = :planId AND dayNumber = :dayNumber")
    suspend fun countByDay(planId: String, dayNumber: Int): Int

    /** 已完成的天数集合（派生进度 / 连续天数用）。 */
    @Query("SELECT dayNumber FROM workout_records WHERE planId = :planId")
    fun observeCompletedDays(planId: String): Flow<List<Int>>

    /**
     * 打卡：同一天已存在则覆盖（补打卡不产生重复行）。
     * `OnConflictStrategy.REPLACE` 依赖 `planId + dayNumber` 唯一索引。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WorkoutRecordEntity)

    /** 取消某天打卡。 */
    @Query("DELETE FROM workout_records WHERE planId = :planId AND dayNumber = :dayNumber")
    suspend fun deleteByDay(planId: String, dayNumber: Int)
}