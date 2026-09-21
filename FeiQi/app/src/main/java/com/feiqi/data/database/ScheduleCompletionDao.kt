package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.feiqi.data.entity.ScheduleCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleCompletionDao {

    @Insert
    suspend fun insert(entity: ScheduleCompletionEntity): Long

    /** 某条待办的全部完成记录（按日期倒序）。 */
    @Query(
        "SELECT * FROM schedule_completions " +
            "WHERE scheduleId = :scheduleId ORDER BY date DESC, time ASC"
    )
    fun getBySchedule(scheduleId: Long): Flow<List<ScheduleCompletionEntity>>

    /** 某清单的全部完成记录（按日期倒序）。 */
    @Query(
        "SELECT * FROM schedule_completions " +
            "WHERE listId = :listId ORDER BY date DESC, time ASC"
    )
    fun getByList(listId: String): Flow<List<ScheduleCompletionEntity>>

    /** 某天的全部完成记录（按时间正序）。 */
    @Query(
        "SELECT * FROM schedule_completions " +
            "WHERE date = :date ORDER BY time ASC"
    )
    fun getByDate(date: String): Flow<List<ScheduleCompletionEntity>>

    @Query("DELETE FROM schedule_completions WHERE scheduleId = :scheduleId")
    suspend fun deleteBySchedule(scheduleId: Long)

    @Query("DELETE FROM schedule_completions WHERE listId = :listId")
    suspend fun deleteByList(listId: String)

    @Query("DELETE FROM schedule_completions")
    suspend fun deleteAll()
}
