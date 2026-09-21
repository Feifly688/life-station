package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY date ASC, time ASC, itemOrder ASC, createdAt ASC")
    fun getAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE date = :date ORDER BY time ASC, itemOrder ASC")
    fun getByDate(date: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE completed = 0 AND date < :today ORDER BY date DESC")
    fun getOverdue(today: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE date BETWEEN :start AND :end ORDER BY date ASC, time ASC")
    fun getBetween(start: String, end: String): Flow<List<ScheduleEntity>>

    /**
     * 取某个清单内【未完成】的条目，按清单顺序排列。
     * 提醒触发时只需要这批数据（用于拼通知文案），避免全表加载 + 内存过滤。
     */
    @Query("SELECT * FROM schedules WHERE listId = :listId AND completed = 0 ORDER BY itemOrder ASC, createdAt ASC")
    suspend fun getPendingByListId(listId: String): List<ScheduleEntity>

    @Insert
    suspend fun insert(entity: ScheduleEntity): Long

    @Update
    suspend fun update(entity: ScheduleEntity)

    /** 批量插入：Room 在单个事务内完成，避免逐条提交（每条一次 fsync）。 */
    @Insert
    suspend fun insertAll(entities: List<ScheduleEntity>): List<Long>

    /** 批量更新：同样收敛到单个事务。 */
    @Update
    suspend fun updateAll(entities: List<ScheduleEntity>)

    @Delete
    suspend fun delete(entity: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE listId = :listId")
    suspend fun deleteByListId(listId: String)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}
